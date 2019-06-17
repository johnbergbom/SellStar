package fi.jonix.huutonet.bank;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import fi.jonix.huutonet.domain.ApplicationContextPlaceholder;
import fi.jonix.huutonet.domain.model.Customer;
import fi.jonix.huutonet.domain.model.OrderBundle;
import fi.jonix.huutonet.domain.model.Seller;
import fi.jonix.huutonet.domain.model.dao.OrderBundleDAO;

public class TargetedBundles {

	public static final Logger logger = Logger.getLogger(TargetedBundles.class);
	
	private BigDecimal expectedPaymentInMarketCurrency;
	private BigDecimal targetedPaidInMarketCurrency;
	private List<OrderBundle> bundles;
	private Seller seller;
	//private Customer customer;
	
	protected TargetedBundles() {
		expectedPaymentInMarketCurrency = BigDecimal.ZERO;
		targetedPaidInMarketCurrency = BigDecimal.ZERO;
		bundles = new ArrayList<OrderBundle>();
	}

	/**
	 * Returns a list of payments for bundles that the customers have done grouped by seller.
	 */
	public static Map<String,TargetedBundles> getUnevenlyTargetedBundles() {
		OrderBundleDAO orderBundleDAO = (OrderBundleDAO) ApplicationContextPlaceholder.applicationContext.getBean("orderBundleDAO");
		Map<String,TargetedBundles> retMap = new HashMap<String,TargetedBundles>();
		
		/* First get uneven payments and arrange them by customer. */
		Map<String,Map<Long,OrderBundle>> personMap = new HashMap<String,Map<Long,OrderBundle>>();
		List<OrderBundle> unevenBundleList = orderBundleDAO.getBundlesWithUnevenTargetPayments();
		logger.debug("unevenBundleList.size = " + unevenBundleList.size());
		for (OrderBundle bundle : unevenBundleList) {
			String id = TargetedOrders.email2Id(bundle.getCustomer().getEmail(),bundle.getSeller(), bundle.getUsedCustomerCurrency());
			Map<Long,OrderBundle> individual = personMap.get(id);
			if (individual == null) {
				individual = new HashMap<Long,OrderBundle>();
				personMap.put(id,individual);
			}
			if (individual.get(bundle.getId()) == null) {
				individual.put(bundle.getId(),bundle);
			}
		}
		
		/* Go through each person. */
		for (String id : personMap.keySet()) {
			TargetedBundles targetedBundles = new TargetedBundles();
			Map<Long,OrderBundle> individual = personMap.get(id);
			
			/* Go through the orders of each person. */
			for (Long bundleId : individual.keySet()) {
				OrderBundle bundle = individual.get(bundleId);
				targetedBundles.seller = bundle.getSeller(); //every bundle has the same seller here 
				//targetedBundles.customer = bundle.getCustomer(); //every bundle has the same customer here 
				targetedBundles.expectedPaymentInMarketCurrency = targetedBundles.expectedPaymentInMarketCurrency.
					add(bundle.getCustomerShippingCostInMarketCurrencyInclVat());
				targetedBundles.targetedPaidInMarketCurrency = targetedBundles.targetedPaidInMarketCurrency.
					add(bundle.getTargetedPaidShippingCostInMarketCurrency());
				targetedBundles.bundles.add(bundle);
			}
			retMap.put(id,targetedBundles);
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

	public void setTargetedPaidInMarketCurrency(
			BigDecimal targetedPaidInMarketCurrency) {
		this.targetedPaidInMarketCurrency = targetedPaidInMarketCurrency;
	}

	public BigDecimal getTargetedPaidInMarketCurrency() {
		return targetedPaidInMarketCurrency;
	}

	public void setBundles(List<OrderBundle> bundles) {
		this.bundles = bundles;
	}

	public List<OrderBundle> getBundles() {
		return bundles;
	}

	public void setSeller(Seller seller) {
		this.seller = seller;
	}

	public Seller getSeller() {
		return seller;
	}

}
