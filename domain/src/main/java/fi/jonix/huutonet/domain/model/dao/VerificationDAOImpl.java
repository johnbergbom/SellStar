package fi.jonix.huutonet.domain.model.dao;

import java.math.BigDecimal;
import java.util.List;

import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Restrictions;

import fi.jonix.huutonet.domain.model.Currency;
import fi.jonix.huutonet.domain.model.Verification;

public class VerificationDAOImpl extends GenericDAOImpl <Verification, Long> implements VerificationDAO {

	public Class<Verification> getEntityClass() {
		return Verification.class;
	}

	public Verification getVerification(BigDecimal vatPercentage, Currency currency) {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.add(Restrictions.eq("vatPercentage",vatPercentage));
		criteria.add(Restrictions.eq("currency",currency));
		List<Verification> list = this.getHibernateTemplate().findByCriteria(criteria);
		if (list != null && list.size() > 0) {
			/* There will never be more than one verification row here, because vatPercentage
			 * and currency are together unique in the database. */
			return list.get(0);
		} 
		return null;
	}

}
