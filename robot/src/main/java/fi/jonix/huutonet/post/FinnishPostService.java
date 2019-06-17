package fi.jonix.huutonet.post;

import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.NameValuePair;
import org.apache.http.protocol.HTTP;
import org.apache.http.client.entity.UrlEncodedFormEntity;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;

import fi.jonix.huutonet.domain.model.Order;
import fi.jonix.huutonet.domain.model.Tracking;
import fi.jonix.huutonet.email.EmailSender;
import fi.jonix.huutonet.exception.PostTrackingDownException;
import fi.jonix.huutonet.robot.SeleniumRobot;

public class FinnishPostService implements PostService {
	
	public static final Logger logger = Logger.getLogger(FinnishPostService.class);

	@Autowired
	private SeleniumRobot seleniumRobot;
	
	/*private String getTrackingServiceUrl(String trackingCode) {
		return "http://www.verkkoposti.com/e3/TrackinternetServlet?lang=fi&LOTUS_hae=Hae&LOTUS_side=1&LOTUS_trackId=" + trackingCode
				+ "&LOTUS_hae=Hae";
	}*/

	@Override
	public String getManualTrackingURL(Tracking trackingObject, List<Order> trackingObjectOrders) {
		return "http://www.posti.fi/postipalvelee/lahetystenseuranta/";
	}
	
	@Override
	public int getShippingStatus(Tracking trackingObject, int state) throws Exception {
		HttpClient client = new DefaultHttpClient();
		HttpPost post = new HttpPost("http://www.seuraapostia.fi/posttracking/tracking/statusQuery");
		//HttpParams params = post.getParams();
		//params.setParameter("trackingCode",trackingObject.getTrackingCode());
		//params.setParameter("machine","true");
		List <NameValuePair> params = new ArrayList<NameValuePair>();
		params.add(new BasicNameValuePair("trackingCode",trackingObject.getTrackingCode()));
		params.add(new BasicNameValuePair("machine","true"));
		post.setEntity(new UrlEncodedFormEntity(params, HTTP.UTF_8));
		String body = null;
		try {
			HttpResponse resp = client.execute(post);
			body = EntityUtils.toString(resp.getEntity());
		} catch (Exception e) {
			logger.debug("Problem contacting post tracking service: ", e);
		}
		if (body == null || (!body.startsWith("ok=") && !body.startsWith("error="))) {
			if (body != null) {
				logger.debug("Got this body: " + body);
			}
			EmailSender.sendOperationProblemReport("Error returned from posttracker",
					"The following body was returned from posttracker:\n\n" + body
					+ "\n\n(for tracking object " + trackingObject.getId() + " ("
					+ trackingObject.getTrackingCode()
					+ ") in FinnishPostService.getShippingStatus)",
					trackingObject.getOrderBundle().getProvider().getSystemEmail());
			throw new PostTrackingDownException("Post tracking service seems to be down.");
		} else if (body.startsWith("ok=")) {
			int status = Integer.parseInt(body.substring(3));
			logger.debug("Status received from seuraapostia.fi for tracking object " + trackingObject.getId() + ": " + status);
			/* The following cases correspond to the constants in Tracking.java of the posttracking application. */
	        switch (status) {
	        case 1: return NOT_REGISTERED; //hasn't yet reached the destination country
	        case 2: if (state == Order.STATE_REACHED_DESTINATION_COUNTRY) {
	        			logger.debug("Tracking object " + trackingObject.getId() + " reached destination country.");
	        		} else {
	        			logger.info("Tracking object " + trackingObject.getId() + " reached destination country.");
	        		}
	        		return Order.STATE_REACHED_DESTINATION_COUNTRY;
	        case 3: if (state == Order.STATE_ARRIVED_TO_CUSTOMERS_POSTOFFICE) {
	        			logger.debug("Tracking object " + trackingObject.getId() + " arrived to customer's postoffice.");
    				} else {
    					logger.info("Tracking object " + trackingObject.getId() + " arrived to customer's postoffice.");
    				}
	        		return Order.STATE_ARRIVED_TO_CUSTOMERS_POSTOFFICE;
	        case 4: if (state == Order.STATE_FETCHED_BY_CUSTOMER) {
	        			logger.debug("Tracking object " + trackingObject.getId() + " fetched by customer.");
					} else {
						logger.info("Tracking object " + trackingObject.getId() + " fetched by customer.");
					}
	        		return Order.STATE_FETCHED_BY_CUSTOMER;
	        case 5: if (state == Order.STATE_ARRIVED_TO_CUSTOMERS_POSTOFFICE) {
        				logger.info("Tracking object " + trackingObject.getId() + " returned to provider.");
					} else {
						throw new Exception("trackingObject " + trackingObject.getId() + " was returned"
								+ " to provider but has wrong state.");
					}
	        		return Order.STATE_CLOSED_RETURNED_TO_PROVIDER;
	        case 99: return NOT_REGISTERED; //hasn't yet reached the destination country and posttracker has given up on this one
	        default: throw new Exception("Getting unexpected answer from post tracking: " + body);
	        }
		} else {
			/* We should _never_ get any other answer than "ok=" unless there is a bug in the code,
			 * so let's throw a normal exception here (which will put SellStar in the error mode). */
			throw new Exception("Getting unexpected answer from post tracking: " + body);
		}
	}
	
	/*public String getPackageArrivedInfo(String trackingCode) throws TrackingCodeNotFoundException {
		CustomSelenium selenium = seleniumRobot.getSelenium();
		selenium.open(this.getTrackingServiceUrl(trackingCode));
		String pageHeadline = selenium.getText("//div[@id='content']//h2");
		String arriveText = "Saapunut toimipaikkaan/terminaaliin";
		
		boolean arrived = false;
		for(int i = 1;;i++){
			if(selenium.isElementPresent("//div[contains(@class, 'contentarea')]/div[1]/p/table/tbody/tr[" + i +"]")){
				String infoRow = selenium.getText("//div[contains(@class, 'contentarea')]/div[1]/p/table/tbody/tr[" + i +"]");
				if(infoRow.indexOf(arriveText) > -1){
					arrived = true;
					break;
				}
			} else {
				break;
			}
		}
		
		if(!arrived)
			return null;
		
		if(selenium.isElementPresent("//div[contains(@class, 'contentarea')]/div[1]/p/table/tbody/tr[1]")){
			String info = selenium.getText("//div[contains(@class, 'contentarea')]/div[1]/p/table/tbody/tr[1]");
			info = info.replaceAll("<\\w+>", "\n").replaceAll("</\\w+>", "\n").trim();
			return info;
		}
		throw new TrackingCodeNotFoundException("Tracking code " + trackingCode + " was not found at Finnish Posti");
	}*/

}
