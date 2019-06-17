package fi.jonix.huutonet.order;

import java.io.File;
import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import fi.jonix.huutonet.domain.ApplicationContextPlaceholder;
import fi.jonix.huutonet.domain.VirtualCoin;
import fi.jonix.huutonet.domain.model.Ad;
import fi.jonix.huutonet.domain.model.AdTemplate;
import fi.jonix.huutonet.domain.model.Currency;
import fi.jonix.huutonet.domain.model.Email;
import fi.jonix.huutonet.domain.model.Market;
import fi.jonix.huutonet.domain.model.Order;
import fi.jonix.huutonet.domain.model.OrderBundle;
import fi.jonix.huutonet.domain.model.Product;
import fi.jonix.huutonet.domain.model.Provider;
import fi.jonix.huutonet.domain.model.ProviderQuestion;
import fi.jonix.huutonet.domain.model.StateChange;
import fi.jonix.huutonet.domain.model.dao.AdDAO;
import fi.jonix.huutonet.domain.model.dao.AdTemplateDAO;
import fi.jonix.huutonet.domain.model.dao.CurrencyDAO;
import fi.jonix.huutonet.domain.model.dao.EmailDAO;
import fi.jonix.huutonet.domain.model.dao.OrderBundleDAO;
import fi.jonix.huutonet.domain.model.dao.OrderDAO;
import fi.jonix.huutonet.domain.model.dao.ProductDAO;
import fi.jonix.huutonet.domain.model.dao.ProviderQuestionDAO;
import fi.jonix.huutonet.email.EmailSender;
import fi.jonix.huutonet.exception.CriticalSectionInfo;
import fi.jonix.huutonet.exception.ProductNotFoundAtProviderException;
import fi.jonix.huutonet.exception.ProviderTemporarilyDownException;
import fi.jonix.huutonet.exception.SellStarRuntimeException;
import fi.jonix.huutonet.exception.SoldOutAtProviderException;
import fi.jonix.huutonet.market.MarketRobot;
import fi.jonix.huutonet.tools.Statics;

/**
 * This class provides a provider independent layer for handling provider specific tasks
 * and the methods are supposed to be used by higher layer code.
 * 
 * @author john
 *
 */
@Component(value = "providerHandler")
public class ProviderHandler extends ProviderOrder {

	public static final Logger logger = Logger.getLogger(ProviderHandler.class);

	public static String PROVIDER_CONVERSATION_TYPE_SHIPPING_STATUS = "Shipping Status Enquiry";
	public static String PROVIDER_CONVERSATION_TYPE_CANCELATION = "Order change & cancellation";
	
	@Autowired
	private OrderDAO orderDAO;
	
	@Autowired
	List<ProviderOrder> providers;

	@Autowired
	private OrderBundleDAO orderBundleDAO;
	
	@Autowired
	private ProductDAO productDAO;
	
	@Autowired
	private ProviderQuestionDAO providerQuestionDAO;
	
	@Autowired
	private EmailDAO emailDAO;
	
	@Autowired
	private AdDAO adDAO;
	
	@Autowired
	private CurrencyDAO currencyDAO;

	@Autowired
	private OrderCancelationHandler orderCancelationHandler;

	@Autowired
	private AdTemplateDAO adTemplateDAO;

	/**
	 * This method goes through all providers in the provider list until
	 * one is found that matches the provider given as an argument.
	 * @param provider
	 * @return
	 */
	public ProviderOrder getProviderOrder(Provider provider) {
		for (ProviderOrder prov : providers) {
			if (prov.canHandleProvider(provider)) {
				return prov;
			}
		}
		return null;
	}
	
