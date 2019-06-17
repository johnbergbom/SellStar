package fi.jonix.huutonet.order.dx;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import javax.mail.Message;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import fi.jonix.huutonet.domain.VirtualCoin;
import fi.jonix.huutonet.domain.model.Currency;
import fi.jonix.huutonet.domain.model.Customer;
import fi.jonix.huutonet.domain.model.Order;
import fi.jonix.huutonet.domain.model.OrderBundle;
import fi.jonix.huutonet.domain.model.Provider;
import fi.jonix.huutonet.domain.model.StateChange;
import fi.jonix.huutonet.domain.model.SystemEmail;
import fi.jonix.huutonet.domain.model.Tracking;
import fi.jonix.huutonet.domain.model.dao.OrderBundleDAO;
import fi.jonix.huutonet.domain.model.dao.OrderDAO;
import fi.jonix.huutonet.domain.model.dao.ProviderDAO;
import fi.jonix.huutonet.domain.model.dao.TrackingDAO;
import fi.jonix.huutonet.email.EmailSender;
import fi.jonix.huutonet.exception.CriticalSectionInfo;
import fi.jonix.huutonet.exception.PlaceOrderException;
import fi.jonix.huutonet.exception.PostTrackingDownException;
import fi.jonix.huutonet.exception.ProcessEmailLaterException;
import fi.jonix.huutonet.exception.UnprocessableEmailException;
import fi.jonix.huutonet.order.OrderRefundHandler;
import fi.jonix.huutonet.order.OrderShippedHandler;
import fi.jonix.huutonet.order.ProviderEmailFilter;
import fi.jonix.huutonet.order.ProviderOrder;

/**
 * This class takes care of emails coming from DealExtreme or otherwise have to do with DealExtreme.
 * 
 * @author john
 *
 */
@Component(value = "dxProcessor")
public class DxProcessor extends ProviderEmailFilter {

	public static final Logger logger = Logger.getLogger(DxProcessor.class);
	
	private static final String ORDER_RECEIVED_ID_STRING = "is received successfully, we will get down to fulfill your order as soon as possible";
	private static final String ORDER_SHIPPED_ID_STRING = "Your order has been shipped from the fulfillment center.";
	private static final String CANCELLATION_OR_DISPUTE_REQUEST_ID_STRING
				= "We have recently received either a cancellation request or a dispute request from PayPal for your order.";
	private static final String CONFIRM_SHIPPING_ADDRESS_ID_STRING = "Please double check your shipping address listed below";
	public static final String PAYMENT_RECEIPT_ID_STRING = "It may take a few moments for this transaction to appear in your account";
	private static final String REFUND_FROM_DX_ID_STRING = "DealExtreme just sent you a full refund of";

	private static final String PAYPAL_PAID_EMAIL = "service@paypal.com.hk";
	private static final String SALES_AT_DX = "sales@dealextreme.com";
	private static final String PAYPAL_REFUND_EMAIL = "service@intl.paypal.com";

	@Autowired
	private OrderDAO orderDAO;
	
	@Autowired
	private ProviderDAO providerDAO;
	
	@Autowired
	private OrderBundleDAO orderBundleDAO;
	
	@Autowired
	private OrderRefundHandler orderRefundHandler;
	
	@Autowired
	private TrackingDAO trackingDAO;
	
