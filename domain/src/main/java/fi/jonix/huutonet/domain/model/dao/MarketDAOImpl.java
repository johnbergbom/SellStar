package fi.jonix.huutonet.domain.model.dao;

import java.util.List;

import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Restrictions;

import fi.jonix.huutonet.domain.model.Market;

public class MarketDAOImpl extends GenericDAOImpl <Market, Long> implements MarketDAO {

	public Class<Market> getEntityClass() {
		return Market.class;
	}

	public Market getByName(String name) {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.add(Restrictions.eq("name", name));
		List<Market> list = this.getHibernateTemplate().findByCriteria(criteria);
		if (list == null || list.size() == 0) {
			return null;
		} 
		/* There will never be more than one market here, because name
		 * is unique in the database. */
		return list.get(0);
	}

}
