package fi.jonix.huutonet.bank;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import fi.jonix.huutonet.domain.ApplicationContextPlaceholder;
import fi.jonix.huutonet.domain.model.Currency;
import fi.jonix.huutonet.domain.model.Order;
import fi.jonix.huutonet.domain.model.Seller;
import fi.jonix.huutonet.domain.model.dao.OrderDAO;
import fi.jonix.huutonet.exception.BankException;

/**
 * Helper class for handling balancing of payments between different orders.
 * 
 * @author john
 *
 */
public class TargetedOrders {

	public static final Logger logger = Logger.getLogger(TargetedOrders.class);
	
	private BigDecimal expectedPaymentInMarketCurrency;
	private BigDecimal targetedPaidInMarketCurrency;
	private List<Order> orders;
	private Seller seller;
	
	protected TargetedOrders() {
		expectedPaymentInMarketCurrency = BigDecimal.ZERO;
		targetedPaidInMarketCurrency = BigDecimal.ZERO;
		orders = new ArrayList<Order>();
	}

	/* Targeted-sum balancing has to be done separately for different currencies,
	 * so therefore add the currency id to the id string. */
	public static String email2Id(String email, Seller seller, Currency usedMarketCurrency) {
		return email + "_" + usedMarketCurrency.getId() + "_" + seller.getId();
	}

	public static String id2Email(String id) {
		/* Email addresses can contain underscores, so we need to take
		 * the substring that ends before the second last underscore. */
		String emailAndCurrency = id.substring(0, id.lastIndexOf("_"));
		return emailAndCurrency.substring(0, emailAndCurrency.lastIndexOf("_"));
	}

	public static Long id2SellerId(String id) {
		return Long.parseLong(id.substring(id.lastIndexOf("_")+1));
	}

	public static Long id2CurrencyId(String id) {
		String emailAndCurrency = id.substring(0, id.lastIndexOf("_"));
		return Long.parseLong(emailAndCurrency.substring(emailAndCurrency.lastIndexOf("_")+1));
	}

	/**
	 * Returns a list of the payments that the customers have done grouped by seller.
	 */
	public static Map<String,TargetedOrders> getUnevenlyTargetedOrders() {
		OrderDAO orderDAO = (OrderDAO) ApplicationContextPlaceholder.applicationContext.getBean("orderDAO");
		Map<String,TargetedOrders> retMap = new HashMap<String,TargetedOrders>();

		/* First get uneven payments and arrange them by customer. */
		Map<String,Map<Long,Order>> personMap = new HashMap<String,Map<Long,Order>>();
		List<Order> unevenTargetList = orderDAO.getOrdersWithUnevenTargetPayments();
		logger.debug("unevenTargetList.size = " + unevenTargetList.size());
		for (Order order : unevenTargetList) {
			String id = email2Id(order.getOrderBundle().getCustomer().getEmail(),order.getOrderBundle().getSeller(),
					order.getOrderBundle().getUsedCustomerCurrency());
			Map<Long,Order> individual = personMap.get(id);
			if (individual == null) {
				individual = new HashMap<Long,Order>();
				personMap.put(id,individual);
			}
			if (individual.get(order.getId()) == null) {
				individual.put(order.getId(),order);
			}
		}
		
		/* Go through each person. */
		for (String id : personMap.keySet()) {
			TargetedOrders targetedOrders = new TargetedOrders();
			Map<Long,Order> individual = personMap.get(id);
			
			/* Go through the orders of each person. */
			for (Long orderId : individual.keySet()) {
				Order order = individual.get(orderId);
				targetedOrders.seller = order.getOrderBundle().getSeller(); //every order has the same seller here 
				targetedOrders.expectedPaymentInMarketCurrency = targetedOrders.expectedPaymentInMarketCurrency.add(order.getFinalSalesPriceInMarketCurrencyInclVat());
				targetedOrders.targetedPaidInMarketCurrency = targetedOrders.targetedPaidInMarketCurrency.add(order.getTargetedPaidInMarketCurrency());
				targetedOrders.orders.add(order);
			}
			retMap.put(id,targetedOrders);
		}
		return retMap;
	}
	
	public void setExpectedPaymentInMarketCurrency(
			BigDecimal expectedPaymentInMarketCurrency) {
		this.expectedPaymentInMarketCurrency = expectedPaymentInMarketCurrency;
	}

	public BigDecimal getExpectedPaymentInMarketCurrency() {
		return expectedPaymentInMarketCurrency;
	}

	public void setTargetedPaidInMarketCurrency(BigDecimal targetedPaidInMarketCurrency) {
		this.targetedPaidInMarketCurrency = targetedPaidInMarketCurrency;
	}

	public BigDecimal getTargetedPaidInMarketCurrency() {
		return targetedPaidInMarketCurrency;
	}

	public void setOrders(List<Order> orders) {
		this.orders = orders;
	}

	public List<Order> getOrders() {
		return orders;
	}

	public void setSeller(Seller seller) {
		this.seller = seller;
	}

	public Seller getSeller() {
		return seller;
	}

}
