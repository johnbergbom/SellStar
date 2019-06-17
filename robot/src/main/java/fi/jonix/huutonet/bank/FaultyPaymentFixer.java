package fi.jonix.huutonet.bank;

import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import fi.jonix.huutonet.domain.ApplicationContextPlaceholder;
import fi.jonix.huutonet.domain.VirtualCoin;
import fi.jonix.huutonet.domain.model.Currency;
import fi.jonix.huutonet.domain.model.Customer;
import fi.jonix.huutonet.domain.model.Market;
import fi.jonix.huutonet.domain.model.Order;
import fi.jonix.huutonet.domain.model.OrderBundle;
import fi.jonix.huutonet.domain.model.Seller;
import fi.jonix.huutonet.domain.model.Settings;
import fi.jonix.huutonet.domain.model.StateChange;
import fi.jonix.huutonet.domain.model.Transaction;
import fi.jonix.huutonet.domain.model.dao.CurrencyDAO;
import fi.jonix.huutonet.domain.model.dao.CustomerDAO;
import fi.jonix.huutonet.domain.model.dao.MessageDAO;
import fi.jonix.huutonet.domain.model.dao.OrderBundleDAO;
import fi.jonix.huutonet.domain.model.dao.OrderDAO;
import fi.jonix.huutonet.domain.model.dao.SellerDAO;
import fi.jonix.huutonet.domain.model.dao.SettingsDAO;
import fi.jonix.huutonet.domain.model.dao.TransactionDAO;
import fi.jonix.huutonet.domain.model.dao.TransactionDAOImpl;
import fi.jonix.huutonet.email.EmailSender;
import fi.jonix.huutonet.email.EmailWrapper;
import fi.jonix.huutonet.order.ContactInfoChecker;
import fi.jonix.huutonet.order.OrderInformation;
import fi.jonix.huutonet.order.ContactInfoChecker.AddressFault;
import fi.jonix.huutonet.tools.CurrencyFormatter;
import fi.jonix.huutonet.tools.Statics;
import fi.jonix.huutonet.tools.StringUtilities;

/**
 * This class attempts to fix too small to too big payments. It attempts to handle
 * the fact that customers who have ordered several items might pay everything in
 * one shot. Another problem with multiple orders is that the customer paid the
 * correct sums but mixed up the reference numbers. This way the total sum is correct
 * but individual items appear in the database as having the wrong payment.
 * 
 * @author john
 */
@Component(value = "faultyPaymentFixer")
public class FaultyPaymentFixer {

	public static final Logger logger = Logger.getLogger(FaultyPaymentFixer.class);
	
	private static int MAX_TOO_SMALL_PAYMENT_REMINDERS = 4;
	
	@Autowired
	private OrderDAO orderDAO;
	
	@Autowired
	private TransactionDAO transactionDAO;
	
	@Autowired
	private MessageDAO messageDAO;
	
	@Autowired
	private CustomerDAO customerDAO;
	
	@Autowired
	private OrderBundleDAO orderBundleDAO;
	
	@Autowired
	private SellerDAO sellerDAO;
	
	@Autowired
	private CurrencyDAO currencyDAO;
	
	/**
	 * Fix payments where the total sum is correct but payment was faulty for individual orders.
	 * This can happen for example if the customer paid for all orders using just one payment,
	 * or if the shipping cost was forgotten and then later paid as a separate transaction.
	 *  
	 * @param individual
	 * @param email
	 * @return Number of fixed payments.
	 */
	private int fixCorrectTotalSumPayments(List<Order> orders, String email, boolean printInfo) {
		/* For all orders set the targeted payment equal to the expected payment + for orders in state
		 * "new" or "paid to little error" set the state to "customer paid". */
		StringBuffer strBuf = new StringBuffer();
		for (Order order : orders) {
			BigDecimal expectedPayMC = order.getFinalSalesPriceInMarketCurrencyInclVat();
			strBuf.append("order " + order.getId() + " had expectedPay " + expectedPayMC.toPlainString()
					+ " and targetedPaid = " + order.getTargetedPaidInMarketCurrency().toPlainString() + ",");
			order.setTargetedPaidInMarketCurrency(expectedPayMC);
		}
		/*if (printInfo) {
			logger.debug("(Fix #1): Fixed " + orders.size() + " orders for " + email + ": " + strBuf.toString());
		}*/
		return orders.size();
	}

	/**
	 * Fix payments where the total sum is correct but payment was faulty for individual bundles.
	 * This can happen for example if the customer paid for all orders using just one payment,
	 * or if the shipping cost was forgotten and then later paid as a separate transaction.
	 *  
	 * @param individual
	 * @param email
	 * @return Number of fixed payments.
	 */
	private int fixCorrectTotalSumPaymentsForBundles(List<OrderBundle> bundles, String email, boolean printInfo) {
		/* For all bundles set the targeted payment equal to the expected payment. */
		StringBuffer strBuf = new StringBuffer();
		for (OrderBundle bundle : bundles) {
			BigDecimal expectedPayMC = bundle.getCustomerShippingCostInMarketCurrencyInclVat();
			strBuf.append("bundle " + bundle.getId() + " had expectedPay " + expectedPayMC.toPlainString()
					+ " and targetedPaid = " + bundle.getTargetedPaidShippingCostInMarketCurrency().toPlainString() + ",");
			bundle.setTargetedPaidShippingCostInMarketCurrency(expectedPayMC);
		}
		if (printInfo) {
			logger.info("(Fix #2): Fixed " + bundles.size() + " bundles for " + email + ": " + strBuf.toString());
		}
		return bundles.size();
	}
	
	/* This method attempts to fix some bundle if possible, in case some bundle has excessive targeted payments
	 * although totally the customer has paid too little.
	 * NOTE: All orders in the orders-argument need to have the same currency.
	 * */
	private int attemptToFixSomeBundle(List<Order> orders, boolean printInfo) {
		int fixedOrders = 0;
		Map<Long,String> bundleIdMap = new HashMap<Long,String>();
		Long currency = null;
		while (true) {
			BigDecimal bundledOrdersTargeted = BigDecimal.ZERO;
			BigDecimal bundledOrdersExpected = BigDecimal.ZERO;
			Long currBundleId = null;
			/* Find an order with a targeted payment that's greater than the expected payment. */
			for (Order order : orders) {
				if (currBundleId == null && bundleIdMap.get(order.getOrderBundle().getId()) == null) {
					currBundleId = order.getOrderBundle().getId();
					bundleIdMap.put(currBundleId, "");
				}
				if (currBundleId != null && currBundleId.equals(order.getOrderBundle().getId())) {
					bundledOrdersTargeted = bundledOrdersTargeted.add(order.getTargetedPaidInMarketCurrency());
					bundledOrdersExpected = bundledOrdersExpected.add(order.getFinalSalesPriceInMarketCurrencyInclVat());
				}
				if (currency == null) {
					currency = order.getOrderBundle().getUsedCustomerCurrency().getId();
				} else if (!order.getOrderBundle().getUsedCustomerCurrency().getId().equals(currency)) {
					throw new RuntimeException("All orders in the list don't have the same currency.");
				}
			}
			if (bundledOrdersTargeted.compareTo(BigDecimal.ZERO) > 0
					&& bundledOrdersTargeted.compareTo(bundledOrdersExpected) >= 0) {
				/* This bundle can be fixed with regard to the product specific
				 * costs (not necessarily with regard to shipping cost). */
				BigDecimal diff = bundledOrdersTargeted.subtract(bundledOrdersExpected);
				StringBuffer strBuf = new StringBuffer();
				Order order = null;
				for (Order o : orders) {
					if (currBundleId.equals(o.getOrderBundle().getId())) {
						order = o;
						BigDecimal expectedPay = o.getFinalSalesPriceInMarketCurrencyInclVat();
						strBuf.append(" order " + o.getId() + " had expectedPay " + expectedPay.toPlainString()
								+ " and targetedPaid = " + o.getTargetedPaidInMarketCurrency().toPlainString() + ",");
						o.setTargetedPaidInMarketCurrency(expectedPay);
						fixedOrders++;
					}
				}
				/* IMPORTANT: Don't move so much of the excess part to the bundle that
				 * bundle.targeted > bundle.expected. The rest should be moved to an order.
				 * Otherwise the following could happen (well, at least if the operator
				 * has changed some targeted payments by hand):
				 * Bundle A: expected = 2, targeted = 2, order A.o1: expected = 2, targeted = 0
				 * Bundle B: expected = 2, targeted = 0, order B.o1: expected = 2, targeted = 5
				 * =>this would then give:
				 * Bundle A: expected = 2, targeted = 2, order A.o1: expected = 2, targeted = 0
				 * Bundle B: expected = 2, targeted = 3, order B.o1: expected = 2, targeted = 2
				 * =>Now the customer will get a payment reminder for bundle A as well as a
				 * "paid-too-much" email regarding bundle B.
				 * Instead we want this:
				 * Bundle A: expected = 2, targeted = 2, order A.o1: expected = 2, targeted = 0
				 * Bundle B: expected = 2, targeted = 2, order B.o1: expected = 2, targeted = 3 */
				BigDecimal bundleTargeted = order.getOrderBundle().getTargetedPaidShippingCostInMarketCurrency();
				BigDecimal bundleExpected = order.getOrderBundle().getCustomerShippingCostInMarketCurrencyInclVat();
				BigDecimal orderTargetedAtStart = order.getTargetedPaidInMarketCurrency();
				
				//First move everything to the bundle
				order.getOrderBundle().setTargetedPaidShippingCostInMarketCurrency(bundleTargeted.add(diff));
				
				//If too much is now targeted to the bundle, then move the excess to the order
				BigDecimal bundleExcess = order.getOrderBundle().getTargetedPaidShippingCostInMarketCurrency().
					subtract(order.getOrderBundle().getCustomerShippingCostInMarketCurrencyInclVat());
				if (bundleExcess.compareTo(BigDecimal.ZERO) > 0) {
					order.getOrderBundle().setTargetedPaidShippingCostInMarketCurrency(bundleExpected);
					order.setTargetedPaidInMarketCurrency(order.getTargetedPaidInMarketCurrency().add(bundleExcess));
				}
				BigDecimal movedToBundle = order.getOrderBundle().getTargetedPaidShippingCostInMarketCurrency().subtract(bundleTargeted);
				BigDecimal movedToOrder = order.getTargetedPaidInMarketCurrency().subtract(orderTargetedAtStart);
				
				if (printInfo) {
					logger.info("(Fix #3): Fixed " + fixedOrders + " orders for " + order.getOrderBundle().getCustomer().getEmail()
							+ ":" + strBuf.toString() + " then moved " + movedToOrder.toPlainString() + " to order " + order.getId()
							+ " and moved " + movedToBundle.toPlainString() + " to bundle " + order.getOrderBundle().getId());
				}
			}
			if (currBundleId == null) {
				// no more bundles found in list, abort
				break;
			}
		}
		return fixedOrders;
	}
	
