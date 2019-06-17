package fi.jonix.huutonet.market.googleorder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.mail.Message;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import fi.jonix.huutonet.bank.FaultyPaymentFixer;
import fi.jonix.huutonet.domain.model.Customer;
import fi.jonix.huutonet.domain.model.Order;
import fi.jonix.huutonet.domain.model.Seller;
import fi.jonix.huutonet.domain.model.dao.CustomerDAO;
import fi.jonix.huutonet.domain.model.dao.OrderDAO;
import fi.jonix.huutonet.email.EmailSender;
import fi.jonix.huutonet.email.EmailWrapper;
import fi.jonix.huutonet.exception.CriticalSectionInfo;
import fi.jonix.huutonet.exception.UnprocessableEmailException;
import fi.jonix.huutonet.market.ListerStrategy;
import fi.jonix.huutonet.market.SellerEmailFilter;
import fi.jonix.huutonet.order.ContactInfoChecker;
import fi.jonix.huutonet.order.OrderBundleHandler;
import fi.jonix.huutonet.order.OrderCancelationHandler;
import fi.jonix.huutonet.order.OrderCreator;
import fi.jonix.huutonet.order.OrderInformation;

@Component(value = "googleOrderProcessor")
public class GoogleOrderProcessor extends SellerEmailFilter {

	public static final Logger logger = Logger.getLogger(GoogleOrderProcessor.class);

	private static final String ORDER_RECEIVED_ID_STRING = "MARKET_SALES_ID";
	private static final String ORDER_CANCELLATION_REQUEST_ID_STRING = "REFERENCE_NUMBER";
	private static final String REFUND_CONFIRMATION_ID_STRING = "ACCOUNT_NUMBER";
	private static final String ADDRESS_CHANGE_ID_STRING = "ADDRESS";
	private static final String STATUS_QUERY_ID_STRING = "EMAIL_ADDRESS";
	private static final String BILL_QUERY_ID_STRING = "EMAIL_ADDRESS";

	//private static final String GOOGLE_ORDER_SOURCE_EMAIL_ADDRESS = "someemail2@gmail.com";
	//private static final String GOOGLE_ORDER_SOURCE_EMAIL_ADDRESS = "some@email.fi";
	private static final String GOOGLE_ORDER_SOURCE_EMAIL_ADDRESS = "someemail3@gmail.com";
	
	@Autowired
	private ListerStrategy strategy;
	
	@Autowired
	private OrderDAO orderDAO;
	
	@Autowired
	private CustomerDAO customerDAO;
	
	@Autowired
	private FaultyPaymentFixer faultyPaymentFixer;
	
	@Autowired
	private OrderCancelationHandler orderCancelationHandler;
	
	@Autowired
	private OrderBundleHandler orderBundleHandler;
	
