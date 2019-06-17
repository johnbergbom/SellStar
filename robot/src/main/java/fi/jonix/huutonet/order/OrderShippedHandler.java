package fi.jonix.huutonet.order;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;

import org.apache.log4j.Logger;

import fi.jonix.huutonet.domain.ApplicationContextPlaceholder;
import fi.jonix.huutonet.domain.model.Order;
import fi.jonix.huutonet.domain.model.OrderBundle;
import fi.jonix.huutonet.domain.model.Tracking;
import fi.jonix.huutonet.domain.model.dao.MessageDAO;
import fi.jonix.huutonet.domain.model.dao.OrderDAO;
import fi.jonix.huutonet.email.EmailSender;
import fi.jonix.huutonet.exception.CriticalSectionInfo;
import fi.jonix.huutonet.exception.PostTrackingDownException;
import fi.jonix.huutonet.post.GeneralPostService;

/**
 * This class handles shipping of orders.
 * 
 * @author john
 *
 */
public class OrderShippedHandler {

	public static final Logger logger = Logger.getLogger(OrderShippedHandler.class);
	
	private static void addPostTracking(Tracking trackingObject, List<Order> trackingObjectOrders,
			List<Order> allOrdersInBundle, CriticalSectionInfo section) throws Exception {
		//OrderDAO orderDAO = (OrderDAO) ApplicationContextPlaceholder.applicationContext.getBean("orderDAO");
		OrderBundle bundle = trackingObject.getOrderBundle();
		HttpClient client = new DefaultHttpClient();
		//GetMethod gm = new GetMethod("http://www.verkkoposti.com/e3/TrackinternetServlet"
		//		     + "?lang=fi&LOTUS_hae=Hae&LOTUS_side=1&LOTUS_trackId=" + trackingCode + "&LOTUS_hae=Hae");
		HttpPost post = new HttpPost("http://www.seuraapostia.fi/posttracking/tracking/");
		//HttpParams params = post.getParams();
		List <NameValuePair> params = new ArrayList<NameValuePair>();
		params.add(new BasicNameValuePair("trackingCode",trackingObject.getTrackingCode()));
		//gm.addParameter("emailAddress","someuser@gmail.com");
		params.add(new BasicNameValuePair("emailAddress",bundle.getCustomer().getEmail()));
		params.add(new BasicNameValuePair("machine","true"));
		if (allOrdersInBundle.size() > trackingObjectOrders.size()) {
			params.add(new BasicNameValuePair("subjectSuffix"," (nippu " + bundle.getId() + ", osa " + trackingObject.getTrackingCode() + ")"));
			params.add(new BasicNameValuePair("productName",trackingObjectOrders.get(0).getAd().getAdTemplate().getHeadline() + " ym. (yhteensä "
					+ trackingObjectOrders.size() + " tuotetta)"));
		} else if (trackingObjectOrders.size() > 1) {
			params.add(new BasicNameValuePair("subjectSuffix"," (nippu " + bundle.getId() + ")"));
			params.add(new BasicNameValuePair("productName",trackingObjectOrders.get(0).getAd().getAdTemplate().getHeadline() + " ym. (yhteensä "
					+ trackingObjectOrders.size() + " tuotetta)"));
		} else {
			Order order = trackingObjectOrders.get(0);
			params.add(new BasicNameValuePair("subjectSuffix"," (tilaus " + order.getId() + ")"));
			params.add(new BasicNameValuePair("productName",order.getAd().getAdTemplate().getHeadline()));
		}
		params.add(new BasicNameValuePair("sellerName",bundle.getSeller().getSignature()));
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
					+ ") in OrderShippedHandler.addPostTracking)",
					trackingObject.getOrderBundle().getProvider().getSystemEmail());
			throw new PostTrackingDownException("Post tracking service seems to be down.");
		} else if (!body.startsWith("ok=")) {
			/* We should _never_ get any other answer than "ok=" unless there is a bug in the code,
			 * so let's throw a normal exception here (which will put SellStar in the error mode). */
			throw new Exception("Getting unexpected answer from post tracking: " + body);
		}
		
		/* Now the database of posttracker is updated, so this transaction needs to go through,
		 * or otherwise it'll fail next time since the tracking code is unique in the database
		 * of the posttracker. */
		section.setTransactionMustGoThrough(true);
	}
	
	/* This method takes care of the case were the product is moved from the provider
	 * to the China or Hong Kong post. */
	public static void handleOrderBundleShippedByProvider(Tracking trackingObject, List<Order> trackingObjectOrders,
			List<Order> allOrdersInBundle, CriticalSectionInfo section) throws Exception {
		OrderDAO orderDAO = (OrderDAO) ApplicationContextPlaceholder.applicationContext.getBean("orderDAO");
		OrderBundle bundle = trackingObjectOrders.get(0).getOrderBundle();
		MessageDAO messageDAO = (MessageDAO) ApplicationContextPlaceholder.applicationContext.getBean("messageDAO");
		GeneralPostService generalPostService = (GeneralPostService) ApplicationContextPlaceholder.applicationContext.getBean("generalPostService");
		for (Order order : trackingObjectOrders) {
			if (order.getState().intValue() < Order.STATE_ORDER_CONFIRMED_BY_PROVIDER
					|| order.getState().intValue() >= Order.STATE_SHIPPED_BY_PROVIDER
					/*|| order.getState().intValue() == Order.STATE_PROVIDER_CANCEL_PENDING*/
					|| order.getState().intValue() == Order.STATE_PROVIDER_CANCEL_CONFIRMED) {
				throw new Exception("order " + order.getId() + " is in the wrong state (OrderShippedHandler.handleOrderShippedByProvider()).");
			}
			order.setStateWithHistory(Order.STATE_SHIPPED_BY_PROVIDER);
		}

		Order firstOrder = trackingObjectOrders.get(0);
		String ordersInfo = "";
		for (Order order : trackingObjectOrders) {
			ordersInfo += messageDAO.getText("oneOrderSimple",
					bundle.getSeller(), order.getMarket().getLanguage(),
					"ORDER_ID",""+order.getId(),
					"AD_HEADLINE",order.getAd().getAdTemplate().getHeadline()) + "\n";
		}

		Map<String,String> addressReplacements = new HashMap<String,String>();
		addressReplacements.put("CUSTOMER_NAME",bundle.getFirstName() + " " + bundle.getLastName());
		addressReplacements.put("CUSTOMER_ADDRESS",bundle.getAddress());
		addressReplacements.put("CUSTOMER_POSTCODE",bundle.getPostcode());
		addressReplacements.put("CUSTOMER_CITY",bundle.getCity());
		
		String orderShippedEmail = messageDAO.getText("headerHi",
				bundle.getSeller(),firstOrder.getMarket().getLanguage()) + "\n\n";
		if (trackingObject != null) {
			trackingObject.setFollow(true);
			if (trackingObjectOrders.size() == 1 && allOrdersInBundle.size() == trackingObjectOrders.size()) {
				//orderShippedEmail = messageDAO.getText("orderShippedEmailWithTrackingCode",
					//	bundle.getSeller(),firstOrder.getMarket().getLanguage(), replacements);
				orderShippedEmail += messageDAO.getText("orderShippedByProviderHeaderWithTrackingCode",
						bundle.getSeller(),firstOrder.getMarket().getLanguage(),
						"TRACKING_OBJECT_ORDERS",ordersInfo,
						"TRACKING_CODE",trackingObject.getTrackingCode()) + "\n\n";
			} else {
				orderShippedEmail += messageDAO.getText("bundleShippedByProviderHeaderWithTrackingCode",
						bundle.getSeller(),firstOrder.getMarket().getLanguage(),
						"BUNDLE_ID",""+bundle.getId(),
						"TRACKING_OBJECT_ORDERS",ordersInfo,
						"TRACKING_CODE",trackingObject.getTrackingCode()) + "\n\n";
				if (allOrdersInBundle.size() > trackingObjectOrders.size()) {
					orderShippedEmail += messageDAO.getText("partlyShippedInfo",
							bundle.getSeller(),firstOrder.getMarket().getLanguage()) + "\n\n";
				} else {
					//orderShippedEmail = messageDAO.getText("multiOrderBundleShippedEmailWithTrackingCode",
						//	bundle.getSeller(),firstOrder.getMarket().getLanguage(), replacements);
				}
			}
			orderShippedEmail += messageDAO.getText("shippedByProviderAddressInfo",
					bundle.getSeller(),firstOrder.getMarket().getLanguage(), addressReplacements) + "\n\n";
			String trackingUrl = null;
			try {
				trackingUrl = generalPostService.getManualTrackingURL(trackingObject,trackingObjectOrders);
			} catch (Exception e) {
				if (e.getMessage().indexOf("Unknown tracking code") >= 0) {
					/* In some cases FocalPrice will give a chinese tracking code. In these
					 * cases we cannot trace the packet and it requires the operator to
					 * fix it manually. */
					EmailSender.sendOperationSummaryReport("Unparsable tracking code detected",
							"Unparsable tracking code (" + trackingObject.getTrackingCode() + ") detected for "
							+ " tracking object " + trackingObject.getId() + ". Please correct this one"
							+ " manually. The url sent to customer for packet tracking: ---",
							trackingObject.getOrderBundle().getProvider().getSystemEmail());
					trackingUrl = "---";
				} else {
					throw e;
				}
			}
			orderShippedEmail += messageDAO.getText("shippedByProviderGeneralTrackingInfo",
					bundle.getSeller(),firstOrder.getMarket().getLanguage(),
					"HONGKONG_OR_CHINA_POST_URL",trackingUrl);
			addPostTracking(trackingObject,trackingObjectOrders,allOrdersInBundle,section);
		} else {
			if (trackingObjectOrders.size() == 1/* && allOrdersInBundle.size() == trackingObjectOrders.size()*/) {
				//orderShippedEmail = messageDAO.getText("orderShippedEmailWithoutTrackingCode",
					//	bundle.getSeller(),firstOrder.getMarket().getLanguage(), replacements);
				orderShippedEmail += messageDAO.getText("orderShippedByProviderHeaderWithoutTrackingCode",
						bundle.getSeller(),firstOrder.getMarket().getLanguage(),
						"TRACKING_OBJECT_ORDERS",ordersInfo) + "\n\n";
			} else {
				orderShippedEmail = messageDAO.getText("bundleShippedFromProviderHeaderWithoutTrackingCode",
						bundle.getSeller(),firstOrder.getMarket().getLanguage(),
						"BUNDLE_ID",""+bundle.getId(),
						"TRACKING_OBJECT_ORDERS",ordersInfo) + "\n\n";
				if (allOrdersInBundle.size() > trackingObjectOrders.size()) {
					orderShippedEmail += messageDAO.getText("partlyShippedInfo",
							bundle.getSeller(),firstOrder.getMarket().getLanguage()) + "\n\n";
				}
			}
			orderShippedEmail += messageDAO.getText("shippedByProviderAddressInfo",
					bundle.getSeller(),firstOrder.getMarket().getLanguage(), addressReplacements) + "\n\n";
			orderShippedEmail += "Tuote saapuu yllä mainittuun osoitteeseen noin kolmen viikon sisällä.\n\n";
		}
		orderShippedEmail += "\n\n" + messageDAO.getText("footer",
				bundle.getSeller(),firstOrder.getMarket().getLanguage(),
				"SIGNATURE",bundle.getSeller().getSignature());
		EmailSender.sendEmailBatch(bundle.getSeller().getSystemEmail(),
				bundle.getCustomer().getEmail(),"Tuote viety postiin",orderShippedEmail);
	}
	
	/* This method takes care of the case where China post or Hong Kong post informs that the
	 * product has left for Finland. This method will only be called for orders having trackingCode != null. */
	public static void handleTrackingObjectShippedByPostOffice(Tracking trackingObject,
			List<Order> trackingObjectOrders, int oldState, List<Order> allOrdersInBundle,
			boolean shippedByProviderInfoCameLate) throws Exception {
		OrderDAO orderDAO = (OrderDAO) ApplicationContextPlaceholder.applicationContext.getBean("orderDAO");
		MessageDAO messageDAO = (MessageDAO) ApplicationContextPlaceholder.applicationContext.getBean("messageDAO");
		if (trackingObject == null || oldState != Order.STATE_SHIPPED_BY_PROVIDER) {
			throw new Exception("Bundle " + trackingObjectOrders.get(0).getOrderBundle().getId() + " has no tracking code or else it's in the"
					+ " wrong state (OrderShippedHandler.handleOrderBundleShippedByPostOffice()).");
		}
		trackingObject.setFollow(true);
		OrderBundle bundle = trackingObject.getOrderBundle();
		boolean someHasZeroFinalSalesPrice = false;
		for (Order order : trackingObjectOrders) {
			order.setStateWithHistory(Order.STATE_SHIPPED_BY_POSTOFFICE);
			if (order.getFinalSalesPriceInMarketCurrencyInclVat().compareTo(new BigDecimal("0.00")) == 0) {
				someHasZeroFinalSalesPrice = true;
			}
		}
		
		if (someHasZeroFinalSalesPrice) {
			/* A possibly refunded order was sent from China. Inform the operator. This might
			 * be a case where the order was refunded because it never came to the customer
			 * (because the post screwed up). Now finally the post office gets their butts in
			 * gear and pass on the product. */
			String operEmail = "Some order for tracking object " + trackingObject.getId() + " was possibly"
				+ "refunded (perhaps because of very long delays at the post office). Now finally the post"
				+ " office gets their butts in gear. Please deal with this one manually (send instructions"
				+ " to the customer how he can return the refund to us).\n";
			EmailSender.sendOperationProblemReport("Product for which a refund was given is actually delivered?",operEmail);
		}
		
		String email = "Moi,\n\n";
		String subject;
		Map<String,String> replacements = new HashMap<String,String>();
		replacements.put("TRACKING_CODE",trackingObject.getTrackingCode());
		replacements.put("SIGNATURE",bundle.getSeller().getSignature());
		if (allOrdersInBundle.size() > trackingObjectOrders.size()) {
			if (shippedByProviderInfoCameLate) {
				email += "Kiinan/Hong Kongin postin tiedot ei ollut ajantasalla ja huomasimme, että pakettisi on jo saapunut"
					+ " Suomeen (nippunumero on " + bundle.getId() + ").";
			} else {
				email += "Lähetyksesi jonka nippunumero on " + bundle.getId() + " on nyt lähtenyt kohti Suomea.";
			}
			email += " Ulkomaiden postinseurantapalvelut eivät kuitenkaan tarjoa tietoa Suomen sisällä tapahtuvasta"
				+ " postin lähetyksen etenemisestä. Olemme ilmoittaneet lähetyksesi tiedot www.seuraapostia.fi-palveluun,"
				+ " joka tiedottaa teitä automaattisesti lähetyksen etenemisestä Suomen postiverkossa. Ensimmäisen"
				+ " tiedotteen saat, kun paketti on saapunut Suomeen. Voit tämän jälkeen halutessasi myös itse seurata"
				+ " paketin etenemistä suoraan postin sivuilta: http://www.verkkoposti.com/e3/TrackinternetServlet?lang"
				+ "=fi&LOTUS_hae=Hae&LOTUS_side=1&LOTUS_trackId=" + trackingObject.getTrackingCode() + "&LOTUS_hae=Hae\n\n"
				+ "HUOM: tämä tiedotus koskee vain nipun seuraavia tilauksia:\n";
			for (Order order : trackingObjectOrders) {
				email += messageDAO.getText("oneOrderSimple",
						bundle.getSeller(), order.getMarket().getLanguage(),
						"ORDER_ID",""+order.getId(),
						"AD_HEADLINE",order.getAd().getAdTemplate().getHeadline()) + "\n";
			}
			email += "\nNipussa on muitakin tilauksia, mutta niistä tiedotetaan erikseen (koska ne lähetetään erikseen).\n\n";
			email += "Ystävällisin terveisin,\n" + bundle.getSeller().getSignature();
			subject = "Nippu lähetetty (osa-nippu seurantakoodilla " + trackingObject.getTrackingCode() + ")";
		} else if (trackingObjectOrders.size() > 1) {
			//replacements.put("BUNDLE_ID",""+bundle.getId());
			String bundleOrderInfo = "";
			for (Order order : trackingObjectOrders) {
				bundleOrderInfo += messageDAO.getText("oneOrderSimple",
						bundle.getSeller(), order.getMarket().getLanguage(),
						"ORDER_ID",""+order.getId(),
						"AD_HEADLINE",order.getAd().getAdTemplate().getHeadline()) + "\n";
			}
			//replacements.put("BUNDLE_ORDERS",bundleOrderInfo);
			//email = messageDAO.getText("multiOrderBundleShippedByPostOffice", bundle.getSeller(),
				//	trackingObjectOrders.get(0).getMarket().getLanguage(), replacements);
			if (shippedByProviderInfoCameLate) {
				email += "Kiinan/Hong Kongin postin tiedot ei ollut ajantasalla ja huomasimme, että pakettisi on jo saapunut"
					+ " Suomeen (nippunumero on " + bundle.getId() + ").";
			} else {
				email += "Lähetyksesi jonka nippunumero on " + bundle.getId() + " on nyt lähtenyt kohti Suomea.";
			}
			email += " Ulkomaiden postinseurantapalvelut eivät kuitenkaan tarjoa tietoa Suomen sisällä tapahtuvasta"
				+ " postin lähetyksen etenemisestä. Olemme ilmoittaneet lähetyksesi tiedot www.seuraapostia.fi-palveluun,"
				+ " joka tiedottaa teitä automaattisesti lähetyksen etenemisestä Suomen postiverkossa. Ensimmäisen"
				+ " tiedotteen saat, kun paketti on saapunut Suomeen. Voit tämän jälkeen halutessasi myös itse seurata"
				+ " paketin etenemistä suoraan postin sivuilta: http://www.verkkoposti.com/e3/TrackinternetServlet?lang"
				+ "=fi&LOTUS_hae=Hae&LOTUS_side=1&LOTUS_trackId=" + trackingObject.getTrackingCode() + "&LOTUS_hae=Hae\n\n"
				+ "Nipussa ovat seuraavat tilaukset:\n" + bundleOrderInfo;
			email += "\nLähetyksen seurantakoodi on " + trackingObject.getTrackingCode() + "\n\n";
			email += "Ystävällisin terveisin,\n" + bundle.getSeller().getSignature();
			subject = "Nippu lähetetty";
		} else {
			/* Single order bundle. */
			Order order = trackingObjectOrders.get(0);
			/*replacements.put("ORDER_ID",""+order.getId());
			replacements.put("AD_HEADLINE",order.getAd().getAdTemplate().getHeadline());
			email = messageDAO.getText("orderShippedByPostOffice", bundle.getSeller(),
					order.getMarket().getLanguage(), replacements);*/
			if (shippedByProviderInfoCameLate) {
				email += "Kiinan/Hong Kongin postin tiedot ei ollut ajantasalla ja huomasimme, että pakettisi on jo saapunut"
					+ " Suomeen (tilausnumero on " + order.getId() + ").";
			} else {
				email += "Lähetyksesi jonka tilausnumero on " + order.getId() + " on nyt lähtenyt kohti Suomea.";
			}
			email += " Ulkomaiden postinseurantapalvelut eivät kuitenkaan tarjoa tietoa Suomen sisällä tapahtuvasta"
				+ " postin lähetyksen etenemisestä. Olemme ilmoittaneet lähetyksesi tiedot www.seuraapostia.fi-palveluun,"
				+ " joka tiedottaa teitä automaattisesti lähetyksen etenemisestä Suomen postiverkossa. Ensimmäisen"
				+ " tiedotteen saat, kun paketti on saapunut Suomeen. Voit tämän jälkeen halutessasi myös itse seurata"
				+ " paketin etenemistä suoraan postin sivuilta: http://www.verkkoposti.com/e3/TrackinternetServlet?lang"
				+ "=fi&LOTUS_hae=Hae&LOTUS_side=1&LOTUS_trackId=" + trackingObject.getTrackingCode() + "&LOTUS_hae=Hae\n\n";
			email += "\nLähetyksen seurantakoodi on " + trackingObject.getTrackingCode() + "\n\n";
			email += "Ystävällisin terveisin,\n" + bundle.getSeller().getSignature();
			subject = "Tuote lähetetty";
		}
		
		EmailSender.sendEmailBatch(bundle.getSeller().getSystemEmail(),
				bundle.getCustomer().getEmail(),subject,email);
	}
	
}
