package fi.jonix.huutonet.market;

import java.awt.Dimension;
import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.apache.log4j.Logger;

import fi.jonix.huutonet.domain.ApplicationContextPlaceholder;
import fi.jonix.huutonet.domain.model.Ad;
import fi.jonix.huutonet.domain.model.AdTemplate;
import fi.jonix.huutonet.domain.model.Category;
import fi.jonix.huutonet.domain.model.Market;
import fi.jonix.huutonet.domain.model.Order;
import fi.jonix.huutonet.domain.model.Product;
import fi.jonix.huutonet.domain.model.ProductKeyword;
import fi.jonix.huutonet.domain.model.Provider;
import fi.jonix.huutonet.domain.model.Seller;
import fi.jonix.huutonet.domain.model.SellerMarket;
import fi.jonix.huutonet.domain.model.dao.AdDAO;
import fi.jonix.huutonet.domain.model.dao.CategoryDAO;
import fi.jonix.huutonet.domain.model.dao.CategoryMappingDAO;
import fi.jonix.huutonet.domain.model.dao.ImageDAO;
import fi.jonix.huutonet.domain.model.dao.MarketDAO;
import fi.jonix.huutonet.domain.model.dao.OrderDAO;
import fi.jonix.huutonet.domain.model.dao.ProductDAO;
import fi.jonix.huutonet.domain.model.dao.ProductKeywordDAO;
import fi.jonix.huutonet.domain.model.dao.ProviderDAO;
import fi.jonix.huutonet.market.huuto.AdComparator;
import fi.jonix.huutonet.robot.TemplateGenerator;
import fi.jonix.huutonet.tools.MathUtilities;
import fi.jonix.huutonet.tools.ProductScoreGenerator;
import fi.jonix.huutonet.tools.Statics;

public abstract class MarketAdDecorator implements TemplateGenerator {

	public static final Logger logger = Logger.getLogger(MarketAdDecorator.class);

	private static int MAX_SIBLING_ADS = 4;

	protected ImageDAO imageDAO;
	private AdDAO adDAO;
	private ProviderDAO providerDAO;
	private ProductDAO productDAO;
	private OrderDAO orderDAO;
	private CategoryDAO categoryDAO;
	private ProductKeywordDAO productKeywordDAO;
	private ProductScoreGenerator productScoreGenerator;
	protected NumberFormat currencyFormatter = NumberFormat.getCurrencyInstance(Statics.FINNISH);
	protected static DateFormat SHORT_DATE_FORMAT = new SimpleDateFormat("dd.MM. HH:mm");

	public abstract String getFormatedBody(Ad ad, SellerMarket sellerMarket, boolean useImageHost,
			TreeMap<Long, List<Long>> preFilledScoreMap);

	public String getWebStorePlainURL(Ad ad) {
		Seller seller = ad.getAdTemplate().getSeller();
		return seller.getStoreUrl() + "/webstore/sellstar/redirector/newOrder/"
			+ seller.getId() + "/" + ad.getMarket().getId() + "/" + ad.getAdTemplate().getId();
	}

	public String getBounceToMarketURL(Ad ad) {
		Seller seller = ad.getAdTemplate().getSeller();
		return seller.getStoreUrl() + "/webstore/sellstar/redirector/bounceToMarket/"
			+ seller.getId() + "/" + ad.getMarket().getId() + "/" + ad.getAdTemplate().getId();
	}

