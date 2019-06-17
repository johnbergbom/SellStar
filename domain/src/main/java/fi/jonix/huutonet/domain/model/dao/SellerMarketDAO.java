package fi.jonix.huutonet.domain.model.dao;

import fi.jonix.huutonet.domain.model.Market;
import fi.jonix.huutonet.domain.model.Seller;
import fi.jonix.huutonet.domain.model.SellerMarket;

public interface SellerMarketDAO extends GenericDAO<SellerMarket, Long> {

	public SellerMarket getBySellerAndMarket(Seller seller, Market market);
	
}
