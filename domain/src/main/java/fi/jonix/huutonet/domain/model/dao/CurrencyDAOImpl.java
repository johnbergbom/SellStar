package fi.jonix.huutonet.domain.model.dao;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Calendar;
import java.util.List;

import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Restrictions;
import org.apache.log4j.Logger;

import fi.jonix.huutonet.domain.ApplicationContextPlaceholder;
import fi.jonix.huutonet.domain.model.Currency;

public class CurrencyDAOImpl extends GenericDAOImpl<Currency, Long> implements CurrencyDAO {

	public static final Logger logger = Logger.getLogger(CurrencyDAOImpl.class);

	@Override
	public Class<Currency> getEntityClass() {
		return Currency.class;
	}

	public Currency getByName(String name) {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.add(Restrictions.eq("name", name));
		List<Currency> list = this.getHibernateTemplate().findByCriteria(criteria);
		if (list == null || list.size() == 0) {
			return null;
		} 
		/* There will never be more than one currency here, because name
		 * is unique in the database. */
		return list.get(0);
	}

	/**
	 * This method converts between different currencies.
	 * 
	 * @param sourceCurrency
	 * @param targetCurrency
	 * @param amount
	 * @return Value in target currency.
	 */
	public BigDecimal convertCurrency(Long sourceCurrency, Long targetCurrency, BigDecimal amount) {
		return convertCurrency(get(sourceCurrency), get(targetCurrency), amount);
	}
	
	public BigDecimal convertCurrency(Currency sourceCurrency, Currency targetCurrency, BigDecimal amount) {
		if (sourceCurrency.equals(targetCurrency)) {
			return amount;
		}
		/* Our base currency is euro, so if neither currency is euro, then we first
		 * need to convert the source currency to euro. */
		if (!sourceCurrency.getId().equals(Currency.EURO) && !targetCurrency.getId().equals(Currency.EURO)) {
			//CurrencyDAO currencyDAO = (CurrencyDAO) ApplicationContextPlaceholder.applicationContext.getBean("currencyDAO");
			Currency currEuro = get(Currency.EURO);
			amount = convertCurrency(sourceCurrency, currEuro, amount);
			sourceCurrency = currEuro;
		}

		/* Print out a warning if we are using too old exchange rates. */
		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.DATE,-1);
		if (cal.getTime().getTime() > sourceCurrency.getUpdateDate().getTime()
				|| cal.getTime().getTime() > targetCurrency.getUpdateDate().getTime()) {
			logger.warn("Using too old exchange rates (last fetched: source = "
					+ sourceCurrency.getUpdateDate() + ", target = "
					+ targetCurrency.getUpdateDate() + ").");
		}
		
		if (targetCurrency.getId().equals(Currency.EURO)) {
			return amount.divide(sourceCurrency.getExchangeRateToEuro(),MathContext.DECIMAL64);
		} else { //if (sourceCurr.getId().equals(Currency.EURO)) {
			return amount.multiply(targetCurrency.getExchangeRateToEuro());
		}
	}
	
}