	public List<Ad> getLinksToOtherAds(Ad ad, List<AdTemplate> addedAdTemplates) {
		List<Ad> linkAds = new ArrayList<Ad>();
		Product linkedProduct = ad.getAdTemplate().getProduct().getProductLinkId();
		if (linkedProduct != null) {
			AdDAO adDAO = (AdDAO) ApplicationContextPlaceholder.applicationContext.getBean("adDAO");
			Ad linkedProductAd = adDAO.getLatestAdByProductAndSellerAndMarket(linkedProduct, ad.getMarket(),ad.getAdTemplate().getSeller());
			if (linkedProductAd != null && linkedProductAd.getAdTemplate().getActive()
					&& !linkedProductAd.getAdTemplate().getProduct().getProductState().equals(Product.STATE_NO_LONGER_FOR_SALE)
					&& !linkedProductAd.getAdTemplate().getProduct().getCompensation()
					&& !addedAdTemplates.contains(linkedProductAd.getAdTemplate())) {
				if (linkedProductAd.getDateFromMarket() == null) {
					// Add this ad if it's still open
					linkAds.add(linkedProductAd);
					addedAdTemplates.add(linkedProductAd.getAdTemplate());
				} else {
					/* Ok, the ad isn't open. This might be simply because the lister hasn't
					 * yet had time to list the linked product. Or else it might be because
					 * the linked product is out of stock. If it was listed less than a week
					 * ago or closed less than two days ago, then we determine that a link
					 * CAN be added. */
					Calendar cal = Calendar.getInstance();
					cal.add(Calendar.DATE,-2);
					Date twoDaysAgo = cal.getTime();
					cal.add(Calendar.DATE,-5);
					Date weekAgo = cal.getTime();
					if (twoDaysAgo.getTime() < linkedProductAd.getDateFromMarket().getTime()
							|| weekAgo.getTime() < linkedProductAd.getDateToMarket().getTime()) {
						logger.debug("Adding linked product although its ad isn't open (ad " + linkedProductAd.getId() + ").");
						linkAds.add(linkedProductAd);
						addedAdTemplates.add(linkedProductAd.getAdTemplate());
					} else {
						logger.debug("Skipping adding linked product because its ad isn't open (ad " + linkedProductAd.getId() + ").");
					}
				}
			}
		}
		linkAds.addAll(getKeywordAds(ad, MAX_SIBLING_ADS - linkAds.size(),addedAdTemplates));
		linkAds.addAll(getSiblingsProducts(ad, MAX_SIBLING_ADS - linkAds.size(),addedAdTemplates));
		return linkAds;
	}
	
	/**
	 * This method returns the best selling ads for the seller in question a pre-filled scoreMap
	 * (generated by prefillSellingStatisticsWithValidAdIdsForListing).
	 * 
	 * @param useRandom If false, then always take the count best products from the scoreMap. If
	 * true, then add a random variation that picks products with higher scores with a higher
	 * probability, but it may also pick products with lower scores.
	 * @param addedAdTemplates 
	 * */
	protected List<Ad> getBestSellingsAds(int count, TreeMap<Long,List<Long>> scoreMap, boolean useRandom, List<AdTemplate> addedAdTemplates) {
		return productScoreGenerator.getBestSellingsAds(count, scoreMap, useRandom, addedAdTemplates);
	}

	public void setImageDAO(ImageDAO imageDAO) {
		this.imageDAO = imageDAO;
	}

	public ImageDAO getImageDAO() {
		return imageDAO;
	}

	private List<Category> getLeafCategories(Category category) {
		List<Category> list = new ArrayList<Category>();
		List<Category> children = categoryDAO.getChildren(category);
		if (children.isEmpty()) {
			list.add(category);
		} else {
			for (Category cat : children) {
				list.addAll(getLeafCategories(cat));
			}
		}
		return list;
	}

	private List<Ad> getSiblingsProducts(Ad ad, int maxCount, List<AdTemplate> addedAdTemplates) {
		//logger.debug("start getSiblingsProducts");
		Category marketCategory = ad.getMarketCategory();
		if (marketCategory.getMarket().getName().equals(Market.SELLSTAR_MARKET_NAME)) {
			throw new RuntimeException("Internal error, getSiblingProducts should be called with market category.");
		}
		
		List<Ad> sameCategoryAds = adDAO.getLatestAdsByMarketCategoryAndMarket(marketCategory,
				ad.getMarket(), ad.getAdTemplate().getSeller());
		List<Ad> candidates = new ArrayList<Ad>();
		long timeNow = (new Date()).getTime();
		Map<Ad, Long> adScores = new HashMap<Ad, Long>();
		for (Ad a : sameCategoryAds) {
			if (!addedAdTemplates.contains(a.getAdTemplate())) {
				boolean suitable = false;
				if (a.getAdTemplate().getActive()
						&& !a.getAdTemplate().getProduct().getProductState().equals(Product.STATE_NO_LONGER_FOR_SALE)
						&& !a.getAdTemplate().getProduct().getCompensation()) {
					if (a.getDateFromMarket() == null) {
						// Add this ad if it's still open
						suitable = true;
					} else {
						/* Ok, the ad isn't open. This might be simply because the lister hasn't
						 * yet had time to list the sibling product. Or else it might be because
						 * the sibling product is out of stock. If it was listed less than a week
						 * ago or closed less than two days ago, then we determine that the sibling
						 * product CAN be added. */
						Calendar cal = Calendar.getInstance();
						cal.add(Calendar.DATE,-2);
						Date twoDaysAgo = cal.getTime();
						cal.add(Calendar.DATE,-5);
						Date weekAgo = cal.getTime();
						if (twoDaysAgo.getTime() < a.getDateFromMarket().getTime()
								|| weekAgo.getTime() < a.getDateToMarket().getTime()) {
							logger.debug("Adding sibling product although its ad isn't open (ad " + a.getId() + ").");
							suitable = true;
						} else {
							logger.debug("Skipping adding sibling product because its ad isn't open (ad " + a.getId() + ").");
						}
					}
				}
				
				if (suitable) {
					/* Consider the price difference, how close it is to the price
					 * of the original product. Note: we no longer need to here
					 * consider when the ad closes since bounceToMarket is used. */
					double startPriceDiff = Math.abs(ad.getStartPriceInEuroInclVat().doubleValue() - a.getStartPriceInEuroInclVat().doubleValue());
					//double closingInDays = (a.getDateClosing().getTime() - timeNow) / (3600 * 1000 * 24);
					long score = Math.round(10 - startPriceDiff/* + closingInDays*/);
					adScores.put(a, score);
					candidates.add(a);
					addedAdTemplates.add(a.getAdTemplate());
				}
			}
			if (candidates.size() >= maxCount) {
				break;
			}
		}
		
		Collections.sort(candidates, new AdComparator(adScores));
		// sorting sorts according to the natural ordering, i.e. worst score
		// first, so let's reverse the sorting
		Collections.reverse(candidates);
		//logger.debug("end getSiblingsProducts");
		if (candidates.size() > maxCount) {
			return candidates.subList(0, maxCount);
		} else {
			return candidates;
		}
	}