	/* Processes order received messages from deal extreme. */
	private boolean processOrderBundleReceived(String contents, SystemEmail systemEmail) throws Exception {
		//String orderDate = getFieldValue(contents,"Order Date:");
		String orderNumber = getFieldValue(contents,"ORDER #:");
		String transactionId = getFieldValue(contents,"RECEIPT #:");
		//DateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy");
		//logger.debug("Order date = " + dateFormat.parse(orderDate));
		logger.debug("Order number = " + orderNumber);
		logger.debug("Transaction ID = " + transactionId);
		if (/*orderDate != null && */orderNumber != null && transactionId != null) {
			logger.info("Order received notification received from DealExtreme for order number \"" + orderNumber + "\"");
			Provider provider = providerDAO.getProviderByName(Provider.DEAL_EXTREME_PROVIDER_NAME);
			OrderBundle orderBundle = orderBundleDAO.getOrderBundleByProviderOrderId(orderNumber,provider);
			if (orderBundle == null) {
				/* The below code doesn't work with bundles, so here let's just throw an unprocessable exception. */
				throw new UnprocessableEmailException("orderBundle.providerOrderId " + orderNumber + " not found in database.");

				/* Here we check if there are some orders in the state STATE_ORDERED_FROM_PROVIDER_NO_ID. */
				/*int nbrFound = 0;
				List<Order> noIdOrders = orderDAO.getOrdersByState(Order.STATE_ORDERED_FROM_PROVIDER_NO_ID);
				for (Order noIdOrder : noIdOrders) {
					String firstName = noIdOrder.getOrderBundle().getFirstName();
					if (contents.indexOf("Dear " + firstName) >= 0) {
						order = noIdOrder;
						nbrFound++;
					}
				}
				if (nbrFound == 0) {
					throw new UnprocessableEmailException("order.providerOrderId " + orderNumber + " not found in database.");
				} else if (nbrFound == 1) {
					logger.debug("Automatically fixing previous ordering problem for order " + order.getId() + " when an \"order received\" came.");
					order.getOrderBundle().setProviderOrderId(orderNumber);
					order.getOrderBundle().setTransactionId(transactionId);
					order.setStateWithHistory(Order.STATE_ORDERED_FROM_PROVIDER);
					Thread.sleep(1000);
					String email = "Order " + order.getId() + " was in state \"ordered from provider no id\" but it was automatically"
						+ " fixed\nwhen an \"order received\" email was received.\n\nPlease check manually and make sure that the"
						+ " automatic fix was correct.\n\nIn particular you might want to make sure that order "
						+ order.getOrderBundle().getProviderOrderId() + " at dx has receiver\n" + order.getOrderBundle().getFirstName()
						+ " " + order.getOrderBundle().getLastName() + " and not \"Etunimi Sukunimi\".\n(Most likely this is correct"
						+ " for this type of problem, but you might want to check.)";
					EmailSender.sendOperationProblemReport("Automatically fixed a previous ordering problem",email,systemEmail);
				} else {
					logger.debug("Couldn't automatically fix previous ordering problem for order " + order.getId()
							+ " when an \"order received\" came because there are " + nbrFound + " possible solutions.");
					String email = "Order " + order.getId() + " is in state \"ordered from provider no id\" but it couldn't"
						+ " automatically be corrected\nbecause there are " + nbrFound + " possible fixes.";
					EmailSender.sendOperationProblemReport("Couldn't automatically fix a previous ordering problem",email,systemEmail);
					throw new UnprocessableEmailException("order.providerOrderId " + orderNumber + " could not be automatically fixed.");
				}*/
			}
			if (!orderBundle.getTransactionId().equals(transactionId)) {
				throw new UnprocessableEmailException("Transaction ID " + orderBundle.getTransactionId()
						+ " doesn't match with the Transaction ID from the email (" + transactionId + ")");
			}
			ProviderOrder.orderBundleConfirmedByProvider(orderBundle,orderDAO.getOrdersInBundle(orderBundle));
			return true;
		} else {
			logger.warn("Couldn't fetch orderDate, orderNumber or transactionId (for order received).");
			return false;
		}
	}

