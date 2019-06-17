package fi.jonix.huutonet.domain.model.dao;

import java.util.Date;
import java.util.List;
import java.util.Map;

import fi.jonix.huutonet.domain.model.Category;
import fi.jonix.huutonet.domain.model.Product;
import fi.jonix.huutonet.domain.model.Provider;

public interface ProductDAO extends GenericDAO<Product, Long> {

	public Product getByProviderProdId(String providerProdId, Provider provider);
	public List<Product> getByProductId(String providerProdId);
	//public List<Product> getProductsHavingCategorySuggestion();
	public List<Product> getProductsWithoutAdTemplate(Provider proider);
	public List<Product> getProductsWithEmptyTechnicalSpecs(Provider provider);
	public List<Product> getTranslatedProductsWithoutCategory(Provider provider);
	public List<Product> getProductsWithSellStarCategory(Category category);
	public List<Product> getProductsInCategory(Category category);
	public List<Product> getProducts(Provider provider);
	public List<Product> getProductsForUpdateProviderPrice(Provider provider);
	public List<Product> getProductsWithoutImages(Provider provider);
	public List<Product> getProductsWithProviderCategory(String category, Provider provider);
	public int getNbrProductsWithProviderCategory(String category, Provider provider);
	public int getNbrProductsWithSellStarCategory(long categoryId);
	public List<Map<String,Object>> getBestSellingProductsBasedOnAmount(Date startDate, Date endDate);
	public List<Map<String,Object>> getBestSellingProductsBasedOnRevenueInEuro(Date startDate, Date endDate);
	public int getNbrProductsLikeProviderCategory(Provider provider, String providerCategory);

}
