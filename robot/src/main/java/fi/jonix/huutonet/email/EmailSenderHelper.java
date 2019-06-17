package fi.jonix.huutonet.email;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.URLName;
import javax.mail.Message.RecipientType;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import fi.jonix.huutonet.domain.ApplicationContextPlaceholder;
import fi.jonix.huutonet.domain.model.OrderBundle;
import fi.jonix.huutonet.domain.model.Seller;
import fi.jonix.huutonet.domain.model.SystemEmail;
import fi.jonix.huutonet.domain.model.User;
import fi.jonix.huutonet.domain.model.dao.EmailDAO;
import fi.jonix.huutonet.domain.model.dao.SellerDAO;
import fi.jonix.huutonet.domain.model.dao.SettingsDAO;
import fi.jonix.huutonet.domain.model.dao.UserDAO;
import fi.jonix.huutonet.domain.model.Email;
import fi.jonix.huutonet.email.processor.EmailTools;
import fi.jonix.huutonet.tools.Encryption;

import org.apache.log4j.Logger;

import com.sun.mail.smtp.SMTPSSLTransport;

//TODO: this class could at some point be split into two: one part is moved to the logic-module
//and the part that actually physically sends the email (the sendEmail-method) maybe needs to stay
//in the robot-module.
@Component(value = "emailSenderHelper")
public class EmailSenderHelper {

	public static final Logger logger = Logger.getLogger(EmailSenderHelper.class);

	@Autowired
	private EmailDAO emailDAO;
	
	@Autowired
	private SettingsDAO settingsDAO;
	
	@Autowired
	private UserDAO userDAO;

	@Autowired
	private SellerDAO sellerDAO;

	/**
	 * This method should be used when we want the email to be sent _even_ if the transaction is rolled back, since this
	 * method executes in its own transaction. This is suitable for problem reports to the operator.
	 */
	public Email addPendingEmailSendingInSeparateTransaction(SystemEmail systemEmail,
			String receiverEmailAddress, String subject, String contents, int priority) {
		logger.debug("Storing email to database for later sending.");
		return storeEmailToDatabase(systemEmail,systemEmail.getEmailAddress(),
				receiverEmailAddress,subject,contents,Email.REAL_EMAIL,Email.OUT_PENDING,
				null,priority);
	}

	/**
	 * This method should be used whenever we DON'T want the email to get sent if the transaction is rolled back, since
	 * this method executes in the same transaction as the calling function. This is suitable for emails to the customer.
	 */
	public Email addPendingEmailSendingInSameTransaction(SystemEmail systemEmail,
			String receiverEmailAddress, String subject, String contents, OrderBundle bundle) {
		logger.debug("Storing email to database for later sending.");
		return storeEmailToDatabase(systemEmail,systemEmail.getEmailAddress(),
				receiverEmailAddress,subject,contents,Email.REAL_EMAIL,Email.OUT_PENDING,bundle,
				Email.PRIORITY_NORMAL);
	}
	
	/**
	 * @return True if text contains some string that cannot be sent to the customer.
	 */
	private boolean prohibitedStrings(String text) {
		String newText = text.toLowerCase();
		//TODO: get the prohibited strings from the database (from provider.name).
		if (newText.indexOf("dealextreme") >= 0 || newText.indexOf("focalprice") >= 0
				|| (newText.indexOf("deal") >= 0 && newText.indexOf("extreme") >= 0)
				|| (newText.indexOf("focal") >= 0 && newText.indexOf("price") >= 0)
				|| (newText.indexOf("hautala serv") >= 0 && newText.indexOf("hautalaserv") >= 0)) {
			return true;
		}
		return false;
	}

