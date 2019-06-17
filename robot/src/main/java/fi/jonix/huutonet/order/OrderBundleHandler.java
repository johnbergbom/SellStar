package fi.jonix.huutonet.order;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import fi.jonix.huutonet.domain.VirtualCoin;
import fi.jonix.huutonet.domain.model.Currency;
import fi.jonix.huutonet.domain.model.Customer;
import fi.jonix.huutonet.domain.model.Order;
import fi.jonix.huutonet.domain.model.OrderBundle;
import fi.jonix.huutonet.domain.model.Product;
import fi.jonix.huutonet.domain.model.Provider;
import fi.jonix.huutonet.domain.model.Seller;
import fi.jonix.huutonet.domain.model.dao.CurrencyDAO;
import fi.jonix.huutonet.domain.model.dao.CustomerDAO;
import fi.jonix.huutonet.domain.model.dao.MessageDAO;
import fi.jonix.huutonet.domain.model.dao.OrderBundleDAO;
import fi.jonix.huutonet.domain.model.dao.OrderDAO;
import fi.jonix.huutonet.email.EmailSender;
import fi.jonix.huutonet.exception.ProcessEmailLaterException;
import fi.jonix.huutonet.exception.ProductNotFoundAtProviderException;
import fi.jonix.huutonet.exception.ProviderTemporarilyDownException;
import fi.jonix.huutonet.exception.SoldOutAtProviderException;
import fi.jonix.huutonet.order.ContactInfoChecker.AddressFault;
import fi.jonix.huutonet.order.ProviderHandler.ProductInfo;
import fi.jonix.huutonet.tools.MathUtilities;
import fi.jonix.huutonet.tools.StringUtilities;

@Component(value = "orderBundleHandler")
public class OrderBundleHandler {

	public static final Logger logger = Logger.getLogger(OrderBundleHandler.class);
	
	@Autowired
	private OrderDAO orderDAO;
	
	@Autowired
	private OrderBundleDAO orderBundleDAO;
	
	@Autowired
	private CustomerDAO customerDAO;
	
	@Autowired
	private MessageDAO messageDAO;

	@Autowired
	private OrderCancelationHandler orderCancelationHandler;
	
	@Autowired
	private CurrencyDAO currencyDAO;
	
	@Autowired
	private ProviderHandler providerHandler;
	
	private boolean sloppyMatch(String str1, String str2) {
		str1 = StringUtilities.toUSASCIICharacters(str1.replaceAll(" ",""));
		str2 = StringUtilities.toUSASCIICharacters(str2.replaceAll(" ",""));
		return str1.equalsIgnoreCase(str2);
	}
	
	private boolean mergeable(OrderBundle fromBundle, OrderBundle toBundle) {
		/* Make sure that the order's of fromBundle are in states suitable for bundling. */
		List<Order> bundleOrders = orderDAO.getOrdersInBundle(fromBundle);
		BigDecimal totalSalesPriceExclVat = BigDecimal.ZERO;
		Currency usedCustomerCurrency = fromBundle.getUsedCustomerCurrency();
		for (Order bundleOrder : bundleOrders) {
			/* Only count the price of non-canceled orders when calculating the total price. */
			if (!bundleOrder.getState().equals(Order.STATE_CLOSED_ORDER_CANCELED)) {
				totalSalesPriceExclVat = totalSalesPriceExclVat.add(bundleOrder.getFinalSalesPriceInMarketCurrencyExclVat());
			}
			if (!bundleOrder.getTryToBundle()) {
				logger.debug("Bundle " + fromBundle.getId() + " cannot be merged because it has order having tryToBundle = false.");
				return false;
			} else if (bundleOrder.getState().equals(Order.STATE_SHOULD_RESEND)
					|| (bundleOrder.getState() >= Order.STATE_ORDERED_FROM_PROVIDER_NO_ID
							&& !bundleOrder.getState().equals(Order.STATE_CLOSED_ORDER_CANCELED))) {
				logger.debug("Bundle " + fromBundle.getId() + " cannot be merged because it has order in the wrong state.");
				return false;
			}
		}
		VirtualCoin shippingCostExclVatMC = new VirtualCoin(usedCustomerCurrency,fromBundle.getCustomerShippingCostInMarketCurrencyExclVat());
		VirtualCoin totalSalesPriceExclVatMC = new VirtualCoin(usedCustomerCurrency,totalSalesPriceExclVat);
		if (!bundlable(toBundle, totalSalesPriceExclVatMC, shippingCostExclVatMC,
				fromBundle.getCustomer(), fromBundle.getProvider(),fromBundle.getSeller(),
				fromBundle.getFirstName(), fromBundle.getLastName(), fromBundle.getAddress(),
				fromBundle.getPostcode(), fromBundle.getCity(),usedCustomerCurrency,
				fromBundle.getMaxBundleSizeInMarketCurrencyExclVat())) {
			return false;
		}
		
		logger.debug("Bundle " + fromBundle.getId() + " can be merged with bundle " + toBundle.getId() + ".");
		return true;
	}
	
