package fi.jonix.huutonet.market;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

import fi.jonix.huutonet.domain.model.Seller;

public class ListerTools {
	/*
	 * Generates as nice looking price with the following rules:
	 *   -1.51 -> 1.55
	 *   -1.54 -> 1.55
	 *   -1.55 -> 1.55
	 *   -1.56 -> 1.60
	 */
	public static BigDecimal generateNiceLookingPrice(BigDecimal price) {
		BigDecimal newPrice = price.multiply(new BigDecimal("2"));
		newPrice = newPrice.setScale(1,BigDecimal.ROUND_UP);
	    return newPrice.divide(new BigDecimal("2"),MathContext.DECIMAL64);
	}
	
	/**
	 * This method calculates the shipping cost based on the sales price of the product
	 * according to the following algorithm:
	 * Around 20% is added to the sales price for shipping. This number is then rounded
	 * to the nearest half decimal. However the maximum shipping price is 10.
	 * Examples:
	 * sales price | shipping cost
	 * ---------------------------
	 * 0.8         | 0.5
	 * 1.3         | 0.5
	 * 5.0         | 1.0
	 * 6.0         | 1.5
	 * 20.0        | 4.0
	 * 30.0        | 6.0
	 * 100.0       | 10.0
	 * 
	 * @param productSalesPrice
	 * @return
	 */
	public static BigDecimal generateCustomerShippingCostInclVat(Seller seller, BigDecimal salesPrice) {
		Double multiplier = seller.getShippingCostMultiplier().doubleValue();
		if (multiplier < 1 || multiplier > 1.5) {
			throw new RuntimeException("Faulty multiplier value: " + multiplier);
		}
		double shipPrice = salesPrice.doubleValue() * multiplier - salesPrice.doubleValue();
		shipPrice = Math.ceil(shipPrice*2) / 2;
		if (shipPrice > 10) {
			return new BigDecimal("10.0");
		} else {
			return new BigDecimal(shipPrice).round(new MathContext(1,RoundingMode.UP));
		}
	}

}
