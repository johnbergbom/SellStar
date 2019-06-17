package fi.jonix.huutonet.order;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import fi.jonix.huutonet.domain.model.Order;
import fi.jonix.huutonet.domain.model.OrderBundle;
import fi.jonix.huutonet.domain.model.Product;
import fi.jonix.huutonet.domain.model.ProviderQuestion;
import fi.jonix.huutonet.domain.model.StateChange;
import fi.jonix.huutonet.domain.model.Tracking;
import fi.jonix.huutonet.domain.model.dao.MessageDAO;
import fi.jonix.huutonet.domain.model.dao.OrderBundleDAO;
import fi.jonix.huutonet.domain.model.dao.OrderDAO;
import fi.jonix.huutonet.domain.model.dao.ProviderQuestionDAO;
import fi.jonix.huutonet.domain.model.dao.TrackingDAO;
import fi.jonix.huutonet.email.EmailSender;
import fi.jonix.huutonet.exception.ProductNotFoundAtProviderException;
import fi.jonix.huutonet.exception.ProviderTemporarilyDownException;
import fi.jonix.huutonet.exception.SoldOutAtProviderException;
import fi.jonix.huutonet.order.ProviderHandler.ProductInfo;
import fi.jonix.huutonet.post.GeneralPostService;
import fi.jonix.huutonet.tools.StringUtilities;

/**
 * TODO: Fixa så att dessa informationsmail till kunderna görs som en koosteraportti eikä jokaiselle
 * tilaukselle erikseen. => Hmm, riittäisikö, että nippukohtaisesti lähetetään statustietoja?
 * 
 * @author john
 *
 */
@Component(value = "orderBundleHousekeeping")
public class OrderBundleHousekeeping {

	public static final Logger logger = Logger.getLogger(OrderBundleHousekeeping.class);

	@Autowired
	private MessageDAO messageDAO;
	
	@Autowired
	private OrderCancelationHandler orderCancelationHandler;
	
	@Autowired
	private OrderDAO orderDAO;
	
	@Autowired
	private OrderBundleDAO orderBundleDAO;
	
	@Autowired
	private TrackingDAO trackingDAO;
	
	@Autowired
	private GeneralPostService generalPostService;
	
	@Autowired
	private ProviderHandler providerHandler;
	
	@Autowired
	private ProviderQuestionDAO providerQuestionDAO;
	
	public void housekeeping(OrderBundle bundle) {
		orderBundleDAO.refresh(bundle);
		logger.debug("Running housekeeping for bundle " + bundle.getId());

		/* Add all non-closed orders to the order list (some order in a
		 * multi-order bundle could be canceled). */
		List<Order> bundleOrders = new ArrayList<Order>();
		for (Order order : orderDAO.getOrdersInBundle(bundle)) {
			if (order.getState().intValue() < Order.STATE_CLOSED) {
				bundleOrders.add(order);
			}
		}

		/* Abort if no open orders were found in the bundle. */
		if (bundleOrders.size() == 0) {
			return;
		}

		/* For resendings the following case can come: one order from a multi-order bundle
		 * was resent (and the other order in the bundle is closed). Therefore make sure
		 * that we only handle such tracking objects that are actually in use (in the normal
		 * non-resending case we'll take all tracking objects (exception: partly shipped
		 * packages that are already delivered to the customer)). */
		List<Tracking> trackingObjects = new ArrayList<Tracking>();
		for (Tracking tr : trackingDAO.getTrackingObjectsInBundle(bundle)) {
			for (Order order : bundleOrders) {
				if (order.getTracking() != null && order.getTracking().getId().equals(tr.getId())) {
					trackingObjects.add(tr);
					break;
				}
			}
		}
		
		/* Find out how many days ago the customer was last informed. */
		long daysSinceLastUpdate = (System.currentTimeMillis() - bundle.getLastCustomerUpdateSent().getTime()) / (1000*3600*24);

		/* Check things that should be done bundlewise or tracking object wise. */
		sendBundleOrTrackingObjectSpecificInfo(bundle,bundleOrders,daysSinceLastUpdate,trackingObjects);

		StringBuffer orderInfo = new StringBuffer();
		StringBuffer stockShortageAfterPaid1 = new StringBuffer();
		StringBuffer stockShortageAfterPaid2 = new StringBuffer();
		Map<Long,String> updatedOrderIds = new HashMap<Long,String>();
		int updates = 0;

		/* Go through all individual orders of the bundle. */
		for (Order order : bundleOrders) {
			/* Find out how many days the order has been in the current state. */
			StateChange latestStateChange = order.getLatestStateChange();
			if (!order.getState().equals(latestStateChange.getState())) {
				/* The only case where this might come is if the operator has manually updated
				 * the state of an order without updating also the stateChanges (or if there
				 * is a bug in the code so that in some place order.setState is used instead
				 * of order.setStateWithHistory). */
				throw new RuntimeException("State and latest state change don't agree for order " + order.getId());
			}
			long daysInThisState = (System.currentTimeMillis() - latestStateChange.getUpdateDate().getTime()) / (1000*3600*24);

			/* Find out how many days ago this product was ordered at the provider (if at all ordered). */
			Date latestOrderedDate = order.getLatestOrderedDate();
			long daysSinceOrdered = -1;
			if (latestOrderedDate != null) {
				daysSinceOrdered = (System.currentTimeMillis() - latestOrderedDate.getTime()) / (1000*3600*24);
			}

			/* Go through the conditionals: if the order has been for a certain time in a certain state,
			 * then do certain things according to the logic below. */
			if (daysSinceLastUpdate < 0) {
				throw new RuntimeException("Error: daysSinceLastUpdate is in the future for order " + order.getId());
			} else if (order.getState().equals(Order.STATE_NEW_OUT_OF_STOCK)) {
				updates += handleNewOutOfStock(order,daysInThisState,daysSinceLastUpdate,orderInfo,
						updatedOrderIds);
			} else if (order.getState().equals(Order.STATE_NEW)
					|| order.getState().equals(Order.STATE_PAID_TOO_LITTLE_ERROR)) {
				if ((order.getState().equals(Order.STATE_NEW) && daysInThisState > 60)
						|| daysInThisState > 90) {
					/* Close the order after two months if it's not paid for, or after three months if it's
					 * just partly paid for. NOTE: it's important to close these orders, because otherwise
					 * we'll be paying too much vat to the government since we SellStar nowadays uses an
					 * order based verification for the bookkeeping. */
					closeOldNewOrFaultyContactInfoOrTooLittlePaid(order);
				} else if (daysInThisState > 6 && daysSinceLastUpdate > 9) {
					/* Due to the fact that it's possible to pay for several orders with
					 * one account transfer, payment reminders cannot be handle purely
					 * orderwise. Therefore payment reminders are handled elsewhere. */
				}
			} else if (order.getState().equals(Order.STATE_CUSTOMER_PAID)) {
				/* An order can stay in the state "customer paid" without moving to the
				 * "customer paid informed" in the following case: the customer has a multi-
				 * order bundle that has some order in the "new out of stock" state but still
				 * the customer has paid for ALL orders in the bundle (then faultyPaymentFixer
				 * sets the states of the other orders to "customer paid", but
				 * CustomerPaymentInformer doesn't change the state to "paid informed" as
				 * long as one order is in state "new out of stock"). However the problematic
				 * order should be either automatically closed (if no new batch comes in) or
				 * updated to state "new" (if no new batch comes within a week). So let's
				 * here inform the operator that something is wrong. */
				if (daysInThisState > 6) {
					informOperatorAboutSittingOrder(order,daysInThisState);
				}
			} else if (order.getState().equals(Order.STATE_CUSTOMER_PAID_INFORMED)
					|| order.getState().equals(Order.STATE_SHOULD_RESEND)) {
				if (daysInThisState > 1) {
					// Don't update "updates" here, because we don't want this to trigger
					// informing of the customer.
					order.setStateWithHistory(Order.STATE_STOCK_SHORTAGE_BEFORE_ORDER);
				}
			} else if (order.getState().equals(Order.STATE_NO_LONGER_FOR_SALE_AT_PROVIDER_EARLY)) {
				if (daysInThisState > 1) {
					throw new RuntimeException("Order " + order.getId() + " has been too long in state "
							+ order.getState() + " (it should have been automatically canceled - bug in the program?).");
				}
			} else if (order.getState().equals(Order.STATE_ORDERED_FROM_PROVIDER_NO_ID)) {
				handleOrderedWithoutId(order,daysInThisState);
			} else if (order.getState().equals(Order.STATE_ORDER_CONFIRMED_BY_PROVIDER)) {
				// Do nothing here, because delayed shipments are taken care of elsewhere (bundle specifically).
			} else if (order.getState().equals(Order.STATE_STOCK_SHORTAGE_BEFORE_ORDER)
					|| order.getState().equals(Order.STATE_AWAITING_STOCK)
					|| order.getState().equals(Order.STATE_WAITING_FOR_SUPPLIER)
					|| order.getState().equals(Order.STATE_STOCK_SHORTAGE)) {
				updates += informAboutStockShortageAfterPaid(order,daysInThisState,
						daysSinceLastUpdate,daysSinceOrdered,orderInfo,stockShortageAfterPaid1,
						stockShortageAfterPaid2,updatedOrderIds,bundleOrders.size());
			} else if (order.getState().equals(Order.STATE_FULFILMENT_CENTER)) {
				/* The orders should never be in this state for more than a week, because
				 * isShippingDelayed should have set it "stock shortage" within a week.
				 * So it's unexpected that the order stays long in this state. Therefore
				 * let's inform the operator if that happens. */
				if (daysInThisState > 6) {
					informOperatorAboutSittingOrder(order,daysInThisState);
				}
			} else if (order.getState().equals(Order.STATE_NO_LONGER_FOR_SALE_AT_PROVIDER_LATE)) {
				if (daysInThisState > 1) {
					throw new RuntimeException("Order " + order.getId() + " has been too long in state "
							+ order.getState() + " (it should have been automatically canceled - bug in the program?).");
				}
			} else if (order.getState().intValue() < Order.STATE_CLOSED
					&& !order.getState().equals(Order.STATE_SHIPPED_BY_PROVIDER)
					&& !order.getState().equals(Order.STATE_SHIPPED_BY_POSTOFFICE)) {
				/* Inform the operator after a week. Exception: don't send information
				 * regarding orders being in state "shipped by provider", because they
				 * are taken care of in sendBundleOrTrackingObjectSpecificInfo (or if the
				 * order stays for very long in state "shipped by provider", then
				 * GeneralPostService will at some point determine that the post screwed
				 * up and tell the customer that packet tracking was stopped).
				 * The same thing applies to "shipped by postoffice". */
				informOperatorAboutSittingOrder(order,daysInThisState);
			}
		}
		
		if (updates > 0) {
			/* Send a summary of all status updates that were made. */
			sendUpdateSummary(bundle,bundleOrders,updates,orderInfo,stockShortageAfterPaid1,stockShortageAfterPaid2,updatedOrderIds);
		} else {
			/* Skip sending these "Tilauksesi edistyy" because these emails don't provide
			 * any more information regarding the order (we don't want to spam the customer
			 * with unnecessary information). */
			//sendGeneralBundleInfo(bundle, bundleOrders, daysSinceLastUpdate);
		}
	}

