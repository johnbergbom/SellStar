package fi.jonix.huutonet.order;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import fi.jonix.huutonet.domain.VirtualCoin;
import fi.jonix.huutonet.domain.model.AdTemplate;
import fi.jonix.huutonet.domain.model.Currency;
import fi.jonix.huutonet.domain.model.Order;
import fi.jonix.huutonet.domain.model.OrderBundle;
import fi.jonix.huutonet.domain.model.Product;
import fi.jonix.huutonet.domain.model.Seller;
import fi.jonix.huutonet.domain.model.StateChange;
import fi.jonix.huutonet.domain.model.Verification;
import fi.jonix.huutonet.domain.model.dao.MessageDAO;
import fi.jonix.huutonet.domain.model.dao.OrderDAO;
import fi.jonix.huutonet.domain.model.dao.VerificationDAO;
import fi.jonix.huutonet.email.EmailSender;
import fi.jonix.huutonet.tools.CurrencyFormatter;
import fi.jonix.huutonet.tools.MathUtilities;
import fi.jonix.huutonet.tools.Statics;
import fi.jonix.huutonet.tools.StringUtilities;

@Component(value = "orderCancelationHandler")
public class OrderCancelationHandler {

	public static final Logger logger = Logger.getLogger(OrderCancelationHandler.class);

	@Autowired
	private MessageDAO messageDAO;

	@Autowired
	private ProviderHandler providerHandler;
	
	@Autowired
	private OrderDAO orderDAO;
	
	@Autowired
	private VerificationDAO verificationDAO;
	
	/**
	 * This method should be called whenever a refund is made to the customer. It zeroes
	 * the finalSalesPrice of the order and also zeroes the bundle.customerShippingCost
	 * if this is the last order in the bundle to be refunded. The refundable amount is
	 * returned.
	 * 
	 * targetedProviderCost.targetedProviderCostInEuro is set to the provider cost which is targeted
	 * to this particular refund. If the shipping cost was refunded, then the targeted provider
	 * cost includes also the amount that was paid to the provider for shipping (if any), or
	 * else just the amount that was paid to the provider for the product (excluding shipping costs).
	 * 
	 * @return The amount of money that should be refunded for this one. It's equal to
	 * order.targetedPaid if there are orders in the bundle that have never been refunded,
	 * or order.targetedPaid + bundle.targetedPaid in case this is the last order in the
	 * bundle to be refunded. */
	public VirtualCoin zeroSalesPriceForRefund(Order order, Map<String,VirtualCoin> targetedProviderCost) {
		order.setTransferedToVerification(false);
		
		/* Zero the sales price of this order. */
		order.setFinalSalesPriceInEuroInclVat(BigDecimal.ZERO);
		order.setFinalSalesPriceInEuroExclVat(BigDecimal.ZERO);
		order.setFinalSalesPriceInMarketCurrencyInclVat(BigDecimal.ZERO);
		order.setFinalSalesPriceInMarketCurrencyExclVat(BigDecimal.ZERO);
		
		if (order.getFinalProviderPriceInEuroInclVat() != null) {
			logger.debug("Adding " + order.getFinalProviderPriceInEuroInclVat() + " euro to targetedProviderCost (for provider product expense).");
			targetedProviderCost.put("targetedProviderCostInEuroInclVat", new VirtualCoin(Currency.EURO,order.getFinalProviderPriceInEuroInclVat()));
			targetedProviderCost.put("targetedProviderCostInEuroExclVat", new VirtualCoin(Currency.EURO,order.getFinalProviderPriceInEuroExclVat()));
		}
		
		/* If the sales price of all orders is zero, it means that all orders
		 * in the bundle have been canceled or refunded ("refund for sent order").
		 * In that case we should also zero the shipping cost of the bundle. */
		BigDecimal refundSumMC = order.getTargetedPaidInMarketCurrency();
		OrderBundle bundle = order.getOrderBundle();
		List<Order> bundleOrders = orderDAO.getOrdersInBundle(bundle);
		BigDecimal totalFinalSalesPriceInclVatMC = BigDecimal.ZERO;
		for (Order o : bundleOrders) {
			totalFinalSalesPriceInclVatMC = totalFinalSalesPriceInclVatMC.add(o.getFinalSalesPriceInMarketCurrencyInclVat());
		}
		if (totalFinalSalesPriceInclVatMC.compareTo(BigDecimal.ZERO) == 0) {
			bundle.setTransferedToVerification(false);
			
			/* Zero the sales price of this bundle. */
			bundle.setCustomerShippingCostInEuroInclVat(BigDecimal.ZERO);
			bundle.setCustomerShippingCostInEuroExclVat(BigDecimal.ZERO);
			bundle.setCustomerShippingCostInMarketCurrencyInclVat(BigDecimal.ZERO);
			bundle.setCustomerShippingCostInMarketCurrencyExclVat(BigDecimal.ZERO);
			refundSumMC = refundSumMC.add(bundle.getTargetedPaidShippingCostInMarketCurrency());
			if (bundle.getProviderShippingCostInEuroInclVat() != null) {
				VirtualCoin oldValueInclVat = targetedProviderCost.get("targetedProviderCostInEuroInclVat");
				BigDecimal bdInclVat = oldValueInclVat.getValue();
				logger.debug("bd is " + bdInclVat + " and to this we add " + bundle.getProviderShippingCostInEuroInclVat() + " to targetedProviderCost (for provider shipping expense).");
				targetedProviderCost.put("targetedProviderCostInEuroInclVat", new VirtualCoin(Currency.EURO,bdInclVat.add(bundle.getProviderShippingCostInEuroInclVat())));
				VirtualCoin oldValueExclVat = targetedProviderCost.get("targetedProviderCostInEuroExclVat");
				BigDecimal bdExclVat = oldValueExclVat.getValue();
				targetedProviderCost.put("targetedProviderCostInEuroExclVat", new VirtualCoin(Currency.EURO,bdExclVat.add(bundle.getProviderShippingCostInEuroExclVat())));
			}
		}
		return new VirtualCoin(bundle.getUsedCustomerCurrency(),refundSumMC);
	}
	
