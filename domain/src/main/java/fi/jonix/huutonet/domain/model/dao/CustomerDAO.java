package fi.jonix.huutonet.domain.model.dao;

import fi.jonix.huutonet.domain.model.Customer;

public interface CustomerDAO extends GenericDAO<Customer, Long> {

	public Customer getCustomerByEmailAddress(String emailAddress);
	public Customer getCustomerByConfirmationCode(String confirmationCode);

}
