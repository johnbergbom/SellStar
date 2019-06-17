package fi.jonix.huutonet.bank;

import java.math.BigDecimal;
import java.util.Map;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import fi.jonix.huutonet.domain.VirtualCoin;
import fi.jonix.huutonet.domain.model.dao.TransactionDAOImpl;
import fi.jonix.huutonet.email.EmailSender;

@Component(value = "moneyTransferer")
public class MoneyTransferer {

	public static final Logger logger = Logger.getLogger(MoneyTransferer.class);
	
	@Autowired
	MoneyTransfererHelper moneyTransfererHelper;
	
	@Autowired
	private BankRobot bankRobot;
	
	public boolean transferToQD(String user, String password, Map<String,String> bankCodes) throws Exception {
		/* Make verification and get the amount to transfer. */
		StringBuffer verification = new StringBuffer();
		VirtualCoin transferSum = moneyTransfererHelper.calculateSumToTransfer(verification);
		if (transferSum.getValue().compareTo(BigDecimal.ZERO) == 0) {
			/* Note that we HAVE to throw an exception here since it's possible that the verification
			 * generator has changed the database EVEN if the sum to transfer is zero, so we need to
			 * roll back the transaction by throwing an exception (for example if no new transactions
			 * have come, then no money will be transfered, but still it's possible that for some
			 * orders or bundles setTransferedToVerification = true was set). */
			logger.info("Skipping money transfer.");
			throw new RuntimeException("Skipping money transfer.");
		}
		logger.info("Transfering " + transferSum + " euros to QD-Tech Oy.");
		//logger.debug("verification = " + verification.toString());

		/* Make the actual transfer. */
		String receiverName = "QD-Tech Oy";
		if (!bankRobot.isLoggedIn()) {
			bankRobot.login(user, password, bankCodes);
		}
		//String sum = MathUtilities.roundTo2Decimals(transferSum.getValue()).replace(".", ",");
		bankRobot.makeTransaction(TransactionDAOImpl.QD_TRANSFER_IDENTIFICATION, transferSum, receiverName, bankCodes.get("QDTECHACCOUNT"));

		/* Inform the operator about the transfer. */
		EmailSender.sendOperationProblemReport("Verification for money transfer from SellStar to QD",
				"Transfer made from SellStar to QD-Tech Oy. Please print out and add this verification"
				+ " to the book keeping.\n\n---------------------------------------------------\n\n"
				+ verification.toString());
		
		//IMPORTANT: IT'S CRUCIAL THAT UPPER LEVEL CODE ALSO CONFIRMS THE MONEY TRANSFER IN THE BANK.
		return true;
	}
	
}