	/**
	 * @param totalSalesPrice This is the price INCLUDING shipping costs.
	 */
	private boolean bundlable(OrderBundle candidateBundle, VirtualCoin totalSalesPriceExclVatMC, VirtualCoin shippingCostExclVatMC,
			Customer customer, Provider provider, Seller seller, String firstName, String lastName, String address,
			String postcode, String city, Currency usedCustomerCurrency, BigDecimal maxBundleSizeExclVatMC) {
		if (!candidateBundle.getCustomer().equals(customer) || !candidateBundle.getProvider().equals(provider)
				|| !candidateBundle.getSeller().equals(seller)) {
			logger.debug("Candidate bundle " + candidateBundle.getId()
					+ " is not suitable because it has the wrong customer, provider or seller.");
			return false;
		}
		
		if (!provider.getBundleSupport()) {
			logger.debug("Candidate bundle " + candidateBundle.getId() + " is not suitable because it doesn't have bundle support.");
		}

		/* Names and addresses are specified by hand by the customer, and as such there can be inconsistencies.
		 * Here we use heuristics in an attempt to deal with those inconsistencies. For example the following
		 * two addresses SHOULD be considered equal: "  Eteläesplanaadi  1    a" and "ETELAESPLANAADI 1A".
		 * Sometimes the customer might have the first and last name switched around, so account also for
		 * that. */
		boolean namesMatch = false;
		if ((sloppyMatch(candidateBundle.getFirstName(),firstName) && sloppyMatch(candidateBundle.getLastName(),lastName))
				|| (sloppyMatch(candidateBundle.getFirstName(),lastName) && sloppyMatch(candidateBundle.getLastName(),firstName))) {
			namesMatch = true;
		}
		if (!namesMatch
				|| !sloppyMatch(candidateBundle.getAddress(),address) || !sloppyMatch(candidateBundle.getPostcode(),postcode)
				|| !sloppyMatch(candidateBundle.getCity(),city)) {
			logger.debug("Candidate bundle " + candidateBundle.getId() + " is not suitable because it has a different address.");
			return false;
		}
		
		/* Make sure that the order's of the bundle are in states suitable for bundling. */
		List<Order> bundleOrders = orderDAO.getOrdersInBundle(candidateBundle);
		if (bundleOrders.size() == 0) {
			throw new RuntimeException("Bundle " + candidateBundle.getId() + " has no orders.");
		}
		BigDecimal candidateTotalPriceExclVatMC = BigDecimal.ZERO;
		boolean hasSuitableOrder = false;
		Currency candidateCustomerCurrency = candidateBundle.getUsedCustomerCurrency();
		for (Order bundleOrder : bundleOrders) {
			/* Only count the price of non-canceled orders when calculating the total price. */
			if (!bundleOrder.getState().equals(Order.STATE_CLOSED_ORDER_CANCELED)) {
				candidateTotalPriceExclVatMC = candidateTotalPriceExclVatMC.add(bundleOrder.getFinalSalesPriceInMarketCurrencyExclVat());
			}
			if (!bundleOrder.getTryToBundle()) {
				logger.debug("Candidate bundle " + candidateBundle.getId() + " is not suitable because it has order having tryToBundle = false.");
				return false;
			} else if (bundleOrder.getState().equals(Order.STATE_SHOULD_RESEND)
					|| (bundleOrder.getState() >= Order.STATE_ORDERED_FROM_PROVIDER_NO_ID
							&& !bundleOrder.getState().equals(Order.STATE_CLOSED_ORDER_CANCELED))) {
				logger.debug("Candidate bundle " + candidateBundle.getId() + " is not suitable because it has order in the wrong state.");
				return false;
			} else if (bundleOrder.getState().intValue() < Order.STATE_CUSTOMER_PAID_INFORMED/*Order.STATE_ORDERED_FROM_PROVIDER_NO_ID*/) {
				hasSuitableOrder = true;
			}
		}
		if (!candidateCustomerCurrency.getId().equals(usedCustomerCurrency.getId())) {
			logger.debug("Candidate bundle " + candidateBundle.getId() + " is not suitable because it has a different "
					+ " customer currency (" + candidateCustomerCurrency.getId() + ").");
			return false;
		}
		if (!hasSuitableOrder) {
			logger.debug("Candidate bundle " + candidateBundle.getId() + " is not suitable because it has no"
					+ " orders in a suitable state.");
			return false;
		}
		
		/* Add the highest shipping cost of the order and the bundle to the totalPrice. It's possible that the bundles have different customer
		 * vat percentages for shipping so that in theory it could be that bundle1.shippingInclVat > bundle2.shippingInclVat and at the same
		 * time bundle1.shippingExclVat < bundle2.shippingExclVat. However that is of no significance here because when checking for the
		 * maximum bundle size we only use prices excluding vat. */
		if (candidateBundle.getCustomerShippingCostInMarketCurrencyExclVat().compareTo(shippingCostExclVatMC.getValue()) > 0) {
			candidateTotalPriceExclVatMC = candidateTotalPriceExclVatMC.add(candidateBundle.getCustomerShippingCostInMarketCurrencyExclVat());
		} else {
			candidateTotalPriceExclVatMC = candidateTotalPriceExclVatMC.add(shippingCostExclVatMC.getValue());
		}
		
		/* Make sure that the total value of the orders in the candidate bundle + the
		 * value of the order doesn't exceed the maximum bundle size. */
		if (candidateTotalPriceExclVatMC.add(totalSalesPriceExclVatMC.getValue()).compareTo(candidateBundle.getMaxBundleSizeInMarketCurrencyExclVat()) > 0
				|| candidateTotalPriceExclVatMC.add(totalSalesPriceExclVatMC.getValue()).compareTo(maxBundleSizeExclVatMC) > 0) {
			logger.debug("Candidate bundle " + candidateBundle.getId() + " is not suitable because it's too big.");
			return false;
		}
		
		logger.debug("Candidate bundle " + candidateBundle.getId() + " is suitable for bundling.");
		return true;
	}

