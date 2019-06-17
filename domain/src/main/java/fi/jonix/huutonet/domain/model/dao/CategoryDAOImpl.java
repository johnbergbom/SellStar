package fi.jonix.huutonet.domain.model.dao;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.persistence.Table;

import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Restrictions;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.jdbc.core.simple.SimpleJdbcTemplate;
import org.springframework.orm.hibernate3.SessionFactoryUtils;

import fi.jonix.huutonet.domain.model.Category;
import fi.jonix.huutonet.domain.model.Market;
import fi.jonix.huutonet.domain.model.Product;

public class CategoryDAOImpl extends GenericDAOImpl <Category, Long> implements CategoryDAO {

	public Class<Category> getEntityClass() {
		return Category.class;
	}

	/*public List<Category> getAllCategoriesForMarket(Market market) {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.add(Restrictions.eq("market",market));
		return this.getHibernateTemplate().findByCriteria(criteria);
	}*/
	
	public List<Category> getChildren(Category parent) {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		if (parent == null) {
			criteria.add(Restrictions.isNull("parentCategory"));
		} else {
			criteria.add(Restrictions.eq("parentCategory",parent));
		}
		criteria.addOrder(org.hibernate.criterion.Order.asc("name"));
		return this.getHibernateTemplate().findByCriteria(criteria);
	}
	
	public Category getChild(Category parent, String childName) {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		if (parent == null) {
			criteria.add(Restrictions.isNull("parentCategory"));
		} else {
			criteria.add(Restrictions.eq("parentCategory",parent));
		}
		criteria.add(Restrictions.eq("name",childName));
		List<Category> children = this.getHibernateTemplate().findByCriteria(criteria);
		if (children == null || children.isEmpty()) {
			return null;
		} else {
			/* There will never be more than one category here, because parent and name
			 * are together unique in the database. */
			return children.get(0);
		}
	}
	
	public Category getChildByNameAndMarketSpecId(Category parent, String childName, int marketSpecId) {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		if (parent == null) {
			criteria.add(Restrictions.isNull("parentCategory"));
		} else {
			criteria.add(Restrictions.eq("parentCategory",parent));
		}
		criteria.add(Restrictions.eq("name",childName));
		criteria.add(Restrictions.eq("marketSpecId",marketSpecId));
		List<Category> children = this.getHibernateTemplate().findByCriteria(criteria);
		if (children == null || children.isEmpty()) {
			return null;
		} else {
			/* There will never be more than one category here, because parent and name
			 * are together unique in the database. */
			return children.get(0);
		}
	}
	
	public Category getChildByMarketSpecId(Category parent, int marketSpecId) {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		if (parent == null) {
			criteria.add(Restrictions.isNull("parentCategory"));
		} else {
			criteria.add(Restrictions.eq("parentCategory",parent));
		}
		criteria.add(Restrictions.eq("marketSpecId",marketSpecId));
		List<Category> children = this.getHibernateTemplate().findByCriteria(criteria);
		if (children == null || children.isEmpty()) {
			return null;
		} else if (children.size() == 1) {
			return children.get(0);
		} else {
			/* In practice we should never get here, because normally there should never be
			 * two categories in the same directory having the same marketSpecId, but since
			 * the database doesn't restrict this from happening it's in theory possible
			 * to get here. If this ever happens, then some code change is probably needed
			 * so that this method actually returns a list of categories and then corresponding
			 * changes at the places where this method is called. */
			throw new RuntimeException("Code change needed, more than one category found.");
		}
	}
	
	/**
	 * @param headline - Example: "aaa" fetches all strings equal to "aaa",
	 * "%aaa" fetches all strings ending with "aaa", "aaa%" fetches all strings
	 * starting with "aaa" and "%aaa%" fetches all strings having "aaa" somewhere
	 * in it.
	 */
	public List<Category> getChildrenContainingNamePart(Category parent, String namePart) {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		if (parent == null) {
			criteria.add(Restrictions.isNull("parentCategory"));
		} else {
			criteria.add(Restrictions.eq("parentCategory",parent));
		}
		criteria.add(Restrictions.like("name",namePart));
		criteria.addOrder(org.hibernate.criterion.Order.asc("name"));
		return this.getHibernateTemplate().findByCriteria(criteria);
	}
	
	/**
	 * @param headline - Example: "aaa" fetches all strings equal to "aaa",
	 * "%aaa" fetches all strings ending with "aaa", "aaa%" fetches all strings
	 * starting with "aaa" and "%aaa%" fetches all strings having "aaa" somewhere
	 * in it.
	 */
	public List<Category> getCategoriesContainingNamePart(String namePart, boolean ignoreCase) {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		if (ignoreCase) {
			criteria.add(Restrictions.like("name",namePart).ignoreCase());
		} else {
			criteria.add(Restrictions.like("name",namePart));
		}
		criteria.addOrder(org.hibernate.criterion.Order.asc("name"));
		return this.getHibernateTemplate().findByCriteria(criteria);
	}
	
	/**
	 * @param headline - Example: "aaa" fetches all strings equal to "aaa",
	 * "%aaa" fetches all strings ending with "aaa", "aaa%" fetches all strings
	 * starting with "aaa" and "%aaa%" fetches all strings having "aaa" somewhere
	 * in it.
	 */
	public List<Category> getCategoriesContainingNamePart(String namePart, Market market, boolean ignoreCase) {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		if (ignoreCase) {
			criteria.add(Restrictions.like("name",namePart).ignoreCase());
		} else {
			criteria.add(Restrictions.like("name",namePart));
		}
		criteria.add(Restrictions.eq("market",market));
		criteria.addOrder(org.hibernate.criterion.Order.asc("name"));
		return this.getHibernateTemplate().findByCriteria(criteria);
	}
	
	/**
	 * 
	 * @param providerCategory
	 * @return A map<categoryId,count> of SellStar categories that are tied to a product having the specified provider category.
	 */
	public Map<Long,Long> getSellStarCategoryCountForProviderCategory(String providerCategory) {
		SimpleJdbcTemplate jdbcTemplate =
			new SimpleJdbcTemplate(SessionFactoryUtils.getDataSource(getSessionFactory()));
		Table table = AnnotationUtils.findAnnotation(Product.class, Table.class);
		List<Map<String,Object>> list = jdbcTemplate.queryForList("SELECT count(id) as count, category_id FROM " + table.name()
				+ " WHERE provider_category = ? AND category_id is not null GROUP BY category_id ORDER BY count DESC",providerCategory);
		Map<Long,Long> catCountMap = new HashMap<Long,Long>();
		for (Map<String,Object> m : list) {
			catCountMap.put((Long)m.get("category_id"), (Long)m.get("count"));
		}
		return catCountMap;
	}
}
