package fi.jonix.huutonet.domain;

import java.math.BigDecimal;

import fi.jonix.huutonet.domain.model.Currency;

public class VirtualCoin {

	private long currency;
	private BigDecimal value;
	
	public VirtualCoin(Currency currency, BigDecimal value) {
		this(currency.getId(),value);
	}

	public VirtualCoin(long currency, BigDecimal value) {
		this.setCurrency(currency);
		this.setValue(value);
	}

	public void setCurrency(long currency) {
		this.currency = currency;
	}

	public Long getCurrency() {
		return currency;
	}

	public void setValue(BigDecimal value) {
		this.value = value;
	}

	public BigDecimal getValue() {
		return value;
	}
	
	public String toString() {
		return "Currency: " + currency + ", value = " + value;
	}
	
}