	public OrderBundle getBundleForNewOrder(String firstName, String lastName, String address, String postCode, String city,
			Customer customer, Provider provider, Seller seller, Order order, VirtualCoin shippingCostInclVatMC,
			Currency usedCustomerCurrency, BigDecimal maxBundleSizeExclVatMC) {
		/* Trim customer information strings. */
		if (firstName != null) {
			firstName = firstName.trim();
		}
		if (lastName != null) {
			lastName = lastName.trim();
		}
		if (address != null) {
			address = address.trim();
		}
		if (postCode != null) {
			postCode = postCode.trim();
		}
		if (city != null) {
			city = city.trim();
		}

		VirtualCoin shippingCostExclVatMC = new VirtualCoin(usedCustomerCurrency,
				MathUtilities.getPriceExclVat(shippingCostInclVatMC.getValue(), seller.getCustomerVatPercentage()));
		OrderBundle bundle = null;
		if (order.getTryToBundle() && provider.getBundleSupport()) {
			/* See if there is some existing bundle with which this order can be bundled. */
			logger.debug("See if new order can be bundled.");
			List<OrderBundle> possibleBundles = orderBundleDAO.getOrderBundles(customer, provider, seller);
			for (OrderBundle candidateBundle : possibleBundles) {
				VirtualCoin totalSalesPriceExclVatMC = new VirtualCoin(usedCustomerCurrency,order.getFinalSalesPriceInMarketCurrencyExclVat());
				if (bundlable(candidateBundle, totalSalesPriceExclVatMC, shippingCostExclVatMC, customer,
						provider, seller, firstName, lastName, address, postCode, city, usedCustomerCurrency, maxBundleSizeExclVatMC)) {
					bundle = candidateBundle;
					break;
				}
			}
		}
		
		if (bundle == null) {
			/* Create a new bundle of no suitable bundle was found. */
			logger.debug("No suitable order found for bundling, creating a new bundle.");
			bundle = new OrderBundle();
			bundle.setFirstName(firstName);
			bundle.setLastName(lastName);
			bundle.setAddress(address);
			bundle.setPostcode(postCode);
			bundle.setCity(city);
			bundle.setCustomer(customer);
			bundle.setProvider(provider);
			bundle.setSeller(seller);
			bundle.setLastCustomerUpdateSent(new Date());
			bundle.setTargetedPaidShippingCostInMarketCurrency(BigDecimal.ZERO);
			bundle.setCustomerShippingCostInMarketCurrencyInclVat(shippingCostInclVatMC.getValue());
			bundle.setCustomerShippingCostInMarketCurrencyExclVat(shippingCostExclVatMC.getValue());
			BigDecimal shippingCostEuroInclVat = currencyDAO.convertCurrency(shippingCostInclVatMC.getCurrency(), Currency.EURO, shippingCostInclVatMC.getValue());
			BigDecimal shippingCostEuroExclVat = currencyDAO.convertCurrency(shippingCostInclVatMC.getCurrency(), Currency.EURO, shippingCostExclVatMC.getValue());
			bundle.setCustomerShippingCostInEuroInclVat(shippingCostEuroInclVat);
			bundle.setCustomerShippingCostInEuroExclVat(shippingCostEuroExclVat);
			bundle.setResendLossInEuroInclVat(BigDecimal.ZERO);
			bundle.setResendLossInEuroExclVat(BigDecimal.ZERO);
			bundle.setUsedProviderCurrency(provider.getCurrency());
			bundle.setUsedCustomerCurrency(usedCustomerCurrency);
			bundle.setMaxBundleSizeInMarketCurrencyExclVat(maxBundleSizeExclVatMC);
			bundle.setCustomerShippingVatPercentage(seller.getCustomerVatPercentage());
			bundle.setTransferedToVerification(false);
			bundle.setOldVerification(false);
			orderBundleDAO.save(bundle);
			order.setOrderBundle(bundle);
		} else {
			addOrderToBundle(order,bundle,shippingCostInclVatMC);
		}
		return bundle;
	}
	