	/* This method finds the latest state changes for all orders in the bundle and
	 * of those it returns the oldest. In other words it returns the StateChange entry
	 * for the order that has been for the longest time in its current state. */
	private StateChange getLatestStateChangeForOldestOrder(List<Order> bundleOrders) {
		StateChange oldest = null;
		for (Order order : bundleOrders) {
			StateChange sc = order.getLatestStateChange();
			if (oldest == null || sc.getUpdateDate().getTime() < oldest.getUpdateDate().getTime()) {
				oldest = sc;
			}
		}
		return oldest;
	}
	
	/* This method finds the latest state changes for all orders in the bundle and
	 * of those it returns the newest. In other words it returns the StateChange entry
	 * for the order that has been for the shortest time in its current state. */
	private StateChange getLatestStateChangeForNewestOrder(List<Order> bundleOrders) {
		StateChange newest = null;
		for (Order order : bundleOrders) {
			StateChange sc = order.getLatestStateChange();
			if (newest == null || sc.getUpdateDate().getTime() > newest.getUpdateDate().getTime()) {
				newest = sc;
			}
		}
		return newest;
	}
	
	/* This method returns the order date of the bundle, which is considered to be
	 * the order date for the earliest placed order in the bundle. */
	private Date getOrderDateOfOrderList(List<Order> bundleOrders) {
		Date oldest = null;
		for (Order order : bundleOrders) {
			Date orderDate = order.getLatestOrderedDate();
			if (orderDate != null && (oldest == null || orderDate.getTime() < oldest.getTime())) {
				oldest = orderDate;
			}
		}
		return oldest;
	}
	
	private boolean sendTrackingObjectSpecificInfo(Tracking tracking, List<Order> trackingObjectOrders,
			long daysSinceLastUpdate, List<Order> allOrdersInBundle) {
		/* Find out the state of this tracking object. */
		boolean allOrdersInSameState = true;
		int state = trackingObjectOrders.get(0).getState();
		for (Order order : trackingObjectOrders) {
			if (!order.getState().equals(state)) {
				allOrdersInSameState = false;
			}
		}

		/* Find out how many days ago this tracking object was ordered at the provider (if at all ordered). */
		Date latestOrderedDate = getOrderDateOfOrderList(trackingObjectOrders);
		long daysSinceOrdered = -1;
		if (latestOrderedDate != null) {
			daysSinceOrdered = (System.currentTimeMillis() - latestOrderedDate.getTime()) / (1000*3600*24);
		}

		/* Find out how many days the order has been in the current state. */
		StateChange oldestBundleStateChange = getLatestStateChangeForOldestOrder(trackingObjectOrders);
		StateChange newestBundleStateChange = getLatestStateChangeForNewestOrder(trackingObjectOrders);
		if (allOrdersInSameState && (state != oldestBundleStateChange.getState().intValue() || state != newestBundleStateChange.getState().intValue())) {
			/* The only case where this might come is if the operator has manually updated
			 * the state of an order without updating also the stateChanges (or if there
			 * is a bug in the code so that in some place order.setState is used instead
			 * of order.setStateWithHistory). */
			throw new RuntimeException("State and oldest bundle state change don't agree for bundle " + trackingObjectOrders.get(0).getOrderBundle().getId());
		}
		long daysInThisState = (System.currentTimeMillis()
				- oldestBundleStateChange.getUpdateDate().getTime()) / (1000*3600*24);
		
		if (allOrdersInSameState) {
			if (state == Order.STATE_SHIPPED_BY_PROVIDER) {
				if (tracking != null && tracking.getFollow()) {
					handleShippedByProviderWithTracking(tracking,trackingObjectOrders,
							daysInThisState,daysSinceLastUpdate,oldestBundleStateChange,allOrdersInBundle);
				} else {
					handleShippedByProviderWithoutTracking(trackingObjectOrders.get(0).getOrderBundle(),
							trackingObjectOrders,daysInThisState,daysSinceLastUpdate,oldestBundleStateChange);
				}
				return true;
			} else if (state == Order.STATE_SHIPPED_BY_POSTOFFICE) {
				if (tracking != null && tracking.getFollow()) {
					handleShippedByPostoffice(tracking,trackingObjectOrders,daysInThisState,
							daysSinceLastUpdate,oldestBundleStateChange,allOrdersInBundle);
				} else {
					/* If tracking is no longer available, then handle this one the same
					 * way as for the "shipped from provider" case (close after some time). */
					handleShippedByProviderWithoutTracking(trackingObjectOrders.get(0).getOrderBundle(),
							trackingObjectOrders,daysInThisState,daysSinceLastUpdate,oldestBundleStateChange);
				}
				return true;
			} else if (state == Order.STATE_REACHED_DESTINATION_COUNTRY && tracking != null) {
				if (daysInThisState > 10) {
					handleTrackingObjectStuckAtPostOffice(tracking,trackingObjectOrders,daysInThisState,oldestBundleStateChange,allOrdersInBundle);
					return true;
				}
			} else if (state == Order.STATE_ARRIVED_TO_CUSTOMERS_POSTOFFICE) {
				/* We probably don't need to inform the customer here, because seuraapostia.fi
				 * takes care of reminding the customer. */
				//informOperatorAboutSittingOrder(order,daysInThisState);
				return true;
			}
		}
		return isShippingDelayed(trackingObjectOrders.get(0).getOrderBundle(),
				trackingObjectOrders,daysInThisState,daysSinceLastUpdate,daysSinceOrdered,allOrdersInBundle);
	}

