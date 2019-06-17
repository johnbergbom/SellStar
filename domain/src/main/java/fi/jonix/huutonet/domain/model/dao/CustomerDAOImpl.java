package fi.jonix.huutonet.domain.model.dao;

import java.util.List;

import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Restrictions;

import fi.jonix.huutonet.domain.model.Customer;

public class CustomerDAOImpl extends GenericDAOImpl <Customer, Long> implements CustomerDAO {

	public Class<Customer> getEntityClass() {
		return Customer.class;
	}

	public Customer getCustomerByEmailAddress(String emailAddress) {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.add(Restrictions.eq("email",emailAddress).ignoreCase());
		List<Customer> list = this.getHibernateTemplate().findByCriteria(criteria);
		if (list != null && list.size() > 0) {
			/* There will never be more than one customer here, because email
			 * is unique in the database. */
			return list.get(0);
		} 
		return null;
	}

	public Customer getCustomerByConfirmationCode(String confirmationCode) {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.add(Restrictions.eq("confirmationCode",confirmationCode));
		List<Customer> list = this.getHibernateTemplate().findByCriteria(criteria);
		if (list != null && list.size() > 0) {
			/* There will never be more than one customer here, because confirmationCode
			 * is unique in the database. */
			return list.get(0);
		} 
		return null;
	}

}
