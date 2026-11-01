package com.stockalert.alert;

public enum Direction {

	ABOVE, BELOW;

	public boolean isCrossed(long targetPaise, long pricePaise) {
		return (this == ABOVE) ? pricePaise >= targetPaise : pricePaise <= targetPaise;
	}

}
