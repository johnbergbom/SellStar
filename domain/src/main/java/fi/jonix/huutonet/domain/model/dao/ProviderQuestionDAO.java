package fi.jonix.huutonet.domain.model.dao;

import java.util.List;

import fi.jonix.huutonet.domain.model.OrderBundle;
import fi.jonix.huutonet.domain.model.ProviderQuestion;

public interface ProviderQuestionDAO extends GenericDAO<ProviderQuestion, Long> {

	public List<ProviderQuestion> getProviderQuestionForBundle(OrderBundle orderBundle);
	public List<ProviderQuestion> getOpenConversations();

}
