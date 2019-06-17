package fi.jonix.huutonet.domain.model.dao;

import java.util.List;

import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Restrictions;

import fi.jonix.huutonet.domain.model.Provider;

public class ProviderDAOImpl extends GenericDAOImpl <Provider, Long> implements ProviderDAO {

	public Class<Provider> getEntityClass() {
		return Provider.class;
	}

	public Provider getProviderByName(String name) {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.add(Restrictions.eq("name", name));
		List<Provider> providers = this.getHibernateTemplate().findByCriteria(criteria);
		if (providers == null || providers.isEmpty()) {
			return null;
		}
		/* There will never be more than one provider here, because name
		 * is unique in the database. */
		return providers.get(0);
	}
	
}
