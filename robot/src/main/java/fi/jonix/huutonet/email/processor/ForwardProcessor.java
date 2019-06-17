package fi.jonix.huutonet.email.processor;

import java.net.URL;
import java.util.List;

import javax.mail.Message;
import javax.mail.internet.InternetAddress;

import org.apache.log4j.Logger;
import org.springframework.stereotype.Component;

import fi.jonix.huutonet.domain.ApplicationContextPlaceholder;
import fi.jonix.huutonet.domain.model.Market;
import fi.jonix.huutonet.domain.model.Provider;
import fi.jonix.huutonet.domain.model.Seller;
import fi.jonix.huutonet.domain.model.SystemEmail;
import fi.jonix.huutonet.domain.model.User;
import fi.jonix.huutonet.domain.model.dao.MarketDAO;
import fi.jonix.huutonet.domain.model.dao.ProviderDAO;
import fi.jonix.huutonet.domain.model.dao.SellerDAO;
import fi.jonix.huutonet.domain.model.dao.SettingsDAO;
import fi.jonix.huutonet.domain.model.dao.UserDAO;
import fi.jonix.huutonet.email.EmailProcessor;
import fi.jonix.huutonet.email.EmailRobot;
import fi.jonix.huutonet.email.EmailSender;
import fi.jonix.huutonet.exception.CriticalSectionInfo;

/**
 * This class forwards ALL messages to a different email address.
 * 
 * @author john
 *
 */
@Component(value = "forwardProcessor")
public class ForwardProcessor extends SpecialEmailFilter {

	public static final Logger logger = Logger.getLogger(ForwardProcessor.class);
	
	private boolean isFromURL(String urlStr, String sender) throws Exception {
		String domain = sender.substring(sender.indexOf("@")+1);
		URL url = new URL(urlStr);
		String hostPart = url.getHost();
		String[] split = hostPart.split("\\.");
		if (split.length > 2) {
			//this is done in order to remove for example a prepended "www." to get the pure domain name
			hostPart = split[split.length-2] + "." + split[split.length-1];
		}
		//the reason why this one doesn't test domain.equals(hostPart) is that for example paypal
		//sometimes uses the domain paypal.com.hk
		if (domain.indexOf(hostPart) >= 0) {
			return true;
		}
		return false;
	}
	
	private String getOperatorAddressToForwardUnknownEmails() throws Exception {
		String unknownOperatorEmails = "";
		UserDAO userDAO = (UserDAO) ApplicationContextPlaceholder.applicationContext.getBean("userDAO");
		List<User> operators = userDAO.getOperators();
		for (User oper : operators) {
			if (oper.getForwardOperatorUnknownEmails()) {
				unknownOperatorEmails += oper.getEmailAddress() + ",";
			}
		}
		if (unknownOperatorEmails.equals("")) {
			throw new Exception("No email address found where to forward unknown provider emails.");
		}
		/* Remove last comma. */
		unknownOperatorEmails = unknownOperatorEmails.substring(0, unknownOperatorEmails.length()-1);

		if (unknownOperatorEmails.equals("")) {
			return null;
		} else {
			return unknownOperatorEmails;
		}
	}
	
	private String getSellerAddressToForwardUnknownEmails(Seller seller) {
		String forwardUnknownSellerEmailsTo = seller.getForwardUnknownEmails();
		if (forwardUnknownSellerEmailsTo == null) {
			forwardUnknownSellerEmailsTo = seller.getUser().getEmailAddress();
		}
		return forwardUnknownSellerEmailsTo;
	}
	
	@Override
	public boolean processSpecial(Message message, Seller seller, int mailboxType, SystemEmail systemEmail,
			CriticalSectionInfo processingInfo) throws Exception {
		String sender = ((InternetAddress) message.getFrom()[0]).getAddress();
		SettingsDAO settingsDAO = (SettingsDAO) ApplicationContextPlaceholder.applicationContext.getBean("settingsDAO");
		String subjectPrefix = settingsDAO.getValue("fi.jonix.huutonet.robot.email.processor.ForwardProcessor.subjectPrefix");
		String operatorEmail = getOperatorAddressToForwardUnknownEmails();
		
		/* If this mailbox is used purely for provider emails, then there is no seller, so then _all_
		 * emails should be forwarded to the operator. Else if this mail box is used purely for
		 * seller emails (unknownProviderEmails == null), then forward all unknown emails to the seller. */
		if (mailboxType == EmailRobot.ONLY_PROVIDER_MAILBOX) {
			logger.info("This is provider only - forward all emails to operator");
			logger.debug("Forwarding email to operator");
			EmailSender.forwardEmailInSameTransaction(systemEmail,operatorEmail,subjectPrefix + ": " + message.getSubject(),message);
			return true;
		} else if (mailboxType == EmailRobot.ONLY_SELLER_MAILBOX) {
			logger.info("This is seller only - forward all emails to seller");
			logger.debug("Forwarding email to seller");
			EmailSender.forwardEmailInSameTransaction(seller.getSystemEmail(),getSellerAddressToForwardUnknownEmails(seller),subjectPrefix + ": " + message.getSubject(),message);
			return true;
		}
		
		/* Forward all emails coming from a provider or a market or from paypal to the operator. */
		ProviderDAO providerDAO = (ProviderDAO) ApplicationContextPlaceholder.applicationContext.getBean("providerDAO");
		List<Provider> providers = providerDAO.loadAll();
		for (Provider provider : providers) {
			if (isFromURL(provider.getUrl(), sender)) {
				logger.info("This email comes from provider - forward email to operator");
				logger.debug("Forwarding email to operator");
				EmailSender.forwardEmailInSameTransaction(systemEmail,operatorEmail,subjectPrefix + ": " + message.getSubject(),message);
				return true;
			}
		}
		MarketDAO marketDAO = (MarketDAO) ApplicationContextPlaceholder.applicationContext.getBean("marketDAO");
		List<Market> markets = marketDAO.loadAll();
		for (Market market : markets) {
			if (isFromURL(market.getUrl(), sender)) {
				logger.info("This email comes from market - forward email to operator");
				logger.debug("Forwarding email to operator");
				EmailSender.forwardEmailInSameTransaction(systemEmail,operatorEmail,subjectPrefix + ": " + message.getSubject(),message);
				return true;
			}
		}
		if (isFromURL("http://www.paypal.com", sender)) {
			logger.info("This email comes from paypal - forward email to operator");
			logger.debug("Forwarding email to operator");
			EmailSender.forwardEmailInSameTransaction(systemEmail,operatorEmail,subjectPrefix + ": " + message.getSubject(),message);
			return true;
		}
		
		/* Forward all other emails to the seller. */
		logger.info("This email comes from an unknown source - forward email to seller");
		logger.debug("Forwarding email to seller");
		EmailSender.forwardEmailInSameTransaction(seller.getSystemEmail(),getSellerAddressToForwardUnknownEmails(seller),subjectPrefix + ": " + message.getSubject(),message);
		return true;
	}

}
