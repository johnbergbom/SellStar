package fi.jonix.huutonet.email.processor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.mail.Message;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import fi.jonix.huutonet.bank.BankHelper;
import fi.jonix.huutonet.domain.VirtualCoin;
import fi.jonix.huutonet.domain.model.Currency;
import fi.jonix.huutonet.domain.model.Order;
import fi.jonix.huutonet.domain.model.OrderBundle;
import fi.jonix.huutonet.domain.model.Product;
import fi.jonix.huutonet.domain.model.Provider;
import fi.jonix.huutonet.domain.model.ProviderQuestion;
import fi.jonix.huutonet.domain.model.Seller;
import fi.jonix.huutonet.domain.model.Transaction;
import fi.jonix.huutonet.domain.model.User;
import fi.jonix.huutonet.domain.model.dao.CategoryDAO;
import fi.jonix.huutonet.domain.model.dao.CurrencyDAO;
import fi.jonix.huutonet.domain.model.dao.MessageDAO;
import fi.jonix.huutonet.domain.model.dao.OrderBundleDAO;
import fi.jonix.huutonet.domain.model.dao.OrderDAO;
import fi.jonix.huutonet.domain.model.dao.ProviderDAO;
import fi.jonix.huutonet.domain.model.dao.ProviderQuestionDAO;
import fi.jonix.huutonet.domain.model.dao.TransactionDAO;
import fi.jonix.huutonet.domain.model.dao.UserDAO;
import fi.jonix.huutonet.email.EmailSender;
import fi.jonix.huutonet.exception.CriticalSectionInfo;
import fi.jonix.huutonet.exception.ProcessEmailLaterException;
import fi.jonix.huutonet.exception.ProviderTemporarilyDownException;
import fi.jonix.huutonet.exception.UnprocessableEmailException;
import fi.jonix.huutonet.market.SellerEmailFilter;
import fi.jonix.huutonet.order.OrderCancelationHandler;
import fi.jonix.huutonet.order.ProviderHandler;
import fi.jonix.huutonet.tools.CurrencyFormatter;
import fi.jonix.huutonet.tools.Statics;
import fi.jonix.huutonet.tools.StatisticsGenerator;
import fi.jonix.huutonet.tools.StringUtilities;

/**
 * This class processes messages from the operators of this program.
 * 
 * @author john
 *
 */
@Component(value = "operatorProcessor")
public class OperatorProcessor extends SellerEmailFilter {

	public static final Logger logger = Logger.getLogger(OperatorProcessor.class);
	
	private static final String ON_VACATION_ID_STRING = "Olen lomalla ja palaan töihin";
	
	@Autowired
	private OrderDAO orderDAO;
	
	@Autowired
	private TransactionDAO transactionDAO;
	
	@Autowired
	private BankHelper bankHelper;
	
	@Autowired
	private UserDAO userDAO;
	
	@Autowired
	private MessageDAO messageDAO;
	
	@Autowired
	private OrderCancelationHandler orderCancelationHandler;
	
	@Autowired
	ProviderDAO providerDAO;
	
	@Autowired
	CategoryDAO categoryDAO;
	
	@Autowired
	StatisticsGenerator statisticsGenerator;
	
	@Autowired
	private ProviderQuestionDAO providerQuestionDAO;
	
	@Autowired
	private ProviderHandler providerHandler;
	
	@Autowired
	private OrderBundleDAO orderBundleDAO;
	
	@Autowired
	private CurrencyDAO currencyDAO;
	
	/**
	 * If we get an email from administrator having the subject Status, then send a status report
	 * back to the administrator.
	 */
	private boolean processStatusMessage(String contents, String emailAddress, Seller seller) throws Exception {
		StringBuffer strBuf = statisticsGenerator.generateSalesReport();
		EmailSender.sendEmailBatch(seller.getSystemEmail(),emailAddress,"Status report",strBuf.toString());
		return true;
	}

	private boolean processProductStatisticsMessage(String contents, String emailAddress, Seller seller) throws Exception {
		StringBuffer strBuf = statisticsGenerator.generateProductStatistics();
		EmailSender.sendEmailBatch(seller.getSystemEmail(),emailAddress,"Product statistics report",strBuf.toString());
		return true;
	}

