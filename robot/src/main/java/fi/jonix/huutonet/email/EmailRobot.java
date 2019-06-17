package fi.jonix.huutonet.email;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.mail.Address;
import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.URLName;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.apache.log4j.Logger;

import com.sun.mail.imap.IMAPSSLStore;

import fi.jonix.huutonet.bank.PaypalProcessor;
import fi.jonix.huutonet.domain.ApplicationContextPlaceholder;
import fi.jonix.huutonet.domain.model.Email;
import fi.jonix.huutonet.domain.model.Provider;
import fi.jonix.huutonet.domain.model.Seller;
import fi.jonix.huutonet.domain.model.SystemEmail;
import fi.jonix.huutonet.domain.model.User;
import fi.jonix.huutonet.domain.model.dao.EmailDAO;
import fi.jonix.huutonet.domain.model.dao.ProviderDAO;
import fi.jonix.huutonet.domain.model.dao.SellerDAO;
import fi.jonix.huutonet.domain.model.dao.SettingsDAO;
import fi.jonix.huutonet.domain.model.dao.UserDAO;
import fi.jonix.huutonet.exception.CriticalSectionInfo;
import fi.jonix.huutonet.exception.ProcessEmailLaterException;
import fi.jonix.huutonet.exception.UnprocessableEmailException;
import fi.jonix.huutonet.market.MarketRobot;
import fi.jonix.huutonet.market.SellerEmailFilter;
import fi.jonix.huutonet.market.googleorder.GoogleOrderProcessor;
import fi.jonix.huutonet.market.huuto.HuutonetProcessor;
import fi.jonix.huutonet.market.mikko.MikkoProcessor;
import fi.jonix.huutonet.order.ProviderEmailFilter;
import fi.jonix.huutonet.order.dx.DxProcessor;
import fi.jonix.huutonet.order.focalprice.FocalPriceProcessor;
import fi.jonix.huutonet.robot.BaseRobot;
import fi.jonix.huutonet.email.processor.EmailFromCustomerProcessor;
import fi.jonix.huutonet.email.processor.EmailTools;
import fi.jonix.huutonet.email.processor.ForwardProcessor;
import fi.jonix.huutonet.email.processor.OperatorProcessor;
import fi.jonix.huutonet.tools.Encryption;

/**
 * Base class for email robots.
 * 
 * @author john
 *
 */
public class EmailRobot implements BaseRobot {

	public static final Logger logger = Logger.getLogger(EmailRobot.class);
	
	public static int ONLY_SELLER_MAILBOX = 1;
	public static int ONLY_PROVIDER_MAILBOX = 2;
	public static int SELLER_AND_PROVIDER_MAILBOX = 3;
	
	private boolean initiated = false;
	private Store store;
	
	public void init(String seleniumProfile) throws Exception {
		throw new UnsupportedOperationException("Call init(...) instead.");
	}
	
	public void init(SystemEmail systemEmail) throws Exception {
		if (!initiated) {
			logger.debug("Initializing mailbox");
			Properties props = new Properties();
			props.put("mail.username",systemEmail.getEmailAddress());
			props.put("mail.password",Encryption.decrypt(systemEmail.getEmailPassword()));
			props.put("mail.host",systemEmail.getEmailInboundHost());
			props.put("mail.store.protocol",systemEmail.getEmailInboundProtocol());
			Session session = Session.getInstance(props);
			URLName url = new URLName(systemEmail.getEmailInboundProtocol(),systemEmail.getEmailInboundHost(),
					systemEmail.getEmailInboundPort(),"",systemEmail.getEmailAddress(),Encryption.decrypt(systemEmail.getEmailPassword()));
			if (systemEmail.getEmailInboundSsl()) {
				store = new IMAPSSLStore(session,url);
			} else {
				store = session.getStore(url);
			}
			store.connect();
			initiated = true;
			logger.debug("Mailbox initialized");
		}
	}

	public void tearDown() throws Exception {
		if (initiated) {
			logger.debug("Tearing down mailbox");
			store.close();
			initiated = false;
			logger.debug("Mailbox torn down");
		}
	}

