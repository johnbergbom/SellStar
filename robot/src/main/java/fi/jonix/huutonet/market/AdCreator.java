package fi.jonix.huutonet.market;

import fi.jonix.huutonet.domain.model.Ad;
import fi.jonix.huutonet.domain.model.AdTemplate;
import fi.jonix.huutonet.domain.model.Category;
import fi.jonix.huutonet.domain.model.SellerMarket;
import fi.jonix.huutonet.order.Price;

public interface AdCreator {
	
	public Ad createAd(AdTemplate adTemplate, Category marketCategory, Price currentProviderPrice,
			ListerStrategy strategy, SellerMarket sellerMarket) throws Exception;
	
}
