package fi.jonix.huutonet.order.focalprice;

import java.math.BigDecimal;

import javax.mail.Message;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import fi.jonix.huutonet.domain.VirtualCoin;
import fi.jonix.huutonet.domain.model.Currency;
import fi.jonix.huutonet.domain.model.OrderBundle;
import fi.jonix.huutonet.domain.model.Provider;
import fi.jonix.huutonet.domain.model.SystemEmail;
import fi.jonix.huutonet.domain.model.dao.OrderBundleDAO;
import fi.jonix.huutonet.domain.model.dao.OrderDAO;
import fi.jonix.huutonet.domain.model.dao.ProviderDAO;
import fi.jonix.huutonet.exception.CriticalSectionInfo;
import fi.jonix.huutonet.exception.ProcessEmailLaterException;
import fi.jonix.huutonet.exception.UnprocessableEmailException;
import fi.jonix.huutonet.order.OrderRefundHandler;
import fi.jonix.huutonet.order.ProviderEmailFilter;
import fi.jonix.huutonet.order.ProviderOrder;
import fi.jonix.huutonet.order.dx.DxProcessor;

/**
 * This class takes care of emails coming from FocalPrice or otherwise have to do with FocalPrice.
 * 
 * @author john
 *
 */
@Component(value = "focalPriceProcessor")
public class FocalPriceProcessor extends ProviderEmailFilter {

	public static final Logger logger = Logger.getLogger(FocalPriceProcessor.class);
	
	private static final String REFUND_FROM_FC_ID_STRING = "FocalPrice Inc. just sent you a refund";
	private static final String PAYPAL_EMAIL = "service@intl.paypal.com";
	private static final String ORDER_RECEIPT_ID_STRING = "Thanks for placing an order at FocalPrice.com";
	private static final String SALES_AT_FOCAL_PRICE = "@focalprice.com"; //sometimes info03@focalprice is used, sometimes info09@focalprice.com or something else...
	private static final String UNREAD_MESSAGE_ID_STRING = "This is just a quick email to let you know ";
	private static final String ORDER_SHIPPED_ID_STRING = "Your order was shipped out on";
	private static final String ORDER_PARTLY_SHIPPED_ID_STRING = "Part of your order was shipped out on";
	private static final String FC_SPAM_ID_STRING = "Do you know that we reward people who review our products";

	@Autowired
	private OrderDAO orderDAO;
	
	@Autowired
	private ProviderDAO providerDAO;
	
	@Autowired
	private OrderBundleDAO orderBundleDAO;
	
	@Autowired
	private OrderRefundHandler orderRefundHandler;
	
