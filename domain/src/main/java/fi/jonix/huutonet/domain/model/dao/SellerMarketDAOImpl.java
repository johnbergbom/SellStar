package fi.jonix.huutonet.domain.model.dao;

import java.util.List;

import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Restrictions;

import fi.jonix.huutonet.domain.model.Market;
import fi.jonix.huutonet.domain.model.Seller;
import fi.jonix.huutonet.domain.model.SellerMarket;

public class SellerMarketDAOImpl extends GenericDAOImpl <SellerMarket, Long> implements SellerMarketDAO {

	@Override
	public Class<SellerMarket> getEntityClass() {
		return SellerMarket.class;
	}

	public SellerMarket getBySellerAndMarket(Seller seller, Market market) {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.add(Restrictions.eq("seller",seller));
		criteria.add(Restrictions.eq("market",market));
		List<SellerMarket> list = this.getHibernateTemplate().findByCriteria(criteria);
		if (list != null && list.size() > 0) {
			/* There will never be more than one seller market here, because seller/market
			 * are declared unique. */
			return list.get(0);
		} 
		return null;
	}

}
