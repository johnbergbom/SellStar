package fi.jonix.huutonet.bank;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import fi.jonix.huutonet.domain.VirtualCoin;
import fi.jonix.huutonet.domain.model.Currency;
import fi.jonix.huutonet.domain.model.Order;
import fi.jonix.huutonet.domain.model.OrderBundle;
import fi.jonix.huutonet.domain.model.Transaction;
import fi.jonix.huutonet.domain.model.dao.MessageDAO;
import fi.jonix.huutonet.domain.model.dao.OrderDAO;
import fi.jonix.huutonet.domain.model.dao.TransactionDAO;
import fi.jonix.huutonet.email.EmailSender;
import fi.jonix.huutonet.exception.BankException;
import fi.jonix.huutonet.robot.SeleniumRobot;
import fi.jonix.huutonet.tools.CurrencyFormatter;
import fi.jonix.huutonet.tools.Encryption;
import fi.jonix.huutonet.tools.Statics;
import fi.jonix.huutonet.tools.StringUtilities;

@Component(value = "bankHelper")
public class BankHelper {

	public static final Logger logger = Logger.getLogger(BankHelper.class);
	
	@Autowired
	private SeleniumRobot seleniumRobot;
	
	@Autowired
	private BankRobot bankRobot;
	
	@Autowired
	private TransactionDAO transactionDAO;
	
	@Autowired
	private OrderDAO orderDAO;
	
	@Autowired
	private MessageDAO messageDAO;
	
	/**
	 * This method associates a transaction with the given order (unless some problem is detected)
	 * and updates the state of the order.
	 * 
	 * @param transaction
	 * @param order
	 */
	public void associateTransactionWithOrder(Transaction transaction, Order order) throws Exception {
		if (!transaction.getCurrency().getId().equals(Currency.EURO)) {
			throw new RuntimeException("Several different currencies in the transaction table"
					+ " aren't yet supported (for example the balance check before and"
					+ " after fetching of new transactions (in getNewTransactions) still expect"
					+ " all transactions to have the same currency).");
		}
		//BigDecimal expectedPayment = order.getFinalSalesPriceInMarketCurrency().add(order.getShippingCostInMarketCurrency());
		//BigDecimal expectedPayment = order.getFinalSalesPriceInMarketCurrency();
		//logger.debug("Expected payment for order " + order.getId() + ":" + expectedPayment);
		logger.debug("Received payment for order " + order.getId() + ":" + transaction.getAmount());
		transaction.setOrder(order);
		order.getOrderBundle().getCustomer().setInformedAboutTooBigPayment(false); //always zero this one when money is received
		//order.getOrderBundle().getCustomer().setDateKicked(new Date()); //always zero this one when money is received
		order.getOrderBundle().getCustomer().setNbrTimesKicked(0); //always zero this one when money is received
		if (!transaction.getCurrency().equals(order.getOrderBundle().getUsedCustomerCurrency())) {
			throw new Exception("Transaction " + transaction.getId() + " has a different currency than order " + order.getId() + ".");
		}
		if (transaction.getAmount().compareTo(BigDecimal.ZERO) > 0) {
			BigDecimal targeted = order.getTargetedPaidInMarketCurrency().add(transaction.getAmount());
			order.setTargetedPaidInMarketCurrency(targeted);
			if (order.getState().equals(Order.STATE_CLOSED_ORDER_CANCELED)) {
				/* Inform the operator if the order was already "closed canceled". If however the order was
				 * in state "closed customer never paid" we DON'T need to inform anybody, because
				 * faultyPaymentFixer takes care of those. But "closed canceled" can NEVER be taken care
				 * of automatically since when putting an order in state "closed canceled", then the final
				 * sales price is zeroed. */
				OrderBundle bundle = order.getOrderBundle();
				String contextPath = messageDAO.getText("contextPath", bundle.getSeller(),order.getMarket().getLanguage());
				//TODO: prefill the account number in case the customer already has given us his
				//account number for some other order
				VirtualCoin vcAmount = new VirtualCoin(transaction.getCurrency(),transaction.getAmount());
				String custEmail = messageDAO.getText("customerPaidForCanceledOrder", bundle.getSeller(), order.getMarket().getLanguage(),
						"AD_HEADLINE",order.getAd().getAdTemplate().getHeadline(),
						"ORDER_ID",""+order.getId(),
						"REFUND_SUM",CurrencyFormatter.print(vcAmount, Statics.FINNISH),
						"GOOGLE_ORDER_URL",bundle.getSeller().getStoreUrl(),
						"CONTEXT_PATH",contextPath,
						"CONFIRMATION_CODE",bundle.getCustomer().getConfirmationCode(),
						"SIGNATURE_URL_ENC",StringUtilities.escapeString2URL(bundle.getSeller().getSignature()),
						"SIGNATURE",bundle.getSeller().getSignature());
				EmailSender.sendEmailBatch(bundle.getSeller().getSystemEmail(),bundle.getCustomer().getEmail(),
						"Rahaa vastaanotettu peruutetusta tilauksesta",custEmail);
				order.getOrderBundle().getCustomer().setInformedAboutTooBigPayment(true);
			} else if (order.getFinalSalesPriceInMarketCurrencyInclVat().compareTo(new BigDecimal("0.00")) == 0) {
				/* We might have gotten money for an order that was refunded because it never came
				 * to the customer (because the post screwed up). Then finally the customer got his
				 * product (with a big delay) and then he paid back the refunded money to us. Inform
				 * the operator in this case. */
				targeted = targeted.subtract(transaction.getAmount());
				order.setTargetedPaidInMarketCurrency(targeted);
				String operEmail = "We got " + transaction.getAmount() + " euros for order " + order.getId()
					+ ". This order seems to have been refunded (because finalSalesPrice is zero but still"
					+ " the order isn't in state \"closed canceled\"). Now money came for this order. Please"
					+ " check if this is because the customer finally got his product and then paid back his"
					+ " refund. If that's the case, then please update the following fields:\n"
					+ "order_bundle.cust_shipping_cost_mc, order_bundle.cust_shipping_cost_euro,"
					+ " order_bundle.targeted_ship_mc, orders.other_loss_euro, orders.targeted_mc,"
					+ " orders.fin_sp_mc, orders.fin_sp_euro and orders.state\n";
				EmailSender.sendOperationProblemReport("Returned refund?",operEmail);
				
			}
		} else {
			/* Don't change the targetedPaid for negative transactions, only update order.refunding. */
			BigDecimal refundingMC = order.getRefundingMarketCurrency().add(transaction.getAmount());
			order.setRefundingMarketCurrency(refundingMC);
			if (order.getRefundingMarketCurrency().compareTo(BigDecimal.ZERO) < 0) {
				throw new Exception("Error: order.refunding is negative (" + order.getRefundingMarketCurrency() + ") for order " + order.getId());
			}
		}
		
	}
	
