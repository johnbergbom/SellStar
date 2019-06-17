package fi.jonix.huutonet.domain.model.dao;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Restrictions;

import fi.jonix.huutonet.domain.model.ErrorTracking;

public class ErrorTrackingDAOImpl extends GenericDAOImpl <ErrorTracking, Long> implements ErrorTrackingDAO {

	public Class<ErrorTracking> getEntityClass() {
		return ErrorTracking.class;
	}

	public List<ErrorTracking> getUnacknowledgedGlobalError() {
		return getUnacknowledgedCommandError(ErrorTracking.GLOBAL_ERROR);
	}

	public List<ErrorTracking> getUnacknowledgedPaymentError() {
		List<ErrorTracking> paymentErrors = new ArrayList<ErrorTracking>();
		for (String paymentErrorCommand : ErrorTracking.PAYMENT_ERROR_MODULES) {
			paymentErrors.addAll(getUnacknowledgedCommandError(paymentErrorCommand));
		}
		return paymentErrors;
	}

	public List<ErrorTracking> getUnacknowledgedCommandError(String command) {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.add(Restrictions.eq("command", command));
		criteria.add(Restrictions.isNull("acknowledged"));
		return this.getHibernateTemplate().findByCriteria(criteria);
		/*if (list != null && list.size() > 1) {
			throw new RuntimeException("Error: more than one global error found. This should never happen.");
		} else if (list != null && list.size() > 0) {
			return list.get(0);
		} 
		return null;*/
	}

}
