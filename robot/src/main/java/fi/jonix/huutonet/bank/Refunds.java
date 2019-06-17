package fi.jonix.huutonet.bank;

import java.math.BigDecimal;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import fi.jonix.huutonet.domain.VirtualCoin;
import fi.jonix.huutonet.domain.model.Currency;
import fi.jonix.huutonet.domain.model.Customer;
import fi.jonix.huutonet.domain.model.Order;
import fi.jonix.huutonet.domain.model.OrderBundle;
import fi.jonix.huutonet.domain.model.Seller;
import fi.jonix.huutonet.domain.model.User;
import fi.jonix.huutonet.domain.model.dao.CurrencyDAO;
import fi.jonix.huutonet.domain.model.dao.CustomerDAO;
import fi.jonix.huutonet.domain.model.dao.MessageDAO;
import fi.jonix.huutonet.domain.model.dao.OrderDAO;
import fi.jonix.huutonet.domain.model.dao.UserDAO;
import fi.jonix.huutonet.email.EmailSender;
import fi.jonix.huutonet.exception.BankException;
import fi.jonix.huutonet.robot.SeleniumRobot;
import fi.jonix.huutonet.tools.CurrencyFormatter;
import fi.jonix.huutonet.tools.MathUtilities;
import fi.jonix.huutonet.tools.Statics;
import fi.jonix.huutonet.tools.StringUtilities;

/**
 * This class handles refunds to the customer.
 * 
 * @author john
 */
@Component(value = "refunds")
public class Refunds {

	public static final Logger logger = Logger.getLogger(Refunds.class);
	
	@Autowired
	private SeleniumRobot seleniumRobot;

	@Autowired
	private OrderDAO orderDAO;

	@Autowired
	private BankRobot bankRobot;
	
	@Autowired
	private FaultyPaymentFixer faultyPaymentFixer;

	@Autowired
	private CustomerDAO customerDAO;

	@Autowired
	private UserDAO userDAO;

	@Autowired
	private MessageDAO messageDAO;

	@Autowired
	private CurrencyDAO currencyDAO;

	/**
	 * This method confirms made payments (if any) and logs out from the bank.
	 */
	public void finishRefunds(String user, String password, Map<String,String> bankCodes, int nbrRefundsMade) throws Exception {
		if (nbrRefundsMade > 0) {
			try {
				logger.info("Confirming transactions");
				bankRobot.confirmTransactions(user, password, bankCodes);
			} catch (BankException e) {
				logger.error("Couldn't confirm bank payments: ",e);
				String operatorEmail = "Confirmation of bank transaction(s) failed (" + e.getMessage() + "). Please logon to the bank"
					+ " account and confirm them manually.";
				EmailSender.sendOperationProblemReport("Bank payment confirmation failed",operatorEmail);
				/* Let's go to the error state here by throwing an exception. The reason for this is that the email
				 * to the operator COULD get lost (happens very rarely but still), and we absolutely DON'T want to
				 * leave unconfirmed payments in the bank when the transactions have gone through and emails have
				 * been sent to the customers about made payments. */
				bankRobot.logout();
				throw new Exception("Couldn't confirm bank payments.");
			}
		}
		//we shouldn't even be logged in to the bank if no refunds have been made
		if (bankRobot.isLoggedIn()) {
			bankRobot.logout();
		}
	}
	
	/**
	 * @return Integer telling how many refunds were made. Note: we want to make just one payment per transaction,
	 * so this method will _never_ return more than one. Then it's up to the caller to repeatedly call this method until
	 * it returns zero.
	 * 
	 * NOTE: It's CRUCIAL to call the finishRefunds method after this method is called, because that
	 * method will confirm the made refunds (and logout from the bank).
	 */
	public int refundToCustomer(String user, String password, Map<String,String> bankCodes, Map<String,BigDecimal> totalRefundSumInEuro) throws Exception {
		faultyPaymentFixer.fixFaultyPayments(false); //first fix possible faulty payments
		this.seleniumRobot.getSelenium().setTimeout("120000");
		try {
			int nbrRefunds = -1;
			while (nbrRefunds == -1) {
				/* refundTooBigPayment returns -1 if the account number was faulty, so keep
				 * going until it returns either 0 (no refunds done) or 1 (one refund done). */
				nbrRefunds = refundTooBigPayment(user,password,bankCodes,totalRefundSumInEuro);
				if (nbrRefunds > 0) {
					return nbrRefunds;
				}
			}
		} catch (BankException e) {
			if (e.getMessage().equals(HBBankRobot.OUT_OF_BANKCODES)) {
				logger.error("BankException: ",e);
				String operatorEmail = "Problem: not possible to logon to the bank. Please check if the bank codes ran out.";
				EmailSender.sendOperationProblemReport("Bank codes ran out?",operatorEmail);
			} else {
				throw e;
			}
		}
		return 0;
	}
	
