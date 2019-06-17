package fi.jonix.huutonet.domain.model.dao;

import java.util.List;

import fi.jonix.huutonet.domain.model.Category;
import fi.jonix.huutonet.domain.model.CategoryMapping;
import fi.jonix.huutonet.domain.model.Market;

public interface CategoryMappingDAO extends GenericDAO<CategoryMapping, Long> {

	public CategoryMapping getCategoryMapping(Category category1, Category category2);
	public List<CategoryMapping> getCategoryMappings(Category category);
	public List<Category> getCategoryMappingsForMarket(Category category, Market market);

}
