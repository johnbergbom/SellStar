package fi.jonix.huutonet.market;

import fi.jonix.huutonet.domain.VirtualCoin;
import fi.jonix.huutonet.domain.model.Ad;
import fi.jonix.huutonet.domain.model.Product;
import fi.jonix.huutonet.domain.model.SellerMarket;
import fi.jonix.huutonet.exception.ListingException;

public interface ListerStrategy {
	
	public void improveAd(Ad ad, SellerMarket sellerMarket, int nbrSold);
	
	//public String getShippingDescription(Ad ad);
	public VirtualCoin getCustomerShippingCostInclVat(Product product, SellerMarket sellerMarket) throws ListingException;
	public String getLocation(Ad ad);
	
}