	/* If we are sending to someone who isn't in the user's table, then make sure that
	 * the email doesn't doesn't contain any prohibited strings (safety measure to make
	 * sure we don't accidentally tell customers about DealExtreme or FocalPrice). */
	private boolean safetyCheckNeeded(String receiver, SystemEmail systemEmail) {
		List<String> emailAddresses = new ArrayList<String>();
		List<User> users = userDAO.loadAll();
		for (User user : users) {
			emailAddresses.add(user.getEmailAddress());
		}
		/* We need to check also the system emails of the sellers, because when the seller
		 * answers for example a "Email from customer", then the answer to SellStar
		 * usually contains prohibited strings (that are indeed allowed when the receiver
		 * is the seller).
		 * => however we get the error "HibernateSystemException: a different object
		 * with the same identifier value was already associated with the session" if
		 * we iterate over all systemEmails, so therefore let's just use the one
		 * given as a parameter. */
		/*List<Seller> sellers = sellerDAO.loadAll();
		for (Seller seller : sellers) {
			emailAddresses.add(seller.getSystemEmail().getEmailAddress());
		}*/
		emailAddresses.add(systemEmail.getEmailAddress());
		for (String email : emailAddresses) {
			if (email.equalsIgnoreCase(receiver)) {
				return false;
			}
		}
		return true;
	}
	
	protected Email storeEmailToDatabase(SystemEmail systemEmail, String senderEmailAddress,
			String receiverEmailAddress, String subject, String contents, int type, int direction,
			OrderBundle bundle, int priority) {
		if (safetyCheckNeeded(receiverEmailAddress,systemEmail)) {
			logger.debug("Checking for prohibited strings.");
			if (prohibitedStrings(subject) || prohibitedStrings(contents)) {
				throw new RuntimeException("Email contains prohibited string.");
			}
		} else {
			logger.debug("Skipping checking of prohibited strings.");
		}
		
		Email email = new Email();
		email.setDate(new Date());
		email.setSystemEmail(systemEmail);
		email.setSender(senderEmailAddress);
		email.setReceiver(receiverEmailAddress);
		email.setContents(contents);
		email.setSubject((subject == null ? "" : subject)); //some customers send emails with no subject
		email.setType(type);
		email.setDirection(direction);
		email.setSendingAttempts(0);
		email.setOrderBundle(bundle);
		email.setPriority(priority);
		emailDAO.save(email);
		emailDAO.flush(); //do a flush in order to catch database errors right away
		logger.debug("Email stored to database.");
		return email;
	}
	
	/**
	 * 
	 * @return Returns a new mail session object.
	 */
	private Session getSession(SystemEmail systemEmail) {
		Properties props = new Properties();
		props.put("mail.transport.protocol",systemEmail.getEmailOutboundProtocol());
		props.put("mail.smtp.host",systemEmail.getEmailOutboundHost());
		props.put("mail.username",systemEmail.getEmailAddress());
		props.put("mail.password",Encryption.decrypt(systemEmail.getEmailPassword()));
		props.put("mail.host",systemEmail.getEmailOutboundHost());
		return Session.getInstance(props);
	}

	public boolean sendEmailInSeparateTransaction(Email email) throws Exception {
		emailDAO.refresh(email);
		Session session = getSession(email.getSystemEmail());
		MimeMessage message = new MimeMessage(session);
		message.setText(email.getContents(),"utf-8");
		message.setSubject(email.getSubject(),"utf-8");
		sendEmail(email,message,session);
		email.setDirection(Email.OUT_SENT);
		emailDAO.update(email);
		emailDAO.flush(); //do a flush in order to catch database errors right away
		return true;
	}

