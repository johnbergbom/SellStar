package fi.jonix.huutonet.market;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.thoughtworks.selenium.SeleniumException;

import fi.jonix.huutonet.domain.model.Ad;
import fi.jonix.huutonet.domain.model.AdTemplate;
import fi.jonix.huutonet.domain.model.Category;
import fi.jonix.huutonet.domain.model.Market;
import fi.jonix.huutonet.domain.model.Order;
import fi.jonix.huutonet.domain.model.OrderBundle;
import fi.jonix.huutonet.domain.model.Product;
import fi.jonix.huutonet.domain.model.SellerMarket;
import fi.jonix.huutonet.domain.model.dao.AdDAO;
import fi.jonix.huutonet.domain.model.dao.AdTemplateDAO;
import fi.jonix.huutonet.domain.model.dao.CategoryMappingDAO;
import fi.jonix.huutonet.domain.model.dao.OrderDAO;
import fi.jonix.huutonet.email.EmailSender;
import fi.jonix.huutonet.exception.CategoryNotFoundException;
import fi.jonix.huutonet.exception.CriticalSectionInfo;
import fi.jonix.huutonet.exception.ListingException;
import fi.jonix.huutonet.market.huuto.HuutonetLister2;
import fi.jonix.huutonet.order.OrderCancelationHandler;
import fi.jonix.huutonet.order.ProviderHandler;
import fi.jonix.huutonet.order.ProviderHandler.ProductInfo;
import fi.jonix.huutonet.tools.MathUtilities;

@Component(value = "itemLister")
public class ItemLister {

	public static final Logger logger = Logger.getLogger(ItemLister.class);

	@Autowired
	AdTemplateDAO adTemplateDAO;
	
	@Autowired
	AdCreator adCreator;
	
	@Autowired
	AdDAO adDAO;
	
	@Autowired
	private OrderDAO orderDAO;
	
	@Autowired
	ProviderHandler providerHandler;
	
	@Autowired
	private ListerStrategy strategy;
	
	@Autowired
	private CategoryMappingDAO categoryMappingDAO;

	@Autowired
	private OrderCancelationHandler orderCancelationHandler;

	private Category getMarketCategory(AdTemplate adTemplate, Market market, int marketCategoryNumber,
			Map<String,Integer> nonExistentCategoryMappings, Map<Long,Integer> adTemplatesWithoutCategory) throws Exception {
		Category sellStarCategory = adTemplate.getProduct().getCategory();
		if (sellStarCategory == null) {
			adTemplatesWithoutCategory.put(adTemplate.getId(),1);
			return null;
		}
		List<Category> marketCategoryList = categoryMappingDAO.getCategoryMappingsForMarket(sellStarCategory, market);
		if (marketCategoryList.isEmpty()) {
			/* If no mapping was found between the sellstar category and market category, then update the
			 * nonExistentCategoryMappings map and then higher level code is responsible for informing
			 * the operator about this problem. */
			String categoryMappingId = "product.category_id = " + sellStarCategory.getId()
				+ " isn't mapped to any category at " + market.getName();
			logger.debug(categoryMappingId);
			if (nonExistentCategoryMappings.get(categoryMappingId) != null) {
				Integer count = nonExistentCategoryMappings.get(categoryMappingId);
				nonExistentCategoryMappings.put(categoryMappingId,count.intValue()+1);
				return null;
			} else {
				nonExistentCategoryMappings.put(categoryMappingId,1);
				return null;
			}
		} else if (marketCategoryNumber > marketCategoryList.size()-1) {
			//logger.debug("skipping listing because marketCategoryNumber is too high (not a problem/error)");
			return null;
		}
		return marketCategoryList.get(marketCategoryNumber);
	}
	
	public void payForHighlightedAds(MarketRobot lister, List<Ad> highlightedAds, CriticalSectionInfo section) {
		section.setInCriticalSection(false);
		section.setTransactionMustGoThrough(false);
		for (Ad ad : highlightedAds) {
			adDAO.refresh(ad);
		}
		lister.payForHighlightedAds(highlightedAds,section);
	}
	