	/**
	 * This method fixes payment problems where several orders were made and the total
	 * sum was correct but individual payments were faulty (can happen e.g. if the customer
	 * mixes up the reference numbers).
	 * 
	 * @return Number of fixed payments.
	 */
	public int fixFaultyPayments(boolean printInfo) {
		/* This method works by moving excessive payments of orders to bundles. Therefore we should start
		 * by first moving money from bundles that have targetedPaid > expectedPaid (only the excessive
		 * part) to orders. If this weren't done then we could possibly run into a situation where too much
		 * is paid for bundles (shipping costs) and too little is paid for orders (finalSalesPrice), but
		 * still the situation cannot be solved automatically).
		 * 
		 * Note: here we could possibly also implement the following: move money from bundles that have
		 * bundle.targetedPaid > 0 to the corresponding order(s) of the bundle if
		 * order.targetedPaid < order.expectedPaid. */
		Map<String,TargetedBundles> targetedBundleMap = TargetedBundles.getUnevenlyTargetedBundles();
		Iterator<String> iter = targetedBundleMap.keySet().iterator();
		while (iter.hasNext()) {
			String id = iter.next();
			TargetedBundles targetedBundles = targetedBundleMap.get(id);
			String email = TargetedOrders.id2Email(id);
			for (OrderBundle bundle : targetedBundles.getBundles()) {
				if (bundle.getTargetedPaidShippingCostInMarketCurrency().compareTo(bundle.getCustomerShippingCostInMarketCurrencyInclVat()) > 0) {
					/* Move the excessive payment of this bundle to some order in this bundle. */
					BigDecimal diff = bundle.getTargetedPaidShippingCostInMarketCurrency().subtract(bundle.getCustomerShippingCostInMarketCurrencyInclVat());
					for (Order o : orderDAO.getOrdersInBundle(bundle)) {
						BigDecimal newTargeted = bundle.getTargetedPaidShippingCostInMarketCurrency().subtract(diff);
						if (printInfo) {
							logger.debug("Moving " + diff.toPlainString() + " " + bundle.getUsedCustomerCurrency().getSymbol() + " from targetedPaid of bundle "
									+ bundle.getId() + " to targetedPaid of order " + o.getId() + " (for customer " + email + ").");
						}
						bundle.setTargetedPaidShippingCostInMarketCurrency(newTargeted);
						newTargeted = o.getTargetedPaidInMarketCurrency().add(diff);
						o.setTargetedPaidInMarketCurrency(newTargeted);
						break; //only move the same money to ONE order!
					}
				}
			}
		}
		
		/* Balance the payments of orders + move excess money from orders to bundles. */
		Map<String,String> tooSmallPaymentsIds = new HashMap<String,String>();
		List<EmailWrapper> emailList = new ArrayList<EmailWrapper>();
		int nbrFixed = 0;
		Map<String,TargetedOrders> targetedOrderMap = TargetedOrders.getUnevenlyTargetedOrders();
		iter = targetedOrderMap.keySet().iterator();
		while (iter.hasNext()) {
			String id = iter.next();
			TargetedOrders targetedOrders = targetedOrderMap.get(id);
			String email = TargetedOrders.id2Email(id);
			
			if (printInfo && targetedOrders.getTargetedPaidInMarketCurrency().compareTo(BigDecimal.ZERO) > 0) {
				logger.info("Total number of orders for " + id + ": " + targetedOrders.getOrders().size() + ". Expected payment: "
						+ targetedOrders.getExpectedPaymentInMarketCurrency()
						+ " and targeted payment: " + targetedOrders.getTargetedPaidInMarketCurrency());
			}
			BigDecimal diff = targetedOrders.getTargetedPaidInMarketCurrency().subtract(targetedOrders.getExpectedPaymentInMarketCurrency());
			if (diff.compareTo(BigDecimal.ZERO) == 0) {
				nbrFixed += fixCorrectTotalSumPayments(targetedOrders.getOrders(),email,printInfo);
			} else if (diff.compareTo(BigDecimal.ZERO) > 0) { //too big payment
				/* Set all orders to state customer paid and move the excess money to the
				 * shipping cost of some bundle. */
				nbrFixed += fixCorrectTotalSumPayments(targetedOrders.getOrders(),email,printInfo);
				OrderBundle bundle = targetedOrders.getOrders().get(0).getOrderBundle();
				bundle.setTargetedPaidShippingCostInMarketCurrency(bundle.getTargetedPaidShippingCostInMarketCurrency().add(diff));
				//Map<String,TargetedBundles> targetedBundleMap = TargetedBundles.getUnevenlyTargetedBundles();
				//TargetedBundles bundles = targetedBundleMap.get(TargetedOrders.email2Id(email,targetedOrders.getSeller()));
			} else { //too small payment
				tooSmallPaymentsIds.put(id,"");
				/* Ok, so the total targeted payment for orders wasn't great enough
				 * to cover the expected cost. However it's possible, that the targeted
				 * payment can cover the expected cost of some bundle. */
				attemptToFixSomeBundle(targetedOrders.getOrders(),printInfo);
			}
		}

		/* Balance the payments of bundles. */
		/*Settings lostRefundRightAmountInclVatSetting = settingsDAO.get("lostRefundRightAmountInclVat");
		Settings lostRefundRightForBundleIdsSetting = settingsDAO.get("lostRefundRightForBundleIds");
		BigDecimal lostRefundRightAmountInclVat = null;
		String lostRefundRightForBundleIds = null;*/
		targetedBundleMap = TargetedBundles.getUnevenlyTargetedBundles();
		iter = targetedBundleMap.keySet().iterator();
		while (iter.hasNext()) {
			String id = iter.next();
			TargetedBundles targetedBundles = targetedBundleMap.get(id);
			String email = TargetedOrders.id2Email(id);
			
			if (printInfo && targetedBundles.getTargetedPaidInMarketCurrency().compareTo(BigDecimal.ZERO) > 0) {
				logger.info("Total number of bundles for " + id + ": " + targetedBundles.getBundles().size() + ". Expected payment: "
						+ targetedBundles.getExpectedPaymentInMarketCurrency()
						+ " and targeted payment: " + targetedBundles.getTargetedPaidInMarketCurrency());
			}
			BigDecimal diff = targetedBundles.getTargetedPaidInMarketCurrency().subtract(targetedBundles.getExpectedPaymentInMarketCurrency());
			if (diff.compareTo(BigDecimal.ZERO) == 0) {
				nbrFixed += fixCorrectTotalSumPaymentsForBundles(targetedBundles.getBundles(),email,printInfo);
			} else if (diff.compareTo(BigDecimal.ZERO) > 0) { //too big payment
				/* If there is more than one bundle, then set the targeted sums equal to the expected sums and
				 * then for the first bundle in the list add diff to the targeted sum. */
				if (targetedBundles.getBundles().size() > 1) {
					nbrFixed += fixCorrectTotalSumPaymentsForBundles(targetedBundles.getBundles(),email,printInfo);
					OrderBundle ob = targetedBundles.getBundles().get(0);
					ob.setTargetedPaidShippingCostInMarketCurrency(ob.getTargetedPaidShippingCostInMarketCurrency().add(diff));
				} else {
					/* This case cannot be fixed - there are no other bundles to which the excess money can be moved.
					 * If the customer hasn't had any activity for a long time, then the customer looses his right
					 * to get a refund for excessive payment. Then we can set targetedPaid = expectedPayment.
					 * Note: in this case too much money might have been paid to the company account (when the order
					 * was canceled the sales and vat decreased but that would have been corrected when we pay back
					 * the money to the customer). Now if the customer never fetches his money, then the sales is zero
					 * as far as the taxes are concerned, but the money still has been paid to the company account.
					 * This is taken care of here but there are some problems with this correction code, so it's
					 * commented out. See "fixa automaattinen täsmäytys för liian isot maksut" in Test.java and
					 * MoneyTransferHelperNew.java for more information. */
					/*if (lostRefundRightAmountInclVatSetting == null) {
						if (targetedBundles.getBundles().get(0).getCustomer().getInformedAboutTooBigPayment()) {
							Calendar halfAYearAgo = Calendar.getInstance();
							halfAYearAgo.add(Calendar.MONTH,-6);
							Customer customer = targetedBundles.getBundles().get(0).getCustomer();
							if (orderDAO.getDateOfLastWonBid(customer,
									targetedBundles.getSeller()).getTime() < halfAYearAgo.getTime().getTime()) {
								if (lostRefundRightAmountInclVat == null) {
									lostRefundRightAmountInclVat = BigDecimal.ZERO;
								}
								OrderBundle ob = targetedBundles.getBundles().get(0);
								logger.info("(Fix #5): Customer " + customer.getId() + " has an excessive payment of " + diff.toPlainString()
										+ " but his last order was placed more than half a year ago, so now he looses his right"
										+ " to a refund (setting targetedPaid = expected for bundle " + ob.getId() + ").");
								if (!ob.getUsedCustomerCurrency().getId().equals(Currency.EURO)) {
									throw new RuntimeException("faultyPaymentFixer cannot yet handle cases where the currency of \"lost refund right\" is not euro (bundle " + ob.getId() + ").");
								}
								ob.setTargetedPaidShippingCostInMarketCurrency(ob.getTargetedPaidShippingCostInMarketCurrency().subtract(diff));
								if (ob.getTargetedPaidShippingCostInMarketCurrency().compareTo(ob.getCustomerShippingCostInMarketCurrencyInclVat()) != 0) {
									throw new RuntimeException("Internal error in fixFaultyPayments.");
								}
								lostRefundRightAmountInclVat = lostRefundRightAmountInclVat.add(diff);
								if (lostRefundRightForBundleIds == null) {
									lostRefundRightForBundleIds = "" + ob.getId();
								} else {
									lostRefundRightForBundleIds = lostRefundRightForBundleIds + "," + ob.getId();
								}
							} else {
								if (printInfo) {
									logger.debug("Customer " + customer.getId() + " has an excessive payment of " + diff.toPlainString()
											+ " but he doesn't yet loose his right to a refund.");
								}
							}
						}
					} else {
						logger.debug("There could possibly be such pending refunds that are so old that the customer looses his right to a refund.");
					}*/
				}
				//For too big payments only remind the customer ONCE (unless the customer places more orders).
				if (targetedBundles.getBundles().get(0).getCustomer().getInformedAboutTooBigPayment() == false) {
					EmailWrapper e = createEmailForRefundBecauseOfTooBigPayment(targetedBundles,email,printInfo);
					if (e != null) {
						emailList.add(e);
					}
					targetedBundles.getBundles().get(0).getCustomer().setInformedAboutTooBigPayment(true);
				}
			} else { //too small payment
				tooSmallPaymentsIds.put(id,"");
			}
		}
		
		/* Store to the database such pending customer refundings for which the customer has
		 * lost his right to a refund. */
		/*if (lostRefundRightAmountInclVatSetting == null && lostRefundRightAmountInclVat != null) {
			if (lostRefundRightAmountInclVat.compareTo(BigDecimal.ZERO) <= 0 || lostRefundRightForBundleIds == null) {
				throw new RuntimeException("Internal error in fixFaultyPayments.");
			}
			lostRefundRightAmountInclVatSetting = new Settings("lostRefundRightAmountInclVat", lostRefundRightAmountInclVat.toPlainString());
			lostRefundRightForBundleIdsSetting = new Settings("lostRefundRightForBundleIds", lostRefundRightForBundleIds);
			logger.info("Altogether lost refund rights: " + lostRefundRightAmountInclVat);
			settingsDAO.save(lostRefundRightAmountInclVatSetting);
			settingsDAO.save(lostRefundRightForBundleIdsSetting);
		}*/

		/* Set all orders in state "new" or "paid too little error" to state "customer paid" if they
		 * have targetedPaid == expected AND the shipping cost of the bundle is paid. Orders having
		 * targetedPaid > 0 but still targetedPaid < expected should be set to the state "paid too little". */
		List<Order> orderList = new ArrayList<Order>();
		orderList.addAll(orderDAO.getOrdersByState(Order.STATE_NEW));
		orderList.addAll(orderDAO.getOrdersByState(Order.STATE_PAID_TOO_LITTLE_ERROR));
		//orderList.addAll(orderDAO.getOrdersByState(Order.STATE_CLOSED_CUSTOMER_NEVER_PAID));
		for (Order order : orderList) {
			if (order.getOrderBundle().getTargetedPaidShippingCostInMarketCurrency().
					compareTo(order.getOrderBundle().getCustomerShippingCostInMarketCurrencyInclVat()) >= 0) {
				/* Shipping is completely paid for. */
				if (order.getTargetedPaidInMarketCurrency().compareTo(order.getFinalSalesPriceInMarketCurrencyInclVat()) >= 0) {
					logger.info("(Fix #6): Set order " + order.getId() + " to state \"customer paid\".");
					order.setStateWithHistory(Order.STATE_CUSTOMER_PAID);
				}
			} else if (/*order.getTargetedPaidInMarketCurrency().compareTo(order.getFinalSalesPriceInMarketCurrencyInclVat()) < 0
					&& */order.getTargetedPaidInMarketCurrency().compareTo(BigDecimal.ZERO) > 0 && order.getState().equals(Order.STATE_NEW)) {
				/* If shipping is not completely paid for and some was paid for the order (no matter
				 * if enough or too little was paid) and the order is in state "new", then set the
				 * state to "paid too little". */
				logger.info("(Fix #7): Set order " + order.getId() + " to state \"paid too little\".");
				order.setStateWithHistory(Order.STATE_PAID_TOO_LITTLE_ERROR);
			}
		}

		/* Send reminders about too small payments. */
		targetedOrderMap = TargetedOrders.getUnevenlyTargetedOrders();
		targetedBundleMap = TargetedBundles.getUnevenlyTargetedBundles();
		iter = tooSmallPaymentsIds.keySet().iterator();
		while (iter.hasNext()) {
			/* Don't remind the customer too often. */
			boolean sendReminder = false;
			String id = iter.next();
			String email = TargetedOrders.id2Email(id);
			Customer customer = customerDAO.getCustomerByEmailAddress(email);
			Calendar weekAgo = Calendar.getInstance();
			weekAgo.add(Calendar.DATE,-7);
			Calendar twoMonthsAgo = Calendar.getInstance();
			twoMonthsAgo.add(Calendar.MONTH,-2);
			if (customer.getNbrTimesKicked() < MAX_TOO_SMALL_PAYMENT_REMINDERS) {
				Seller seller = sellerDAO.get(TargetedOrders.id2SellerId(id));
				if (orderDAO.getDateOfLastWonBid(customer,seller).getTime() < twoMonthsAgo.getTime().getTime()) {
					if (printInfo) {
						logger.debug("Giving up reminding customer " + email + " regarding missing payments because last"
								+ " won bid was over two months ago. ");
					}
				} else if (customer.getDateKicked().getTime() < weekAgo.getTime().getTime()) {
					if (printInfo) {
						logger.info("Sending a reminder to customer " + email + " regarding missing payments.");
					}
					sendReminder = true;
				} else {
					if (printInfo) {
						logger.debug("Customer " + email + " has missing payments but don't yet remind (last reminder was on "
								+ customer.getDateKicked() + ").");
					}
				}
			} else if (printInfo) {
				logger.debug("Giving up reminding customer " + email + " regarding missing payments because reminder"
						+ " limit was reached (last reminder was on " + customer.getDateKicked() + ").");
			}
			
			/* Don't send any reminder if some order is in state "faulty contact info", because for that
			 * order he hasn't even gotten any bill. Housekeeping will remind the customer about correcting
			 * the contact information. */
			if (sendReminder) {
				TargetedOrders targetedOrders = targetedOrderMap.get(id);
				if (targetedOrders != null) {
					for (Order order : targetedOrders.getOrders()) {
						if (order.getState().equals(Order.STATE_FAULTY_CONTACT_INFO)) {
							sendReminder = false;
							if (printInfo) {
								logger.info("Skip sending reminder to customer " + email + " regarding missing payments"
										+ " because some order is in state \"faulty contact info\".");
							}
							break;
						}
					}
				}
			}
			
			/* Finally send reminder. */
			if (sendReminder) {
				TargetedOrders targetedOrders = targetedOrderMap.get(id);
				TargetedBundles targetedBundles = targetedBundleMap.get(id);
				EmailWrapper reminder = createEmailForPaymentReminderBecauseOfTooSmallPayment(targetedOrders,targetedBundles);
				if (reminder != null) {
					emailList.add(reminder);
					customer.setDateKicked(new Date());
					customer.setNbrTimesKicked(customer.getNbrTimesKicked() + 1);
				}
			}
		}
		
		for (EmailWrapper e : emailList) {
			EmailSender.sendEmailBatch(e.getSystemEmail(),e.getAddress(),e.getSubject(),e.getText());
		}

		return nbrFixed;
	}
	