	/**
	 * All refunds are handled as "paid-too-much" payments.
	 * 
	 * @return Integer telling how many refunds were made.
	 */
	private int refundTooBigPayment(String user, String password, Map<String,String> bankCodes, Map<String,BigDecimal> totalRefundSumInEuro) throws Exception {
		BigDecimal sumToRefundMC = BigDecimal.ZERO;
		
		/* First check if there are payments to make because some customer has paid too much.
		 * Note that it's enough to only check unevenly targeted bundles since faultyPaymentFixer
		 * always moves excessive payments to bundles. */
		Map<String,TargetedBundles> targetedBundleMap = TargetedBundles.getUnevenlyTargetedBundles();
		Iterator<String> iter = targetedBundleMap.keySet().iterator();
		Customer customer = null;
		OrderBundle bundle = null;
		Long currency = null;
		while (iter.hasNext()) {
			String id = iter.next();
			TargetedBundles targetedBundles = targetedBundleMap.get(id);
			if (targetedBundles.getTargetedPaidInMarketCurrency().compareTo(targetedBundles.getExpectedPaymentInMarketCurrency()) > 0) {
				sumToRefundMC = targetedBundles.getTargetedPaidInMarketCurrency().subtract(targetedBundles.getExpectedPaymentInMarketCurrency());
				String email = TargetedOrders.id2Email(id);
				customer = customerDAO.getCustomerByEmailAddress(email);
				if (customer.getAccountNumber() != null && !customer.getAccountNumber().startsWith("USED_")) {
					bundle = targetedBundles.getBundles().get(0);
					currency = TargetedOrders.id2CurrencyId(id);
					break;
				} else {
					logger.debug("Refunding due for customer " + customer.getId()
							+ " (" + sumToRefundMC + " mc) but cannot refund yet because account number is missing.");
				}
			}
		}
		
		if (sumToRefundMC.compareTo(BigDecimal.ZERO) == 0) {
			logger.info("No refundings due.");
			return 0;
		} else if (customer.getAccountNumber() == null) {
			logger.info("Refundings due but cannot refund yet because account number is missing.");
			return 0;
		}

		//Direct the payment to some random order of the bundle.
		Order order = orderDAO.getOrdersInBundle(bundle).get(0);
		VirtualCoin sumToRefundVC = new VirtualCoin(currency,sumToRefundMC);
		try {
			refundHelper(user,password,bankCodes,order,sumToRefundVC,customer.getAccountNumber(),totalRefundSumInEuro);
		} catch (BankException be) {
			if (be.getMessage().equals(HBBankRobot.FAULTY_ACCOUNT_NUMBER)) {
				logger.info("Customer " + customer.getEmail() + " specified a faulty account number ("
						+ customer.getAccountNumber() + "). Reseting it and informing the user.");
				String subject = "Virheellinen tilinumero - hyvitysmaksu keskeytetty";
				Seller seller = order.getOrderBundle().getSeller();
				String contextPath = messageDAO.getText("contextPath", seller,
						order.getMarket().getLanguage());
				String faultyAccountNumberEmail = "Moi,\n\nOlet ilmoittanut meille, että hyvitysmaksusi"
					+ " " + CurrencyFormatter.print(sumToRefundVC, Statics.FINNISH) + " pitää maksaa tilille "
					+ customer.getAccountNumber() + ". Havaittiin maksua suorittaessa, että tämä on virheellinen"
					+ " tilinumero. Ole hyvä ja tarkista tilinumero ja anna alla olevan linkkin kautta oikea"
					+ " tilinumero:\n\n"
					+ seller.getStoreUrl() + contextPath + "/refund?seller="
					+ StringUtilities.escapeString2URL(seller.getSignature()) + "&confirmationCode="
					+ customer.getConfirmationCode()
					+ "\n\nVarmennuskoodisi hyvitystä varten on " + customer.getConfirmationCode()
					+ "\n\nYstävällisin terveisin,\n"
					+ order.getOrderBundle().getSeller().getSignature();
				EmailSender.sendEmailBatch(seller.getSystemEmail(),
						customer.getEmail(),subject,faultyAccountNumberEmail);
				customer.setAccountNumber(null);
				return -1;
			} else {
				throw be;
			}
		}

		logger.info("Done refunding to customer " + customer.getEmail());
		// Decrease the targetedPaid for the bundle + direct the refunding sum to the chosen random order of the bundle.
		bundle.setTargetedPaidShippingCostInMarketCurrency(bundle.getTargetedPaidShippingCostInMarketCurrency().subtract(sumToRefundMC));
		order.setRefundingMarketCurrency(order.getRefundingMarketCurrency().add(sumToRefundMC));
		//zero the account number and informedAboutTooBigPayment
		customer.setAccountNumber("USED_" + customer.getAccountNumber());
		customer.setInformedAboutTooBigPayment(false);
		return 1;
	}
	
