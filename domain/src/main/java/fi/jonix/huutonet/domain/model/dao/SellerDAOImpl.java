package fi.jonix.huutonet.domain.model.dao;

import java.util.List;

import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Restrictions;

import fi.jonix.huutonet.domain.model.Seller;
import fi.jonix.huutonet.domain.model.User;

public class SellerDAOImpl extends GenericDAOImpl <Seller, Long> implements SellerDAO {

	@Override
	public Class<Seller> getEntityClass() {
		return Seller.class;
	}

	public List<Seller> getSellersByUser(User user) {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.add(Restrictions.eq("user", user));
		return this.getHibernateTemplate().findByCriteria(criteria);
	}
	
	/*public Seller getFirstSellerByEmailAddress(String emailAddress) {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.createAlias("systemEmail", "s");
		criteria.add(Restrictions.eq("s.emailAddress",emailAddress.toLowerCase()));
		List<Seller> list = this.getHibernateTemplate().findByCriteria(criteria);
		if (list != null && list.size() > 0) {
			/* Here there can be several sellers, but let's return the first one. /
			return list.get(0);
		} 
		return null;
	}*/

}