	public void sendEmailInSameTransaction(SystemEmail systemEmail, String receiverEmailAddress, String subject, Message message) throws Exception {
		String contents = EmailTools.getPlainTextContents(message);

		/* Only send email if we are in the production environment and the email faking isn't
		 * set to true. */
		if (Boolean.parseBoolean(settingsDAO.getValue("fi.jonix.huutonet.robot.email.fakeSend"))
				|| !Boolean.parseBoolean(settingsDAO.getValue("productionEnvironment"))) {
			if (((MimeMessage)message).getSender() != null) {
				logger.warn("Faking sending email coming from " + ((MimeMessage)message).getSender() + " having subject \""
						+ message.getSubject() + "\" to " + receiverEmailAddress);
			} else {
				logger.warn("Faking sending email having subject \"" + message.getSubject() + "\" to " + receiverEmailAddress);
			}
			logger.debug("Contents of email to be fake sent: " + contents);
			storeEmailToDatabase(systemEmail,systemEmail.getEmailAddress(),receiverEmailAddress,subject,contents,
					Email.FAKE_EMAIL,Email.OUT_SENT,null,Email.PRIORITY_NORMAL);
			return;
		}
		Email email = storeEmailToDatabase(systemEmail,systemEmail.getEmailAddress(),receiverEmailAddress,subject,contents,
				Email.REAL_EMAIL,Email.OUT_PENDING,null,Email.PRIORITY_NORMAL);
		sendEmail(email,message,getSession(systemEmail));
		email.setDirection(Email.OUT_SENT);
		emailDAO.update(email);
		emailDAO.flush(); //do a flush in order to catch database errors right away
	}
	
	/**
	 * This method connects to the outgoing mail server and sends the email.
	 * 
	 * @param receiverEmailAddress
	 * @param subject
	 * @param message
	 * @param session
	 * @throws Exception
	 */
	private void sendEmail(Email email, Message message, Session session) throws Exception {
		if (Boolean.parseBoolean(settingsDAO.getValue("fi.jonix.huutonet.robot.email.fakeSend"))
				|| !Boolean.parseBoolean(settingsDAO.getValue("productionEnvironment"))
				|| !email.getType().equals(Email.REAL_EMAIL) || !email.getDirection().equals(Email.OUT_PENDING)) {
			throw new Exception("Internal error in sendEmail");
		}

		MimeMessage newMessage = new MimeMessage((MimeMessage)message);
		
		/* Make sure that the email is _only_ sent to the specified receiver, even
		 * if a forwarded email contained several recipients. */
		newMessage.setRecipients(RecipientType.TO,new InternetAddress[0]);
		newMessage.setRecipients(RecipientType.CC,new InternetAddress[0]);
		newMessage.setRecipients(RecipientType.BCC,new InternetAddress[0]);
		if (email.getSubject() != null) {
			newMessage.setSubject(email.getSubject(),"utf-8");
		}
		String sender = email.getSystemEmail().getEmailAddress();
		String password = email.getSystemEmail().getEmailPassword();
		Integer port = email.getSystemEmail().getEmailOutboundPort();
		String protocol = email.getSystemEmail().getEmailOutboundProtocol();
		String host = email.getSystemEmail().getEmailOutboundHost();
		newMessage.setFrom(new InternetAddress(email.getSystemEmail().getName() + " <" + sender + ">"));
		newMessage.setSender(new InternetAddress(sender));
		InternetAddress[] recipient = new InternetAddress[1];
		recipient[0] = new InternetAddress(email.getReceiver(),true);
		newMessage.setHeader("To",email.getReceiver());
		//newMessage.setHeader("Content-Type","text/plain; charset=\"utf-8\"");
		newMessage.setHeader("Content-Transfer-Encoding", "quoted-printable");

		/* Store the email to the log _before_ we try to send it, which might help manual recovery in case
		 * the email sending failed and the transaction was rolled back. */
		if (((MimeMessage)message).getSender() != null) {
			logger.info("Forwarding email " + email.getId() + " coming from " + ((MimeMessage)message).getSender() + " having subject \""
					+ message.getSubject() + "\" to " + email.getReceiver());
		} else {
			logger.info("Sending email " + email.getId() + " coming from " + sender + " having subject \"" + message.getSubject()
					+ "\" to " + email.getReceiver());
		}
		logger.debug("Contents of email to be sent: " + EmailTools.getPlainTextContents(message));

		Transport transport = null;
		try {
			URLName url = new URLName(protocol,host,port,"",sender,Encryption.decrypt(password));
			transport = new SMTPSSLTransport(session,url);
			transport.connect();
			transport.sendMessage(newMessage,recipient);
		} finally {
			if (transport != null) {
				if (transport.isConnected()) {
					transport.close();
				}
			}
		}
	}

}