	/**
	 * Sometimes the packet gets stuck at the Finnish post office. This is most likely because
	 * they screwed up and forgot to update the status after delivering it to the customer, or
	 * else they delivered the packet using the normaly delivery (of non-trackable deliveries).
	 * We cannot take responsibility for when the post office screws up, so let's inform the
	 * customer that he needs to get in touch with the post office in case he hasn't received
	 * his packet and then we close the order(s).
	 * 
	 * Note: we don't need to stop the tracking in posttracking, because posttracking will
	 * automatically stop the tracking after the packet has been in state "visible" for
	 * more than 30 days.
	 */
	private void handleTrackingObjectStuckAtPostOffice(Tracking tracking, List<Order> trackingObjectOrders,
			long daysInThisState, StateChange latestStateChange, List<Order> allOrdersInBundle) {
		DateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy");
		OrderBundle bundle = tracking.getOrderBundle();
		if (daysInThisState > 10) {
			String email = "Moi,\n\n";
			if (allOrdersInBundle.size() > 1) {
				email += "Nippusi " + bundle.getId() + " sisältää seuraavat tilaukset:\n\n";
				for (Order order : trackingObjectOrders) { //TODO: use messageDAO.getText("oneOrderSimple",...) here
					email += "Tilaus " + order.getId() + ": " + order.getAd().getAdTemplate().getHeadline() + "\n";
				}
			} else {
				email += "Tiedote liittyen tilaukseesi " + trackingObjectOrders.get(0).getId()
					+ ", " + trackingObjectOrders.get(0).getAd().getAdTemplate().getHeadline() + ".\n";
			}
			try {
				email += "\nPakettisi on ollut postin lähetysseurannan mukaan postissa "
					+ dateFormat.format(latestStateChange.getUpdateDate()) + " lähtien. Joskus käy niin,"
					+ " että posti vahingossa jättää päivittämättä pakettien seurantatietoja tai laittaa"
					+ " paketit tavalliseen jakeluun vaikka onkin kirjattu kirje. Oletko saanut pakettisi?"
					+ " Otathan yhteyttä postiin mikäli pakettisi ei ole saapunut viikon sisällä tästä"
					+ " päivästä. Me emme voi valitettavasti vaikuttaa postin kulkuun.\n\n"
					+ "Postin lähetysseurannan osoite:\n"
					+ generalPostService.getManualTrackingURL(tracking, trackingObjectOrders) + "\n\n"
					+ "Seurantakoodisi on " + tracking.getTrackingCode() + "\n\n";
			} catch (Exception e) {
				throw new RuntimeException("Unknown error when handling tracking object " + tracking.getId());
			}
			email += "Ystävällisin terveisin,\n" + bundle.getSeller().getSignature();
			EmailSender.sendEmailBatch(bundle.getSeller().getSystemEmail(),bundle.getCustomer().getEmail(),
					"Paketti saapunut?",email);
			//bundle.setLastCustomerUpdateSent(new Date());
			for (Order order : trackingObjectOrders) {
				logger.info("Closing order " + order.getId() + " (tracking object " + tracking.getId() + ") because the packet might be stuck at the post office.");
				order.setStateWithHistory(Order.STATE_CLOSED);
			}
		}
	}

	/**
	 * @return True if only bundle specific info or tracking object specific info needs to be sent
	 * to the customer, false if the individual orders need to be gone through.
	 */
	private void sendBundleOrTrackingObjectSpecificInfo(OrderBundle bundle, List<Order> bundleOrders, long daysSinceLastUpdate, List<Tracking> trackingObjects) {
		/* Faulty contact info is a bundle specific problem, so check that one first. */
		if (bundleOrders.get(0).getState().intValue() == Order.STATE_FAULTY_CONTACT_INFO) {
			StateChange oldestBundleStateChange = getLatestStateChangeForOldestOrder(bundleOrders);
			long daysInThisState = (System.currentTimeMillis()
					- oldestBundleStateChange.getUpdateDate().getTime()) / (1000*3600*24);
			if (daysInThisState > 60) {
				/* Close the order after two months. Don't inform the customer about this one. */
				closeOldNewOrFaultyContactInfoOrTooLittlePaid(bundleOrders);
			} else if (daysSinceLastUpdate > 6) {
				sendReminderAboutFaultyContactInfo(bundle,bundleOrders);
			}
			bundleOrders.clear(); //clear the order bundle list to make sure that no more status messages are sent out for this bundle
			return;
		}
		
		/* Check tracking for shipped orders. */
		List<Order> handledOrders = new ArrayList<Order>();
		if (trackingObjects.size() > 0) {
			for (Tracking tracking : trackingObjects) {
				List<Order> trackingObjectOrders = new ArrayList<Order>();
				for (Order order : bundleOrders) {
					if (order.getTracking() != null && order.getTracking().getId().equals(tracking.getId())) {
						trackingObjectOrders.add(order);
					}
				}
				/* There might not be any order in trackingObjectOrders in case the orders in
				 * this tracking object was already delivered to the customer and the orders
				 * thus closed. */
				if (trackingObjectOrders.size() > 0) {
					if (sendTrackingObjectSpecificInfo(tracking,trackingObjectOrders,daysSinceLastUpdate,bundleOrders)) {
						handledOrders.addAll(trackingObjectOrders);
					}
				}
			}
		}
		
		/* Some orders might not have a tracking code, so check also those. The following cases are recognized:
		 * 1.) No tracking code was even bought from the provider,
		 * 2.) The provider messed up and sent the packet without tracking (although we paid for it),
		 * 3.) The bundle was shipped in several batches of which the first batch was already shipped
		 *     and the second one isn't yet shipped. */
		if (bundleOrders.size() > handledOrders.size()) {
			List<Order> previousBundleOrderList = new ArrayList<Order>();
			previousBundleOrderList.addAll(bundleOrders);
			bundleOrders.removeAll(handledOrders);
			if (sendTrackingObjectSpecificInfo(null,bundleOrders,daysSinceLastUpdate,previousBundleOrderList)) {
				bundleOrders.clear();
			}
		} else {
			/* Remove all handled orders so that they aren't handled again. */
			bundleOrders.clear();
		}
	}
	
