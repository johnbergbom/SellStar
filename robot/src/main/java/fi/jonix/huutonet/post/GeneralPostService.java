package fi.jonix.huutonet.post;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import fi.jonix.huutonet.domain.model.Order;
import fi.jonix.huutonet.domain.model.OrderBundle;
import fi.jonix.huutonet.domain.model.Tracking;
import fi.jonix.huutonet.domain.model.dao.OrderDAO;
import fi.jonix.huutonet.domain.model.dao.TrackingDAO;
import fi.jonix.huutonet.email.EmailSender;
import fi.jonix.huutonet.order.OrderShippedHandler;

@Component(value = "generalPostService")
public class GeneralPostService implements PostService {

	public static final Logger logger = Logger.getLogger(GeneralPostService.class);

	@Autowired
	private OrderDAO orderDAO;
	
	@Autowired
	private TrackingDAO trackingDAO;
	
	@Override
	public String getManualTrackingURL(Tracking trackingObject, List<Order> trackingObjectOrders) {
		int state = trackingObjectOrders.get(0).getState();
		for (Order order : trackingObjectOrders) {
			if (!order.getState().equals(state)) {
				throw new RuntimeException("Not all orders are in the same state for tracking object " + trackingObject.getId() + ".");
			}
		}
		return getManualTrackingURL(trackingObject, state);
	}
	
	public String getManualTrackingURL(Tracking trackingObject, int state) {
		String trackingCode = trackingObject.getTrackingCode();
		PostService postService = null;
		if (trackingCode == null || state < Order.STATE_SHIPPED_BY_PROVIDER) {
			return null;
		} else if (state == Order.STATE_SHIPPED_BY_PROVIDER) {
			if (trackingCode.endsWith("CN")) {
				postService = new ChinaPostService();
			} else if (trackingCode.endsWith("HK")) {
				postService = new HongKongPostService();
			} else {
				throw new RuntimeException("Unknown tracking code " + trackingCode);
			}
		} else {
			postService = new FinnishPostService();
		}
		return postService.getManualTrackingURL(trackingObject,null);
	}
	
	@Override
	public int getShippingStatus(Tracking trackingObject, int state) throws Exception {
		logger.debug("Getting shipping status for tracking object " + trackingObject.getId());
		String trackingCode = trackingObject.getTrackingCode();
		PostService postService = null;
		if (trackingCode == null || state < Order.STATE_SHIPPED_BY_PROVIDER) {
			return NOT_REGISTERED;
		} else if (state == Order.STATE_SHIPPED_BY_PROVIDER) {
			if (trackingCode.endsWith("CN")) {
				postService = new ChinaPostService();
			} else if (trackingCode.endsWith("HK")) {
				postService = new HongKongPostService();
			} else {
				throw new Exception("Unknown tracking code " + trackingCode);
			}
		} else {
			postService = new FinnishPostService();
		}
		try {
			return postService.getShippingStatus(trackingObject,state);
		} catch (Exception e) {
			if (e.getMessage() != null && e.getMessage().indexOf("post puked on tracking code") >= 0) {
				throw new Exception("Unknown tracking code " + trackingCode);
			}
			throw e;
		}
	}
	