	/* This method processes payment sent messages from PayPal. We don't get any email from
	 * FocalPrice when an order is placed (unlike DealExtreme), but the email from PayPal
	 * confirming the payment contains an id which can be used to track which order this
	 * payment belongs to (unlike the DealExtreme case). So for FocalPrice we use the
	 * PayPal payment confirmation as a confirmation for the order.
	 * =>UPDATE: from the new FocalPrice system we do get an order receipt by email. */
	private boolean processPaymentReceived(String contents) throws Exception {
		/* Safety measure: we should always be getting prices in dollars from FocalPrice,
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
		//TODO: possibly we could here compare the amount to what we expect the payment to be
		VirtualCoin amount = new VirtualCoin(currency,new BigDecimal(amountStr));
		String transactionId = getFieldValue(contents,"Transaction ID:");
		if (transactionId.indexOf("<") >= 0) {
			transactionId = transactionId.substring(0,transactionId.indexOf("<"));
		}
		logger.debug("transactionId = " + transactionId);
		String payPalId = getFieldValue(contents,"Invoice ID:");
		logger.debug("payPalId = " + payPalId);
		Provider provider = providerDAO.getProviderByName(Provider.FOCAL_PRICE_PROVIDER_NAME);
		OrderBundle bundle = orderBundleDAO.getOrderBundleByPayPalId(payPalId,provider);
		if (bundle == null) {
			/* Now and then it happens that paypal sends out an email so quickly after placeorder that
			 * the place ordering thread hasn't yet stored the new ad to the database. In those cases it works
			 * if we try again a little later to process the same email. */
			throw new ProcessEmailLaterException("FocalPriceProcessor.processPaymentReceived: bundle.payPalId "
					+ payPalId + " not found in database.");
		} else if (!bundle.getUsedProviderCurrency().getId().equals(currency)) {
			throw new RuntimeException("Unexpected currency for \"payment received\"");
		}
		logger.info("Payment sent notification received from PayPal regarding order bundle "
				+ bundle.getId() + " (order confirmation from FocalPrice).");
		ProviderOrder.orderBundleConfirmedByProvider(bundle,orderDAO.getOrdersInBundle(bundle));
		return true;
	}

	private boolean processOrderPlacedReceipt(String contents, String subject) throws Exception {
		/* Safety measure: we should always be getting prices in dollars from FocalPrice,
		 * but make sure we bail out in case we don't. */
		Long currency = null;
		if (!matchEmailContents(contents,"Your order has been successfully received")) {
			return false;
		} else if (!matchEmailContents(contents,"US$")) {
			throw new Exception("Unknown currency for payment through PayPal.");
		} else {
			currency = Currency.DOLLAR;
		}
		String providerOrderId = getFieldValue(subject,"Congratulations! FocalPrice Order");
		logger.debug("providerOrderId = " + providerOrderId);
		Provider provider = providerDAO.getProviderByName(Provider.FOCAL_PRICE_PROVIDER_NAME);
		OrderBundle bundle = orderBundleDAO.getOrderBundleByProviderOrderId(providerOrderId,provider);
		if (bundle == null) {
			/* Now and then it happens that paypal sends out an email so quickly after placeorder that
			 * the place ordering thread hasn't yet stored the new ad to the database. In those cases it works
			 * if we try again a little later to process the same email. */
			throw new ProcessEmailLaterException("FocalPriceProcessor.processOrderPlacedReceipt: bundle.providerOrderId "
					+ providerOrderId + " not found in database.");
		} else if (!bundle.getUsedProviderCurrency().getId().equals(currency)) {
			throw new RuntimeException("Unexpected currency for \"order placed receipt\"");
		}
		logger.info("Order placed receipt received from FocalPrice regarding order bundle "
				+ bundle.getId() + " (order confirmation from FocalPrice).");
		ProviderOrder.orderBundleConfirmedByProvider(bundle,orderDAO.getOrdersInBundle(bundle));
		return true;
	}

	/* Don't take any action when we get notifications that an order was shipped, because
	 * they are taken care of by updateStatusOfOrders. */
	private boolean processOrderShippedNotification(String contents, String subject) throws Exception {
		return processOrderShipped(getFieldValue(subject,"Congratulations, your FocalPrice order"));
	}

	/* Don't take any action when we get notifications that an order was shipped, because
	 * they are taken care of by updateStatusOfOrders. */
	private boolean processOrderPartlyShippedNotification(String contents, String subject) throws Exception {
		return processOrderShipped(getFieldValue(subject,"FocalPrice Shipment Notification For"));
	}
	
	private boolean processOrderShipped(String providerOrderId) throws Exception {
		logger.debug("providerOrderId = " + providerOrderId);
		Provider provider = providerDAO.getProviderByName(Provider.FOCAL_PRICE_PROVIDER_NAME);
		OrderBundle bundle = orderBundleDAO.getOrderBundleByProviderOrderId(providerOrderId,provider);
		if (bundle == null) {
			throw new UnprocessableEmailException("Bundle having providerOrderId " + providerOrderId + " not found (for order shipped notification)");
		}
		logger.info("Order shipped notification received from FocalPrice regarding order bundle "
				+ bundle.getId() + ".");
		return true;
	}

	/* Processes refunds from FocalPrice. */
	private boolean processRefundFromFc(String contents) throws Exception {
		/* Safety measure: we should always be getting prices in dollars from FocalPrice,
		 * but make sure we bail out in case we don't. */
		String amountStr = null;
		Boolean fullRefund = null;
		if (matchEmailContents(contents,"FocalPrice Inc. just sent you a full refund of $")) {
			fullRefund = new Boolean(true);
			amountStr = getFieldValue(contents,"FocalPrice Inc. just sent you a full refund of");
		} else if (matchEmailContents(contents,"FocalPrice Inc. just sent you a partial refund of $")) {
			fullRefund = new Boolean(false);
			amountStr = getFieldValue(contents,"FocalPrice Inc. just sent you a partial refund of");
		}
		Long currency = null;
		if (fullRefund == null || !matchEmailContents(contents,"USD for your purchase")) {
			throw new UnprocessableEmailException("Unknown currency for paypal refund from FocalPrice.");
		} else {
			currency = Currency.DOLLAR;
		}
		if (amountStr.startsWith("$")) {
			amountStr = amountStr.replace("$","");
		}
		String payPalId = getFieldValue(contents,"Invoice Number:");
		logger.debug("payPalId = " + payPalId);
		if (payPalId == null) {
			throw new UnprocessableEmailException("Invoice number not found for paypal refund for focal price.");
		}
		if (payPalId.endsWith("Questions?")) {
			payPalId = payPalId.replace("Questions?","");
		}
		if (payPalId.endsWith("Yours")) {
			payPalId = payPalId.replace("Yours","");
		}
		logger.info("PayPal refund from FocalPrice received for order \"" + payPalId + "\".");
		Provider provider = providerDAO.getProviderByName(Provider.FOCAL_PRICE_PROVIDER_NAME);
		OrderBundle bundle = orderBundleDAO.getOrderBundleByPayPalId(payPalId,provider);
		
		if (bundle == null) {
			throw new UnprocessableEmailException("orderBundle.payPalId " + payPalId + " not found in database.");
		} else if (!bundle.getUsedProviderCurrency().getId().equals(currency)) {
			throw new RuntimeException("Unexpected currency for \"order placed receipt\"");
		}
		//List<Order> orders = orderDAO.getOrdersInBundle(bundle);
		/*if (orders.size() > 1) {
			throw new Exception("processRefundFromFc doesn't yet support bundles having several orders. TARKISTA:"
					+ " Jos nipussa on kaksi tilausta ja peruutetaan toinen providerilta ja saadaan siitä refund (ilman postikuluja?)"
					+ " ja sen jälkeen peruutetaan toinen nipussa olevista tilauksista, niin jos ei missään vaiheessa saada providerilta"
					+ " refundausta postikuluista (eli trackingkoodista), niin se pitää kai myös näkyä otherLosses:ssa.");
		}*/
		orderRefundHandler.processRefundFromProvider(bundle, new VirtualCoin(currency,new BigDecimal(amountStr)), fullRefund);
		return true;
	}

	/* Don't take any action when we get notifications that we have unread messages, because
	 * they are taken care of by updateProviderCommunication. */
	private boolean processUnreadMessage(String contents) throws Exception {
		String providerOrderId = getFieldValue(contents,"regarding to your order");
		logger.debug("Unread message notification received from FocalPrice for provider order id \"" + providerOrderId + "\"");
		if (providerOrderId.indexOf(".") >= 0) {
			providerOrderId = providerOrderId.replace(".","");
		}
		logger.debug("provider order id \"" + providerOrderId + "\"");
		//TODO: if no provider conversation exists for this bundle, then we should probably return false here so that
		//it's sent forward to the operator (as an unknown email), or else we can send an email to the operator that
		//FocalPrice has some information regarding bundle xxx.
		return true;
	}
	
	private boolean processSpamFromFocalPrice(String contents) throws Exception {
		logger.debug("Received spam from FocalPrice.");
		return true;
	}
	
	@Override
	public boolean processProviderEmail(Message message, SystemEmail systemEmail,
			CriticalSectionInfo processingInfo) throws Exception {
		/* Check if it's a payment receipt message from PayPal regarding an order for FocalPrice. */
		String contents = matchEmail(message,PAYPAL_EMAIL,"Receipt for Your Payment to FocalPrice Inc.",
				true,true,DxProcessor.PAYMENT_RECEIPT_ID_STRING);
		if (contents != null) {
			return processPaymentReceived(contents);
		}

		/* Check if it's an order placed receipt message from FocalPrice. */
		contents = matchEmail(message,SALES_AT_FOCAL_PRICE,"Congratulations! FocalPrice Order",true,ORDER_RECEIPT_ID_STRING);
		if (contents != null) {
			return processOrderPlacedReceipt(contents,message.getSubject());
		}

		/* Check if it's an order shipped notification from FocalPrice. */
		contents = matchEmail(message,SALES_AT_FOCAL_PRICE,"Congratulations, your FocalPrice order",true,ORDER_SHIPPED_ID_STRING);
		if (contents != null) {
			return processOrderShippedNotification(contents,message.getSubject());
		}

		/* Check if it's an order partly shipped notification from FocalPrice. */
		contents = matchEmail(message,SALES_AT_FOCAL_PRICE,"FocalPrice Shipment Notification For",true,ORDER_PARTLY_SHIPPED_ID_STRING);
		if (contents != null) {
			return processOrderPartlyShippedNotification(contents,message.getSubject());
		}

		/* Check if it's a notification regarding an unread message from FocalPrice. */
		contents = matchEmail(message,SALES_AT_FOCAL_PRICE,"You've got an unread message from FocalPrice",true,UNREAD_MESSAGE_ID_STRING);
		if (contents != null) {
			return processUnreadMessage(contents);
		}

		/* Check if it's "spam" from FocalPrice in the form of a meaningless "Thanks for your order". */
		contents = matchEmail(message,SALES_AT_FOCAL_PRICE,"Thanks for your order. Share your experience with your friends",true,FC_SPAM_ID_STRING);
		if (contents != null) {
			return processSpamFromFocalPrice(contents);
		}

		/* Check if it's a refund from PayPal regarding an order from FocalPrice. */
		contents = matchEmail(message,PAYPAL_EMAIL,"Refund from FocalPrice Inc.",true,REFUND_FROM_FC_ID_STRING);
		if (contents != null) {
			return processRefundFromFc(contents);
		}

		return false;
	}

}