	/* Processes order shipped messages from deal extreme. */
	private boolean processOrderShipped(String contents) throws Exception {
		String orderNumber = getFieldValue(contents,"ORDER #:");
		String trackingNumber = getFieldValue(contents,"TRACKING #:");
		logger.debug("Order number = " + orderNumber);
		logger.debug("Tracking number = " + trackingNumber);
		if (orderNumber != null && trackingNumber != null) {
			logger.info("Order shipped notification received from DealExtreme for order number \"" + orderNumber + "\"");
			Provider provider = providerDAO.getProviderByName(Provider.DEAL_EXTREME_PROVIDER_NAME);
			OrderBundle bundle = orderBundleDAO.getOrderBundleByProviderOrderId(orderNumber,provider);
			if (bundle == null) {
				throw new UnprocessableEmailException("orderBundle.providerOrderId " + orderNumber + " not found in database.");
			}
			
			/* Get the orders for the bundle. */
			List<Order> bundleOrders = new ArrayList<Order>();
			for (Order order : orderDAO.getOrdersInBundle(bundle)) {
				if (!order.getState().equals(Order.STATE_CLOSED_ORDER_CANCELED)) {
					bundleOrders.add(order);
				}
			}
			
			Tracking tracking = null;
			if (trackingNumber != null && !trackingNumber.equals("-")) {
				tracking = new Tracking();
				tracking.setOrderBundle(bundle);
				tracking.setTrackingCode(trackingNumber);
				tracking.setFollow(true);
				trackingDAO.save(tracking);
			}
			
			/* Associate the new tracking object (if any) with the order and remove the old one
			 * if this is a resending. */
			for (Order order : bundleOrders) {
				//Tracking oldTrackingObject = order.getTracking();
				if (order.getTracking() != null) {
					throw new Exception("Error: old tracking object exists when detecting \"shipped\" (bundle "
							+ bundle.getId() + ", order " + order.getId() + ").");
				}
				order.setTracking(tracking); //tracking can be null here, if no tracking code was available

				/* If this is a resending, then let's remove the old tracking object. */
				/*if (oldTrackingObject != null) {
					if (isResend(order)) {
						logger.debug("Removing old tracking object " + oldTrackingObject.getId()
								+ " (" + oldTrackingObject.getTrackingCode() + ") for bundle "
								+ bundle.getId() + " because of resending.");
						trackingDAO.delete(oldTrackingObject);
					} else {
						throw new Exception("Error: old tracking object exists although not resending (bundle "
								+ bundle.getId() + ", order " + order.getId() + ").");
					}
				}*/
			}

			throw new RuntimeException("This one should use CriticalSectionInfo");
			/*try {
				OrderShippedHandler.handleOrderBundleShippedByProvider(tracking, bundleOrders, bundleOrders);
			} /*catch (PostTrackingDownException e) {
				throw new ProcessEmailLaterException("Couldn't process order shipped info from"
						+ " dx because post tracking is down: " + e.getMessage());
			} /catch (Exception e) {
				if (e.getMessage().indexOf("is in the wrong state") > 0) {
					throw new UnprocessableEmailException(e.getMessage());
				} else {
					throw e;
				}
			}
			return true;*/
		} else {
			logger.warn("Couldn't fetch orderNumber or trackingNumber (for order shipped).");
			return false;
		}
	}

	//TODO: this method is just copy'n'pasted from ProviderOrder - refactor!
	/*private boolean isResend(Order order) {
		for (StateChange sc : order.getStateChanges()) {
			if (sc.getState().equals(Order.STATE_SHOULD_RESEND)) {
				return true;
			}
		}
		return false;
	}*/
	
	/* Processes "order cancellation or dispute request" messages from deal extreme. This
	 * method actually doesn't do anything with the email, other than checking that
	 * the order actually exists in the database. The reason nothing is done based on
	 * this email is that "cancellation or dispute" doesn't actually tell anything,
	 * but anyway if the email is parsed okay, then we don't need to forward it to
	 * the operators, so that's why we have this method in the first place. */
	private boolean processCancellationOrDisputeRequest(String contents) throws Exception {
		String orderNumber = getFieldValue(contents,"ORDER #:");
		logger.debug("Order number = " + orderNumber);
		if (orderNumber != null) {
			logger.info("Cancellation or dispute request received from DealExtreme for order number \"" + orderNumber + "\"");
			Provider provider = providerDAO.getProviderByName(Provider.DEAL_EXTREME_PROVIDER_NAME);
			OrderBundle orderBundle = orderBundleDAO.getOrderBundleByProviderOrderId(orderNumber,provider);
			if (orderBundle == null) {
				throw new UnprocessableEmailException("orderBundle.providerOrderId " + orderNumber + " not found in database.");
			}
			return true;
		} else {
			logger.warn("Couldn't fetch orderNumber (for cancellation or dispute request).");
			return false;
		}
	}

