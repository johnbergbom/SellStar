package fi.jonix.huutonet.order;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

import fi.jonix.huutonet.bank.TargetedBundles;
import fi.jonix.huutonet.bank.TargetedOrders;
import fi.jonix.huutonet.domain.ApplicationContextPlaceholder;
import fi.jonix.huutonet.domain.VirtualCoin;
import fi.jonix.huutonet.domain.model.Currency;
import fi.jonix.huutonet.domain.model.Market;
import fi.jonix.huutonet.domain.model.Order;
import fi.jonix.huutonet.domain.model.OrderBundle;
import fi.jonix.huutonet.domain.model.Product;
import fi.jonix.huutonet.domain.model.StateChange;
import fi.jonix.huutonet.domain.model.Tracking;
import fi.jonix.huutonet.domain.model.dao.OrderBundleDAO;
import fi.jonix.huutonet.domain.model.dao.OrderDAO;
import fi.jonix.huutonet.domain.model.dao.SettingsDAO;
import fi.jonix.huutonet.market.MarketRobot;
import fi.jonix.huutonet.post.GeneralPostService;
import fi.jonix.huutonet.tools.CurrencyFormatter;
import fi.jonix.huutonet.tools.MathUtilities;
import fi.jonix.huutonet.tools.Statics;

/**
 * This class contains helper methods for getting nicely
 * formatted information regarding orders.
 */
public class OrderInformation {

	/**
	 * NOTE: we need to be careful with naming of these states, because the customer
	 * will see this strings, so it's important that we don't reveal too much. For
	 * example no names of provider etc.
	 * 
	 * @param state
	 * @return
	 */
	public static String getStateName(Integer state) {
		if(state.equals(Order.STATE_PROVIDER_CANCEL_CONFIRMED))
			return "Tilaus peruttu";
		else if(state.equals(Order.STATE_PROVIDER_CANCEL_PENDING))
			return "Tilausta peruutetaan";
		else if(state.equals(Order.STATE_CLOSED))
			return "Suljettu";
		else if(state.equals(Order.STATE_CLOSED_CUSTOMER_NEVER_PAID))
			return "Suljettu (ilman kauppaa)";
		//else if(state.equals(Order.STATE_CUSTOMER_CANCEL_PENDING))
			//return "Tilausta peruutetaan (vahvistamatta)";
		else if(state.equals(Order.STATE_CUSTOMER_CANCEL_CONFIRMED))
			return "Tilausta peruutetaan (asiakas vahvistanut)";
		else if(state.equals(Order.STATE_MONEY_REFUNDED_BY_PROVIDER_FOR_CANCELED_ORDER))
			return "Rahat palautetaan (vaihe 1)";
		//else if(state.equals(Order.STATE_MONEY_REFUNDABLE_TO_CUSTOMER_FOR_EARLY_CANCEL))
			//return "Rahat palautetaan (vaihe 2)";
		//else if(state.equals(Order.STATE_REFUND_PENDING_FOR_SENT_ORDER))
			//return "Hyvitys maksetaan";
		//else if(state.equals(Order.STATE_MONEY_REFUNDED_FOR_SENT_ORDER))
			//return "Hyvitys maksettu";
		else if(state.equals(Order.STATE_FAULTY_CONTACT_INFO))
			return "Virheelliset yhteystiedot";
		else if(state.equals(Order.STATE_STOCK_SHORTAGE_BEFORE_ORDER))
			return "Tuote väliaikaisesti loppunut (maksettu)";
		else if(state.equals(Order.STATE_CLOSED_ORDER_CANCELED))
			return "Suljettu (peruutettu)";
		else if(state.equals(Order.STATE_CUSTOMER_PAID))
			return "Maksu vastaanotettu";
		else if(state.equals(Order.STATE_CUSTOMER_PAID_INFORMED))
			return "Maksu saapunut";
		else if(state.equals(Order.STATE_SHOULD_RESEND))
			return "Lähetettävä uudelleen";
		else if(state.equals(Order.STATE_FULFILMENT_CENTER))
			return "Tilaus pakattavana";
		//else if(state.equals(Order.STATE_MONEY_REFUNDED_TO_CUSTOMER_FOR_CANCELED_ORDER))
			//return "Tilaus peruttu ja rahat palautettu";
		else if(state.equals(Order.STATE_NEW))
			return "Uusi tilaus";
		else if(state.equals(Order.STATE_NEW_OUT_OF_STOCK))
			return "Uusi tilaus (väliaikaisesti loppui varastolta)";
		else if(state.equals(Order.STATE_ORDER_CONFIRMED_BY_PROVIDER))
			return "Tilaus käsiteltävänä (vaihe 2)";
		else if(state.equals(Order.STATE_ORDERED_FROM_PROVIDER_NO_ID))
			return "Tilaus käsiteltävänä (ilman tunnistetta)";
		else if(state.equals(Order.STATE_ORDERED_FROM_PROVIDER))
			return "Tilaus käsiteltävänä (vaihe 1)";
		else if(state.equals(Order.STATE_OTHER_ERROR))
			return "Tilauksessa ilmennyttä ongelmaa selvitetään";
		else if(state.equals(Order.STATE_PAID_TOO_LITTLE_ERROR))
			return "Maksusuoritus puutteellinen";
		else if(state.equals(Order.STATE_SHIPPED_BY_PROVIDER))
			return "Tilaus viety postiin";
		else if(state.equals(Order.STATE_SHIPPED_BY_POSTOFFICE))
			return "Tilaus lähetetty";
		else if(state.equals(Order.STATE_STOCK_SHORTAGE))
			return "Tuote väliaikaisesti loppunut";
		//else if(state.equals(Order.STATE_STOCK_SHORTAGE_INFORMED))
			//return "Tuote väliaikaisesti loppunut (tiedotettu)";
		else if(state.equals(Order.STATE_NO_LONGER_FOR_SALE_AT_PROVIDER_EARLY))
			return "Tuote poistunut tuotevalikoimasta";
		else if(state.equals(Order.STATE_NO_LONGER_FOR_SALE_AT_PROVIDER_LATE))
			return "Tuote poistumassa tuotevalikoimasta";
		else if(state.equals(Order.STATE_AWAITING_STOCK))
			return "Varaston täydennys tulossa (vaihe 1)";
		else if(state.equals(Order.STATE_WAITING_FOR_SUPPLIER))
			return "Varaston täydennys tulossa (vaihe 2)";
		else if(state.equals(Order.STATE_REACHED_DESTINATION_COUNTRY))
			return "Saapunut maahan";
		else if(state.equals(Order.STATE_ARRIVED_TO_CUSTOMERS_POSTOFFICE))
			return "Haettavissa postista";
		else if(state.equals(Order.STATE_FETCHED_BY_CUSTOMER))
			return "Noudettu";
		else if(state.equals(Order.STATE_CLOSED_RETURNED_TO_PROVIDER))
			return "Palautettu lähettäjälle";
		else {
			//return "Tuntematon";
			throw new RuntimeException("Unknown state " + state);
		}
	}

