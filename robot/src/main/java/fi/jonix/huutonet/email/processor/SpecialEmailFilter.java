package fi.jonix.huutonet.email.processor;

import javax.mail.Message;

import fi.jonix.huutonet.domain.model.Seller;
import fi.jonix.huutonet.domain.model.SystemEmail;
import fi.jonix.huutonet.exception.CriticalSectionInfo;

/**
 * This class implements processProviderEmail and processSellerEmail and throws an
 * UnsupportedOperationException if any of those are called. Subclasses are supposed
 * to implement the processSpecial method.
 * 
 * @author john
 *
 */
public abstract class SpecialEmailFilter extends EmailFilter {

	@Override
	public boolean processProviderEmail(Message message, SystemEmail systemEmail,
			CriticalSectionInfo processingInfo) throws Exception {
		throw new UnsupportedOperationException("Use processSpecial instead.");
	}
	
	@Override
	public boolean processSellerEmail(Message message, Seller seller,
			CriticalSectionInfo processingInfo) throws Exception {
		throw new UnsupportedOperationException("Use processSpecial instead.");
	}
	
}
