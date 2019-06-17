package fi.jonix.huutonet.email;

import java.util.List;

import javax.mail.Message;

import org.apache.log4j.Logger;

import fi.jonix.huutonet.domain.ApplicationContextPlaceholder;
import fi.jonix.huutonet.domain.model.Email;
import fi.jonix.huutonet.domain.model.OrderBundle;
import fi.jonix.huutonet.domain.model.Provider;
import fi.jonix.huutonet.domain.model.SystemEmail;
import fi.jonix.huutonet.domain.model.User;
import fi.jonix.huutonet.domain.model.dao.ProviderDAO;
import fi.jonix.huutonet.domain.model.dao.UserDAO;

/**
 * This class is a utility class for sending emails.
 * 
 * @author john
 *
 */
public class EmailSender {

	public static final Logger logger = Logger.getLogger(EmailSender.class);

	/**
	 * This method sends an error report to the operator in its own transaction (using the
	 * default systemEmail). The email is stored into the database and then sent later in a
	 * separate batch (i.e. the email is sent _even_ if the outer transaction fails after
	 * calling this method).
	 */
	public static void sendErrorReport(String subject, String contents) {
		// hardcoded to send email to operator from the DealExtreme system email address
		ProviderDAO providerDAO = (ProviderDAO) ApplicationContextPlaceholder.applicationContext.getBean("providerDAO");
		Provider provider = providerDAO.getProviderByName(Provider.DEAL_EXTREME_PROVIDER_NAME);
		SystemEmail systemEmail = provider.getSystemEmail();
		sendErrorReport(subject, contents, systemEmail);
	}
	
	/**
	 * This method sends an error report to the operator in its own transaction. The email
	 * is stored into the database and then sent later in a separate batch (i.e. the email
	 * is sent _even_ if the outer transaction fails after calling this method).
	 */
	public static void sendErrorReport(String subject, String contents, SystemEmail systemEmail) {
		UserDAO userDAO = (UserDAO) ApplicationContextPlaceholder.applicationContext.getBean("userDAO");
		List<User> operators = userDAO.getOperators();
		EmailSenderHelper emailSenderHelper = (EmailSenderHelper) ApplicationContextPlaceholder.applicationContext.getBean("emailSenderHelper");
		boolean foundReceiver = false;
		for (User oper : operators) {
			if (oper.getForwardGlobalErrors()) {
				foundReceiver = true;
				logger.info("Sending error report to operator, please wait...");
				emailSenderHelper.addPendingEmailSendingInSeparateTransaction(systemEmail,oper.getEmailAddress(),
						"Error report: " + subject,contents,Email.PRIORITY_NORMAL);
				logger.info("Error report sent successfully.");
			}
		}
		if (!foundReceiver) {
			throw new RuntimeException("Found no receiver for email.");
		}
	}
	
	/**
	 * This method sends a bug report to the operator in its own transaction (using the
	 * default systemEmail). The email is stored into the database and then sent later in a
	 * separate batch (i.e. the email is sent _even_ if the outer transaction fails after
	 * calling this method).
	 */
	public static void sendBugReport(String subject, String contents) {
		// hardcoded to send email to operator from the DealExtreme system email address
		ProviderDAO providerDAO = (ProviderDAO) ApplicationContextPlaceholder.applicationContext.getBean("providerDAO");
		Provider provider = providerDAO.getProviderByName(Provider.DEAL_EXTREME_PROVIDER_NAME);
		SystemEmail systemEmail = provider.getSystemEmail();
		sendBugReport(subject, contents, systemEmail);
	}
	
	/**
	 * This method sends a bug report to the operator in its own transaction. The email
	 * is stored into the database and then sent later in a separate batch (i.e. the email
	 * is sent _even_ if the outer transaction fails after calling this method).
	 */
	public static void sendBugReport(String subject, String contents, SystemEmail systemEmail) {
		UserDAO userDAO = (UserDAO) ApplicationContextPlaceholder.applicationContext.getBean("userDAO");
		List<User> operators = userDAO.getOperators();
		EmailSenderHelper emailSenderHelper = (EmailSenderHelper) ApplicationContextPlaceholder.applicationContext.getBean("emailSenderHelper");
		boolean foundReceiver = false;
		for (User oper : operators) {
			if (oper.getForwardBugs()) {
				foundReceiver = true;
				logger.info("Sending bug report to operator, please wait...");
				emailSenderHelper.addPendingEmailSendingInSeparateTransaction(systemEmail,oper.getEmailAddress(),
						"Bug report: " + subject,contents,Email.PRIORITY_NORMAL);
				logger.info("Bug report sent successfully.");
			}
		}
		if (!foundReceiver) {
			throw new RuntimeException("Found no receiver for email.");
		}
	}
	
	/**
	 * This method sends an operation problem report to the operator in its own transaction (using the
	 * default systemEmail). The email is stored into the database and then sent later in a
	 * separate batch (i.e. the email is sent _even_ if the outer transaction fails after
	 * calling this method).
	 */
	public static void sendOperationProblemReport(String subject, String contents) {
		// hardcoded to send email to operator from the DealExtreme system email address
		ProviderDAO providerDAO = (ProviderDAO) ApplicationContextPlaceholder.applicationContext.getBean("providerDAO");
		Provider provider = providerDAO.getProviderByName(Provider.DEAL_EXTREME_PROVIDER_NAME);
		SystemEmail systemEmail = provider.getSystemEmail();
		sendOperationProblemReport(subject, contents, systemEmail);
	}
	
