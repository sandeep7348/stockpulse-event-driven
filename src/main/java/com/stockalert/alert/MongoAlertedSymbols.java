package com.stockalert.alert;

import java.util.Collection;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import com.stockalert.market.AlertedSymbols;

@Component
public class MongoAlertedSymbols implements AlertedSymbols {

	private final MongoTemplate mongo;

	public MongoAlertedSymbols(MongoTemplate mongo) {
		this.mongo = mongo;
	}

	@Override
	public Collection<String> symbols() {
		return this.mongo.findDistinct(Query.query(Criteria.where("status").is(AlertStatus.ACTIVE)), "symbol",
				Alert.class, String.class);
	}

}