	/* Processes "Please Confirm Your Shipping Address" messages from deal extreme. These emails are normally
	 * only sent from DX in case there was some problem during the placing of the order, so the purpose of this
	 * method is to automatically correct such problems whenever possible. */
	private boolean processConfirmShippingAddress(String contents, SystemEmail systemEmail) throws Exception {
		String orderNumber = getFieldValue(contents,"ORDER #:");
		String receipt = getFieldValue(contents,"RECEIPT #:");
		String totalPriceStr = getFieldValue(contents,"ORDER TOTAL:");
		logger.debug("Order number = " + orderNumber);
		logger.debug("receipt = " + receipt);
		String address = getRowValue(contents,"Current shipping address:",2);
		String cityAndZip = getRowValue(contents,"Current shipping address:",3);
		logger.debug("address = " + address);
		logger.debug("cityAndZip = " + cityAndZip);
		
		logger.info("Confirm shipping address notification received from DealExtreme for order number \"" + orderNumber + "\"");
		Provider provider = providerDAO.getProviderByName(Provider.DEAL_EXTREME_PROVIDER_NAME);
		OrderBundle orderBundle = orderBundleDAO.getOrderBundleByProviderOrderId(orderNumber,provider);
		if (orderBundle == null) {
			/* The below code doesn't work with bundles, so here let's just throw an unprocessable exception. */
			throw new UnprocessableEmailException("orderBundle.providerOrderId " + orderNumber + " not found in database.");
			
			/* Here we check if there are some orders in the state STATE_ORDERED_FROM_PROVIDER_NO_ID. */
			/*int nbrFound = 0;
			List<Order> noIdOrders = orderDAO.getOrdersByState(Order.STATE_ORDERED_FROM_PROVIDER_NO_ID);
			for (Order noIdOrder : noIdOrders) {
				//Customer cust = noIdOrder.getCustomer();
				if (noIdOrder.getOrderBundle().getAddress().equals(address) && cityAndZip.indexOf(noIdOrder.getOrderBundle().getCity()) >= 0
						&& cityAndZip.indexOf(noIdOrder.getOrderBundle().getPostcode()) >= 0
						&& noIdOrder.getFinalProviderPriceInProviderCurrency().compareTo(new BigDecimal(totalPrice)) == 0) {
					order = noIdOrder;
					nbrFound++;
				}
			}
			if (nbrFound == 0) {
				throw new UnprocessableEmailException("order.providerOrderId " + orderNumber + " not found in database.");
			} else if (nbrFound == 1) {
				logger.debug("Automatically fixing previous ordering problem for order " + order.getId()
						+ " when a \"confirm shipping address\" came.");
				order.getOrderBundle().setProviderOrderId(orderNumber);
				order.getOrderBundle().setTransactionId(receipt);
				order.setStateWithHistory(Order.STATE_ORDERED_FROM_PROVIDER);
				Thread.sleep(1000);
				//order.setStateWithHistory(Order.STATE_ORDER_CONFIRMED_BY_PROVIDER);
				ProviderOrder.orderConfirmedByProvider(order);
				Thread.sleep(1000);
				String email = "Order " + order.getId() + " was in state \"ordered from provider no id\" but it was automatically fixed\n"
					+ "when a \"Please Confirm Your Shipping Address\" came.\n\nPlease check manually and make sure that the automatic"
					+ " fix was correct.\n\nIMPORTANT: YOU PROBABLY NEED TO MANUALLY CHANGE THE RECEIVER'S NAME FROM ETUNIMI SUKUNIMI\n"
					+ "TO " + order.getOrderBundle().getFirstName() + " " + order.getOrderBundle().getLastName() + " AT DEALEXTREME!\n\n"
					+ "(order id = " + order.getOrderBundle().getProviderOrderId() + ")";
				EmailSender.sendOperationProblemReport("Automatically fixed a previous ordering problem",email,systemEmail);
			} else {
				logger.debug("Couldn't automatically fix previous ordering problem for order " + order.getId()
						+ " when a \"confirm shipping address\" came because there are " + nbrFound + " possible solutions.");
				String email = "Order " + order.getId() + " is in state \"ordered from provider no id\" but it couldn't automatically be corrected\n"
					+ "because there are " + nbrFound + " possible fixes.";
				EmailSender.sendOperationProblemReport("Couldn't automatically fix a previous ordering problem",email,systemEmail);
				throw new UnprocessableEmailException("order.providerOrderId " + orderNumber + " could not be automatically fixed");
			}*/
		}
		//We don't get the used currency from the email itself, so let's take it from the provider.
		VirtualCoin totalPrice = new VirtualCoin(orderBundle.getUsedProviderCurrency(), new BigDecimal(totalPriceStr));
		logger.debug("totalPrice = " + totalPrice);
		if (!orderBundle.getAddress().equals(address) || cityAndZip.indexOf(orderBundle.getCity()) < 0
				|| cityAndZip.indexOf(orderBundle.getPostcode()) < 0) {
			String email = "Order bundle " + orderBundle.getId() + " should according to the database be sent to the following address:\n"
				+ orderBundle.getFirstName() + " " + orderBundle.getLastName() + "\n"
				+ orderBundle.getAddress() + "\n" + orderBundle.getPostcode() + " "
				+ orderBundle.getCity() + "\n\nHowever according to DX is should be shipped to the following address:\n"
				+ address + "\n" + cityAndZip + "\n\nPlease check this one manually.";
			EmailSender.sendOperationProblemReport("Faulty address information detected for order bundle " + orderBundle.getId(),email,systemEmail);
			throw new UnprocessableEmailException("Faulty delivery address for order bundle " + orderBundle.getId());
		} else {
			EmailSender.sendOperationProblemReport("Correct address information detected for order bundle " + orderBundle.getId(),
					"Order bundle " + orderBundle.getId() + " has correct address information.",systemEmail);
		}
		return true;
	}

