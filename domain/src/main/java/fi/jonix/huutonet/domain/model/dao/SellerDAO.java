package fi.jonix.huutonet.domain.model.dao;

import java.util.List;

import fi.jonix.huutonet.domain.model.Seller;
import fi.jonix.huutonet.domain.model.User;

public interface SellerDAO extends GenericDAO<Seller, Long> {

	public List<Seller> getSellersByUser(User user);
	
	//public Seller getFirstSellerByEmailAddress(String emailAddress);
	
}