	/**
	 * This method attempts to fix transactions that couldn't be mapped to any order.
	 * An email will be sent to the operator if there are transactions that couldn't
	 * automatically be mapped to any order.
	 * 
	 * @param sendEmail If true, then actually send emails.
	 * @return Number of faulty transactions in the database after the attempted fixes.
	 */
	public int fixFaultyTransactions(boolean sendEmail) throws Exception {
		/* Inform the operator in case there are normal transactions that couldn't be
		 * mapped to any order. (A normal transaction is a transaction that has
		 * transaction.oper_descr = null) */
		List<Transaction> trans = transactionDAO.getUnmappedNormalTransactions();
		if (trans != null && trans.size() > 0) {
			logger.info("There are " + trans.size() + " transaction(s) in the database that aren't mapped to any order.");
			int newStatePoints = 10;
			String newStateExpl = "";
			int faultyStatePoints = 3;
			String faultyStateExpl = " but faulty state";
			for (Transaction tr : trans) {
				/* For unmapped transactions try to figure out possible likely orders to which the
				 * transaction might belong, and give different scores to the different alternatives
				 * depending on how many checks they matched. */
				Map<Long,Integer> scores = new HashMap<Long,Integer>(); //orderId, score
				Map<Long,String> explanations = new HashMap<Long,String>(); //orderId, explanation
				Map<Long,ArrayList<Long>> multiOrderBundles = new HashMap<Long,ArrayList<Long>>(); //bundleId,ArrayList<orderId>

				/* First see if the reference number matches the reference number of some
				 * order for which we are expecting a payment. These cases should normally
				 * be taken care of by BankHelper, but not if for example the order was in
				 * a faulty state (although the reference number is correct). */
				Order matchingRefNbr = orderDAO.getByReferenceNumberOnly(tr.getReferenceNumber(),tr.getCurrency());
				if (matchingRefNbr != null) {
					if (matchingRefNbr.getOrderBundle().getUsedCustomerCurrency().equals(tr.getCurrency())) {
						int currScore = 0;
						if (scores.get(matchingRefNbr.getId()) != null) {
							currScore = scores.get(matchingRefNbr.getId());
						}
						currScore += (matchingRefNbr.getState().equals(Order.STATE_NEW) ? newStatePoints : faultyStatePoints);
						scores.put(matchingRefNbr.getId(),currScore);
						String currExplanation = "";
						if (explanations.get(matchingRefNbr.getId()) != null) {
							currExplanation = explanations.get(matchingRefNbr.getId()) + ",";
						}
						currExplanation += "matching ref nbr" + (matchingRefNbr.getState().equals(Order.STATE_NEW) ? newStateExpl : faultyStateExpl);
						explanations.put(matchingRefNbr.getId(),currExplanation);
					} else {
						throw new Exception("Is order " + matchingRefNbr.getId() + " paid with the wrong currency?");
					}
				}

				/* Then see if the paid sum matches the remaining sum of some partly paid order. */
				List<OrderBundle> correctSum = orderBundleDAO.getPartlyPaidBundlesByRemainingFinalSalesPricePlusShippingCost(tr.getAmount(),tr.getCurrency());
				for (OrderBundle ob : correctSum) {
					if (ob.getUsedCustomerCurrency().equals(tr.getCurrency())) {
						List<Order> orderList = orderDAO.getOrdersInBundle(ob);
						ArrayList<Long> bundleOrderIds = new ArrayList<Long>();
						/* Check if this bundle has correct (expected) states for the orders. */
						boolean someIsPaidTooLittle = false;
						boolean someIsOtherThanNewOrPaidTooLittle = false;
						for (Order o : orderList) {
							if (o.getState().equals(Order.STATE_PAID_TOO_LITTLE_ERROR)) {
								someIsPaidTooLittle = true;
							} else if (!o.getState().equals(Order.STATE_NEW)) {
								someIsOtherThanNewOrPaidTooLittle = true;
							}
						}
						boolean inExpectedState = false;
						if (!someIsOtherThanNewOrPaidTooLittle && someIsPaidTooLittle) {
							inExpectedState = true;
						}
						for (Order o : orderList) {
							int currScore = 0;
							if (scores.get(o.getId()) != null) {
								currScore = scores.get(o.getId());
							}
							currScore += (inExpectedState ? newStatePoints : faultyStatePoints);
							scores.put(o.getId(),currScore);
							String currExplanation = "";
							if (explanations.get(o.getId()) != null) {
								currExplanation = explanations.get(o.getId()) + ",";
							}
							if (orderList.size() > 1) {
								currExplanation += "correct remaining sum for partly paid multi-order bundle" + (inExpectedState ? newStateExpl : faultyStateExpl);
								bundleOrderIds.add(o.getId());
							} else {
								currExplanation += "correct remaining sum for partly paid single-order bundle" + (inExpectedState ? newStateExpl : faultyStateExpl);
							}
							explanations.put(o.getId(),currExplanation);
						}
						if (bundleOrderIds.size() > 0) {
							multiOrderBundles.put(ob.getId(),bundleOrderIds);
						}
					} else {
						logger.debug("Different currency match for transaction " + tr.getId() + ": bundle " + ob.getId());
					}
				}
				
				/* Then see if the paid sum matches the sum of some bundle (sum of individual
				 * orders + bundle shipping cost). Note: This one matches DIFFERENT bundles
				 * than the previous search. */
				correctSum = orderBundleDAO.getBundlesByFinalSalesPricePlusShippingCost(tr.getAmount(),tr.getCurrency());
				for (OrderBundle ob : correctSum) {
					if (ob.getUsedCustomerCurrency().equals(tr.getCurrency())) {
						List<Order> orderList = orderDAO.getOrdersInBundle(ob);
						ArrayList<Long> bundleOrderIds = new ArrayList<Long>();
						for (Order o : orderList) {
							int currScore = 0;
							if (scores.get(o.getId()) != null) {
								currScore = scores.get(o.getId());
							}
							currScore += (o.getState().equals(Order.STATE_NEW) ? newStatePoints : faultyStatePoints);
							scores.put(o.getId(),currScore);
							String currExplanation = "";
							if (explanations.get(o.getId()) != null) {
								currExplanation = explanations.get(o.getId()) + ",";
							}
							if (orderList.size() > 1) {
								currExplanation += "correct sum for multi-order bundle" + (o.getState().equals(Order.STATE_NEW) ? newStateExpl : faultyStateExpl);
								bundleOrderIds.add(o.getId());
							} else {
								currExplanation += "correct sum for single-order bundle" + (o.getState().equals(Order.STATE_NEW) ? newStateExpl : faultyStateExpl);
							}
							explanations.put(o.getId(),currExplanation);
						}
						if (bundleOrderIds.size() > 0) {
							multiOrderBundles.put(ob.getId(),bundleOrderIds);
						}
					} else {
						logger.debug("Different currency match for transaction " + tr.getId() + ": bundle " + ob.getId());
					}
				}
				
				/* Then see if the name of the payer matches the name of some person in our customer database.
				 * Note: here we don't differentiate between different sellers because here it's okay to take
				 * advantage of if some one has paid earlier no matter to which seller it was.
				 * TODO: would it be possible to add a scandic-safe comparison (which doesn't give as
				 * many points as an absolute check). For example if the payer according to the back is
				 * "ÄÄKKÖSSUKUNIMI Name1" and there is a customer with the name "Name2 aakkossukunimi", then some points
				 * whould be given for this as well.
				 * TODO: check the first- and last names also from the field tr.message. */
				String[] payer = tr.getPayer().split(" ");
				Map<String,String> usedPayerStrings = new HashMap<String,String>();
				for (String p : payer) {
					/* For "SUKUNIMI NIMI1 TAI SUKUNIMI NIMI2" we should only check "SUKUNIMI" once. */
					if (usedPayerStrings.get(p) != null) {
						continue;
					} else {
						usedPayerStrings.put(p,"");
					}
					List<Order> matchingPayer = orderDAO.getOrdersByCustomerStringFieldIgnoreCase("firstName", p);
					for (Order o : matchingPayer) {
						if (o.getOrderBundle().getUsedCustomerCurrency().equals(tr.getCurrency())) {
							int currScore = 0;
							if (scores.get(o.getId()) != null) {
								currScore = scores.get(o.getId());
							}
							currScore += (o.getState().equals(Order.STATE_NEW) ? newStatePoints : faultyStatePoints);
							scores.put(o.getId(),currScore);
							String currExplanation = "";
							if (explanations.get(o.getId()) != null) {
								currExplanation = explanations.get(o.getId()) + ",";
							}
							currExplanation += "first name matched" + (o.getState().equals(Order.STATE_NEW) ? newStateExpl : faultyStateExpl);
							explanations.put(o.getId(),currExplanation);
						} else {
							logger.debug("Different currency match for transaction " + tr.getId() + ": order " + o.getId());
						}
					}
					matchingPayer = orderDAO.getOrdersByCustomerStringFieldIgnoreCase("lastName", p);
					for (Order o : matchingPayer) {
						if (o.getOrderBundle().getUsedCustomerCurrency().equals(tr.getCurrency())) {
							int currScore = 0;
							if (scores.get(o.getId()) != null) {
								currScore = scores.get(o.getId());
							}
							currScore += (o.getState().equals(Order.STATE_NEW) ? newStatePoints : faultyStatePoints);
							scores.put(o.getId(),currScore);
							String currExplanation = "";
							if (explanations.get(o.getId()) != null) {
								currExplanation = explanations.get(o.getId()) + ",";
							}
							currExplanation += "last name matched" + (o.getState().equals(Order.STATE_NEW) ? newStateExpl : faultyStateExpl);
							explanations.put(o.getId(),currExplanation);
						} else {
							logger.debug("Different currency match for transaction " + tr.getId() + ": order " + o.getId());
						}
					}
				}

				/* Then see if the payer has written the order number in the description field which
				 * matches the order number of some order for which we are expecting money. Don't
				 * use this for numbers that are too low since also some products have names containing
				 * (low) numbers. For example we want "4078 - DUAL SHOCK 3 LANGATON PELIOH" to match
				 * order 4078 but not order 3. */
				String[] tokens = tr.getMessage().split(" ");
				for (String token : tokens) {
					try {
						Long orderId = Long.parseLong(token);
						Order o = orderDAO.get(orderId);
						if (o != null && o.getId().intValue() >= 4000) {
							if (o.getOrderBundle().getUsedCustomerCurrency().equals(tr.getCurrency())) {
								int currScore = 0;
								if (scores.get(o.getId()) != null) {
									currScore = scores.get(o.getId());
								}
								currScore += (o.getState().equals(Order.STATE_NEW) ? newStatePoints : faultyStatePoints);
								scores.put(o.getId(),currScore);
								String currExplanation = "";
								if (explanations.get(o.getId()) != null) {
									currExplanation = explanations.get(o.getId()) + ",";
								}
								currExplanation += "message matched orderId" + (o.getState().equals(Order.STATE_NEW) ? newStateExpl : faultyStateExpl);
								explanations.put(o.getId(),currExplanation);
							} else {
								logger.debug("Different currency match for transaction " + tr.getId() + ": order " + o.getId());
							}
						}
					} catch (NumberFormatException nfe) {
						//do nothing, this token wasn't a number
					}
				}

				/* Then see if the payer has written the reference number in the message field which
				 * matches the reference number of some order for which we are expecting money. */
				String referenceNumber = tr.getMessage().replaceAll("[^0-9]","");
				if (tr.getMessage().indexOf(referenceNumber+"") >= 0) { //make sure that "I bought 1 clock with reference number 5" isn't interpreted as "15"
					Order o = orderDAO.getByReferenceNumberOnly(referenceNumber,tr.getCurrency());
					if (o != null) {
						if (o.getOrderBundle().getUsedCustomerCurrency().equals(tr.getCurrency())) {
							int currScore = 0;
							if (scores.get(o.getId()) != null) {
								currScore = scores.get(o.getId());
							}
							currScore += (o.getState().equals(Order.STATE_NEW) ? newStatePoints : faultyStatePoints);
							scores.put(o.getId(),currScore);
							String currExplanation = "";
							if (explanations.get(o.getId()) != null) {
								currExplanation = explanations.get(o.getId()) + ",";
							}
							currExplanation += "message matched referenceNumber" + (o.getState().equals(Order.STATE_NEW) ? newStateExpl : faultyStateExpl);
							explanations.put(o.getId(),currExplanation);
						} else {
							logger.debug("Different currency match for transaction " + tr.getId() + ": order " + o.getId());
						}
					}
				}

				/* Then see if the payer has written the market sales id in the message field which
				 * matches the market sales id of some order for which we are expecting money. */
				String marketSalesId = tr.getMessage().replaceAll("[^0-9]","");
				if (tr.getReferenceNumber().indexOf(marketSalesId+"") >= 0) { //make sure that "I bought 1 clock with market sales id 5" isn't interpreted as "15"
					List<Order> oCandidates = orderDAO.getByMarketSalesId(marketSalesId,null,null);
					if (oCandidates.size() > 0) {
						for (Order o : oCandidates) {
							if (o.getOrderBundle().getUsedCustomerCurrency().equals(tr.getCurrency())) {
								int currScore = 0;
								if (scores.get(o.getId()) != null) {
									currScore = scores.get(o.getId());
								}
								currScore += (o.getState().equals(Order.STATE_NEW) ? newStatePoints : faultyStatePoints);
								scores.put(o.getId(),currScore);
								String currExplanation = "";
								if (explanations.get(o.getId()) != null) {
									currExplanation = explanations.get(o.getId()) + ",";
								}
								currExplanation += "message matched marketSalesId" + (o.getState().equals(Order.STATE_NEW) ? newStateExpl : faultyStateExpl);
								explanations.put(o.getId(),currExplanation);
							} else {
								logger.debug("Different currency match for transaction " + tr.getId() + ": order " + o.getId());
							}
						}
					}
				}

				/* Then check if the same payer has previously made a payment which was successfully bound to
				 * some order. Note: here we don't differentiate between different sellers because here it's okay
				 * to take advantage of if some one has paid earlier no matter to which seller it was. */
				List<Transaction> transactionsWithSamePayer = transactionDAO.getTransactionsByPayer(tr.getPayer());
				Map<String,Integer> tried = new HashMap<String,Integer>(); //email, 1
				for (Transaction samePayerTr : transactionsWithSamePayer) {
					Order o = samePayerTr.getOrder();
					if (o != null && tried.get(o.getOrderBundle().getCustomer().getEmail()) == null) {
						tried.put(o.getOrderBundle().getCustomer().getEmail(),1);
						/* Ok, a payer with this name has previously paid for some order. See if the same payer has
						 * some order with state = new. */
						List<Order> matchingPayer = orderDAO.getOrdersByCustomerEmailAddress(o.getOrderBundle().getCustomer().getEmail());
						for (Order o2 : matchingPayer) {
							if (o2.getOrderBundle().getUsedCustomerCurrency().equals(tr.getCurrency())) {
								int currScore = 0;
								if (scores.get(o2.getId()) != null) {
									currScore = scores.get(o2.getId());
								}
								currScore += (o2.getState().equals(Order.STATE_NEW) ? newStatePoints : faultyStatePoints);
								scores.put(o2.getId(),currScore);
								String currExplanation = "";
								if (explanations.get(o2.getId()) != null) {
									currExplanation = explanations.get(o2.getId()) + ",";
								}
								currExplanation += "transaction.payer matched a previous transaction.payer"
									+ (o2.getState().equals(Order.STATE_NEW) ? newStateExpl : faultyStateExpl);
								explanations.put(o2.getId(),currExplanation);
							} else {
								logger.debug("Different currency match for transaction " + tr.getId() + ": order " + o2.getId());
							}
						}
					}
				}
				
				/* Then look for a message matching the adtemplate headline (this only works for
				 * orders having order.ad != null). */
				if (!tr.getMessage().trim().equals("")) {
					String[] descr = tr.getMessage().split(" ");
					/* Try in two ways to find a match. For example if the customer wrote the following
					 * in the message field: "tuote Suojaava koristetarrat Xbox 360 konsolille", then we look for
					 * the following matches:
					 * 1.) tuote Suojaava koristetarrat Xbox
					 * 2.) Suojaava koristetarrat Xbox
					 * 3.) koristetarrat Xbox
					 * 4.) Xbox */
					boolean matchFound = false;
					int index = 0;
					for (String d : descr) {
						int tmpIndex = tr.getMessage().substring(index).indexOf(d);
						String possibleHeadline = tr.getMessage().substring(index + tmpIndex);
						index = index+tmpIndex;
						/* Don't use this one if the possibleHeadline is too short. For example we
						 * don't want "HUUTO 187244109 Langaton optinen hi" to match
						 * "Himmeä LCD Näytönsuojakalvo Apple iPhone 4G:lle (Läpinäkyvä)" just
						 * because "hi" matches "Himmeä". */
						if (possibleHeadline.length() > 4) {
							String searchPrefix = "";
							if (possibleHeadline.length() > 15) {
								/* If the possible headline is long enough, then it's not required
								 * that the product name _starts_ with possibleHeadline. It's enough
								 * that it's somewhere in the product name. For example
								 * "HUUTO 187244109 Langaton optinen hi" should match
								 * "8500 2.4G USB 3D Langaton optinen hiiri kannettavalle /PC:lle (Punainen)". */
								searchPrefix = "%";
							}
							List<Order> similarHeadlines = orderDAO.getBySimilarHeadline(searchPrefix + possibleHeadline + "%");
							for (Order o : similarHeadlines) {
								if (o.getOrderBundle().getUsedCustomerCurrency().equals(tr.getCurrency())) {
									int currScore = 0;
									if (scores.get(o.getId()) != null) {
										currScore = scores.get(o.getId());
									}
									currScore += (o.getState().equals(Order.STATE_NEW) ? newStatePoints : faultyStatePoints);
									scores.put(o.getId(),currScore);
									String currExplanation = "";
									if (explanations.get(o.getId()) != null) {
										currExplanation = explanations.get(o.getId()) + ",";
									}
									currExplanation += "message might match headline" + (o.getState().equals(Order.STATE_NEW) ? newStateExpl : faultyStateExpl);
									explanations.put(o.getId(),currExplanation);
									matchFound = true;
								} else {
									logger.debug("Different currency match for transaction " + tr.getId() + ": order " + o.getId());
								}
							}
						}
						if (matchFound) {
							break;
						}
					}
					/* The above attempt won't catch things like the following: "Muovi takakansi Applen logolla kiitos", so
					 * if no match was found above, then check for the two first words: "Muovi takakansi". */
					if (!matchFound && descr.length > 1) {
						String possibleHeadline = descr[0] + " " + descr[1];
						List<Order> similarHeadlines = orderDAO.getBySimilarHeadline(possibleHeadline + "%");
						for (Order o : similarHeadlines) {
							/* Note: for this one we only add the order if it is in state new because
							 * otherwise we get too many hits. */
							if (o.getState().equals(Order.STATE_NEW)) {
								if (o.getOrderBundle().getUsedCustomerCurrency().equals(tr.getCurrency())) {
									int currScore = 0;
									if (scores.get(o.getId()) != null) {
										currScore = scores.get(o.getId());
									}
									currScore += (o.getState().equals(Order.STATE_NEW) ? newStatePoints : faultyStatePoints);
									scores.put(o.getId(),currScore);
									String currExplanation = "";
									if (explanations.get(o.getId()) != null) {
										currExplanation = explanations.get(o.getId()) + ",";
									}
									currExplanation += "message might match headline" + (o.getState().equals(Order.STATE_NEW) ? newStateExpl : faultyStateExpl);
									explanations.put(o.getId(),currExplanation);
									matchFound = true;
								} else {
									logger.debug("Different currency match for transaction " + tr.getId() + ": order " + o.getId());
								}
							}
							if (matchFound) {
								break;
							}
						}
					}
				}

				/* Then check if this seems to be a (valid) special transaction (use orderId = 0 for those,
				 * because there won't be any order in the database having id = 0). */
				String specialActionOperatorDescription = "";
				if (tr.getAmount().compareTo(BigDecimal.ZERO) < 0) {
					if (/*(tr.getPayer().toUpperCase().contains("TOMI") && tr.getPayer().toUpperCase().contains("TOMI"))
							|| */tr.getMessage().toUpperCase().contains("PAYPAL") || tr.getPayer().toUpperCase().contains("AMERICAN EXPRESS")) {
						scores.put(0L,20);
						explanations.put(0L,"seems to be a (negative) paypal payment");
						specialActionOperatorDescription = "Payment to PayPal";
					} else if ((tr.getPayer().equals("HUUTO.NET") || tr.getPayer().equals("SANOMA NEWS OY"))
							&& tr.getReferenceNumber().length() > 17) {
						scores.put(0L,20);
						explanations.put(0L,"seems to be a (negative) provision payment to huutonet");
						specialActionOperatorDescription = "Provision to huutonet";
						specialActionOperatorDescription = "Payment to PayPal";
					} else if (tr.getPayer().equals("QD-TECH OY")
							&& tr.getMessage().equalsIgnoreCase(TransactionDAOImpl.QD_TRANSFER_IDENTIFICATION)) {
						scores.put(0L,40);
						explanations.put(0L,"seems to be a money transfer from SellStar to QD");
						specialActionOperatorDescription = TransactionDAOImpl.QD_TRANSFER_IDENTIFICATION;
					} else {
						scores.put(0L,10);
						explanations.put(0L,"faulty payment? (negative unknown transaction)");
						specialActionOperatorDescription = "Faulty payment";
					}
				}

				/* Now and then the customer gets one number wrong for the reference number but the reference number
				 * is still valid and thus goes through with the bank. This happened for example for "Unmapped transaction 7914".
				 * Check for similar reference numbers. */
				String firstPart = null;
				String middlePart = null;
				String lastPart = null;
				if (tr.getReferenceNumber().length() > 12) {
					firstPart = tr.getReferenceNumber().substring(0, 7);
					middlePart = tr.getReferenceNumber().substring(4, 11);
					lastPart = tr.getReferenceNumber().substring(7);
				}
				List<Order> candidateOrders = new ArrayList<Order>();
				Map<Long,Integer> addedOrders = new HashMap<Long,Integer>();
				if (firstPart != null && middlePart != null && lastPart != null) {
					List<Order> similarRefNumbers = orderDAO.getBySimilarReferenceNumberOnly(firstPart + "%",tr.getCurrency());
					candidateOrders.addAll(similarRefNumbers);
					for (Order o : similarRefNumbers) {
						addedOrders.put(o.getId(),new Integer(1));
					}
					similarRefNumbers = orderDAO.getBySimilarReferenceNumberOnly("%" + middlePart + "%",tr.getCurrency());
					for (Order o : similarRefNumbers) {
						if (addedOrders.get(o.getId()) == null) {
							candidateOrders.add(o);
							addedOrders.put(o.getId(),new Integer(0));
						}
						addedOrders.put(o.getId(),addedOrders.get(o.getId()).intValue()+1);
					}
					similarRefNumbers = orderDAO.getBySimilarReferenceNumberOnly("%" + lastPart,tr.getCurrency());
					for (Order o : similarRefNumbers) {
						if (addedOrders.get(o.getId()) == null) {
							candidateOrders.add(o);
							addedOrders.put(o.getId(),new Integer(0));
						}
						addedOrders.put(o.getId(),addedOrders.get(o.getId()).intValue()+1);
					}
					/* Make sure that testing for reference number similarity is only done if there
					 * are no exact matches (because those have already been taken care of earlier
					 * in this method). */
					boolean exactMatchFound = false;
					for (Order o : candidateOrders) {
						if (o.getCustomerReferenceNumber().equals(tr.getReferenceNumber())) {
							exactMatchFound = true;
						}
					}
					if (!exactMatchFound) {
						for (Order o : candidateOrders) {
							int dist = StringUtils.getLevenshteinDistance(o.getCustomerReferenceNumber(), tr.getReferenceNumber());
							int distScore = newStatePoints - (dist - 1);
							if (distScore > 2) { //if sufficiently close
								if (o.getOrderBundle().getUsedCustomerCurrency().equals(tr.getCurrency())) {
									int currScore = 0;
									if (scores.get(o.getId()) != null) {
										currScore = scores.get(o.getId());
									}
									currScore += (o.getState().equals(Order.STATE_NEW) ? distScore : (distScore / 3));
									scores.put(o.getId(),currScore);
									String currExplanation = "";
									if (explanations.get(o.getId()) != null) {
										currExplanation = explanations.get(o.getId()) + ",";
									}
									currExplanation += "similar reference number" + (o.getState().equals(Order.STATE_NEW) ? newStateExpl : faultyStateExpl);
									explanations.put(o.getId(),currExplanation);
								} else {
									throw new Exception("Is order " + o.getId() + " paid with the wrong currency?");
								}								
							}
						}
					}
				}
				
				/* Payments are always associated with single orders and not with bundles. However it's possible that
				 * the customer paid for a multi-order bundle using a single payment. All orders of the bundle might
				 * not have received the same score (for example if the message field contained the name of _one_ of
				 * the orders in the bundle. For multi-order bundles let's here only keep the one order that got the
				 * highest score. (If we would keep all of the orders in the bundle, then these ones will probably not
				 * get automatically fixed by the logic below, because the score of individual orders in the bundle are
				 * most likely approximately the same, and automatic fixing requires the score of one order to be much
				 * better than other possible solutions.) */
				Map<Long,Integer> removedScores = new HashMap<Long,Integer>(); //orderId, score
				Map<Long,String> removedExplanations = new HashMap<Long,String>(); //orderId, explanation
				if (multiOrderBundles.size() > 0) {
					Iterator<Long> iter = multiOrderBundles.keySet().iterator();
					while (iter.hasNext()) {
						Long bundleId = iter.next();
						ArrayList<Long> orderIds = multiOrderBundles.get(bundleId);
						int highestScore = 0;
						long idToKeep = -1;
						for (Long orderId : orderIds) {
							if (scores.get(orderId) > highestScore) {
								highestScore = scores.get(orderId);
								idToKeep = orderId;
							}
						}
						for (Long orderId : orderIds) {
							if (idToKeep != orderId.longValue()) {
								logger.debug("Removed order " + orderId + " from multi-order bundle " + bundleId + ".");
								removedScores.put(orderId, scores.get(orderId));
								scores.remove(orderId);
								removedExplanations.put(orderId, explanations.get(orderId));
								explanations.remove(orderId);
							}
						}
					}
				}
				
				//StringBuffer solutions = new StringBuffer();
				StringBuffer newEmail = new StringBuffer();
				StringBuffer newEmailStart = new StringBuffer();
				StringBuffer actionDescr = new StringBuffer();
				StringBuffer orderInfo = new StringBuffer();
				newEmailStart.append("The following transaction isn't mapped to any order:\n\n"
					+ "id: " + tr.getId() + ", amount: " + tr.getAmount() + ", date: " + tr.getDate()
					+ ", description: " + tr.getDescription() + "\n"
					+ "message: " + tr.getMessage() + ", payer: " + tr.getPayer() + ", ref.nr: " + tr.getReferenceNumber()
					+ ", currency = " + tr.getCurrency().getSymbol() + "\n"
					+ "\nIt wasn't possible to automatically map this transaction to any order.\n"
					+ "Possible actions:\n");

				/* Sort the different alternatives according to their score and the list the alternatives. */
				int actionNum = 1;
				int specialActionNumber = -1;
				int globalMaxScore = -1;
				int secondBestScore = 10000;
				if (scores.size() > 0) {
					List<Integer> scoreList = new ArrayList<Integer>();
					Iterator<Long> iter = scores.keySet().iterator();
					while (iter.hasNext()) {
						Long key = iter.next();
						scoreList.add(scores.get(key));
					}
					int prevMax = 10000;
					globalMaxScore = Collections.max(scoreList);
					try {
						do {
						Integer maxScore = Collections.max(scoreList);
						/* Now list all orders having score = maxScore (there could be several having the same score). */
						if (prevMax > maxScore) { //scoreList.remove(maxScore) below removes just the first occurrence, therefore this condition
							iter = scores.keySet().iterator();
							while (iter.hasNext()) {
								Long key = iter.next();
								if (scores.get(key).equals(maxScore)) {
									//don't list such orders that just got the absolute minimum of points
									//if there exist such orders that have a higher score:
									if (maxScore > faultyStatePoints || globalMaxScore == faultyStatePoints) {
										if (key == 0) {
											newEmail.append(actionNum + ".) mark this transaction as special "
													+ "(operator description is mandatory in this case), score " + maxScore + "\n");
											newEmail.append("    "+ "(" + explanations.get(key) + ")\n");
											actionDescr.append("<ACTION_DESCR_" + actionNum + ">special</ACTION_DESCR_" + actionNum + ">\n");
											specialActionNumber = actionNum;
										} else {
											newEmail.append(actionNum + ".) associate with order " + key + ", score " + maxScore + "\n");
											newEmail.append("    "+ "(" + explanations.get(key) + ")\n");
											actionDescr.append("<ACTION_DESCR_" + actionNum + ">" + key + "</ACTION_DESCR_" + actionNum + ">\n");
											Order o = orderDAO.get(key);
											/* Here let's also list the other orders in a multi-order bundle. */
											if (multiOrderBundles.get(o.getOrderBundle().getId()) != null) {
												orderInfo.append("MULTI-ORDER BUNDLE: " + OrderInformation.getOrderInfo(o,true) + "\n");
												ArrayList<Long> orderIds = multiOrderBundles.get(o.getOrderBundle().getId());
												for (Long orderId : orderIds) {
													if (!orderId.equals(o.getId())) {
														orderInfo.append("OTHER ORDER IN BUNDLE: " + OrderInformation.getOrderInfo(orderDAO.get(orderId),true) + "\n");
													}
												}
											} else {
												orderInfo.append(OrderInformation.getOrderInfo(o,true) + "\n");
											}
										}
										actionNum++;
									}
								}
							}
						}
						scoreList.remove(maxScore); //this one only removes the first occurrence
						if (maxScore == globalMaxScore) {
							if (scoreList.isEmpty()) {
								secondBestScore = 0;
							} else {
								secondBestScore = Collections.max(scoreList);
							}
						}
						prevMax = maxScore;
						} while (true); //exit from this when Collections.max(scoreList) above generates an exception because the list is empty
					} catch (NoSuchElementException nsee) {
						//this means the scoreList is now empty
					}
				}

				/* If no special action was added above, then add it to the end of the list. */
				if (specialActionNumber == -1) {
					newEmail.append(actionNum + ".) mark this transaction as special (operator description is mandatory in this case), score "
							+ (scores.get(0L) != null ? scores.get(0L) : "0") + "\n");
					actionDescr.append("<ACTION_DESCR_" + actionNum + ">special</ACTION_DESCR_" + actionNum + ">\n");
					specialActionNumber = actionNum;
					actionNum++;
				}
				StringBuffer actionBuf = new StringBuffer();
				actionBuf.append("\n<ACTION>");
				if ((specialActionNumber == 1 && scores.get(0L) != null && scores.get(0L) > 0) || globalMaxScore >= 30) {
					/* If the first alternative was a special transaction and its score was greater than one
					 * OR if score for the first alternative was at least 30, then fill out the first alternative as a default. */
					actionBuf.append("1");
				}
				actionBuf.append("</ACTION>\n\n<OPERATOR_DESCRIPTION>");
				if (specialActionNumber == 1 && scores.get(0L) != null && scores.get(0L) > 0) {
					/* Fill out a default operator description id the special action was the first action on the list
					 * and the score for this one was greater than zero. */
					actionBuf.append(specialActionOperatorDescription);
				}
				actionBuf.append("</OPERATOR_DESCRIPTION>\n\n\n");
				newEmailStart.insert(0,actionBuf.toString());
				newEmail.append("\n\n\n\n" + orderInfo.toString() + "\n\n\n\n");
				
				/* Check if we can actually fix this transaction automatically. */
				boolean fixed = false;
				if (globalMaxScore >= 40 && globalMaxScore - 20 > secondBestScore) {
					//If the most suitable order has at least 40 points and it has more than 20 points
					//more that the second best score, then we can automatically assign the transaction with
					//this order.
					Iterator<Long> iter = scores.keySet().iterator();
					while (iter.hasNext()) {
						Long key = iter.next();
						if (scores.get(key).equals(globalMaxScore)) {
							if (key != 0) { //don't fix special transactions automatically, at least not yet
								Order o = orderDAO.get(key);
								BankHelper bankHelper = (BankHelper) ApplicationContextPlaceholder.applicationContext.getBean("bankHelper");
								bankHelper.associateTransactionWithOrder(tr,o);
								fixed = true;
								logger.info("Transaction " + tr.getId() + " was automatically mapped to order " + o.getId());
								String operEmail = "For your information, the first action was automatically taken for:\n\n"
									+ "id: " + tr.getId() + ", amount: " + tr.getAmount() + ", date: " + tr.getDate()
									+ ", description: " + tr.getDescription() + "\n"
									+ "message: " + tr.getMessage() + ", payer: " + tr.getPayer()
									+ ", ref.nr: " + tr.getReferenceNumber()
									+ ", currency: " + tr.getCurrency().getSymbol() + "\n"
									+ "\nPossible actions:\n" + newEmail.toString();
								EmailSender.sendOperationSummaryReport("Transaction automatically mapped to order",operEmail);
							}
							break;
						}
					}
				}
				
				if (!fixed) {
					newEmail.insert(0,newEmailStart.toString());
					newEmail.append(actionDescr.toString()
							+ "<TRANSACTION_ID>" + tr.getId() + "</TRANSACTION_ID>\n");
					if (sendEmail) {
						EmailSender.sendOperationProblemReport("Unmapped transaction " + tr.getId(),newEmail.toString());
					} else {
						logger.info("To     : EmailSender.sendOperationProblemReport(...)");
						logger.info("Subject: Unmapped transaction");
						logger.info("Contents:");
						logger.info(newEmail.toString());
						logger.info("-------------------------------------");
					}
				}
			}
			//return trans.size();
			return 0;
		} else {
			logger.info("No unmapped transactions exist in the database.");
			return 0;
		}
	}
	