	/* Processes messages from google order telling that a new order has been placed. */
	private boolean processOrderReceived(String contents, Seller seller) throws Exception {
		String firstName = getTagValue(contents,"FIRST_NAME");
		String lastName = getTagValue(contents,"LAST_NAME");
		String address = getTagValue(contents,"ADDRESS");
		String postCode = getTagValue(contents,"POST_CODE");
		String city = getTagValue(contents,"CITY");
		String marketSalesId = getTagValue(contents,"MARKET_SALES_ID");
		String email = getTagValue(contents,"EMAIL_ADDRESS");
		String marketStr = getTagValue(contents,"MARKET");
		String amountStr = getTagValue(contents,"COUNT");
		//String providerStr = getTagValue(contents,"PROVIDER");
		logger.info("New order notification received from google order.");
		logger.debug("firstName = " + firstName + ", lastName = " + lastName + ", address = " + address + ", postCode = " + postCode);
		logger.debug("city = " + city + ", marketSalesId = " + marketSalesId + ", email = " + email);
		logger.debug("marketStr = " + marketStr + ", amountStr = " + amountStr);
		int amount = Integer.parseInt(amountStr);
		
		/* Don't allow greater amounts than 10. This was added since a grumpy customer tried
		 * to order 10000 units of something for a non-existing address. */
		if (amount > 10) {
			String operatorEmail = "Problem: somebody wanted to order " + amount + " units of marketSalesId " + marketSalesId + "\n"
				+ "through google-order. If this seems to be correct, then please fix this manually, or if it seems to be\n"
				+ "vandalism, then you can forget about this order.\n\n"
				+ "Here is information regarding the order:\n"
				+ "first name: " + firstName + "\n"
				+ "last name: " + lastName + "\n"
				+ "address: " + address + "\n"
				+ "postcode: " + postCode + "\n"
				+ "city: " + city + "\n"
				+ "\nemail: " + email;
			EmailSender.sendOperationProblemReport("Vandalism at google-order?",operatorEmail,seller.getSystemEmail());
			return true;
		}
		
		logger.debug("New order came through google-order for ad " + marketSalesId + " (email = " + email + ")");
		
		OrderCreator.createNewOrders(firstName,lastName,address,postCode,city,
				email,null,null,marketStr,marketSalesId,seller,amount,null);
		return true;
	}
	
	/* Processes messages from google order telling that an order cancellation request has come. */
	private boolean processOrderCancellationRequest(String contents, Seller seller) throws Exception {
		logger.info("Order cancellation request received from google order.");
		String referenceNbr = getTagValue(contents,"REFERENCE_NUMBER");
		logger.debug("referenceNbr = " + referenceNbr);
		Order order = orderDAO.getByReferenceNumber(referenceNbr,seller);
		if (order == null) {
			throw new UnprocessableEmailException("Order having reference number " + referenceNbr + " not found in database (cancellation request).");
		}
		orderCancelationHandler.handleCancelationRequest(order);
		return true;
	}
	
	/**
	 * Processes messages from google order telling that an order refund confirmation has come.
	 */
	private boolean processOrderRefundConfirmation(String contents, Seller seller) throws Exception {
		logger.info("Order refund confirmation received from google order."); // (singleOrder = " + singleOrder + ").");
		String accountNumber = getTagValue(contents,"ACCOUNT_NUMBER");
		String confirmationCode = getTagValue(contents,"CONFIRMATION_CODE");
		logger.debug("accountNumber = " + accountNumber + ", confirmationCode = " + confirmationCode);
		Customer customer = customerDAO.getCustomerByConfirmationCode(confirmationCode);
		if (customer == null) {
			String operatorEmail = "Faulty confirmation code received for refund paid-too-much confirmation: "
				+ confirmationCode + " (accountNumber = " + accountNumber + ").";
			EmailSender.sendOperationProblemReport("Faulty refund too-much-paid confirmation received: "
					+ confirmationCode,operatorEmail,seller.getSystemEmail());
		} else if (accountNumber == null || accountNumber.trim().equals("")) {
			//TODO: Take the contents of this email from the database.
			String errorEmail = "Moi,\n\nHyvityksen vahvistusta ei hyväksytty. Tilinumero ei saa olla tyhjä.\n\n"
				+ "Ystävällisin terveisin,\n" + seller.getSignature() + "\n";
			EmailSender.sendEmailBatch(seller.getSystemEmail(),customer.getEmail(),
					"Virheellinen tilinumero hyvitykselle (vahvistuskoodi " + confirmationCode + ")",errorEmail);
		} else {
			customer.setAccountNumber(accountNumber);
			//TODO: Take the contents of this email from the database.
			String infoEmail = "Moi,\n\nHyvitysvahvistuksesi on vastaanotettu onnistuneesti (vahvistuskoodi " + confirmationCode + ").\n"
				+ "Rahat maksetaan viikon sisällä.\n\nYstävällisin terveisin,\n" + seller.getSignature() + "\n";
			EmailSender.sendEmailBatch(seller.getSystemEmail(),customer.getEmail(),
					"Hyvitysvahvistus vastaanotettu (vahvistuskoodi " + confirmationCode + ")",infoEmail);
		}
		return true;
	}
	
