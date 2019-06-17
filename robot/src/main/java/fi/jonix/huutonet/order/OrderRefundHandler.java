package fi.jonix.huutonet.order;

import java.math.BigDecimal;
import java.math.MathContext;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import fi.jonix.huutonet.domain.VirtualCoin;
import fi.jonix.huutonet.domain.model.Currency;
import fi.jonix.huutonet.domain.model.Order;
import fi.jonix.huutonet.domain.model.OrderBundle;
import fi.jonix.huutonet.domain.model.dao.CurrencyDAO;
import fi.jonix.huutonet.domain.model.dao.MessageDAO;
import fi.jonix.huutonet.domain.model.dao.OrderDAO;
import fi.jonix.huutonet.email.EmailSender;
import fi.jonix.huutonet.exception.UnprocessableEmailException;
import fi.jonix.huutonet.tools.CurrencyFormatter;
import fi.jonix.huutonet.tools.MathUtilities;
import fi.jonix.huutonet.tools.Statics;
import fi.jonix.huutonet.tools.StringUtilities;

@Component(value = "orderRefundHandler")
public class OrderRefundHandler {

	public static final Logger logger = Logger.getLogger(OrderRefundHandler.class);

	@Autowired
	private OrderDAO orderDAO;
	
	@Autowired
	private MessageDAO messageDAO;
	
	@Autowired
	private OrderCancelationHandler orderCancelationHandler;
	
	
	@Autowired
	private CurrencyDAO currencyDAO;
	