	private void refundHelper(String user, String password, Map<String,String> bankCodes, Order order,
			VirtualCoin sumToRefund, String accountNumber, Map<String,BigDecimal> totalRefundSumInEuro) throws Exception {
		/* Safety measure. */
		if (sumToRefund.getValue().compareTo(BigDecimal.ZERO) <= 0 || sumToRefund.getValue().doubleValue() > 50) {
			throw new Exception("Refund error: refusing to refund " + sumToRefund + " euros to the customer.");
		}
		
		BigDecimal sumToRefundEuro = currencyDAO.convertCurrency(sumToRefund.getCurrency(), Currency.EURO, sumToRefund.getValue());
		logger.info("Refunding " + sumToRefund + " mc (" + sumToRefundEuro + " euros) to customer " + order.getOrderBundle().getCustomer().getEmail() + " for order " + order.getId());
		totalRefundSumInEuro.put("totalRefundSumInEuro", totalRefundSumInEuro.get("totalRefundSumInEuro").add(sumToRefundEuro));
		//String sum = MathUtilities.roundTo2Decimals(sumToRefund).replace(".", ",");
		String receiverName = order.getOrderBundle().getFirstName() + " " + order.getOrderBundle().getLastName();

		//TODO: Take the contents of the email to the customer from the database.
		String subject = "Hyvitys maksettu tilille";
		String refundEmail = "Moi,\n\nOlemme tänään lähettäneet hyvityksen " + CurrencyFormatter.print(sumToRefund, Statics.FINNISH)
			+ " tilillenne " + accountNumber + ".\n\nMaksu on suoritettu Handels Bankenin tililtä. Mikäli tilinne on eri"
			+ " pankissa, siirto kirjautuu tilillenne parin pankkipäivän aikana.\n\nYstävällisin terveisin,\n"
			+ order.getOrderBundle().getSeller().getSignature();
		EmailSender.sendEmailBatch(order.getOrderBundle().getSeller().getSystemEmail(),
				order.getOrderBundle().getCustomer().getEmail(),subject,refundEmail);

		/* Finally inform the operators about this automatic refund. */
		List<User> operators = userDAO.getOperators();
		String operatorInfoStr = "Info: automatic refunding of " + CurrencyFormatter.print(sumToRefund, Statics.FINNISH)
			+ " was done.\nThe payment was done to account number " + accountNumber + " (for customer "
			+ order.getOrderBundle().getCustomer().getEmail() + ").";
		for (User oper : operators) {
			/* Use normal sendEmailBatch instead of operatorProblem, because by using sendEmailBatch
			 * we make sure that the email will only be sent of the transaction goes through. */
			if (oper.getLoginName().equals("john")) { //TODO: remove hard coding of "john" here
				EmailSender.sendEmailBatch(order.getOrderBundle().getSeller().getSystemEmail(),
						oper.getEmailAddress(),"Automatic refunding done",operatorInfoStr);
			}
		}

		/* Do the actual transaction at the end, because this one cannot be rolled back (in case the email
		 * sending above bails out, it's better to send the emails first). */
		if (!bankRobot.isLoggedIn()) {
			bankRobot.login(user, password, bankCodes);
		}
		bankRobot.makeTransaction(order.getCustomerReferenceNumber(), sumToRefund, receiverName, accountNumber);

	}
		
}
