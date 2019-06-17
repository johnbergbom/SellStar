package fi.jonix.huutonet.domain.model.dao;

import java.math.BigDecimal;

import fi.jonix.huutonet.domain.model.Currency;

public interface CurrencyDAO extends GenericDAO<Currency, Long> {

	public Currency getByName(String name);
	public BigDecimal convertCurrency(Long sourceCurrency, Long targetCurrency, BigDecimal amount);
	public BigDecimal convertCurrency(Currency sourceCurrency, Currency targetCurrency, BigDecimal amount);

}