	/* If the order was already paid for, then cancel the order and ask the customer to
	 * confirm the cancelation of the product, or else simply close the order and just
	 * inform the customer. */
	public void cancelWhenNoLongerForSale(Order order) {
		OrderBundle bundle = order.getOrderBundle();
		String infoEmail = null;
		if (isOrdered(order)) {
			logger.debug("Order " + order.getId() + " is ordered from provider but canceled because it's no longer for sale.");
			/* DON'T run zeroSalesPriceForRefund here, because if we would do so, then SellStar might give
			 * the customer a refund before we've gotten a refund from the provider. For this case we should
			 * rather call zeroSalesPriceForRefund when we get a refund from the provider for this order. */
			infoEmail = messageDAO.getText("productNoLongerForSaleAtProviderOrderedFromProviderAndCanceled", bundle.getSeller(), order.getMarket().getLanguage(),
					"AD_HEADLINE",order.getAd().getAdTemplate().getHeadline(),
					"ORDER_ID",""+order.getId(),
					"SIGNATURE",bundle.getSeller().getSignature());
			order.setStateWithHistory(Order.STATE_NO_LONGER_FOR_SALE_AT_PROVIDER_LATE);
			EmailSender.sendOperationProblemReport("Please cancel order at provider",
					"Order " + order.getId() + " needs to be canceled at provider because it's no longer for sale. Or maybe we should"
					+ " ask the provider if they will still ship this product although it's no longer for sale at the web page.\n\n"
					+ "TODO: when the detection of \"no longer for sale\" has been made waterproof, then this one can also be automated.\n"
					+ "Actually it isn't necessarily needed to automate this one because in these cases the provider normally refunds"
					+ " the order as a discontinuer.");
		} else {
			//BigDecimal shippingCostToRefund = zeroSalesPriceForRefund(order);
			//BigDecimal refundSum = shippingCostToRefund.add(order.getTargetedPaidInMarketCurrency());
			VirtualCoin refundSumMC = zeroSalesPriceForRefund(order,new HashMap<String,VirtualCoin>());
			if (refundSumMC.getValue().compareTo(BigDecimal.ZERO) > 0) {
				logger.debug("Order " + order.getId() + " is paid (but not ordered from provider) and canceled because it's no longer for sale.");
				String contextPath = messageDAO.getText("contextPath", bundle.getSeller(),order.getMarket().getLanguage());
				infoEmail = messageDAO.getText("productNoLongerForSaleAtProviderPaidAndCanceled", bundle.getSeller(), order.getMarket().getLanguage(),
						"AD_HEADLINE",order.getAd().getAdTemplate().getHeadline(),
						"ORDER_ID",""+order.getId(),
						"REFUND_SUM",CurrencyFormatter.print(refundSumMC, Statics.FINNISH),
						"GOOGLE_ORDER_URL",bundle.getSeller().getStoreUrl(),
						"CONTEXT_PATH",contextPath,
						"CONFIRMATION_CODE",bundle.getCustomer().getConfirmationCode(),
						"SIGNATURE_URL_ENC",StringUtilities.escapeString2URL(bundle.getSeller().getSignature()),
						"SIGNATURE",bundle.getSeller().getSignature());
				bundle.getCustomer().setInformedAboutTooBigPayment(true);
			} else {
				logger.debug("Order " + order.getId() + " is unpaid and canceled because it's no longer for sale.");
				infoEmail = messageDAO.getText("productNoLongerForSaleAtProviderUnpaid", bundle.getSeller(), order.getMarket().getLanguage(),
						"ORDER_ID",""+order.getId(),
						"AD_HEADLINE",order.getAd().getAdTemplate().getHeadline(),
						"SIGNATURE",bundle.getSeller().getSignature());
			}
			order.setStateWithHistory(Order.STATE_NO_LONGER_FOR_SALE_AT_PROVIDER_EARLY);
			order.setStateWithHistory(Order.STATE_CLOSED_ORDER_CANCELED);
		}
		EmailSender.sendEmailBatch(bundle.getSeller().getSystemEmail(),bundle.getCustomer().getEmail(),
				"Kävipä nolosti",infoEmail);
	}
	
