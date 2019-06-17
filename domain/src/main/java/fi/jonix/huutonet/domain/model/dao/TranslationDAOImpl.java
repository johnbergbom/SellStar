package fi.jonix.huutonet.domain.model.dao;

import java.util.List;

import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Restrictions;

import fi.jonix.huutonet.domain.model.Translation;

public class TranslationDAOImpl extends GenericDAOImpl<Translation, Long>
		implements TranslationDAO {
	
	public static String SPECIAL_SEPARATOR = "[\\d\\p{Upper}\\.,]";
	public static String TRANSLATION_PART_SEPARATORS = "^" + SPECIAL_SEPARATOR
			+ "{2,}\\b|\\b" + SPECIAL_SEPARATOR + "+\\b|" + SPECIAL_SEPARATOR
			+ "{2,}$|\\b\\d+:\\d+\\b|[\\(\\)]| x |: ";
	
	public Class<Translation> getEntityClass() {
		return Translation.class;
	}

	public Translation getTranslation(String text, String language) {
		DetachedCriteria criteria = DetachedCriteria.forClass(this
				.getEntityClass());
		criteria.add(Restrictions.eq("language", language));
		criteria.add(Restrictions.eq("originalText", text.replaceAll("'","\\\\'")));
		List<Translation> list = this.getHibernateTemplate().findByCriteria(criteria);
		if(list != null && !list.isEmpty()){
			return list.get(0);
		} else {
			return null;
		}
	}

	public Translation getCloseTranslation(String text, String language) {
		DetachedCriteria criteria = DetachedCriteria.forClass(this
				.getEntityClass());
		criteria.add(Restrictions.eq("language", language));
		criteria.add(Restrictions.ilike("originalText", text.replaceAll("'","\\\\'")));
		List<Translation> list = this.getHibernateTemplate().findByCriteria(criteria);
		if(list != null && !list.isEmpty()){
			return list.get(0);
		} else {
			return null;
		}
	}

}
