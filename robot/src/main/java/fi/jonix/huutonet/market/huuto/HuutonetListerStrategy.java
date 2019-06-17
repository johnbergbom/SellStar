package fi.jonix.huutonet.market.huuto;

import java.math.BigDecimal;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import fi.jonix.huutonet.domain.VirtualCoin;
import fi.jonix.huutonet.domain.model.Ad;
import fi.jonix.huutonet.domain.model.Currency;
import fi.jonix.huutonet.domain.model.Product;
import fi.jonix.huutonet.domain.model.SellerMarket;
import fi.jonix.huutonet.domain.model.dao.CurrencyDAO;
import fi.jonix.huutonet.domain.model.dao.SettingsDAO;
import fi.jonix.huutonet.exception.ListingException;
import fi.jonix.huutonet.market.ListerStrategy;
import fi.jonix.huutonet.market.ListerTools;
import fi.jonix.huutonet.tools.MathUtilities;

@Component(value = "strategy")
public class HuutonetListerStrategy implements ListerStrategy {
	
	public static final Logger logger = Logger.getLogger(HuutonetListerStrategy.class);

	@Autowired
	private CurrencyDAO currencyDAO;

	public void improveAd(Ad ad, SellerMarket sellerMarket, int nbrSold) {
		/* Override the quantity for ads going to the display window. */
		if (ad.getVisibilityType() > 0) {
			/* Use a random number between 30 and 100. Reason: it looks like
			 * some items are already bought if we don't use an even number. */
			//ad.setOriginalAmount(50);
			ad.setOriginalAmount(MathUtilities.getRandomNumberBetween(30,100));
		}
		
		/* Use buyout-only for ads whose quantity > 1, or otherwise use
		 * the default. The reason for only using buyout if quantity > 1
		 * is that the "won bid" emails for this type are a little different
		 * and HuutonetProcessor doesn't yet support those (see also
		 * HuutonetProcessor.processWonBidMulti regarding these).
		 * 
		 * In addition it was decided that products going to the display window
		 * should always have buyout-only and this in effect does this since
		 * products going to the display window always have a quantity > 1.
		 * 
		 * In addition: huutonet doesn't allow "auction AND buyout" if the
		 * quantity is greater than one. */
		if (ad.getOriginalAmount() > 1) {
			ad.setSalesType(HuutonetLister2.SALES_TYPE_BUY);
		}

		/* Set the open days of the products: 
		 * If the product is a display window product: 16 days
		 * If the product is never sold: 7 days
		 * If the product is sold 1 time: 5 days
		 * If the product is sold 2-10 times: 4 days
		 * If the product is sold > 10 times: 3 days */
		if (ad.getVisibilityType() > 0) {
			ad.setOpenDays(16);
		} else if (nbrSold == 0) {
			ad.setOpenDays(7);
		} else if (nbrSold == 1) {
			ad.setOpenDays(5);
		} else if (nbrSold <= 10) {
			ad.setOpenDays(4);
		} else {
			ad.setOpenDays(3);
		}

		BigDecimal buyoutPriceInclVatMC = calculateBuyOutPrice(ad.getMinSellingPriceInMarketCurrencyInclVat(),
				sellerMarket.getBuyoutPriceMultiplier());
		BigDecimal buyoutPriceInclVatEuro = calculateBuyOutPrice(ad.getMinSellingPriceInEuroInclVat(),
				sellerMarket.getBuyoutPriceMultiplier());
		ad.setBuyOutPriceInMarketCurrencyInclVat(buyoutPriceInclVatMC);
		ad.setBuyOutPriceInEuroInclVat(buyoutPriceInclVatEuro);
	}
	
	private BigDecimal calculateBuyOutPrice(BigDecimal minSellingPrice, BigDecimal buyOutMultiplier) {
		BigDecimal buyoutPriceInclVatMC = minSellingPrice;
		if (buyOutMultiplier.compareTo(new BigDecimal("1.00")) != 0) {
			buyoutPriceInclVatMC = ListerTools.generateNiceLookingPrice(minSellingPrice.multiply(buyOutMultiplier));
		}
		return buyoutPriceInclVatMC;
	}
	
	public String getLocation(Ad ad) {
		return ad.getAdTemplate().getProduct().getProvider().getLocation();
	}

	public VirtualCoin getCustomerShippingCostInclVat(Product product, SellerMarket sellerMarket) throws ListingException {
		BigDecimal minProvShippingCostInclVatMC = currencyDAO.convertCurrency(product.getProvider().getCurrency(),
				sellerMarket.getMarket().getCurrency(), product.getMinProviderShippingCostProviderCurrencyIncVat());
		BigDecimal minSellerMarketShipCostInclVatMC = sellerMarket.getMinShippingCostMarketCurrInclVat();

		/* Return the biggest one of these. */
		BigDecimal shippingCostIncVatMC = minProvShippingCostInclVatMC.max(minSellerMarketShipCostInclVatMC); 

		/* For some of the products for Hautala it wasn't possible to get the shipping
		 * cost (product.min_prov_ship_cost_pc_inc_vat). For those products the shipping
		 * cost was set to 1000. Here we make sure that those products aren't listed.
		 * =>Figure out what the shipping cost is for those products so that
		 * we can also list those ones. When that's done, then this error check can still
		 * be here, but then we can rather go to the error state instead of just throwing
		 * a listing exception. => UPDATE: Hautala's products are no longer for sale, so
		 * this is no longer current. */
		BigDecimal shippingCostInclVatEuro = currencyDAO.convertCurrency(sellerMarket.getMarket().getCurrency().getId(),
				Currency.EURO, shippingCostIncVatMC);
		if (shippingCostInclVatEuro.compareTo(new BigDecimal("500.00")) > 0) {
			logger.debug("Too high shipping cost for product " + product.getId() + ". Skipping listing.");
			throw new ListingException("Too high shipping cost for product " + product.getId()
					+ ". Skipping listing.",false);
		}
		
		return new VirtualCoin(sellerMarket.getMarket().getCurrency(),shippingCostIncVatMC);
	}

	/*public String getShippingDescription(Ad ad) {
		//TODO: the limit where the tracking code is automatically included varies between different
		//providers so we need to add a new field provider.auto_tracking_limit and use it like this:
		//if (ad.getProviderPriceAtListingTimeProviderCurrency().compareTo(ad.getAdTemplate().getProduct().getProvider().getAutoTrackingLimit()) > 0) {
		String shippingDescription = "";
		if (ad.getProviderPriceAtListingTimeProviderCurrency().compareTo(new BigDecimal("15.01")) > 0) {
			shippingDescription = settingsDAO.getValue(SHIPPING_DESCRIPTION_REGISTERED_MAIL);
		} else {
			shippingDescription = settingsDAO.getValue(SHIPPING_DESCRIPTION_AIR_MAIL);
		}
		shippingDescription += "\n\n" + settingsDAO.getValue(SHIPPING_DESCRIPTION);
		return shippingDescription;
	}*/
	
}