	/**
	 * 
	 * @return Calendar telling from what date we should start fetching transactions.
	 */
	private Calendar getStartDateForTransactionFetching() {
		/* Check when the latest transaction in the database took place. If no transactions
		 * were found, then fetch all transactions for a month back in time, otherwise
		 * start fetching from four days BEFORE the currently last transaction. This is
		 * necessary because the date in the date that is in the internet bank is the date
		 * when the payment was done, and in some cases it takes a couple of days for the
		 * money to arrive, so the transactions in the bank aren't always in descending
		 * order sorted on the date. */
		Date latestTransactionDate = transactionDAO.getLatestTransactionDate();
		Calendar startFrom = Calendar.getInstance();
		if (latestTransactionDate == null) {
			logger.info("No transactions found in database, fetching all transactions from a month back in time.");
			startFrom.add(Calendar.MONTH,-1);
		} else {
			startFrom.setTime(latestTransactionDate);
			startFrom.add(Calendar.DATE,-4); //fetch from 4 days back in time just to be safe
		}
		startFrom.set(Calendar.HOUR_OF_DAY,0);
		startFrom.set(Calendar.MINUTE,0);
		startFrom.set(Calendar.SECOND,0);
		startFrom.set(Calendar.MILLISECOND,0);
		logger.debug("latestTransactionDate = " + latestTransactionDate);
		return startFrom;
	}
	
	/**
	 * This method fetches new transactions from the bank and associate them with orders if possible. An error email is
	 * sent if a transaction was found that couldn't be associated with any order.
	 */
	public void getNewTransactions(String[] args) throws Exception {
		Calendar startFrom = getStartDateForTransactionFetching();
		logger.info("Fetch transactions starting from " + startFrom.getTime() + " until the very latest one.");
		
		/* Fetch the arguments. */
		String user = args[1];
		String password = args[2];
		this.seleniumRobot.getSelenium().setTimeout("120000");
		Map<String,String> bankCodes = StringUtilities.keyValueArray2Map(args[3]);

		VirtualCoin balance = null;
		try {
			if (!bankRobot.isLoggedIn()) {
				bankRobot.login(user, password, bankCodes);
			}
			balance = bankRobot.getAccountBalance();
			List<Transaction> transactions = bankRobot.getTransactions(startFrom);
			bankRobot.logout();
			addNewTransactionsToDB(transactions);
		} catch (BankException e) {
			if (e.getMessage().equals(HBBankRobot.OUT_OF_BANKCODES)) {
				logger.error("BankException: " + e.getMessage());
				String operatorEmail = "Problem: not possible to logon to the bank. Please check if the bank codes ran out.";
				EmailSender.sendOperationProblemReport("Bank codes ran out?",operatorEmail);
				return;
			} else {
				throw e;
			}
		}
		
		/* Right after fetching transactions there should normally not be any orders having a non-zero refundings,
		 * because normally refunds show up right away in the transaction list. If these still exist, that might
		 * be a sign of some problem. */
		List<Order> nonZeroRefundings = orderDAO.getOrdersWithNonZeroRefundings();
		if (nonZeroRefundings.size() > 0) {
			throw new Exception("Orders exist having non-zero refundings even AFTER fetchtransactions.");
		}
		
		/* Make sure that the account balance as reported by the bank is equal to the balance according
		 * to SellStar's transaction table. */
		BigDecimal sellstarBalance = transactionDAO.getBalance();
		logger.debug("SellStar's balance according to the transaction table: " + sellstarBalance);
		if (sellstarBalance.compareTo(balance.getValue()) != 0) {
			logger.error("Balance according to the bank is " + balance.getValue()+ " and according to SellStar's transaction table " + sellstarBalance);
			throw new Exception("Balance according to the bank is " + balance.getValue() + " and according to SellStar's transaction table " + sellstarBalance);
		}
	}

