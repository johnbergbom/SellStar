package fi.jonix.huutonet.post;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;

import org.apache.log4j.Logger;

import fi.jonix.huutonet.domain.model.Order;
import fi.jonix.huutonet.domain.model.Tracking;
import fi.jonix.huutonet.exception.PostTrackingDownException;

public class ChinaPostService implements PostService {

	public static final Logger logger = Logger.getLogger(ChinaPostService.class);

	@Override
	public String getManualTrackingURL(Tracking trackingObject, List<Order> trackingObjectOrders) {
		return "http://www.emsairmailtracking.com/";
	}
	
	private String getNextDate(String text) {
		Pattern p = Pattern.compile("\\p{Digit}{4}-\\p{Digit}{2}-\\p{Digit}{2}");
		Matcher matcher = p.matcher(text);
		if (matcher.find()) {
			//logger.debug(text.substring(matcher.start(),matcher.end()));
			return text.substring(matcher.start(),matcher.end());
		}
		return null;
	}

	@Override
	public int getShippingStatus(Tracking trackingObject, int state) throws Exception {
		DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
		HttpClient client = new DefaultHttpClient();
		HttpPost post = new HttpPost("http://www.emsairmailtracking.com/chinapost.php");
		//HttpParams params = post.getParams();
		//params.setParameter("itemNo",trackingObject.getTrackingCode());
		List <NameValuePair> params = new ArrayList<NameValuePair>();
		params.add(new BasicNameValuePair("itemNo",trackingObject.getTrackingCode()));
		post.setEntity(new UrlEncodedFormEntity(params, HTTP.UTF_8));
		String body = null;
		try {
			HttpResponse resp = client.execute(post);
			body = EntityUtils.toString(resp.getEntity());
		} catch (Exception e) {
			logger.debug("Problem contacting post tracking service (body = " + body + "): ", e);
			throw new PostTrackingDownException("Post tracking service seems to be down.");
		}
		if (body == null) {
			throw new PostTrackingDownException("Post tracking service seems to be down.");
		} else if (body.indexOf("departure from outward office of exchange") > 0) {
			String dateStr = getNextDate(body.substring(body.indexOf("departure from outward office of exchange")));
			Date date = dateFormat.parse(getNextDate(dateStr));
			if (state == Order.STATE_SHIPPED_BY_POSTOFFICE) {
				logger.debug("Tracking object " + trackingObject.getId() + " left China on " + date);
			} else {
				logger.info("Tracking object " + trackingObject.getId() + " left China on " + date);
			}
			return Order.STATE_SHIPPED_BY_POSTOFFICE;
		} else if (body.indexOf("arrival at inward office of exchange") > 0 && body.indexOf("HELSINKI") > 0
				&& body.indexOf("departure from transit office of exchange") > 0) {
			/* In some cases the "departure from outward office of exchange" seems to be left out. Then let's use the
			 * "departure from transit office of exchange" as the departure date (although it's not totally correct). */
			String dateStr = getNextDate(body.substring(body.indexOf("departure from transit office of exchange")));
			Date date = dateFormat.parse(getNextDate(dateStr));
			if (state == Order.STATE_SHIPPED_BY_POSTOFFICE) {
				logger.debug("Tracking object " + trackingObject.getId() + " left China a little after " + date);
			} else {
				logger.info("Tracking object " + trackingObject.getId() + " left China a little after " + date);
			}
			return Order.STATE_SHIPPED_BY_POSTOFFICE;
		} else if (body.indexOf(trackingObject.getTrackingCode()) == body.lastIndexOf(trackingObject.getTrackingCode())
				&& body.indexOf(trackingObject.getTrackingCode()) > 0) {
			/* The item isn't yet registered at China post. */
			logger.debug("Tracking object " + trackingObject.getId() + " isn't yet registered at China post.");
			return NOT_REGISTERED;
		} else if (body.indexOf("posting") > 0 || body.indexOf("arrival at transit office of exchange") > 0) {
			/* The item is registered at China post but hasn't yet left. */
			String dateStr = null;
			if (body.indexOf("posting") > 0) {
				dateStr = getNextDate(body.substring(body.indexOf("posting")));
			} else {
				dateStr = getNextDate(body.substring(body.indexOf("arrival at transit office of exchange")));
			}
			Date date = dateFormat.parse(dateStr);
			logger.debug("Tracking object " + trackingObject.getId() + " arrived to China post on " + date);
			if (body.toLowerCase().indexOf("finland") < 0) {
				logger.info("Wrong country for tracking object " + trackingObject.getId() + ".");
				return WRONG_COUNTRY;
			}
			return NOT_SENT;
		} else if (body.indexOf("Resource Limit Is Reached") > 0) {
			logger.debug("China post resource limit was reached.");
			throw new PostTrackingDownException("Post tracking service seems to be down.");
		} else {
			logger.debug("Unparsable body received from China post: " + body);
			throw new Exception("China post puked on tracking code " + trackingObject.getTrackingCode());
		}
	}

}
