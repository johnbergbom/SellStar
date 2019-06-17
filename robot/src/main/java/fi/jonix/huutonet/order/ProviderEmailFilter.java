package fi.jonix.huutonet.order;

import javax.mail.Message;

import fi.jonix.huutonet.domain.model.Seller;
import fi.jonix.huutonet.domain.model.SystemEmail;
import fi.jonix.huutonet.email.processor.EmailFilter;
import fi.jonix.huutonet.exception.CriticalSectionInfo;

/**
 * This class implements processSpecial and processSellerEmail and throws an
 * UnsupportedOperationException if any of those are called. Subclasses are supposed
 * to implement the processProviderEmail method.
 * 
 * @author john
 *
 */
public abstract class ProviderEmailFilter extends EmailFilter {

	@Override
	public boolean processSpecial(Message message, Seller seller, int mailboxType, SystemEmail systemEmail,
			CriticalSectionInfo processingInfo) throws Exception {
		throw new UnsupportedOperationException("Use processProviderEmail instead.");
	}
	
	@Override
	public boolean processSellerEmail(Message message, Seller seller,
			CriticalSectionInfo processingInfo) throws Exception {
		throw new UnsupportedOperationException("Use processProviderEmail instead.");
	}
	
}