	/* This method processes payment sent messages from PayPal. Actually it always just
	 * accepts these as is, because the PayPal Transaction ID is different from the
	 * DealExtreme Transaction ID, and there is no way we can associate the contents of
	 * this email to any existing order. But this shouldn't matter because the email
	 * doesn't really contain any information that is necessary for further processing. */
	private boolean processPaymentSent(String contents, String subject) throws Exception {
		/* Safety measure: we should always be getting prices in dollars from Huutonet,
		 * but make sure we bail out in case we don't. */
		Long currency = null;
		if (!matchEmailContents(contents,"You sent a payment of $") || !matchEmailContents(contents,"USD to")) {
			throw new Exception("Unknown currency for payment through PayPal.");
		} else {
			currency = Currency.DOLLAR;
		}
		String amountStr = getFieldValue(contents,"You sent a payment of");
		if (amountStr.startsWith("$")) {
			amountStr = amountStr.replace("$","");
		}
		VirtualCoin amount = new VirtualCoin(currency, new BigDecimal(amountStr));
		String transactionId = getFieldValue(contents,"Transaction ID:");
		if (transactionId.indexOf("<") >= 0) {
			transactionId = transactionId.substring(0,transactionId.indexOf("<"));
		}
		logger.info("Payment sent notification received from PayPal (provider = DealExtreme, PayPal Transaction ID " + transactionId + ").");
		return true;
	}

