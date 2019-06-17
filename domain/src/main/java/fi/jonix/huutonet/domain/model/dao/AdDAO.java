package fi.jonix.huutonet.domain.model.dao;

import java.util.Date;
import java.util.List;

import javax.persistence.Table;

import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.jdbc.core.simple.SimpleJdbcTemplate;
import org.springframework.orm.hibernate3.SessionFactoryUtils;

import fi.jonix.huutonet.domain.model.Ad;
import fi.jonix.huutonet.domain.model.AdTemplate;
import fi.jonix.huutonet.domain.model.Category;
import fi.jonix.huutonet.domain.model.Market;
import fi.jonix.huutonet.domain.model.Product;
import fi.jonix.huutonet.domain.model.Seller;

public interface AdDAO extends GenericDAO<Ad, Long> {
	
	public List<Ad> getLatestAdsByMarketCategoryAndMarket(Category category, Market market, Seller seller);
	public List<Ad> getOpenAdsByProduct(Product product);
	public Ad getLatestAdByProduct(Product product);
	public Ad getLatestAdByProductAndSellerAndMarket(Product product, Market market, Seller seller);
	public Ad getByMarketSalesId(String marketSalesId, Market market, Seller seller);
	public Ad getByMarketSalesIdWithoutSeller(String marketSalesId, Market market);
	public Ad getByDateToMarketWithoutSeller(Date dateToMarket, Market market);
	//public boolean isAdTemplateCurrentlyOnSale(AdTemplate adTemplate, Category marketCategory);
	public List<Ad> getAdTemplatesCurrentlyOnSale(AdTemplate adTemplate, Category marketCategory);
	//public List<Ad> getAdTemplatesCurrentlyOnSale(AdTemplate adTemplate);
	public int getNbrOpenAds();
	public int getNbrClosedAds();
	public List<Ad> getUnconfirmedAds();
	public List<Ad> getOpenOldAds();
	public List<Ad> getAdsByMarketCategory(Category category);
	public List<Ad> findByAdTemplate(AdTemplate adTemplate);
	public List<Ad> findAdsForDisplayWindow(Seller seller);
	public List<Ad> findUnverifiedAds();

}
