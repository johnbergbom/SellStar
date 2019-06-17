package fi.jonix.huutonet.domain.model.dao;

import java.util.List;
import java.util.Map;

import fi.jonix.huutonet.domain.model.Category;
import fi.jonix.huutonet.domain.model.Market;

public interface CategoryDAO extends GenericDAO<Category, Long> {

	//public List<Category> getAllCategoriesForMarket(Market market);
	public List<Category> getChildren(Category parent);
	public Category getChild(Category parent, String childName);
	public Category getChildByNameAndMarketSpecId(Category parent, String childName, int marketSpecId);
	public Category getChildByMarketSpecId(Category parent, int marketSpecId);
	public List<Category> getChildrenContainingNamePart(Category parent, String namePart);
	public List<Category> getCategoriesContainingNamePart(String namePart, boolean ignoreCase);
	public List<Category> getCategoriesContainingNamePart(String namePart, Market market, boolean ignoreCase);
	public Map<Long,Long> getSellStarCategoryCountForProviderCategory(String providerCategory);

}