	private boolean processAddProviderConversation(String contents, String operatorEmailAddress, Seller seller) throws Exception {
		String orderBundleIdStr = getTagValue(contents, "ORDER_BUNDLE");
		String providerOrderIdStr = getTagValue(contents, "PROVIDER_ORDER_ID");
		if (orderBundleIdStr == null && providerOrderIdStr == null) {
			return false;
		}
		if (orderBundleIdStr != null && providerOrderIdStr != null) {
			throw new UnprocessableEmailException("You cannot specify both ORDER_BUNDLE and PROVIDER_ORDER_ID.");
		}
		OrderBundle bundle = null;
		if (orderBundleIdStr != null) {
			bundle = orderBundleDAO.get(Long.parseLong(orderBundleIdStr));
		} else {
			//TODO: remove the hardcoding for FocalPrice (we would then need to specify the
			//provider as a tag in the email).
			Provider fc = providerDAO.getProviderByName(Provider.FOCAL_PRICE_PROVIDER_NAME);
			bundle = orderBundleDAO.getOrderBundleByProviderOrderId(providerOrderIdStr, fc);
		}
		if (bundle == null) {
			throw new UnprocessableEmailException("Bundle not found in SellStar's database when running add provider conversation.");
		}
		logger.info("Request received: add provider conversation to SellStar.");
		try {
			providerHandler.updateConversationAtProvider(bundle, null);
		} catch (ProviderTemporarilyDownException e) {
			throw new ProcessEmailLaterException("Provider temporarily down when adding provider conversation for bundle " + bundle.getId());
		}
		return true;
	}
	
	private boolean processAnswerToProviderConversation(String contents, String operatorEmailAddress, Seller seller) throws Exception {
		String answerToProviderStr = getTagValue(contents, "ANSWER_TO_PROVIDER");
		if (answerToProviderStr == null) {
			return false;
		}
		String answerToCustomerStr = getTagValue(contents, "ANSWER_TO_CUSTOMER");
		if (answerToCustomerStr == null) {
			return false;
		}
		String provQuestionIdStr = getTagValue(contents, "PROVIDER_QUESTION_ID");
		if (provQuestionIdStr == null) {
			return false;
		}
		String orderBundleIdStr = getTagValue(contents, "ORDER_BUNDLE_ID");
		if (orderBundleIdStr == null) {
			return false;
		}
		logger.info("Answer to provider conversation received.");
		ProviderQuestion providerQuestion = null;
		OrderBundle bundle = null;
		if (provQuestionIdStr.equals("")) {
			/* No conversation exists at provider. */
			bundle = orderBundleDAO.get(Long.parseLong(orderBundleIdStr));
		} else {
			providerQuestion = providerQuestionDAO.get(Long.parseLong(provQuestionIdStr));

			/* Safety check. */
			if (providerQuestion == null) {
				throw new UnprocessableEmailException("Provider question " + provQuestionIdStr + " not found in database.");
			}
			bundle = providerQuestion.getOrderBundle();

			/* Safety check. */
			if (!Long.valueOf(orderBundleIdStr).equals(bundle.getId())) {
				throw new UnprocessableEmailException("Bundle " + orderBundleIdStr
						+ " doesn't belong to provider conversation " + providerQuestion.getId() + ".");
			}
		}
		boolean answerToCustomer = new Boolean(answerToCustomerStr);
		boolean answerToProvider = new Boolean(answerToProviderStr);
		if (!answerToCustomer && !answerToProvider) {
			throw new UnprocessableEmailException("No action (answerToCustomer and answerToProvider are both false)");
		}

		/* Safety check. */
		String customerEmailAddress = getTagValue(contents, "CUSTOMER_EMAIL_ADDRESS");
		customerEmailAddress = StringUtilities.fixNewLinesToUnix(customerEmailAddress).replace("\n","");
		if (!bundle.getCustomer().getEmail().equals(customerEmailAddress)) {
			throw new UnprocessableEmailException("Disagreement: email for provider question "
					+ (providerQuestion != null ? providerQuestion.getId() : " for bundle " + bundle.getId())
					+ " is " + bundle.getCustomer().getEmail() + " and not " + customerEmailAddress);
		}
		
		/* Safety check: refuse to handle this email if all orders are already sent and we're supposed to
		 * send a message to the provider. */
		if (answerToProvider) {
			boolean someUnshipped = false;
			for (Order order : orderDAO.getOrdersInBundle(bundle)) {
				if (order.getState().intValue() < Order.STATE_SHIPPED_BY_PROVIDER) {
					someUnshipped = true;
				}
			}
			if (!someUnshipped) {
				logger.debug("Refusing to handle answer to provider conversation because all orders are shipped or canceled.");
				throw new UnprocessableEmailException("Refusing to handle answer to provider conversation"
						+ " for bundle " + bundle.getId() + ":\n"
						+ " Seller wanted to send a message to provider but all orders in the bundle are now"
						+ "shipped or canceled. Please deal with this one manually.\n\n"
						+ (answerToCustomer ? "NOTE: the seller also wanted to send a message to the customer"
								+ " but that wasn't sent either!": ""));
			}
		}
		
		if (answerToCustomer) {
			String customerEmailSubject = getTagValue(contents, "CUSTOMER_EMAIL_SUBJECT");
			customerEmailSubject = StringUtilities.replaceNewLinesWithSpace(customerEmailSubject);
			String answer = getTagValue(contents, "ANSWER_TO_CUSTOMER_MESSAGE");
			if (StringUtilities.sloppyMatch("",answer.replaceAll("Moi","").replaceAll("Ystävällisin terveisin","").replaceAll(seller.getSignature(),""))) {
				String newEmail = "You tried to send an empty email to customer " + customerEmailAddress + ".\n"
					+ "Remember that the answer MUST be within the <ANSWER_TO_CUSTOMER_MESSAGE></ANSWER_TO_CUSTOMER_MESSAGE> tag!\n\n"
					+ "Please answer again!!";
				EmailSender.sendEmailBatch(seller.getSystemEmail(),operatorEmailAddress,"Cannot answer to provider conversation",newEmail);
			} else {
				EmailSender.sendEmailBatch(seller.getSystemEmail(),customerEmailAddress,customerEmailSubject,answer,bundle);
			}
		}
		if (answerToProvider) {
			String providerMessageSubject = getTagValue(contents, "PROVIDER_MESSAGE_SUBJECT");
			providerMessageSubject = StringUtilities.replaceNewLinesWithSpace(providerMessageSubject);
			logger.debug("providerMessageSubject = " + providerMessageSubject);
			String answer = getTagValue(contents, "ANSWER_TO_PROVIDER_MESSAGE");
			providerHandler.addQuestionToProvider(providerQuestion, bundle,
					(providerQuestion == null ? providerMessageSubject : null), answer, ProviderHandler.PROVIDER_CONVERSATION_TYPE_SHIPPING_STATUS);
		}
		return true;
	}
	
