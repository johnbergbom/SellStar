package fi.jonix.huutonet.domain.model.dao;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;

import fi.jonix.huutonet.domain.model.Currency;
import fi.jonix.huutonet.domain.model.Customer;
import fi.jonix.huutonet.domain.model.Market;
import fi.jonix.huutonet.domain.model.Order;
import fi.jonix.huutonet.domain.model.OrderBundle;
import fi.jonix.huutonet.domain.model.Product;
import fi.jonix.huutonet.domain.model.Provider;
import fi.jonix.huutonet.domain.model.Seller;
import fi.jonix.huutonet.domain.model.SellerMarket;
import fi.jonix.huutonet.domain.model.Tracking;

public interface OrderDAO extends GenericDAO<Order, Long> {

	public List<Order> getByProviderProductId(String providerProdId, Provider provider);
	public int getNbrOrdersInBundle(OrderBundle orderBundle);
	public List<Order> getOrdersInBundle(OrderBundle orderBundle);
	public List<Order> getOrdersInTrackingObject(Tracking tracking);
	public Order getByReferenceNumberOnly(String customerReferenceNumber, Currency usedCustomerCurrency);
	public List<Order> getBySimilarReferenceNumberOnly(String partialReferenceNumber, Currency usedCustomerCurrency);
	public Order getByReferenceNumber(String customerReferenceNumber, Seller seller);
	public List<Order> getByMarketSalesId(String marketSalesId, Seller seller, Market market);
	public List<Order> getAllOrdersRefundedFromProvider();
	public List<Order> getAllOrdersInOrderConfirmedOrLaterStates(Date startDate, Date endDate);
	public List<Order> getAllOrdersInOrderConfirmedOrLaterStatesByTranslator(Date startDate, Date endDate, String translator);
	public int getNbrOrdersInState(int state);
	public int getNbrOrdersHavingAtLeastState(int state);
	public int getNbrOrders();
	//public List<Map<String,Object>> getBestSellingProviderProdIds();
	public List<Map<String,Object>> getBestSellingProviderProdIds(int weeksBack);
	public List<Order> getRecentOutOfStockOrdersForProduct(int weeksBack, Product product);
	public int getNbrOrdersForProduct(Product product);
	public List<Order> getOrdersByState(int state);
	public List<Order> getOrdersWithoutFeedbackInPaidOrLaterStates(SellerMarket sellerMarket);
	public List<Order> getOrdersByCustomerEmailAddress(String emailAddress);
	public List<Order> getOrdersByCustomerEmailAddress(String emailAddress, Seller seller);
	public List<Order> getOrdersByCustomer(Customer customer, Seller seller);
	public List<Order> getOrdersByStateAndCustomerEmailAddress(int state, String emailAddress, Seller seller);
	//public String getProductName(Order order);
	public List<Order> getOrdersByCustomerStringFieldIgnoreCase(String fieldName, String value);

	/**
	 * @param headline - Example: "aaa" fetches all strings equal to "aaa",
	 * "%aaa" fetches all strings ending with "aaa", "aaa%" fetches all strings
	 * starting with "aaa" and "%aaa%" fetches all strings having "aaa" somewhere
	 * in it.
	 */
	public List<Order> getBySimilarHeadline(String headline);

	public List<Order> getOrdersWithUnevenTargetPayments();
	
	//public List<Order> getAllOrdersForBundlesWhereSomeOrderIsRefunded();
	//public List<Long> getIdsOfAllRefundedOrders();
	public List<Order> getOrdersWithNonZeroRefundings();
	public Date getDateOfLastWonBid(Customer customer, Seller seller);
	public int getNbrShippingBatchesForBundle(OrderBundle bundle);
	public List<Order> getOrdersNotTransferedToVerification();
	public BigDecimal getTotalSalesInclVat(Currency currency, BigDecimal customerVatPercentage);
	public BigDecimal getTotalSalesExclVat(Currency currency, BigDecimal customerVatPercentage);
	public List<BigDecimal> getDistinctCustomerVatPercentages();

}
