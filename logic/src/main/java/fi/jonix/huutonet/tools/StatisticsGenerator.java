package fi.jonix.huutonet.tools;

import java.math.BigDecimal;
import java.math.MathContext;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import fi.jonix.huutonet.domain.VirtualCoin;
import fi.jonix.huutonet.domain.model.Category;
import fi.jonix.huutonet.domain.model.Currency;
import fi.jonix.huutonet.domain.model.Order;
import fi.jonix.huutonet.domain.model.OrderBundle;
import fi.jonix.huutonet.domain.model.Product;
import fi.jonix.huutonet.domain.model.Provider;
import fi.jonix.huutonet.domain.model.Settings;
import fi.jonix.huutonet.domain.model.StateChange;
import fi.jonix.huutonet.domain.model.Transaction;
import fi.jonix.huutonet.domain.model.dao.AdDAO;
import fi.jonix.huutonet.domain.model.dao.AdTemplateDAO;
import fi.jonix.huutonet.domain.model.dao.CategoryDAO;
import fi.jonix.huutonet.domain.model.dao.CurrencyDAO;
import fi.jonix.huutonet.domain.model.dao.OrderDAO;
import fi.jonix.huutonet.domain.model.dao.ProductDAO;
import fi.jonix.huutonet.domain.model.dao.ProviderDAO;
import fi.jonix.huutonet.domain.model.dao.SettingsDAO;
import fi.jonix.huutonet.domain.model.dao.TransactionDAO;

@Component(value = "statisticsGenerator")
public class StatisticsGenerator {

	public static final Logger logger = Logger.getLogger(StatisticsGenerator.class);
	
	private static final int MAX_CATEGORY_ROWS_PER_SUBREPORT = 10;

	@Autowired
	private OrderDAO orderDAO;
	
	@Autowired
	private AdTemplateDAO adTemplateDAO;
	
	@Autowired
	private AdDAO adDAO;
	
	@Autowired
	private SettingsDAO settingsDAO;
	
	@Autowired
	private TransactionDAO transactionDAO;
	
	@Autowired
	private ProductDAO productDAO;
	
	@Autowired
	ProviderDAO providerDAO;
	
	@Autowired
	CategoryDAO categoryDAO;
	
	@Autowired
	CurrencyDAO currencyDAO;
	
	public StringBuffer generateProductStatistics() {
		StringBuffer strBuf = new StringBuffer();
		StringBuffer categoryStatisticsBuf = new StringBuffer();
		Calendar cal = Calendar.getInstance();
		cal.set(Calendar.DAY_OF_MONTH,1);
		cal.set(Calendar.HOUR_OF_DAY,0);
		cal.set(Calendar.MINUTE,0);
		cal.set(Calendar.SECOND,0);
		cal.set(Calendar.MILLISECOND,0);
		Date end = cal.getTime();
		cal.add(Calendar.MONTH,-1);
		Date start = cal.getTime();
		printSalesStatistics(start, end, strBuf, null, categoryStatisticsBuf, 10000);
		return categoryStatisticsBuf;
	}
	