	private OrderBundle tryToMergeBundle(OrderBundle bundle) {
		if (bundle.getProvider().getBundleSupport()) {
			/* See if there is some existing bundle with which this order can be bundled. */
			logger.debug("See if new order can be bundled.");
			List<OrderBundle> possibleBundles = orderBundleDAO.getOrderBundles(bundle.getCustomer(), bundle.getProvider(), bundle.getSeller());
			OrderBundle toBundle = null;
			for (OrderBundle candidateBundle : possibleBundles) {
				if (!candidateBundle.getId().equals(bundle.getId()) && mergeable(bundle, candidateBundle)) {
					toBundle = candidateBundle;
					break;
				}
			}
			
			if (toBundle != null) {
				/* Move possible targetedPayments from the old bundle to some order. */
				List<Order> bundleOrders = orderDAO.getOrdersInBundle(bundle);
				BigDecimal oldBundleTargetedPaid = bundle.getTargetedPaidShippingCostInMarketCurrency();
				if (oldBundleTargetedPaid.compareTo(BigDecimal.ZERO) > 0) {
					Order order = bundleOrders.get(0);
					logger.debug("Moving targetedPaid " + oldBundleTargetedPaid + " from bundle " + bundle.getId()
							+ " to bundle " + toBundle.getId() + " (order " + order.getId() + ").");
					order.setTargetedPaidInMarketCurrency(order.getTargetedPaidInMarketCurrency().add(oldBundleTargetedPaid));
					bundle.setTargetedPaidShippingCostInMarketCurrency(BigDecimal.ZERO);
				}
				
				/* Merge the bundles. */
				for (Order bundleOrder : bundleOrders) {
					logger.debug("Moving order " + bundleOrder.getId() + " from bundle "
							+ bundle.getId() + " to bundle " + toBundle.getId());
					VirtualCoin shippingCostInclVatMC = new VirtualCoin(bundle.getUsedCustomerCurrency(),bundle.getCustomerShippingCostInMarketCurrencyInclVat());
					addOrderToBundle(bundleOrder,toBundle,shippingCostInclVatMC);
					orderDAO.update(bundleOrder);
					orderDAO.flush();
					orderDAO.refresh(bundleOrder);
				}
				
				/* Remove the old empty bundle. */
				logger.debug("Removing empty bundle " + bundle.getId() + ".");
				orderBundleDAO.delete(bundle);
				orderBundleDAO.flush();
				return toBundle;
			}
		}
		return bundle;
	}
	
	
	/* This method adds an order to a bundle and adjusts the shipping costs if necessary (we should only pay for
	 * shipping for one product in the bundle).
	 * 
	 * Regarding vat handling (these rules were verified with our accountancy company):
	 * Normally vat percentages change quite rarely, so in the normal case
	 * seller.customerVatPercentage is the same as bundle.customerShippingVatPercentage. If however
	 * vat would change from 23% to 24% on 2012-01-01 and the bill was sent to the customer before
	 * the vat changed but he pays after the change has gone into effect, then that's alright because
	 * the date of the bill determines that vat to be used.
	 * 
	 * If the customer added another order to the bundle after the vat change went into effect, then
	 * the vat of the new _order_ should be 24% but the vat of the shipping can still stay at 23%
	 * because the _shipment_ was ordered before the vat change.
	 * 
	 * However SellStar adds another twist to this: always use the shipping cost that has the highest
	 * value EXCLUDING vat. We can think of it this way: if the old bundle has lower shipping cost
	 * excluding vat, then the old "shipment order" was replaced with a new one. If instead the old
	 * bundle has a higher shipping cost excluding vat, then the old "shipment order" is still in
	 * effect. */
	private void addOrderToBundle(Order order, OrderBundle bundle, VirtualCoin newShippingCostInclVatMC) {
		if (bundle.getCustomerShippingCostInMarketCurrencyInclVat() == null
				|| bundle.getCustomerShippingCostInMarketCurrencyInclVat().compareTo(BigDecimal.ZERO) <= 0
				|| bundle.getCustomerShippingCostInMarketCurrencyExclVat() == null
				|| bundle.getCustomerShippingCostInMarketCurrencyExclVat().compareTo(BigDecimal.ZERO) <= 0
				|| order.getState() == null) {
			throw new RuntimeException("Unexpected: bundle " + bundle.getId() + " to whom order is to be added doesn't have"
					+ " a shipping cost defined, or else state is null.");
		}
		
		/* Calculate the price of shipping cost excluding vat for the product to be added to the bundle. If the
		 * order is already part of another bundle (i.e. the order is moved to a new bundle), then use the
		 * customerShippingVatPercentage of the old bundle. Otherwise (if this is a brand new order that's
		 * included in this bundle), then use the customerShippingVatPercentage of the seller. (The shipping
		 * vat could be different for the old bundle and for the seller in case the vat percentage has changed.) */
		BigDecimal shippingVatForNewOrder = null;
		if (order.getOrderBundle() != null) {
			shippingVatForNewOrder = order.getOrderBundle().getCustomerShippingVatPercentage();
		} else {
			shippingVatForNewOrder = bundle.getSeller().getCustomerVatPercentage();
		}
		BigDecimal newShippingCostExclVatMC = MathUtilities.getPriceExclVat(newShippingCostInclVatMC.getValue(), shippingVatForNewOrder);

		/* Set the shipping cost of the bundle to the largest value of the old and the
		 * new value EXCLUDING vat. */
		if (newShippingCostExclVatMC.compareTo(bundle.getCustomerShippingCostInMarketCurrencyExclVat()) > 0) {
			bundle.setCustomerShippingCostInMarketCurrencyInclVat(newShippingCostInclVatMC.getValue());
			bundle.setCustomerShippingCostInMarketCurrencyExclVat(newShippingCostExclVatMC);
			BigDecimal newShippingCostInclVatEuro = currencyDAO.convertCurrency(newShippingCostInclVatMC.getCurrency(), Currency.EURO, newShippingCostInclVatMC.getValue());
			BigDecimal newShippingCostExclVatEuro = currencyDAO.convertCurrency(newShippingCostInclVatMC.getCurrency(), Currency.EURO, newShippingCostExclVatMC);
			bundle.setCustomerShippingCostInEuroInclVat(newShippingCostInclVatEuro);
			bundle.setCustomerShippingCostInEuroExclVat(newShippingCostExclVatEuro);
			bundle.setCustomerShippingVatPercentage(shippingVatForNewOrder);
		}
		
		/* Finally add the order to the bundle. */
		logger.debug("Adding order " + order.getId() + " to bundle " + bundle.getId() + ".");
		order.setOrderBundle(bundle);
	}
	