	public static StringBuffer getOrderInfo(Order order, boolean showOperatorInfo) {
		OrderDAO orderDAO = (OrderDAO) ApplicationContextPlaceholder.applicationContext.getBean("orderDAO");
		GeneralPostService generalPostService = (GeneralPostService) ApplicationContextPlaceholder.applicationContext.getBean("generalPostService");
		StringBuffer buffer = new StringBuffer();
		OrderBundle bundle = order.getOrderBundle();
		VirtualCoin prodPriceMCInclVat = new VirtualCoin(bundle.getUsedCustomerCurrency(),order.getFinalSalesPriceInMarketCurrencyInclVat());
		VirtualCoin shippingCostMCInclVat = new VirtualCoin(bundle.getUsedCustomerCurrency(),bundle.getCustomerShippingCostInMarketCurrencyInclVat());
		VirtualCoin totCostMCInclVat = new VirtualCoin(bundle.getUsedCustomerCurrency(),shippingCostMCInclVat.getValue().add(prodPriceMCInclVat.getValue()));
		List<Order> bundleOrders = orderDAO.getOrdersInBundle(bundle);
		if (bundleOrders.size() > 1) {
			String vatInfoForShipping = "";
			if (bundle.getCustomerShippingVatPercentage().compareTo(BigDecimal.ZERO) > 0) {
				BigDecimal shippingVatAmount = bundle.getCustomerShippingCostInMarketCurrencyInclVat().
					subtract(bundle.getCustomerShippingCostInMarketCurrencyExclVat());
				VirtualCoin shippingVatAmountVC = new VirtualCoin(bundle.getUsedCustomerCurrency(),shippingVatAmount);
				vatInfoForShipping = " (sis. alv " + MathUtilities.roundTo2Decimals(bundle.getCustomerShippingVatPercentage())
					+ " %, eli " + CurrencyFormatter.print(shippingVatAmountVC, Statics.FINNISH) + ")";
			}
			buffer.append("Nippu " + bundle.getId() + ", nipun postikulut" + vatInfoForShipping + ": " + CurrencyFormatter.print(shippingCostMCInclVat, Statics.FINNISH)
					+ " (maksettu " + CurrencyFormatter.print(new VirtualCoin(bundle.getUsedCustomerCurrency(),
							bundle.getTargetedPaidShippingCostInMarketCurrency()), Statics.FINNISH) + ")\n");
			/*if (showOperatorInfo) {
				buffer.append("Old bundle-verification: " + bundle.getOldVerification() + "\n");
			}*/
		}
		buffer.append("Tilausnumero " + order.getId() + ", ");
		buffer.append(order.getAd().getAdTemplate().getHeadline() + ", ");
		if (bundleOrders.size() > 1) {
			String vatInfo = "";
			if (order.getCustomerVatPercentage().compareTo(BigDecimal.ZERO) > 0) {
				BigDecimal productVatAmount = order.getFinalSalesPriceInMarketCurrencyInclVat().
					subtract(order.getFinalSalesPriceInMarketCurrencyExclVat());
				VirtualCoin productVatAmountVC = new VirtualCoin(bundle.getUsedCustomerCurrency(),productVatAmount);
				vatInfo = "sis. alv " + CurrencyFormatter.print(productVatAmountVC, Statics.FINNISH) + ", ";
			}
			buffer.append(CurrencyFormatter.print(prodPriceMCInclVat, Statics.FINNISH) + " (" + vatInfo + "maksettu "
					+ CurrencyFormatter.print(new VirtualCoin(bundle.getUsedCustomerCurrency(),order.getTargetedPaidInMarketCurrency()), Statics.FINNISH) + ")\n");
		} else {
			String vatInfo = "";
			if (bundle.getCustomerShippingVatPercentage().compareTo(BigDecimal.ZERO) > 0 || order.getCustomerVatPercentage().compareTo(BigDecimal.ZERO) > 0) {
				BigDecimal shippingVatAmount = bundle.getCustomerShippingCostInMarketCurrencyInclVat().
					subtract(bundle.getCustomerShippingCostInMarketCurrencyExclVat());
				BigDecimal productVatAmount = order.getFinalSalesPriceInMarketCurrencyInclVat().
					subtract(order.getFinalSalesPriceInMarketCurrencyExclVat());
				VirtualCoin totalVatAmountVC = new VirtualCoin(bundle.getUsedCustomerCurrency(),shippingVatAmount.add(productVatAmount));
				vatInfo = "sis. alv " + CurrencyFormatter.print(totalVatAmountVC, Statics.FINNISH) + ", ";
			}
			VirtualCoin targetedTotalPaidMC = new VirtualCoin(bundle.getUsedCustomerCurrency(),
					order.getTargetedPaidInMarketCurrency().add(bundle.getTargetedPaidShippingCostInMarketCurrency()));
			buffer.append(CurrencyFormatter.print(prodPriceMCInclVat, Statics.FINNISH) + " + " + CurrencyFormatter.print(shippingCostMCInclVat, Statics.FINNISH)
					+ " = " + CurrencyFormatter.print(totCostMCInclVat, Statics.FINNISH) + " (" + vatInfo + "maksettu "
					+ CurrencyFormatter.print(targetedTotalPaidMC, Statics.FINNISH) + ")\n");
		}
		buffer.append(getUrlForMarketSalesId(order.getMarket(), order.getAd().getMarketSalesId()) + ", ");
		buffer.append("viitenumero " + order.getCustomerReferenceNumber() + "\n");
		buffer.append("Osoite: " + order.getOrderBundle().getFirstName() + " " + order.getOrderBundle().getLastName() + "; "
				+ order.getOrderBundle().getAddress() + "; " + order.getOrderBundle().getPostcode() + "; "
				+ order.getOrderBundle().getCity() + "\n");
		buffer.append("Tapahtumat\n");
		TreeSet<StateChange> treeSet = new TreeSet<StateChange>(order.getStateChanges());
		for (StateChange stateChange : treeSet) {
			//if (showOperatorInfo || isCustomerViewableState(stateChange.getState())) {
				buffer.append(" -" + Statics.DATE_FORMAT.format(stateChange.getUpdateDate()) + " " + getStateName(stateChange.getState()) + "\n");
			//}
		}
		/* Show order tracking information if the order is shipped by the provider but still not closed. */
		if (order.getState().intValue() >= Order.STATE_SHIPPED_BY_PROVIDER
				&& order.getState().intValue() <= Order.STATE_FETCHED_BY_CUSTOMER) {
			Tracking tracking = order.getTracking();
			if (tracking != null) {
				buffer.append("Seurantakoodi: " + tracking.getTrackingCode() + "\n");
				List<Order> orderList = new ArrayList<Order>();
				orderList.add(order);
				buffer.append("Seurantaosoite: " + generalPostService.getManualTrackingURL(tracking,orderList) + "\n");
			}
		}
		if (showOperatorInfo) {
			//buffer.append("http://www.dealextreme.com/details.dx/" + order.getProviderProdId() + "\n");
			buffer.append("Provider product id: " + order.getProduct().getProviderProdId() + "\n");
			buffer.append("Provider product url: " + order.getProduct().getProviderUrl() + "\n");
			buffer.append("Provider order id: " + order.getOrderBundle().getProviderOrderId() + "\n");
			if (order.getProduct().getManufacturerUrl() != null) {
				buffer.append("Manufacturer url: " + order.getProduct().getManufacturerUrl() + "\n");
			}
			buffer.append("Google order: " + order.getGoogleOrder() + "\n");
			buffer.append("Offline sales: " + order.getProduct().getOfflineSale() + "\n");
			buffer.append("Product state: ");
			if (order.getProduct().getProductState().equals(Product.STATE_NO_LONGER_FOR_SALE)) {
				buffer.append("NO LONGER FOR SALE");
			} else if (order.getProduct().getProductState().equals(Product.STATE_OUT_OF_STOCK)) {
				buffer.append("OUT OF STOCK");
			} else if (order.getProduct().getProductState().equals(Product.STATE_IN_STOCK)) {
				buffer.append("in stock");
			} else if (order.getProduct().getProductState().equals(Product.STATE_UNKNOWN)) {
				buffer.append("UNKNOWN");
			} else {
				throw new RuntimeException("Unknown state for product " + order.getProduct().getId());
			}
			if (order.getProduct().getStatusFetchDate() != null) {
				buffer.append(" (last fetched on " + Statics.DATE_FORMAT.format(order.getProduct().getStatusFetchDate()) + ")");
			}
			buffer.append("\n");
			if (order.getProduct().getCurrentProviderPriceProviderCurrencyInclVat() != null) {
				buffer.append("Provider price: " + order.getProduct().getCurrentProviderPriceProviderCurrencyInclVat().toPlainString());
			} else {
				buffer.append("Provider price: unknown");
			}
			buffer.append("\n");
			//buffer.append("Old order-verification: " + order.getOldVerification() + "\n");
		}
		return buffer;
	}
	