	public void generateCommonBill(Customer customer, Seller seller) throws Exception {
		fixFaultyPayments(false); //first fix possible faulty payments

		Map<String,TargetedOrders> targetedOrderMap = TargetedOrders.getUnevenlyTargetedOrders();
		Map<String,TargetedBundles> targetedBundleMap = TargetedBundles.getUnevenlyTargetedBundles();
		
		/* Go through all currencies (the customer might have bought stuff for
		 * example using both kronor and euros). */
		for (Currency currency : currencyDAO.loadAll()) {
			/* Check if the customer has bought anything using this currency (there are several currencies in the currency
			 * table and we don't want to send one "all orders are paid correctly" for each row in the currency table, or
			 * even worse: the customer gets one "all orders are paid correctly" (because he hasn't bought anything in
			 * dollars) and one "you need to pay 10 euros". */
			if (orderBundleDAO.getBundleCountForUsedCustomerCurrency(customer,currency) > 0) {
				String id = TargetedOrders.email2Id(customer.getEmail(),seller, currency);
				TargetedOrders targetedOrders = targetedOrderMap.get(id);
				TargetedBundles targetedBundles = targetedBundleMap.get(id);
				
				if (targetedOrders == null && targetedBundles == null) {
					/* No unevenly targeted orders not bundles are found for the customer. */
					//return new EmailWrapper(seller.getSystemEmail(),customer.getEmail(),
						//	"Koontilasku","Moi,\n\nKaikki tilaukset ovat täsmällisesti maksettu.\n\nYstävällisin terveisin,\n" + seller.getSignature());
					EmailSender.sendEmailBatch(seller.getSystemEmail(),customer.getEmail(), "Koontilasku",
							"Moi,\n\nKaikki tilaukset ovat täsmällisesti maksettu.\n\nYstävällisin terveisin,\n" + seller.getSignature());
				} else {
					/* Don't send any common bill if the contact information for any order of the customer is faulty.
					 * We don't want to get into a situation where the customer pays and the contact information
					 * is faulty when the money comes in, because then we would need to do work by hand in
					 * order to correct things. => actually it might actually work so that no manual work
					 * is necessary after recent changes in the payment handling, but let's still for now
					 * keep this behavior. */
					boolean faultyContactInfo = false;
					StringBuffer strBuf = new StringBuffer();
					strBuf.append("Moi,\n\n");
					strBuf.append("Ei voi lähettää koontilaskua ennen kuin yhteystiedot ovat kunnossa:\n\n");
					strBuf.append("Sähköpostiosoite: " + customer.getEmail() + "\n\n\n\n");
					for (Order order : orderDAO.getOrdersByStateAndCustomerEmailAddress(Order.STATE_FAULTY_CONTACT_INFO, customer.getEmail(), seller)) {
						OrderBundle bundle = order.getOrderBundle();
						List<AddressFault> contactInfoProblems = ContactInfoChecker.checkContactInfo(bundle.getFirstName(),
								bundle.getLastName(),bundle.getAddress(),
								bundle.getPostcode(), bundle.getCity(),customer.getEmail(),order.getGoogleOrder());
						if (contactInfoProblems.size() > 0) {
							faultyContactInfo = true;
							String reason = "";
							for (AddressFault addressFault : contactInfoProblems) {
								reason += messageDAO.getText(addressFault.getField(), bundle.getSeller(),
										order.getMarket().getLanguage());
								reason += ": " + messageDAO.getText(addressFault.getError(), bundle.getSeller(),
										order.getMarket().getLanguage()) + "\n";
							}
							String contextPath = messageDAO.getText("contextPath", seller,order.getMarket().getLanguage());
							strBuf.append(messageDAO.getText("faultyContactInfoForOrderX", seller,order.getMarket().getLanguage(),
									"ORDER_ID",""+order.getId(),
									"REFERENCE_NUMBER",order.getCustomerReferenceNumber(),
									"CUSTOMER_FIRST_NAME",bundle.getFirstName(),
									"CUSTOMER_LAST_NAME",bundle.getLastName(),
									"CUSTOMER_ADDRESS",bundle.getAddress(),
									"CUSTOMER_POSTCODE",bundle.getPostcode(),
									"CUSTOMER_CITY",bundle.getCity(),
									"REASON",reason,
									"GOOGLE_ORDER_URL",seller.getStoreUrl(),
									"CONTEXT_PATH",contextPath,
									"SIGNATURE_URL_ENC",StringUtilities.escapeString2URL(seller.getSignature()),
									"FIRST_NAME_URL_ENC",StringUtilities.escapeString2URL(bundle.getFirstName()),
									"LAST_NAME_URL_ENC",StringUtilities.escapeString2URL(bundle.getLastName()),
									"CUSTOMER_ADDRESS_URL_ENC",StringUtilities.escapeString2URL(bundle.getAddress()),
									"CUSTOMER_POSTCODE_URL_ENC",StringUtilities.escapeString2URL(bundle.getPostcode()),
									"CUSTOMER_CITY_URL_ENC",StringUtilities.escapeString2URL(bundle.getCity()),
									"CUSTOMER_EMAIL_URL_ENC",StringUtilities.escapeString2URL(bundle.getCustomer().getEmail())));
							strBuf.append("\n\n\n\n");
						}
					}
					if (faultyContactInfo) {
						strBuf.append("Ystävällisin terveisin,\n");
						strBuf.append(seller.getSignature() + "\n");
						//return new EmailWrapper(seller.getSystemEmail(),customer.getEmail(),"Koontilaskun tuottaminen epäonnistui",strBuf.toString());
						EmailSender.sendEmailBatch(seller.getSystemEmail(),customer.getEmail(),
								"Koontilaskun tuottaminen epäonnistui",strBuf.toString());
					}
					
					if (targetedOrders != null
							&& targetedOrders.getTargetedPaidInMarketCurrency().compareTo(targetedOrders.getExpectedPaymentInMarketCurrency()) > 0) {
						throw new Exception("Internal error: targetedOrderPayment should never be positive (since excess should be moved to the shipping cost of the bundle)");
					}
					
					Market market = null;
					BigDecimal expectedOrderPayment = BigDecimal.ZERO;
					BigDecimal targetedOrderPayment = BigDecimal.ZERO;
					BigDecimal expectedShippingPayment = BigDecimal.ZERO;
					BigDecimal targetedShippingPayment = BigDecimal.ZERO;
					if (targetedOrders != null) {
						expectedOrderPayment = expectedOrderPayment.add(targetedOrders.getExpectedPaymentInMarketCurrency());
						targetedOrderPayment = targetedOrderPayment.add(targetedOrders.getTargetedPaidInMarketCurrency());
						market = targetedOrders.getOrders().get(0).getMarket();
					}
					if (targetedBundles != null) {
						expectedShippingPayment = expectedShippingPayment.add(targetedBundles.getExpectedPaymentInMarketCurrency());
						targetedShippingPayment = targetedShippingPayment.add(targetedBundles.getTargetedPaidInMarketCurrency());
						if (market == null) {
							market = orderDAO.getOrdersInBundle(targetedBundles.getBundles().get(0)).get(0).getMarket();
						}
					}
					BigDecimal totalExpectedPayment = expectedOrderPayment.add(expectedShippingPayment);
					BigDecimal totalTargetedPayment = targetedOrderPayment.add(targetedShippingPayment);
		
					if (targetedBundles != null && targetedOrders != null
							&& targetedBundles.getTargetedPaidInMarketCurrency().compareTo(targetedBundles.getExpectedPaymentInMarketCurrency()) > 0
							&& targetedOrders.getTargetedPaidInMarketCurrency().compareTo(targetedOrders.getExpectedPaymentInMarketCurrency()) != 0) {
						throw new Exception("Internal error: orders.expected != orders.targeted although bundle.targeted > bundle.expected.");
					}
					
					if (totalTargetedPayment.compareTo(totalExpectedPayment) == 0) {
						//return new EmailWrapper(seller.getSystemEmail(),customer.getEmail(),
							//	"Koontilasku","Moi,\n\nKaikki tilaukset ovat täsmällisesti maksettu.\n\nYstävällisin terveisin,\n" + seller.getSignature());
						EmailSender.sendEmailBatch(seller.getSystemEmail(),customer.getEmail(),
								"Koontilasku","Moi,\n\nKaikki tilaukset ovat täsmällisesti maksettu.\n\nYstävällisin terveisin,\n" + seller.getSignature());
					} else if (totalTargetedPayment.compareTo(totalExpectedPayment) > 0) {
						//return createEmailForRefundBecauseOfTooBigPayment(targetedBundles, customer.getEmail(),true);
						EmailWrapper e = createEmailForRefundBecauseOfTooBigPayment(targetedBundles, customer.getEmail(),true);
						if (e != null) {
							if (!e.getSystemEmail().getId().equals(seller.getSystemEmail().getId())) {
								throw new Exception("Internal error in generateCommonBill");
							}
							EmailSender.sendEmailBatch(e.getSystemEmail(),e.getAddress(),e.getSubject(),e.getText());
						}
					} else {
						/* Don't send normal payment instructions if all orders are in state "new out of stock".
						 * TODO: what if just _one_ order is out of stock and the rest are not? The simplest
						 * solution would be to then simply add a disclaimer to the payment instructions:
						 * "Note: some product is not in stock at the moment." Or else we could maybe remove that order
						 * from targetedOrders.getOrders (but then we would also need to remove the corresponding
						 * bundle from targetedBundles in case the bundle has no orders that are not in
						 * "new out of stock"). See also createEmailForPaymentReminderBecauseOfTooSmallPayment. */
						boolean allOrdersNewOutOfStock = true;
						if (targetedOrders != null) {
							for (Order order : targetedOrders.getOrders()) {
								if (!order.getState().equals(Order.STATE_NEW_OUT_OF_STOCK)) {
									allOrdersNewOutOfStock = false;
								}
							}
						} else {
							allOrdersNewOutOfStock = false;
						}
						
						strBuf = new StringBuffer();
						if (allOrdersNewOutOfStock) {
							strBuf.append("Moi,\n\n");
							strBuf.append("Tilaamasi tuote/tuotteet eivät ole varastolla. Odotetaan täydennyserää. Saat viikon sisällä");
							strBuf.append(" lisätietoja.\n\n");
							strBuf.append("Ystävällisin terveisin,\n");
							strBuf.append(seller.getSignature() + "\n");
							EmailSender.sendEmailBatch(seller.getSystemEmail(),customer.getEmail(),
									"Koontilaskun tuottaminen epäonnistui",strBuf.toString());
						} else {
							strBuf.append("Moi,\n\n");
							strBuf.append("Koontilasku - seuraavat tilaukset odottavat tällä hetkellä maksusuoritustasi.\n\n");
							strBuf.append(OrderInformation.getOrderPaymentsDescription(targetedOrders,targetedBundles));
							strBuf.append(messageDAO.getText("bundleInformation", seller, market.getLanguage()));
							strBuf.append("Ystävällisin terveisin,\n");
							strBuf.append(seller.getSignature() + "\n");
							EmailSender.sendEmailBatch(seller.getSystemEmail(),customer.getEmail(),
									"Koontilasku",strBuf.toString());
						}
					}
				}
			}
		}
	}
	