	protected List<Ad> getKeywordAds(Ad ad, int maxCount, List<AdTemplate> addedAdTemplates) {
		Product product = ad.getAdTemplate().getProduct();
		List<Ad> keywordAds = new ArrayList<Ad>();
		List<ProductKeyword> productKeywords = this.productKeywordDAO.loadAll();
		for (ProductKeyword productKeyword : productKeywords) {
			String[] keywords = productKeyword.getKeyword().split(" ");
			boolean hit = false;
			for (String keyword : keywords) {
				if (this.hasKeyword(product.getName(), keyword)) {
					hit = true;
					break;
				} else if (this.hasKeyword(product.getTechnicalSpecs(), keyword)) {
					hit = true;
					break;
				}
			}
			if (hit) {
				if (!ad.getAdTemplate().getProduct().equals(productKeyword.getProduct())) {
					Ad latestAd = adDAO.getLatestAdByProductAndSellerAndMarket(productKeyword.getProduct(),
							ad.getMarket(),ad.getAdTemplate().getSeller());
					if (latestAd != null && latestAd.getAdTemplate().getActive()
							&& !latestAd.getAdTemplate().getProduct().getProductState().equals(Product.STATE_NO_LONGER_FOR_SALE)
							&& !latestAd.getAdTemplate().getProduct().getCompensation()
							&& !addedAdTemplates.contains(latestAd.getAdTemplate())) {
						if (latestAd.getDateFromMarket() == null) {
							// Add this ad if it's still open
							keywordAds.add(latestAd);
							addedAdTemplates.add(latestAd.getAdTemplate());
						} else {
							/* Ok, the ad isn't open. This might be simply because the lister hasn't
							 * yet had time to list the keyword product. Or else it might be because
							 * the keyword product is out of stock. If it was listed less than a week
							 * ago or closed less than two days ago, then we determine that the keyword
							 * product CAN be added. */
							Calendar cal = Calendar.getInstance();
							cal.add(Calendar.DATE,-2);
							Date twoDaysAgo = cal.getTime();
							cal.add(Calendar.DATE,-5);
							Date weekAgo = cal.getTime();
							if (twoDaysAgo.getTime() < latestAd.getDateFromMarket().getTime()
									|| weekAgo.getTime() < latestAd.getDateToMarket().getTime()) {
								logger.debug("Adding keyword product although its ad isn't open (ad " + latestAd.getId() + ").");
								keywordAds.add(latestAd);
								addedAdTemplates.add(latestAd.getAdTemplate());
							} else {
								logger.debug("Skipping adding keyword product because its ad isn't open (ad " + latestAd.getId() + ").");
							}
						}
					}
				}
			}
		}
		if (keywordAds.size() > maxCount) {
			return keywordAds.subList(0, maxCount);
		} else {
			return keywordAds;
		}
	}

	private boolean hasKeyword(String text, String keyword) {
		if (text == null) {
			return false;
		}
		if (text.indexOf(keyword) > -1) {
			return true;
		}
		return false;
	}