	/**
	 * @return True if shipping was delayed, or else false.
	 */
	private boolean isShippingDelayed(OrderBundle bundle, List<Order> orderList,
			long daysInThisState, long daysSinceLastUpdate, long daysSinceOrdered, List<Order> allOrdersInBundle) {
		for (Order order : orderList) {
			/* If a week has gone since the bundle was ordered from the provider and the bundle
			 * still isn't shipped, then inform the customer that there is some problem with
			 * the bundle, and then set the state to "stock shortage" (then the customer will
			 * be informed about the stock shortage a week later (unless the product is shipped
			 * by then). */
			if (daysSinceLastUpdate > 6 && daysSinceOrdered > 6
					&& order.getState().intValue() >= Order.STATE_ORDERED_FROM_PROVIDER_NO_ID
					&& order.getState().intValue() < Order.STATE_SHIPPED_BY_PROVIDER
					&& !order.getState().equals(Order.STATE_STOCK_SHORTAGE)
					&& !order.getState().equals(Order.STATE_CUSTOMER_CANCEL_CONFIRMED)
					&& !order.getState().equals(Order.STATE_PROVIDER_CANCEL_PENDING)
					&& !order.getState().equals(Order.STATE_PROVIDER_CANCEL_CONFIRMED)) {
				/* Inform the operator if the bundle hasn't been shipped after two weeks.
				 * => UPDATE: informing of operator is moved to stockShortageAfterPaid1 */
				/*if (daysInThisState > 13 && daysSinceLastUpdate > 6) {
					informOperatorAboutSittingOrder(order,daysInThisState);
				}*/
				
				/*String email = messageDAO.getText("generalProblem", bundle.getSeller(), order.getMarket().getLanguage(),
					"ORDER_ID",""+order.getId(),
					"SIGNATURE",bundle.getSeller().getSignature());*/
				String email = "Moi,\n\n";
				if (allOrdersInBundle.size() > 1) {
					email += "Nippusi " + bundle.getId();
				} else {
					email += "Tilauksesi " + order.getId();
				}
				email += " käsittelyssä on ilmennyt ongelmia. Tiedoitamme"
					+ " asiasta enemmän viikon sisällä.\n\n";
				if (allOrdersInBundle.size() > orderList.size()) {
					email += "Tämä koskee nipun seuraavia tilauksia:\n";
				} else {
					email += "Nipussa on seuraavat tilaukset:\n";
				}
				for (Order o : orderList) {
					if (o.getState().intValue() >= Order.STATE_ORDERED_FROM_PROVIDER_NO_ID
							&& o.getState().intValue() < Order.STATE_SHIPPED_BY_PROVIDER
							&& !o.getState().equals(Order.STATE_STOCK_SHORTAGE)
							&& !o.getState().equals(Order.STATE_CUSTOMER_CANCEL_CONFIRMED)
							&& !o.getState().equals(Order.STATE_PROVIDER_CANCEL_PENDING)
							&& !o.getState().equals(Order.STATE_PROVIDER_CANCEL_CONFIRMED)) {
						o.setStateWithHistory(Order.STATE_STOCK_SHORTAGE);
						email += "Tilaus " + o.getId() + ": " + o.getAd().getAdTemplate().getHeadline() + "\n"; //TODO: use messageDAO.getText("oneOrderSimple",...) here
					}
				}
				if (allOrdersInBundle.size() > orderList.size()) {
					email += "\nNipussa on muitakin tilauksia, mutta niistä tiedotetaan erikseen.\n\n";
				} else {
					email += "\n";
				}
				email += "Ystävällisin terveisin,\n" + bundle.getSeller().getSignature();
				EmailSender.sendEmailBatch(bundle.getSeller().getSystemEmail(),
						bundle.getCustomer().getEmail(),"Ongelmia tilauksen käsittelyssä",email);
				bundle.setLastCustomerUpdateSent(new Date());
				logger.debug("Asking the provider why bundle " + bundle.getId() + " ("
						+ bundle.getProviderOrderId() + ") isn't yet shipped.");
				try {
					//TODO: the message to provider can be made better: if some orders in the bundle are shipped,
					//then we can ask "Why isn't product XXXXX yet shipped? Is it out of stock?" => this probably
					//isn't super important. The FocalPrice staff still understands what it's about.
					List<ProviderQuestion> providerQuestionList = providerQuestionDAO.getProviderQuestionForBundle(bundle);
					ProviderQuestion providerQuestion = null;
					if (providerQuestionList.size() > 0) {
						providerQuestion = providerQuestionList.get(0);
					}
					providerHandler.addQuestionToProvider(providerQuestion, bundle, "Why isn't this order shipped",
							"Why isn't this order shipped yet?", ProviderHandler.PROVIDER_CONVERSATION_TYPE_SHIPPING_STATUS);
				} catch (Exception e) {
					logger.error("Failed adding question to provider: ", e);
					throw new RuntimeException("Failed adding question to provider: " + e.getMessage());
				}
				/*EmailSender.sendOperationProblemReport("Automatically added question to provider",
						"Automatically added question to provider for bundle " + bundle.getId() + " ("
						+ bundle.getProviderOrderId() + "). Please check if everything is alright and if it is, then"
						+ " change the code so that no email is sent to the operator anymore for this event.");*/
				return true;
			}
		}
		return false;
	}
	
	private void sendUpdateSummary(OrderBundle bundle, List<Order> bundleOrders, int updates, StringBuffer orderInfo,
			StringBuffer stockShortageAfterPaid1, StringBuffer stockShortageAfterPaid2, Map<Long,String> updatedOrderIds) {
		String email = null;
		if (bundleOrders.size() > 1) {
			if (updates == 1) {
				email = "Moi,\n\nSeuraava nipun " + bundle.getId() + " tilauksen status on päivitetty:\n\n";
			} else {
				email = "Moi,\n\nSeuraavat nipun " + bundle.getId() + " tilausten statukset ovat päivitettyjä:\n\n";
			}
		} else {
			email = "Moi,\n\nSeuraavan tilauksen status on päivitetty:\n\n";
		}
		email += orderInfo.toString(); // + "\nYstävällisin terveisin,\n" + bundle.getSeller().getSignature();
		if (stockShortageAfterPaid1.length() > 0 || stockShortageAfterPaid2.length() > 0) {
			email += "\nLisätietoja:\n\n";
			email += "\n" + stockShortageAfterPaid1.toString();
			email += "\n" + stockShortageAfterPaid2.toString();
			email += "\n";
		}
		if (bundleOrders.size() > updates) {
			email += "\n\nMuita nipun tilauksia:\n";
			for (Order order : bundleOrders) {
				if (updatedOrderIds.get(order.getId()) == null) {
					email += "Tilausnumero " + order.getId() + ": " + OrderInformation.getStateName(order.getState()) + "\n";
				}
			}
		}
		email += "\nYstävällisin terveisin,\n" + bundle.getSeller().getSignature();
		EmailSender.sendEmailBatch(bundle.getSeller().getSystemEmail(),bundle.getCustomer().getEmail(),
				"Statuspäivitys",email);
		bundle.setLastCustomerUpdateSent(new Date());
		/*if (bundleOrders.size() > 1) {
			EmailSender.sendOperationProblemReport("OrderBundleHousekeeping",
					"OrderBundleHousekeeping sent out a Statuspäivitys to address "
					+ bundle.getCustomer().getEmail() + " (bundle " + bundle.getId() + "). Did it look sensible?\n\n"
					+ "Does it look sensible also for multi-order bundles?");
		}*/
	}
	
	private void closeOldNewOrFaultyContactInfoOrTooLittlePaid(Order order) {
		/* Note: let's not zero the order.targetedPaid nor finalSalesPrice here,
		 * because if the customer never canceled the order, then he should get a
		 * reminder about that if he ever orders some other product in the future.
		 * In addition some customers delay the payment so long that the order is
		 * automatically closed, and when this happens we cannot re-open the order
		 * if it has been zeroed.
		 * => UPDATE: it's important that finalSalesPrice is zeroed here because
		 * otherwise we'll be paying too much vat to the government. */
		orderCancelationHandler.closeOldNewOrFaultyContactInfoOrTooLittlePaid(order);
	}
	
	private void closeOldNewOrFaultyContactInfoOrTooLittlePaid(List<Order> bundleOrders) {
		for (Order order : bundleOrders) {
			closeOldNewOrFaultyContactInfoOrTooLittlePaid(order);
		}
	}
	
	private void sendReminderAboutFaultyContactInfo(OrderBundle bundle, List<Order> bundleOrders) {
		Order order = bundleOrders.get(0);
		String contextPath = messageDAO.getText("contextPath", bundle.getSeller(),order.getMarket().getLanguage());
		String email = messageDAO.getText("faultyContactInfoReminder", bundle.getSeller(), order.getMarket().getLanguage(),
				"GOOGLE_ORDER_URL",bundle.getSeller().getStoreUrl(),
				"CONTEXT_PATH",contextPath,
				"SIGNATURE_URL_ENC",StringUtilities.escapeString2URL(bundle.getSeller().getSignature()),
				"REFERENCE_NUMBER",order.getCustomerReferenceNumber(),
				"FIRST_NAME_URL_ENC",StringUtilities.escapeString2URL(bundle.getFirstName()),
				"LAST_NAME_URL_ENC",StringUtilities.escapeString2URL(bundle.getLastName()),
				"CUSTOMER_ADDRESS_URL_ENC",StringUtilities.escapeString2URL(bundle.getAddress()),
				"CUSTOMER_POSTCODE_URL_ENC",StringUtilities.escapeString2URL(bundle.getPostcode()),
				"CUSTOMER_CITY_URL_ENC",StringUtilities.escapeString2URL(bundle.getCity()),
				"CUSTOMER_EMAIL_URL_ENC",StringUtilities.escapeString2URL(bundle.getCustomer().getEmail()),
				"SIGNATURE",bundle.getSeller().getSignature());
		EmailSender.sendEmailBatch(bundle.getSeller().getSystemEmail(),bundle.getCustomer().getEmail(),
				"Muistutus virheellisista yhteystiedoista",email);
		bundle.setLastCustomerUpdateSent(new Date());
	}
	
