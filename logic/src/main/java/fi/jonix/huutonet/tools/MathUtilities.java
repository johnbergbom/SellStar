package fi.jonix.huutonet.tools;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.Random;

public class MathUtilities {

	/*public static double roundTo2Decimals(double d) {
		//TODO: Ev. använd DecimalFormat.format(d) för detta istället, och returnera sedan en sträng,
		//för ifall det finns avrundningsfel i d så kan även det returnerade värdet innehålla avrundningsfel
		//ifall man även returnerar en double. (utan istället bör man alltså ev. returnera en sträng).
		//Nått i den här stilen kan ev. funka:
		//DecimalFormat twoDForm = new DecimalFormat("#.##");
		// return twoDForm.format(d);
		//eller gör så här:
		//DecimalFormat df = new DecimalFormat();
		//df.setMaximumFractionDigits(2);
		//df.format(8.76543);
		long i = Math.round(d*100);
		return (double) i / 100;
	}*/

	public static String roundTo2Decimals(BigDecimal bd) {
		DecimalFormat df = new DecimalFormat("0.##");
		return df.format(bd.doubleValue());
	}
	
	public static String getRandomLongNumber(int length) {
		Random rand = new Random(System.currentTimeMillis());
		StringBuffer strBuf = new StringBuffer();
		while (true) {
			strBuf.append(Math.abs(rand.nextInt()));
			if (strBuf.length() > length) {
				break;
			}
		}
		return strBuf.toString().substring(0,length);
	}

	public static int getRandomNumberBetween(int lowestInclusive, int highestInclusive) {
		int adjustedHighest = highestInclusive - lowestInclusive;
		// Don't use the current time as a seed because then we'll get
		// the same value if invoking this method several times in rapid succession.
		Random random = new Random();
		return random.nextInt(adjustedHighest+1) + lowestInclusive;
	}

	public static BigDecimal getPriceExclVat(BigDecimal priceInclVat, BigDecimal vatPercentage) {
		if (vatPercentage.compareTo(BigDecimal.ZERO) == 0) {
			return priceInclVat;
		} else if (vatPercentage.compareTo(new BigDecimal("0.20")) < 0 || vatPercentage.compareTo(new BigDecimal("0.30")) > 0) {
			throw new RuntimeException("Faulty vatPercentage: " + vatPercentage.toPlainString());
		}
		BigDecimal exclVat = priceInclVat.divide(vatPercentage.add(BigDecimal.ONE),MathContext.DECIMAL64);
		exclVat = exclVat.setScale(2,RoundingMode.HALF_UP);
		return exclVat;
	}

	public static BigDecimal getPriceInclVat(BigDecimal priceExclVat, BigDecimal vatPercentage) {
		if (vatPercentage.compareTo(BigDecimal.ZERO) == 0) {
			return priceExclVat;
		} else if (vatPercentage.compareTo(new BigDecimal("0.20")) < 0 || vatPercentage.compareTo(new BigDecimal("0.30")) > 0) {
			throw new RuntimeException("Faulty vatPercentage: " + vatPercentage.toPlainString());
		}
		BigDecimal inclVat = vatPercentage.add(BigDecimal.ONE).multiply(priceExclVat);
		inclVat = inclVat.setScale(2,RoundingMode.HALF_DOWN);
		return inclVat;
	}

}
