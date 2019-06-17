package fi.jonix.huutonet.tools;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;

import fi.jonix.huutonet.domain.model.Order;
import fi.jonix.huutonet.email.processor.EmailFilter;
import fi.jonix.huutonet.exception.PostTrackingDownException;

public class TomisTester {
	
	public static final Logger logger = Logger.getLogger(TomisTester.class);

	public static void tomiTester(String[] args) throws Exception {
		int state = -999;
		DateFormat dateFormat = new SimpleDateFormat("dd-MMM-yyyy");
		HttpClient client = new DefaultHttpClient();
		HttpPost post = new HttpPost("http://app3.hongkongpost.com/CGI/mt/genresult.jsp");
		//HttpParams params = post.getParams();
		//params.setParameter("tracknbr",trackingObject.getTrackingCode());
		List <NameValuePair> params = new ArrayList<NameValuePair>();
		params.add(new BasicNameValuePair("tracknbr","RB406601004HK"));
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
		} else if (body.indexOf("left Hong Kong for its destination on") > 0) {
			Date date = dateFormat.parse(EmailFilter.getFieldValue(body,"left Hong Kong for its destination on"));
			if (state == Order.STATE_SHIPPED_BY_POSTOFFICE) {
				logger.info("Tracking object xxx left Hong Kong on " + date);
			} else {
				logger.info("Tracking object xxx left Hong Kong on " + date);
			}
			logger.info("return Order.STATE_SHIPPED_BY_POSTOFFICE");
		} else if (body.indexOf("To promptly retrieve the record of your item, please key in your Enquiry Reference Number") > 0
				|| (body.indexOf("The Enquiry Reference Number") > 0 && body.indexOf("is not available") > 0)) {
			/* The item isn't yet registered at Hong Kong post. */
			logger.info("Tracking object xxx isn't yet registered at Hong Kong post.");
			logger.info("return NOT_REGISTERED");
		} else if (body.indexOf("will leave Hong Kong for its destination on") > 0) {
			/* The item is registered at Hong Kong post but hasn't yet left. */
			Date date = dateFormat.parse(EmailFilter.getFieldValue(body,"will leave Hong Kong for its destination on"));
			logger.info("Tracking object xxx will leave Hong Kong on " + date);
			logger.info("return NOT_SENT");
		} else if (body.indexOf("is being processed for departure from Hong Kong as of") > 0) {
			/* The item is registered at Hong Kong post but hasn't yet left. */
			String dateStr = EmailFilter.getFieldValue(body,"is being processed for departure from Hong Kong as of");
			if (dateStr.indexOf(".") >= 0) {
				dateStr = dateStr.substring(0,dateStr.indexOf("."));
			}
			logger.info("dateStr = " + dateStr);
			Date date = dateFormat.parse(dateStr);
			logger.info("Tracking object xxx will leave Hong Kong on " + date);
			logger.info("return NOT_SENT");
		} else if (body.indexOf("and is being processed for delivery to the addressee") > 0) {
			/* The item is registered at Hong Kong post but no date for shipping is yet known. */
			Date date = dateFormat.parse(EmailFilter.getFieldValue(body,"was posted on"));
			logger.info("Tracking object xxx was registered at Hong Kong post at "
					+ date + " (shipping date from Hong Kong post not yet known).");
			logger.info("return NOT_SENT");
		} else if (body.indexOf("Invalid item number format") > 0) {
			logger.error("Invalid item number format at Hong Kong post for RB406601004HK.");
			//throw new Exception("Hong Kong post puked on tracking code RB406601004HK");
		} else if (body.indexOf("The Track and Trace System is now busy, please try later") > 0) {
			logger.info("Post tracking service temporarily down (body = " + body + "): ");
			//throw new PostTrackingDownException("Post tracking service seems to be down.");
		} else {
			logger.info("Unparsable body received from Hong Kong post: " + body);
			//throw new Exception("Hong Kong post puked on tracking code RB406601004HK");
		}
		logger.info("state = " + state);
	}
	
}