	/* This method updates the address of ONE order in the bundle (and thus splits the bundle
	 * if there are more than one orders in the bundle). If the address change fails it
	 * informs the customer. If the order was in state faulty address then this method checks
	 * if the state should be updated to "new out of stock" or to "new" by checking
	 * if the product is still available at the provider.*/
	public void updateAddressOfSingleOrderInBundle(OrderBundle bundle, Order order, Customer customer, String firstName,
			String lastName, String address, String postCode, String city, String email, boolean fromGoogleOrder) throws Exception {
		/* Emails to the customer should be sent to both the new and the old
		 * email address (if the email address was changed). */
		String addressToSendCustomerInfoTo = bundle.getCustomer().getEmail();
		if (customer == null && email != null && ContactInfoChecker.correctEmailAddress(email)) {
			addressToSendCustomerInfoTo += "," + email;
		}

		address = ContactInfoChecker.fixAddress(address);
		postCode = ContactInfoChecker.fixPostCode(postCode);
		if (!canUpdateAddressOfBundle(bundle,customer,firstName,lastName,
				address,postCode,city,email, addressToSendCustomerInfoTo,fromGoogleOrder)) {
			return;
		}
		
		if (customer == null) {
			/* Customer will only be null if the email address was changed. In this case don't create a new
			 * customer, but rather get the customer of the order in question and update its email address. */
			customer = bundle.getCustomer();
			customer.setEmail(email.toLowerCase()); //this only needs to be updated if customer was null above..
		}
		customerDAO.update(customer);
		List<Order> bundleOrders = orderDAO.getOrdersInBundle(bundle);
		int originalBundleSize = bundleOrders.size();
		
		/* Find an order in the old bundle which is different from the order whose
		 * address in changed (only applicable if the bundle consists of more than
		 * one order). */
		Order unchangedOrderInOldBundle = null;
		for (Order o : bundleOrders) {
			if (!o.equals(order)) {
				unchangedOrderInOldBundle = o;
			}
		}
		
		OrderBundle newBundle = null;
		if (originalBundleSize > 1) {
			/* If the bundle consists of more than one order, then this order needs
			 * to be split. Start by creating a new bundle. */
			newBundle = new OrderBundle();
			newBundle.setFirstName(firstName);
			newBundle.setLastName(lastName);
			newBundle.setAddress(address);
			newBundle.setPostcode(postCode);
			newBundle.setCity(city);
			newBundle.setCustomer(customer);
			newBundle.setProvider(bundle.getProvider());
			newBundle.setSeller(bundle.getSeller());
			newBundle.setLastCustomerUpdateSent(new Date());
			newBundle.setCustomerShippingCostInMarketCurrencyInclVat(bundle.getCustomerShippingCostInMarketCurrencyInclVat());
			BigDecimal shippingCostExclVatMC = MathUtilities.getPriceExclVat(bundle.getCustomerShippingCostInMarketCurrencyInclVat(), bundle.getSeller().getCustomerVatPercentage());
			newBundle.setCustomerShippingCostInMarketCurrencyExclVat(shippingCostExclVatMC);
			newBundle.setCustomerShippingCostInEuroInclVat(bundle.getCustomerShippingCostInEuroInclVat());
			BigDecimal shippingCostExclVatEuro = MathUtilities.getPriceExclVat(bundle.getCustomerShippingCostInEuroInclVat(), bundle.getSeller().getCustomerVatPercentage());
			newBundle.setCustomerShippingCostInEuroExclVat(shippingCostExclVatEuro);
			newBundle.setTargetedPaidShippingCostInMarketCurrency(BigDecimal.ZERO);
			newBundle.setResendLossInEuroInclVat(BigDecimal.ZERO);
			newBundle.setResendLossInEuroExclVat(BigDecimal.ZERO);
			newBundle.setUsedProviderCurrency(bundle.getUsedProviderCurrency());
			newBundle.setUsedCustomerCurrency(bundle.getUsedCustomerCurrency());
			newBundle.setMaxBundleSizeInMarketCurrencyExclVat(bundle.getMaxBundleSizeInMarketCurrencyExclVat());
			newBundle.setCustomerShippingVatPercentage(bundle.getSeller().getCustomerVatPercentage());
			newBundle.setTransferedToVerification(false);
			newBundle.setOldVerification(false);
			orderBundleDAO.save(newBundle);
			// Then add the order in question to the just created bundle.
			VirtualCoin shippingCostInclVatMC = new VirtualCoin(bundle.getUsedCustomerCurrency(),bundle.getCustomerShippingCostInMarketCurrencyInclVat());
			addOrderToBundle(order,newBundle,shippingCostInclVatMC);
			logger.debug("Order " + order.getId() + " moved to the new bundle " + newBundle.getId() + ".");
		} else {
			/* Just one order in the bundle, no splitting of bundle is necessary,
			 * simply update the existing bundle with the new address information. */
			bundle.setFirstName(firstName);
			bundle.setLastName(lastName);
			bundle.setAddress(address);
			bundle.setPostcode(postCode);
			bundle.setCity(city);
			newBundle = bundle;
		}
		
		/* See if the bundle can be merged with some other bundle. Note that
		 * this can happen both for the case where a multi-order bundle was
		 * split and for the case where a single-order bundle was updated. */
		boolean merged = false;
		Long newBundleId = newBundle.getId();
		newBundle = tryToMergeBundle(newBundle);
		if (!newBundleId.equals(newBundle.getId())) {
			merged = true;
			logger.debug("New bundle " + newBundleId + " merged into bundle " + newBundle.getId() + ".");
		} else if (originalBundleSize > 1) {
			//do nothing
		} else {
			logger.debug("Address information was updated for bundle " + bundle.getId()
					+ " (no splitting was necessary since the bundle consists of just one order,"
					+ " and no merging was possible).");
		}
		logger.debug("Reloading orders for bundle " + newBundle.getId() + ".");
		bundleOrders = orderDAO.getOrdersInBundle(newBundle);
		logger.debug("Number of orders in new bundle " + newBundle.getId() + ": " + bundleOrders.size());

		/* Special care is needed if the state of the order was "faulty contact info". */
		Order updatedOrder = null;
		if (order.getState().equals(Order.STATE_FAULTY_CONTACT_INFO)) {
			updateStateOfOrderFromFaultyContactInfo(order,newBundle,bundleOrders.size(),addressToSendCustomerInfoTo);
			updatedOrder = order;
		}

		/* If an order was updated from the state "faulty contact info", then we always need
		 * to send an email regarding that order, because the customer has never gotten
		 * any bill for orders in the state "faulty contact info". In addition to that
		 * we need to confirm the address change + send a new bundle bill to the customer
		 * in case the shipping cost changed because of the address change. Let's first
		 * figure out what emails we need to send. */
		List<String> billsToSend = new ArrayList<String>();
		String bundleInfo = null;
		if (merged && originalBundleSize > 1) {
			/* Shipping costs haven't changed. */
			if (!bundle.getId().equals(newBundle.getId())) {
				/* It's possible that the customer specified the same address as what we already
				 * had in the database and in that case bundle.id == newBundle.id (because the
				 * split was undone by the merge) and in that case we don't need to send any
				 * information about changed bundles. */
				bundleInfo = "Osoitepäivityksen yhteydessä tilausten niputukset muuttuivat seuraavasti:\n"
					+ "Tilaus " + order.getId() + " siirtyi nipusta " + bundle.getId() + " nippuun "
					+ newBundle.getId() + ".\nPostikulut pysyvät ennallaan.\n";
			}
			if (updatedOrder != null) {
				bundleInfo += "\nSaat erilliset sähköpostit jotka tarkemmin kuvaavat uudet niput.\n";
				billsToSend.add(OrderCreator.generatePaymentInformationEmailForBundle(bundle,unchangedOrderInOldBundle));
				billsToSend.add(OrderCreator.generatePaymentInformationEmailForBundle(newBundle,order));
			}
		} else if (merged && originalBundleSize == 1) {
			/* Shipping costs have decreased since a single order was merged into another bundle. */
			bundleInfo = "Osoitepäivityksen yhteydessä tilausten niputukset muuttuivat seuraavasti:\n"
				+ "Tilaus " + order.getId() + " siirtyi nippuun " + newBundle.getId() + ".\n"
				+ "Tämän nippujen yhdistämisen takia postikulut laskevat ja saat erillisen sähköpostin"
				+ " joka tarkemmin kuvaa uutta nippua.\n";
			billsToSend.add(OrderCreator.generatePaymentInformationEmailForBundle(newBundle,order));
		} else if (!merged && originalBundleSize > 1) {
			/* Shipping costs have risen since a bundle was split into two. */
			bundleInfo = "Osoitepäivityksen yhteydessä tilausten niputukset muuttuivat seuraavasti:\n"
				+ "Nippu " + bundle.getId() + " purettiin kahdeksi nipuksi (" + bundle.getId() + " ja "
				+ newBundle.getId() + "). Nippujen purkamisen takia postikulut\nnousevat koska pitää"
				+ " erikseen maksaa postikulut jokaisesta nipusta. Saat erilliset sähköpostit\njotka"
				+ " tarkemmin kuvaavat uudet niput.\n";
			billsToSend.add(OrderCreator.generatePaymentInformationEmailForBundle(bundle,unchangedOrderInOldBundle));
			billsToSend.add(OrderCreator.generatePaymentInformationEmailForBundle(newBundle,order));
		} else { // !merged && originalBundleSize == 1
			/* Shipping costs haven't changed. */
			bundleInfo = "";
			if (updatedOrder != null) {
				bundleInfo = "Saat erillisen sähköpostin joka tarkemmin kuvaa uutta nippua.\n";
				billsToSend.add(OrderCreator.generatePaymentInformationEmailForBundle(newBundle,order));
			}
		}
		
		/* Then send a confirmation regarding the address change. */
		String custEmail = "Moi,\n\nOsoitteenmuutos tehty onnistuneesti!\n\n"
			+ "Tilaus " + order.getId() + " lähetetään seuraavaan osoitteeseen:\n"
			+ firstName + " " + lastName + "\n"
			+ address + "\n"
			+ postCode + " " + city + "\n\n"
			+ bundleInfo
			+ "\nYstävällisin terveisin,\n" + bundle.getSeller().getSignature() + "\n";
		EmailSender.sendEmailBatch(bundle.getSeller().getSystemEmail(),addressToSendCustomerInfoTo,
				"Osoitteenmuutos onnistui (tilaus " + bundleOrders.get(0).getId() + ")",custEmail);
		
		/* Finally send information about changed bundles (if any). */
		for (String emailContents : billsToSend) {
			EmailSender.sendEmailBatch(bundle.getSeller().getSystemEmail(),
					customer.getEmail(),"Tilaus vastaanotettu",emailContents);
		}
	}
	
