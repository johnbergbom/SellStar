package fi.jonix.huutonet.order;

import java.math.BigDecimal;

import fi.jonix.huutonet.domain.VirtualCoin;
import fi.jonix.huutonet.domain.model.Currency;
import fi.jonix.huutonet.tools.MathUtilities;

public class Price {

	private VirtualCoin priceInclVat;
	private VirtualCoin priceExclVat;
	private BigDecimal vatPercentage;
	
	public Price(VirtualCoin priceInclVat, VirtualCoin priceExclVat, BigDecimal vatPercentage) {
		if (!priceInclVat.getCurrency().equals(priceExclVat.getCurrency())) {
			throw new RuntimeException("Currency for priceInclVat and priceExclVat differ.");
		}
		if (priceInclVat == null || priceExclVat == null) {
			throw new RuntimeException("priceInclVat or priceExclVat is null");
		}
		
		/* Make sure that the correct vat percentage is used. */
		if (MathUtilities.getPriceExclVat(priceInclVat.getValue(), vatPercentage).compareTo(priceExclVat.getValue()) != 0
				&& MathUtilities.getPriceInclVat(priceExclVat.getValue(), vatPercentage).compareTo(priceInclVat.getValue()) != 0) {
			throw new RuntimeException("Wrong vat percentage: incl. vat according to argument: "
					+ priceInclVat.getValue().toPlainString() + " => with vat " + vatPercentage + " the"
					+ " price excl. vat should be "
					+ MathUtilities.getPriceExclVat(priceInclVat.getValue(), vatPercentage).toPlainString()
					+ ", excl. vat according to argument: " + priceExclVat.getValue().toPlainString() + " => with vat "
					+ vatPercentage + " the price incl. vat should be "
					+ MathUtilities.getPriceInclVat(priceExclVat.getValue(), vatPercentage));
		}
		
		this.priceInclVat = priceInclVat;
		this.priceExclVat = priceExclVat;
		this.vatPercentage = vatPercentage;
	}

	public Price(BigDecimal price, boolean includesVat, Currency currency, BigDecimal vatPercentage) {
		this(new VirtualCoin(currency,price),includesVat,vatPercentage);
	}

	public Price(VirtualCoin price, boolean includesVat, BigDecimal vatPercentage) {
		this.vatPercentage = vatPercentage;
		if (includesVat) {
			this.priceInclVat = price;
			this.priceExclVat = new VirtualCoin(price.getCurrency(),MathUtilities.getPriceExclVat(priceInclVat.getValue(), vatPercentage));
		} else {
			this.priceExclVat = price;
			this.priceInclVat = new VirtualCoin(price.getCurrency(),MathUtilities.getPriceInclVat(priceExclVat.getValue(), vatPercentage));
		}
	}
	
	public void setPriceInclVat(VirtualCoin priceInclVat) {
		this.priceInclVat = priceInclVat;
	}

	public VirtualCoin getPriceInclVat() {
		return priceInclVat;
	}

	public void setPriceExclVat(VirtualCoin priceExclVat) {
		this.priceExclVat = priceExclVat;
	}

	public VirtualCoin getPriceExclVat() {
		return priceExclVat;
	}

	public void setVatPercentage(BigDecimal vatPercentage) {
		this.vatPercentage = vatPercentage;
	}

	public BigDecimal getVatPercentage() {
		return vatPercentage;
	}
	
	public long getCurrency() {
		return priceInclVat.getCurrency();
	}

}