	public void processRefundFromProvider(OrderBundle bundle, VirtualCoin amount, Boolean fullRefund) throws UnprocessableEmailException {
		/* In case we have started using a different currency when buying from the
		 * provider, we make sure that the currency at ordering time is used (I don't
		 * know if this will EVER be different from amount.getCurrency, but we do
		 * this just in case). */
		BigDecimal amountPC = currencyDAO.convertCurrency(amount.getCurrency(),
				bundle.getUsedProviderCurrency().getId(), amount.getValue());
		/* This can be a refund for one or several orders in the bundle. We don't get any information from
		 * the email about which orders are refunded (for a multi-order bundle), so let's figure it out here.
		 * 
		 * NOTE: we actually don't need the fullRefund information. Rather we use the state information
		 * of the orders in the bundle. */
		List<Order> bundleOrders = orderDAO.getOrdersInBundle(bundle);
		List<Order> refundableOrders = new ArrayList<Order>();
		boolean allClosedReturnedToProvider = true;
		for (Order order : bundleOrders) {
			if (order.getFinalSalesPriceInMarketCurrencyInclVat().compareTo(BigDecimal.ZERO) > 0
					&& (order.getState().equals(Order.STATE_NO_LONGER_FOR_SALE_AT_PROVIDER_LATE)
							|| order.getState().equals(Order.STATE_PROVIDER_CANCEL_PENDING))) {
				refundableOrders.add(order);
			}
			if (!order.getState().equals(Order.STATE_CLOSED_RETURNED_TO_PROVIDER)) {
				allClosedReturnedToProvider = false;
			}
		}
		if (allClosedReturnedToProvider/* && refundableOrders.size() == 0*/) {
			logger.debug("This refund is for \"closed returned to provider\".");
			for (Order order : bundleOrders) {
				refundableOrders.add(order);
			}
		}

		boolean discontinuer = false;
		if (refundableOrders.size() == 0) {
			/* This might be a discontinuer that FocalPrice has refunded without us asking for cancelation.
			 * Assume that discontinuers are always refunded for just one product at a time. We have two problems
			 * here determining for _which_ product in the bundle the discontinuer was issued. The first
			 * problem is that due to a bug in FocalPrice the refunded sum is not what we actually paid
			 * for the product, because we always get a small discount but the refunded sum is the nominal
			 * price. => This problem might now be fixed at FocalPrice (2.8.2012).
			 * The second problem is that several products can have the same price and in that case we won't
			 * know which one was refunded. */
			List<Order> refundableAlt1 = new ArrayList<Order>(); //First check for exact price matches
			for (Order order : bundleOrders) {
				//TODO: if this product was resent, then we should use the price of the resend-fields of the
				//order and the bundle
				if (order.getFinalSalesPriceInMarketCurrencyInclVat().compareTo(BigDecimal.ZERO) > 0
						&& (order.getState() >= Order.STATE_ORDERED_FROM_PROVIDER
								&& order.getState() < Order.STATE_SHIPPED_BY_PROVIDER)
								&& (order.getFinalProviderPriceInProviderCurrencyInclVat().compareTo(amountPC) == 0
										|| (bundleOrders.size() == 1
										&& order.getFinalProviderPriceInProviderCurrencyInclVat().
											add(bundle.getProviderShippingCostInProviderCurrencyInclVat()).
											compareTo(amountPC) == 0))) {
					refundableAlt1.add(order);
				}
			}
			List<Order> refundableAlt2 = new ArrayList<Order>(); //Then check for discounted price matches (between 1.5% and 2%)
			BigDecimal maxPrice = new BigDecimal(MathUtilities.roundTo2Decimals(amountPC.multiply(new BigDecimal("0.985"))));
			BigDecimal minPrice = new BigDecimal(MathUtilities.roundTo2Decimals(amountPC.multiply(new BigDecimal("0.98"))));
			for (Order order : bundleOrders) {
				//TODO: if this product was resent, then we should use the price of the resend-fields of the
				//order and the bundle
				if (order.getFinalSalesPriceInMarketCurrencyInclVat().compareTo(BigDecimal.ZERO) > 0
						&& (order.getState() >= Order.STATE_ORDERED_FROM_PROVIDER
								&& order.getState() < Order.STATE_SHIPPED_BY_PROVIDER)
								&& ((order.getFinalProviderPriceInProviderCurrencyInclVat().compareTo(maxPrice) <= 0
										&& order.getFinalProviderPriceInProviderCurrencyInclVat().compareTo(minPrice) >= 0)
										|| (bundleOrders.size() == 1
										&& (order.getFinalProviderPriceInProviderCurrencyInclVat().
												add(bundle.getProviderShippingCostInProviderCurrencyInclVat()).
												compareTo(maxPrice) <= 0
											&& order.getFinalProviderPriceInProviderCurrencyInclVat().
												add(bundle.getProviderShippingCostInProviderCurrencyInclVat()).
												compareTo(minPrice) >= 0)))) {
					refundableAlt2.add(order);
				}
			}
			if (refundableAlt1.size() == 1 && refundableAlt2.size() == 0) {
				refundableOrders = refundableAlt1;
			} else if (refundableAlt1.size() == 0 && refundableAlt2.size() == 1) {
				refundableOrders = refundableAlt2;
			} else {
				logger.warn("Not possible to figure out to which order a refund belongs for bundle " + bundle.getId()
						+ " (refundableAlt1.size = " + refundableAlt1.size() + ", refundableAlt2.size = " + refundableAlt2.size() + ").");
				throw new UnprocessableEmailException("Not possible to figure out to which order a refund belongs for bundle " + bundle.getId());
			}
			discontinuer = true;
			logger.debug("This refund is for a discontinuer (bundle " + bundle.getId() + ").");
		}
		/*if (discontinuer && fullRefund) {
			throw new UnprocessableEmailException("How could we get a full refund for a discontinuer (for bundle " + bundle.getId() + ")?");
		}*/
		
		/* Check if some order is resent. In that case the resendLoss should be used instead of
		 * the providerShippingCost. */
		for (Order order : refundableOrders) {
			if (order.getSendCount() > 1) {
				throw new UnprocessableEmailException("Not yet supported: cannot process refund from provider for"
						+ " bundle " + bundle.getId() + " since some order has been resent. Implement support for this case.");
			}
		}
		
		/* We only expect the shipping cost to be refunded if all other orders in the bundle
		 * have already been canceled. So let's figure out whether we should be getting a
		 * refund for shipping cost or not. */
		BigDecimal expectedShippingCostRefundPC = bundle.getProviderShippingCostInProviderCurrencyInclVat();
		if (refundableOrders.size() < bundleOrders.size()) {
			/* This refund doesn't concern all orders in the bundle. However shipping cost might still be
			 * refunded, because some order in the bundle might have been canceled even before ordering
			 * at provider (or some orders might even have been previously refunded). */
			for (Order order : bundleOrders) {
				if (!refundableOrders.contains(order)
						&& order.getFinalSalesPriceInMarketCurrencyInclVat().compareTo(BigDecimal.ZERO) > 0) {
					/* The bundle has other orders that are non-canceled. Don't expect refund for
					 * shipping cost. */
					expectedShippingCostRefundPC = BigDecimal.ZERO;
					break;
				}
			}
		}
		logger.debug("Expecting return of shipping cost = " + expectedShippingCostRefundPC);
		
		/* Check if the refunded amount is equal to what we expect. */
		boolean shippingCostRefundingAsExpected = true;
		BigDecimal totalSumPC = BigDecimal.ZERO;
		for (Order order : refundableOrders) {
			totalSumPC = totalSumPC.add(order.getFinalProviderPriceInProviderCurrencyInclVat());
		}
		if (totalSumPC.add(expectedShippingCostRefundPC).compareTo(amountPC) < 0) {
			/* It seems like the new FocalPrice system refunds not what we actually paid (using discount), but rather
			 * what the cost would be without any discount. So let's just accept this refund provided that the
			 * difference isn't too big. */
			//BigDecimal minAllowedRefundSum = new BigDecimal(MathUtilities.roundTo2Decimals(totalSum.
				//	divide(new BigDecimal("0.985"),MathContext.DECIMAL64))).add(expectedShippingCostRefund);
			BigDecimal maxAllowedRefundSum = new BigDecimal(MathUtilities.roundTo2Decimals(totalSumPC.
					divide(new BigDecimal("0.98"),MathContext.DECIMAL64))).add(expectedShippingCostRefundPC);
			if (amountPC.compareTo(maxAllowedRefundSum) > 0) {
				throw new UnprocessableEmailException("Unexpected refund amount received from provider for bundle " + bundle.getId()
						+ ". Did the provider refund TOO MUCH?");
			} else { //if (amount.compareTo(minAllowedRefundSum) >= 0) {
				logger.debug("Received a little too big refund (" + amountPC + " instead of the expected "
						+ totalSumPC.add(expectedShippingCostRefundPC) + " - however accept this since the difference"
						+ " is small enough (refunds are sometimes given using the nominal price, not considering"
						+ " discounts that we got at purchase time).");
			}
			/*BigDecimal maxAllowedProcentDiff = new BigDecimal("0.05");
			BigDecimal maxAllowedRefundSum = totalSum.multiply(maxAllowedProcentDiff);
			BigDecimal diff = amount.subtract(totalSum.add(expectedShippingCostRefund));
			if (diff.abs().compareTo(maxAllowedRefundSum) > 0) {
				throw new UnprocessableEmailException("Unexpected refund amount received from provider for bundle " + bundle.getId()
						+ ". Did the provider refund TOO MUCH?");
			} else {
				logger.debug("Received a little too big refund (" + amount + " instead of the expected "
						+ totalSum.add(expectedShippingCostRefund) + " - however accept this since the difference"
						+ " is small enough (refunds are sometimes given using the nominal price, not considering"
						+ " discounts that we got at purchase time).");
			}*/
		} else if (totalSumPC.add(expectedShippingCostRefundPC).compareTo(amountPC) > 0) {
			if (expectedShippingCostRefundPC.compareTo(BigDecimal.ZERO) > 0 && totalSumPC.compareTo(amountPC) == 0) {
				shippingCostRefundingAsExpected = false;
				logger.info("Provider didn't return shipping cost for bundle " + bundle.getId() + " although we expected refund also of shipping costs!");
			} else {
				throw new UnprocessableEmailException("Unexpected refund amount received from provider for bundle " + bundle.getId()
						+ ". The provider refunded an unexpected amount of money.");
			}
		}
		
		if (allClosedReturnedToProvider) {
			/* This one is extremely rare, because usually we don't get any "Refund from FocalPrice Inc."
			 * for refunds for "closed returned to provider". In fact it has only come once (for
			 * providerOrderId FPDI550702UY).
			 * Note: when the customer hasn't fetched his packet, then it's his own problem, so we
			 * won't give the money back to him (unless he asks about it). */
			DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
			String comment = "We got a refund of " + amount.getValue().toPlainString() + " from the provider for bundle " + bundle.getId()
					+ " which was closed and returned to the provider (on " + dateFormat.format(new Date()) + ").";
			logger.info(comment);
			if (bundle.getComment() != null && !bundle.getComment().trim().equals("")) {
				bundle.setComment(bundle.getComment() + ";" + comment);
			} else {
				bundle.setComment(comment);
			}
		} else {
			/* Inform the customer about the refund by iterating over the refunded orders.
			 * Note that setting of otherLoss will work also if several orders in the bundle
			 * are refunded, because refundSum > orderTargetedPaidBeforeZeroing only for
			 * the last order for which zeroSalesPriceForRefund is ran (and not even then
			 * if the bundle contains other orders that shouldn't be refunded). */
			for (Order order : refundableOrders) {
				/* For discontinuers also inform the customer separately about the discontinuation. */
				if (discontinuer) {
					String custEmail = "Moi,\n\nValitettavasti tuote \"" + order.getAd().getAdTemplate().getHeadline()
						+ "\" (tilausnumero " + order.getId() + ") ei ole enää saatavilla ja joudumme sen"
						+ " takia perumaan tilauksesi. Pahoittelemme tapahtunutta. Tietysti saat rahat takaisin.\n\n"
						+ "Ystävällisin terveisin,\nHK-myynti\n";
					EmailSender.sendEmailBatch(bundle.getSeller().getSystemEmail(),bundle.getCustomer().getEmail(),
							"Peruutettu tilaus",custEmail);
					order.setStateWithHistory(Order.STATE_NO_LONGER_FOR_SALE_AT_PROVIDER_LATE);
				}
				// If the provider didn't refund the shipping cost and we need to refund
				// the shipping cost to our customers, then we need to increase otherLoss
				// with what we paid to the provider for shipping.
				BigDecimal orderTargetedPaidBeforeZeroingMC = order.getTargetedPaidInMarketCurrency();
				VirtualCoin refundSumMC = orderCancelationHandler.zeroSalesPriceForRefund(order,new HashMap<String,VirtualCoin>());
				if (refundSumMC.getValue().compareTo(orderTargetedPaidBeforeZeroingMC) > 0 && !shippingCostRefundingAsExpected) {
					BigDecimal otherLossEuro = order.getOtherLossInEuro();
					BigDecimal providerShippingCostEuro = currencyDAO.convertCurrency(bundle.getUsedProviderCurrency().getId(),
							Currency.EURO, bundle.getProviderShippingCostInEuroInclVat());
					order.setOtherLossInEuro(otherLossEuro.add(providerShippingCostEuro));
				}
				order.setStateWithHistory(Order.STATE_MONEY_REFUNDED_BY_PROVIDER_FOR_CANCELED_ORDER);
				order.setStateWithHistory(Order.STATE_CLOSED_ORDER_CANCELED);
				String contextPath = messageDAO.getText("contextPath", bundle.getSeller(),order.getMarket().getLanguage());
				String custEmail = messageDAO.getText("moneyRefundedFromProvider", bundle.getSeller(), order.getMarket().getLanguage(),
						"ORDER_ID",""+order.getId(),
						"AD_HEADLINE",order.getAd().getAdTemplate().getHeadline(),
						"REFUND_SUM",CurrencyFormatter.print(refundSumMC, Statics.FINNISH),
						"GOOGLE_ORDER_URL",bundle.getSeller().getStoreUrl(),
						"CONTEXT_PATH",contextPath,
						"CONFIRMATION_CODE",bundle.getCustomer().getConfirmationCode(),
						"SIGNATURE_URL_ENC",StringUtilities.escapeString2URL(bundle.getSeller().getSignature()),
						"SIGNATURE",bundle.getSeller().getSignature());
				EmailSender.sendEmailBatch(bundle.getSeller().getSystemEmail(),bundle.getCustomer().getEmail(),
						"Hyvitys",custEmail);
				bundle.getCustomer().setInformedAboutTooBigPayment(true);
			}
		}
	}
	
}
