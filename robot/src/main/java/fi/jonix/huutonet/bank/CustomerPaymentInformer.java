package fi.jonix.huutonet.bank;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import fi.jonix.huutonet.domain.VirtualCoin;
import fi.jonix.huutonet.domain.model.Order;
import fi.jonix.huutonet.domain.model.OrderBundle;
import fi.jonix.huutonet.domain.model.dao.MessageDAO;
import fi.jonix.huutonet.domain.model.dao.OrderBundleDAO;
import fi.jonix.huutonet.domain.model.dao.OrderDAO;
import fi.jonix.huutonet.email.EmailWrapper;
import fi.jonix.huutonet.email.EmailSender;
import fi.jonix.huutonet.tools.CurrencyFormatter;
import fi.jonix.huutonet.tools.Statics;

/**
 * We have two different states STATE_CUSTOMER_PAID and STATE_CUSTOMER_PAID_INFORMED. Originally
 * the reason why we separated the registering of the payment and the informing of the customer
 * about it was that it's not possible to rollback a sent email. Nowadays email sending is
 * taken care of in a completely different way. However the state STATE_CUSTOMER_PAID_INFORMED
 * still has a point: an order can be set into the state STATE_CUSTOMER_PAID in several places
 * (at least in BankHelper and in FaultyPaymentFixer.moveMoneyFromEarlyCancels), so then
 * informing the customer about it might be better to do in a separate step.
 * 
 * @author john
 *
 */
@Component(value = "customerPaymentInformer")
public class CustomerPaymentInformer {

	public static final Logger logger = Logger.getLogger(CustomerPaymentInformer.class);
	
	@Autowired
	private OrderBundleDAO orderBundleDAO;

	@Autowired
	private OrderDAO orderDAO;

	@Autowired
	private MessageDAO messageDAO;
	
	/**
	 * Sends emails to customers whose orders are in the state STATE_CUSTOMER_PAID
	 * and sets the new state to STATE_CUSTOMER_PAID_INFORMED.
	 */
	public void confirmPaymentToCustomer(OrderBundle bundle) throws Exception {
		orderBundleDAO.refresh(bundle);
		EmailWrapper email = null;

		/* Get all orders for the bundle and exclude all canceled orders (a multi-order
		 * bundle can have some order canceled). */
		List<Order> bundleOrders = new ArrayList<Order>();
		for (Order order : orderDAO.getOrdersInBundle(bundle)) {
			if (!order.getState().equals(Order.STATE_CLOSED_ORDER_CANCELED)) {
				bundleOrders.add(order);
			}
		}

		/* Make sure that _all_ orders in the bundle are paid (it's an error if they aren't, because
		 * faultyPaymentFixer shouldn't set the state to "paid" until also the shipping cost is paid). */
		BigDecimal expectedPaymentMC = bundle.getCustomerShippingCostInMarketCurrencyInclVat();
		for (Order order : bundleOrders) {
			if (!order.getState().equals(Order.STATE_CUSTOMER_PAID)) {
				if (order.getState().equals(Order.STATE_NEW_OUT_OF_STOCK)) {
					/* This one comes very rarely. It comes only if the customer has paid for _all_ orders
					 * in the bundle although some order is in state "new out of stock". */
					logger.info("Bundle " + bundle.getId() + " has some order in \"new out of stock\" although"
							+ " the orders are paid. Don't inform the customer about the payment until the"
							+ " \"new out of stock\" order is either canceled or has changed to state \"new\".");
					return;
				} else {
					throw new Exception("Some order in bundle " + bundle.getId() + " isn't paid.");
				}
			}
			expectedPaymentMC = expectedPaymentMC.add(order.getFinalSalesPriceInMarketCurrencyInclVat());
		}

		//TODO: change this one so that this email is built up of smaller elements. At the
		//moment we have for example moneyReceivedForBundleEmail and also shippedByProviderAddressInfo
		//and perhaps it would be easier to administer these if the emails are built up out of
		//smaller elements that are combined.
		logger.debug("Total price for bundle " + bundle.getId() + ":" + expectedPaymentMC);
		VirtualCoin vcExpectedPaymentMC = new VirtualCoin(bundle.getUsedCustomerCurrency(),expectedPaymentMC);
		if (bundleOrders.size() > 1) {
			// Use the language of the first order
			String lang = bundleOrders.get(0).getMarket().getLanguage();
			String orderReceivedEmail = messageDAO.getText("moneyReceivedForBundleEmail", bundle.getSeller(), lang,
					"TOTAL_PRICE",CurrencyFormatter.print(vcExpectedPaymentMC, Statics.FINNISH),
					"BUNDLE_ID",""+bundle.getId(),
					"CUSTOMER_NAME",bundle.getFirstName() + " " + bundle.getLastName(),
					"CUSTOMER_ADDRESS",bundle.getAddress(),
					"CUSTOMER_POSTCODE",bundle.getPostcode(),
					"CUSTOMER_CITY",bundle.getCity(),
					"SIGNATURE",bundle.getSeller().getSignature());
			email = new EmailWrapper(bundle.getSeller().getSystemEmail(),bundle.getCustomer().getEmail(),
					"Maksu saapunut",orderReceivedEmail);
		} else {
			Order order = bundleOrders.get(0);
			String orderReceivedEmail = messageDAO.getText("moneyReceivedEmail", bundle.getSeller(), order.getMarket().getLanguage(),
					"TOTAL_PRICE",CurrencyFormatter.print(vcExpectedPaymentMC, Statics.FINNISH),
					"ORDER_ID",""+order.getId(),
					"AD_HEADLINE",order.getAd().getAdTemplate().getHeadline(),
					"CUSTOMER_NAME",bundle.getFirstName() + " " + bundle.getLastName(),
					"CUSTOMER_ADDRESS",bundle.getAddress(),
					"CUSTOMER_POSTCODE",bundle.getPostcode(),
					"CUSTOMER_CITY",bundle.getCity(),
					"SIGNATURE",bundle.getSeller().getSignature());
			email = new EmailWrapper(bundle.getSeller().getSystemEmail(),bundle.getCustomer().getEmail(),
					"Maksu saapunut",orderReceivedEmail);
		}

		/* Update the statuses to "paid informed". */
		for (Order order : bundleOrders) {
			logger.debug("Setting state to STATE_CUSTOMER_PAID_INFORMED for order " + order.getId() + " (nippu " + bundle.getId() + ").");
			order.setStateWithHistory(Order.STATE_CUSTOMER_PAID_INFORMED);
		}
		
		/* Finally send out the email. */
		logger.debug("Sending payment confirmation email for bundle " + bundle.getId());
		EmailSender.sendEmailBatch(email.getSystemEmail(),email.getAddress(),email.getSubject(),email.getText());
	}
	
}