	public StringBuffer generateSalesReport() {
		//logger.debug("In StatisticsGenerator.generateStatistics");
		StringBuffer strBuf = new StringBuffer();
		StringBuffer categoryStatisticsBuf = new StringBuffer();
		
		/* Get sales statistics. */
		strBuf.append("Sales statistics:\n");
		strBuf.append("-----------------\n");
		//TODO: Include also all other expenses! It can be done like this:
		//select sum(amount) from transaction where order_id is null and oper_descr not like 'Payment to PayPal%' and date >= 'xxx' and date < 'yyy'
		//=> this probably isn't necessary because we have the "cash flow" info! => update: there are no longer any other expenses since nowadays
		//everything is paid using the account of qd.
		strBuf.append("The sales statistics only include revenues and expenses that are directly related to selling and\n");
		strBuf.append("buying. In particular the following expenses are NOT included: market place provisions, salaries and\n");
		strBuf.append("other expenses such as virtual server hosting, etc.\n\n");
		Calendar currYear = Calendar.getInstance();
		Calendar cal = Calendar.getInstance();
		//cal.set(Calendar.YEAR, 2012);
		//cal.set(Calendar.MONTH,Calendar.JANUARY);
		cal.add(Calendar.MONTH,-1);
		cal.set(Calendar.DAY_OF_MONTH,1);
		cal.set(Calendar.HOUR_OF_DAY,0);
		cal.set(Calendar.MINUTE,0);
		cal.set(Calendar.SECOND,0);
		cal.set(Calendar.MILLISECOND,0);
		Date periodStart = cal.getTime();
		VirtualCoin profit = new VirtualCoin(Currency.EURO,BigDecimal.ZERO);
		while (cal.get(Calendar.YEAR) <= currYear.get(Calendar.YEAR)) {
			Date start = cal.getTime();
			cal.add(Calendar.MONTH,1);
			Date end = cal.getTime();
			//VirtualCoin periodProfit = new VirtualCoin(Currency.EURO, BigDecimal.ZERO);
			VirtualCoin periodProfit = printSalesStatistics(start, end, strBuf, null, categoryStatisticsBuf, MAX_CATEGORY_ROWS_PER_SUBREPORT);
			BigDecimal periodProfitEuro = currencyDAO.convertCurrency(periodProfit.getCurrency(), Currency.EURO, periodProfit.getValue());
			profit = new VirtualCoin(Currency.EURO, profit.getValue().add(periodProfitEuro));
		}
		Date periodEnd = cal.getTime();
		strBuf.append("\nTotal profit: " + CurrencyFormatter.print(profit, Statics.FINNISH) + "\n");
		int nbrClosedAds = adDAO.getNbrClosedAds();
		int totNbrAdTemplates = adTemplateDAO.getNbrAdTemplates();

		//TODO: move the profit/ad and profit/adtemplate to the printSaleStatistics(...)-method.
		strBuf.append("\n\nHere is presented also numbers for profit per ad and profit per adtemplate. These numbers\n");
		strBuf.append("are however somewhat misleading. The value for profit per ad depends on for how long the ad was\n");
		strBuf.append("open for bid. If the ad would be open for bid forever, then ultimately someone would make a bid,\n");
		strBuf.append("So the profit per ad number is only useful for comparing different periods between which the\n");
		strBuf.append("number of days open stayed the same. The profit per adtemplate can also be used for comparing\n");
		strBuf.append("different periods, but here we have another problem, namely that new adtemplates might have been\n");
		strBuf.append("in the middle of the period, which makes the number somewhat lower than what would otherwise have\n");
		strBuf.append("been the case.\n");
		VirtualCoin profitPerAd = new VirtualCoin(profit.getCurrency(),profit.getValue().divide(new BigDecimal(nbrClosedAds),MathContext.DECIMAL64));
		strBuf.append("Total profit/ad: " + CurrencyFormatter.print(profitPerAd, Statics.FINNISH) + "\n");
		VirtualCoin profitPerAdTemplate = new VirtualCoin(profit.getCurrency(),profit.getValue().divide(new BigDecimal(totNbrAdTemplates),MathContext.DECIMAL64));
		strBuf.append("Total profit/adtemplate: " + CurrencyFormatter.print(profitPerAdTemplate, Statics.FINNISH) + "\n");
		strBuf.append("\n\n\n");
		
		/* Get translator statistics. */
		strBuf.append("Translator statistics:\n");
		strBuf.append("-----------------\n");
		strBuf.append("The translator statistics count only such sales that come through a market place since it's not\n");
		strBuf.append("possible to target a google-order sale to any translator. Therefore the total sales of all translators\n");
		strBuf.append("is lower than the total sales for the same period.\n");
		strBuf.append("\nIt's not possible to make a straight comparison between different translators based on this data because\n");
		strBuf.append("a poor translator that translated 100 products in January will still get more sales throughout the year\n");
		strBuf.append("than an excellent translator that started translating in December.\n\n");
		logger.debug("Going through the translators");
		for (String translator : adTemplateDAO.getAllTranslators()) {
			logger.debug("For translator " + translator);
			printSalesStatistics(periodStart, periodEnd, strBuf, translator, null, MAX_CATEGORY_ROWS_PER_SUBREPORT);
			logger.debug("Done with translator " + translator);
		}
		logger.debug("Done going through the translators");
		strBuf.append("\n\n\n");
		
		/* Bid percentage. */
		int nbrOpenAds = adDAO.getNbrOpenAds();
		int totNbrOrders = orderDAO.getNbrOrders();
		if (nbrClosedAds > 0) {
			strBuf.append("The bid percentage counts all incoming orders, not making adjustments\n");
			strBuf.append("for canceled orders not for orders that were never paid.\n");
			strBuf.append("Neither does the bid percentage take into consideration that some orders come\n");
			strBuf.append("trough google-order. These things make the bid percentage slightly higher\n");
			strBuf.append("than it would have been otherwise.\n");
			strBuf.append("Bid percentage: " + ((double)totNbrOrders / nbrClosedAds) + "\n");
		}
		
		/* Get statistics about ads. */
		strBuf.append("Ad statistics:\n");
		strBuf.append("Number of open ads: " + nbrOpenAds + "\n");
		strBuf.append("Number of closed ads: " + nbrClosedAds + "\n");
		strBuf.append("\n");

		/* Get statistics about orders. */
		strBuf.append("Order statistics:\n");
		int nbrFaultyPaymentError = orderDAO.getNbrOrdersInState(Order.STATE_PAID_TOO_LITTLE_ERROR);
		int nbrOtherError = orderDAO.getNbrOrdersInState(Order.STATE_OTHER_ERROR);
		int nbrNew = orderDAO.getNbrOrdersInState(Order.STATE_NEW);
		int nbrCustomerPaid = orderDAO.getNbrOrdersInState(Order.STATE_CUSTOMER_PAID);
		int nbrOrderedFromProvider = orderDAO.getNbrOrdersInState(Order.STATE_ORDERED_FROM_PROVIDER);
		int nbrOrdersConfirmedByProvider = orderDAO.getNbrOrdersInState(Order.STATE_ORDER_CONFIRMED_BY_PROVIDER);
		int nbrShippedByProvider = orderDAO.getNbrOrdersInState(Order.STATE_SHIPPED_BY_PROVIDER);
		int nbrClosedOrders = orderDAO.getNbrOrdersHavingAtLeastState(Order.STATE_CLOSED);
		//TODO: Add also the rest of the states to this email
		strBuf.append("Number of orders in error state (paid too little + other error): " + (nbrFaultyPaymentError + nbrOtherError) + "\n");
		strBuf.append("Number of new orders: " + nbrNew + "\n");
		strBuf.append("Number of paid orders having customer paid: " + nbrCustomerPaid + "\n");
		strBuf.append("Number of orders ordered from provider: " + nbrOrderedFromProvider + "\n");
		strBuf.append("Number of orders confirmed by provider: " + nbrOrdersConfirmedByProvider + "\n");
		strBuf.append("Number of orders shipped by provider: " + nbrShippedByProvider + "\n");
		strBuf.append("Number of closed orders: " + nbrClosedOrders + "\n");
		strBuf.append("\n");

		/* Special characters test. */
		strBuf.append("Special characters test:\n");
		strBuf.append("Euro symbol : € &euro; &#8364;\n");
		strBuf.append("Pound : £ &pound; &#163;\n");
		strBuf.append("Dollar : $ &dollar; &#36;\n");
		strBuf.append("Scandic 1 : å ä ö &aring; &auml; &ouml;\n");
		strBuf.append("Scandic 2 : Å Ä Ö &Aring; &Auml; &Ouml;\n");
		strBuf.append("\n");

		/* Get info about running of different modules. */
		strBuf.append("Modules statistics:\n");
		List<Settings> settingsList = settingsDAO.loadAll();
		for (Settings setting : settingsList) {
			if (setting.getKey().endsWith(".lastRun")) {
				strBuf.append(setting.getKey().substring(0,setting.getKey().length()-".lastRun".length()) + ": ");
				strBuf.append(setting.getValue());
				strBuf.append("\n");
			}
		}
		strBuf.append("\n");
		
		strBuf.append("\nCategory statistics:\n--------------------\nNOTE: Only the product revenue is");
		strBuf.append(" considered regarding category statistics and not bundle revenue (=shipping costs),");
		strBuf.append(" because otherwise we would get problems of correctly distributing shipping costs");
		strBuf.append(" when two products in the same bundle belong to different categories.\n\n");
		strBuf.append(categoryStatisticsBuf.toString());
		return strBuf;
	}
	