	/**
	 * This method creates a new transaction (defined in the spring application
	 * context) and within that transaction it tries to list the ad defined by
	 * the ad template. Any exception rolls back the transaction.
	 * @param marketCategoryNumber The given adTemplate might have a product that belongs
	 * to several different categories at the given market place. The marketCategoryNumber
	 * tells which one of those will be used for the listing.
	 * @param preFilledScoreMap 
	 * @return True if listing was done and false if it wasn't done.
	 * @throws ListingException in case listing failed because of some exceptional circumstance.
	 */
	public boolean listItem(AdTemplate adTemplate, boolean forceAd, int marketCategoryNumber, SellerMarket sellerMarket,
			Map<String,Integer> nonExistentCategories, Map<String,Integer> nonExistentCategoryMappings,
			Map<Long,Integer> adTemplatesWithoutCategory, MarketRobot lister, TreeMap<Long, List<Long>> preFilledScoreMap) throws Exception {
		adTemplateDAO.refresh(adTemplate);
		//logger.debug("Trying listItem for adTemplate " + adTemplate.getId());
		if (Boolean.TRUE.equals(adTemplate.getActive())
				&& !adTemplate.getProduct().getProductState().equals(Product.STATE_NO_LONGER_FOR_SALE)) {
			
			if (Boolean.TRUE.equals(adTemplate.getProduct().getCompensation())) {
				logger.debug("Skipping adTemplate " + adTemplate.getId() + " because this is a compensation product.");
				return false;
			}
			
			/* Error check. */
			if (!sellerMarket.getSeller().equals(adTemplate.getSeller())) {
				throw new Exception("Internal error in listItem");
			}
			
			/* Abort if no category was found. */
			Category marketCategory = getMarketCategory(adTemplate,sellerMarket.getMarket(),marketCategoryNumber,
					nonExistentCategoryMappings,adTemplatesWithoutCategory);
			if (marketCategory == null) {
				return false;
			}

			/* Error check. */
			if (!sellerMarket.getMarket().getId().equals(marketCategory.getMarket().getId())) {
				throw new Exception("Internal error in listItem");
			}
			
			/* Do listing according to the following logics:
			 * If the product is a display window product: only one concurrent listing
			 * If the product is sold 1 time: only one concurrent listing
			 * If the product is sold 2-4 times: list once every 48 hours
			 * If the product is sold 5-10 times: list once every 20 hours
			 * If the product is sold 11-20 times: list once every 12 hours
			 * If the product is sold > 20 times: list once every 6 hours */
			int nbrSold = orderDAO.getNbrOrdersForProduct(adTemplate.getProduct());
			logger.debug("Nbr sold for product " + adTemplate.getProduct().getId() + ": " + nbrSold);
			List<Ad> currOnSaleList = adDAO.getAdTemplatesCurrentlyOnSale(adTemplate,marketCategory);
			boolean currentlyOnSale = currOnSaleList.size() > 0;
			if (currentlyOnSale) {
				long hoursSinceLastListing = 10000;
				for (Ad ad : currOnSaleList) {
					long hoursSinceLastListingTemp = (System.currentTimeMillis() - ad.getDateToMarket().getTime()) / (1000*3600);
					if (hoursSinceLastListingTemp < hoursSinceLastListing) {
						hoursSinceLastListing = hoursSinceLastListingTemp;
					}
				}
				//Ad latestAd = adDAO.getLatestAdByProductAndSellerAndMarket(adTemplate.getProduct(), sellerMarket.getMarket(), sellerMarket.getSeller());
				//long hoursSinceLastListing = (System.currentTimeMillis() - latestAd.getDateToMarket().getTime()) / (1000*3600);
				if (!forceAd) {
					if (adTemplate.getVisibilityType() > 0) {
						logger.debug("Skipping multiple listing of ad template " + adTemplate.getId()
								+ " because it's a display window product (nbrSold = " + nbrSold
								+ ", hoursSinceLastListing = " + hoursSinceLastListing + ", nbr currently"
								+ " on sale = " + currOnSaleList.size() + ").");
						return false;
					} else if (nbrSold < 2
							|| (nbrSold <= 4 && hoursSinceLastListing < 48)
							|| (nbrSold <= 10 && hoursSinceLastListing < 20)
							|| (nbrSold <= 20 && hoursSinceLastListing < 12)
							|| (hoursSinceLastListing < 6)) {
						logger.debug("Ad template " + adTemplate.getId() + " already has ad(s) that are on sale"
								+ " in the market, skipping this ad template (nbrSold = " + nbrSold
								+ ", hoursSinceLastListing = " + hoursSinceLastListing + ", nbr currently"
								+ " on sale = " + currOnSaleList.size() + ").");
						return false;
					} else {
						logger.debug("Listing multiple ads for product " + adTemplate.getProduct().getId() + ": nbrSold = " + nbrSold
								+ ", hoursSinceLastListing = " + hoursSinceLastListing + ", nbr currently on sale = " + currOnSaleList.size());
					}
				}
			}
			
			ProductInfo prodInfo = providerHandler.getProductInfo(adTemplate.getProduct(),true);
			if (prodInfo == null || prodInfo.getCurrentPrice() == null) {
				return false;
			}

			logger.debug("listing ad template " + adTemplate.getId() + " to market " + marketCategory.getMarket().getName());
			logger.debug("Creating ad");
			Ad ad = adCreator.createAd(adTemplate, marketCategory, prodInfo.getCurrentPrice(), strategy, sellerMarket);

			// Strategy can make any changes to the ad
			this.strategy.improveAd(ad,sellerMarket,nbrSold);

			/* Abort if the price is too high. */
			if (!priceIsOk(ad, adTemplate.getProduct().getProviderProdId(),sellerMarket)) {
				return false;
			}

			/* Do the actual listing. */
			logger.debug("Listing ad");
			try {
				if (!listAd(ad,lister,nonExistentCategories,sellerMarket,preFilledScoreMap)) {
					throw new ListingException("Listing failed for some reason", false);
				}
			} catch (CategoryNotFoundException cnf) {
				logger.error("Category not found: " + cnf.getMessage());
				return false;
			}				
			return true;
		} else {
			logger.debug("ad template " + adTemplate.getId() + " is inactive or product no longer"
					+ " exists at provider and therefore isn't listed");
		}
		return false;
	}
	
