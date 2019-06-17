package fi.jonix.huutonet.domain.model.dao;

import java.util.List;

import fi.jonix.huutonet.domain.model.Email;
import fi.jonix.huutonet.domain.model.OrderBundle;

public interface EmailDAO extends GenericDAO<Email, Long> {

	public List<Email> getEmailsPendingForSending();
	public List<Email> getEmailsPendingForSendingWithLowPriority();
	public List<Email> getEmailsForBundle(OrderBundle bundle);

}