	/**
	 * Only count orders that are actually placed at provider (because only those ones have order.finalProviderPrice set).
	 */
	private VirtualCoin printSalesStatistics(Date start, Date end, StringBuffer strBuf, String addedBy, StringBuffer catStatBuf, int maxCategoryRows) {
		logger.debug("At start of printSalesStatistics");
		List<Order> periodOrders = null;
		if (addedBy != null) {
			periodOrders = orderDAO.getAllOrdersInOrderConfirmedOrLaterStatesByTranslator(start,end,addedBy);
		} else {
			periodOrders = orderDAO.getAllOrdersInOrderConfirmedOrLaterStates(start,end);
		}
		if (periodOrders.size() > 0) {
			Calendar cal = Calendar.getInstance();
			cal.setTime(end);
			cal.add(Calendar.DATE,-1);
			DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
			strBuf.append("PERIOD: " + dateFormat.format(start) + " - " + dateFormat.format(cal.getTime()) + "\n");
			logger.debug("Generating statistics for period " + dateFormat.format(start) + " - "
					+ dateFormat.format(cal.getTime()) + " for translator " + addedBy);
			if (catStatBuf != null) {
				catStatBuf.append("PERIOD: " + dateFormat.format(start) + " - " + dateFormat.format(cal.getTime()) + "\n");
			}
			if (addedBy != null) {
				strBuf.append("ADDED BY: " + addedBy + "\n");
			}
			/**
			 * For all orders during the period do the following:
			 * 1.) For all orders that are ordered from provider:
			 *     a.) Increase "product revenue" (=order.finalSalesPrice, which is zero for canceled
			 *         orders that weren't ever ordered from provider or that were ordered from provider
			 *         and then refunded from provider).
			 *     b.) Increase "product expenses to provider" (=order.finalProviderPrice, which is null
			 *         for orders that aren't ordered from provider and _never_ zero for canceled orders
			 *         that were ordered from provider).
			 *     c.) Increase "refundings from provider" for orders that have been/are in state.
			 *         STATE_MONEY_REFUNDED_BY_PROVIDER_FOR_CANCELED_ORDER (=order.finalProviderPrice)
			 *     d.) Increase "resend losses" for those products that have been ordered several
			 *         times from provider.
			 * 2.) For all orders:
			 *     a.) Increase "other loss" (order.otherLoss).
			 *     b.) Increase "refundings to customer" if the transaction table contains negative
			 *         payments for the order in question (no matter whether the refund took place
			 *         within the interval or not). This one contains both refunds for cancelations
			 *         as well as refunds because of paidTooMuch (since all refunds are handled as
			 *         "paid too much").
			 *     c.) Increase "tooBigPayments" if targeted > expected
			 * 3.) For all bundles:
			 *     a.) Increase "customer shipping cost" (=bundle.customerShippingCost, which is zero if
			 *         all the orders of the bundle are canceled).
			 *     b.) Increase "provider shipping cost" (=bundle.providerShippingCost, which is null
			 *         for orders that weren't ever ordered from provider and _never_ zero for
			 *         canceled orders that were ordered from provider).
			 *     c.) Increase "tooBigPayments" if targeted > expected
			 * 
			 * About tooBigPayments:
			 * tooBigPayments are included on the plus side (counted as income) since these are incomes
			 * that were in "product revenue" or "customer shipping cost" before the order was canceled.
			 * In addition tooBigPayments contains cases where the customer really did pay too much.
			 * When the customer gets a cash refund this money is moved to "refundings to customer" or
			 * to "product revenue"/"customer shipping cost" if the customer rather uses the money for
			 * buying more products.
			 * 
			 * About cancelations:
			 * No special care is needed with respect to cancelations. These have finalSalesPrice = zero,
			 * and either targetedPaid = original price, or else targetedPaid is moved to the targetedPaid
			 * of the bundle (if faultyPaymentFixer has been ran). In both cases the money the customer
			 * paid for canceled products end up in "tooBigPayments", i.e. it's counted on the plus side
			 * (income).
			 * 
			 * Limitation:
			 * In the following case the shipping cost will show the wrong value: Bundle B has order 1
			 * that was paid in June and order 2 was paid in July. In this case the shipping cost will
			 * be shown both in the June report as well as in the July report. This case shouldn't
			 * normally come since faultyPaymentFixer sets the status to "customer paid" only if the
			 * whole bundle is paid. However in the following rare case it can come: Bundle B has order
			 * 1 which is in stock and order 2 which is out of stock, but the customer pays for _all_
			 * orders in the bundle (including for order 2).
			 * => TODO: This can be solved by using STATE_CUSTOMER_PAID_INFORMED instead of STATE_CUSTOMER_PAID
			 * since CustomerPaymentInformer doesn't set the state to "paid informed" until all orders are paid.
			 */
			int nbrOrderedFromProvider = 0;
			int nbrBundles = 0;
			BigDecimal productRevenue = BigDecimal.ZERO;
			BigDecimal productExpensesToProvider = BigDecimal.ZERO;
			BigDecimal productRefundingsFromProvider = BigDecimal.ZERO;
			BigDecimal bundleRefundingsFromProvider = BigDecimal.ZERO;
			BigDecimal refundingsToCustomer = BigDecimal.ZERO;
			Map<Long,Long> bundlesGoneThrough = new HashMap<Long,Long>();
			BigDecimal customerShippingCost = BigDecimal.ZERO;
			BigDecimal providerShippingCost = BigDecimal.ZERO;
			BigDecimal productResendLoss = BigDecimal.ZERO;
			BigDecimal bundleResendLoss = BigDecimal.ZERO;
			BigDecimal otherLoss = BigDecimal.ZERO;
			//BigDecimal tooBigPayments = BigDecimal.ZERO;
			Map<String,BigDecimal> providerCategoryProductRevenue = new HashMap<String,BigDecimal>(); //<providerId_providerCategory,productRevenue>
			List<Long> providers = new ArrayList<Long>(); 
			Map<Long,BigDecimal> sellStarCategoryProductRevenue = new HashMap<Long,BigDecimal>(); //<sellStarCategory,productRevenue>
			logger.debug("Going through orders (periodOrders.size = " + periodOrders.size() + ")");
			Map<Long,String> orderIds = new HashMap<Long,String>();
			for (Order order : periodOrders) {
				orderIds.put(order.getId(),"");
				/*boolean orderedFromProvider = false;
				for (StateChange sc : order.getStateChanges()) {
					if (sc.getState().equals(Order.STATE_ORDERED_FROM_PROVIDER)) {
						orderedFromProvider = true;
						break;
					}
				}*/
				
				//boolean firstRefundingFromProvider = false;
				//boolean secondRefundingFromProvider = false;
				//if (orderedFromProvider) {
					nbrOrderedFromProvider++;
					
					/* Add category statistics for orders. */
					if (catStatBuf != null) {
						//Product product = productDAO.getByProviderProdId(order.getProviderProdId(), order.getOrderBundle().getProvider());
						String sKey = order.getProduct().getProvider().getId() + "_" + order.getProduct().getProviderCategory();
						if (providerCategoryProductRevenue.get(sKey) == null) {
							providerCategoryProductRevenue.put(sKey, new BigDecimal("0.00"));
						}
						BigDecimal val = providerCategoryProductRevenue.get(sKey);
						providerCategoryProductRevenue.put(sKey, val.add(order.getFinalSalesPriceInEuroExclVat()));
						Long lKey = order.getProduct().getCategory().getId();
						if (sellStarCategoryProductRevenue.get(lKey) == null) {
							sellStarCategoryProductRevenue.put(lKey, new BigDecimal("0.00"));
						}
						val = sellStarCategoryProductRevenue.get(lKey);
						sellStarCategoryProductRevenue.put(lKey, val.add(order.getFinalSalesPriceInEuroExclVat()));
						if (!providers.contains(order.getProduct().getProvider().getId())) {
							providers.add(order.getProduct().getProvider().getId());
						}
					}
					
					/* Add info for total revenue etc. for orders. */
					productRevenue = productRevenue.add(order.getFinalSalesPriceInEuroExclVat());
					productExpensesToProvider = productExpensesToProvider.add(order.getFinalProviderPriceInEuroExclVat());

					/* Add info regarding refundings from provider. */
					/*Date firstOrdering = null;
					Date secondOrdering = null;
					for (StateChange s : order.getStateChanges()) {
						if (s.getState().equals(Order.STATE_ORDERED_FROM_PROVIDER)) {
							if (firstOrdering == null) {
								firstOrdering = s.getUpdateDate();
							} else {
								if (firstOrdering.before(s.getUpdateDate())) {
									secondOrdering = s.getUpdateDate();
								} else {
									secondOrdering = firstOrdering;
									firstOrdering = s.getUpdateDate();
								}
							}
						}
					}
					logger.debug("Going through orders (subsubC)");
					for (StateChange s : order.getStateChanges()) {
						if (s.getState().equals(Order.STATE_MONEY_REFUNDED_BY_PROVIDER_FOR_CANCELED_ORDER)) {
							if (secondOrdering == null || secondOrdering.after(s.getUpdateDate())) {
								productRefundingsFromProvider = productRefundingsFromProvider.add(order.getFinalProviderPriceInEuroExclVat());
								firstRefundingFromProvider = true;
							} else {
								productRefundingsFromProvider = productRefundingsFromProvider.add(order.getResendLossInEuroExclVat());
								secondRefundingFromProvider = true;
							}
						}
					}
					logger.debug("Going through orders (subsubD)");*/
					
					/* Add info for resendings. */
					if (order.getResendLossInEuroExclVat().compareTo(BigDecimal.ZERO) != 0) {
						productResendLoss = productResendLoss.add(order.getResendLossInEuroExclVat());
					}
				//}
				
				/* Orders not ordered from provider has no finalSalesPrice, but still count otherLosses for those ones. */
				otherLoss = otherLoss.add(order.getOtherLossInEuro());
				
				/*for (Transaction tr : transactionDAO.getTransactionsForOrder(order)) {
					if (tr.getAmount().compareTo(BigDecimal.ZERO) < 0) {
						BigDecimal transactionAmountInEuro = currencyDAO.convertCurrency(tr.getCurrency().getId(), Currency.EURO, tr.getAmount());
						refundingsToCustomer = refundingsToCustomer.subtract(transactionAmountInEuro);
					}
				}*/

				/*if (order.getTargetedPaidInMarketCurrency().compareTo(order.getFinalSalesPriceInMarketCurrency()) != 0) {
					BigDecimal diffMC = order.getTargetedPaidInMarketCurrency().subtract(order.getFinalSalesPriceInMarketCurrency());
					BigDecimal diffEuro = currencyDAO.convertCurrency(order.getOrderBundle().getUsedCustomerCurrency().getId(), Currency.EURO, diffMC);
					tooBigPayments = tooBigPayments.add(diffEuro);
				}*/
				
				/* Handle bundle specific stuff. */
				OrderBundle bundle = order.getOrderBundle();
				if (bundlesGoneThrough.get(bundle.getId()) == null) {
					nbrBundles++;
					customerShippingCost = customerShippingCost.add(bundle.getCustomerShippingCostInEuroExclVat());
					if (bundle.getProviderShippingCostInEuroExclVat() != null) {
						// bundle.providerShippingCost is null for orders that aren't ever ordered from provider 
						providerShippingCost = providerShippingCost.add(bundle.getProviderShippingCostInEuroExclVat());
					}
					/*if (bundle.getTargetedPaidShippingCostInMarketCurrency().
							compareTo(bundle.getCustomerShippingCostInMarketCurrency()) != 0) {
						BigDecimal diffMC = bundle.getTargetedPaidShippingCostInMarketCurrency().subtract(bundle.getCustomerShippingCostInMarketCurrency());
						BigDecimal diffEuro = currencyDAO.convertCurrency(bundle.getUsedCustomerCurrency().getId(), Currency.EURO, diffMC);
						tooBigPayments = tooBigPayments.add(diffEuro);
					}*/
					
					/* Add info regarding bundle specific refunding from provider. */
					/*if (bundle.getCustomerShippingCostInEuroExclVat().compareTo(BigDecimal.ZERO) == 0) {
						/* Ok, shipping cost is refunded to customer, so in this case also the provider
						 * should return the shipping cost (in case the provider has at all given us a
						 * refund). Note: even if the provider didn't refund the shipping cost (only
						 * the product cost), then we can still add the provider shipping cost to the
						 * refundingsFromProvider since it will have been added to otherLoss in case
						 * the provider only refunded the product cost (so the total sum will still
						 * be correct). /
						if (firstRefundingFromProvider) {
							bundleRefundingsFromProvider = bundleRefundingsFromProvider.add(bundle.getProviderShippingCostInEuroExclVat());
						}
						if (secondRefundingFromProvider) {
							bundleRefundingsFromProvider = bundleRefundingsFromProvider.add(bundle.getResendLossInEuroExclVat());
						}
					}*/
					
					/* Add info for resendings. */
					if (bundle.getResendLossInEuroExclVat().compareTo(BigDecimal.ZERO) != 0) {
						bundleResendLoss = bundleResendLoss.add(bundle.getResendLossInEuroExclVat());
					}

					bundlesGoneThrough.put(bundle.getId(),1L);
				}
			}
			logger.debug("Done going through orders");

			/* Add info regarding order specific refundings from provider. */
			logger.debug("Add info regarding refundings from provider");
			Map<Long,OrderBundle> firstRefundingBundlesGoneThrough = new HashMap<Long,OrderBundle>();
			Map<Long,OrderBundle> secondRefundingBundlesGoneThrough = new HashMap<Long,OrderBundle>();
			List<Order> refundedOrders = orderDAO.getAllOrdersRefundedFromProvider();
			for (Order order : refundedOrders) {
				if (orderIds.get(order.getId()) != null) {
					Date firstOrdering = null;
					Date secondOrdering = null;
					for (StateChange s : order.getStateChanges()) {
						if (s.getState().equals(Order.STATE_ORDERED_FROM_PROVIDER)) {
							if (firstOrdering == null) {
								firstOrdering = s.getUpdateDate();
							} else {
								if (firstOrdering.before(s.getUpdateDate())) {
									secondOrdering = s.getUpdateDate();
								} else {
									secondOrdering = firstOrdering;
									firstOrdering = s.getUpdateDate();
								}
							}
						}
					}
					for (StateChange s : order.getStateChanges()) {
						if (s.getState().equals(Order.STATE_MONEY_REFUNDED_BY_PROVIDER_FOR_CANCELED_ORDER)) {
							if (secondOrdering == null || secondOrdering.after(s.getUpdateDate())) {
								productRefundingsFromProvider = productRefundingsFromProvider.add(order.getFinalProviderPriceInEuroExclVat());
								firstRefundingBundlesGoneThrough.put(order.getOrderBundle().getId(),order.getOrderBundle());
							} else {
								productRefundingsFromProvider = productRefundingsFromProvider.add(order.getResendLossInEuroExclVat());
								secondRefundingBundlesGoneThrough.put(order.getOrderBundle().getId(),order.getOrderBundle());
							}
						}
					}
				}
			}
			
			/* Add info regarding bundle specific refunding from provider. If the shipping cost is
			 * refunded to customer, then also the provider should return the shipping cost (in
			 * case the provider has at all given us a refund). Note: even if the provider didn't
			 * refund the shipping cost (only the product cost), then we can still add the provider
			 * shipping cost to the refundingsFromProvider since it will have been added to otherLoss
			 * in case the provider only refunded the product cost (so the total sum will still be
			 * correct). */
			Iterator<OrderBundle> bIter = firstRefundingBundlesGoneThrough.values().iterator();
			while (bIter.hasNext()) {
				OrderBundle bundle = bIter.next();
				if (bundle.getCustomerShippingCostInEuroExclVat().compareTo(BigDecimal.ZERO) == 0) {
					bundleRefundingsFromProvider = bundleRefundingsFromProvider.add(bundle.getProviderShippingCostInEuroExclVat());
				}
			}
			bIter = secondRefundingBundlesGoneThrough.values().iterator();
			while (bIter.hasNext()) {
				OrderBundle bundle = bIter.next();
				if (bundle.getCustomerShippingCostInEuroExclVat().compareTo(BigDecimal.ZERO) == 0) {
					bundleRefundingsFromProvider = bundleRefundingsFromProvider.add(bundle.getResendLossInEuroExclVat());
				}
			}
			logger.debug("Done adding info regarding refundings from provider");
			
			/* Add info regarding refunds to customers. */
			logger.debug("Add info regarding refunds to customers");
			for (Transaction tr : transactionDAO.getOrderRefunds()) {
				if (orderIds.get(tr.getOrder().getId()) != null) {
					BigDecimal transactionAmountInEuro = currencyDAO.convertCurrency(tr.getCurrency().getId(), Currency.EURO, tr.getAmount());
					refundingsToCustomer = refundingsToCustomer.subtract(transactionAmountInEuro);
				}
			}
			logger.debug("Done adding info regarding refunds to customers");

			/*strBuf.append("Number of paid orders: " + periodOrders.size());
			if (nbrOrderedFromProvider != periodOrders.size()) {
				strBuf.append(" (ordered from provider: " + nbrOrderedFromProvider + ")");
			}*/
			strBuf.append("Number of orders ordered from provider: " + periodOrders.size());
			strBuf.append(", number of bundles: " + nbrBundles);
			strBuf.append("\n");
			VirtualCoin productRevenueVC = new VirtualCoin(Currency.EURO, productRevenue);
			VirtualCoin customerShippingCostVC = new VirtualCoin(Currency.EURO, customerShippingCost);
			//VirtualCoin tooBigPaymentsVC = new VirtualCoin(Currency.EURO, tooBigPayments);
			VirtualCoin productExpensesToProviderVC = new VirtualCoin(Currency.EURO, productExpensesToProvider);
			VirtualCoin providerShippingCostVC = new VirtualCoin(Currency.EURO, providerShippingCost);
			VirtualCoin productRefundingsFromProviderVC = new VirtualCoin(Currency.EURO, productRefundingsFromProvider);
			VirtualCoin bundleRefundingsFromProviderVC = new VirtualCoin(Currency.EURO, bundleRefundingsFromProvider);
			VirtualCoin refundingsToCustomerVC = new VirtualCoin(Currency.EURO, refundingsToCustomer);
			VirtualCoin productResendLossVC = new VirtualCoin(Currency.EURO, productResendLoss);
			VirtualCoin bundleResendLossVC = new VirtualCoin(Currency.EURO, bundleResendLoss);
			VirtualCoin otherLossVC = new VirtualCoin(Currency.EURO, otherLoss);
			//TODO: add information about currency exchange rate gains/losses (for orders coming through google-order we need to count
			//from the time the product was ordered (because then the price in euros was set) and for normal orders from the time the
			//ad was listed (because then the price in euros was set) until the order was placed at the provider
			//TODO: add information about gains/losses because the provider price changed from the time the ad was listed / order placed through
			//google order until we actually placed the order at the provider
		    strBuf.append("   + product revenue               : " + CurrencyFormatter.print(productRevenueVC, Statics.FINNISH) + "\n");
		    strBuf.append("   + shipping revenue              : " + CurrencyFormatter.print(customerShippingCostVC, Statics.FINNISH) + "\n");
			/*if (tooBigPaymentsVC.getValue().compareTo(BigDecimal.ZERO) != 0) {
				strBuf.append("   + too big payments              : " + CurrencyFormatter.print(tooBigPaymentsVC, Statics.FINNISH) + "\n");
			}*/
			strBuf.append("   - product expenses to provider  : " + CurrencyFormatter.print(productExpensesToProviderVC, Statics.FINNISH) + "\n");
			strBuf.append("   - shipping expenses to provider : " + CurrencyFormatter.print(providerShippingCostVC, Statics.FINNISH) + "\n");
			if (productRefundingsFromProviderVC.getValue().compareTo(BigDecimal.ZERO) != 0) {
				strBuf.append("   + prod refundings from provider : " + CurrencyFormatter.print(productRefundingsFromProviderVC, Statics.FINNISH) + "\n");
			}
			if (bundleRefundingsFromProviderVC.getValue().compareTo(BigDecimal.ZERO) != 0) {
				strBuf.append("   + ship refundings from provider : " + CurrencyFormatter.print(bundleRefundingsFromProviderVC, Statics.FINNISH) + "\n");
			}
			if (refundingsToCustomerVC.getValue().compareTo(BigDecimal.ZERO) != 0) {
				strBuf.append("   - refundings to customers       : " + CurrencyFormatter.print(refundingsToCustomerVC, Statics.FINNISH) + "\n");
			}
			if (productResendLossVC.getValue().compareTo(BigDecimal.ZERO) != 0) {
				strBuf.append("   - prod resend losses            : " + CurrencyFormatter.print(productResendLossVC, Statics.FINNISH) + "\n");
			}
			if (bundleResendLossVC.getValue().compareTo(BigDecimal.ZERO) != 0) {
				strBuf.append("   - ship resend losses            : " + CurrencyFormatter.print(bundleResendLossVC, Statics.FINNISH) + "\n");
			}
			if (otherLossVC.getValue().compareTo(BigDecimal.ZERO) != 0) {
				strBuf.append("   - other losses                  : " + CurrencyFormatter.print(otherLossVC, Statics.FINNISH) + "\n");
			}
			strBuf.append("-------------------------------------------\n");
			BigDecimal profit = productRevenue.add(customerShippingCost)./*add(tooBigPayments).*/
				subtract(productExpensesToProvider).subtract(providerShippingCost).
				add(productRefundingsFromProvider).add(bundleRefundingsFromProvider).
				subtract(refundingsToCustomer).subtract(productResendLoss).
				subtract(bundleResendLoss).subtract(otherLoss);
			VirtualCoin profitVC = new VirtualCoin(Currency.EURO, profit);
			strBuf.append("Profit                             : " + CurrencyFormatter.print(profitVC, Statics.FINNISH) + "\n");
			if (addedBy == null) {
				if (transactionDAO.getNbrTransactionsForCurrency(Currency.EURO) != transactionDAO.getTotalNbrTransactions()) {
					/* When we start getting different currencies in parallel in the transaction table, then we
					 * probably need to calculate the cash flow separately for the different currencies
					 * (plus a summary where all individual cash flows are converted to euros and then summed). */
					throw new RuntimeException("Not yet supported: different currencies in the transaction table.");
				} else {
					VirtualCoin cashFlow = new VirtualCoin(Currency.EURO,transactionDAO.getSumAmount(start, end));
					strBuf.append("(cash flow during period           : " + CurrencyFormatter.print(cashFlow, Statics.FINNISH) + ")\n");
				}
			}
			strBuf.append("\n\n");
			
			/* Add category statistics for orders. */
			logger.debug("Calculating statistics");
			if (catStatBuf != null) {
				/* Generate statistics for provider categories. */
				for (Long providerId : providers) {
					Provider provider = providerDAO.get(providerId);
					Iterator<String> iter = providerCategoryProductRevenue.keySet().iterator();
					List<StringDoubleKV> productRevenueEuro = new ArrayList<StringDoubleKV>(); //<providerCategory,productRevenue>
					List<StringDoubleKV> productRevenueAdjusted = new ArrayList<StringDoubleKV>(); //<providerCategory,productRevenueAdjusted>
					//logger.debug("Statistik1 for provider " + provider.getName());
					while (iter.hasNext()) {
						String key = iter.next();
						if (key.startsWith(providerId + "_")) {
							String providerCategory = key.substring((providerId + "_").length());
							// Note: it isn't totally accurate to simply take the number of products in the
							// provider category, because some of the products might be inactive. In addition some
							// products are listed several times (because for some products there are several mappings
							// to different market categories). Neither would it be totally accurate to take the number
							// of active adTemplates, because some adTemplate might have been marked as inactive at
							// the beginning of the month and some at the end of the month. The _correct_ way would
							// be to actually count the number of _ads_. However that database operation is so heavy
							// that we are satisfied with a (pretty good) estimation.
							int nbrProductsInCategory = productDAO.getNbrProductsWithProviderCategory(providerCategory,provider);
							productRevenueEuro.add(new StringDoubleKV(providerCategory, providerCategoryProductRevenue.get(key).doubleValue()));
							productRevenueAdjusted.add(new StringDoubleKV(providerCategory,
									(double) providerCategoryProductRevenue.get(key).doubleValue() / nbrProductsInCategory));
						}
					}
					//logger.debug("Done with statistik1 for provider " + provider.getName());
					//logger.debug("Statistik2 for provider " + provider.getName());
					if (productRevenueEuro.size() > 0) {
						StringBuffer provBuf = new StringBuffer();
						Collections.sort(productRevenueEuro, new StringDoubleKVComparator());
						provBuf.append("Revenue based provider category statistics for " + provider.getName() + " (best " + maxCategoryRows + "):\n");
						int i = 0;
						for (StringDoubleKV entry : productRevenueEuro) {
							if (i >= maxCategoryRows) {
								break;
							}
							VirtualCoin entryVC = new VirtualCoin(Currency.EURO,new BigDecimal(entry.getValue()));
							provBuf.append(CurrencyFormatter.print(entryVC, Statics.FINNISH) + " (" + entry.getKey() + ")\n");
							i++;
						}
						provBuf.append("\n\n");
						Collections.sort(productRevenueAdjusted, new StringDoubleKVComparator());
						provBuf.append("Adjusted revenue based provider category statistics for " + provider.getName() + " (best " + maxCategoryRows + "):\n");
						i = 0;
						for (StringDoubleKV entry : productRevenueAdjusted) {
							if (i >= maxCategoryRows) {
								break;
							}
							VirtualCoin entryVC = new VirtualCoin(Currency.EURO,new BigDecimal(entry.getValue()));
							provBuf.append(CurrencyFormatter.print(entryVC, Statics.FINNISH) + " (" + entry.getKey() + ")\n");
							i++;
						}
						provBuf.append("\n\n");
						catStatBuf.append(provBuf.toString());
					}
					//logger.debug("Done with statistik2 for provider " + provider.getName());
				}
				
				/* Generate statistics for SellStar categories. */
				//logger.debug("Statistik3");
				List<StringDoubleKV> productRevenueEuro = new ArrayList<StringDoubleKV>(); //<providerCategory,productRevenue>
				List<StringDoubleKV> productRevenueAdjusted = new ArrayList<StringDoubleKV>(); //<providerCategory,productRevenueAdjusted>
				Iterator<Long> iter = sellStarCategoryProductRevenue.keySet().iterator();
				while (iter.hasNext()) {
					Long sellStarCategoryId = iter.next();
					int nbrProductsInCategory = productDAO.getNbrProductsWithSellStarCategory(sellStarCategoryId);
					String categoryFullPath = extractPath(getWholePathFromRootFor(categoryDAO.get(sellStarCategoryId)));
					productRevenueEuro.add(new StringDoubleKV(categoryFullPath, sellStarCategoryProductRevenue.get(sellStarCategoryId).doubleValue()));
					productRevenueAdjusted.add(new StringDoubleKV(categoryFullPath,
							(double) sellStarCategoryProductRevenue.get(sellStarCategoryId).doubleValue() / nbrProductsInCategory));
				}
				//logger.debug("Done with statistik3");
				//logger.debug("Statistik4");
				if (productRevenueEuro.size() > 0) {
					StringBuffer provBuf = new StringBuffer();
					Collections.sort(productRevenueEuro, new StringDoubleKVComparator());
					provBuf.append("Revenue based SellStar category statistics (best " + maxCategoryRows + "):\n");
					int i = 0;
					for (StringDoubleKV entry : productRevenueEuro) {
						if (i >= maxCategoryRows) {
							break;
						}
						VirtualCoin entryVC = new VirtualCoin(Currency.EURO,new BigDecimal(entry.getValue()));
						provBuf.append(CurrencyFormatter.print(entryVC, Statics.FINNISH) + " (" + entry.getKey() + ")\n");
						i++;
					}
					provBuf.append("\n\n");
					Collections.sort(productRevenueAdjusted, new StringDoubleKVComparator());
					provBuf.append("Adjusted revenue based SellStar category statistics (best " + maxCategoryRows + "):\n");
					i = 0;
					for (StringDoubleKV entry : productRevenueAdjusted) {
						if (i >= maxCategoryRows) {
							break;
						}
						VirtualCoin entryVC = new VirtualCoin(Currency.EURO,new BigDecimal(entry.getValue()));
						provBuf.append(CurrencyFormatter.print(entryVC, Statics.FINNISH) + " (" + entry.getKey() + ")\n");
						i++;
					}
					provBuf.append("\n\n");
					catStatBuf.append(provBuf.toString());
				}
				//logger.debug("Done with statistik4");
				
				/* Generate statistics on the maxCategoryRows most sold products based on number of orders. */
				//logger.debug("Statistik5");
				List<Map<String,Object>> bestSellingProducts = productDAO.getBestSellingProductsBasedOnAmount(start,end);
				if (bestSellingProducts.size() > 0) {
					catStatBuf.append(maxCategoryRows + " most sold products during period based on number of orders:\n");
					int i = 0;
					for (Map<String,Object> listObj : bestSellingProducts) {
						if (i >= maxCategoryRows) {
							break;
						}
						Long productId = (Long)listObj.get("product_id");
						String headline = (String) listObj.get("headline");
						Long count = (Long) listObj.get("count");
						catStatBuf.append(count + " (productId = " + productId + ", " + headline + ")\n");
						i++;
					}
					catStatBuf.append("\n\n");
				}
				//logger.debug("Done with statistik5");
				
				/* Generate statistics on the maxCategoryRows most sold products based on revenue. */
				//logger.debug("Statistik6");
				bestSellingProducts = productDAO.getBestSellingProductsBasedOnRevenueInEuro(start,end);
				if (bestSellingProducts.size() > 0) {
					catStatBuf.append(maxCategoryRows + " most sold products during period based on revenue:\n");
					int i = 0;
					for (Map<String,Object> listObj : bestSellingProducts) {
						if (i >= maxCategoryRows) {
							break;
						}
						Long productId = (Long)listObj.get("product_id");
						String headline = (String) listObj.get("headline");
						VirtualCoin sum = new VirtualCoin(Currency.EURO,(BigDecimal) listObj.get("sum"));
						catStatBuf.append(CurrencyFormatter.print(sum, Statics.FINNISH) + " (productId = " + productId + ", " + headline + ")\n");
						i++;
					}
					catStatBuf.append("\n\n");
				}
				//logger.debug("Done with statistik6");
			}
			logger.debug("Done calculating statistics");
			
			return profitVC;
		} else {
			return new VirtualCoin(Currency.EURO, BigDecimal.ZERO);
		}
	}
	
