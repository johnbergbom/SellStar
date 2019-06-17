package fi.jonix.huutonet.domain.model.dao;

import java.util.List;

import fi.jonix.huutonet.domain.model.ErrorTracking;

public interface ErrorTrackingDAO extends GenericDAO<ErrorTracking, Long> {

	public List<ErrorTracking> getUnacknowledgedGlobalError();
	public List<ErrorTracking> getUnacknowledgedPaymentError();
	public List<ErrorTracking> getUnacknowledgedCommandError(String command);

}
