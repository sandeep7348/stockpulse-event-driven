package com.stockalert.market.upstox;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;

/**
 * A minimal stand-in for Upstox: the HTTP authorize endpoint plus just enough of the
 * WebSocket protocol to accept connections, read client frames and send binary frames.
 */
final class FakeUpstoxServer implements AutoCloseable {

	private static final String WEBSOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

	private static final int OP_CONTINUATION = 0x0;

	private static final int OP_BINARY = 0x2;

	private static final int OP_TEXT = 0x1;

	private final HttpServer http;

	private final ServerSocket webSockets;

	final List<String> authorizationHeaders = new CopyOnWriteArrayList<>();

	final List<String> requestedCodes = new CopyOnWriteArrayList<>();

	final List<String> binaryFrames = new CopyOnWriteArrayList<>();

	final List<String> textFrames = new CopyOnWriteArrayList<>();

	private final List<Integer> scriptedStatuses = new CopyOnWriteArrayList<>();

	private final AtomicInteger codes = new AtomicInteger();

	private volatile Socket client;

	FakeUpstoxServer() throws IOException {
		this.webSockets = new ServerSocket(0);
		this.http = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		this.http.createContext("/v3/feed/market-data-feed/authorize", (exchange) -> {
			this.authorizationHeaders.add(exchange.getRequestHeaders().getFirst("Authorization"));
			int status = this.scriptedStatuses.isEmpty() ? 200 : this.scriptedStatuses.remove(0);
			byte[] body = "{\"status\":\"success\",\"data\":{\"authorized_redirect_uri\":\"ws://localhost:%d/feed?code=%d\"}}"
				.formatted(this.webSockets.getLocalPort(), this.codes.incrementAndGet())
				.getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(status, body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
		});
		this.http.start();
		Thread acceptor = new Thread(this::acceptLoop, "fake-upstox");
		acceptor.setDaemon(true);
		acceptor.start();
	}

	String baseUrl() {
		return "http://localhost:" + this.http.getAddress().getPort();
	}

	void respondToNextAuthorizeWith(int... statuses) {
		for (int status : statuses) {
			this.scriptedStatuses.add(status);
		}
	}

	void send(byte[] message) throws IOException {
		writeFrame(true, OP_BINARY, message);
	}

	void sendFragmented(byte[] message) throws IOException {
		int middle = message.length / 2;
		writeFrame(false, OP_BINARY, java.util.Arrays.copyOfRange(message, 0, middle));
		writeFrame(true, OP_CONTINUATION, java.util.Arrays.copyOfRange(message, middle, message.length));
	}

	void dropConnection() throws IOException {
		this.client.close();
	}

	private synchronized void writeFrame(boolean fin, int opcode, byte[] payload) throws IOException {
		OutputStream out = this.client.getOutputStream();
		out.write((fin ? 0x80 : 0) | opcode);
		if (payload.length < 126) {
			out.write(payload.length);
		}
		else {
			out.write(126);
			out.write(payload.length >> 8);
			out.write(payload.length);
		}
		out.write(payload);
		out.flush();
	}

	private void acceptLoop() {
		while (!this.webSockets.isClosed()) {
			try {
				Socket socket = this.webSockets.accept();
				DataInputStream in = new DataInputStream(socket.getInputStream());
				List<String> request = readHeaders(in);
				String requestLine = request.get(0);
				this.requestedCodes.add(requestLine.substring(requestLine.indexOf("code=") + 5).split(" ")[0]);
				shakeHands(socket, request);
				this.client = socket;
				Thread reader = new Thread(() -> readFrames(in), "fake-upstox-reader");
				reader.setDaemon(true);
				reader.start();
			}
			catch (IOException | RuntimeException ex) {
				return;
			}
		}
	}

	private static List<String> readHeaders(DataInputStream in) throws IOException {
		List<String> lines = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		int current;
		while ((current = in.read()) != -1) {
			if (current == '\n') {
				String text = line.toString().trim();
				if (text.isEmpty()) {
					return lines;
				}
				lines.add(text);
				line.setLength(0);
			}
			else {
				line.append((char) current);
			}
		}
		throw new IOException("Connection closed during handshake");
	}

	private static void shakeHands(Socket socket, List<String> request) throws IOException {
		String key = request.stream()
			.filter((header) -> header.toLowerCase().startsWith("sec-websocket-key:"))
			.map((header) -> header.substring(header.indexOf(':') + 1).trim())
			.findFirst()
			.orElseThrow();
		String accept;
		try {
			accept = Base64.getEncoder()
				.encodeToString(MessageDigest.getInstance("SHA-1")
					.digest((key + WEBSOCKET_GUID).getBytes(StandardCharsets.UTF_8)));
		}
		catch (java.security.NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
		String response = "HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
				+ "Sec-WebSocket-Accept: " + accept + "\r\n\r\n";
		socket.getOutputStream().write(response.getBytes(StandardCharsets.US_ASCII));
		socket.getOutputStream().flush();
	}

	private void readFrames(DataInputStream in) {
		try {
			while (true) {
				int opcode = in.readUnsignedByte() & 0x0F;
				int length = in.readUnsignedByte() & 0x7F;
				if (length == 126) {
					length = in.readUnsignedShort();
				}
				byte[] mask = in.readNBytes(4);
				byte[] payload = in.readNBytes(length);
				for (int i = 0; i < payload.length; i++) {
					payload[i] ^= mask[i % 4];
				}
				String text = new String(payload, StandardCharsets.UTF_8);
				if (opcode == OP_BINARY) {
					this.binaryFrames.add(text);
				}
				else if (opcode == OP_TEXT) {
					this.textFrames.add(text);
				}
			}
		}
		catch (IOException ex) {
			// The connection ended, which the tests cause on purpose.
		}
	}

	@Override
	public void close() throws IOException {
		this.http.stop(0);
		this.webSockets.close();
		if (this.client != null) {
			this.client.close();
		}
	}

}