	private boolean processAnswerToEmailFromCustomer(String contents, String operatorEmailAddress, Seller seller) throws Exception {
		String answer = getTagValue(contents, "ANSWER");
		if (answer == null) {
			return false;
		}
		logger.debug("answer = " + answer);
		String oldEmail = getTagValue(contents, "OLD_EMAIL");
		if (oldEmail == null) {
			return false;
		}
		String customerEmailAddress = getTagValue(contents, "CUSTOMER_EMAIL_ADDRESS");
		if (customerEmailAddress == null) {
			return false;
		}
		customerEmailAddress = StringUtilities.fixNewLinesToUnix(customerEmailAddress).replace("\n","");
		logger.debug("customerEmailAddress = " + customerEmailAddress);
		String originalSubject = getTagValue(contents, "ORIGINAL_SUBJECT");
		if (originalSubject == null) {
			return false;
		}
		originalSubject = StringUtilities.replaceNewLinesWithSpace(originalSubject);
		logger.debug("originalSubject = " + originalSubject);

		/* Make a check in order to avoid sending empty emails to the customers. */
		if (StringUtilities.sloppyMatch("",answer.replaceAll("Moi","").replaceAll("Ystävällisin terveisin","").replaceAll(seller.getSignature(),""))) {
			String newEmail = "You tried to send an empty email to customer " + customerEmailAddress + ".\n"
				+ "Remember that the answer MUST be within the <ANSWER></ANSWER> tag!\n\n"
				+ "Please answer again!!";
			EmailSender.sendEmailBatch(seller.getSystemEmail(),operatorEmailAddress,"Cannot send empty email to customer",newEmail);
		} else {
			/* Check if this email should be associated with some bundle. */
			OrderBundle bundleToAssociateWith = null;
			List<Order> orderList = orderDAO.getOrdersByCustomerEmailAddress(customerEmailAddress,seller);
			for (Order order : orderList) {
				if (order.getState().intValue() >= Order.STATE_CUSTOMER_PAID_INFORMED
						&& order.getState().intValue() < Order.STATE_SHIPPED_BY_PROVIDER) {
					bundleToAssociateWith = order.getOrderBundle();
					break; //pick the first one found (hard to do any better)
				}
			}
			
			String newEmail = answer
				+ "\n\n\n"
				+ "----------------\n"
				+ oldEmail;
			EmailSender.sendEmailBatch(seller.getSystemEmail(),customerEmailAddress,originalSubject,newEmail,bundleToAssociateWith);
		}
		logger.info("Answer to email from customer received.");
		return true;
	}
	
