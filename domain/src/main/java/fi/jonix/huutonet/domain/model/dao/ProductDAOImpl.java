package fi.jonix.huutonet.domain.model.dao;

import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.persistence.Table;

import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Restrictions;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.jdbc.core.simple.SimpleJdbcTemplate;
import org.springframework.orm.hibernate3.SessionFactoryUtils;

import fi.jonix.huutonet.domain.model.AdTemplate;
import fi.jonix.huutonet.domain.model.Category;
import fi.jonix.huutonet.domain.model.Order;
import fi.jonix.huutonet.domain.model.OrderBundle;
import fi.jonix.huutonet.domain.model.Product;
import fi.jonix.huutonet.domain.model.Provider;
import fi.jonix.huutonet.domain.model.StateChange;

public class ProductDAOImpl extends GenericDAOImpl <Product, Long> implements ProductDAO  {

	public Class<Product> getEntityClass() {
		return Product.class;
	}
	
	@SuppressWarnings("unchecked")
	public Product getByProviderProdId(String providerProdId, Provider provider) {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.add(Restrictions.eq("providerProdId", providerProdId));
		criteria.add(Restrictions.eq("provider", provider));
		List<Product> list = this.getHibernateTemplate().findByCriteria(criteria);
		if (list != null && list.size() > 0) {
			/* There will never be more than one ad here, because providerProdId and provider
			 * are together unique in the database. */
			return list.get(0);
		} 
		return null;
	}
	
	/**
	 * This one can return several products since several providers can have a product
	 * having the same product id.
	 * 
	 * @param providerProdId
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public List<Product> getByProductId(String providerProdId) {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.add(Restrictions.eq("providerProdId", providerProdId));
		return this.getHibernateTemplate().findByCriteria(criteria);
	}
	
	/*@SuppressWarnings("unchecked")
	public List<Product> getProductsHavingCategorySuggestion() {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.add(Restrictions.isNotNull("categorySuggestion"));
		return this.getHibernateTemplate().findByCriteria(criteria);
	}*/
	
	@SuppressWarnings("unchecked")
	public List<Product> getProductsWithoutAdTemplate(Provider provider) {
		return this.getHibernateTemplate().find("SELECT p FROM Product p WHERE p NOT IN (SELECT at.product from AdTemplate at) and p.provider = ?",provider);
	}
	
	@SuppressWarnings("unchecked")
	public List<Product> getProductsWithEmptyTechnicalSpecs(Provider provider) {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.add(Restrictions.eq("provider", provider));
		criteria.add(Restrictions.eq("technicalSpecs", ""));
		return this.getHibernateTemplate().findByCriteria(criteria);
	}
	
	@SuppressWarnings("unchecked")
	public List<Product> getTranslatedProductsWithoutCategory(Provider provider) {
		return this.getHibernateTemplate().find("SELECT p FROM Product p WHERE p IN (SELECT at.product from AdTemplate at)"
				+ " AND p.category IS NULL");
	}
	
	@SuppressWarnings("unchecked")
	public List<Product> getProductsWithSellStarCategory(Category category) {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.add(Restrictions.eq("category", category));
		return this.getHibernateTemplate().findByCriteria(criteria);
	}

	//this method actually does the same thing as getProductsWithSellStarCategory
	@SuppressWarnings("unchecked")
	public List<Product> getProductsInCategory(Category category) {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.add(Restrictions.eq("category", category));
		return this.getHibernateTemplate().findByCriteria(criteria);
	}

	@SuppressWarnings("unchecked")
	public List<Product> getProducts(Provider provider) {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.add(Restrictions.eq("provider", provider));
		return this.getHibernateTemplate().findByCriteria(criteria);
	}

	@SuppressWarnings("unchecked")
	public List<Product> getProductsForUpdateProviderPrice(Provider provider) {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.add(Restrictions.eq("provider", provider));
		criteria.add(Restrictions.ne("productState", Product.STATE_NO_LONGER_FOR_SALE));
		criteria.add(Restrictions.eq("compensation", Boolean.FALSE));
		criteria.add(Restrictions.isNotNull("category"));
		
		/* Make sure that the oldest ones come first. */
		criteria.addOrder(org.hibernate.criterion.Order.asc("statusFetchDate"));
		
		return this.getHibernateTemplate().findByCriteria(criteria);
	}

	@Override
	public List<Product> getProductsWithoutImages(Provider provider) {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.add(Restrictions.isEmpty("images"));
		criteria.add(Restrictions.eq("provider", provider));
		return this.getHibernateTemplate().findByCriteria(criteria);
	}

	public List<Product> getProductsWithProviderCategory(String category, Provider provider) {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.add(Restrictions.eq("providerCategory", category));
		criteria.add(Restrictions.eq("provider", provider));
		return this.getHibernateTemplate().findByCriteria(criteria);
	}

	public int getNbrProductsWithProviderCategory(String category, Provider provider) {
		SimpleJdbcTemplate jdbcTemplate =
			new SimpleJdbcTemplate(SessionFactoryUtils.getDataSource(getSessionFactory()));
		Table table = AnnotationUtils.findAnnotation(Product.class, Table.class);
		return jdbcTemplate.queryForInt("SELECT count(*) FROM " + table.name() + " WHERE provider_category = ? AND provider_id = ?",category,provider.getId());
	}

	public int getNbrProductsWithSellStarCategory(long categoryId) {
		SimpleJdbcTemplate jdbcTemplate =
			new SimpleJdbcTemplate(SessionFactoryUtils.getDataSource(getSessionFactory()));
		Table table = AnnotationUtils.findAnnotation(Product.class, Table.class);
		return jdbcTemplate.queryForInt("SELECT count(*) FROM " + table.name() + " WHERE category_id = ?",categoryId);
	}

