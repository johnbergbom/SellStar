package fi.jonix.huutonet.domain.model.dao;

import java.util.List;

import fi.jonix.huutonet.domain.model.AdTemplate;
import fi.jonix.huutonet.domain.model.Category;
import fi.jonix.huutonet.domain.model.Product;
import fi.jonix.huutonet.domain.model.Seller;

public interface AdTemplateDAO extends GenericDAO<AdTemplate, Long> {

	public List<AdTemplate> findByProviderCategory(String originalCategory);
	public List<AdTemplate> findBySellStarCategory(Category sellStarCategory);
	public List<AdTemplate> findByHeadline(String headline);
	public List<AdTemplate> getAdTemplatesWithoutCategory();
	public List<AdTemplate> getAdTemplatesWithAssignableCategory();
	public int getNbrAdTemplates();
	public List<String> getAllTranslators();
	public List<AdTemplate> findByProduct(Product product);
	public List<AdTemplate> findBySeller(Seller seller);
	//public List<AdTemplate> findBySellerWithRemainder(Seller seller, int remainder);

}
