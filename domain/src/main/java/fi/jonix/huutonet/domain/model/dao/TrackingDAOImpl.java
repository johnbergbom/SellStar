package fi.jonix.huutonet.domain.model.dao;

import java.util.List;

import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Restrictions;

import fi.jonix.huutonet.domain.model.Order;
import fi.jonix.huutonet.domain.model.OrderBundle;
import fi.jonix.huutonet.domain.model.Tracking;

public class TrackingDAOImpl extends GenericDAOImpl <Tracking, Long> implements TrackingDAO {

	@Override
	public Class<Tracking> getEntityClass() {
		return Tracking.class;
	}

	public List<Tracking> getTrackingObjectsInBundle(OrderBundle orderBundle) {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.add(Restrictions.eq("orderBundle",orderBundle));
		return this.getHibernateTemplate().findByCriteria(criteria);
	}

	public List<Tracking> getTrackingObjectsForLocationTracking() {
		Object[] states = new Object[2];
		states[0] = Order.STATE_SHIPPED_BY_PROVIDER;
		states[1] = Order.STATE_FETCHED_BY_CUSTOMER;
		return this.getHibernateTemplate().find("SELECT t FROM Tracking t WHERE t IN (SELECT o.tracking FROM Order o"
				+ " WHERE o.state >= ? AND o.state < ?)",states);
	}

	public boolean trackingCodeExists(String trackingCode) {
		DetachedCriteria criteria = DetachedCriteria.forClass(Tracking.class);
		criteria.add(Restrictions.eq("trackingCode", trackingCode));
		List<Tracking> list = this.getHibernateTemplate().findByCriteria(criteria);
		if (list != null && list.size() > 0) {
			return true;
		} 
		return false;
	}

	public Tracking getTrackingObject(String trackingCode) {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.add(Restrictions.eq("trackingCode",trackingCode));
		List<Tracking> list = this.getHibernateTemplate().findByCriteria(criteria);
		if (list != null && list.size() > 0) {
			/* There will never be more than one trackign object here, because trackingCode
			 * is unique. */
			return list.get(0);
		}
		return null;
	}

}
