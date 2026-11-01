package com.stockalert.alert;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DirectionTests {

	@Test
	void aboveIsCrossedAtOrOverTarget() {
		assertThat(Direction.ABOVE.isCrossed(100, 99)).isFalse();
		assertThat(Direction.ABOVE.isCrossed(100, 100)).isTrue();
		assertThat(Direction.ABOVE.isCrossed(100, 101)).isTrue();
	}

	@Test
	void belowIsCrossedAtOrUnderTarget() {
		assertThat(Direction.BELOW.isCrossed(100, 101)).isFalse();
		assertThat(Direction.BELOW.isCrossed(100, 100)).isTrue();
		assertThat(Direction.BELOW.isCrossed(100, 99)).isTrue();
	}

}
