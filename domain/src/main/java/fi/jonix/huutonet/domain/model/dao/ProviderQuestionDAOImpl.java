package fi.jonix.huutonet.domain.model.dao;

import java.util.List;

import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Restrictions;

import fi.jonix.huutonet.domain.model.OrderBundle;
import fi.jonix.huutonet.domain.model.ProviderQuestion;

public class ProviderQuestionDAOImpl extends GenericDAOImpl <ProviderQuestion, Long> implements ProviderQuestionDAO {

	@Override
	public Class<ProviderQuestion> getEntityClass() {
		return ProviderQuestion.class;
	}

	/**
	 * This method returns all provider conversations for the bundle in question
	 * sorted so that the latest conversation is at the beginning of the list.
	 */
	public List<ProviderQuestion> getProviderQuestionForBundle(OrderBundle orderBundle) {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.add(Restrictions.eq("orderBundle",orderBundle));
		criteria.addOrder(org.hibernate.criterion.Order.desc("id"));
		return this.getHibernateTemplate().findByCriteria(criteria);
	}

	public List<ProviderQuestion> getOpenConversations() {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.add(Restrictions.eq("open",true));
		return this.getHibernateTemplate().findByCriteria(criteria);
	}

}