	private int handleNewOutOfStock(Order order, long daysInThisState, long daysSinceLastUpdate, StringBuffer orderInfo, Map<Long,String> updatedOrderIds) {
		boolean outOfStock = false;
		/* Here we need to use checkDataBase = false because otherwise getProductInfo will ALWAYS return
		 * "out of stock" since the fact that this order is in state "new out of stock" then triggers
		 * getProductInfo to return "out of stock". Even if this would be accounted for by sending this
		 * order as a parameter to getProductInfo, that still wouldn't solve the problem, because if
		 * there are two orders for the same product and both are in state "out of stock", then they will
		 * never get updated if we use checkDataBase = true. */
		ProductInfo prodInfo = providerHandler.getProductInfo(order.getProduct(), false/*order.getGoogleOrder()*/);
		if (prodInfo.getProductState().equals(Product.STATE_UNKNOWN)) {
			logger.info("Provider temporarily down - cannot check if order " + order.getId() + " is still out of stock,"
					+ " or else the product is no longer for sale at provider, but the not found counter isn't yet great enough.");
			return 0;
		} else if (prodInfo.getProductState().equals(Product.STATE_NO_LONGER_FOR_SALE)) {
			orderCancelationHandler.cancelWhenNoLongerForSale(order);
			orderInfo.append("Tilausnumero " + order.getId() + ": Tuote " + order.getAd().getAdTemplate().getHeadline()
					+ " ei ole enää saatavilla.\n");
			updatedOrderIds.put(order.getId(),"");
			//order.getOrderBundle().setLastCustomerUpdateSent(new Date()); //should this one really be updated here?
			return 1;
		} else if (prodInfo.getProductState().equals(Product.STATE_OUT_OF_STOCK)) {
			outOfStock = true;
		}
		if (!outOfStock) {
			/* Send the payment information if no longer in state outOfStock. */
			order.setStateWithHistory(Order.STATE_NEW);
			EmailSender.sendEmailBatch(order.getOrderBundle().getSeller().getSystemEmail(),
					order.getOrderBundle().getCustomer().getEmail(),"Tilaus vastaanotettu",
					OrderCreator.generatePaymentInformationEmailForBundle(order.getOrderBundle(),order));
			/*orderInfo.append(messageDAO.getText("newBatchReceivedForMultiOrderBundle", order.getOrderBundle().getSeller(), order.getMarket().getLanguage(),
					"ORDER_ID",""+order.getId(),
					"AD_HEADLINE",order.getAd().getAdTemplate().getHeadline()) + "\n");*/
			order.getOrderBundle().setLastCustomerUpdateSent(new Date());
			return 0;
		} else if (daysInThisState > 6 && daysSinceLastUpdate > 6) {
			/* Close the order and inform the customer if a new batch didn't come within a week. */
			orderCancelationHandler.closeBecauseNewBatchDidNotCome(order);
			orderInfo.append("Tilausnumero " + order.getId() + ": Tuotteen " + order.getAd().getAdTemplate().getHeadline()
					+ " täydennyserä ei valitettavasti tullut.\n");
			//order.getOrderBundle().setLastCustomerUpdateSent(new Date()); //should this one really be updated here?
			updatedOrderIds.put(order.getId(),"");
			return 1;
		} else {
			logger.debug("Order " + order.getId() + " is still in state \"new out of stock\" but don't yet close it.");
		}
		return 0;
	}
	