	/* Processes refunds from DealExtreme. */
	private boolean processRefundFromDx(String contents) throws Exception {
		/* Safety measure: we should always be getting prices in dollars from DealExtreme,
		 * but make sure we bail out in case we don't. */
		boolean fullRefund = true;
		Long currency = null;
		if (!matchEmailContents(contents,"DealExtreme just sent you a full refund of $") || !matchEmailContents(contents,"USD for your purchase")) {
			throw new UnprocessableEmailException("Unknown currency for paypal refund from DealExtreme.");
		} else {
			currency = Currency.DOLLAR;
		}
		String amountStr = getFieldValue(contents,"DealExtreme just sent you a full refund of");
		if (amountStr.startsWith("$")) {
			amountStr = amountStr.replace("$","");
		}
		String orderNumber = getFieldValue(contents,"This is a refund for your DX Order");
		if (orderNumber.endsWith(".")) {
			orderNumber = orderNumber.replace(".","");
		}
		logger.info("PayPal refund from DealExtreme received for order \"" + orderNumber + "\".");
		Provider provider = providerDAO.getProviderByName(Provider.DEAL_EXTREME_PROVIDER_NAME);
		OrderBundle bundle = orderBundleDAO.getOrderBundleByProviderOrderId(orderNumber,provider);
		if (bundle == null) {
			throw new UnprocessableEmailException("bundle.providerOrderId " + orderNumber + " not found in database.");
		} else if (!bundle.getUsedProviderCurrency().getId().equals(currency)) {
			throw new RuntimeException("Unexpected currency for \"order placed receipt\"");
		}
		orderRefundHandler.processRefundFromProvider(bundle, new VirtualCoin(currency,new BigDecimal(amountStr)), fullRefund);
		return true;
	}

	@Override
	public boolean processProviderEmail(Message message, SystemEmail systemEmail,
			CriticalSectionInfo processingInfo) throws Exception {
		/* Check if it's an order received message from DealExtreme. */
		String contents = matchEmail(message,SALES_AT_DX,"Your order #",true,ORDER_RECEIVED_ID_STRING);
		if (contents != null) {
			return processOrderBundleReceived(contents,systemEmail);
		}

		/* Check if it's an order shipped message from DealExtreme. */
		contents = matchEmail(message,SALES_AT_DX,"Order Shipped - ",true,ORDER_SHIPPED_ID_STRING);
		if (contents != null) {
			return processOrderShipped(contents);
		}

		/* Check if it's an order cancellation or dispute request message from DealExtreme. */
		contents = matchEmail(message,SALES_AT_DX,"Order Cancellation - ",true,CANCELLATION_OR_DISPUTE_REQUEST_ID_STRING);
		if (contents != null) {
			return processCancellationOrDisputeRequest(contents);
		}

		/* Check if it's a "Please Confirm Your Shipping Address" message from DealExtreme. */
		if (message.getSubject() != null && message.getSubject().indexOf("Please Confirm Your Shipping Address") > 0) {
			contents = matchEmail(message,SALES_AT_DX,"Order Confirmation - ",true,CONFIRM_SHIPPING_ADDRESS_ID_STRING);
			if (contents != null) {
				return processConfirmShippingAddress(contents,systemEmail);
			}
		}

		/* Check if it's a payment receipt message from PayPal regarding an order for DealExtreme. */
		contents = matchEmail(message,PAYPAL_PAID_EMAIL,"Receipt for Your Payment to DealExtreme",true,PAYMENT_RECEIPT_ID_STRING);
		if (contents != null) {
			return processPaymentSent(contents,message.getSubject());
		}

		/* Check if it's a refund from PayPal regarding an order from DealExtreme. */
		contents = matchEmail(message,PAYPAL_REFUND_EMAIL,"Refund from DealExtreme",true,REFUND_FROM_DX_ID_STRING);
		if (contents != null) {
			return processRefundFromDx(contents);
		}

		return false;
	}

}