	/**
	 * This method returns a list of provider product id's for the products that have sold the most.
	 */
	public List<Map<String,Object>> getBestSellingProductsBasedOnAmount(Date startDate, Date endDate) {
		// select p.id, at.headline, count(p.id) from product p, adtemplate at, orders o, order_bundle ob,
		// state_changes sc where o.order_bundle_id = ob.id and sc.order_id = o.id and sc.state = 140 and
		// sc.update_date >= '2011-07-04 00:00:00' and sc.update_date < '2011-07-05 00:00:00' and
		// p.provider_prod_id = o.provider_prod_id and p.provider_id = ob.provider_id and at.product_id = p.id
		// group by p.id, at.headline order by count desc;
		SimpleJdbcTemplate jdbcTemplate =
			new SimpleJdbcTemplate(SessionFactoryUtils.getDataSource(getSessionFactory()));
		Table orderTable = AnnotationUtils.findAnnotation(Order.class, Table.class);
		Table orderBundleTable = AnnotationUtils.findAnnotation(OrderBundle.class, Table.class);
		Table stateChangesTable = AnnotationUtils.findAnnotation(StateChange.class, Table.class);
		Table productTable = AnnotationUtils.findAnnotation(Product.class, Table.class);
		Table adTemplateTable = AnnotationUtils.findAnnotation(AdTemplate.class, Table.class);
		return jdbcTemplate.queryForList("SELECT p.id AS product_id, at.headline, count(p.id) FROM "
				+ productTable.name() + " p, " + adTemplateTable.name() + " at, " + orderTable.name() + " o, "
				+ orderBundleTable.name() + " ob, " + stateChangesTable.name() + " sc WHERE"
				+ " o.order_bundle_id = ob.id AND sc.order_id = o.id AND sc.state = ? AND"
				+ " sc.update_date >= ? AND sc.update_date < ? AND p.id = o.product_id"
				+ " AND p.provider_id = ob.provider_id AND at.product_id = p.id GROUP BY p.id, at.headline ORDER BY count DESC",
				Order.STATE_CUSTOMER_PAID_INFORMED,startDate,endDate);
	}
	
	/**
	 * This method returns a list of provider product id's for the products that have sold the most.
	 */
	public List<Map<String,Object>> getBestSellingProductsBasedOnRevenueInEuro(Date startDate, Date endDate) {
		// select p.id, at.headline, sum(o.fin_sp_euro) from product p, adtemplate at, orders o, order_bundle ob,
		// state_changes sc where o.order_bundle_id = ob.id and sc.order_id = o.id and sc.state = 140 and
		// sc.update_date >= '2011-07-04 00:00:00' and sc.update_date < '2011-07-05 00:00:00' and
		// p.provider_prod_id = o.provider_prod_id and p.provider_id = ob.provider_id and at.product_id = p.id
		// group by p.id, at.headline order by sum desc;
		SimpleJdbcTemplate jdbcTemplate =
			new SimpleJdbcTemplate(SessionFactoryUtils.getDataSource(getSessionFactory()));
		Table orderTable = AnnotationUtils.findAnnotation(Order.class, Table.class);
		Table orderBundleTable = AnnotationUtils.findAnnotation(OrderBundle.class, Table.class);
		Table stateChangesTable = AnnotationUtils.findAnnotation(StateChange.class, Table.class);
		Table productTable = AnnotationUtils.findAnnotation(Product.class, Table.class);
		Table adTemplateTable = AnnotationUtils.findAnnotation(AdTemplate.class, Table.class);
		return jdbcTemplate.queryForList("SELECT p.id AS product_id, at.headline, sum(o.fin_sp_euro_exc_vat) FROM "
				+ productTable.name() + " p, " + adTemplateTable.name() + " at, " + orderTable.name() + " o, "
				+ orderBundleTable.name() + " ob, " + stateChangesTable.name() + " sc WHERE"
				+ " o.order_bundle_id = ob.id AND sc.order_id = o.id AND sc.state = ? AND"
				+ " sc.update_date >= ? AND sc.update_date < ? AND p.id = o.product_id"
				+ " AND p.provider_id = ob.provider_id AND at.product_id = p.id GROUP BY p.id, at.headline ORDER BY sum DESC",
				Order.STATE_CUSTOMER_PAID_INFORMED,startDate,endDate);
	}
	
	public int getNbrProductsLikeProviderCategory(Provider provider, String providerCategory) {
		SimpleJdbcTemplate jdbcTemplate =
			new SimpleJdbcTemplate(SessionFactoryUtils.getDataSource(getSessionFactory()));
		Table table = AnnotationUtils.findAnnotation(Product.class, Table.class);
		/*return jdbcTemplate.queryForInt("SELECT count(*) FROM " + table.name() + " WHERE provider_id = ?"
				+ " AND provider_category like '?%' AND product_state <> ?",provider.getId(),categoryNameStart,
				Product.STATE_NO_LONGER_FOR_SALE);*/
		String category = providerCategory.replaceAll("'","\\\\'");
		return jdbcTemplate.queryForInt("SELECT count(*) FROM " + table.name() + " WHERE provider_id = "
				+ provider.getId() + " AND provider_category like '" + category + "'"
				+ " AND product_state <> " + Product.STATE_NO_LONGER_FOR_SALE);
		/*DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.add(Restrictions.eq("provider", provider));
		criteria.add(Restrictions.like("providerCategory",providerCategory));
		criteria.add(Restrictions.ne("productState", Product.STATE_NO_LONGER_FOR_SALE));
		List<Product> prodList = this.getHibernateTemplate().findByCriteria(criteria);
		return prodList.size();*/
	}

}