	/* Processes address change messages from google order. */
	private boolean processAddressChange(String contents, Seller seller) throws Exception {
		String referenceNumber = getTagValue(contents,"REFERENCE_NUMBER");
		String firstName = getTagValue(contents,"FIRST_NAME");
		String lastName = getTagValue(contents,"LAST_NAME");
		String address = getTagValue(contents,"ADDRESS");
		String postCode = getTagValue(contents,"POST_CODE");
		String city = getTagValue(contents,"CITY");
		String email = getTagValue(contents,"EMAIL_ADDRESS");
		logger.debug("firstName = " + firstName);
		logger.debug("lastName = " + lastName);
		logger.debug("address = " + address);
		logger.debug("postCode = " + postCode);
		logger.debug("city = " + city);
		logger.debug("email = " + email);
		logger.debug("referenceNumber = " + referenceNumber);
		logger.info("Address change request received from google order.");

		/* Get the order from database. */
		boolean onlyChangeOneOrder = false;
		if (referenceNumber.endsWith("_only")) {
			onlyChangeOneOrder = true;
			referenceNumber = referenceNumber.substring(0, referenceNumber.length()-5);
			logger.debug("new referenceNumber = " + referenceNumber);
		}
		Order order = orderDAO.getByReferenceNumber(referenceNumber,seller);
		
		/* Get the customer if possible (not possible if the email address was changed). */
		Customer customer = customerDAO.getCustomerByEmailAddress(email);
		
		if (order != null && customer != null && !order.getOrderBundle().getCustomer().getId().equals(customer.getId())) {
			String operatorEmail = "Address change problem: customer " + order.getOrderBundle().getCustomer().getId() + " tried to change\n"
					+ "email address to the address of another (existing) customer (customer id = " + customer.getId() + ").\n"
					+ "This needs to be resolved manually.";
			EmailSender.sendOperationProblemReport("Faulty email address change for customer "
					+ order.getOrderBundle().getCustomer().getId() + "/" + customer.getId(),operatorEmail,seller.getSystemEmail());
		} else if (order != null && referenceNumber != null && order.getCustomerReferenceNumber().equals(referenceNumber)) {
			if (onlyChangeOneOrder) {
				orderBundleHandler.updateAddressOfSingleOrderInBundle(order.getOrderBundle(),order,
						customer,firstName,lastName,address,postCode,city,email,true);
			} else {
				orderBundleHandler.updateAddressOfBundle(order.getOrderBundle(),customer,firstName,
						lastName,address,postCode,city,email,true);
			}
		} else {
			logger.warn("Illegal address change detected (couldn't be automatically fixed).");
			EmailSender.sendOperationProblemReport("Faulty email address change for customer",
					"Illegal address change detected (couldn't be automatically fixed).",seller.getSystemEmail());
		}
		return true;
	}
	
	/* Processes order status queries from google order. */
	private boolean processStatusQuery(String contents, Seller seller) throws Exception {
		String email = getTagValue(contents,"EMAIL_ADDRESS");
		logger.debug("email = " + email);
		if (email == null) {
			return false;
		}
		logger.info("Order status query received from google order.");
		if (email.trim().equals("") || !ContactInfoChecker.correctEmailAddress(email)) {
			logger.warn("Faulty email received from google-orders status query (email = " + email + ")");
			return true;
		}

		/* Get the customer. */
		Customer customer = customerDAO.getCustomerByEmailAddress(email);
		if (customer == null) {
			logger.warn("Faulty email received from google-orders status query (email = " + email + " not found in the customer database)");
			return true;
		}
		
		/* Get the orders of the customer from database. */
		List<Order> orderList = orderDAO.getOrdersByCustomer(customer,seller);

		StringBuffer strBuf = new StringBuffer();
		strBuf.append("Hei,\n\nTässä statustietoa avoimista tilauksistasi:\n\n");
		//strBuf.append(OrderInformation.getCustomerAddressInfo(customer) + "\n");
		strBuf.append(OrderInformation.getOrderStockInfo(orderList,false));
		strBuf.append("\n\n\n\nYstävällisin terveisin,\n" + seller.getSignature() + "\n");
		EmailSender.sendEmailBatch(seller.getSystemEmail(),email, "Tilaustesi status", strBuf.toString());
		return true;
	}
	