	/**
	 * We don't need to inform the customer here. If money does show up after all (_after_
	 * this method is ran), then BankHandler sends an email about this to the customer.
	 * Exception: if refundSumMC > 0 below, then we should inform the customer that the
	 * order was closed automatically (but the implementation of this can wait until
	 * this case actually comes up in practice).
	 */
	public void closeOldNewOrFaultyContactInfoOrTooLittlePaid(Order order) {
		if (!order.getState().equals(Order.STATE_NEW)
				&& !order.getState().equals(Order.STATE_FAULTY_CONTACT_INFO)
				&& !order.getState().equals(Order.STATE_PAID_TOO_LITTLE_ERROR)) {
			throw new RuntimeException("Faulty state for order " + order.getId() + " in closeOldNewOrFaultyContactInfoOrTooLittlePaid.");
		}
		/* First set the state to "closed never paid" so that a trace of this is left. */
		order.setStateWithHistory(Order.STATE_CLOSED_CUSTOMER_NEVER_PAID);

		VirtualCoin refundSumMC = zeroSalesPriceForRefund(order,new HashMap<String,VirtualCoin>());
		if (refundSumMC.getValue().compareTo(BigDecimal.ZERO) > 0) {
			// We get here if the customer had paid for part of his product. Since this comes
			// so rarely I haven't yet implemented this one. We need to send a refund message
			// to the customer here.
			// NOTE: should we here set informedAboutTooBigPayment = false?
			logger.info("Closing order " + order.getId() + " because it's not fully paid and has been in state "
					+ order.getState() + " for a long time. WE NEED TO INFORM THE CUSTOMER ABOUT THIS (SEND HIM A REFUND MESSAGE).");
			throw new RuntimeException("Not yet implemented. Here we need to send a refund message to the customer.");
		} else {
			logger.info("Closing order " + order.getId() + " because it's unpaid and has been in state "
					+ order.getState() + " for a long time. We do NOT inform the customer about this.");
		}
		order.setStateWithHistory(Order.STATE_CLOSED_ORDER_CANCELED);
	}
	