	/*public static StringBuffer getBundleInfo(OrderBundle bundle, List<Order> bundleOrders) {
		OrderDAO orderDAO = (OrderDAO) ApplicationContextPlaceholder.applicationContext.getBean("orderDAO");
		GeneralPostService generalPostService = (GeneralPostService) ApplicationContextPlaceholder.applicationContext.getBean("generalPostService");
		StringBuffer buffer = new StringBuffer();
		BigDecimal prodPriceMC = BigDecimal.ZERO;
		for (Order order : bundleOrders) {
			prodPriceMC = prodPriceMC.add(order.getFinalSalesPriceInMarketCurrency());
		}
		Currency usedCustomerCurrency = bundle.getUsedCustomerCurrency();
		VirtualCoin shippingCostMC = new VirtualCoin(usedCustomerCurrency,bundle.getCustomerShippingCostInMarketCurrency());
		VirtualCoin totCostMC = new VirtualCoin(usedCustomerCurrency,shippingCostMC.getValue().add(prodPriceMC));
		if (bundleOrders.size() > 1) {
			VirtualCoin targetedBundlePaidMC = new VirtualCoin(usedCustomerCurrency,bundle.getTargetedPaidShippingCostInMarketCurrency());
			buffer.append("Nippu " + bundle.getId() + ", nipun postikulut: " + CurrencyFormatter.print(shippingCostMC, Statics.FINNISH)
					+ " (maksettu " + CurrencyFormatter.print(targetedBundlePaidMC, Statics.FINNISH) + ")\n");
			buffer.append("Osoite: " + bundle.getFirstName() + " " + bundle.getLastName() + "; "
					+ bundle.getAddress() + "; " + bundle.getPostcode() + "; "
					+ bundle.getCity() + "\n\n");
		}
		for (Order order : bundleOrders) {
			buffer.append("Tilausnumero " + order.getId() + ", ");
			buffer.append(order.getAd().getAdTemplate().getHeadline() + ", ");
			VirtualCoin orderFinalSalesPriceMC = new VirtualCoin(usedCustomerCurrency,
					order.getFinalSalesPriceInMarketCurrency());
			if (bundleOrders.size() > 1) {
				VirtualCoin targetedOrderPaidMC = new VirtualCoin(usedCustomerCurrency,
						order.getTargetedPaidInMarketCurrency());
				buffer.append(CurrencyFormatter.print(orderFinalSalesPriceMC, Statics.FINNISH) + " (maksettu "
						+ CurrencyFormatter.print(targetedOrderPaidMC, Statics.FINNISH) + ")\n");
			} else {
				VirtualCoin targetedTotalPaidMC = new VirtualCoin(usedCustomerCurrency,
						order.getTargetedPaidInMarketCurrency().add(bundle.getTargetedPaidShippingCostInMarketCurrency()));
				buffer.append(CurrencyFormatter.print(orderFinalSalesPriceMC, Statics.FINNISH)
						+ " + " + CurrencyFormatter.print(shippingCostMC, Statics.FINNISH)
						+ " = " + CurrencyFormatter.print(totCostMC, Statics.FINNISH) + " (maksettu "
						+ CurrencyFormatter.print(targetedTotalPaidMC, Statics.FINNISH) + ")\n");
			}
			buffer.append(getUrlForMarketSalesId(order.getMarket(), order.getAd().getMarketSalesId()) + ", ");
			buffer.append("viitenumero " + order.getCustomerReferenceNumber() + "\n");
			if (bundleOrders.size() == 1) {
				buffer.append("Osoite: " + bundle.getFirstName() + " " + bundle.getLastName() + "; "
						+ bundle.getAddress() + "; " + bundle.getPostcode() + "; "
						+ bundle.getCity() + "\n");
			}
			buffer.append("Tapahtumat\n");
			TreeSet<StateChange> treeSet = new TreeSet<StateChange>(order.getStateChanges());
			for(StateChange stateChange : treeSet) {
				buffer.append(" -" + Statics.DATE_FORMAT.format(stateChange.getUpdateDate()) + " " + getStateName(stateChange.getState()) + "\n");
			}
			/* Show order tracking information if the order is shipped by the provider but still not closed. /
			if (order.getState().intValue() >= Order.STATE_SHIPPED_BY_PROVIDER
					&& order.getState().intValue() <= Order.STATE_FETCHED_BY_CUSTOMER) {
				Tracking tracking = order.getTracking();
				if (tracking != null) {
					buffer.append("Seurantakoodi: " + tracking.getTrackingCode() + "\n");
					List<Order> orderList = new ArrayList<Order>();
					orderList.add(order);
					buffer.append("Seurantaosoite: " + generalPostService.getManualTrackingURL(tracking,orderList) + "\n");
				}
			}
			buffer.append("\n");
		}
		return buffer;
	}*/