	/* The same message is sent no matter if the product has gone out of stock before or after
	 * ordering at provider. */
	private int informAboutStockShortageAfterPaid(Order order, long daysInThisState,
			long daysSinceLastUpdate, long daysSinceOrdered, StringBuffer orderInfo,
			StringBuffer stockShortageAfterPaid1, StringBuffer stockShortageAfterPaid2,
			Map<Long,String> updatedOrderIds, int bundleSize) {
		Boolean veryOld = null;
		if (daysSinceLastUpdate > 6 && (daysSinceOrdered > 20 || daysInThisState > 13)) {
			veryOld = true;
		} else if (daysSinceLastUpdate > 6 && (daysSinceOrdered > 13 || daysInThisState > 6)) {
			veryOld = false;
		}
		
		if (order.getState().equals(Order.STATE_STOCK_SHORTAGE_BEFORE_ORDER)) {
			boolean productInStock;
			/* Here we need to use checkDataBase = false because otherwise getProductInfo will ALWAYS return
			 * "out of stock" since the fact that this order is in state "stock shortage before order" then
			 * triggers getProductInfo to return "out of stock". Even if this would be accounted for by sending
			 * this order as a parameter to getProductInfo, that still wouldn't solve the problem, because if
			 * there are two orders for the same product and both are in state "stock shortage before order",
			 * then they will never get updated if we use checkDataBase = true. */
			ProductInfo prodInfo = providerHandler.getProductInfo(order.getProduct(), false/*order.getGoogleOrder()*/);
			if (prodInfo.getProductState().equals(Product.STATE_UNKNOWN)) {
				logger.info("Provider temporarily down - cannot check if order " + order.getId() + " is still out of stock,"
						+ " or else the product is no longer for sale at provider, but the not found counter isn't yet great enough.");
				return 0;
			} else if (prodInfo.getProductState().equals(Product.STATE_NO_LONGER_FOR_SALE)) {
				orderCancelationHandler.cancelWhenNoLongerForSale(order);
				orderInfo.append("Tilausnumero " + order.getId() + ": ei ole enää saatavilla ("
						+ order.getAd().getAdTemplate().getHeadline() + ").\n");
				updatedOrderIds.put(order.getId(),"");
				return 1;
			} else if (prodInfo.getProductState().equals(Product.STATE_OUT_OF_STOCK)) {
				productInStock = false;
			} else {
				productInStock = true;
			}

			if (veryOld != null) {
				/* If the product is in stock and this is a multi-order bundle, then problem is that
				 * some _other_ order in the bundle is out of stock, so we don't need to inform the
				 * customer about this one. However if this is the only order in the bundle and it
				 * still hasn't been ordered at provider, then that's fishy, so in that case don't
				 * do a quick return here. */
				if (productInStock && bundleSize > 1) {
					orderInfo.append("Tilausnumero " + order.getId() + ": löytyy varastolla ("
							+ order.getAd().getAdTemplate().getHeadline() + ").\n");
					updatedOrderIds.put(order.getId(),"");
					return 1;
				}
			}
		}
		
		OrderBundle bundle = order.getOrderBundle();
		if (veryOld == null) {
			return 0;
		} else if (veryOld == true) {
			/* After one week "Ongelmia tilauksen käsittelyssä" was sent and the order was put into state
			 * "stock shortage". After yet another week stockShortageAfterPaid1 was sent. After three
			 * weeks we send stockShortageAfterPaid2. */
			/*String contextPath = messageDAO.getText("contextPath", bundle.getSeller(),order.getMarket().getLanguage());
			String email = messageDAO.getText("headerHi", bundle.getSeller(), order.getMarket().getLanguage()) + "\n\n";
			email += messageDAO.getText("stockShortageAfterPaid2", bundle.getSeller(), order.getMarket().getLanguage(),
					"ORDER_ID",""+order.getId(),
					"GOOGLE_ORDER_URL",bundle.getSeller().getStoreUrl(),
					"CONTEXT_PATH",contextPath,
					"SIGNATURE_URL_ENC",StringUtilities.escapeString2URL(bundle.getSeller().getSignature()),
					"REFERENCE_NUMBER",order.getCustomerReferenceNumber(),
					"SIGNATURE",bundle.getSeller().getSignature()) + "\n\n";
			email += messageDAO.getText("footer", bundle.getSeller(), order.getMarket().getLanguage(),
					"SIGNATURE",bundle.getSeller().getSignature());
			EmailSender.sendEmailBatch(bundle.getSeller().getSystemEmail(),bundle.getCustomer().getEmail(),
					"Täydennyserä ei tullut (tilaus " + order.getId() + ")",email);
			order.getOrderBundle().setLastCustomerUpdateSent(new Date());*/
			if (order.getState().equals(Order.STATE_STOCK_SHORTAGE_BEFORE_ORDER)
					&& stockShortageAfterPaid2.length() == 0 && bundleSize > 1) {
				stockShortageAfterPaid2.append("Joku nipun tuotteista on loppunut varastolta ja se estää nipun muiden"
						+ " tuotteiden lähettämistä.\n\n");
			}
			String contextPath = messageDAO.getText("contextPath", bundle.getSeller(),order.getMarket().getLanguage());
			stockShortageAfterPaid2.append(messageDAO.getText("stockShortageAfterPaid2", bundle.getSeller(), order.getMarket().getLanguage(),
					"ORDER_ID",""+order.getId(),
					"GOOGLE_ORDER_URL",bundle.getSeller().getStoreUrl(),
					"CONTEXT_PATH",contextPath,
					"SIGNATURE_URL_ENC",StringUtilities.escapeString2URL(bundle.getSeller().getSignature()),
					"REFERENCE_NUMBER",order.getCustomerReferenceNumber()) + "\n\n");
			orderInfo.append("Tilausnumero " + order.getId() + ": täydennyserä "
					+ " ei ole vieläkään tullut. Suosittelemme peruuttamista (" + order.getAd().getAdTemplate().getHeadline() + ").\n");
			updatedOrderIds.put(order.getId(),"");
			if (!order.getState().equals(Order.STATE_STOCK_SHORTAGE_BEFORE_ORDER)) {
				informOperatorAboutProviderDelays(bundle);
			}
			return 1;
		} else { //not very old
			/* After one week "Ongelmia tilauksen käsittelyssä" was sent and the order was put into state
			 * "stock shortage". After yet another week we send stockShortageAfterPaid1. */
			/*String email = messageDAO.getText("headerHi", bundle.getSeller(), order.getMarket().getLanguage()) + "\n\n";
			email += messageDAO.getText("stockShortageAfterPaid1", bundle.getSeller(), order.getMarket().getLanguage(),
					"ORDER_ID",""+order.getId(),
					"SIGNATURE",bundle.getSeller().getSignature()) + "\n\n";
			email += messageDAO.getText("footer", bundle.getSeller(), order.getMarket().getLanguage(),
					"SIGNATURE",bundle.getSeller().getSignature());
			EmailSender.sendEmailBatch(bundle.getSeller().getSystemEmail(),bundle.getCustomer().getEmail(),
					"Tuote on loppunut varastolta (tilaus " + order.getId() + ")",email);
			order.getOrderBundle().setLastCustomerUpdateSent(new Date());*/
			if (order.getState().equals(Order.STATE_STOCK_SHORTAGE_BEFORE_ORDER)
					&& stockShortageAfterPaid1.length() == 0 && bundleSize > 1) {
				stockShortageAfterPaid1.append("Joku nipun tuotteista on loppunut varastolta ja se estää nipun muiden"
						+ " tuotteiden lähettämistä.\n\n");
			}
			String contextPath = messageDAO.getText("contextPath", bundle.getSeller(),order.getMarket().getLanguage());
			stockShortageAfterPaid1.append(messageDAO.getText("stockShortageAfterPaid1", bundle.getSeller(), order.getMarket().getLanguage(),
					"ORDER_ID",""+order.getId(),
					"GOOGLE_ORDER_URL",bundle.getSeller().getStoreUrl(),
					"CONTEXT_PATH",contextPath,
					"SIGNATURE_URL_ENC",StringUtilities.escapeString2URL(bundle.getSeller().getSignature()),
					"REFERENCE_NUMBER",order.getCustomerReferenceNumber()) + "\n\n");
			orderInfo.append("Tilausnumero " + order.getId() + ": odottaa täydennyserää ("
					+ order.getAd().getAdTemplate().getHeadline() + ").\n");
			updatedOrderIds.put(order.getId(),"");
			//informOperatorAboutSittingOrder(order,daysInThisState);
			if (!order.getState().equals(Order.STATE_STOCK_SHORTAGE_BEFORE_ORDER)) {
				informOperatorAboutProviderDelays(bundle);
			}
			return 1;
		}
	}
	
	private void informOperatorAboutProviderDelays(OrderBundle bundle) {
		String forwardProviderEmailsTo = bundle.getSeller().getForwardCustomerEmails();
		if (forwardProviderEmailsTo == null) {
			forwardProviderEmailsTo = bundle.getSeller().getUser().getEmailAddress();
		}
		List<ProviderQuestion> providerQuestionList = providerQuestionDAO.getProviderQuestionForBundle(bundle);
		ProviderQuestion providerQuestion = null;
		if (providerQuestionList.size() > 0) {
			providerQuestion = providerQuestionList.get(0);
		}
		
		/* Make sure that this message is sent to operator only once per bundle.
		 * Note: this isn't totally waterproof because if no provider conversation
		 * exists for this bundle, then one message will still be sent for each order. */
		long dayAgo = System.currentTimeMillis() - 1000*3600*24;
		if (providerQuestion != null && providerQuestion.getOperatorLastInformed() != null
				&& providerQuestion.getOperatorLastInformed().getTime() > dayAgo) {
			logger.debug("Skipping informing the operator again about delays for bundle " + bundle.getId());
			return;
		}
		logger.debug("Informing the operator about delays for bundle " + bundle.getId());
		
		List<Order> orderList = orderDAO.getOrdersInBundle(bundle);
		/* By default the message to provider is empty so the operator needs to fill
		 * something out himself. */
		String defaultMessageToProvider = null;
		String defaultInfoToOperator = "You should probably kick the provider about this one.";
		if (providerQuestion == null) {
			/* No conversation exists yet at provider. */
			defaultMessageToProvider = "Why isn't this order shipped yet?";
		} else if (providerQuestion.getLastProviderComment() == null
				|| providerQuestion.getLastProviderComment().getTime() < providerQuestion.getLastOwnComment().getTime()) {
			/* The provider hasn't yet answered our last comment in this conversation. */
			defaultMessageToProvider = "What about this order?";
		} else {
			/* Conversation already exists but the provider has answered our last comment. */
			defaultInfoToOperator = "You might want to kick the provider about this one.";
		}
		String email = "Provider delays for bundle " + bundle.getId()
			+ " (" + bundle.getProviderOrderId() + "). " + defaultInfoToOperator + "\n\n"
			+ providerHandler.createProviderConversationEmail(providerQuestion, bundle, orderList,defaultMessageToProvider,false);
		EmailSender.sendEmailBatch(bundle.getSeller().getSystemEmail(),forwardProviderEmailsTo,
				"Provider conversation for bundle " + bundle.getId(), email);
		if (providerQuestion != null) {
			providerQuestion.setOperatorLastInformed(new Date());
		}
	}
	
