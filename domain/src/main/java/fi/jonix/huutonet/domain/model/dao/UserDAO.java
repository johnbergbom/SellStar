package fi.jonix.huutonet.domain.model.dao;

import java.util.List;

import fi.jonix.huutonet.domain.model.User;

public interface UserDAO extends GenericDAO<User, Long> {

	public User getByLoginName(String loginName);
	public User getByLoginName(String loginName, String plainTextPassword);
	public List<User> getOperators();
	
}
