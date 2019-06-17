package fi.jonix.huutonet.order;

import java.io.File;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import fi.jonix.huutonet.domain.ApplicationContextPlaceholder;
import fi.jonix.huutonet.domain.VirtualCoin;
import fi.jonix.huutonet.domain.model.AdTemplate;
import fi.jonix.huutonet.domain.model.Currency;
import fi.jonix.huutonet.domain.model.Order;
import fi.jonix.huutonet.domain.model.OrderBundle;
import fi.jonix.huutonet.domain.model.Product;
import fi.jonix.huutonet.domain.model.Provider;
import fi.jonix.huutonet.domain.model.ProviderQuestion;
import fi.jonix.huutonet.domain.model.StateChange;
import fi.jonix.huutonet.domain.model.dao.CurrencyDAO;
import fi.jonix.huutonet.domain.model.dao.MessageDAO;
import fi.jonix.huutonet.email.EmailSender;
import fi.jonix.huutonet.exception.CriticalSectionInfo;
import fi.jonix.huutonet.exception.PayPalCaptchaException;
import fi.jonix.huutonet.exception.PlaceOrderException;
import fi.jonix.huutonet.exception.ProductNotFoundAtProviderException;
import fi.jonix.huutonet.exception.ProviderTemporarilyDownException;
import fi.jonix.huutonet.exception.SoldOutAtProviderException;
import fi.jonix.huutonet.tools.MathUtilities;

import org.apache.log4j.Logger;

public abstract class ProviderOrder {
	
	public static final Logger logger = Logger.getLogger(ProviderOrder.class);
	
	public static Double MIN_MULTIPLIER = 1.05;
	
	public abstract boolean canHandleProvider(Provider provider);
	
	public abstract Price getPrice(Product product, boolean sloppyStockChecking)
		throws SoldOutAtProviderException, ProductNotFoundAtProviderException, ProviderTemporarilyDownException;
	
	public abstract void loadProductPage(String providerProductId);
	
	public abstract void loadProductPage(String providerProductId, int retries, String timeout);

	public abstract void placeOrder(OrderBundle bundle, List<Order> bundleOrders, String user, String password, CriticalSectionInfo section)
		throws PlaceOrderException, ProviderTemporarilyDownException, SoldOutAtProviderException, PayPalCaptchaException, ProductNotFoundAtProviderException;
	
	public abstract boolean cancelOrder(Order order) throws Exception;

	public abstract void updateOrderStatus(OrderBundle bundle, List<Order> bundleOrders, CriticalSectionInfo section) throws Exception;

	public abstract Product createProduct(String providerProductId) throws Exception;

	public abstract boolean isProductInStock(Product product, boolean sloppyStockChecking) throws ProductNotFoundAtProviderException, ProviderTemporarilyDownException;
	
	public abstract void productExistsAtProvider(Product product) throws ProductNotFoundAtProviderException, ProviderTemporarilyDownException;
	
	public abstract List<String> getGoodProductIds(int count, VirtualCoin minPriceExclVat, VirtualCoin maxPriceExclVat, BigDecimal percentsOfProductsInSameCategory) throws Exception;
	
	public abstract void downloadImages(Product product, File basePath) throws Exception;

	public abstract void updateTechnicalSpecs(Product product) throws Exception;
	
	public abstract ProviderQuestion updateConversationAtProvider(OrderBundle bundle, ProviderQuestion providerQuestion) throws Exception;

	public abstract ProviderQuestion addQuestionToProvider(ProviderQuestion providerQuestion, OrderBundle bundle,
			String subject, String question, String conversationType) throws Exception;

	public abstract AdTemplate createAutoTemplate(Product product);

