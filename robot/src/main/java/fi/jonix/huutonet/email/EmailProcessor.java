package fi.jonix.huutonet.email;

import javax.mail.Message;

import fi.jonix.huutonet.domain.model.Seller;
import fi.jonix.huutonet.domain.model.SystemEmail;
import fi.jonix.huutonet.exception.CriticalSectionInfo;

/**
 * Base class for processing emails.
 * 
 * @author john
 *
 */
public interface EmailProcessor {

	/**
	 * This one is used for special mail types. Known mail processors of this type are:
	 * MultiProcessorEmailRobot, ForwardProcessor
	 */
	public boolean processSpecial(Message message, Seller seller, int mailboxType, SystemEmail systemEmail,
			CriticalSectionInfo processingInfo) throws Exception;
	
	/**
	 * This one is for mails for seller. Known mail processors of this type are:
	 * HuutonetProcessor, MikkoProcessor, GoogleOrderProcessor, OperatorProcessor, EmailFromCustomerProcessor
	 */
	public boolean processSellerEmail(Message message, Seller seller,
			CriticalSectionInfo processingInfo) throws Exception;
	
	/**
	 * This one is for mails for provider. Known mail processors of this type are:
	 * DxProcessor, FocalPriceProcessor, PaypalProcessor
	 */
	public boolean processProviderEmail(Message message, SystemEmail systemEmail,
			CriticalSectionInfo processingInfo) throws Exception;

}