	/**
	 * This method sends a bug report to the operator in its own transaction. The email
	 * is stored into the database and then sent later in a separate batch (i.e. the email
	 * is sent _even_ if the outer transaction fails after calling this method).
	 */
	public static void sendOperationProblemReport(String subject, String contents, SystemEmail systemEmail) {
		UserDAO userDAO = (UserDAO) ApplicationContextPlaceholder.applicationContext.getBean("userDAO");
		List<User> operators = userDAO.getOperators();
		EmailSenderHelper emailSenderHelper = (EmailSenderHelper) ApplicationContextPlaceholder.applicationContext.getBean("emailSenderHelper");
		boolean foundReceiver = false;
		for (User oper : operators) {
			if (oper.getForwardGlobalOperProblems()) {
				foundReceiver = true;
				logger.info("Sending operation problem report to operator, please wait...");
				emailSenderHelper.addPendingEmailSendingInSeparateTransaction(systemEmail,oper.getEmailAddress(),
						"Operation problem: " + subject,contents,Email.PRIORITY_NORMAL);
				logger.info("Operation problem report sent successfully.");
			}
		}
		if (!foundReceiver) {
			throw new RuntimeException("Found no receiver for email.");
		}
	}
	
	/**
	 * This method sends an operation problem report to the operator in its own transaction (using the
	 * default systemEmail). The email is stored into the database and then sent later in a
	 * separate batch (i.e. the email is sent _even_ if the outer transaction fails after
	 * calling this method).
	 * 
	 * Note: summary reports aren't sent right away. Rather they are sent once per day were
	 * all summary reports are bundled together as a single email.
	 */
	public static void sendOperationSummaryReport(String subject, String contents) {
		// hardcoded to send email to operator from the DealExtreme system email address
		ProviderDAO providerDAO = (ProviderDAO) ApplicationContextPlaceholder.applicationContext.getBean("providerDAO");
		Provider provider = providerDAO.getProviderByName(Provider.DEAL_EXTREME_PROVIDER_NAME);
		SystemEmail systemEmail = provider.getSystemEmail();
		sendOperationSummaryReport(subject, contents, systemEmail);
	}
	
	/**
	 * This method sends a bug report to the operator in its own transaction. The email
	 * is stored into the database and then sent later in a separate batch (i.e. the email
	 * is sent _even_ if the outer transaction fails after calling this method).
	 * 
	 * Note: summary reports aren't sent right away. Rather they are sent once per day were
	 * all summary reports are bundled together as a single email.
	 */
	public static void sendOperationSummaryReport(String subject, String contents, SystemEmail systemEmail) {
		UserDAO userDAO = (UserDAO) ApplicationContextPlaceholder.applicationContext.getBean("userDAO");
		List<User> operators = userDAO.getOperators();
		EmailSenderHelper emailSenderHelper = (EmailSenderHelper) ApplicationContextPlaceholder.applicationContext.getBean("emailSenderHelper");
		boolean foundReceiver = false;
		for (User oper : operators) {
			if (oper.getForwardGlobalOperProblems()) {
				foundReceiver = true;
				logger.info("Sending operation summary report to operator, please wait...");
				emailSenderHelper.addPendingEmailSendingInSeparateTransaction(systemEmail,oper.getEmailAddress(),
						subject,contents,Email.PRIORITY_LOW);
				logger.info("Operation summary report sent successfully.");
			}
		}
		if (!foundReceiver) {
			throw new RuntimeException("Found no receiver for email.");
		}
	}
	
	/**
	 * This method sends an email to the given address or addresses (multiple addresses are separated by a comma).
	 * The email is stored into the database and then sent later in a separate batch. If the transaction of the calling
	 * function is rolled back, then the email will NOT be sent. This is suitable for emails going to customers.
	 * 
	 * @param receiverEmailAddresses Comma separated list of email addresses.
	 * @param subject
	 * @param contents
	 * @throws Exception
	 */
	public static void sendEmailBatch(SystemEmail systemEmail, String receiverEmailAddresses, String subject, String contents, OrderBundle bundle) {
		String[] emailAddressArray = receiverEmailAddresses.split(",");
		for (String emailAddress : emailAddressArray) {
			String email = emailAddress.trim();
			EmailSenderHelper emailSenderHelper = (EmailSenderHelper) ApplicationContextPlaceholder.applicationContext.getBean("emailSenderHelper");
			emailSenderHelper.addPendingEmailSendingInSameTransaction(systemEmail,email,subject,contents,bundle);
		}
	}

	public static void sendEmailBatch(SystemEmail systemEmail, String receiverEmailAddresses, String subject, String contents) {
		sendEmailBatch(systemEmail, receiverEmailAddresses, subject, contents, null);
	}

	/**
	 * This method forwards an email to the given address. The email is sent directly (not just stored for later sending).
	 * It would actually be better to also handle forwarding as a batch type of sending, but we don't do this because
	 * that would require us to correctly being able to parse multipart emails, store them to the database and then
	 * later restore them and send them.
	 * 
	 * @param receiverEmailAddresses Comma separated list of email addresses.
	 * @param subjectPrefix Prefix which will be added to the email subject, for example "Automatic forward: ".
	 * @param message
	 * @throws Exception
	 */
	public static void forwardEmailInSameTransaction(SystemEmail systemEmail, String receiverEmailAddresses, String subject, Message message) throws Exception {
		String[] emailAddressArray = receiverEmailAddresses.split(",");
		EmailSenderHelper emailSenderHelper = (EmailSenderHelper) ApplicationContextPlaceholder.applicationContext.getBean("emailSenderHelper");
		for (String emailAddress : emailAddressArray) {
			emailSenderHelper.sendEmailInSameTransaction(systemEmail,emailAddress,subject,message);
		}
	}

}
