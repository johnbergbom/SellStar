package fi.jonix.huutonet.domain.model.dao;

import fi.jonix.huutonet.domain.model.Provider;

public interface ProviderDAO extends GenericDAO<Provider, Long> {
	
	Provider getProviderByName(String name);
	
}