	/**
	 * This method copies message from sourceFolder to destinationFolder and then
	 * removes the message from the sourceFolder.
	 * 
	 * @param message
	 * @param fromFolder
	 * @param toFolder
	 * @throws Exception
	 */
	private void moveEmailToFolder(Message message, Folder sourceFolder, Folder destinationFolder) throws Exception {
		try {
			Message[] msgArr = new Message[1];
			msgArr[0] = message;
			//logger.debug("Copying message to the " + destinationFolder.getName() + " folder.");
			boolean copyingSucceeded = false;
			//try {
				sourceFolder.copyMessages(msgArr,destinationFolder);
				copyingSucceeded = true;
			/*} catch (Exception e) {
				logger.error("Copying to the " + destinationFolder.getName()
						+ " folder failed. Leaving processed mail in " + sourceFolder.getName() + ": " + e);
			}*/
			if (copyingSucceeded) {
				//logger.debug("Marking message for removal from the " + sourceFolder.getName() + ".");
				message.setFlag(Flags.Flag.DELETED,true);
				logger.debug("Moved message to the " + destinationFolder.getName() + " folder.");
			}
		} catch (Exception e) {
			logger.error("Error moving email to other folder: ", e);
			/* Go to the error state if this occurs. Otherwise the following could happen: we get
			 * a "won bid" email and process it thereby creating a new order. Then the mailbox is
			 * closed due to some gmail error. Then at the next run the same email is processed
			 * a second time and a second order is created. This actually happened once for
			 * someuser@gmail.com (orders 6231 and 6232). */
			throw new Exception("Error moving email to other folder: ", e);
		}
	}

	private void addEmailProcessors(MultiProcessorEmailRobot processor, int mailboxType) {
		if (mailboxType == ONLY_PROVIDER_MAILBOX || mailboxType == SELLER_AND_PROVIDER_MAILBOX) {
			List<ProviderEmailFilter> providerEmailProcessors = (List<ProviderEmailFilter>) ApplicationContextPlaceholder.applicationContext.getBean("providerEmailProcessors");
			for (ProviderEmailFilter emailProc : providerEmailProcessors) {
				logger.debug("Adding provider email processor " + emailProc.getClass().getName());
				processor.addEmailProcessor(emailProc);
			}
		}
		if (mailboxType == ONLY_SELLER_MAILBOX || mailboxType == SELLER_AND_PROVIDER_MAILBOX) {
			/* Remember to add the GoogleOrderProcessor and OperatorProcessor before the EmailFromCustomerProcessor. */
			List<SellerEmailFilter> sellerEmailProcessors = (List<SellerEmailFilter>) ApplicationContextPlaceholder.applicationContext.getBean("sellerEmailProcessors");
			for (SellerEmailFilter emailProc : sellerEmailProcessors) {
				logger.debug("Adding seller email processor " + emailProc.getClass().getName());
				processor.addEmailProcessor(emailProc);
			}
		}
		/* Always add the ForwardProcessor at the end of the list. */
		logger.debug("Adding the following email processor: ForwardProcessor");
		ForwardProcessor forwardProcessor = (ForwardProcessor) ApplicationContextPlaceholder.applicationContext.getBean("forwardProcessor");
		processor.addEmailProcessor(forwardProcessor); //ForwardProcessor använder seller
	}
	
	/* Some email addresses are used for both provider emails and seller emails. This method
	 * tells whether a given seller email is also used as a provider email. */
	private boolean isSellerEmailUsedAlsoAsProviderEmail(Seller seller) {
		ProviderDAO providerDAO = (ProviderDAO) ApplicationContextPlaceholder.applicationContext.getBean("providerDAO");
		List<Provider> providers = providerDAO.loadAll();
		for (Provider provider : providers) {
			if (provider.getSystemEmail().getId().equals(seller.getSystemEmail().getId())) {
				return true;
			}
		}
		return false;
	}
	
	/* Some email addresses are used for both provider emails and seller emails. This method
	 * tells whether a given provider email is also used as a seller email. */
	private boolean isProviderEmailUsedAlsoAsSellerEmail(Provider provider) {
		SellerDAO sellerDAO = (SellerDAO) ApplicationContextPlaceholder.applicationContext.getBean("sellerDAO");
		List<Seller> sellers = sellerDAO.loadAll();
		for (Seller seller : sellers) {
			if (seller.getSystemEmail().getId().equals(provider.getSystemEmail().getId())) {
				return true;
			}
		}
		return false;
	}
	
	/**
	 * This method reads the inbox and for each email calls the abstract process() method.
	 * If the email was successfully processed, then the email is moved to the processed folder.
	 * 
	 * @throws Exception
	 */
	public void processInbox(MultiProcessorEmailRobot processor, Seller seller, boolean inErrorState, CriticalSectionInfo section) throws Exception {
		logger.info("processInbox.seller");
		section.setInCriticalSection(false);
		if (inErrorState) {
			sendPendingEmails();
			return;
		}

		/* If address of the seller is also used for email from providers, then we need to determine
		 * an address where to send unknown provider emails. */
		int mailboxType = ONLY_SELLER_MAILBOX;
		if (isSellerEmailUsedAlsoAsProviderEmail(seller)) {
			mailboxType = SELLER_AND_PROVIDER_MAILBOX;
		}
		
		/* Add the email processors. */
		addEmailProcessors(processor,mailboxType);
		
		/* Process the mail box. */
		try {
			init(seller.getSystemEmail());
			processInboxInternal(processor,seller,mailboxType,seller.getSystemEmail(),section);
		} finally {
			tearDown();
			processor.removeAllEmailProcessors();
		}

		sendPendingEmails();
	}
	
