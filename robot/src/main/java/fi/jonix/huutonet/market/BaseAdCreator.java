package fi.jonix.huutonet.market;

import java.math.BigDecimal;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import fi.jonix.huutonet.domain.VirtualCoin;
import fi.jonix.huutonet.domain.model.Ad;
import fi.jonix.huutonet.domain.model.AdTemplate;
import fi.jonix.huutonet.domain.model.Category;
import fi.jonix.huutonet.domain.model.Currency;
import fi.jonix.huutonet.domain.model.SellerMarket;
import fi.jonix.huutonet.domain.model.dao.CurrencyDAO;
import fi.jonix.huutonet.domain.model.dao.SellerMarketDAO;
import fi.jonix.huutonet.market.huuto.HuutonetLister2;
import fi.jonix.huutonet.order.Price;
import fi.jonix.huutonet.tools.MathUtilities;

@Component(value = "baseAdCreator")
public class BaseAdCreator implements AdCreator {

	public static final Logger logger = Logger.getLogger(BaseAdCreator.class);
	
	@Autowired
	private CurrencyDAO currencyDAO;

	@Autowired
	private SellerMarketDAO sellerMarketDAO;

	/* Calculate the minimum sales price like this:
	 * 1.) find out the provider price exclVat in market currency
	 * 2.) find out an approximate sales price exclVat by multiplying #1 with the sales margin
	 * 3.) find out an approximate sales price inclVat by adding vat to #2
	 * 4.) find out the final sales price by turning #3 into a nice number
	 * The reason for making this so complicated is that we want to make sure that
	 * we can also handle the case where the vat percentage that we need to pay to the
	 * provider is different than the vat percentage that we get from our customers.
	 * Therefore the sales margin needs to be calculated on the price EXCLUDING vat.
	 * 
	 * For example: provider vat percentage = 50%, provider price excl. vat = 100,
	 * provider price incl. vat = 150. Market place vat percentage = 23%. Now we
	 * can actually sell the product for the same price that we pay to the provider
	 * and still make profit since the vat percentage of the market place is lower
	 * than that of the provider. If we instead had calculated the price inluding
	 * vat, then our sales price would have needed to be greater than 150. */
	public BigDecimal calculateMinSellingPrice(SellerMarket sellerMarket, Price currentProviderPrice, BigDecimal providerVatPercentage) {
		Long marketCurr = sellerMarket.getMarket().getCurrency().getId();
		Long provCurr = currentProviderPrice.getCurrency();
		BigDecimal minSellingPriceInclVatMC = null;
		if (sellerMarket.getSeller().getMargin().compareTo(new BigDecimal("1.00")) == 0 && marketCurr.equals(provCurr)
				&& sellerMarket.getSeller().getCustomerVatPercentage().compareTo(providerVatPercentage) == 0) {
			/* Simple case: no conversions need to be done. */
			minSellingPriceInclVatMC = currencyDAO.convertCurrency(provCurr, marketCurr, currentProviderPrice.getPriceInclVat().getValue());
		} else {
			BigDecimal providerPriceExclVatMC = currencyDAO.convertCurrency(provCurr, marketCurr, currentProviderPrice.getPriceExclVat().getValue());
			BigDecimal crudeCustomerPriceExclVatMC = providerPriceExclVatMC.multiply(sellerMarket.getSeller().getMargin());
			BigDecimal crudeCustomerPriceInclVatMC = MathUtilities.getPriceInclVat(crudeCustomerPriceExclVatMC, sellerMarket.getSeller().getCustomerVatPercentage());
			minSellingPriceInclVatMC = ListerTools.generateNiceLookingPrice(crudeCustomerPriceInclVatMC);
		}
		return minSellingPriceInclVatMC;
	}
	
