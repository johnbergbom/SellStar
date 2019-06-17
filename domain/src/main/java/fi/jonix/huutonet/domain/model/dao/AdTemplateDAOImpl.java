package fi.jonix.huutonet.domain.model.dao;

import java.util.List;

import javax.persistence.Table;

import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Restrictions;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.jdbc.core.simple.SimpleJdbcTemplate;
import org.springframework.orm.hibernate3.SessionFactoryUtils;

import fi.jonix.huutonet.domain.model.AdTemplate;
import fi.jonix.huutonet.domain.model.Category;
import fi.jonix.huutonet.domain.model.Product;
import fi.jonix.huutonet.domain.model.Seller;

public class AdTemplateDAOImpl extends GenericDAOImpl <AdTemplate, Long> implements AdTemplateDAO  {

	public Class<AdTemplate> getEntityClass() {
		return AdTemplate.class;
	}

	public List<AdTemplate> findByProviderCategory(String providerCategory) {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.createAlias("product", "p");
		criteria.add(Restrictions.eq("p.providerCategory", providerCategory));
		return this.getHibernateTemplate().findByCriteria(criteria);
	}

	public List<AdTemplate> findBySellStarCategory(Category sellStarCategory) {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.createAlias("product", "p");
		criteria.add(Restrictions.eq("p.category", sellStarCategory));
		return this.getHibernateTemplate().findByCriteria(criteria);
	}

	public List<AdTemplate> findByHeadline(String headline) {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.add(Restrictions.eq("headline", headline));
		return this.getHibernateTemplate().findByCriteria(criteria);
	}

	public List<AdTemplate> getAdTemplatesWithoutCategory() {
		return this.getHibernateTemplate().find("SELECT a FROM AdTemplate a WHERE a.product IN (SELECT p from Product p"
				+ " WHERE p.category IS NULL)");
	}

	public List<AdTemplate> getAdTemplatesWithAssignableCategory() {
		return this.getHibernateTemplate().find("SELECT a FROM AdTemplate a WHERE a.product IN (SELECT p from Product p"
				+ " WHERE p.category IS NULL AND p.categorySuggestion IS NOT NULL) ORDER BY a.id");
	}

	public int getNbrAdTemplates() {
		SimpleJdbcTemplate jdbcTemplate =
			new SimpleJdbcTemplate(SessionFactoryUtils.getDataSource(getSessionFactory()));
		Table table = AnnotationUtils.findAnnotation(AdTemplate.class, Table.class);
		return jdbcTemplate.queryForInt("SELECT count(*) FROM " + table.name());
	}
	
	public List<String> getAllTranslators() {
		return this.getHibernateTemplate().find("SELECT DISTINCT a.addedBy FROM AdTemplate a");
	}

	public List<AdTemplate> findByProduct(Product product) {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.add(Restrictions.eq("product", product));
		return this.getHibernateTemplate().findByCriteria(criteria);
	}

	public List<AdTemplate> findBySeller(Seller seller) {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.add(Restrictions.eq("seller", seller));
		
		/* Make sure that highly visible items are listed LAST!
		 * Reason: we want to have open ads to which the highly visible items
		 * are pointing and if these are listed last, then there will always be
		 * open ads to which these can point. */
		criteria.addOrder(org.hibernate.criterion.Order.asc("visibilityType"));

		/* Make sure that items with a short closing time are listed first.
		 * Reason: they are closed so fast that we want to make sure that
		 * they are unlisted as short time as possible (because these
		 * products usually sell quite well). */
		criteria.addOrder(org.hibernate.criterion.Order.asc("openDays"));
		return this.getHibernateTemplate().findByCriteria(criteria);
	}

	/*public List<AdTemplate> findBySellerWithRemainder(Seller seller, int remainder) {
		Object[] cond = new Object[2];
		cond[0] = seller;
		cond[1] = remainder;
		return this.getHibernateTemplate().find("SELECT a FROM AdTemplate a WHERE a.seller = ?"
				+ " AND a.id % 2 = ?",cond);
	}*/

}