	private boolean processUnmappedTransaction(String contents, String operatorEmailAddress, Seller seller) throws Exception {
		String trIdStr = getTagValue(contents,"TRANSACTION_ID");
		String operDescr = getTagValue(contents,"OPERATOR_DESCRIPTION");
		String action = getTagValue(contents,"ACTION");
		String actionDescr = getTagValue(contents,"ACTION_DESCR_" + action);
		logger.debug("trIdStr = " + trIdStr);
		logger.debug("action = " + action);
		logger.debug("actionDescr = " + actionDescr);
		logger.debug("operDescr = " + operDescr);
		if (trIdStr == null || action == null || operDescr == null || action == null) {
			return false;
		}
		logger.info("Answer to unmapped transaction received.");
		Long trId = null;
		try {
			trId = Long.parseLong(trIdStr);
		} catch (NumberFormatException nfe) {
			logger.debug("trIdStr = " + trIdStr);
		}
		if (trId == null || action.trim().equals("") || actionDescr == null || actionDescr.trim().equals("")) {
			String newEmail = "Cannot take action for unmapped transaction " + trIdStr + " because a faulty ACTION was specified.";
			EmailSender.sendEmailBatch(seller.getSystemEmail(),operatorEmailAddress,"Faulty action specified for " + trIdStr,newEmail);
			return true;
		}

		Transaction tr = transactionDAO.get(trId);
		if (tr == null) {
			throw new UnprocessableEmailException("Error: transaction " + trId + " not found in database.");
		}
		if (actionDescr.equals("special")) {
			if (operDescr == null || operDescr.trim().equals("")) {
				String newEmail = "Cannot mark unmapped transaction " + tr.getId() + " as special because mandatory field operator description is empty.";
				EmailSender.sendEmailBatch(seller.getSystemEmail(),operatorEmailAddress,"Faulty operator description specified for " + trIdStr,newEmail);
				return true;
			} else if (tr.getOperatorDescription() != null || tr.getOrder() != null) {
				String newEmail = "Cannot mark transaction " + tr.getId() + " as special because it is already\n"
					+ "special or else it's already mapped to an order.";
				EmailSender.sendEmailBatch(seller.getSystemEmail(),operatorEmailAddress,"Faulty transaction mapping for " + trIdStr,newEmail);
				return true;
			}
			logger.info("Marking transaction " + tr.getId() + " as a special transaction with operator description " + operDescr);
			tr.setOperatorDescription(operDescr);
		} else {
			Long orderId = Long.parseLong(actionDescr);
			Order order = orderDAO.get(orderId);
			//don't check the seller here
			if (order == null) {
				throw new UnprocessableEmailException("Error: order " + orderId + " not found in database.");
			}
			if (tr.getOperatorDescription() != null || tr.getOrder() != null) {
				String newEmail = "Cannot map transaction " + tr.getId() + " to an order because it's already marked\n"
					+ "as special or else it's already mapped to an order.";
				EmailSender.sendEmailBatch(seller.getSystemEmail(),operatorEmailAddress,"Faulty transaction mapping for " + trIdStr,newEmail);
				return true;
			}
			logger.info("Mapping transaction " + tr.getId() + " to order " + order.getId());
			bankHelper.associateTransactionWithOrder(tr,order);
		}
		return true;
	}
	
