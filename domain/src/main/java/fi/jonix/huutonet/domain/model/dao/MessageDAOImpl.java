package fi.jonix.huutonet.domain.model.dao;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Restrictions;

import fi.jonix.huutonet.domain.model.Message;
import fi.jonix.huutonet.domain.model.Seller;

public class MessageDAOImpl extends GenericDAOImpl<Message, Long> implements MessageDAO {

	@Override
	public Class<Message> getEntityClass() {
		return Message.class;
	}
	
	/*public class TextReplacement {
		String key;
		String replacement;
		public TextReplacement(String key, String replacement) {
			this.key = key;
			this.replacement = replacement;
		}
		public String getKey() {
			return key;
		}
		public String getReplacement() {
			return replacement;
		}
	}
	
	public String getText(String key, Seller seller, String language, TextReplacement... replacements) {
		Map<String,String> map = new HashMap<String,String>();
		for(TextReplacement replacement : replacements) {
			map.put(replacement.getKey(), replacement.getReplacement());
		}
		return getText(key,seller,language,map);
	}*/
	
	public String getText(String key, Seller seller, String language, String... replacements) {
		Map<String,String> map = new HashMap<String,String>();
		if (replacements.length % 2 != 0) {
			throw new RuntimeException("Uneven number of replacements.");
		}
		for (int i = 0; i <= replacements.length; i++) {
			if (i > 0 && i % 2 == 0) {
				map.put(replacements[i-2],replacements[i-1]);
			}
		}
		return getText(key,seller,language,map);
	}
	
	public String getText(String key, Seller seller, String language, Map<String, String> replacements) {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.add(Restrictions.eq("key", key));
		criteria.add(Restrictions.eq("seller", seller));
		//criteria.add(Restrictions.eq("language", locale.toString()));
		criteria.add(Restrictions.eq("language", language));
		List<Message> list = this.getHibernateTemplate().findByCriteria(criteria);
		if (list.size() == 1) {
			return getFormattedString(list.get(0).getText(),replacements);
		} else {
			/* If we get here, then we know that no list was found, because we can get
			 * at the most one it since "key", "seller_id" and "language" are unique.
			 * If no seller specific row was found, then search for the default text. */
			criteria = DetachedCriteria.forClass(this.getEntityClass());
			criteria.add(Restrictions.eq("key", key));
			criteria.add(Restrictions.isNull("seller"));
			//criteria.add(Restrictions.eq("language", locale.toString()));
			criteria.add(Restrictions.eq("language", language));
			list = this.getHibernateTemplate().findByCriteria(criteria);
			if (list.size() == 1) {
				return getFormattedString(list.get(0).getText(),replacements);
			} else {
				throw new RuntimeException("No message found or too many messages found.");
			}
		}
	}
	
	
	/*
	 * Replace all the keys ${REPLACEMENT} in text with the replacements in map 
	 */
	private String getFormattedString(String text, Map<String, String> replacements) {
		String formattedString = text;
		Map<String,String> replaced = new HashMap<String,String>();
		while (true) {
			Pattern pattern = Pattern.compile("\\$\\{[\\w]+\\}");
			Matcher m = pattern.matcher(formattedString);
			if (m.find()) {
				String key = m.group().substring(2, m.group().length() - 1);
				if (replacements.get(key) == null) {
					throw new RuntimeException("No replacement for the key ${" + key + "}");
				}
				formattedString = formattedString.substring(0, m.start()) + replacements.get(key)
						+ formattedString.substring(m.end());
				replaced.put(key,"");
			} else {
				break;
			}
		}
		/* Make sure that there were no key that didn't get replaced. */
		if (replaced.size() != replacements.size()) {
			throw new RuntimeException("Some of the keys to replace weren't found in the text.");
		}
		return formattedString;
	}

}
