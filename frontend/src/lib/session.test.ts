import { describe, expect, it } from 'vitest';

import { decodeToken, formatRemaining, toSession } from './session';

const token = (claims: object) => `header.${btoa(JSON.stringify(claims)).replace(/=+$/, '')}.signature`;

describe('toSession', () => {
  it('reads the user id and expiry from the token', () => {
    expect(toSession(token({ sub: 'user-1', exp: 1000 }), 'a@example.test')).toEqual({
      token: token({ sub: 'user-1', exp: 1000 }),
      email: 'a@example.test',
      userId: 'user-1',
      expiresAt: 1_000_000,
    });
  });

  it('rejects tokens without an expiry or that cannot be read', () => {
    expect(toSession(token({ sub: 'user-1' }), 'a@example.test')).toBeNull();
    expect(decodeToken('not-a-token')).toBeNull();
  });
});

describe('formatRemaining', () => {
  it('rounds up so a session never shows 0m while still valid', () => {
    expect(formatRemaining(1000)).toBe('1m');
    expect(formatRemaining(90 * 60_000)).toBe('1h 30m');
    expect(formatRemaining(-5)).toBe('0m');
  });
});