	/**
	 * This method reads the inbox and for each email calls the abstract process() method.
	 * If the email was successfully processed, then the email is moved to the processed folder.
	 * This method should be called _only_ when the mailbox in question is NOT used also for
	 * seller emails.
	 * 
	 * @throws Exception
	 */
	public void processInbox(MultiProcessorEmailRobot processor, Provider provider, boolean inErrorState, CriticalSectionInfo section) throws Exception {
		logger.info("processInbox.provider");
		section.setInCriticalSection(false);
		if (inErrorState) {
			sendPendingEmails();
			return;
		}
		int mailboxType = ONLY_PROVIDER_MAILBOX;

		/* Make sure that this mailbox is used only for provider emails. */
		if (isProviderEmailUsedAlsoAsSellerEmail(provider)) {
			throw new Exception("Internal error: use processInbox(MultiProcessorEmailRobot processor, Seller seller) instead");
		}
		
		/* Add the email processors. */
		addEmailProcessors(processor,mailboxType);
		
		/* Process the mail box. */
		try {
			init(provider.getSystemEmail());
			processInboxInternal(processor,null,mailboxType,provider.getSystemEmail(),section);
		} finally {
			tearDown();
			processor.removeAllEmailProcessors();
		}

		sendPendingEmails();
	}

	private void sendPendingEmails() throws Exception {
		EmailDAO emailDAO = (EmailDAO) ApplicationContextPlaceholder.applicationContext.getBean("emailDAO");
		EmailSenderHelper emailSenderHelper = (EmailSenderHelper) ApplicationContextPlaceholder.applicationContext.getBean("emailSenderHelper");
		List<Email> pendingSendings = emailDAO.getEmailsPendingForSending();
		logger.info("Sending " + pendingSendings.size() + " unsent emails.");

		/* First try sending the ones that haven't earlier been tried. */
		Map<Long,String> tried = new HashMap<Long,String>();
		for (Email email : pendingSendings) {
			if (email.getSendingAttempts().intValue() == 0) {
				try {
					tried.put(email.getId(), "");
					emailSenderHelper.sendEmailInSeparateTransaction(email);
				} finally {
					email.setSendingAttempts(email.getSendingAttempts()+1);
					emailDAO.update(email); //need to save explicitly since this is outside of any transaction
				}
			}
		}
		
		/* Then try the ones that have been (unsuccessfully) sent earlier. */
		for (Email email : pendingSendings) {
			if (tried.get(email.getId()) == null && email.getSendingAttempts().intValue() < Email.MAX_SENDING_ATTEMPTS) {
				try {
					emailSenderHelper.sendEmailInSeparateTransaction(email);
				} finally {
					email.setSendingAttempts(email.getSendingAttempts()+1);
					emailDAO.update(email); //need to save explicitly since this is outside of any transaction
					if (email.getSendingAttempts().intValue() >= Email.MAX_SENDING_ATTEMPTS && email.getDirection().intValue() != Email.OUT_SENT) {
						throw new Exception("Giving up sending email " + email.getId());
					}
				}
			}
		}
	}
	