	private EmailWrapper createEmailForPaymentReminderBecauseOfTooSmallPayment(TargetedOrders targetedOrders, TargetedBundles targetedBundles) {
		if (targetedOrders == null && targetedBundles == null) {
			/* Something wrong if both are null. */
			throw new RuntimeException("Internal error in createEmailForPaymentReminderBecauseOfTooSmallPayment");
		}
		String emailAddress = null;
		Seller seller = null;
		Market market = null;
		//Customer customer = null;
		//BigDecimal expectedPayment = BigDecimal.ZERO;
		//BigDecimal targetedPayment = BigDecimal.ZERO;
		if (targetedOrders != null) {
			//expectedPayment = expectedPayment.add(targetedOrders.getExpectedPaymentInMarketCurrency());
			//targetedPayment = targetedPayment.add(targetedOrders.getTargetedPaidInMarketCurrency());
			emailAddress = targetedOrders.getOrders().get(0).getOrderBundle().getCustomer().getEmail();
			seller = targetedOrders.getSeller();
			//customer = targetedOrders.getCustomer();
			market = targetedOrders.getOrders().get(0).getMarket();
		}
		if (targetedBundles != null) {
			//expectedPayment = expectedPayment.add(targetedBundles.getExpectedPaymentInMarketCurrency());
			//targetedPayment = targetedPayment.add(targetedBundles.getTargetedPaidInMarketCurrency());
			emailAddress = targetedBundles.getBundles().get(0).getCustomer().getEmail();
			seller = targetedBundles.getSeller();
			//customer = targetedBundles.getCustomer();
			if (market == null) {
				market = orderDAO.getOrdersInBundle(targetedBundles.getBundles().get(0)).get(0).getMarket();
			}
		}
		
		/* Skip sending payment reminder if all orders are in state "new out of stock".
		 * TODO: what if just _one_ order is out of stock and the rest are not? The simplest
		 * solution would be to then simply add a disclaimer to the reminder: "Note: some
		 * product is not in stock at the moment." Or else we could maybe remove that order
		 * from targetedOrders.getOrders (but then we would also need to remove the corresponding
		 * bundle from targetedBundles in case the bundle has no orders that are not in
		 * "new out of stock"). See also generateCommonBill. */
		if (targetedOrders != null) {
			boolean allOrdersNewOutOfStock = true;
			for (Order order : targetedOrders.getOrders()) {
				if (!order.getState().equals(Order.STATE_NEW_OUT_OF_STOCK)) {
					allOrdersNewOutOfStock = false;
				}
			}
			if (allOrdersNewOutOfStock) {
				logger.debug("Skipping sending payment reminder to " + emailAddress + " because all orders are \"new out of stock\".");
				return null;
			}
		}
		
		StringBuffer strBuf = new StringBuffer();
		//TODO: Don't hardcode the email message but rather get the contents from the database.
		strBuf.append("Moi,\n\nEmme ole vielä saaneet kaikkia maksusuorituksiasi huutamistasi/ostamistasi tuotteista.\n");
		strBuf.append("Mikäli olet maksanut tuotteet aivan lähipäivinä, maksut eivät välttämättä ole vielä ehtinyt kirjautua pankkiimme (Handelsbanken).\n\n");
		strBuf.append("Seuraavat tilaukset odottavat tällä hetkellä maksusuoritustasi.\n\n");
		strBuf.append(OrderInformation.getOrderPaymentsDescription(targetedOrders,targetedBundles));
		strBuf.append("Mikäli olet maksanut kaikki yllä olevat kohteet jo yli neljä päivää sitten,\n");
		strBuf.append("on todennäköistä, että käyttämäsi viitenumero on ollut virheellinen tai se on jäänyt maksusuorituksesta pois.\n");
		strBuf.append("Tässä tapauksessa otathan meihin yhteyttä ja listaa tarkat päivämäärät ja summat, jolloin\n");
		strBuf.append("maksusuoritukset on tehty.\n\n");
		strBuf.append(messageDAO.getText("bundleInformation", seller, market.getLanguage()));
		strBuf.append("Ystävällisin terveisin,\n");
		strBuf.append(seller.getSignature() + "\n");
		return new EmailWrapper(seller.getSystemEmail(),emailAddress,"Maksumuistutus",strBuf.toString());
	}
	