	private boolean canUpdateAddressOfBundle(OrderBundle bundle, Customer customer, String firstName,
			String lastName, String address, String postCode, String city, String email,
			String addressToSendCustomerInfoTo, boolean fromGoogleOrder) {
		List<AddressFault> contactInfoProblems = ContactInfoChecker.checkContactInfo(firstName, lastName,
				address, postCode, city, email, fromGoogleOrder);
		List<Order> bundleOrders = orderDAO.getOrdersInBundle(bundle);
		logger.debug("Number of orders in bundle " + bundle.getId() + ": " + bundleOrders.size());
		String bundleInfo = null;
		String bundleInfoBrief = null;
		if (bundleOrders.size() < 2) {
			bundleInfo = "Tilausnumero: " + bundleOrders.get(0).getId() + " (viitenumero: "
				+ bundleOrders.get(0).getCustomerReferenceNumber() + ")";
			bundleInfoBrief = "tilaus " + bundleOrders.get(0).getId();
		} else {
			bundleInfo = "Nippunumero: " + bundle.getId();
			bundleInfoBrief = "nippu " + bundle.getId();
		}
		if (contactInfoProblems.size() > 0) {
			String reason = "";
			for (AddressFault addressFault : contactInfoProblems) {
				reason += messageDAO.getText(addressFault.getField(), bundle.getSeller(),
						bundleOrders.get(0).getMarket().getLanguage());
				reason += ": " + messageDAO.getText(addressFault.getError(), bundle.getSeller(),
						bundleOrders.get(0).getMarket().getLanguage()) + "\n";
			}
			String errorEmail = "Moi,\n\nOsoitteenmuutos epäonnistui.\n\nSYY:\n" + reason + "\n"
				+ bundleInfo + "\n"
				+ ContactInfoChecker.formatContactInfo(firstName, lastName, address, postCode, city,email) + "\n"
				+ "Mikäli olet varma siitä, että osoite on oikein, niin ole hyvä ja ota yhteyttä meihin sähköpostitse.\n\n"
				+ "Paketti lähetetään seuraavaan osoitteeseen:\n"
				+ bundle.getFirstName() + " " + bundle.getLastName() + "\n"
				+ bundle.getAddress() + "\n"
				+ bundle.getPostcode() + "\n"
				+ bundle.getCity() + "\n"
				+ "\nYstävällisin terveisin,\n" + bundle.getSeller().getSignature() + "\n";
			EmailSender.sendEmailBatch(bundle.getSeller().getSystemEmail(),
					addressToSendCustomerInfoTo,"Osoitteenmuutos epäonnistui (" + bundleInfoBrief + ")",errorEmail);
			return false;
		} else if (bundleOrders.get(0).getState() >= Order.STATE_ORDERED_FROM_PROVIDER_NO_ID) {
			String errorEmail = "Moi,\n\nOsoitteenmuutos epäonnistui.\n\nSYY:\ntiedot osoitteenmuutoksesta tulivat liian myöhään.\n\n"
				+ "Paketti lähetetään seuraavaan osoitteeseen:\n"
				+ bundle.getFirstName() + " " + bundle.getLastName() + "\n"
				+ bundle.getAddress() + "\n"
				+ bundle.getPostcode() + "\n"
				+ bundle.getCity() + "\n"
				+ "\nYstävällisin terveisin,\n" + bundle.getSeller().getSignature() + "\n";
			EmailSender.sendEmailBatch(bundle.getSeller().getSystemEmail(),
					addressToSendCustomerInfoTo,"Osoitteenmuutos epäonnistui (" + bundleInfoBrief + ")",errorEmail);
			return false;
		}
		return true;
	}
	
