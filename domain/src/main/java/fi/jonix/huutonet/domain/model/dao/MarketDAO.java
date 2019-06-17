package fi.jonix.huutonet.domain.model.dao;

import fi.jonix.huutonet.domain.model.Market;

public interface MarketDAO extends GenericDAO<Market, Long> {

	public Market getByName(String name);
	
}
