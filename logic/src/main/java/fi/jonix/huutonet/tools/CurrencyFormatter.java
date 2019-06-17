package fi.jonix.huutonet.tools;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

import fi.jonix.huutonet.domain.ApplicationContextPlaceholder;
import fi.jonix.huutonet.domain.VirtualCoin;
import fi.jonix.huutonet.domain.model.Currency;
import fi.jonix.huutonet.domain.model.dao.CurrencyDAO;

/**
 * This is a dummy class for formating currencies. In spring 3 there
 * is a full fledged implementation of this. When we move to spring 3
 * this class can be removed.
 * => hmm, in java core there are the NumberFormat and Currency classes
 * that can probably be used here:
 * NumberFormat currencyFormatter = NumberFormat.getCurrencyInstance(Statics.FINNISH);
 * 
 * @author john
 *
 */
public class CurrencyFormatter {
	
	/*public static String print(Double d, Locale locale) {
		return MathUtilities.roundTo2Decimals(new BigDecimal(d)).replace('.',',') + " €";
	}*/

	/*public static String print(BigDecimal d, Locale locale) {
		if (d.compareTo(BigDecimal.ZERO) == 0) {
			//Make sure that 0,00 is printed out as "0,00 €" and not as "0 €".
			return "0,00 €";
		} else {
			return MathUtilities.roundTo2Decimals(d).replace('.',',') + " €";
		}
	}*/

	public static String print(VirtualCoin virtualCoin, Locale locale) {
		String str = null;
		if (virtualCoin.getValue().compareTo(BigDecimal.ZERO) == 0) {
			//Make sure that 0,00 is printed out as "0,00 €" and not as "0 €".
			str = "0,00";
		} else {
			str = MathUtilities.roundTo2Decimals(virtualCoin.getValue()).replace('.',',');
		}
		CurrencyDAO currencyDAO = (CurrencyDAO) ApplicationContextPlaceholder.applicationContext.getBean("currencyDAO");
		Currency currency = currencyDAO.get(virtualCoin.getCurrency());
		return str + " " + currency.getSymbol();
	}

}
