package fi.jonix.huutonet.domain.model.dao;

import java.util.List;

import fi.jonix.huutonet.domain.model.OrderBundle;
import fi.jonix.huutonet.domain.model.Tracking;

public interface TrackingDAO extends GenericDAO<Tracking, Long> {

	public List<Tracking> getTrackingObjectsInBundle(OrderBundle orderBundle);
	public List<Tracking> getTrackingObjectsForLocationTracking();
	public boolean trackingCodeExists(String trackingCode);
	public Tracking getTrackingObject(String trackingCode);

}