	private void handleOrderedWithoutId(Order order, long daysInThisState) {
		/* Check if some DX order has been in the state STATE_ORDERED_FROM_PROVIDER_NO_ID for at least a week. If so
		 * we draw the conclusion that the order never went through and we need to place the order again. */
		if (daysInThisState > 6) {
			//set the state to STATE_CUSTOMER_PAID_INFORMED + inform the customer that there has been some problems
			/*logger.info("Moving order " + order.getId() + " from state \"ordered no id\" to \"customer paid informed\".");
			order.getOrderBundle().setProviderOrderId(null);
			order.getOrderBundle().setTransactionId(null);
			order.setFinalProviderPriceInProviderCurrency(null);
			order.setFinalProviderPriceInProviderCurrencyExclVat(null);
			order.setFinalProviderPriceInEuro(null);
			order.setFinalProviderPriceInEuroExclVat(null);
			order.setSendCount(order.getSendCount()-1);
			order.setStateWithHistory(Order.STATE_CUSTOMER_PAID_INFORMED);
			String email = messageDAO.getText("generalProblem", order.getOrderBundle().getSeller(), order.getMarket().getLanguage(),
					"ORDER_ID",""+order.getId(),
					"SIGNATURE",order.getOrderBundle().getSeller().getSignature());
			EmailSender.sendEmailBatch(order.getOrderBundle().getSeller().getSystemEmail(),order.getOrderBundle().getCustomer().getEmail(),
					"Ongelmia tilauksen käsittelyssä",email);
			order.getOrderBundle().setLastCustomerUpdateSent(new Date());
			EmailSender.sendOperationProblemReport("Automatically fixed a previous ordering problem",
					"Order " + order.getId() + " was in state \"ordered from provider no id\" but it seems like this order\n"
					+ "never went through so we set this one to state \"customer paid informed\".");*/
			throw new RuntimeException("Fixme (handleOrderedWithoutId).");
		} else {
			logger.info("Not yet moving order " + order.getId() + " from state \"ordered no id\" to \"customer paid informed\".");
		}
	}
	
	/*private int handleFulfilmentCenter(Order order, long daysInThisState, long daysSinceLastUpdate, StringBuffer orderInfo,
			Map<Long,String> updatedOrderIds) {
		if (daysInThisState > 13 && daysSinceLastUpdate > 6) {
			/* If it has been in this state for two weeks, then inform the customer AND the operator. */
			/*String email = messageDAO.getText("packingProblem", order.getOrderBundle().getSeller(), order.getMarket().getLanguage(),
					"ORDER_ID",""+order.getId(),
					"SIGNATURE",order.getOrderBundle().getSeller().getSignature());
			EmailSender.sendEmailBatch(order.getOrderBundle().getSeller().getSystemEmail(),order.getOrderBundle().getCustomer().getEmail(),
					"Pakkausosastolla edelleen ongelmia",email);
			order.getOrderBundle().setLastCustomerUpdateSent(new Date());/
			orderInfo.append("Tilausnumero " + order.getId() + ": Pakkausosastolla edelleen ongelmia."
					+ " Tiedoitamme asiasta enemmän viikon sisällä.\n");
			informOperatorAboutSittingOrder(order,daysInThisState);
			updatedOrderIds.put(order.getId(),"");
			return 1;
		} else if (daysInThisState > 6 && daysSinceLastUpdate > 6) {
			/* If it has been in this state for a week, then only inform the customer. */
			/*String email = messageDAO.getText("packingProblem", order.getOrderBundle().getSeller(), order.getMarket().getLanguage(),
					"ORDER_ID",""+order.getId(),
					"SIGNATURE",order.getOrderBundle().getSeller().getSignature());
			EmailSender.sendEmailBatch(order.getOrderBundle().getSeller().getSystemEmail(),order.getOrderBundle().getCustomer().getEmail(),
					"Pakkausosastolla ongelmia",email);
			order.getOrderBundle().setLastCustomerUpdateSent(new Date());/
			orderInfo.append("Tilausnumero " + order.getId() + ": Tuotteen \"" + order.getAd().getAdTemplate().getHeadline()
					+ "\" käsittelyssä on pakattaessa ilmennyt ongelmia. Tiedoitamme asiasta enemmän viikon sisällä.\n");
			updatedOrderIds.put(order.getId(),"");
			return 1;
		}
		return 0;
	}*/
	
	private void informOperatorAboutSittingOrder(Order order, long daysInThisState) {
		/* Don't inform the operator if the state is STATE_REACHED_DESTINATION_COUNTRY,
		 * because this state is taken care of tracking object specifically (in the
		 * handleTrackingObjectStuckAtPostOffice method). */
		if (daysInThisState > 6 && !order.getState().equals(Order.STATE_REACHED_DESTINATION_COUNTRY)) {
			EmailSender.sendOperationProblemReport("Order sitting for at least a week",
					"Order " + order.getId() + " has been sitting in state " + order.getState()
					+ " for " + daysInThisState + " days. Please check this one manually.\n\n"
					+ (order.getTracking() != null ? "trackingCode = " + order.getTracking().getTrackingCode() : ""));
		}
	}
	
	private void handleShippedByProviderWithTracking(Tracking tracking, List<Order> trackingObjectOrders,
			long daysInThisState, long daysSinceLastUpdate, StateChange latestStateChange, List<Order> allOrdersInBundle) {
		DateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy");
		OrderBundle bundle = tracking.getOrderBundle();
		if (daysInThisState > 13 && daysSinceLastUpdate > 6) {
			/* If it has been in this state for two weeks, then inform the customer. */
			/*String email = messageDAO.getText("shippedByProviderDelay", bundle.getSeller(), order.getMarket().getLanguage(),
						"ORDER_ID",""+order.getId(),
						"DATE",dateFormat.format(latestStateChange.getUpdateDate()),
						"SIGNATURE",bundle.getSeller().getSignature());*/
			String email = "Moi,\n\n";
			if (allOrdersInBundle.size() > 1) {
				email += "Nippusi " + bundle.getId();
			} else {
				email += "Tilauksesi " + trackingObjectOrders.get(0).getId();
			}
			email += " on viety postin toimitettavaksi jo "
				+ dateFormat.format(latestStateChange.getUpdateDate()) + ", mutta postin sisäinen"
				+ " käsittely on vielä kesken. Posti kerryttää tilauksia kohdemaahan, jolloin"
				+ " yksittäisissä tapauksissa postin sisäiseen käsittelyyn voi kulua muutamia"
				+ " päiviä tavallista kauemmin.\n\n";
			if (allOrdersInBundle.size() > trackingObjectOrders.size()) {
				email += "HUOM: tämä koskee vaan nipun seuraavia tilauksia:\n";
				for (Order order : trackingObjectOrders) { //TODO: use messageDAO.getText("oneOrderSimple",...) here
					email += "Tilaus " + order.getId() + ": " + order.getAd().getAdTemplate().getHeadline() + "\n";
				}
				email += "\nNipussa on muitakin tilauksia, mutta niistä tiedotetaan erikseen.\n\n";
			}
			email += "Ystävällisin terveisin,\n" + bundle.getSeller().getSignature();
			EmailSender.sendEmailBatch(bundle.getSeller().getSystemEmail(),bundle.getCustomer().getEmail(),
					"Hong Kongin/Kiinan postissa viivästys",email);
			bundle.setLastCustomerUpdateSent(new Date());
			/* Note: don't inform the operator here, because GeneralPostService will after some time
			 * conclude that the post has screwed up and sent the packet without using tracking. */
			//informOperatorAboutSittingOrder(trackingObjectOrders.get(0),daysInThisState);
		} else if (daysInThisState > 6 && daysSinceLastUpdate > 6) {
			/* If it has been in this state for a week, then only inform the customer.
			 * =>UPDATE: don't inform the customer, because it's so common that it's more than a week
			 * in this state (no use telling about "delays" for the "normal" case). */
			/*String email = messageDAO.getText("shippedByProviderDelay", bundle.getSeller(), order.getMarket().getLanguage(),
						"ORDER_ID",""+order.getId(),
						"DATE",dateFormat.format(latestStateChange.getUpdateDate()),
						"SIGNATURE",bundle.getSeller().getSignature());*/
			/*String email = "Moi,\n\n";
			if (allOrdersInBundle.size() > 1) {
				email += "Nippusi " + bundle.getId();
			} else {
				email += "Tilauksesi " + trackingObjectOrders.get(0).getId();
			}
			email += " on viety postin toimitettavaksi jo "
				+ dateFormat.format(latestStateChange.getUpdateDate()) + ", mutta postin sisäinen"
				+ " käsittely on vielä kesken. Posti kerryttää tilauksia kohdemaahan, jolloin"
				+ " yksittäisissä tapauksissa postin sisäiseen käsittelyyn voi kulua muutamia"
				+ " päiviä tavallista kauemmin.\n\n";
			if (allOrdersInBundle.size() > trackingObjectOrders.size()) {
				email += "HUOM: tämä koskee vaan nipun seuraavia tilauksia:\n";
				for (Order order : trackingObjectOrders) { //TODO: use messageDAO.getText("oneOrderSimple",...) here
					email += "Tilaus " + order.getId() + ": " + order.getAd().getAdTemplate().getHeadline() + "\n";
				}
				email += "\nNipussa on muitakin tilauksia, mutta niistä tiedotetaan erikseen.\n\n";
			}
			email += "Ystävällisin terveisin,\n" + bundle.getSeller().getSignature();
			EmailSender.sendEmailBatch(bundle.getSeller().getSystemEmail(),bundle.getCustomer().getEmail(),
					"Hong Kongin/Kiinan postissa viivästys",email);
			bundle.setLastCustomerUpdateSent(new Date());*/
		}
	}
	
