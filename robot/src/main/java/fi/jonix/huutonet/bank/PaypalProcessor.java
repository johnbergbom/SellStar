package fi.jonix.huutonet.bank;

import javax.mail.Message;

import org.springframework.stereotype.Component;

import fi.jonix.huutonet.domain.model.SystemEmail;
import fi.jonix.huutonet.exception.CriticalSectionInfo;
import fi.jonix.huutonet.order.ProviderEmailFilter;

/**
 * This class takes care of emails coming from paypal. Actually there are currently no recognized emails
 * from PayPal that aren't taken care of elsewhere.
 * 
 * @author john
 *
 */
@Component(value = "paypalProcessor")
public class PaypalProcessor extends ProviderEmailFilter {

	@Override
	public boolean processProviderEmail(Message message, SystemEmail systemEmail,
			CriticalSectionInfo processingInfo) throws Exception {
		return false;
	}
}