	/*public static String getCustomerAddressInfo(Customer customer) {
		return "Osoite:\n" + customer.getFirstName() + " " + customer.getLastName() + "\n"
			+ customer.getAddress() + "\n" + customer.getPostcode() + "\n" + customer.getCity() + "\n";
	}*/
	
	/*public static String getOrderAddressInfo(Order order) {
		return "Osoite:\n" + order.getFirstName() + " " + order.getLastName() + "\n"
			+ order.getAddress() + "\n" + order.getPostcode() + "\n" + order.getCity() + "\n";
	}*/
	
	public static StringBuffer getOrderStockInfo(List<Order> orderList, boolean showOperatorInfo) {
		StringBuffer buffer = new StringBuffer();
		if (orderList.isEmpty()) {
			return buffer;
		}

		/* Get all the states of the orders for the customer. */
		List<Integer> states = new ArrayList<Integer>();
		for (Order order : orderList) {
			if (!states.contains(order.getState())) {
				states.add(order.getState());
			}
		}
		Collections.sort(states);
		
		for (Integer state : states) {
			buffer.append("\n\n");
			//String stateName = (showOperatorInfo || isCustomerViewableState(state) ? getStateName(state) : "Tuntematon");
			String stateName = getStateName(state);
			buffer.append(stateName + "\n");

			for (int i = 0; i < stateName.length(); i++) {
				buffer.append("=");
			}
			buffer.append("\n\n");

			for (Order order : orderList) {
				if (order.getState().equals(state)) {
					buffer.append(getOrderInfo(order,showOperatorInfo) + "\n");
				}
			}
		}

		return buffer;
	}
	
