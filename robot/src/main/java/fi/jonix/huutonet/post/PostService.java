package fi.jonix.huutonet.post;

import java.util.List;

import fi.jonix.huutonet.domain.model.Order;
import fi.jonix.huutonet.domain.model.Tracking;
//import fi.jonix.huutonet.exception.TrackingCodeNotFoundException;
import fi.jonix.huutonet.domain.model.OrderBundle;

public interface PostService {
	
	public static int NOT_REGISTERED = -3;
	public static int WRONG_COUNTRY = -2;
	public static int NOT_SENT = -1;
	
	public abstract String getManualTrackingURL(Tracking trackingObject, List<Order> trackingObjectOrders);
	public abstract int getShippingStatus(Tracking trackingObject, int state) throws Exception;
	//String getPackageArrivedInfo(String trackingCode) throws TrackingCodeNotFoundException;
	
}