	private boolean hasResending(List<Order> rawOrderList) {
		for (Order order : rawOrderList) {
			if (order.getState().equals(Order.STATE_SHOULD_RESEND)) {
				return true;
			} else if (order.getState().equals(Order.STATE_STOCK_SHORTAGE_BEFORE_ORDER)) {
				for (StateChange sc : order.getStateChanges()) {
					if (sc.getState().equals(Order.STATE_SHOULD_RESEND)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	public void placeOrder(OrderBundle bundle, String[] args, CriticalSectionInfo section) throws Exception {
		section.setInCriticalSection(false);
		section.setTransactionMustGoThrough(false);
		orderBundleDAO.refresh(bundle);
		List<Order> bundleOrders = new ArrayList<Order>();

		/* Safety check. */
		if (bundle.getTargetedPaidShippingCostInMarketCurrency().compareTo(bundle.getCustomerShippingCostInMarketCurrencyInclVat()) < 0) {
			throw new Exception("Internal error in placeOrder: shipping cost of bundle not paid.");
		}
		
		/* Safety check #2: make sure that all orders in the bundle are in suitable states. */
		BigDecimal totalPriceMCExclVat = BigDecimal.ZERO;
		//boolean firstOrdering = false;
		//boolean closedAndDeliveredOrdersInBundle = false;
		List<Order> rawOrderList = orderDAO.getOrdersInBundle(bundle);
		boolean resending = hasResending(rawOrderList);
		for (Order order : rawOrderList) {
			if (!order.getState().equals(Order.STATE_CLOSED_ORDER_CANCELED)) {
				/*if (order.getState().equals(Order.STATE_CUSTOMER_PAID_INFORMED)
						|| order.getState().equals(Order.STATE_STOCK_SHORTAGE_BEFORE_ORDER)) {
					firstOrdering = true;
				} else if (order.getState().equals(Order.STATE_SHOULD_RESEND)) {
					resending = true;
				}*/
				totalPriceMCExclVat = totalPriceMCExclVat.add(order.getFinalSalesPriceInMarketCurrencyExclVat());
				if (!order.getState().equals(Order.STATE_CUSTOMER_PAID_INFORMED)
						&& !order.getState().equals(Order.STATE_STOCK_SHORTAGE_BEFORE_ORDER)
						&& !order.getState().equals(Order.STATE_SHOULD_RESEND)) {
					if (/*(order.getState().equals(Order.STATE_CLOSED) || order.getState().equals(Order.STATE_SHIPPED_BY_POSTOFFICE)) && */resending) {
						////closedAndDeliveredOrdersInBundle = true;
						// Closed and delivered products are allowed to exist in the bundle
						// only if this is a resending, so do nothing here.
						logger.debug("Order " + order.getId() + " of bundle " + bundle.getId() + " has state " + order.getState()
								+ ". Not including for resending.");
					} else {
						throw new Exception("Some order in bundle " + bundle.getId() + " has the wrong state (" + order.getState() + ").");
					}
				} else {
					logger.info("Bundle " + bundle.getId() + " has order " + order.getId() + " (in state " + order.getState() + ").");
					bundleOrders.add(order);
				}
			}
		}
		totalPriceMCExclVat = totalPriceMCExclVat.add(bundle.getCustomerShippingCostInMarketCurrencyExclVat());
		
		/* Safety check #3: closed and delivered products are allowed to exist in the bundle
		 * only if this is a resending. It will crash here the first time we get the following
		 * case: a multi-order bundle has one closed order and one order that should be resent.
		 * Then the order to resend is out of stock and then gets into the state "stock shortage
		 * before order". That's a perfectly fine situation, so fix this safety check when
		 * this happens the first time... The same problem exists for safety check #7. */
		/*if (closedAndDeliveredOrdersInBundle && !resending) {
			throw new Exception("Some order in bundle " + bundle.getId() + " has the wrong state (closed and delivered"
					+ " orders in bundle although this isn't a resending).");
		}*/
		
		/* Safety check #4: make sure that the bundle isn't empty. */
		if (bundleOrders.size() == 0) {
			throw new Exception("Bundle " + bundle.getId() + " has no orders.");
		}
		
		/* Safety check #5: make sure that resendings and first orderings aren't mixed. */
		/*if (firstOrdering == resending) {
			throw new Exception("Bundle " + bundle.getId() + " has mixed resendings and first ordering.");
		}*/
		
		/* Safety check #6: make sure that the total cost isn't too big. */
		if (totalPriceMCExclVat.compareTo(bundle.getMaxBundleSizeInMarketCurrencyExclVat()) > 0) {
			throw new Exception("Bundle " + bundle.getId() + " too expensive (costs " + totalPriceMCExclVat
					+ " " + bundle.getUsedCustomerCurrency().getSymbol()+ ").");
		}
		
		/* Safety check #7: for resendings all orders should be in the same state. This safety check
		 * has the same problem as safety check #3... */
		/*if (resending) {
			Integer state = bundleOrders.get(0).getState();
			for (Order order : bundleOrders) {
				if (!order.getState().equals(state)) {
					throw new Exception("Bundle " + bundle.getId() + " has a resending and all orders not in the same state.");
				}
			}
		}*/
		
		String user = args[1];
		String password = args[2];
		ProviderOrder prov = getProviderOrder(bundle.getProvider());
		if (prov != null) {
			prov.placeOrder(bundle,bundleOrders,user,password,section);
		}
	}
	
	public void updateOrderStatus(OrderBundle bundle, CriticalSectionInfo section) throws Exception {
		section.setInCriticalSection(false);
		section.setTransactionMustGoThrough(false);
		orderBundleDAO.refresh(bundle);
		
		/* Get all orders for the bundle and exclude shipped and canceled orders. */
		List<Order> bundleOrders = new ArrayList<Order>();
		for (Order order : orderDAO.getOrdersInBundle(bundle)) {
			if (order.getState().intValue() < Order.STATE_SHIPPED_BY_PROVIDER) {
				bundleOrders.add(order);
			}
		}

		/* Make sure that all orders are in an updatable state. */
		boolean allOrdersInUpdatableState = true;
		for (Order order : bundleOrders) {
			if (!isInUpdatableState(order)) {
				allOrdersInUpdatableState = false;
			}
		}
		if (!allOrdersInUpdatableState) {
			throw new RuntimeException("Not all orders are in updatable states for bundle " + bundle.getId() + ".");
		}

		ProviderOrder prov = getProviderOrder(bundle.getProvider());
		if (prov != null) {
			prov.updateOrderStatus(bundle,bundleOrders,section);
		}
	}

	/**
	 * Either bundle or providerQuestion has to be null. When updating a conversation that already exists
	 * in SellStar's database, then bundle should be null and providerQuestion should be != null. If we
	 * are instead adding to SellStar a manually added FocalPrice conversation, then bundle should be != null
	 * and providerQuestion should be null.
	 */
	public ProviderQuestion updateConversationAtProvider(OrderBundle bundle, ProviderQuestion providerQuestion) throws Exception {
		if ((bundle != null && providerQuestion != null) || (bundle == null && providerQuestion == null)) {
			throw new Exception("bundle != null AND providerQuestion != null or else both are null");
		}
		Date lastProviderComment = null;
		if (providerQuestion != null) {
			providerQuestionDAO.refresh(providerQuestion);
			lastProviderComment = providerQuestion.getLastProviderComment();
			bundle = providerQuestion.getOrderBundle();
		}
		ProviderOrder prov = getProviderOrder(bundle.getProvider());
		if (prov != null) {
			providerQuestion = prov.updateConversationAtProvider(bundle, providerQuestion);
		}
		/*if (providerQuestion == null) {
			/* Now a new conversation is stored to the database, so let's fetch it from there. /
			providerQuestion = providerQuestionDAO.getProviderQuestionForBundle(bundle);
			lastProviderComment = providerQuestion.getLastProviderComment();
		}*/
		
		/* Figure out to what address the provider conversation should be sent. */
		String forwardProviderEmailsTo = bundle.getSeller().getForwardCustomerEmails();
		if (forwardProviderEmailsTo == null) {
			forwardProviderEmailsTo = bundle.getSeller().getUser().getEmailAddress();
		}
		
		/* Inform the operator if new information has come.
		 * =>UPDATE: no need to inform the operator. The operator will see what the provider
		 * wrote next time the customer writes to us or else housekeeping will inform us
		 * at some point. If the order is soon shipped, then there might be no need to
		 * show the message at all to the operator. */
		List<Order> orderList = orderDAO.getOrdersInBundle(bundle);
		/*if ((lastProviderComment == null && providerQuestion.getLastProviderComment() != null)
				|| (lastProviderComment != null && providerQuestion.getLastProviderComment() != null
						&& providerQuestion.getLastProviderComment().getTime() > lastProviderComment.getTime())) {
			String email = createProviderConversationEmail(providerQuestion, bundle, orderList,null,false);
			EmailSender.sendEmailBatch(bundle.getSeller().getSystemEmail(),forwardProviderEmailsTo,
					"Provider conversation for bundle " + bundle.getId() + " (answer received)", email);
			providerQuestion.setOperatorLastInformed(new Date());
		}*/
		
		/* Check if all orders of the bundle are shipped. */
		boolean someUnshipped = false;
		for (Order order : orderList) {
			if (order.getState().intValue() < Order.STATE_SHIPPED_BY_PROVIDER) {
				someUnshipped = true;
			}
		}
		//logger.debug("someUnshipped = " + someUnshipped);
		
		if (!someUnshipped) {
			/* If none of the orders are unshipped (=all orders are shipped or canceled),
			 * then this conversation can be closed. */
			if (providerQuestion.getLastProviderComment() != null
					&& providerQuestion.getLastProviderComment().getTime() > providerQuestion.getLastOwnComment().getTime()) {
				/* If we are not awaiting any answer then close the conversation in SellStar
				 * without informing the operator. */
				logger.debug("Closing conversation " + providerQuestion.getId() + " because last"
						+ " product is shipped and we aren't waiting for any answer from provider.");
			} else {
				logger.debug("Closing conversation " + providerQuestion.getId() + " because last"
						+ " product is shipped (we were waiting for an answer from provider, but it's"
						+ " not necessary to show it since the last product is shipped).");
				/* If we are awaiting an answer then close the conversation in SellStar
				 * and inform the operator.
				 * =>UPDATE: no need to inform the operator. */
				/*String email = "For your information:\nNo answer was received from"
					+ " provider. However all orders in bundle " + bundle.getId() + " (" + bundle.getProviderOrderId()
					+ ") are shipped or canceled, so this conversation is now closed. Please deal with this one"
					+ " manually if an answer is really needed for this one.\n\n"
					+ createProviderConversationEmail(providerQuestion, bundle, orderList,null,false);
				EmailSender.sendEmailBatch(bundle.getSeller().getSystemEmail(),forwardProviderEmailsTo,
						"Provider conversation for bundle " + bundle.getId() + " (closing thread)", email);
				providerQuestion.setOperatorLastInformed(new Date());*/
			}
			providerQuestion.setOpen(false);
		} else {
			/* If we are expecting an answer but no answer has come within a week, then inform
			 * the operator.
			 * => UPDATE: this isn't necessary because housekeeping will keep informing the operator
			 * once per week if the order is still unshipped + the operator is informed anyway when
			 * a conversation is closed although the provider hasn't given any answer. */
			/*long weekAgo = System.currentTimeMillis() - 1000*3600*24*7;
			//logger.debug("weekAgo = " + weekAgo);
			if ((providerQuestion.getLastProviderComment() == null
					|| providerQuestion.getLastProviderComment().getTime() < providerQuestion.getLastOwnComment().getTime())
					&& providerQuestion.getLastOwnComment().getTime() < weekAgo
					&& (providerQuestion.getOperatorLastInformed() == null
							|| providerQuestion.getOperatorLastInformed().getTime() < weekAgo)) {
				String email = "No answer was received within a week from provider and bundle " + bundle.getId()
					+ " (" + bundle.getProviderOrderId() + ") still has unshipped/uncanceled orders. You might want to"
					+ " kick the provider about this one.\n\n"
					+ createProviderConversationEmail(providerQuestion, bundle, orderList, "What's the status of this one?",false);
				EmailSender.sendEmailBatch(bundle.getSeller().getSystemEmail(),forwardProviderEmailsTo,
						"Provider conversation for bundle " + bundle.getId() + " (unanswered, kick provider?)", email);
				providerQuestion.setOperatorLastInformed(new Date());
			}*/
		}
		return providerQuestion;
	}
	
	//TODO: this method is copy'n'pasted from EmailFilter.java. Refactor!
	private String getTagValue(String text, String field) {
		int startIndex = text.indexOf("<" + field + ">");
		int endIndex = text.indexOf("</" + field + ">");
		if (startIndex < 0 || endIndex < 0) {
			return null;
		}
		String rawContent = text.substring(startIndex + ("<" + field + ">").length(),endIndex);
		
		/* If the string has multiple rows, then let's remove the lines starting with ">"
		 * (because they are usually added automatically when replying to an email). */
		String[] lines = rawContent.split("\n");
		String content = "";
		for (int i = 0; i < lines.length; i++) {
			String line = lines[i].trim();
			while (line.startsWith(">")) {
				line = line.substring(1);
			}
			content += line.trim();
			if (i < lines.length - 1) {
				content += "\n";
			}
		}
		return content;
	}
	
	public String createProviderConversationEmail(ProviderQuestion providerQuestion, OrderBundle bundle, List<Order> orderList,
			String defaultMessageToProvider, boolean answerToCustomer) {
		List<Email> associatedEmails = emailDAO.getEmailsForBundle(bundle);
		StringBuffer customerCommunication = new StringBuffer();
		DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		String customerEmailSubject = "Tiedote";
		for (Email email : associatedEmails) {
			//TODO: don't hardcode hkmyynti@gmail.com/gardenia.myynti@gmail.com nor someuser@gmail.com (rather
			//take from the system_email and users table).
			if ((email.getSender().equals("hkmyynti@gmail.com") || email.getSender().equals("gardenia.myynti@gmail.com"))
					&& email.getReceiver().equals("someuser@gmail.com")
					&& email.getSubject().startsWith("Email from customer")) {
				/* Email from customer. */
				String customerEmailAddress = getTagValue(email.getContents(), "CUSTOMER_EMAIL_ADDRESS");
				customerCommunication.append("From: " + customerEmailAddress + "\n");
				customerCommunication.append("To: " + email.getSender() + "\n");
				customerCommunication.append("Date: " + dateFormat.format(email.getDate()) + "\n");
				String originalSubject = getTagValue(email.getContents(), "ORIGINAL_SUBJECT");
				customerCommunication.append("Subject: " + originalSubject + "\n\n");
				customerEmailSubject = originalSubject;
				String emailContents = getTagValue(email.getContents(), "OLD_EMAIL");
				customerCommunication.append(emailContents + "\n\n----------");
			} else {
				/* Answer to email from customer. */
				customerCommunication.append("From: " + email.getSender() + "\n");
				customerCommunication.append("To: " + email.getReceiver() + "\n");
				customerCommunication.append("Date: " + dateFormat.format(email.getDate()) + "\n");
				customerCommunication.append("Subject: " + email.getSubject() + "\n\n");
				customerEmailSubject = email.getSubject();
				customerCommunication.append(email.getContents() + "\n\n----------");
			}
		}
		String userName = bundle.getSeller().getUser().getName();
		if (userName.indexOf(" ") > 0) {
			userName = userName.substring(0,userName.indexOf(" "));
		}
		return "<ANSWER_TO_PROVIDER_MESSAGE>\nHi,\n\n"
			+ (defaultMessageToProvider != null ? defaultMessageToProvider : "")
			+ "\n\nRegards,\n" + userName + "\n</ANSWER_TO_PROVIDER_MESSAGE>\n\n"
			+ "NOTE: when answering to the customer the customer's email WON'T be automatically"
			+ " included (unlike the normal \"Email from customer\"), so you need to ADD also the"
			+ " customer's email in the tag below if you want to answer a specific email from the customer.\n\n"
			+ "<ANSWER_TO_CUSTOMER_MESSAGE>\nMoi,\n\n\n\nYstävällisin terveisin,\n" + bundle.getSeller().getSignature() + "\n</ANSWER_TO_CUSTOMER_MESSAGE>\n\n"
			+ "<ANSWER_TO_PROVIDER>" + (defaultMessageToProvider != null ? "true" : "false") + "</ANSWER_TO_PROVIDER>\n\n"
			+ "<ANSWER_TO_CUSTOMER>" + answerToCustomer + "</ANSWER_TO_CUSTOMER>\n\n"
			+ "<PROVIDER_MESSAGE_SUBJECT>Why isn't this order shipped</PROVIDER_MESSAGE_SUBJECT>\n\n"
			+ "<CUSTOMER_EMAIL_SUBJECT>" + customerEmailSubject + "</CUSTOMER_EMAIL_SUBJECT>\n\n"
			+ "<MESSAGE_FROM_PROVIDER>\n"
			+ (providerQuestion != null ?
					"Nbr messages: " + providerQuestion.getNbrMessages() + "\n"
					+ "Last own comment: " + providerQuestion.getLastOwnComment() + "\n"
					+ "Last provider comment: " + providerQuestion.getLastProviderComment() + "\n"
					+ "Provider order id: " + bundle.getId() + "\n"
					+ "Provider ticket id: " + providerQuestion.getProviderTicketId() + "\n"
					+ "Subject: " + providerQuestion.getSubject() + "\n"
					+ "Contents:\n" + providerQuestion.getContents() + "\n"
					: "No message ever sent to provider")
			+ "</MESSAGE_FROM_PROVIDER>\n\n"
			+ "<PROVIDER_QUESTION_ID>" + (providerQuestion != null ? providerQuestion.getId() : "") + "</PROVIDER_QUESTION_ID>\n\n"
			+ "<ORDER_BUNDLE_ID>" + bundle.getId() + "</ORDER_BUNDLE_ID>\n\n"
			+ (customerCommunication.length() > 0 ? "<MESSAGE_FROM_CUSTOMER>\n" + customerCommunication.toString() + "</MESSAGE_FROM_CUSTOMER>\n\n" : "")
			+ OrderInformation.getOrderStockInfo(orderList,true)
			+ "<CUSTOMER_EMAIL_ADDRESS>" + bundle.getCustomer().getEmail() + "</CUSTOMER_EMAIL_ADDRESS>\n\n";
	}

	/**
	 * @param providerQuestion Is null if this is a new conversation
	 * @param subject Needs to be not null if this is a new conversation
	 */
	@Override
	public ProviderQuestion addQuestionToProvider(ProviderQuestion providerQuestion, OrderBundle bundle, String subject,
			String question, String conversationType) throws Exception {
		String userName = bundle.getSeller().getUser().getName();
		if (userName.indexOf(" ") > 0) {
			userName = userName.substring(0,userName.indexOf(" "));
		}
		question = "Hi,\n\n" + question + "\n\nRegards,\n" + userName + "\n";
		if (providerQuestion == null) {
			List<ProviderQuestion> providerQuestionList = providerQuestionDAO.getProviderQuestionForBundle(bundle);
			if (providerQuestionList.size() > 0) {
				providerQuestion = providerQuestionList.get(0);
			}
		}
		if (providerQuestion == null && subject == null) {
			throw new Exception("subject cannot be null for new conversations");
		}
		ProviderOrder prov = getProviderOrder(bundle.getProvider());
		if (prov != null) {
			providerQuestion = prov.addQuestionToProvider(providerQuestion, bundle, subject, question, conversationType);
		}
		return providerQuestion;
	}
	
	@Override
	public void updateOrderStatus(OrderBundle bundle, List<Order> bundleOrders, CriticalSectionInfo section) throws Exception {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean cancelOrder(Order order) throws Exception {
		ProviderOrder prov = getProviderOrder(order.getOrderBundle().getProvider());
		if (prov != null) {
			return prov.cancelOrder(order);
		}
		return false;
	}
	
	public class ProductInfo {
		private Price currentPrice;
		private Integer productState;
		/*public ProductInfo(Price currentPrice, int productState) {
			this.setCurrentPrice(currentPrice);
			this.setProductState(productState);
		}*/
		public void setCurrentPrice(Price currentPrice) {
			this.currentPrice = currentPrice;
		}
		public Price getCurrentPrice() {
			return currentPrice;
		}
		public void setProductState(Integer productState) {
			this.productState = productState;
		}
		public Integer getProductState() {
			return productState;
		}
	}
	
	/**
	 * 
	 * @param product
	 * @param sloppyStockChecking Check the stock information sloppily at provider (return "in stock" when not sure).
	 * @return
	 */
	public void updateProviderPrice(Product product, boolean sloppyStockChecking) {
		productDAO.refresh(product);
		
		/* Skip products that are no longer for sale at provider. */
		if (product.getProductState().equals(Product.STATE_NO_LONGER_FOR_SALE)) {
			logger.debug("Skipping updateProviderPrice for product " + product.getId() + " because it's no longer for sale at provider.");
			return;
		}
		
		/* Skip compensation products. */
		if (product.getCompensation()) {
			logger.debug("Skipping updateProviderPrice for product " + product.getId() + " because it's a compensation product.");
			return;
		}
		
		/* Skip products that have no category defined because these ones won't be listed anyway. */
		if (product.getCategory() == null) {
			logger.debug("Skipping updateProviderPrice for product " + product.getId() + " because it has no category defined.");
			return;
		}
		
		/* Skip products having no active adtemplates. */
		boolean someActive = false;
		for (AdTemplate adTemplate : adTemplateDAO.findByProduct(product)) {
			if (adTemplate.getActive()) {
				someActive = true;
			}
		}
		if (!someActive) {
			logger.debug("Skipping updateProviderPrice for product " + product.getId() + " because it has no active adTemplates.");
			return;
		}
		
		/* Check this product often in case there are unshipped orders for this product. */
		List<Order> ordersForProd = orderDAO.getByProviderProductId(product.getProviderProdId(),product.getProvider());
		boolean unshippedOrderExists = false;
		for (Order order : ordersForProd) {
			if (order.getState().intValue() < Order.STATE_SHIPPED_BY_PROVIDER) {
				unshippedOrderExists = true;
				break;
			}
		}
		
		/* Check this product often in case there are ads in the display window and/or ads
		 * having originalAmount > 1 for this product. */
		List<Ad> openAds = adDAO.getOpenAdsByProduct(product);
		boolean multiAdExists = false;
		boolean displayWindowAdExists = false;
		for (Ad ad : openAds) {
			if (ad.getVisibilityType().intValue() > 0) {
				displayWindowAdExists = true;
			}
			if (ad.getOriginalAmount().intValue() > 1) {
				multiAdExists = true;
			}
		}
		
		/* Normal fetch limit tells for how long we'll trust previously fetched information. */
		int normalFetchLimitHours = 48;

		/* Provider down fetch limit tells for how long we'll trust previously fetched
		 * information in case we cannot reach the provider. */
		int providerDownFetchLimitHours = normalFetchLimitHours + 24;

		/* Trust previously fetched information for a longer time if this product is "offline sales" since
		 * for these ones it takes a lot longer to get the desired information. */
		int offlineSalesFetchLimitHours = 168;

		/* Provider down fetch limit tells for how long we'll trust previously fetched
		 * information in case we cannot reach the provider. */
		int providerDownOfflineSalesFetchLimitHours = offlineSalesFetchLimitHours + 24;

		/* Trust previously fetched information for a longer time if this product is too expensive.
		 * The reason is that usually it's too expensive also if we check a week later. */
		int tooExpensiveFetchLimitHours = 168;

		/* The FocalPrice web pages are quite unreliable and sometimes reports a product
		 * as no longer for sale altough it's indeed for sale, we don't close ads until
		 * it has been reported as not for sale at least closeAdLimit times. If it's
		 * reported as non-existing at least closeAdLimit + 5 times, then we'll also mark
		 * the product as non-existing in the database + cancel orders for the product
		 * in question. */
		int closeAdLimit = 5;
		int maxNotFoundLimit = closeAdLimit + 5;
		if (maxNotFoundLimit <= closeAdLimit) {
			throw new RuntimeException("Faulty configuration: closeAdLimit = " + closeAdLimit + ", maxNotFoundLimit = " + maxNotFoundLimit);
		}

		/* Determine if we need to fetch the price again (if it was fetched very recently,
		 * then we can use the values that are already in the database). */
		boolean needNewFetch = true;
		Date lastFetched = product.getStatusFetchDate();
		long hoursSinceLastFetch = 10000;
		if (unshippedOrderExists) {
			logger.debug("New fetch necessary for product " + product.getId() + " because unshipped orders exist for this product.");
		} else if (multiAdExists || displayWindowAdExists) {
			logger.debug("New fetch necessary for product " + product.getId() + " because it's in display window and/or has originalAmount greater than one.");
		} else if (lastFetched != null && product.getCurrentProviderPriceProviderCurrencyInclVat() != null
				&& !product.getProductState().equals(Product.STATE_UNKNOWN)) {
			hoursSinceLastFetch = (System.currentTimeMillis() - lastFetched.getTime()) / (1000*3600);
			if ((!product.getOfflineSale() && hoursSinceLastFetch < normalFetchLimitHours)
					|| (product.getOfflineSale() && hoursSinceLastFetch < offlineSalesFetchLimitHours)) {
				logger.debug("New fetch not necessary for product " + product.getId() + " since it was fetched recently.");
				needNewFetch = false;
			} else {
				/* If this item is in stock and still hasn't been listed for two weeks, then
				 * conclude that the item is too expensive. */
				boolean tooExpensive = false;
				if (product.getProductState().equals(Product.STATE_IN_STOCK)) {
					long hoursSinceLastListing = 10000;
					Ad latestAd = adDAO.getLatestAdByProduct(product);
					if (latestAd != null) {
						hoursSinceLastListing = (System.currentTimeMillis() - latestAd.getDateToMarket().getTime()) / (1000*3600);
					}
					if (latestAd != null && hoursSinceLastListing > 336) {
						tooExpensive = true;
					}
					if (tooExpensive) {
						BigDecimal priceInclVatEuro = currencyDAO.convertCurrency(product.getProvider().getCurrency().getId(),
								Currency.EURO, product.getCurrentProviderPriceProviderCurrencyInclVat());
						if (priceInclVatEuro.compareTo(new BigDecimal("20.00")) < 0) {
							/* How come this one is deemed as too expensive although the price is quite low?
							 * It could be because it has recently been out of stock. */
							boolean foundStockShortageOrder = hasStockShortageOrder(product);
							if (!foundStockShortageOrder) {
								logger.error("Price for product " + product.getId()
										+ " is deemed too high although priceInclVatEuro is " + priceInclVatEuro.toPlainString());
								throw new RuntimeException("Price for product " + product.getId()
										+ " is deemed too high although priceInclVatEuro is " + priceInclVatEuro.toPlainString());
								//EmailSender.sendOperationProblemReport("Unlisted product detected",
									//	"How come product " + product.getId() + " hasn't been listed for a long time?");
							}
						}
					}
				}
				if (tooExpensive && hoursSinceLastFetch < tooExpensiveFetchLimitHours) {
					logger.debug("New fetch not necessary for product " + product.getId() + " since it's too expensive ("
							+ product.getCurrentProviderPriceProviderCurrencyInclVat().toPlainString() + ") and fetched quite recently.");
					needNewFetch = false;
				} else {
					logger.debug("New fetch necessary for product " + product.getId() + " because last fetch is too old.");
				}
			}
		} else {
			//always fetch if state isn't known
			logger.debug("New fetch necessary for product " + product.getId() + " because product state is unknown.");
		}

		int prevState = product.getProductState();
		if (needNewFetch) {
			try {
				ProviderOrder prov = getProviderOrder(product.getProvider());
				Price currentPrice = prov.getPrice(product,sloppyStockChecking);
				product.setNotFoundCounter(0);
				product.setProductState(Product.STATE_IN_STOCK);

				/* On at least one occation (someuser@gmail.com) we got a problem where SellStar
				 * got the wrong price from the web pages of FocalPrice (possibly because the web
				 * page of FocalPrice was temporarily screwed up) and that led to listing of an ad
				 * having a too low price. As a security check throw an exception if the new price
				 * differs too much from the old price. */
				BigDecimal oldPrice = product.getCurrentProviderPriceProviderCurrencyInclVat();
				BigDecimal newPrice = currentPrice.getPriceInclVat().getValue();

				if (oldPrice == null) {
					logger.info("Price fetched for product " + product.getId() + ": " + newPrice.toPlainString());
				} else if (newPrice.compareTo(oldPrice) != 0) {
					logger.info("Price changed for product " + product.getId() + " (provider "
							+ product.getProvider().getName() + "): oldPrice = " + oldPrice.toPlainString()
							+ " (fetched " + product.getStatusFetchDate() + "), newPrice = " + newPrice.toPlainString());
					BigDecimal minAllowedNewPrice = oldPrice.multiply(new BigDecimal("0.65"));
					BigDecimal maxAllowedNewPrice = oldPrice.multiply(new BigDecimal("1.5"));
					if (newPrice.compareTo(minAllowedNewPrice) < 0 || newPrice.compareTo(maxAllowedNewPrice) > 0) {
						logger.error("Too big difference between old and new price for product " + product.getId()
								+ " (old price = " + oldPrice.toPlainString() + ", new price = " + newPrice.toPlainString() + ").");
						String extraInfo = "Offline sales: " + product.getOfflineSale() + ", product state: ";
						if (product.getProductState().equals(Product.STATE_NO_LONGER_FOR_SALE)) {
							extraInfo += "NO LONGER FOR SALE";
						} else if (product.getProductState().equals(Product.STATE_OUT_OF_STOCK)) {
							extraInfo += "OUT OF STOCK";
						} else if (product.getProductState().equals(Product.STATE_IN_STOCK)) {
							extraInfo += "in stock";
						} else if (product.getProductState().equals(Product.STATE_UNKNOWN)) {
							extraInfo += "UNKNOWN";
						} else {
							throw new SellStarRuntimeException("Unknown state for product " + product.getId(), null, 0, null);
						}
						EmailSender.sendOperationSummaryReport("Unexpected price difference",
								"Big difference between old and new price for product " + product.getId()
								+ " (" + product.getProviderProdId() + ", url = " + product.getProviderUrl()
								+ "): old price = " + oldPrice.toPlainString()
								+ ", new price = " + newPrice.toPlainString() + ". Using the new price anyway, but"
								+ " this should perhaps be checked by hand (minAllowedNewPrice = " + minAllowedNewPrice.toPlainString()
								+ ", maxAllowedNewPrice = " + maxAllowedNewPrice.toPlainString() + "). " + extraInfo);
					}
				} else {
					logger.debug("Price stayed the same for product " + product.getId() + " (provider "
							+ product.getProvider().getName() + "): price = " + newPrice.toPlainString());
				}
				product.setCurrentProviderPriceProviderCurrencyInclVat(newPrice);
				product.setStatusFetchDate(new Date());
			} catch (SoldOutAtProviderException e) {
				logger.debug("Product " + product.getId() + " is out of stock.");
				product.setProductState(Product.STATE_OUT_OF_STOCK);
				product.setNotFoundCounter(0);
				product.setStatusFetchDate(new Date());
				if (multiAdExists || displayWindowAdExists) {
					EmailSender.sendOperationProblemReport("Listed product out of stock.",
							"Product " + product.getId() + " is out of stock and is listed (" + openAds.size() + " times)."
							+ " Should these ads be closed? (Some ad is in the display window and/or have quantity greater than one.)");
				}
			} catch (ProductNotFoundAtProviderException e) {
				product.setProductState(Product.STATE_UNKNOWN);
				product.setNotFoundCounter(product.getNotFoundCounter()+1);
				logger.debug("Product " + product.getId() + " was not found at provider (notFoundCounter = " + product.getNotFoundCounter() + ").");
				if (product.getNotFoundCounter() >= closeAdLimit) {
					logger.debug("Product " + product.getId() + " (" + product.getProviderProdId()
							+ ") not found at provider - closing " + openAds.size() + " open ads.");
					for (Ad ad : openAds) {
						MarketRobot lister = getListerForMarket(ad.getMarket());
						lister.closeAdAtMarket(ad);
					}
				} else {
					logger.info("Product " + product.getId() + " (" + product.getProviderProdId() + ") not found at provider, but not yet"
							+ " closing open ads (" + openAds.size() + " ads are open, not found counter = " + product.getNotFoundCounter() + ").");
				}
				if (product.getNotFoundCounter() >= maxNotFoundLimit) {
					logger.debug("Product " + product.getId() + " (" + product.getProviderProdId()
							+ ") not found at provider - marking it as \"no longer for sale\".");
					product.setProductState(Product.STATE_NO_LONGER_FOR_SALE);
					checkForNonShippedRemovedProducts(product);
				} else {
					logger.info("Product " + product.getId() + " (" + product.getProviderProdId() + ") not found at provider, but not yet"
							+ " setting to exists = false (not found counter = " + product.getNotFoundCounter() + ").");
				}
			} catch (ProviderTemporarilyDownException e) {
				if (((!product.getOfflineSale() && hoursSinceLastFetch < providerDownFetchLimitHours)
						|| (product.getOfflineSale() && hoursSinceLastFetch < providerDownOfflineSalesFetchLimitHours))
						&& product.getNotFoundCounter().equals(0)
						&& product.getCurrentProviderPriceProviderCurrencyInclVat() != null) {
					/* If the provider cannot be reached, then we'll still use the old values
					 * if providerDownFetchLimitHours hasn't been reached.
					 * Note: in this case do NOT update the state of the product to unknown. */
					logger.debug("Using stored values for product " + product.getId() + " since provider is down.");
				} else {
					product.setProductState(Product.STATE_UNKNOWN);
					logger.debug("Provider down, but cannot use stored values for product " + product.getId() + ".");
				}
			}
			if (product.getProductState().equals(prevState)) {
				logger.debug("State stayed the same at price fetch (" + product.getProductState() + ").");
			} else {
				logger.debug("State changed at price fetch (prevState = " + prevState + ", newState = " + product.getProductState() + ").");
			}
		} else {
			logger.debug("Using stored values for product " + product.getId() + " since status and prices were fetched recently.");
		}
		if ((multiAdExists || displayWindowAdExists) && hasStockShortageOrder(product)) {
			EmailSender.sendOperationProblemReport("Listed product out of stock.",
					"Product " + product.getId() + " is out of stock according to the database and is listed (" + openAds.size() + " times)."
					+ " Should these ads be closed? (Some ad is in the display window and/or have quantity greater than one.)\n\n"
					+ "Previous product state = " + prevState + ", curr product state = " + product.getProductState());
		}
	}

	//TODO: this one is just copy'n'pasted from Test.java - refactor!
	private MarketRobot getListerForMarket(Market market) {
		List<MarketRobot> listers = (List<MarketRobot>) ApplicationContextPlaceholder.applicationContext.getBean("listers");
		for (MarketRobot lister : listers) {
			if (lister.canHandleMarket(market)) {
				return lister;
			}
		}
		return null;
	}

	private void checkForNonShippedRemovedProducts(Product product) {
		List<Order> list = new ArrayList<Order>();
		for (Order order : orderDAO.getByProviderProductId(product.getProviderProdId(), product.getProvider())) {
			if (order.getState().intValue() < Order.STATE_SHIPPED_BY_PROVIDER) {
				list.add(order);
			}
		}
		
		for (Order order : list) {
			OrderBundle bundle = order.getOrderBundle();
			/*if (orderDAO.getNbrOrdersInBundle(bundle) > 1) {
				throw new RuntimeException("checkForNonShippedRemovedProducts doesn't yet support bundles having several orders.");
			}*/
			if (order.getState().equals(Order.STATE_CUSTOMER_CANCEL_CONFIRMED)) {
				/* I think that we should never get here with the new refunding code, so
				 * let's just throw an exception here. */
				throw new RuntimeException("How come we got here?");
			} else if (order.getState().equals(Order.STATE_PROVIDER_CANCEL_PENDING) || order.getState().equals(Order.STATE_PROVIDER_CANCEL_CONFIRMED)) {
				logger.debug("Product for order " + order.getId() + " is no longer for sale at provider but no action is"
						+ " necessary because order is already in state " + order.getState() + ".");
			} else if (order.getState().equals(Order.STATE_OTHER_ERROR) || order.getState().equals(Order.STATE_SHOULD_RESEND)) {
				EmailSender.sendOperationProblemReport("Removed product has order in state otherError or shouldResend",
						"Product for order " + order.getId() + " is removed from the product range and"
						+ " it's in state \"other error\" or \"should resend\". Doesn't know how to handle this case"
						+ " automatically. Please take care of it manually.", bundle.getSeller().getSystemEmail());
			} else { //if (order.getState().intValue() < Order.STATE_SHIPPED_BY_PROVIDER) {
				orderCancelationHandler.cancelWhenNoLongerForSale(order);
			}
		}
	}
	
	private boolean hasStockShortageOrder(Product product) {
		boolean foundStockShortageOrder = false;
		for (Order order : orderDAO.getOrdersByState(Order.STATE_STOCK_SHORTAGE)) {
			if (order.getProduct().equals(product)) {
				foundStockShortageOrder = true;
			}
		}
		for (Order order : orderDAO.getOrdersByState(Order.STATE_NEW_OUT_OF_STOCK)) {
			if (order.getProduct().equals(product)) {
				foundStockShortageOrder = true;
			}
		}
		for (Order order : orderDAO.getOrdersByState(Order.STATE_STOCK_SHORTAGE_BEFORE_ORDER)) {
			if (order.getProduct().equals(product)) {
				foundStockShortageOrder = true;
			}
		}
		
		/* Sometimes the stock information at both DealExtreme and FocalPrice isn't updated if the
		 * product goes out of stock, so here let's determine that the product is out of stock if
		 * some order has had stock shortage during the last four weeks. */
		if (!foundStockShortageOrder) {
			for (Order order : orderDAO.getRecentOutOfStockOrdersForProduct(4,product)) {
				if ((order.getState().intValue() >= Order.STATE_SHIPPED_BY_PROVIDER
						&& order.getState().intValue() <= Order.STATE_FETCHED_BY_CUSTOMER)
						|| order.getState().intValue() == Order.STATE_CLOSED
						|| order.getState().intValue() == Order.STATE_NEW
						|| order.getState().intValue() == Order.STATE_PAID_TOO_LITTLE_ERROR
						|| order.getState().intValue() == Order.STATE_CUSTOMER_PAID_INFORMED
						|| order.getState().intValue() == Order.STATE_ORDERED_FROM_PROVIDER
						|| order.getState().intValue() == Order.STATE_ORDER_CONFIRMED_BY_PROVIDER) {
					//do nothing, this one got processed further
					logger.debug("Found recent \"out of stock\" based on the state of order "
							+ order.getId() + " for product " + product.getId() + " BUT the order has progressed since.");
				} else {
					foundStockShortageOrder = true;
					logger.debug("Found recent \"out of stock\" based on the state of order "
							+ order.getId() + " for product " + product.getId() + " - returning \"out of stock\".");
					break;
				}
			}
		}
		return foundStockShortageOrder;
	}
	
	/**
	 * 
	 * @param product
	 * @param checkDataBase Return "out of stock" in case some order has "stock shortage" or "stock shortage before order".
	 * @return
	 */
	public ProductInfo getProductInfo(Product product, boolean checkDataBase) {
		//productDAO.refresh(product);
		ProductInfo prodInfo = new ProductInfo();
		prodInfo.setProductState(product.getProductState());
		if (!product.getProductState().equals(Product.STATE_NO_LONGER_FOR_SALE)) {
			/* If checkDataBase is true, then check the database for orders being in state "out of stock". */
			boolean foundStockShortageOrder = false;
			if (checkDataBase) {
				foundStockShortageOrder = hasStockShortageOrder(product);
			}

			if (foundStockShortageOrder) {
				//product.setProductState(Product.STATE_OUT_OF_STOCK);
				//prodInfo.setProductState(product.getProductState());
				prodInfo.setProductState(Product.STATE_OUT_OF_STOCK);
				logger.debug("Returning \"out of stock\" based on the state of orders for product " + product.getId());
			} else if (product.getStatusFetchDate() == null || product.getCurrentProviderPriceProviderCurrencyInclVat() == null) {
				prodInfo.setProductState(Product.STATE_UNKNOWN);
				logger.debug("Returning \"unknown\" since price has never been fetched for product " + product.getId());
			} else if (product.getProductState().equals(Product.STATE_UNKNOWN)) {
				logger.debug("Returning \"unknown\" since state is unknown for product " + product.getId());
			} else if (product.getProductState().equals(Product.STATE_OUT_OF_STOCK)) {
				logger.debug("Returning \"out of stock\" for product " + product.getId());
			} else if (product.getProductState().equals(Product.STATE_IN_STOCK)) {
				Provider provider = product.getProvider();
				Currency currency = provider.getCurrency();
				Price currentPrice = new Price(product.getCurrentProviderPriceProviderCurrencyInclVat(),
						true,currency,provider.getVatPercentage());
				prodInfo.setCurrentPrice(currentPrice);
				logger.debug("Returning \"in stock\" for product " + product.getId());
			}
		}
		return prodInfo;
	}

	@Override
	public Price getPrice(Product product, boolean sloppyStockChecking)
		throws SoldOutAtProviderException, ProductNotFoundAtProviderException, ProviderTemporarilyDownException {
		ProviderOrder prov = getProviderOrder(product.getProvider());
		if (prov != null) {
			return prov.getPrice(product,sloppyStockChecking);
		}
		return null;
	}

	@Override
	public boolean canHandleProvider(Provider provider) {
		throw new UnsupportedOperationException();
	}

	public Product createProduct(Provider provider, String providerProductId) throws Exception {
		ProviderOrder prov = getProviderOrder(provider);
		if (prov != null) {
			return prov.createProduct(providerProductId);
		}
		return null;
	}

	@Override
	public Product createProduct(String providerProductId) throws Exception {
		throw new UnsupportedOperationException();
	}
	
	public List<String> getGoodProductIds(Provider provider, int count, VirtualCoin minPriceExclVat,
			VirtualCoin maxPriceExclVat, BigDecimal percentsOfProductsInSameCategory)
			throws Exception {
		ProviderOrder prov = getProviderOrder(provider);
		if (prov != null) {
			return prov.getGoodProductIds(count,minPriceExclVat,maxPriceExclVat,percentsOfProductsInSameCategory);
		}
		return null;
	}

	@Override
	public List<String> getGoodProductIds(int count, VirtualCoin minPriceExclVat,
			VirtualCoin maxPriceExclVat, BigDecimal percentsOfProductsInSameCategory)
			throws Exception {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean isProductInStock(Product product, boolean sloppyStockChecking) throws ProductNotFoundAtProviderException, ProviderTemporarilyDownException {
		ProviderOrder prov = getProviderOrder(product.getProvider());
		if (prov != null) {
			return prov.isProductInStock(product,sloppyStockChecking);
		}
		return false;
	}

	public void loadProductPage(Provider provider, String providerProductId) {
		ProviderOrder prov = getProviderOrder(provider);
		if (prov != null) {
			prov.loadProductPage(providerProductId);
		}
	}

	@Override
	public void loadProductPage(String providerProductId) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void placeOrder(OrderBundle bundle, List<Order> bundleOrders, String user, String password, CriticalSectionInfo section) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void productExistsAtProvider(Product product) throws ProductNotFoundAtProviderException, ProviderTemporarilyDownException {
		ProviderOrder prov = getProviderOrder(product.getProvider());
		if (prov != null) {
			prov.productExistsAtProvider(product);
		}
	}

	@Override
	public void downloadImages(Product product, File basePath) throws Exception {
		ProviderOrder prov = getProviderOrder(product.getProvider());
		if (prov != null) {
			prov.downloadImages(product,basePath);
		}
	}

	public void loadProductPage(Provider provider, String providerProductId, int retries,
			String timeout) {
		ProviderOrder prov = getProviderOrder(provider);
		if (prov != null) {
			prov.loadProductPage(providerProductId,retries,timeout);
		}
	}
	
	@Override
	public void loadProductPage(String providerProductId, int retries,
			String timeout) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void updateTechnicalSpecs(Product product) throws Exception {
		productDAO.refresh(product);
		/*if (!product.getTechnicalSpecs().trim().equals("") || !product.getProvider().getName().equals(Provider.FOCAL_PRICE_PROVIDER_NAME)) {
			return;
		}*/
		/*if (!product.getProvider().getName().equals(Provider.HAUTALA_PROVIDER_NAME)) {
			return;
		}*/
		if (!product.getProvider().getName().equals(Provider.FOCAL_PRICE_PROVIDER_NAME)) {
			return;
		}
		logger.info("Updating technical specs for product.id = " + product.getId() + " (" + product.getProviderProdId() + ")");
		ProviderOrder prov = getProviderOrder(product.getProvider());
		prov.updateTechnicalSpecs(product);
	}

	@Override
	public AdTemplate createAutoTemplate(Product product) {
		ProviderOrder prov = getProviderOrder(product.getProvider());
		if (prov != null) {
			return prov.createAutoTemplate(product);
		}
		return null;
	}

	@Override
	public void login(Provider provider, int tryCount)
			throws ProviderTemporarilyDownException {
		ProviderOrder prov = getProviderOrder(provider);
		if (prov != null) {
			prov.login(provider, tryCount);
		}
	}

	@Override
	public void logout(Provider provider, boolean removeDanglingOrders) {
		ProviderOrder prov = getProviderOrder(provider);
		if (prov != null) {
			prov.logout(provider,removeDanglingOrders);
		}
	}

}