	/* This method updates the address of the bundle if possible, or else it sends an email to the
	 * customer that the address update failed (fails if the address is faulty or if the state
	 * of some order in the bundle is faulty). If some order was in state faulty address then this
	 * method checks if the state should be updated to "new out of stock" or to "new" by checking
	 * if the product is still available at the provider. It also merges this bundle into some
	 * other bundle if the address change makes this possible. */
	public void updateAddressOfBundle(OrderBundle bundle, Customer customer, String firstName,
			String lastName, String address, String postCode, String city, String email, boolean fromGoogleOrder) throws Exception {
		/* Emails to the customer should be sent to both the new and the old
		 * email address (if the email address was changed). */
		String addressToSendCustomerInfoTo = bundle.getCustomer().getEmail();
		if (customer == null && email != null && ContactInfoChecker.correctEmailAddress(email)) {
			addressToSendCustomerInfoTo += "," + email;
		}

		address = ContactInfoChecker.fixAddress(address);
		postCode = ContactInfoChecker.fixPostCode(postCode);
		if (!canUpdateAddressOfBundle(bundle,customer,firstName,lastName,
				address,postCode,city,email, addressToSendCustomerInfoTo,fromGoogleOrder)) {
			return;
		}
		
		if (customer == null) {
			/* Customer will only be null if the email address was changed. In this case don't create a new
			 * customer, but rather get the customer of the order in question and update its email address. */
			customer = bundle.getCustomer();
			customer.setEmail(email.toLowerCase()); //this only needs to be updated if customer was null above..
		}
		customerDAO.update(customer);
		bundle.setFirstName(firstName);
		bundle.setLastName(lastName);
		bundle.setAddress(address);
		bundle.setPostcode(postCode);
		bundle.setCity(city);
		OrderBundle newBundle = tryToMergeBundle(bundle);
		List<Order> bundleOrders = null;
		Order updatedOrder = null;
		if (!bundle.getId().equals(newBundle.getId())) {
			/* Shipping costs have decreased since the bundle was merged into another bundle. */
			logger.debug("Bundle " + bundle.getId() + " merged into bundle " + newBundle.getId() + ". Reloading orders.");
			bundleOrders = orderDAO.getOrdersInBundle(newBundle);
			updatedOrder = bundleOrders.get(0);
			logger.debug("Number of orders in new bundle " + newBundle.getId() + ": " + bundleOrders.size());
			String custEmail = "Moi,\n\nVanha nippu " + bundle.getId() + " yhdistettiin nippuun " + newBundle.getId() + ".\n"
				+ "Nippu " + bundle.getId() + " ei ole enää olemassa.\n\n"
				+ "Tämän nippujen yhdistämisen takia postikulut laskevat ja saat erillisen\nsähköpostin"
				+ " joka tarkemmin kuvaa uutta nippua.\n\n"
				+ "Ystävällisin terveisin,\n" + newBundle.getSeller().getSignature() + "\n";
			EmailSender.sendEmailBatch(newBundle.getSeller().getSystemEmail(),addressToSendCustomerInfoTo,
					"Nippujen yhdistäminen",custEmail);
		} else {
			bundleOrders = orderDAO.getOrdersInBundle(newBundle);
		}
		for (Order singleOrder : bundleOrders) {
			if (singleOrder.getState().equals(Order.STATE_FAULTY_CONTACT_INFO)) {
				updateStateOfOrderFromFaultyContactInfo(singleOrder,newBundle,bundleOrders.size(),addressToSendCustomerInfoTo);
				updatedOrder = singleOrder;
			} else {
				/* Otherwise do nothing, i.e. if state is new, then it should stay in that state,
				 * and same thing if the state is new out of stock. */
			}
		}
		
		/* Send email acknowledging the address change. */
		String bundleInfo = null;
		String bundleInfoBrief = null;
		String extraBundleInfo = "";
		if (bundleOrders.size() > 1) {
			bundleInfo = "Nippunumero " + newBundle.getId();
			bundleInfoBrief = "nippu " + newBundle.getId();
			extraBundleInfo = "\nTämä osoitteenmuutos tehtiin koko nippuun. Mikäli olisit sen sijaan"
				+ " halunnut muuttaa vaan yksittäisen tilauksen osoitetiedot, niin ota yhteyttä meihin"
				+ " sähköpostitse.\n";
		} else {
			bundleInfo = "Tilausnumero " + bundleOrders.get(0).getId() + " (viitenumero: "
				+ bundleOrders.get(0).getCustomerReferenceNumber() + ")";
			bundleInfoBrief = "tilaus " + bundleOrders.get(0).getId();
		}
		String custEmail = "Moi,\n\nOsoitteenmuutos tehty onnistuneesti!\n\n"
			+ bundleInfo + " lähetetään seuraavaan osoitteeseen:\n"
			+ firstName + " " + lastName + "\n"
			+ address + "\n"
			+ postCode + " " + city + "\n"
			+ extraBundleInfo
			+ "\nYstävällisin terveisin,\n" + newBundle.getSeller().getSignature() + "\n";
		EmailSender.sendEmailBatch(newBundle.getSeller().getSystemEmail(),addressToSendCustomerInfoTo,
				"Osoitteenmuutos onnistui (" + bundleInfoBrief + ")",custEmail);

		/* Send payment information in case the bundle was merged into another one (=shipping cost decreased)
		 * or if some order had previously been in the "faulty contact info" state. */
		if (updatedOrder != null) {
			/* Send payment information. */
			EmailSender.sendEmailBatch(newBundle.getSeller().getSystemEmail(),
					customer.getEmail(),"Tilaus vastaanotettu",
					OrderCreator.generatePaymentInformationEmailForBundle(newBundle,updatedOrder));
		}
	}
	
