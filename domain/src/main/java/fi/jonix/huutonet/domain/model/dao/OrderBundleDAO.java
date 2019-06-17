package fi.jonix.huutonet.domain.model.dao;

import java.math.BigDecimal;
import java.util.List;

import fi.jonix.huutonet.domain.model.Currency;
import fi.jonix.huutonet.domain.model.Customer;
import fi.jonix.huutonet.domain.model.Order;
import fi.jonix.huutonet.domain.model.OrderBundle;
import fi.jonix.huutonet.domain.model.Provider;
import fi.jonix.huutonet.domain.model.Seller;

public interface OrderBundleDAO extends GenericDAO<OrderBundle, Long> {

	public OrderBundle getOrderBundleByProviderOrderId(String providerOrderId, Provider provider);
	public OrderBundle getOrderBundleByPayPalId(String payPalId, Provider provider);
	public List<OrderBundle> getOrderBundles(Customer customer, Provider provider, Seller seller);
	public List<OrderBundle> getBundlesWithUnevenTargetPayments();
	public List<OrderBundle> getBundlesByFinalSalesPricePlusShippingCost(BigDecimal totalExpectedPayment, Currency usedCustomerCurrency);
	public List<OrderBundle> getPartlyPaidBundlesByRemainingFinalSalesPricePlusShippingCost(BigDecimal remainingExpectedPayment, Currency usedCustomerCurrency);
	public List<OrderBundle> getEmptyBundles();
	public List<OrderBundle> getPaidOrPartlyPaidBundlesWhereCustomerIsNotInformed();
	public List<OrderBundle> getBundlesForPlaceOrder(Provider provider);
	public List<OrderBundle> getBundlesForStatusUpdate(Provider provider);
	//public List<OrderBundle> getBundlesForLocationTracking();
	public List<OrderBundle> getAllOpenBundles();
	public int getBundleCountForUsedCustomerCurrency(Customer customer, Currency usedCustomerCurrency);
	public List<OrderBundle> getBundlesNotTransferedToVerification();
	public BigDecimal getTotalSalesInclVat(Currency currency, BigDecimal customerVatPercentage);
	public BigDecimal getTotalSalesExclVat(Currency currency, BigDecimal customerVatPercentage);
	public List<BigDecimal> getDistinctCustomerVatPercentages();

}