	/* Makes a payment that doesn't have to do with any order (for example provision payment to huutonet). */
	/*public void makePayment(BigDecimal sum, String accountNumber, String receiverName, String message, String[] args) throws Exception {
		/* Fetch the arguments. /
		String user = args[5];
		String password = args[6];
		this.seleniumRobot.getSelenium().setTimeout("120000");
		ArrayList<String> bankCodes = new ArrayList<String>();
		for (int i = 7; i < args.length; i++) {
			bankCodes.add(args[i]);
		}

		logger.info("Making payment of " + sum + " euros to account " + accountNumber + "(receiver name = " + receiverName
				+ ", message = " + message + ")");
		String sumStr = ("" + MathUtilities.roundTo2Decimals(sum)).replace(".", ",");
		if (!bankRobot.isLoggedIn()) {
			bankRobot.login(user, password, bankCodes);
		}
		bankRobot.makeTransaction(message, sumStr, receiverName, accountNumber);
		bankRobot.confirmTransactions(user, password, bankCodes);
		bankRobot.logout();
		logger.info("Done making payment.");
		throw new Exception("Should this method ever be used?");
	}*/
	
	/**
	 * This method figures out which transactions were new and adds those ones to the database.
	 * 
	 * @param transactions List of transactions.
	 * @return Number of transactions actually added to the database (possible already existing transactions
	 * are not added).
	 */
	protected int addNewTransactionsToDB(List<Transaction> transactions) throws Exception {
		logger.info("Processing " + transactions.size() + " transactions.");
		int unmappableTransactions = 0;
		int transactionsAdded = 0;

		for(Transaction transaction : transactions) {
			/* There is no unique id for each transaction in the internet bank, so it's hard to know
			 * if a certain transaction already has been save to the database or not. Assumption:
			 * if all fields have the same value then two transactions (one in bank and one in our db)
			 * are the same.
			 * 
			 * Only store the transaction to the database if there are fewer transactions in the database
			 * than in the list. This doesn't work if we only fetch a very small subset of transactions.
			 * For example if there are 10 identical transactions in the database and in the internet
			 * bank there are 11 corresponding ones, then if we fetch them one at a time from the
			 * internet bank, then the 11th one will never be stored. However here we always fetch the
			 * transactions starting from 4 days backward counting from the latest entry in the database
			 * up to and including the latest transaction and then this algorithm should work. */
			int nbrInDb = transactionDAO.identicalTransactionsFoundInDb(transaction);
			int nbrInList = transactionDAO.identicalTransactionsFoundInList(transaction,transactions);
			//logger.debug("nbrInDb = " + nbrInDb + ", nbrInList = " + nbrInList);
			if (nbrInDb < nbrInList) {
				logger.debug("storing");
				//boolean associated = associateTransactionWithOrder(transaction);
				Order order = this.orderDAO.getByReferenceNumberOnly(transaction.getReferenceNumber(),transaction.getCurrency());
				if (order == null) {
					logger.debug("Transaction found that cannot be mapped to an order (id = " + transaction.getId()
							+ ", ref nr = " + transaction.getReferenceNumber() + ", date = " + transaction.getDate() + ")");
					unmappableTransactions++;
				} else {
					associateTransactionWithOrder(transaction,order);
				}
				transactionDAO.save(transaction);
				transactionDAO.flush(); //do a flush in order to catch database errors right away
				transactionsAdded++;
			} else {
				//logger.debug("not storing");
			}
		}

		logger.info("Stored " + transactionsAdded + " new transaction(s) to the database.");
		logger.info((transactionsAdded - unmappableTransactions) + " transaction(s) were mapped to an order.");
		if (unmappableTransactions > 0) {
			logger.warn("Warning: " + unmappableTransactions + " transaction(s) found that couldn't be mapped to any order!");
		}
		return transactionsAdded;
	}
	
}