	private Dimension getImageDimensions(String imageFileName) {
		try {
			ImageInputStream in = ImageIO.createImageInputStream(new File(imageFileName));
			try {
				Iterator readers = ImageIO.getImageReaders(in);
				if (readers.hasNext()) {
					ImageReader reader = (ImageReader) readers.next();
					try {
						reader.setInput(in);
						return new Dimension(reader.getWidth(reader.getMinIndex()), reader.getHeight(reader.getMinIndex()));
					} finally {
						reader.dispose();
					}
				} else {
					logger.warn("No image reader found for given file: " + imageFileName);
				}
			} finally {
				if (in != null) {
					in.close();
				}
			}
		} catch (IOException e) {
			logger.warn("Error determining dimensions of image file: " + imageFileName,e);
		}
		return null;
	}
	
	protected Dimension getScaledImageDimensions(String imageFileName, int maxWidth, int maxHeight, boolean zoom) {
		Dimension dim = getImageDimensions(imageFileName);
		if (dim == null) {
			/* Not possible to determine the dimensions of the image. Use maxWidth
			 * and maxHeight (image might get distorted). */
			return new Dimension(maxWidth,maxHeight);
		} else {
			//logger.debug("Dimensions for " + imageFileName + ": width = " + dim.getWidth() + ", height = " + dim.getHeight());
			//logger.debug("Max allowed dimensions for " + imageFileName + ": width = " + maxWidth + ", height = " + maxHeight);
			int finalWidth = -1;
			int finalHeight = -1;
			if ((double) maxWidth / maxHeight > dim.getWidth() / dim.getHeight()) {
				/* Image can be stretched out in x-direction by height is limiting. */
				finalHeight = maxHeight;
				finalWidth = (int) (dim.getWidth()*((double)maxHeight/dim.getHeight()));
				//logger.debug("Height is limiting (maxWidth = " + maxWidth + ", maxHeight = " + maxHeight + ", dim.height = " + dim.getHeight() + ")");
			} else if ((double) maxWidth / maxHeight < dim.getWidth() / dim.getHeight()) {
				/* Image can be stretched out in y-direction by width is limiting. */
				finalWidth = maxWidth;
				finalHeight = (int) (dim.getHeight()*((double)maxWidth/dim.getWidth()));
				//logger.debug("Width is limiting (maxHeight = " + maxHeight + ", maxWidth = " + maxWidth + ", dim.width = " + dim.getWidth() + ")");
			} else {
				/* Image has same proportions as maxWidth/maxHeight, so image can be stretched
				 * out to fully cover the area. */
				finalWidth = maxWidth;
				finalHeight = maxHeight;
				//logger.debug("Same proportions");
			}
			//return new Dimension(finalWidth, finalHeight);
			if (zoom) {
				return new Dimension(finalWidth, finalHeight);
			} else {
				if (finalWidth > dim.getWidth() || finalHeight > dim.getHeight()) {
					return new Dimension((int)dim.getWidth(), (int)dim.getHeight());
				} else {
					return new Dimension(finalWidth, finalHeight);
				}
			}
		}
	}

	public void setAdDAO(AdDAO adDAO) {
		this.adDAO = adDAO;
	}

	public AdDAO getAdDAO() {
		return adDAO;
	}

	public abstract String getBeanVersion();

	public void setOrderDAO(OrderDAO orderDAO) {
		this.orderDAO = orderDAO;
	}

	public OrderDAO getOrderDAO() {
		return orderDAO;
	}

	public void setProductDAO(ProductDAO productDAO) {
		this.productDAO = productDAO;
	}

	public ProductDAO getProductDAO() {
		return productDAO;
	}

	public void setProviderDAO(ProviderDAO providerDAO) {
		this.providerDAO = providerDAO;
	}

	public ProviderDAO getProviderDAO() {
		return providerDAO;
	}

	public void setCategoryDAO(CategoryDAO categoryDAO) {
		this.categoryDAO = categoryDAO;
	}

	public CategoryDAO getCategoryDAO() {
		return categoryDAO;
	}

	public void setProductKeywordDAO(ProductKeywordDAO productKeywordDAO) {
		this.productKeywordDAO = productKeywordDAO;
	}

	public ProductKeywordDAO getProductKeywordDAO() {
		return productKeywordDAO;
	}

	public void setProductScoreGenerator(ProductScoreGenerator productScoreGenerator) {
		this.productScoreGenerator = productScoreGenerator;
	}

	public ProductScoreGenerator getProductScoreGenerator() {
		return productScoreGenerator;
	}

}
