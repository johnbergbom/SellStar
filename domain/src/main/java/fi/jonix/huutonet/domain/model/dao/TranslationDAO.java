package fi.jonix.huutonet.domain.model.dao;

import fi.jonix.huutonet.domain.model.Translation;

public interface TranslationDAO extends GenericDAO<Translation, Long> {
	
	public Translation getTranslation(String text, String language);
	public Translation getCloseTranslation(String text, String language);
	
}