	private boolean processResendOrder(String contents, String operatorEmailAddress, Seller seller) throws Exception {
		String orderIdsStr = getTagValue(contents,"ORDER_TO_RESEND");
		logger.debug("orderIdsStr = " + orderIdsStr);
		orderIdsStr = StringUtilities.replaceNewLinesWithSpace(orderIdsStr).replaceAll(" ","");
		String custShippingCostMCStr = getTagValue(contents,"CUSTOMER_SHIPPING_COST");
		logger.debug("custShippingCostMCStr = " + custShippingCostMCStr);
		logger.info("Operator resend order command received.");
		
		/* Make sure that the orderIdsStr is on the correct format. */
		List<Long> orderIds = new ArrayList<Long>();
		for (String orderIdStr : orderIdsStr.split(",")) {
			Long orderId = null;
			try {
				orderId = Long.parseLong(orderIdStr);
				orderIds.add(orderId);
			} catch (NumberFormatException nfe) { }
			if (orderIdStr == null || orderIdStr.trim().equals("") || orderId == null) {
				String newEmail = "Faulty orderIds (" + orderIdsStr + ") specified for resending command.\n"
					+ "The orderIds should be a comma separated string of orderIds within an ORDER_TO_RESEND-tag.";
				EmailSender.sendEmailBatch(seller.getSystemEmail(),operatorEmailAddress,"Faulty orderIds specified for resending",newEmail);
				return true;
			}
		}
		
		/* Make sure that the id's are valid, the orders are in allowed states
		 * and that all orders belong to the same bundle. */
		List<Order> orders = new ArrayList<Order>();
		OrderBundle bundle = null;
		for (Long orderId : orderIds) {
			Order order = orderDAO.get(orderId);
			if (order == null || !order.getOrderBundle().getSeller().equals(seller)) {
				String newEmail = "Faulty orderId (" + orderId + ") specified for resending command.\n"
					+ "This orderId wasn't found in the database.";
				EmailSender.sendEmailBatch(seller.getSystemEmail(),operatorEmailAddress,"Unknown orderId specified for resending",newEmail);
				return true;
			} else if (!order.getState().equals(Order.STATE_SHIPPED_BY_PROVIDER) && !order.getState().equals(Order.STATE_SHIPPED_BY_POSTOFFICE)
					&& !order.getState().equals(Order.STATE_CLOSED) && !order.getState().equals(Order.STATE_CLOSED_RETURNED_TO_PROVIDER)) {
				String newEmail = "Order " + orderId + " was in a faulty state, couldn't do resending (state = " + order.getState() + ").\n"
					+ " The only allowed states are \"shipped by provider\", \"shipped by postoffice\", \"closed\""
					+ " and \"closed returned to provider\".";
				EmailSender.sendEmailBatch(order.getOrderBundle().getSeller().getSystemEmail(),operatorEmailAddress,"Problem resending order " + orderId,newEmail);
				return true;
			} else if ((order.getState().equals(Order.STATE_SHIPPED_BY_PROVIDER) || order.getState().equals(Order.STATE_SHIPPED_BY_POSTOFFICE))
					&& order.getTracking() != null && order.getTracking().getFollow()) {
				String newEmail = "Order " + orderId + " still has tracking.follow = true. Refusing to do resending.\n";
				EmailSender.sendEmailBatch(order.getOrderBundle().getSeller().getSystemEmail(),operatorEmailAddress,"Problem resending order " + orderId,newEmail);
				return true;
			}
			if (bundle == null) {
				bundle = order.getOrderBundle();
			}
			if (!bundle.getId().equals(order.getOrderBundle().getId())) {
				String newEmail = "All orders aren't in the same bundle for resending (" + orderIdsStr + ")."
					+ " Cannot resend, please send a correct resend command.";
				EmailSender.sendEmailBatch(order.getOrderBundle().getSeller().getSystemEmail(),operatorEmailAddress,"Problem resending order " + orderId,newEmail);
				return true;
			}
			if (order.getProduct().getProductState().equals(Product.STATE_NO_LONGER_FOR_SALE)) {
				String newEmail = "Cannot resend order " + order.getId() + " for orderIdsStr = " + orderIdsStr
					+ " because product " + order.getProduct().getId() + " is no longer for sale at provider.";
				EmailSender.sendEmailBatch(order.getOrderBundle().getSeller().getSystemEmail(),operatorEmailAddress,"Problem resending order " + orderId,newEmail);
				return true;
			}
			if (!order.getAd().getAdTemplate().getActive()) {
				String newEmail = "Cannot resend order " + order.getId() + " for orderIdsStr = " + orderIdsStr
					+ " because its adTemplate is no longer active.";
				EmailSender.sendEmailBatch(order.getOrderBundle().getSeller().getSystemEmail(),operatorEmailAddress,"Problem resending order " + orderId,newEmail);
				return true;
			}
			//TODO: add a check here which refuses to resend if the order hasn't been in the current state for at least three weeks.
			orders.add(order);
		}

		/* Check if this order has already been resent. This isn't a waterproof check, since
		 * it doesn't prohibit the operator from sending the same resend command several
		 * times in rapid succession (since placeOrder hasn't run in between). However
		 * that doesn't matter because in that case the above state check catches this.
		 * In addition the final check is done in placeOrders, making sure that we never
		 * resend more than once.
		 *   This check is however is good if one order in the bundle has already been resent
		 * and one later wants to resend another one from the same bundle. We don't want to
		 * allow that, because then we would need to pay several times for shipping.
		 *   It won't catch the case where we first send a "resend order" for the first
		 * order in bundle B and then 5 minutes later send a "resend order" for the second
		 * order of bundle B. However that _should_ be allowed (as long as placeOrder hasn't
		 * ran in between), so it's alright that this case isn't caught. */
		if (bundle.getResendLossInEuroInclVat().compareTo(BigDecimal.ZERO) != 0) {
			String newEmail = "Bundle " + bundle.getId() + " (or some order from this bundle) has already been resent once."
				+ " Refusing to do it again (" + orderIdsStr + ").";
			EmailSender.sendEmailBatch(bundle.getSeller().getSystemEmail(),operatorEmailAddress,"Problem resending bundle " + bundle.getId(),newEmail);
			return true;
		}
		
		/* Make sure that the refundable shipping cost is okay. */
		BigDecimal custShippingCostMC = null;
		BigDecimal custShippingCostEuro = null;
		if (custShippingCostMCStr != null) {
			custShippingCostMCStr = custShippingCostMCStr.replace(",", ".");
			boolean faultyCustShippingCost = false;
			try {
				custShippingCostMC = new BigDecimal(custShippingCostMCStr);
				custShippingCostEuro = currencyDAO.convertCurrency(bundle.getUsedCustomerCurrency().getId(), Currency.EURO, custShippingCostMC);
			} catch (NumberFormatException nfe) {
				faultyCustShippingCost = true;
			}
			if (!faultyCustShippingCost && custShippingCostEuro.compareTo(new BigDecimal("7.20")) > 0) {
				/* Some customers send returns as a recommended letter. This shouldn't cost more than 6 euros.
				 * However some products are heavier so that once the shipping cost 6,80 euros.
				 * => Update: Actually once we got a return whose shipping cost was 7.20 euros. */
				faultyCustShippingCost = true;
			}
			if (faultyCustShippingCost) {
				String newEmail = "Faulty custShippingCost (" + custShippingCostMCStr + ") specified for resend order command.\n"
					+ "The custShippingCost should be within an CUSTOMER_SHIPPING_COST-tag.";
				EmailSender.sendEmailBatch(seller.getSystemEmail(),operatorEmailAddress,"Faulty custShippingCost specified for resend order",newEmail);
				return true;
			}
		}

		/* Refund the customerShippingCost if applicable (what the customer paid for shipping when
		 * he returned the product to us). Note that we should NOT refund what the customer paid
		 * for shipping when sending the product from provider to the customer (bundle.customerShippingCost),
		 * so orderCancelationHandler.zeroSalesPriceForRefund should NOT be called here. */
		if (custShippingCostMC != null) {
			Order order = orders.get(0);
			logger.debug("Adding " + custShippingCostMC + " to otherLoss and targetedPaid for order "
					+ order.getId() + " (because customer returned the product and wants a \"resend order\").");
			BigDecimal otherLoss = order.getOtherLossInEuro().add(custShippingCostEuro);
			order.setOtherLossInEuro(otherLoss);
			BigDecimal targetedPaid = order.getTargetedPaidInMarketCurrency().add(custShippingCostMC);
			order.setTargetedPaidInMarketCurrency(targetedPaid);
			//TODO: instead of setting informedAboutTooBigPayment to false, rather do like it's
			//done in processRefundSentOrder: inform the customer about the too big payment and
			//then set informedAboutTooBigPayment = true
			bundle.getCustomer().setInformedAboutTooBigPayment(false);
		}
		
		/* Note: do NOT change order.resendLoss nor bundle.resendLoss here, because these
		 * are set in placeOrder when actually re-ordering the product from the provider. */
		
		/* Update the order state and zero the tracking for the order. */
		//List<Tracking> removedTrackingObjects = new ArrayList<Tracking>();
		for (Order order : orders) {
			order.setStateWithHistory(Order.STATE_SHOULD_RESEND);
			if (order.getTracking() != null) {
				logger.debug("Order " + order.getId() + " had an old tracking code of " + order.getTracking().getTrackingCode() + ".");
				/*if (!removedTrackingObjects.contains(order.getTracking())) {
					removedTrackingObjects.add(order.getTracking());
				}*/
				order.setTracking(null);
			}
		}
		
		/* Remove dangling tracking objects (that no longer have orders
		 * pointing to them). This will probably result in an exception
		 * if one product in a multi-order bundle was resent since the
		 * other orders in the bundle still point to the removed tracking
		 * object.
		 * =>UPDATE: Don't remove the old tracking object, because we need to have it in order to catch the
		 * situation where FocalPrice screws up and gives the same tracking code to several packets.*/
		/*for (Tracking tr : removedTrackingObjects) {
			trackingDAO.delete(tr);
		}*/
		
		return true;
	}
	