	/**
	 * This method reads the inbox and for each email calls the abstract process() method.
	 * If the email was successfully processed, then the email is moved to the processed folder.
	 * 
	 * @throws Exception
	 */
	private void processInboxInternal(MultiProcessorEmailRobot processor, Seller seller, int mailboxType,
			SystemEmail systemEmail, CriticalSectionInfo section) throws Exception {
		logger.info("processing inbox with mailboxType " + mailboxType);
		Folder folder = null;
		Folder processedFolder = null;
		Folder unProcessedFolder = null;
		int unprocessed = 0;
		StringBuffer unprocBuf = new StringBuffer();
		SettingsDAO settingsDAO = (SettingsDAO) ApplicationContextPlaceholder.applicationContext.getBean("settingsDAO");
		EmailSenderHelper emailSenderHelper = (EmailSenderHelper) ApplicationContextPlaceholder.applicationContext.getBean("emailSenderHelper");
		try {
			//Folder folder = store.getDefaultFolder();
			folder = store.getFolder("INBOX");
			processedFolder = store.getFolder("processed");
			unProcessedFolder = store.getFolder("unprocessed");
			if (folder != null && folder.exists() && processedFolder != null && processedFolder.exists()
					 && unProcessedFolder != null && unProcessedFolder.exists()) {
				if (!processedFolder.isOpen()) {
					processedFolder.open(Folder.READ_WRITE);
				}
				if (!unProcessedFolder.isOpen()) {
					unProcessedFolder.open(Folder.READ_WRITE);
				}
				if (!folder.isOpen()) {
					folder.open(Folder.READ_WRITE);
				}
				Message[] messages = folder.getMessages();
				for (int i = 0; i < messages.length; i++) {
					Message message = messages[i];
					Address[] address = message.getFrom();
					if (address == null || address.length == 0 || !(address[0] instanceof InternetAddress) || !(message instanceof MimeMessage)) {
						throw new Exception("Sender couldn't be determined or message not Mime.");
					} else {
						String contents = EmailTools.getPlainTextContents(message);
						logger.debug("Email received from " + ((InternetAddress)address[0]).getAddress() + " having subject \""
								+ message.getSubject() + "\" and contents: " + contents);
						try {
							CriticalSectionInfo processingInfo = new CriticalSectionInfo();
							processingInfo.setCanProcessEmailOnlyOnce(true); //by default an email can be processed only once
							boolean processed = processor.processSpecial(message,seller,mailboxType,systemEmail,processingInfo);
							/* Move the email to the processed folder if it was processed successfully. */
							if (processed) {
								/* It's critical that the following goes through, because otherwise the following
								 * could happen:
								 * 1.) we get a "new order" email which is processed
								 * 2.) moving the email to the processed folder fails
								 * 3.) at the next run checkemail will process the same
								 *     email again and errouneously create a second order
								 * There is one exception to this though: if canProcessEmailOnlyOnce is false, then
								 * it's not critical that moving the email to another folder goes through. */
								section.setInCriticalSection(processingInfo.isCanProcessEmailOnlyOnce()/*true*/);
								try {
									moveEmailToFolder(message,folder,processedFolder);
								} catch (Exception e) {
									if (section.isInCriticalSection()) {
										logger.error("Couldn't move email to processed folder although we are in a critical section.");
									} else {
										logger.info("Couldn't move email to processed folder but we are NOT in a critical section.");
									}
									throw e;
									/*logger.warn("Couldn't move email to processed folder although we are in a critical section: ",e);
									logger.warn("Sleeping for two minutes and trying again.");
									Thread.sleep(1000*60*2);
									try {
										moveEmailToFolder(message,folder,processedFolder);
										logger.info("Moving to other folder succeeded at the second attempt.");
									} catch (Exception e2) {
										logger.warn("Failed also at the second attempt.");
										throw e2;
									}*/
								}
								section.setInCriticalSection(false);
							} else {
								logger.info("Unprocessable email from " + ((InternetAddress)address[0]).getAddress() + " having subject "
										+ message.getSubject() + ". Moving to unprocessed folder.");
								moveEmailToFolder(message,folder,unProcessedFolder);
								unprocessed++;
							}
							emailSenderHelper.storeEmailToDatabase(systemEmail,((InternetAddress) message.getFrom()[0]).getAddress(),
									systemEmail.getEmailAddress(),message.getSubject(),contents,Email.REAL_EMAIL,Email.IN,null,Email.PRIORITY_NORMAL);
						} catch (ProcessEmailLaterException e) {
							logger.info("Couldn't process email received from " + ((InternetAddress)address[0]).getAddress() + " having subject \""
									+ message.getSubject() + "\" - postponing processing (message = " + e.getMessage() + ")");
						} catch (UnprocessableEmailException e) {
							//TODO: possibly also NullPointerExceptions could be caught and handled the same way as UnprocessableEmailExceptions
							logger.debug("Unprocessable email: ",e);
							logger.info("Unprocessable email from " + ((InternetAddress)address[0]).getAddress() + " having subject " + message.getSubject()
									+ " (" + e.getMessage() + "). Moving to unprocessed folder.");
							moveEmailToFolder(message,folder,unProcessedFolder);
							unprocessed++;
							unprocBuf.append("\n\n" + message.getSubject() + ":\n" + e.getMessage());
						}
					}
				}
			}
		} catch (Exception e) {
			logger.debug("Error in processInboxInternal: ",e);
			throw e;
		} finally {
			if (folder != null && folder.isOpen()) {
				folder.close(true); //expunge deleted messages
			}
			if (processedFolder != null && processedFolder.isOpen()) {
				processedFolder.close(false); //don't expunge anything
			}
			if (unProcessedFolder != null && unProcessedFolder.isOpen()) {
				unProcessedFolder.close(false); //don't expunge anything
			}
			/* Send an email if some emails where moved to the unprocessed directory. */
			if (unprocessed > 0) {
				EmailSender.sendOperationProblemReport("Unprocessed emails",
						unprocessed + " unprocessed emails. Please resolve manually." + unprocBuf.toString(),systemEmail);
			}
		}
	}

}