	private boolean listAd(Ad ad, MarketRobot lister, Map<String,Integer> nonExistentCategories, SellerMarket sellerMarket,
			TreeMap<Long, List<Long>> preFilledScoreMap) throws Exception {
		boolean success = false;
		/* This sometimes fails due to timeouts or because the page hasn't completely loaded, so try this 3 times. */
		for (int i = 0; i < 3; i++) {
			try {
				success = lister.listItemForSale(ad, strategy, nonExistentCategories, sellerMarket,preFilledScoreMap);
				if (success) {
					adDAO.save(ad);
					adDAO.flush(); //do a flush in order to catch database errors right away
					break;
				}
			} catch (SeleniumException e) {
				if (e.getMessage() != null && e.getMessage().startsWith("Timed out after")) {
					logger.debug("Selenium timed out: ",e);
					/* In some cases the listing is accepted by huutonet although
					 * selenium fails with a timeout exception. If marketSalesId
					 * is set, then we know that it went through okay. */
					if (!ad.getMarketSalesId().equals("temp")) {
						logger.error("Selenium timed out at listing for adtemplate " + ad.getAdTemplate().getId()
								+ " but it appears to be accepted by market.");
						adDAO.save(ad);
						adDAO.flush(); //do a flush in order to catch database errors right away
						success = true;
						logger.debug("ad id = " + ad.getId());
						break;
					}
				} else if (e.getMessage() != null && ((e.getMessage().indexOf("Element") > -1 && e.getMessage().endsWith("not found"))
						|| (e.getMessage().indexOf("XHR ERROR") >= 0 && e.getMessage().indexOf("Response_Code = 503 Error_Message = Service Temporarily Unavailable") >= 0))) {
					/* In some cases we get errors because selenium hasn't had time to load the
					 * page completely, so fetching of the elements on the page can then fail.
					 * Usually it works if we try again. */
					//do nothing here
					logger.debug(e.getMessage());
				} else {
					throw e;
				}
				if (i < 2) {
					logger.error("Selenium timed out at listing. Trying again.");
				} else {
					logger.error("Selenium timed out at listing. Giving up.");
				}
			}
		}
		return success;
	}
	
	/* Returns true if price is okay and false if the price is so high
	 * that the customer has to pay toll and/or taxes. */
	private boolean priceIsOk(Ad ad, String providerProdId, SellerMarket sellerMarket) {
		//TODO: allow smaller margins for expensive products so that we more seldom will
		//skip listing of products since the price is too high.
		BigDecimal totalPriceInclVatMC = ad.getMinSellingPriceInMarketCurrencyInclVat().add(ad.getShippingCostInMarketCurrencyInclVat());
		BigDecimal totalPriceExclVatMC = MathUtilities.getPriceExclVat(totalPriceInclVatMC, ad.getAdTemplate().getSeller().getCustomerVatPercentage());
		BigDecimal totalBuyOutPriceInclVatMC = ad.getBuyOutPriceInMarketCurrencyInclVat().add(ad.getShippingCostInMarketCurrencyInclVat()).
			add(new BigDecimal(HuutonetLister2.HUUTONET_BUG_EXTRA));
		BigDecimal totalBuyOutPriceExclVatMC = MathUtilities.getPriceExclVat(totalBuyOutPriceInclVatMC,
				ad.getAdTemplate().getSeller().getCustomerVatPercentage());
		if (totalPriceExclVatMC.compareTo(sellerMarket.getMaxBundleSizeInMarketCurrencyExclVat()) > 0
				|| totalBuyOutPriceExclVatMC.compareTo(sellerMarket.getMaxBundleSizeInMarketCurrencyExclVat()) > 0) {
			logger.warn("Item " + providerProdId + " price is too high!");
			//throw new ListingException("Item " + providerProdId + " price is too high!",false);
			return false;
		}
		return true;
	}

}
