package fi.jonix.huutonet.domain.model.dao;

import java.util.Map;

import fi.jonix.huutonet.domain.model.Message;
import fi.jonix.huutonet.domain.model.Seller;

public interface MessageDAO extends GenericDAO<Message, Long> {

	//public String getText(String key, Seller seller, String language, TextReplacement... replacements);
	public String getText(String key, Seller seller, String language, String... replacements);
	public String getText(String key, Seller seller, String language, Map<String, String> replacements);
	
}