	/* Processes order status queries from google order. */
	private boolean processCommonBillQuery(String contents, Seller seller) throws Exception {
		String email = getTagValue(contents,"EMAIL_ADDRESS");
		logger.debug("email = " + email);
		if (email == null) {
			return false;
		}
		logger.info("Common bill query received from google order.");
		if (email.trim().equals("") || !ContactInfoChecker.correctEmailAddress(email)) {
			logger.warn("Faulty email received from google-orders common bill query (email = " + email + ")");
			return true;
		}

		/* Get the customer. */
		Customer customer = customerDAO.getCustomerByEmailAddress(email);
		if (customer == null || orderDAO.getOrdersByCustomer(customer, seller).size() == 0) {
			// We get here if customer isn't recognized OR if the customer is recognized but the
			// customer hasn't bought anything from the seller in question.
			logger.warn("Faulty email received from google-orders common bill query (email = " + email
					+ " not found in the customer database, or else the customer hasn't bought anything"
					+ " from this seller)");
			return true;
		}

		faultyPaymentFixer.generateCommonBill(customer,seller);
		return true;
	}
	
	@Override
	public boolean processSellerEmail(Message message, Seller seller,
			CriticalSectionInfo processingInfo) throws Exception {
		/* Check if it's an order received message from google order. */
		String contents = matchEmail(message,GOOGLE_ORDER_SOURCE_EMAIL_ADDRESS,"GAE_NEW_ORDER",true,ORDER_RECEIVED_ID_STRING);
		if (contents != null) {
			return processOrderReceived(contents,seller);
		}

		/* Check if it's an order cancellation request from google order. */
		contents = matchEmail(message,GOOGLE_ORDER_SOURCE_EMAIL_ADDRESS,"GAE_CANCEL_ORDER",true,ORDER_CANCELLATION_REQUEST_ID_STRING);
		if (contents != null) {
			return processOrderCancellationRequest(contents,seller);
		}

		/* Check if it's an order refund confirmation from google order. */
		contents = matchEmail(message,GOOGLE_ORDER_SOURCE_EMAIL_ADDRESS,"GAE_REFUND_FOR_ORDER",true,REFUND_CONFIRMATION_ID_STRING);
		if (contents != null) {
			return processOrderRefundConfirmation(contents,seller);
		}

		/* Check if it's an address change from google order. */
		contents = matchEmail(message,GOOGLE_ORDER_SOURCE_EMAIL_ADDRESS,"GAE_ADDRESS_CHANGE",true,ADDRESS_CHANGE_ID_STRING);
		if (contents != null) {
			return processAddressChange(contents,seller);
		}

		/* Check if it's a order status query from google order. */
		contents = matchEmail(message,GOOGLE_ORDER_SOURCE_EMAIL_ADDRESS,"GAE_STATUS",true,STATUS_QUERY_ID_STRING);
		if (contents != null) {
			return processStatusQuery(contents,seller);
		}

		/* Check if it's a common bill query from google order. */
		contents = matchEmail(message,GOOGLE_ORDER_SOURCE_EMAIL_ADDRESS,"GAE_BILL",true,BILL_QUERY_ID_STRING);
		if (contents != null) {
			return processCommonBillQuery(contents,seller);
		}

		return false;
	}

}
