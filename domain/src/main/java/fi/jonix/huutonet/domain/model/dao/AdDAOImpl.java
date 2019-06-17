package fi.jonix.huutonet.domain.model.dao;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import javax.persistence.Table;

import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Disjunction;
import org.hibernate.criterion.Restrictions;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.jdbc.core.simple.SimpleJdbcTemplate;
import org.springframework.orm.hibernate3.HibernateTemplate;
import org.springframework.orm.hibernate3.SessionFactoryUtils;

import fi.jonix.huutonet.domain.model.Ad;
import fi.jonix.huutonet.domain.model.AdTemplate;
import fi.jonix.huutonet.domain.model.Category;
import fi.jonix.huutonet.domain.model.Market;
import fi.jonix.huutonet.domain.model.Product;
import fi.jonix.huutonet.domain.model.Seller;

public class AdDAOImpl extends GenericDAOImpl<Ad, Long> implements AdDAO {

	public Class<Ad> getEntityClass() {
		return Ad.class;
	}

	/* This method returns the last ad that was listed for the given
	 * adTemplate (get all open ones + some closed ones (if not enough
	 * opened ones were found)). */
	public List<Ad> getLatestAdsByMarketCategoryAndMarket(Category category, Market market, Seller seller) {
		/* First get the open ads. */
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.add(Restrictions.eq("marketCategory", category));
		criteria.add(Restrictions.eq("market", market));
		criteria.createAlias("adTemplate", "template");
		criteria.add(Restrictions.eq("template.seller", seller));
		criteria.add(Restrictions.isNull("dateFromMarket"));

		// Then order by dateToMarket in descending order to make sure that for
		// open orders (having dateFromMarket == null) we'll first get the most
		// recently listed ones.
		criteria.addOrder(org.hibernate.criterion.Order.desc("dateToMarket"));
		List adList = getHibernateTemplate().findByCriteria(criteria);
		if (adList.size() < 30) {
			/* Then get the first 30 closed ads. */
			criteria = DetachedCriteria.forClass(this.getEntityClass());
			criteria.add(Restrictions.eq("marketCategory", category));
			criteria.add(Restrictions.eq("market", market));
			criteria.createAlias("adTemplate", "template");
			criteria.add(Restrictions.eq("template.seller", seller));
			criteria.add(Restrictions.isNotNull("dateFromMarket"));
	
			// Make sure that we first get the most recently listed ones.
			criteria.addOrder(org.hibernate.criterion.Order.desc("dateToMarket"));
			
			HibernateTemplate hibernateTemplate = getHibernateTemplate();
			//int oldMaxResults = hibernateTemplate.getMaxResults();
			hibernateTemplate.setMaxResults(30-adList.size());
			List closedList = hibernateTemplate.findByCriteria(criteria);
			
			//Note: set maxResult to the default (=0) and NOT to oldMaxResults,
			//because otherwise this can create problems when two requests come
			//to google-order in rapid succession.
			//hibernateTemplate.setMaxResults(oldMaxResults);
			hibernateTemplate.setMaxResults(0);
			
			adList.addAll(closedList);
		}
		return adList;
	}
	
	/* This method returns all open ads that are listed for the given product. */
	public List<Ad> getOpenAdsByProduct(Product product) {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.createAlias("adTemplate", "template");
		criteria.add(Restrictions.eq("template.product", product));
		criteria.add(Restrictions.eq("confirmed", new Boolean(true)));
		criteria.add(Restrictions.isNull("dateFromMarket"));
		return getHibernateTemplate().findByCriteria(criteria);
	}

	/* This method returns the last ad that was listed for the given
	 * adTemplate (open or closed). */
	public Ad getLatestAdByProduct(Product product) {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.createAlias("adTemplate", "template");
		criteria.add(Restrictions.eq("template.product", product));
		
		// Ordering dateFromMarket in descending order makes sure that open ads
		// come first (if there are such ones).
		criteria.addOrder(org.hibernate.criterion.Order.desc("dateFromMarket"));
		
		HibernateTemplate hibernateTemplate = getHibernateTemplate();
		//int oldMaxResults = hibernateTemplate.getMaxResults();
		hibernateTemplate.setMaxResults(1);
		List<Ad> returnList = hibernateTemplate.findByCriteria(criteria);
		
		//Note: set maxResult to the default (=0) and NOT to oldMaxResults,
		//because otherwise this can create problems when two requests come
		//to google-order in rapid succession.
		//hibernateTemplate.setMaxResults(oldMaxResults);
		hibernateTemplate.setMaxResults(0);
		
		if (returnList != null && returnList.size() > 0) {
			return returnList.get(0);
		}
		return null;
	}