	private static String getUrlForMarketSalesId(Market market, String marketSalesId) {
		List<MarketRobot> listers = (List<MarketRobot>) ApplicationContextPlaceholder.applicationContext.getBean("listers");
		for (MarketRobot lister : listers) {
			if (lister.canHandleMarket(market)) {
				return lister.getUrlForMarketSalesId(marketSalesId);
			}
		}
		//It's an error if we get here.
		throw new RuntimeException("Unknown market: " + market.getId());
	}
	
	public static String getOrderPaymentsDescription(TargetedOrders targetedOrders, TargetedBundles targetedBundles) {
		OrderDAO orderDAO = (OrderDAO) ApplicationContextPlaceholder.applicationContext.getBean("orderDAO");
		OrderBundleDAO orderBundleDAO = (OrderBundleDAO) ApplicationContextPlaceholder.applicationContext.getBean("orderBundleDAO");
		StringBuffer paymentDescr = new StringBuffer();
		
		List<Long> allBundles = new ArrayList<Long>();
		BigDecimal expectedOrderPaymentMC = BigDecimal.ZERO;
		BigDecimal targetedOrderPaymentMC = BigDecimal.ZERO;
		BigDecimal expectedShippingPaymentMC = BigDecimal.ZERO;
		BigDecimal targetedShippingPaymentMC = BigDecimal.ZERO;
		Currency usedCustomerCurrency = null;
		if (targetedOrders != null) {
			expectedOrderPaymentMC = expectedOrderPaymentMC.add(targetedOrders.getExpectedPaymentInMarketCurrency());
			targetedOrderPaymentMC = targetedOrderPaymentMC.add(targetedOrders.getTargetedPaidInMarketCurrency());
			for (Order order : targetedOrders.getOrders()) {
				if (usedCustomerCurrency == null) {
					usedCustomerCurrency = order.getOrderBundle().getUsedCustomerCurrency();
				} else if (!order.getOrderBundle().getUsedCustomerCurrency().equals(usedCustomerCurrency)) {
					//We should never get here since TargetedOrders makes sure that different currencies
					//are handled separately
					throw new RuntimeException("Cannot handle: different currencies for different orders.");
				}
				if (!allBundles.contains(order.getOrderBundle().getId())) {
					allBundles.add(order.getOrderBundle().getId());
				}
			}
		}
		if (targetedBundles != null) {
			expectedShippingPaymentMC = expectedShippingPaymentMC.add(targetedBundles.getExpectedPaymentInMarketCurrency());
			targetedShippingPaymentMC = targetedShippingPaymentMC.add(targetedBundles.getTargetedPaidInMarketCurrency());
			for (OrderBundle orderBundle : targetedBundles.getBundles()) {
				if (usedCustomerCurrency == null) {
					usedCustomerCurrency = orderBundle.getUsedCustomerCurrency();
				} else if (!orderBundle.getUsedCustomerCurrency().equals(usedCustomerCurrency)) {
					//We should never get here since TargetedOrders makes sure that different currencies
					//are handled separately
					throw new RuntimeException("Cannot handle: different currencies for different bundles.");
				}
				if (!allBundles.contains(orderBundle.getId())) {
					allBundles.add(orderBundle.getId());
				}
			}
		}
		VirtualCoin totalExpectedPaymentMC = new VirtualCoin(usedCustomerCurrency,expectedOrderPaymentMC.add(expectedShippingPaymentMC));
		VirtualCoin totalTargetedPaymentMC = new VirtualCoin(usedCustomerCurrency,targetedOrderPaymentMC.add(targetedShippingPaymentMC));

		/* First print the shipping costs. */
		/*paymentDescr.append("Postikulut:\n");
		paymentDescr.append("Yhteensä: " + CurrencyFormatter.print(expectedShippingPayment,Statics.FINNISH) + "\n");
		paymentDescr.append("Josta maksettu: " + CurrencyFormatter.print(targetedShippingPayment,Statics.FINNISH));
		paymentDescr.append("\n\n");*/

		/* Then print all orders in the bundles. */
		for (Long bundleId : allBundles) {
			OrderBundle bundle = orderBundleDAO.get(bundleId);
			VirtualCoin bundleShippingCostMC = new VirtualCoin(usedCustomerCurrency,bundle.getCustomerShippingCostInMarketCurrencyInclVat());
			VirtualCoin bundleTargetedPaidMC = new VirtualCoin(usedCustomerCurrency,bundle.getTargetedPaidShippingCostInMarketCurrency());
			List<Order> nonCanceledBundleOrders = new ArrayList<Order>();
			/* Only add non-canceled orders to the list (note: it doesn't matter that we above
			 * included also canceled orders when calculating the expectedOrderPayment and
			 * targetedOrderPayment because those should always have the same value for canceled
			 * orders. */
			for (Order order : orderDAO.getOrdersInBundle(bundle)) {
				if (!order.getState().equals(Order.STATE_CLOSED_ORDER_CANCELED)) {
					nonCanceledBundleOrders.add(order);
				}
			}
			if (nonCanceledBundleOrders.size() > 1) {
				String vatInfoForShipping = "";
				if (bundle.getCustomerShippingVatPercentage().compareTo(BigDecimal.ZERO) > 0) {
					BigDecimal shippingVatAmount = bundle.getCustomerShippingCostInMarketCurrencyInclVat().
						subtract(bundle.getCustomerShippingCostInMarketCurrencyExclVat());
					VirtualCoin shippingVatAmountVC = new VirtualCoin(bundle.getUsedCustomerCurrency(),shippingVatAmount);
					vatInfoForShipping = " (sis. alv " + MathUtilities.roundTo2Decimals(bundle.getCustomerShippingVatPercentage())
						+ " %, eli " + CurrencyFormatter.print(shippingVatAmountVC, Statics.FINNISH) + ")";
				}
				paymentDescr.append("Nippunumero " + bundle.getId() + ":n postikulut: "
						+ CurrencyFormatter.print(bundleShippingCostMC,Statics.FINNISH)
						+ vatInfoForShipping + ", josta maksettu: " + CurrencyFormatter.print(bundleTargetedPaidMC,Statics.FINNISH) + "\n");
			}
			for (Order order : nonCanceledBundleOrders) {
				paymentDescr.append("Tilaus " + order.getId());
				/*if (orderDAO.getNbrOrdersInBundle(bundle) > 1) {
					paymentDescr.append(" (nippunro. " + bundle.getId() + ")");
				}*/
				paymentDescr.append(" - " + order.getAd().getAdTemplate().getHeadline());
				paymentDescr.append(" (" + getUrlForMarketSalesId(order.getMarket(), order.getAd().getMarketSalesId()) + ")");
				paymentDescr.append(":\n");
				VirtualCoin expectedPaymentWithoutShippingMCInclVat = new VirtualCoin(usedCustomerCurrency,order.getFinalSalesPriceInMarketCurrencyInclVat());
				VirtualCoin expectedPaymentWithShippingMC = new VirtualCoin(usedCustomerCurrency,
						expectedPaymentWithoutShippingMCInclVat.getValue().add(bundleShippingCostMC.getValue()));
				if (nonCanceledBundleOrders.size() > 1) {
					String vatInfo = "";
					if (order.getCustomerVatPercentage().compareTo(BigDecimal.ZERO) > 0) {
						BigDecimal productVatAmount = order.getFinalSalesPriceInMarketCurrencyInclVat().
							subtract(order.getFinalSalesPriceInMarketCurrencyExclVat());
						VirtualCoin productVatAmountVC = new VirtualCoin(bundle.getUsedCustomerCurrency(),productVatAmount);
						vatInfo = "(sis. alv " + CurrencyFormatter.print(productVatAmountVC, Statics.FINNISH) + ")";
					}
					paymentDescr.append("  hinta (ilman postikuluja): " + CurrencyFormatter.print(expectedPaymentWithoutShippingMCInclVat,Statics.FINNISH));
					paymentDescr.append(vatInfo + ", maksettu: ");
					VirtualCoin targetedPaidMC = new VirtualCoin(usedCustomerCurrency,
							order.getTargetedPaidInMarketCurrency());
					paymentDescr.append(CurrencyFormatter.print(targetedPaidMC,Statics.FINNISH));
				} else {
					String vatInfo = "";
					if (bundle.getCustomerShippingVatPercentage().compareTo(BigDecimal.ZERO) > 0 || order.getCustomerVatPercentage().compareTo(BigDecimal.ZERO) > 0) {
						BigDecimal shippingVatAmount = bundle.getCustomerShippingCostInMarketCurrencyInclVat().
							subtract(bundle.getCustomerShippingCostInMarketCurrencyExclVat());
						BigDecimal productVatAmount = order.getFinalSalesPriceInMarketCurrencyInclVat().
							subtract(order.getFinalSalesPriceInMarketCurrencyExclVat());
						VirtualCoin totalVatAmountVC = new VirtualCoin(bundle.getUsedCustomerCurrency(),shippingVatAmount.add(productVatAmount));
						vatInfo = "(sis. alv " + CurrencyFormatter.print(totalVatAmountVC, Statics.FINNISH) + ")";
					}
					paymentDescr.append("  hinta postikuluineen: " + CurrencyFormatter.print(expectedPaymentWithShippingMC,Statics.FINNISH));
					paymentDescr.append(vatInfo + ", maksettu: ");
					VirtualCoin targetedPaymentWithShippingMC = new VirtualCoin(usedCustomerCurrency,
							order.getTargetedPaidInMarketCurrency().add(bundleTargetedPaidMC.getValue()));
					paymentDescr.append(CurrencyFormatter.print(targetedPaymentWithShippingMC,Statics.FINNISH));
				}
				paymentDescr.append("\n");
			}
			paymentDescr.append("\n");
		}
		
		//TODO: the total price shows the wrong value if the customer has paid for _some_ of the
		//orders in the bundle, because only the orders in targetedOrders are taken into account.
		//For example someuser@gmail.com got a funny payment reminder on 2011-09-10.
		paymentDescr.append("\nKaikkien tilaustesi kokonaishinta on ");
		paymentDescr.append(CurrencyFormatter.print(totalExpectedPaymentMC,Statics.FINNISH) + ".\n");
		paymentDescr.append("Tällä hetkellä maksusuorituksia on kirjautunut yhteensä ");
		paymentDescr.append(CurrencyFormatter.print(totalTargetedPaymentMC,Statics.FINNISH) + ".\n");
		/*if (targetedOrders.totalRefundsInMarketCurrency != 0) {
			paymentDescr.append("Hyvitetty summa ");
			paymentDescr.append(CurrencyFormatter.print(MathUtilities.roundTo2Decimals(targetedOrders.actualPaymentInMarketCurrency),Statics.FINNISH) + ".\n");
		}*/
		VirtualCoin missingSumMC = new VirtualCoin(usedCustomerCurrency,
				totalExpectedPaymentMC.getValue().subtract(totalTargetedPaymentMC.getValue()));
		if (missingSumMC.getValue().compareTo(BigDecimal.ZERO) <= 0) {
			throw new RuntimeException("Internal error in getOrderPaymentsDescription: missingSum is <= 0");
		}
		paymentDescr.append("Odotamme teiltä vielä ");
		paymentDescr.append(CurrencyFormatter.print(missingSumMC,Statics.FINNISH));
		paymentDescr.append(":n maksusuoritusta.\n");
		paymentDescr.append("Ole hyvä ja maksa jäljellä olevat maksusuoritukset seuraavalla koontilaskulla:\n\n");
		paymentDescr.append("-------------------\n\n");
		SettingsDAO settingsDAO = (SettingsDAO) ApplicationContextPlaceholder.applicationContext.getBean("settingsDAO");
		//String accountNumber = settingsDAO.getValue("bankAccountNumber");
		String accountNumberIban = settingsDAO.getValue("bankAccountNumberIban");
		String accountNumberBIC = settingsDAO.getValue("bankAccountNumberBIC");
		paymentDescr.append("Tilinumero: " + accountNumberIban + " (BIC: " + accountNumberBIC + ")\n");
		paymentDescr.append("Maksun summa: " + CurrencyFormatter.print(missingSumMC,Statics.FINNISH) + "\n");

		/* Figure out a reference number to use. */
		String referenceNumber = null;
		if (targetedOrders == null) {
			/* targetedOrders is null if all orders are paid correctly but the shipping cost
			 * isn't paid for. In this case use the customer reference number of some unpaid
			 * order of the customer. */
			//List<Order> bundleOrders = orderDAO.getOrdersInBundle(targetedBundles.getBundles().get(0));
			//referenceNumber = bundleOrders.get(0).getCustomerReferenceNumber();
			for (OrderBundle bundle : targetedBundles.getBundles()) {
				if (bundle.getTargetedPaidShippingCostInMarketCurrency().compareTo(bundle.getCustomerShippingCostInMarketCurrencyInclVat()) < 0) {
					for (Order order : orderDAO.getOrdersInBundle(bundle)) {
						//if (order.getTargetedPaidInMarketCurrency().compareTo(order.getFinalSalesPriceInMarketCurrency()) < 0) {
						if (order.getState() <= Order.STATE_CUSTOMER_PAID_INFORMED) {
							referenceNumber = order.getCustomerReferenceNumber();
							break;
						}
					}
				}
				if (referenceNumber != null) {
					break;
				}
			}
		} else {
			//Make sure that we use the reference number of an order that's still unpaid
			for (Order order : targetedOrders.getOrders()) {
				if (order.getTargetedPaidInMarketCurrency().compareTo(order.getFinalSalesPriceInMarketCurrencyInclVat()) < 0) {
					referenceNumber = order.getCustomerReferenceNumber();
					break;
				}
			}
			//referenceNumber = targetedOrders.getOrders().get(0).getCustomerReferenceNumber();
		}
		
		/* Safety check, we should never get here. */
		if (referenceNumber == null) {
			throw new RuntimeException("No suitable reference number found.");
		}
		
		paymentDescr.append("Viitenumero: " + referenceNumber + "\n\n");
		paymentDescr.append("HUOM: Muistathan aina käyttää maksaessasi viitenumeroa. Maksut kirjautuvat viitenumeron perusteella.\n\n");
		paymentDescr.append("-------------------\n\n");
		return paymentDescr.toString();
	}

}