	public void updateShippingStatus(Tracking trackingObject) throws Exception {
		trackingDAO.refresh(trackingObject);
		
		/* Get all orders for the whole bundle as well as for the tracking object (separately)
		 * and exclude all canceled orders (a multi-order bundle can have some order canceled). */
		List<Order> trackingObjectOrders = new ArrayList<Order>();
		for (Order order : orderDAO.getOrdersInTrackingObject(trackingObject)) {
			if (!order.getState().equals(Order.STATE_CLOSED_ORDER_CANCELED)) {
				trackingObjectOrders.add(order);
			}
		}
		List<Order> allOrdersInBundle = new ArrayList<Order>();
		for (Order order : orderDAO.getOrdersInBundle(trackingObject.getOrderBundle())) {
			if (!order.getState().equals(Order.STATE_CLOSED_ORDER_CANCELED)) {
				allOrdersInBundle.add(order);
			}
		}

		/* Safety check: make sure that all orders are in the same state. This might need to be changed
		 * later, but for now it's safer to have such a limitation, until we know what kind of valid
		 * exceptions there can be to this. */
		int oldState = trackingObjectOrders.get(0).getState();
		for (Order order : trackingObjectOrders) {
			if (!order.getState().equals(oldState)) {
				throw new RuntimeException("Not all orders are in the same state for tracking object " + trackingObject.getId() + ".");
			}
		}
		
		long daysInThisState = (System.currentTimeMillis() - trackingObjectOrders.get(0).
				getLatestStateChange().getUpdateDate().getTime()) / (1000*3600*24);
		
		int newState = NOT_REGISTERED;
		Exception fetchingException = null;
		try {
			newState = getShippingStatus(trackingObject,oldState);
		} catch (Exception e) {
			/* Tracking is not always possible. The most normal reasons are:
			 * 1.) Tracking page is temporarily down or too busy.
			 * 2.) In some cases FocalPrice will give a chinese tracking code. In these
			 *     cases we cannot trace the packet and it requires the operator to
			 *     fix it manually.
			 * 3.) Sometimes FocalPrice ships the packets using China post and the web based
			 *     tracking system of China Post doesn't work.
			 * 
			 * Store the error and possibly take some action on it after the "info came late"
			 * check below. */
			logger.debug("Couldn't fetch shipping status, but also check for \"info came late\": ",e);
			fetchingException = e;
			//TODO: at least on one occation the tracking code at FocalPrice had CHANGED! For order.id = 11057
			//we got the tracking code RC218101464HK and later when I checked this one by hand, the pages of
			//FocalPrice said that the tracking code was RA556540331CN. So if we get here several times, then
			//we could possibly check the pages of FocalPrice again to see if the tracking information has
			//changed. This can be good to do anyway since it seems like for multi-order bundles that are
			//shipped a few orders at a time, we get all tracking codes only when the last item in the bundle
			//is shipped. See also "gör ändring i FocalPriceProvider.handleShipped så att den vid shipping" i Test.java
		}
		
		/* Sometimes china post hasn't updated its web page although the packet is already shipped,
		 * so if the packet has been for more than four days in state "shipped by provider", then
		 * also check the Finnish post if the packet has arrived to Finland.
		 * 
		 * Note: wait as much as four days before checking this, because in some cases "Finland"
		 * isn't shown on the China Post's web page right away and then if you check a few days
		 * later also the country is specified (this was a problem for someuser@hotmail.com).
		 * We don't want to inform the customer erroneously that "china post screwed up". */
		boolean shippedByProviderInfoCameLate = false;
		if ((newState == NOT_REGISTERED || newState == WRONG_COUNTRY || newState == NOT_SENT || oldState == newState)
				&& oldState == Order.STATE_SHIPPED_BY_PROVIDER && daysInThisState > 4) {
			int temp = getShippingStatus(trackingObject,Order.STATE_SHIPPED_BY_POSTOFFICE);
			if (temp > Order.STATE_SHIPPED_BY_PROVIDER) {
				logger.info("Shipping detected late for tracking object " + trackingObject.getId() + ".");
				shippedByProviderInfoCameLate = true;
				newState = temp;
			} else if (newState == WRONG_COUNTRY || (newState == NOT_REGISTERED && daysInThisState > 14) || (newState == NOT_SENT && daysInThisState > 20)) {
				/* Sometimes China post screws up in one of these ways:
				 * 1.) They give the tracking information for the wrong packet (is detected
				 *     if the web page says that the packet is going to the wrong country).
				 * 2.) The packet hasn't shown up at the China post web page after two weeks
				 *     of waiting (in this case we draw the conclusion that China post shipped
				 *     the item without using the tracking code). In this case perhaps the
				 *     Finnish post can still take advantage of the tracking code(?). Posttracker
				 *     will in that case keep informing the customer.
				 * 3.) China post doesn't update the status to shipped.
				 *     
				 * In all of these cases inform the customer and remove the tracking entry. */
				if (trackingObject.getFollow()) {
					OrderBundle bundle = trackingObject.getOrderBundle();
					String subject = "Seurantatietoja ei saada";
					String email = "Moi,\n\nKiinan/Hong Kongin posti on valitettavasti kämmännyt ja";
					if (newState == WRONG_COUNTRY) {
						email += " antanut meille väärät seurantatiedot. Paketin seuranta ei ole sen takia"
							+ " saatavilla.";// Ilmoitathan mikäli pakettisi ei ole saapunut kuukauden sisällä"
							//+ " tästä päivästä.";
						
					} else {
						email += " laittanut pakettisi tavalliseen jakeluun eikä ole paketin seuranta saatavilla."
							+ " Pakettisi on jo saattanut lähteä kohti Suomea.";// Ilmoitathan mikäli pakettisi ei"
							//+ " ole saapunut kolmen viikon sisällä tästä päivästä.";
					}
					email += "\n\n";
					if (trackingObjectOrders.size() > 1) {
						email += "Tämä ilmoitus koskee nippua " + bundle.getId() + ".\n\n";
					} else {
						email += "Tämä ilmoitus koskee tilausta " + trackingObjectOrders.get(0).getId() + ".\n\n";
					}
					email += "Ystävällisin terveisin,\n" + bundle.getSeller().getSignature();
					EmailSender.sendEmailBatch(bundle.getSeller().getSystemEmail(),
							bundle.getCustomer().getEmail(),subject,email);
					String orders = "";
					for (Order order : trackingObjectOrders) {
						orders += order.getId() + " ";
						//order.setTracking(null);
					}
					logger.info("Shipped by provider: Stop following tracking for " + bundle.getCustomer().getEmail()
							+ " (trackingCode = " + trackingObject.getTrackingCode()
							+ ", bundle " + bundle.getId() + ")"
							+ ", for the following orders: " + orders);
					trackingObject.setFollow(false);
					/* Don't remove the old tracking object, because we need to have it in order to catch the
					 * situation where FocalPrice screws up and gives the same tracking code to several packets. */
					//trackingDAO.delete(trackingObject);
				} else {
					logger.debug("Shipped by provider: Not informing customer because tracking.follow = false (for tracking object " + trackingObject.getId() + ").");
				}
				return;
			}
		} else if (newState == NOT_REGISTERED && oldState == Order.STATE_SHIPPED_BY_POSTOFFICE && daysInThisState > 14) {
			/* Another problem is when the packet was indeed shipped from China but the Finnish post
			 * screws up so that the packet never shows up in the Finnish tracking system. Normally
			 * it shows up in the Finnish tracking system within a week after being shipped from
			 * China. If however it doesn't show up after two weeks, then determine that the Finnish
			 * post screwed up. */
			if (trackingObject.getFollow()) {
				OrderBundle bundle = trackingObject.getOrderBundle();
				String subject = "Seurantatietoja ei saada";
				String email = "Moi,\n\nSuomen posti on valitettavasti kämmännyt ja";
				email += " laittanut pakettisi tavalliseen jakeluun eikä ole paketin seuranta saatavilla."
					+ " Pakettisi on jo saattanut tulla perille. Ilmoitathan mikäli pakettisi ei"
					+ " ole saapunut kahden viikon sisällä tästä päivästä.";
				email += "\n\n";
				if (trackingObjectOrders.size() > 1) {
					email += "Tämä ilmoitus koskee nippua " + bundle.getId() + ".\n\n";
				} else {
					email += "Tämä ilmoitus koskee tilausta " + trackingObjectOrders.get(0).getId() + ".\n\n";
				}
				email += "Ystävällisin terveisin,\n" + bundle.getSeller().getSignature();
				EmailSender.sendEmailBatch(bundle.getSeller().getSystemEmail(),
						bundle.getCustomer().getEmail(),subject,email);
				String orders = "";
				for (Order order : trackingObjectOrders) {
					orders += order.getId() + " ";
					//order.setTracking(null);
				}
				logger.info("Shipped by postoffice: Stop following tracking for " + bundle.getCustomer().getEmail()
						+ " (trackingCode = " + trackingObject.getTrackingCode()
						+ ", bundle " + bundle.getId() + ")"
						+ ", for the following orders: " + orders);
				trackingObject.setFollow(false);
				/* Don't remove the old tracking object, because we need to have it in order to catch the
				 * situation where FocalPrice screws up and gives the same tracking code to several packets. */
				//trackingDAO.delete(trackingObject);
			} else {
				logger.debug("Shipped by postoffice: Not informing customer because tracking.follow = false (for tracking object " + trackingObject.getId() + ").");
			}
			return;
		}
		
		/* If we got a fetchingException above and the packet has been in this state for several days, then
		 * inform the operator or throw an exception. */
		if (fetchingException != null && daysInThisState > 4 && (oldState == newState || newState == NOT_REGISTERED || newState == NOT_SENT)) {
			if (fetchingException.getMessage() != null && fetchingException.getMessage().indexOf("Unknown tracking code") >= 0) {
				EmailSender.sendOperationSummaryReport("Unparsable tracking code detected",
						"Unparsable tracking code (" + trackingObject.getTrackingCode() + ") detected for "
						+ " tracking object " + trackingObject.getId() + ". Please correct this one"
						+ " manually. This packet cannot be traced as long as it has a faulty tracking code.",
						trackingObject.getOrderBundle().getProvider().getSystemEmail());
				return;
			} else {
				/* Some other kind of unknown error. */
				throw fetchingException;
			}
		}
		
		if (newState != NOT_REGISTERED && newState != NOT_SENT && oldState != newState) {
			if (newState == Order.STATE_SHIPPED_BY_POSTOFFICE
					|| (shippedByProviderInfoCameLate && newState != Order.STATE_FETCHED_BY_CUSTOMER)) {
				/* Inform the customer that the packet was shipped by postoffice in two cases:
				 * 1.) the packet was really just shipped by postoffice
				 * 2.) the packet was earlier shipped by postoffice (going undetected because
				 *     China post's web page isn't up to date) and the packet still isn't
				 *     fetched by the customer (no use to inform the customer if he has already
				 *     fetched the packet) */
				OrderShippedHandler.handleTrackingObjectShippedByPostOffice(trackingObject,
						trackingObjectOrders,oldState,allOrdersInBundle,shippedByProviderInfoCameLate);
			} else if (newState == Order.STATE_REACHED_DESTINATION_COUNTRY
					|| newState == Order.STATE_ARRIVED_TO_CUSTOMERS_POSTOFFICE
					|| newState == Order.STATE_FETCHED_BY_CUSTOMER
					|| newState == Order.STATE_CLOSED_RETURNED_TO_PROVIDER) {
				for (Order order : trackingObjectOrders) {
					order.setStateWithHistory(newState);
				}
				// Finally close the orders if the bundle was fetched by the customer
				if (newState == Order.STATE_FETCHED_BY_CUSTOMER) {
					for (Order order : trackingObjectOrders) {
						order.setStateWithHistory(Order.STATE_CLOSED);
					}
				}
			} else {
				throw new Exception("Internal error in GeneralPostService.updateShippingStatus (trackingObject "
						+ trackingObject.getId() + ", oldState = " + oldState + ", newState = " + newState + ")");
			}
		}
	}

}