	/* This method tells whether the given order is in such a state that its
	 * status can be updated by updateOrderStatus. */
	protected boolean isInUpdatableState(Order order) {
		for (int i = 0; i < Order.UPDATABLE_STATES.length; i++) {
			if (order.getState().intValue() == Order.UPDATABLE_STATES[i]) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Check if the sum that we need to pay for the provider is sensible. Note that the vat percentage of
	 * the provider could possibly be different from the vat percentage that we take from our customers,
	 * so here we compare the prices excluding vat.
	 */
	protected void checkTheTotalSum(BigDecimal totalProviderPriceInEuroExclVat, OrderBundle bundle, List<Order> bundleOrders) throws PlaceOrderException {
		BigDecimal orderSalesPriceInEuroExclVat = BigDecimal.ZERO;
		for (Order order : bundleOrders) {
			orderSalesPriceInEuroExclVat = orderSalesPriceInEuroExclVat.add(order.getFinalSalesPriceInEuroExclVat());
		}
		BigDecimal totalBundleSalesPriceInEuroExclVat = orderSalesPriceInEuroExclVat.add(bundle.getCustomerShippingCostInEuroExclVat());
		if (totalProviderPriceInEuroExclVat.compareTo(totalBundleSalesPriceInEuroExclVat) > 0) {
			throw new PlaceOrderException("Provider price (excl. vat " + totalProviderPriceInEuroExclVat
					+ " euros) is higher than total sales price (excl. vat " + totalBundleSalesPriceInEuroExclVat.toPlainString() + ").");
		}
		double multiplier = (bundle.getSeller().getMargin().doubleValue() - 0.3) * (MIN_MULTIPLIER);
		if (totalProviderPriceInEuroExclVat.doubleValue() * multiplier > totalBundleSalesPriceInEuroExclVat.doubleValue()) {
			/* This can come if the provider price has increased since the listing took place and in
			 * that case we have to accept this one (if it's within reasonable limits). */
			BigDecimal providerPriceAtListingTimeEuroInclVat = BigDecimal.ZERO;
			for (Order order : bundleOrders) {
				providerPriceAtListingTimeEuroInclVat = providerPriceAtListingTimeEuroInclVat.add(order.getAd().getProviderPriceAtListingTimeEuroInclVat());
			}
			BigDecimal providerPriceAtListingTimeEuroExclVat = MathUtilities.getPriceExclVat(providerPriceAtListingTimeEuroInclVat, bundle.getProvider().getVatPercentage());
			providerPriceAtListingTimeEuroExclVat = providerPriceAtListingTimeEuroExclVat.add(bundle.getProviderShippingCostInEuroExclVat());
			if (providerPriceAtListingTimeEuroExclVat.compareTo(totalProviderPriceInEuroExclVat) < 0) {
				logger.debug("Provider price has increased from listing time to placeOrder time: listing price = "
						+ providerPriceAtListingTimeEuroExclVat.toPlainString() + ", placeOrder price = "
						+ totalProviderPriceInEuroExclVat.toPlainString() + " but accepting this one anyway.");
				EmailSender.sendOperationSummaryReport("Provider price change detected at ordering time",
						"Bundle " + bundle.getId() + " had a price of " + providerPriceAtListingTimeEuroExclVat.toPlainString()
						+ " euro excl vat at listing time but the provider price has increased to "
						+ totalProviderPriceInEuroExclVat.toPlainString() + " euro excl vat at ordering time. Still accepting this"
						+ " one since we get a small profit from this one nevertheless.",bundle.getProvider().getSystemEmail());
			} else {
				throw new PlaceOrderException("Provider price (excl. vat " + totalProviderPriceInEuroExclVat
						+ " euros) is higher than maximum allowed price.");
			}
		}
	}

	/* This method updates the state to "confirmed by provider" + informs the customer
	 * in case the order has been in the state "stock shortage before order". */
	public static void orderBundleConfirmedByProvider(OrderBundle bundle, List<Order> bundleOrders) throws Exception {
		boolean someOrderConfirmed = false;
		for (Order order : bundleOrders) {
			if (order.getState().equals(Order.STATE_ORDERED_FROM_PROVIDER)) {
				someOrderConfirmed = true;
				order.setStateWithHistory(Order.STATE_ORDER_CONFIRMED_BY_PROVIDER);
				for (StateChange sc : order.getStateChanges()) {
					if (sc.getState().equals(Order.STATE_STOCK_SHORTAGE_BEFORE_ORDER)) {
						long daysSinceGotIntoStockShortage = (System.currentTimeMillis() - sc.getUpdateDate().getTime()) / (1000*3600*24);
						if (daysSinceGotIntoStockShortage > 6) {
							/* Inform the customer in case we got stock shortage more than a week ago. This will look funny if
							 * there are several orders in the bundle but this happens so rarely, that it's not such a big deal.
							 * (It has only come once for bundle 4416 on 2012-01-16.) */
							MessageDAO messageDAO = (MessageDAO) ApplicationContextPlaceholder.applicationContext.getBean("messageDAO");
							String email = messageDAO.getText("orderedSuccessfullyFromProviderAfterStockShortage",
									bundle.getSeller(), order.getMarket().getLanguage(),
									"ORDER_ID",""+order.getId(),
									"SIGNATURE",bundle.getSeller().getSignature());
							EmailSender.sendEmailBatch(bundle.getSeller().getSystemEmail(),
									bundle.getCustomer().getEmail(),
									"Tilauksesi on edennyt (tilaus " + order.getId() + ")",email);
							bundle.setLastCustomerUpdateSent(new Date());
						}
						break;
					}
				}
			} else if (order.getState().equals(Order.STATE_CLOSED_ORDER_CANCELED)) {
				// Do nothing if some order in a multi-order bundle is canceled.
				continue;
			} else if (order.getState().equals(Order.STATE_FULFILMENT_CENTER)) {
				/* In some cases the state at FocalPrice goes from fulfillment center to confirmed by provider.
				 * Don't set the state in SellStar to confirmed if it has gone to fulfillment center. */
				someOrderConfirmed = true;
			} else if (order.getState().equals(Order.STATE_STOCK_SHORTAGE)) {
				/* Don't update the state of orders having "stock shortage". */
				someOrderConfirmed = true;
			} else if (order.getState().equals(Order.STATE_PROVIDER_CANCEL_PENDING)) {
				/* Don't update the state of orders having "provider cancel pending". */
				someOrderConfirmed = true;
			} else if (order.getState().equals(Order.STATE_ORDER_CONFIRMED_BY_PROVIDER)) {
				/* It's possible to confirm order bundles in two ways: by receiving a confirmation email
				 * or by running updateStatusOfOrders, so it's alright to already be in the state
				 * "confirmed by provider" when we get here. */
				someOrderConfirmed = true;
			} else if (order.getState().equals(Order.STATE_SHIPPED_BY_PROVIDER)) {
				/* Sometimes the delay between placing the order and FocalPrice sending out
				 * the order confirmation email is so long that the order has already been
				 * shipped. In this case don't do anything. */
				logger.info("Order confirmed came after the bundle was already shipped (for order " + order.getId() + " in bundle " + bundle.getId() + ").");
				someOrderConfirmed = true;
			} else if (order.getState().equals(Order.STATE_CLOSED)) {
				//For resendings of a multi-order bundle the orders that aren't
				//resent are in state closed, so do nothing here.
				continue;
			} else {
				/* If we are in some other state, then that's an error. */
				throw new Exception("order " + order.getId() + " is in the wrong state (ProviderOrder.orderBundleConfirmedByProvider()).");
			}
		}
		
		if (!someOrderConfirmed) {
			// This would probably be a problem. Did we get an order confirmed message
			// from the provider for a bundle where all orders are canceled?
			throw new RuntimeException("No order in bundle " + bundle.getId() + " was confirmed.");
		}
	}
	
	public static void setStateOfBundle(int newState, OrderBundle bundle, List<Order> bundleOrders) throws Exception {
		boolean someSet = false;
		for (Order order : bundleOrders) {
			/* Don't update the state of orders having "stock shortage" or "provider cancel pending"
			 * unless the new state is "shipped". */
			if ((!order.getState().equals(Order.STATE_STOCK_SHORTAGE)
					&& !order.getState().equals(Order.STATE_PROVIDER_CANCEL_PENDING))
					|| newState == Order.STATE_SHIPPED_BY_PROVIDER) {
				order.setStateWithHistory(newState);
				someSet = true;
			}
		}
		if (newState == Order.STATE_PROVIDER_CANCEL_CONFIRMED && someSet) {
			EmailSender.sendOperationProblemReport("Bundle unexpectedly set to provider cancel confirmed",
					"Bundle " + bundle.getId() + " unexpectedly set to \"provider cancel confirmed\" at provider."
					+ " Please inspect manually.",bundle.getProvider().getSystemEmail());
		}
	}

	protected void setShippingCostAtOrderingFromProvider(OrderBundle bundle, VirtualCoin shippingCostInclVatPC, boolean resending) throws PlaceOrderException {
		if ((bundle.getProviderShippingCostInEuroInclVat() == null && bundle.getProviderShippingCostInProviderCurrencyInclVat() != null)
				|| (bundle.getProviderShippingCostInEuroInclVat() != null && bundle.getProviderShippingCostInProviderCurrencyInclVat() == null)
				|| (bundle.getProviderShippingCostInEuroExclVat() == null && bundle.getProviderShippingCostInProviderCurrencyExclVat() != null)
				|| (bundle.getProviderShippingCostInEuroExclVat() != null && bundle.getProviderShippingCostInProviderCurrencyExclVat() == null)) {
			throw new PlaceOrderException("Inconsistent database discovered in ProviderOrder with regard to provider shipping cost.");
		}
		if (bundle.getResendLossInEuroInclVat().compareTo(BigDecimal.ZERO) != 0 || bundle.getResendLossInEuroExclVat().compareTo(BigDecimal.ZERO) != 0) {
			throw new PlaceOrderException("Inconsistent database discovered in ProviderOrder with regard to resendings (are we trying to resend a second time?).");
		}
		CurrencyDAO currencyDAO = (CurrencyDAO) ApplicationContextPlaceholder.applicationContext.getBean("currencyDAO");
		//BigDecimal shippingCostInEuro = currencyChecker.getDollar2Euro(shippingCostInDollars);
		BigDecimal shippingCostExclVatPC = MathUtilities.getPriceExclVat(shippingCostInclVatPC.getValue(), bundle.getProvider().getVatPercentage());
		BigDecimal shippingCostInEuroInclVat = currencyDAO.convertCurrency(shippingCostInclVatPC.getCurrency(), Currency.EURO, shippingCostInclVatPC.getValue());
		BigDecimal shippingCostInEuroExclVat = currencyDAO.convertCurrency(shippingCostInclVatPC.getCurrency(), Currency.EURO, shippingCostExclVatPC);
		if (resending) {
			if (bundle.getProviderShippingCostInEuroInclVat() == null || bundle.getProviderShippingCostInProviderCurrencyInclVat() == null
					|| bundle.getProviderShippingCostInEuroExclVat() == null || bundle.getProviderShippingCostInProviderCurrencyExclVat() == null) {
				throw new PlaceOrderException("Inconsistent database discovered in ProviderOrder with regard to resendings/provider shipping cost.");
			}
			bundle.setResendLossInEuroInclVat(shippingCostInEuroInclVat);
			bundle.setResendLossInEuroExclVat(shippingCostInEuroExclVat);
		} else {
			if (bundle.getProviderShippingCostInEuroInclVat() != null || bundle.getProviderShippingCostInProviderCurrencyInclVat() != null
					|| bundle.getProviderShippingCostInEuroExclVat() != null || bundle.getProviderShippingCostInProviderCurrencyExclVat() != null) {
				throw new PlaceOrderException("Inconsistent database discovered in ProviderOrder with regard to provider shipping cost.");
			}
			bundle.setProviderShippingCostInProviderCurrencyInclVat(shippingCostInclVatPC.getValue());
			bundle.setProviderShippingCostInProviderCurrencyExclVat(shippingCostExclVatPC);
			bundle.setProviderShippingCostInEuroInclVat(shippingCostInEuroInclVat);
			bundle.setProviderShippingCostInEuroExclVat(shippingCostInEuroExclVat);
		}
	}

	protected void setFinalProviderPriceAtOrderingFromProvider(Order order, VirtualCoin unitPriceInclVatPC) throws PlaceOrderException {
		if ((order.getFinalProviderPriceInProviderCurrencyInclVat() != null && order.getFinalProviderPriceInEuroInclVat() == null)
				|| (order.getFinalProviderPriceInProviderCurrencyInclVat() == null && order.getFinalProviderPriceInEuroInclVat() != null)
				|| (order.getFinalProviderPriceInProviderCurrencyExclVat() != null && order.getFinalProviderPriceInEuroExclVat() == null)
				|| (order.getFinalProviderPriceInProviderCurrencyExclVat() == null && order.getFinalProviderPriceInEuroExclVat() != null)) {
			throw new PlaceOrderException("Inconsistent database discovered in ProviderOrder.setFinalProviderPrice");
		}
		if ((order.getFinalProviderPriceInProviderCurrencyInclVat() == null || order.getFinalProviderPriceInEuroInclVat() == null
				|| order.getFinalProviderPriceInProviderCurrencyExclVat() == null || order.getFinalProviderPriceInEuroExclVat() == null)
				&& order.getState().equals(Order.STATE_SHOULD_RESEND)) {
			throw new PlaceOrderException("Inconsistent database discovered in ProviderOrder.setFinalProviderPrice");
		}
		if ((order.getFinalProviderPriceInProviderCurrencyInclVat() != null || order.getFinalProviderPriceInEuroInclVat() != null
				|| order.getFinalProviderPriceInProviderCurrencyExclVat() != null || order.getFinalProviderPriceInEuroExclVat() != null)
				&& !order.getState().equals(Order.STATE_SHOULD_RESEND)) {
			throw new PlaceOrderException("Inconsistent database discovered in ProviderOrder.setFinalProviderPrice");
		}

		CurrencyDAO currencyDAO = (CurrencyDAO) ApplicationContextPlaceholder.applicationContext.getBean("currencyDAO");
		//BigDecimal unitPriceEuro = currencyDAO.convertCurrency(unitPriceInclVatPC.getCurrency(), Currency.EURO, unitPriceInclVatPC.getValue());
		BigDecimal unitPriceExclVatPC = MathUtilities.getPriceExclVat(unitPriceInclVatPC.getValue(), order.getOrderBundle().getProvider().getVatPercentage());
		BigDecimal unitPriceInclVatEuro = currencyDAO.convertCurrency(unitPriceInclVatPC.getCurrency(), Currency.EURO, unitPriceInclVatPC.getValue());
		BigDecimal unitPriceExclVatEuro = currencyDAO.convertCurrency(unitPriceInclVatPC.getCurrency(), Currency.EURO, unitPriceExclVatPC);
		if (order.getState().equals(Order.STATE_SHOULD_RESEND)) {
			if (order.getResendLossInEuroInclVat().compareTo(BigDecimal.ZERO) != 0 || order.getResendLossInEuroExclVat().compareTo(BigDecimal.ZERO) != 0) {
				throw new PlaceOrderException("Inconsistent database discovered in ProviderOrder with regard to resendings.");
			}
			order.setResendLossInEuroInclVat(unitPriceInclVatEuro);
			order.setResendLossInEuroExclVat(unitPriceExclVatEuro);
		} else {
			if (order.getFinalProviderPriceInProviderCurrencyInclVat() != null || order.getFinalProviderPriceInEuroInclVat() != null
					|| order.getFinalProviderPriceInProviderCurrencyExclVat() != null || order.getFinalProviderPriceInEuroExclVat() != null) {
				throw new PlaceOrderException("Inconsistent database discovered in ProviderOrder with regard to provider product cost.");
			}
			order.setFinalProviderPriceInProviderCurrencyInclVat(unitPriceInclVatPC.getValue());
			order.setFinalProviderPriceInProviderCurrencyExclVat(unitPriceExclVatPC);
			order.setFinalProviderPriceInEuroInclVat(unitPriceInclVatEuro);
			order.setFinalProviderPriceInEuroExclVat(unitPriceExclVatEuro);
		}
		
		order.setSendCount(order.getSendCount() + 1);
		if (order.getSendCount() > 2) {
			throw new PlaceOrderException("Refusing to send order " + order.getId() + " a third time, only one resending allowed");
		}
	}

	public abstract void login(Provider provider, int tryCount) throws ProviderTemporarilyDownException;

	public abstract void logout(Provider provider, boolean removeDanglingOrders);

}