	private void handleShippedByProviderWithoutTracking(OrderBundle bundle, List<Order> orderList,
			long daysInThisState, long daysSinceLastUpdate, StateChange latestStateChange) {
		int state = orderList.get(0).getState();
		for (Order order : orderList) {
			if (!order.getState().equals(state)) {
				throw new RuntimeException("Not all orders are in the same state for bundle " + bundle.getId()
						+ " (in handleShippedByProvider).");
			}
		}
		if (daysInThisState > 61) {
			logger.info("Closing shipped bundle " + bundle.getId() + " because it has been in state "
					+ orderList.get(0).getState() + " for a long time.");
			for (Order order : orderList) {
				order.setStateWithHistory(Order.STATE_CLOSED);
			}
		} else {
			logger.debug("Bundle " + bundle.getId() + " is shipped and has been in state " + orderList.get(0).getState()
					+ " for some time but don't close it yet.");
		}
	}
	
	private void handleShippedByPostoffice(Tracking tracking, List<Order> trackingObjectOrders,
			long daysInThisState, long daysSinceLastUpdate, StateChange latestStateChange, List<Order> allOrdersInBundle) {
		OrderBundle bundle = tracking.getOrderBundle();
		DateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy");
		if ((daysInThisState > 13 && daysInThisState <= 20 && daysSinceLastUpdate > 6) || (daysInThisState > 20 && daysSinceLastUpdate > 13)) {
			/* If it has been in this state for two weeks, then inform the customer. Note: after sending this
			 * one once, then let's send the same thing only every second week after this - "daysSinceLastUpdate > 13").
			 * At some point GeneralPostService will conclude that tracking is not available because the
			 * post office screwed up. */
			/*String email = messageDAO.getText("notYetReachedDestinationCountry", bundle.getSeller(), order.getMarket().getLanguage(),
						"ORDER_ID",""+order.getId(),
						"DATE",dateFormat.format(latestStateChange.getUpdateDate()),
						"SIGNATURE",bundle.getSeller().getSignature());*/
			String email = "Moi,\n\n";
			if (allOrdersInBundle.size() > 1) {
				email += "Nippusi " + bundle.getId();
			} else {
				email += "Tilauksesi " + trackingObjectOrders.get(0).getId();
			}
			email += " on lähtenyt postin mukana kohti Suomea"
				+ " (" + dateFormat.format(latestStateChange.getUpdateDate()) + "), mutta ei ole vielä kirjautunut"
				+ " Suomen postin järjestelmään. On mahdollista, että posti on kämmännyt ja laittanut pakettisi"
				+ " tavalliseen jakeluun eikä silloin seuranta ole saatavilla.\n\n";
			if (allOrdersInBundle.size() > trackingObjectOrders.size()) {
				email += "HUOM: tämä koskee vaan nipun seuraavia tilauksia:\n";
				for (Order order : trackingObjectOrders) { //TODO: use messageDAO.getText("oneOrderSimple",...) here
					email += "Tilaus " + order.getId() + ": " + order.getAd().getAdTemplate().getHeadline() + "\n";
				}
				email += "\nNipussa on muitakin tilauksia, mutta niistä tiedotetaan erikseen.\n\n";
			}
			email += "Ystävällisin terveisin,\n" + bundle.getSeller().getSignature();
			EmailSender.sendEmailBatch(bundle.getSeller().getSystemEmail(),bundle.getCustomer().getEmail(),
					"Kuljetus viivästyy",email);
			bundle.setLastCustomerUpdateSent(new Date());
			/* Note: don't inform the operator here, because GeneralPostService will after some time
			 * conclude that the post has screwed up and sent the packet without using tracking. */
			//informOperatorAboutSittingOrder(trackingObjectOrders.get(0),daysInThisState);
		} else if (daysInThisState > 6 && daysSinceLastUpdate > 6) {
			/* If it has been in this state for a week, then only inform the customer. */
			/*String email = messageDAO.getText("notYetReachedDestinationCountry", bundle.getSeller(), order.getMarket().getLanguage(),
						"ORDER_ID",""+order.getId(),
						"DATE",dateFormat.format(latestStateChange.getUpdateDate()),
						"SIGNATURE",bundle.getSeller().getSignature());*/
			String email = "Moi,\n\n";
			if (allOrdersInBundle.size() > 1) {
				email += "Nippusi " + bundle.getId();
			} else {
				email += "Tilauksesi " + trackingObjectOrders.get(0).getId();
			}
			email += " on lähtenyt postin mukana kohti Suomea"
				+ " (" + dateFormat.format(latestStateChange.getUpdateDate()) + "), mutta ei ole vielä kirjautunut"
				+ " Suomen postin järjestelmään. Tiedoitamme lisää viikon sisällä.\n\n";
			if (allOrdersInBundle.size() > trackingObjectOrders.size()) {
				email += "HUOM: tämä koskee vaan nipun seuraavia tilauksia:\n";
				for (Order order : trackingObjectOrders) { //TODO: use messageDAO.getText("oneOrderSimple",...) here
					email += "Tilaus " + order.getId() + ": " + order.getAd().getAdTemplate().getHeadline() + "\n";
				}
				email += "\nNipussa on muitakin tilauksia, mutta niistä tiedotetaan erikseen.\n\n";
			}
			email += "Ystävällisin terveisin,\n" + bundle.getSeller().getSignature();
			EmailSender.sendEmailBatch(bundle.getSeller().getSystemEmail(),bundle.getCustomer().getEmail(),
					"Kuljetus viivästyy",email);
			bundle.setLastCustomerUpdateSent(new Date());
		}
	}
	
	/*private void sendGeneralBundleInfo(OrderBundle bundle, List<Order> bundleOrders, long daysSinceLastUpdate) {
		/* If it's at least a week since the customer was last informed about status changes (and none
		 * of the above rules triggered information sending), then inform the customer that the order
		 * progresses. /
		if (daysSinceLastUpdate > 6) {
			/* Make sure that none of the above rules triggered information sending. /
			long latestUpdate = (System.currentTimeMillis() - bundle.getLastCustomerUpdateSent().getTime()) / (1000*3600*24);

			/* Figure out if the customer has paid for all orders. /
			boolean hasPaidForAllOrders = true;
			boolean someOrderHasInfoState = false;
			for (Order order : bundleOrders) {
				if (order.getState().intValue() < Order.STATE_CUSTOMER_PAID) {
					hasPaidForAllOrders = false;
				}
				if (order.getState().intValue() < Order.STATE_CLOSED
						&& order.getState().intValue() != Order.STATE_PROVIDER_CANCEL_PENDING
						&& order.getState().intValue() != Order.STATE_STOCK_SHORTAGE_BEFORE_ORDER) {
					someOrderHasInfoState = true;
				}
			}
			
			// Don't send any information about that the order is making progress
			// if the customer hasn't even paid for it, or if all orders of the
			// bundle are closed (they might have been closed in the above
			// code somewhere).
			if (hasPaidForAllOrders && someOrderHasInfoState && latestUpdate > 6) {
				//inform the customer that his bundle progresses
				String email = "Moi,\n\nTilauksesi edistyy.\n\n" + OrderInformation.getBundleInfo(bundle,bundleOrders).toString()
					+ "\nYstävällisin terveisin,\n" + bundle.getSeller().getSignature();
				EmailSender.sendEmailBatch(bundle.getSeller().getSystemEmail(),bundle.getCustomer().getEmail(),
						"Tilauksesi edistyy",email);
				bundle.setLastCustomerUpdateSent(new Date());
			}
		}
	}*/
	
}
