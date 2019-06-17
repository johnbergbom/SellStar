package fi.jonix.huutonet.email;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.mail.Message;

import org.apache.log4j.Logger;

import fi.jonix.huutonet.domain.ApplicationContextPlaceholder;
import fi.jonix.huutonet.domain.model.Seller;
import fi.jonix.huutonet.domain.model.SystemEmail;
import fi.jonix.huutonet.domain.model.dao.SellerDAO;
import fi.jonix.huutonet.email.processor.SpecialEmailFilter;
import fi.jonix.huutonet.exception.CriticalSectionInfo;
import fi.jonix.huutonet.market.SellerEmailFilter;
import fi.jonix.huutonet.order.ProviderEmailFilter;

/**
 * Class that can register multiple processors in order to take
 * different actions depending on the email.
 * 
 * @author john
 *
 */
public class MultiProcessorEmailRobot extends SpecialEmailFilter {

	public static final Logger logger = Logger.getLogger(MultiProcessorEmailRobot.class);

	private List<EmailProcessor> emailProcessors;
	
	public MultiProcessorEmailRobot() {
		emailProcessors = new ArrayList<EmailProcessor>();
	}
	
	public void addEmailProcessor(EmailProcessor emailProcessor) {
		if (emailProcessors == null) {
			emailProcessors = new ArrayList<EmailProcessor>();
		}
		emailProcessors.add(emailProcessor);
	}
	
	public void removeAllEmailProcessors() {
		emailProcessors.clear();
	}
	
	/**
	 * This method creates a new transaction (defined in the spring application
	 * context) and within that transaction it tries to process the email using
	 * the specified email processors in turn until one processor accepts the
	 * message. Any exception in the email processor rolls back the transaction.
	 */
	@Override
	public boolean processSpecial(Message message, Seller seller, int mailboxType, SystemEmail systemEmail,
			CriticalSectionInfo processingInfo) throws Exception {
		SellerDAO sellerDAO = (SellerDAO) ApplicationContextPlaceholder.applicationContext.getBean("sellerDAO");
		sellerDAO.refresh(seller);
		Iterator<EmailProcessor> iter = emailProcessors.iterator();
		while (iter.hasNext()) {
			EmailProcessor processor = iter.next();
			if (processor instanceof SellerEmailFilter) {
				if (processor.processSellerEmail(message, seller, processingInfo)) {
					logger.debug("Message accepted by " + processor.getClass().getSimpleName());
					return true;
				}
			} else if (processor instanceof ProviderEmailFilter) {
				if (processor.processProviderEmail(message,systemEmail, processingInfo)) {
					logger.debug("Message accepted by " + processor.getClass().getSimpleName());
					return true;
				}
			} else if (processor instanceof SpecialEmailFilter) {
				if (processor.processSpecial(message, seller, mailboxType, systemEmail, processingInfo)) {
					logger.debug("Message accepted by " + processor.getClass().getSimpleName());
					return true;
				}
			} else {
				throw new Exception("Internal error: unknown email processor.");
			}
		}
		return false;
	}

}
