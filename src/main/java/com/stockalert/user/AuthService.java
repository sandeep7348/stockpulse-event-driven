package com.stockalert.user;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.stockalert.common.ApiException;
import com.stockalert.config.AppProperties;

@Service
public class AuthService {

	private static final int BCRYPT_MAX_BYTES = 72;

	private final AppUserRepository users;

	private final PasswordEncoder passwordEncoder;

	private final JwtEncoder jwtEncoder;

	private final Clock clock;

	private final Duration tokenTtl;

	private final String unknownUserHash;

	public AuthService(AppUserRepository users, PasswordEncoder passwordEncoder, JwtEncoder jwtEncoder, Clock clock,
			AppProperties properties) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.jwtEncoder = jwtEncoder;
		this.clock = clock;
		this.tokenTtl = properties.jwt().ttl();
		this.unknownUserHash = passwordEncoder.encode("placeholder-for-unknown-users");
	}

	public String register(String email, String password) {
		if (password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "password must be at most " + BCRYPT_MAX_BYTES + " bytes");
		}
		try {
			AppUser user = this.users.save(new AppUser(normalize(email), this.passwordEncoder.encode(password)));
			return issueToken(user);
		}
		catch (DuplicateKeyException ex) {
			throw new ApiException(HttpStatus.CONFLICT, "Email is already registered");
		}
	}

	public String login(String email, String password) {
		Optional<AppUser> user = this.users.findByEmail(normalize(email));
		// Hash even for unknown emails so response time does not reveal which accounts exist.
		String hash = user.map(AppUser::getPasswordHash).orElse(this.unknownUserHash);
		boolean matches = this.passwordEncoder.matches(password, hash);
		if (user.isEmpty() || !matches) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
		}
		return issueToken(user.get());
	}

	private String issueToken(AppUser user) {
		Instant now = this.clock.instant();
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.issuer("stock-alert-system")
			.subject(user.getId())
			.issuedAt(now)
			.expiresAt(now.plus(this.tokenTtl))
			.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		return this.jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}

	private static String normalize(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

}