	public class StringDoubleKVComparator implements Comparator<StringDoubleKV> {
		public int compare(StringDoubleKV obj1, StringDoubleKV obj2) {
			if (obj1 != null && obj2 != null) {
				return obj2.getValue().compareTo(obj1.getValue());
			} else if (obj2 == null) {
				return -1;
			} else {
				return 1;
			}
		}
	}
	
	public class StringDoubleKV {
		private String key;
		private Double value;
		
		public StringDoubleKV(String key, Double value) {
			this.key = key;
			this.value = value;
		}

		public String getKey() {
			return key;
		}

		public Double getValue() {
			return value;
		}
	}
	
	//TODO: this method is just copy'n'pasted from CategoryHelper - refactor!
	private List<Category> getWholePathFromRootFor(Category category) {
		List<Category> list = new ArrayList<Category>();
		Category p = category;
		list.add(p);
		while (p.getParentCategory() != null) {
			p = p.getParentCategory();
			list.add(p);
		}
		Collections.reverse(list);
		return list;
		
	}
	
	//TODO: this method is just copy'n'pasted from CategoryHelper - refactor!
	private String extractPath(List<Category> path) {
		StringBuffer strBuf = new StringBuffer();
		if (path.isEmpty()) {
			strBuf.append("/");
		} else {
			for (Category category : path) {
				strBuf.append("/" + category.getName());
			}
		}
		return strBuf.toString();
	}

}
