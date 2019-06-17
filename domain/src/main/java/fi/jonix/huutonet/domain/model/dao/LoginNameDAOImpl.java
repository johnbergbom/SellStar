package fi.jonix.huutonet.domain.model.dao;

import java.util.List;

import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Restrictions;

import fi.jonix.huutonet.domain.model.Customer;
import fi.jonix.huutonet.domain.model.LoginName;
import fi.jonix.huutonet.domain.model.Market;

public class LoginNameDAOImpl extends GenericDAOImpl <LoginName, Long> implements LoginNameDAO {

	@Override
	public Class<LoginName> getEntityClass() {
		return LoginName.class;
	}

	public LoginName getByCustomerAndMarket(Customer customer, Market market) {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.add(Restrictions.eq("customer",customer));
		criteria.add(Restrictions.eq("market",market));
		List<LoginName> list = this.getHibernateTemplate().findByCriteria(criteria);
		if (list != null && list.size() > 0) {
			/* There will never be more than one login name here, because customer/market
			 * are declared unique. */
			return list.get(0);
		} 
		return null;
	}

	public LoginName getByLoginNameAndMarket(String loginName, Market market) {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.add(Restrictions.eq("loginName",loginName));
		criteria.add(Restrictions.eq("market",market));
		List<LoginName> list = this.getHibernateTemplate().findByCriteria(criteria);
		if (list != null && list.size() > 0) {
			/* There will never be more than one login name here, because loginName/market
			 * are declared unique. */
			return list.get(0);
		} 
		return null;
	}

}
