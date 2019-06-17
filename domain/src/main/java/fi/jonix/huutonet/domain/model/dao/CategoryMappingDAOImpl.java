package fi.jonix.huutonet.domain.model.dao;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Restrictions;

import fi.jonix.huutonet.domain.model.Category;
import fi.jonix.huutonet.domain.model.CategoryMapping;
import fi.jonix.huutonet.domain.model.Market;

public class CategoryMappingDAOImpl extends GenericDAOImpl <CategoryMapping, Long> implements CategoryMappingDAO {

	public Class<CategoryMapping> getEntityClass() {
		return CategoryMapping.class;
	}

	public CategoryMapping getCategoryMapping(Category category1, Category category2) {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.add(Restrictions.eq("category1",category1));
		criteria.add(Restrictions.eq("category2",category2));
		List<CategoryMapping> list = this.getHibernateTemplate().findByCriteria(criteria);
		if (list == null || list.isEmpty()) {
			return null;
		} else {
			/* There will never be more than one category mapping here, because category1
			 * and category2 are together unique in the database. */
			return list.get(0);
		}
	}
	
	/**
	 * @return all mappings for the given category
	 */
	public List<CategoryMapping> getCategoryMappings(Category category) {
		//TODO: this can be combined into one database query using "category1 = category OR category2 = category".
		List<CategoryMapping> returnList = new ArrayList<CategoryMapping>();
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.add(Restrictions.eq("category1",category));
		returnList.addAll(this.getHibernateTemplate().findByCriteria(criteria));
		criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.add(Restrictions.eq("category2",category));
		returnList.addAll(this.getHibernateTemplate().findByCriteria(criteria));
		return returnList;
	}
	
	/**
	 * Returns a list of categories for the given market to which the given category is mapped.
	 * @param category
	 * @param market
	 * @return
	 */
	public List<Category> getCategoryMappingsForMarket(Category category, Market market) {
		List<Category> returnList = new ArrayList<Category>();
		for (CategoryMapping mapping : getCategoryMappings(category)) {
			if (mapping.getCategory1().getMarket().getId().equals(market.getId())) {
				returnList.add(mapping.getCategory1());
			}
			if (mapping.getCategory2().getMarket().getId().equals(market.getId())) {
				returnList.add(mapping.getCategory2());
			}
		}
		return returnList;
	}
	
}
