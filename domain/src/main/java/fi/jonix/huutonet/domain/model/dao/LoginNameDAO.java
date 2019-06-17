package fi.jonix.huutonet.domain.model.dao;

import fi.jonix.huutonet.domain.model.Customer;
import fi.jonix.huutonet.domain.model.LoginName;
import fi.jonix.huutonet.domain.model.Market;

public interface LoginNameDAO extends GenericDAO<LoginName, Long> {

	public LoginName getByCustomerAndMarket(Customer customer, Market market);
	public LoginName getByLoginNameAndMarket(String loginName, Market market);

}