	private boolean processRefundSentOrder(String contents, String operatorEmailAddress, Seller seller) throws Exception {
		String orderIdStr = getTagValue(contents,"SENT_ORDER_TO_REFUND");
		logger.debug("orderId = " + orderIdStr);
		String custShippingCostMCStr = getTagValue(contents,"CUSTOMER_SHIPPING_COST");
		logger.debug("custShippingCostMCStr = " + custShippingCostMCStr);
		logger.info("Operator refund sent order command received.");
		Long orderId = null;
		try {
			orderId = Long.parseLong(orderIdStr);
		} catch (NumberFormatException nfe) { }
		if (orderIdStr == null || orderId == null) {
			String newEmail = "Faulty orderId (" + orderIdStr + ") specified for refund sent order command.\n"
				+ "The orderId should be within an SENT_ORDER_TO_REFUND-tag.";
			EmailSender.sendEmailBatch(seller.getSystemEmail(),operatorEmailAddress,"Faulty orderId specified for refund sent order",newEmail);
			return true;
		}
		Order order = orderDAO.get(orderId);
		if (order == null/* || !order.getOrderBundle().getSeller().equals(seller)*/) {
			String newEmail = "Faulty orderId (" + orderIdStr + ") specified for refund sent order command.\n"
				+ "This orderId wasn't found in the database.";
			EmailSender.sendEmailBatch(seller.getSystemEmail(),operatorEmailAddress,
					"Unknown orderId specified for refund sent order",newEmail);
		} else if (!order.getState().equals(Order.STATE_SHIPPED_BY_PROVIDER) && !order.getState().equals(Order.STATE_SHIPPED_BY_POSTOFFICE)
				 && !order.getState().equals(Order.STATE_CLOSED) && !order.getState().equals(Order.STATE_CLOSED_RETURNED_TO_PROVIDER)) {
			String newEmail = "Order " + orderId + " was in a faulty state, couldn't do refund sent order (state = "
				+ order.getState() + ").\nThe only allowed states are \"shipped by provider\", \"shipped by postoffice\", \"closed\""
				+ " and \"closed returned to provider\".";
			EmailSender.sendEmailBatch(order.getOrderBundle().getSeller().getSystemEmail(),
					operatorEmailAddress,"Problem refunding sent order " + orderId,newEmail);
		} else if ((order.getState().equals(Order.STATE_SHIPPED_BY_PROVIDER) || order.getState().equals(Order.STATE_SHIPPED_BY_POSTOFFICE))
				&& order.getTracking() != null && order.getTracking().getFollow()) {
			String newEmail = "Order " + orderId + " still has tracking.follow = true. Refusing to do refunding.\n";
			EmailSender.sendEmailBatch(order.getOrderBundle().getSeller().getSystemEmail(),operatorEmailAddress,"Problem refunding sent order " + orderId,newEmail);
		} else {
			/* Make sure that the refundable shipping cost is okay. */
			Currency usedCustomerCurrency = order.getOrderBundle().getUsedCustomerCurrency();
			BigDecimal custShippingCostMC = null;
			BigDecimal custShippingCostEuro = null;
			if (custShippingCostMCStr != null) {
				custShippingCostMCStr = custShippingCostMCStr.replace(",", ".");
				boolean faultyCustShippingCost = false;
				try {
					custShippingCostMC = new BigDecimal(custShippingCostMCStr);
					custShippingCostEuro = currencyDAO.convertCurrency(usedCustomerCurrency.getId(), Currency.EURO, custShippingCostMC);
				} catch (NumberFormatException nfe) {
					faultyCustShippingCost = true;
				}
				if (!faultyCustShippingCost && custShippingCostEuro.compareTo(new BigDecimal("7.20")) > 0) {
					/* Some customers send returns as a recommended letter. This shouldn't cost more than 6 euros.
					 * However some products are heavier so that once the shipping cost 6,80 euros.
					 * => Update: Actually once we got a return whose shipping cost was 7.20 euros. */
					faultyCustShippingCost = true;
				}
				if (faultyCustShippingCost) {
					String newEmail = "Faulty custShippingCost (" + custShippingCostMCStr + ") specified for refund sent order command.\n"
						+ "The custShippingCost should be within an CUSTOMER_SHIPPING_COST-tag.";
					EmailSender.sendEmailBatch(seller.getSystemEmail(),operatorEmailAddress,"Faulty custShippingCost specified for refund sent order",newEmail);
					return true;
				}
			}

			//TODO: add a check here which refuses to refund if the order hasn't been in the current state for at least three weeks.
			if (order.getFinalSalesPriceInEuroInclVat().compareTo(BigDecimal.ZERO) == 0) {
				throw new RuntimeException("Order " + order.getId() + " seems to have been refunded already. Refusing to refund twice.");
			}
			/* DON'T change the state of the order in this method. */
			//order.setStateWithHistory(Order.STATE_REFUND_PENDING_FOR_SENT_ORDER);
			Map<String,VirtualCoin> targetedProviderCost = new HashMap<String,VirtualCoin>();
			VirtualCoin refundSumMC = orderCancelationHandler.zeroSalesPriceForRefund(order,targetedProviderCost);

			/* Here otherLoss should be increased with what we paid to provider + what the customer
			 * paid to us for shipping when he returned the product. */
			logger.debug("Adding " + targetedProviderCost.get("targetedProviderCostInEuroInclVat") + " to otherLoss (for provider expenses).");
			BigDecimal otherLoss = order.getOtherLossInEuro().add(targetedProviderCost.get("targetedProviderCostInEuroInclVat").getValue());
			if (custShippingCostMC != null) {
				logger.debug("Adding " + custShippingCostEuro + " euro to otherLoss (for customer's extra expenses).");
				otherLoss = otherLoss.add(custShippingCostEuro);
				BigDecimal targetedPaid = order.getTargetedPaidInMarketCurrency();
				order.setTargetedPaidInMarketCurrency(targetedPaid.add(custShippingCostMC));
				refundSumMC = new VirtualCoin(usedCustomerCurrency,refundSumMC.getValue().add(custShippingCostMC));
			}
			logger.debug("Setting otherLoss to " + otherLoss);
			order.setOtherLossInEuro(otherLoss);
			OrderBundle bundle = order.getOrderBundle();
			logger.info("Operator refund sent order for bundle " + bundle.getId() + " (order " + order.getId() + ").");
			String contextPath = messageDAO.getText("contextPath", bundle.getSeller(),order.getMarket().getLanguage());
			String custEmail = messageDAO.getText("refundToCustomerAccepted", bundle.getSeller(), order.getMarket().getLanguage(),
					"ORDER_ID",""+order.getId(),
					"REFUND_SUM",CurrencyFormatter.print(refundSumMC,Statics.FINNISH),
					"GOOGLE_ORDER_URL",bundle.getSeller().getStoreUrl(),
					"CONTEXT_PATH",contextPath,
					"SIGNATURE_URL_ENC",StringUtilities.escapeString2URL(bundle.getSeller().getSignature()),
					"CONFIRMATION_CODE",bundle.getCustomer().getConfirmationCode(),
					"SIGNATURE",bundle.getSeller().getSignature());
			EmailSender.sendEmailBatch(bundle.getSeller().getSystemEmail(),
					bundle.getCustomer().getEmail(),"Hyvitys",custEmail);
			bundle.getCustomer().setInformedAboutTooBigPayment(true);
		}
		return true;
	}
	