	public void closeBecauseNewBatchDidNotCome(Order order) {
		OrderBundle bundle = order.getOrderBundle();
		//BigDecimal shippingCostToRefund = zeroSalesPriceForRefund(order);
		VirtualCoin refundSumMC = zeroSalesPriceForRefund(order,new HashMap<String,VirtualCoin>());
		if (refundSumMC.getValue().compareTo(BigDecimal.ZERO) > 0) {
			//throw new RuntimeException("Unexpected event (closeBecauseNewBatchDidNotCome).");
			/* Ok, so the customer had actually paid for this order. This can happen for example
			 * if the customer has ordered a common bill and paid according to that instead
			 * of according to the payment information email. Make sure that the customer
			 * will be informed about this by setting informedAboutTooBigPayment = false.
			 * 
			 * TODO: here we could send an email telling the customer that "oops, you'll get
			 * a refund of X euros because the already paid order was out of stock". However
			 * this isn't so crucial, because by setting informedAboutTooBigPayment = false
			 * the customer will still get this information next time faultyPaymentFixer is ran. */
			order.getOrderBundle().getCustomer().setInformedAboutTooBigPayment(false);
		}
		order.setStateWithHistory(Order.STATE_CLOSED_ORDER_CANCELED);
		String notForSaleEmail = messageDAO.getText("newBatchNotReceivedClosing", bundle.getSeller(), order.getMarket().getLanguage(),
				"ORDER_ID",""+order.getId(),
				"SIGNATURE",bundle.getSeller().getSignature());
		EmailSender.sendEmailBatch(bundle.getSeller().getSystemEmail(),bundle.getCustomer().getEmail(),
				"Täydennyserä ei tullut (tilaus " + order.getId() + ")",notForSaleEmail);
	}

	/* This method returns true if an order is placed at provider. The following special case is also
	 * recognized: in case the order was placed at provider and _later_ set in the state "should resend",
	 * then true is returned only if it exists _after_ the "should resend" state. */
	private boolean isOrdered(Order order) {
		Date latestOrderedDate = null;
		Date latestResendDate = null;
		for (StateChange sc : order.getStateChanges()) {
			if (sc.getState().equals(Order.STATE_ORDERED_FROM_PROVIDER) || sc.getState().equals(Order.STATE_ORDERED_FROM_PROVIDER_NO_ID)) {
				if (latestOrderedDate == null || latestOrderedDate.before(sc.getUpdateDate())) {
					latestOrderedDate = sc.getUpdateDate();
				}
			} else if (sc.getState().equals(Order.STATE_SHOULD_RESEND)) {
				if (latestResendDate == null || latestResendDate.before(sc.getUpdateDate())) {
					latestResendDate = sc.getUpdateDate();
				}
			}
		}
		if (latestOrderedDate != null && (latestResendDate == null || latestOrderedDate.after(latestResendDate))) {
			return true;
		}
		return false;
	}
	