	public Ad createAd(AdTemplate adTemplate, Category marketCategory, Price currentProviderPrice,
			ListerStrategy strategy, SellerMarket sellerMarket) throws Exception {
		sellerMarketDAO.refresh(sellerMarket);
		if (!marketCategory.getMarket().getId().equals(sellerMarket.getMarket().getId())) {
			throw new RuntimeException("marketCategory.market != sellerMarket.market");
		}

		BigDecimal minSellingPriceInclVatMC = calculateMinSellingPrice(sellerMarket,
				currentProviderPrice, adTemplate.getProduct().getProvider().getVatPercentage());
		BigDecimal startPriceInclVatMC = minSellingPriceInclVatMC.subtract(sellerMarket.getMinBidIncreaseInMarketCurrencyInclVat());

		Ad ad = new Ad();
		ad.setMarket(marketCategory.getMarket());
		ad.setStartPriceInMarketCurrencyInclVat(startPriceInclVatMC);
		ad.setMinSellingPriceInMarketCurrencyInclVat(minSellingPriceInclVatMC);
		Long marketCurr = sellerMarket.getMarket().getCurrency().getId();
		BigDecimal startPriceInclVatEuro = currencyDAO.convertCurrency(marketCurr, Currency.EURO, startPriceInclVatMC);
		ad.setStartPriceInEuroInclVat(startPriceInclVatEuro);
		BigDecimal minSellingPriceInclVatEuro = currencyDAO.convertCurrency(marketCurr, Currency.EURO, minSellingPriceInclVatMC);
		ad.setMinSellingPriceInEuroInclVat(minSellingPriceInclVatEuro);
		ad.setProviderPriceAtListingTimeProviderCurrencyInclVat(currentProviderPrice.getPriceInclVat().getValue());
		BigDecimal providerPriceInclVatEuro = currencyDAO.convertCurrency(currentProviderPrice.getCurrency(),
				Currency.EURO, currentProviderPrice.getPriceInclVat().getValue());
		ad.setProviderPriceAtListingTimeEuroInclVat(providerPriceInclVatEuro);
		ad.setAdTemplate(adTemplate);
		ad.setConfirmed(false); //set to true vasta when a confirmation email comes from the market
		ad.setMarketSalesId("temp"); //this cannot be null, the correct value is set at listing time

		/* Use the shipping cost of seller, or if that is null, then generate a shipping cost. */
		VirtualCoin shippingCostInclVatMC = strategy.getCustomerShippingCostInclVat(adTemplate.getProduct(),sellerMarket);
		/*if (shippingCostInclVatMC == null) {
			BigDecimal generatedShipCostInclVat = ListerTools.generateCustomerShippingCostInclVat(sellerMarket.getSeller(),
					ad.getStartPriceInMarketCurrencyInclVat());
			shippingCostInclVatMC = new VirtualCoin(marketCurr,generatedShipCostInclVat);
		}*/
		ad.setShippingCostInMarketCurrencyInclVat(shippingCostInclVatMC.getValue());
		BigDecimal shippingCostInclVatEuro = currencyDAO.convertCurrency(marketCurr, Currency.EURO, shippingCostInclVatMC.getValue());
		ad.setShippingCostInEuroInclVat(shippingCostInclVatEuro);
		ad.setMarketCategory(marketCategory);
		ad.setNumberOfPublicQuestions(0);
		ad.setVisibilityType(adTemplate.getVisibilityType());
		//ad.setVisibilityType(0);
		ad.setBoughtAmount(0);
		ad.setOpenDays(adTemplate.getOpenDays()); //this value might get overridden by the strategy
		ad.setOriginalAmount(adTemplate.getQuantity()); //this value might get overridden by the strategy
		ad.setSalesType(HuutonetLister2.SALES_TYPE_AUCTION_OR_BUY); //this value might get overridden by the strategy
		ad.setAdvertisementCostInEuroExclVat(BigDecimal.ZERO); //this one might get changed by the lister in case this is paid ad
		ad.setAdvertisementCostInEuroInclVat(BigDecimal.ZERO); //this one might get changed by the lister in case this is paid ad
		ad.setTransferedToVerification(false);
		return ad;
	}

}