	private void updateStateOfOrderFromFaultyContactInfo(Order order, OrderBundle bundle, int bundleSize, String email) throws Exception {
		boolean outOfStock = false;
		ProductInfo prodInfo = providerHandler.getProductInfo(order.getProduct(), true);
		if (prodInfo.getProductState().equals(Product.STATE_UNKNOWN)) {
			logger.warn("Provider temporarily down, or else the product is no longer for sale at provider, but the not found counter isn't yet great enough.");
			throw new ProcessEmailLaterException("Address change request received from google-order regarding order that's in state 5,"
					+ " but the price cannot be determined because provider is temporarily down (or possibly no longer for sale).");
		} else if (prodInfo.getProductState().equals(Product.STATE_NO_LONGER_FOR_SALE)) {
			if (bundleSize > 1) {
				throw new RuntimeException("Not yet implemented: address change of bundle which has several orders"
						+ " of whom some is no longer for sale at provider.");
			} else {
				VirtualCoin refundSumMC = orderCancelationHandler.zeroSalesPriceForRefund(order,new HashMap<String,VirtualCoin>());
				if (refundSumMC.getValue().compareTo(BigDecimal.ZERO) > 0) {
					throw new RuntimeException("Unexpected: order " + order.getId() + " is in state \"faulty contact info\""
							+ " and then we discovered that the product is no longer found at the provider, but still"
							+ " it has a non-zero shipping cost that should be refunded. Is this because somehow"
							+ " faultyPaymentFixer has moved money to this one? Virheellisesti? If this is actually"
							+ " a correct case that can sometimes happen, then here we need to send a refund message"
							+ " to the customer.");
				}
				String marketSalesId = order.getAd().getMarketSalesId();
				String notForSaleEmail = null;
				if (order.getGoogleOrder()) {
					notForSaleEmail = messageDAO.getText("noLongerForSale", bundle.getSeller(), order.getMarket().getLanguage(),
							"MARKET_SALES_ID",marketSalesId,
							"AD_HEADLINE",order.getAd().getAdTemplate().getHeadline(),
							"SIGNATURE",bundle.getSeller().getSignature());
				} else {
					notForSaleEmail = messageDAO.getText("noLongerForSalePurchaseFromMarket", bundle.getSeller(), order.getMarket().getLanguage(),
							"MARKET_SALES_ID",marketSalesId,
							"AD_HEADLINE",order.getAd().getAdTemplate().getHeadline(),
							"SIGNATURE",bundle.getSeller().getSignature());
				}
				EmailSender.sendEmailBatch(bundle.getSeller().getSystemEmail(),email,"Tuote ei ole enää myynnissä",notForSaleEmail);
				order.setStateWithHistory(Order.STATE_NO_LONGER_FOR_SALE_AT_PROVIDER_EARLY);
				order.setStateWithHistory(Order.STATE_CLOSED_ORDER_CANCELED);
				return;
			}
		} else if (prodInfo.getProductState().equals(Product.STATE_OUT_OF_STOCK)) {
			logger.warn("Sold out.");
			outOfStock = true;
		}
		if (outOfStock) {
			order.setStateWithHistory(Order.STATE_NEW_OUT_OF_STOCK);
		} else {
			order.setStateWithHistory(Order.STATE_NEW);
		}
	}
	
}