	public void handleCancelationRequest(Order order) throws Exception {
		OrderBundle bundle = order.getOrderBundle();
		Seller seller = bundle.getSeller();
		if (order.getState().equals(Order.STATE_CLOSED_ORDER_CANCELED)) {
			String errorEmail = "Moi,\n\nTilauksesi " + order.getId() + " on jo suljettu.\n\n"
				+ "Ystävällisin terveisin,\n" + seller.getSignature() + "n";
			EmailSender.sendEmailBatch(seller.getSystemEmail(),bundle.getCustomer().getEmail(),
				"Tilaus on jo suljettu (tilausnumero " + order.getId() + ")",errorEmail);
		} else if (order.getState() >= Order.STATE_FULFILMENT_CENTER && !order.getState().equals(Order.STATE_CLOSED_CUSTOMER_NEVER_PAID)) {
			//TODO: Take the contents of this email from the database.
			String errorEmail = "Moi,\n\nValitettavasti ei pysty enää peruuttamaan tilausta numero " + order.getId() + ".\n\n"
				+ "Ystävällisin terveisin,\n" + seller.getSignature() + "\n";
			EmailSender.sendEmailBatch(seller.getSystemEmail(),bundle.getCustomer().getEmail(),
					"Tilausta ei pysty enää peruuttamaan (tilausnumero " + order.getId() + ")",errorEmail);
		} else if ((order.getState() >= Order.STATE_FAULTY_CONTACT_INFO && order.getState() <= Order.STATE_STOCK_SHORTAGE)
				|| order.getState().equals(Order.STATE_CLOSED_CUSTOMER_NEVER_PAID)) {
			String subject = "Peruutuspyyntö vastaanotettu (tilausnumero " + order.getId() + ")";
			//order.setStateWithHistory(Order.STATE_CUSTOMER_CANCEL_PENDING);
			order.setStateWithHistory(Order.STATE_CUSTOMER_CANCEL_CONFIRMED);
			
			String custEmail = null;
			String contextPath = messageDAO.getText("contextPath", seller,order.getMarket().getLanguage());
			if (isOrdered(order)) {
				logger.debug("Order " + order.getId() + " ordered from provider and canceled at customer's request.");
				/* DON'T run zeroSalesPriceForRefund here, because if we would do so, then SellStar might give
				 * the customer a refund before we've gotten a refund from the provider. For this case we should
				 * rather call zeroSalesPriceForRefund when we get a refund from the provider for this order. */
				order.setStateWithHistory(Order.STATE_PROVIDER_CANCEL_PENDING);
				custEmail = messageDAO.getText("orderCancelRequestOrderedFromProvider", seller, order.getMarket().getLanguage(),
						"ORDER_ID",""+order.getId(),
						"SIGNATURE",seller.getSignature());
				if (orderDAO.getNbrOrdersInBundle(bundle) == 1) {
					/* This one is simple to handle automatically. */
					boolean success = providerHandler.cancelOrder(order);
					if (!success) {
						EmailSender.sendOperationProblemReport("Please cancel order at provider",
								"Customer wants to cancel order " + order.getId() + " at provider (automated cancelation"
								+ " failed for single-order bundle).");
					} else {
						//order.setStateWithHistory(Order.STATE_PROVIDER_CANCEL_CONFIRMED);
					}
				} else {
					EmailSender.sendOperationProblemReport("Please cancel order at provider",
							"Customer wants to cancel order " + order.getId() + " at provider (automated cancelation"
							+ " is not yet implemented for multi-order bundles).");
				}
			} else {
				//BigDecimal shippingCostToRefund = zeroSalesPriceForRefund(order);
				//BigDecimal refundSum = shippingCostToRefund.add(order.getTargetedPaidInMarketCurrency());
				VirtualCoin refundSumMC = zeroSalesPriceForRefund(order,new HashMap<String,VirtualCoin>());
				if (refundSumMC.getValue().compareTo(BigDecimal.ZERO) > 0) {
					logger.debug("Order " + order.getId() + " is paid (but not ordered from provider) and canceled at customer's request.");
					custEmail = messageDAO.getText("orderCancelRequestPaid", seller, order.getMarket().getLanguage(),
							"ORDER_ID",""+order.getId(),
							"REFUND_SUM",CurrencyFormatter.print(refundSumMC, Statics.FINNISH),
							"GOOGLE_ORDER_URL",seller.getStoreUrl(),
							"CONTEXT_PATH",contextPath,
							"CONFIRMATION_CODE",bundle.getCustomer().getConfirmationCode(),
							"SIGNATURE_URL_ENC",StringUtilities.escapeString2URL(seller.getSignature()),
							"SIGNATURE",seller.getSignature());
					bundle.getCustomer().setInformedAboutTooBigPayment(true);
				} else {
					logger.debug("Order " + order.getId() + " is unpaid and canceled at customer's request.");
					custEmail = messageDAO.getText("orderCancelRequestUnpaid", seller, order.getMarket().getLanguage(),
							"ORDER_ID",""+order.getId(),
							"SIGNATURE",seller.getSignature());
				}
				order.setStateWithHistory(Order.STATE_CLOSED_ORDER_CANCELED);
			}
			EmailSender.sendEmailBatch(seller.getSystemEmail(),bundle.getCustomer().getEmail(),subject,custEmail);
		} else {
			String operatorEmail = "Problem: the customer wanted to cancel order " + order.getId() + " but this order is in an\n"
				+ "unexpected state (state = " + order.getState() + "). Cancellation request failed.\n\n"
				+ "A code change might be necessary here (because this should never occur).\n\n"
				+ "orderId = " + order.getId() + ", referenceNbr = " + order.getCustomerReferenceNumber() + "\n";
			EmailSender.sendOperationProblemReport("Cancellation received problem for order "
					+ order.getId(),operatorEmail,seller.getSystemEmail());
		}
	}
	
}