	private EmailWrapper createEmailForRefundBecauseOfTooBigPayment(TargetedBundles targetedBundles, String emailAddress, boolean printInfo) {
		/* If we already have the customer's account number, then pay the money straight to the customer
		 * (in the Refunds class), or else ask him to provide an account number. */
		Customer customer = customerDAO.getCustomerByEmailAddress(emailAddress);
		if (customer.getAccountNumber() == null) {
			//Get some order of the customer (in order to figure out what language to use)
			Order order = orderDAO.getOrdersInBundle(targetedBundles.getBundles().get(0)).get(0);

			BigDecimal refundSum = targetedBundles.getTargetedPaidInMarketCurrency().subtract(targetedBundles.getExpectedPaymentInMarketCurrency());
			if (refundSum.compareTo(BigDecimal.ZERO) == 0) {
				throw new RuntimeException("Refundable sum is zero.");
			}
			VirtualCoin vcRefundSum = new VirtualCoin(targetedBundles.getBundles().get(0).getUsedCustomerCurrency(),refundSum);
			String contextPath = messageDAO.getText("contextPath", targetedBundles.getSeller(),
					order.getMarket().getLanguage());
			String custEmail = messageDAO.getText("customerPaidTooMuch", targetedBundles.getSeller(),
					order.getMarket().getLanguage(),
					"REFUND_SUM",CurrencyFormatter.print(vcRefundSum,Statics.FINNISH),
					"GOOGLE_ORDER_URL",targetedBundles.getSeller().getStoreUrl(),
					"CONTEXT_PATH",contextPath,
					"SIGNATURE_URL_ENC",StringUtilities.escapeString2URL(targetedBundles.getSeller().getSignature()),
					"CONFIRMATION_CODE",customer.getConfirmationCode(),
					"SIGNATURE",targetedBundles.getSeller().getSignature());
			return new EmailWrapper(targetedBundles.getSeller().getSystemEmail(),emailAddress,"Hyvitys",custEmail);
		} else if (printInfo) {
			logger.info("Customer " + emailAddress + " has paid too much but don't send him any email about it because"
					+ " we already have his account information.");
			//Don't pay back the money here. Rather it's taken care of by the Refunds class (in a separate transaction).
		}
		return null;
	}
	
}