	@Override
	public boolean processSellerEmail(Message message, Seller seller,
			CriticalSectionInfo processingInfo) throws Exception {
		List<User> operators = userDAO.getOperators();
		for (User oper : operators) {
			String email = oper.getEmailAddress();
			
			/* Check if Status message from an operator. */
			String contents = matchEmail(message,email,"status",true,null);
			if (contents != null) {
				return processStatusMessage(contents,email,seller);
			}

			/* Check if product statistics message from an operator. */
			contents = matchEmail(message,email,"product statistics",true,null);
			if (contents != null) {
				return processProductStatisticsMessage(contents,email,seller);
			}

			/* Check if an answer to an email from a customer. */
			contents = matchEmail(message,email,"Email from customer",false,null);
			if (contents != null) {
				return processAnswerToEmailFromCustomer(contents,email,seller);
			}

			/* Check if an answer to a provider conversation. */
			contents = matchEmail(message,email,"Provider conversation for bundle",false,null);
			if (contents != null) {
				return processAnswerToProviderConversation(contents,email,seller);
			}

			/* Check if a request to add an existing (manually added) conversation to SellStar. */
			contents = matchEmail(message,email,"Add provider conversation to SellStar",true,null);
			if (contents != null) {
				return processAddProviderConversation(contents,email,seller);
			}

			/* Check if an answer to an unmapped transaction message. */
			contents = matchEmail(message,email,"Unmapped transaction",false,null);
			if (contents != null) {
				return processUnmappedTransaction(contents,email,seller);
			}

			/* Check if this is a command to resend a certain order. */
			contents = matchEmail(message,email,"Resend order",true,null);
			if (contents != null) {
				return processResendOrder(contents,email,seller);
			}

			/* Check if this is a command to refund an already sent order. */
			contents = matchEmail(message,email,"Refund sent order",true,null);
			if (contents != null) {
				return processRefundSentOrder(contents,email,seller);
			}

			/* Check if it's a on vacation email from an operator. */
			contents = matchEmail(message,email,"Olen lomalla",false,ON_VACATION_ID_STRING);
			if (contents != null) {
				logger.info("On vacation email received from operator.");
				return true;
			}
		}
		return false;
	}

}
