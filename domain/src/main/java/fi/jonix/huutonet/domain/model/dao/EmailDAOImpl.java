package fi.jonix.huutonet.domain.model.dao;

import java.util.List;
import java.util.Map;

import javax.persistence.Table;

import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Restrictions;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.jdbc.core.simple.SimpleJdbcTemplate;
import org.springframework.orm.hibernate3.SessionFactoryUtils;

import fi.jonix.huutonet.domain.model.Email;
import fi.jonix.huutonet.domain.model.Order;
import fi.jonix.huutonet.domain.model.OrderBundle;
import fi.jonix.huutonet.domain.model.Product;

public class EmailDAOImpl extends GenericDAOImpl<Email, Long> implements EmailDAO {

	public Class<Email> getEntityClass() {
		return Email.class;
	}

	public List<Email> getEmailsPendingForSending() {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.add(Restrictions.eq("type",Email.REAL_EMAIL));
		criteria.add(Restrictions.eq("direction",Email.OUT_PENDING));
		criteria.add(Restrictions.ge("priority",Email.PRIORITY_NORMAL));
		criteria.add(Restrictions.lt("sendingAttempts",Email.MAX_SENDING_ATTEMPTS));
		//make sure the emails are sent in the same order as they are created:
		criteria.addOrder(org.hibernate.criterion.Order.asc("id"));
		return this.getHibernateTemplate().findByCriteria(criteria);
	}

	public List<Email> getEmailsPendingForSendingWithLowPriority() {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.add(Restrictions.eq("type",Email.REAL_EMAIL));
		criteria.add(Restrictions.eq("direction",Email.OUT_PENDING));
		criteria.add(Restrictions.eq("priority",Email.PRIORITY_LOW));
		criteria.add(Restrictions.lt("sendingAttempts",Email.MAX_SENDING_ATTEMPTS));
		//make sure the emails are sent in the same order as they are created:
		criteria.addOrder(org.hibernate.criterion.Order.asc("id"));
		return this.getHibernateTemplate().findByCriteria(criteria);
	}

	public List<Email> getEmailsForBundle(OrderBundle bundle) {
		if (bundle == null) {
			throw new RuntimeException("bundle cannot be null (would return too many emails)");
		}
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.add(Restrictions.eq("orderBundle",bundle));
		//make sure the emails are returned in the same order as they are created:
		criteria.addOrder(org.hibernate.criterion.Order.asc("id"));
		return this.getHibernateTemplate().findByCriteria(criteria);
	}

}