	/* This method returns the last ad that was listed for the given
	 * adTemplate (open or closed). */
	public Ad getLatestAdByProductAndSellerAndMarket(Product product, Market market, Seller seller) {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.add(Restrictions.eq("market", market));
		criteria.createAlias("adTemplate", "template");
		criteria.add(Restrictions.eq("template.seller", seller));
		criteria.add(Restrictions.eq("template.product", product));
		
		// Ordering dateFromMarket in descending order makes sure that open ads
		// come first (if there are such ones).
		criteria.addOrder(org.hibernate.criterion.Order.desc("dateFromMarket"));
		
		HibernateTemplate hibernateTemplate = getHibernateTemplate();
		//int oldMaxResults = hibernateTemplate.getMaxResults();
		hibernateTemplate.setMaxResults(1);
		List<Ad> returnList = hibernateTemplate.findByCriteria(criteria);

		//Note: set maxResult to the default (=0) and NOT to oldMaxResults,
		//because otherwise this can create problems when two requests come
		//to google-order in rapid succession.
		//hibernateTemplate.setMaxResults(oldMaxResults);
		hibernateTemplate.setMaxResults(0);

		if (returnList != null && returnList.size() > 0) {
			return returnList.get(0);
		}
		return null;
	}

	public Ad getByMarketSalesId(String marketSalesId, Market market, Seller seller) {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.add(Restrictions.eq("marketSalesId", marketSalesId));
		criteria.add(Restrictions.eq("market", market));
		criteria.createAlias("adTemplate", "template");
		criteria.add(Restrictions.eq("template.seller", seller));
		List<Ad> list = this.getHibernateTemplate().findByCriteria(criteria);
		if (list != null && list.size() > 0) {
			/* There will never be more than one ad here, because market and marketSalesId
			 * are together unique in the database. */
			return list.get(0);
		} 
		return null;
	}

	public Ad getByMarketSalesIdWithoutSeller(String marketSalesId, Market market) {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.add(Restrictions.eq("marketSalesId", marketSalesId));
		criteria.add(Restrictions.eq("market", market));
		List<Ad> list = this.getHibernateTemplate().findByCriteria(criteria);
		if (list != null && list.size() > 0) {
			/* There will never be more than one ad here, because market and marketSalesId
			 * are together unique in the database. */
			return list.get(0);
		} 
		return null;
	}

	public Ad getByDateToMarketWithoutSeller(Date dateToMarket, Market market) {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.add(Restrictions.eq("dateToMarket", dateToMarket));
		criteria.add(Restrictions.eq("market", market));
		criteria.add(Restrictions.isNull("originalAd"));
		List<Ad> list = this.getHibernateTemplate().findByCriteria(criteria);
		if (list != null && list.size() > 0) {
			if (list.size() > 1) {
				/* There can be several ads having the same dateToMarket if there is already
				 * one cloned ad (plus the original ad) and then somebody buys another product.
				 * However we have the limitation that "originalAd == null" in the search here,
				 * so we should NEVER get here. */
				throw new RuntimeException("Cannot handle: more than one none-cloned ad put on the market at the same time.");
			}
			return list.get(0);
		} 
		return null;
	}

	/**
	 * 
	 * @param adTemplate
	 * @return True if any ad based on the given ad template is currently on sale.
	 * in the market, otherwise false.
	 */
	/*public boolean isAdTemplateCurrentlyOnSale(AdTemplate adTemplate, Category marketCategory) {
		if (getAdTemplatesForDisplayWindowCurrentlyOnSale(adTemplate, marketCategory).size() > 0) {
			return true;
		} 
		return false;
	}*/

	public List<Ad> getAdTemplatesCurrentlyOnSale(AdTemplate adTemplate, Category marketCategory) {
		/* Fetch all ads that are generated from the ad template in question
		 * and has an empty dateFromMarket. */
		DetachedCriteria criteria = DetachedCriteria.forClass(Ad.class);
		criteria.add(Restrictions.eq("adTemplate", adTemplate));
		criteria.add(Restrictions.eq("market", marketCategory.getMarket()));
		criteria.add(Restrictions.eq("marketCategory", marketCategory));
		criteria.add(Restrictions.eq("confirmed", new Boolean(true)));
		criteria.add(Restrictions.isNull("dateFromMarket"));
		return this.getHibernateTemplate().findByCriteria(criteria);
	}

	/*public List<Ad> getAdTemplatesCurrentlyOnSale(AdTemplate adTemplate) {
		/* Fetch all ads that are generated from the ad template in question
		 * and has an empty dateFromMarket. /
		DetachedCriteria criteria = DetachedCriteria.forClass(Ad.class);
		criteria.add(Restrictions.eq("adTemplate", adTemplate));
		criteria.add(Restrictions.eq("confirmed", new Boolean(true)));
		criteria.add(Restrictions.isNull("dateFromMarket"));
		return this.getHibernateTemplate().findByCriteria(criteria);
	}*/

	public int getNbrOpenAds() {
		SimpleJdbcTemplate jdbcTemplate =
			new SimpleJdbcTemplate(SessionFactoryUtils.getDataSource(getSessionFactory()));
		Table table = AnnotationUtils.findAnnotation(Ad.class, Table.class);
		return jdbcTemplate.queryForInt("SELECT count(*) FROM " + table.name() + " WHERE date_from_market is null");
	}

	public int getNbrClosedAds() {
		SimpleJdbcTemplate jdbcTemplate =
			new SimpleJdbcTemplate(SessionFactoryUtils.getDataSource(getSessionFactory()));
		Table table = AnnotationUtils.findAnnotation(Ad.class, Table.class);
		return jdbcTemplate.queryForInt("SELECT count(*) FROM " + table.name() + " WHERE date_from_market is not null");
	}

	public List<Ad> getUnconfirmedAds() {
		DetachedCriteria criteria = DetachedCriteria.forClass(getEntityClass());
		criteria.add(Restrictions.eq("confirmed", new Boolean(false)));
		return this.getHibernateTemplate().findByCriteria(criteria);
	}

	/* Returns all open ads whose date_closing has gone by. */
	public List<Ad> getOpenOldAds() {
		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.DATE,-1);
		DetachedCriteria criteria = DetachedCriteria.forClass(getEntityClass());
		//criteria.add(Restrictions.le("dateToMarket", cal.getTime()));
		criteria.add(Restrictions.le("dateClosing", cal.getTime()));
		criteria.add(Restrictions.isNull("dateFromMarket"));
		return this.getHibernateTemplate().findByCriteria(criteria);
	}

	public List<Ad> getAdsByMarketCategory(Category category) {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.add(Restrictions.eq("marketCategory", category));
		return this.getHibernateTemplate().findByCriteria(criteria);
	}

	public List<Ad> findByAdTemplate(AdTemplate adTemplate) {
		DetachedCriteria criteria = DetachedCriteria.forClass(getEntityClass());
		criteria.add(Restrictions.eq("adTemplate", adTemplate));
		return this.getHibernateTemplate().findByCriteria(criteria);
	}

	@Override
	public List<Ad> findAdsForDisplayWindow(Seller seller) {
		DetachedCriteria criteria = DetachedCriteria.forClass(getEntityClass());
		criteria.add(Restrictions.eq("confirmed", new Boolean(true)));
		criteria.createAlias("adTemplate", "template");
		criteria.add(Restrictions.eq("template.seller", seller));
		criteria.add(Restrictions.gt("visibilityType", new Integer(0)));
		criteria.add(Restrictions.eq("transferedToVerification", new Boolean(false)));
		Disjunction orCriteria = Restrictions.disjunction();
		orCriteria.add(Restrictions.eq("advertisementCostInEuroInclVat", BigDecimal.ZERO));
		orCriteria.add(Restrictions.eq("advertisementCostInEuroExclVat", BigDecimal.ZERO));
		criteria.add(orCriteria);
		return this.getHibernateTemplate().findByCriteria(criteria);
	}

	@Override
	public List<Ad> findUnverifiedAds() {
		DetachedCriteria criteria = DetachedCriteria.forClass(getEntityClass());
		criteria.add(Restrictions.eq("confirmed", new Boolean(true)));
		criteria.add(Restrictions.gt("visibilityType", new Integer(0)));
		criteria.add(Restrictions.eq("transferedToVerification", new Boolean(false)));
		Disjunction orCriteria = Restrictions.disjunction();
		orCriteria.add(Restrictions.gt("advertisementCostInEuroInclVat", BigDecimal.ZERO));
		orCriteria.add(Restrictions.gt("advertisementCostInEuroExclVat", BigDecimal.ZERO));
		criteria.add(orCriteria);
		criteria.addOrder(org.hibernate.criterion.Order.asc("id"));
		return this.getHibernateTemplate().findByCriteria(criteria);
	}

}
