package fi.jonix.huutonet.tools;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import fi.jonix.huutonet.domain.model.Ad;
import fi.jonix.huutonet.domain.model.AdTemplate;
import fi.jonix.huutonet.domain.model.Market;
import fi.jonix.huutonet.domain.model.Product;
import fi.jonix.huutonet.domain.model.Provider;
import fi.jonix.huutonet.domain.model.Seller;
import fi.jonix.huutonet.domain.model.dao.AdDAO;
import fi.jonix.huutonet.domain.model.dao.OrderDAO;
import fi.jonix.huutonet.domain.model.dao.ProductDAO;
import fi.jonix.huutonet.domain.model.dao.ProviderDAO;

@Component(value = "productScoreGenerator")
public class ProductScoreGenerator {

	public static final Logger logger = Logger.getLogger(ProductScoreGenerator.class);
	
	@Autowired
	private OrderDAO orderDAO;
	
	@Autowired
	ProviderDAO providerDAO;
	
	@Autowired
	private ProductDAO productDAO;
	
	@Autowired
	private AdDAO adDAO;
	
	/**
	 * This method returns the best selling ads for the seller in question using the following
	 * heuristics:
	 * 1.) Only look a year back for statistics (so if a product hasn't been sold for more than a year
	 *     it will never be included).
	 * 2.) If the product has sold well during the last three months it get's a higher score
	 * 3.) If the product has sold well during the last month it get's a higher score
	 * 4.) If the product has sold poorly during the last month it get's a lower score
	 * 5.) If the product isn't listed and it's a long time ago since it was listed, then
	 *     give a lower score (it might then be out of stock).
	 * 
	 * NOTE: the purpose with this method is to reduce duplicate work at listing. This method should
	 * be called before listing starts and then the scoreMap returned by this method can be used
	 * for very quickly generating best selling items at listing time by calling the getBestSellingsAds
	 * method.
	 * 
	 * @return A sorted map of <score,list of adIds having this score>
	 * */
	public TreeMap<Long,List<Long>> prefillSellingStatisticsWithValidAdIdsForListing(int count, Market market, Seller seller) {
		/* Get sales statistics and give more scores for products that have lately sold well. */
		logger.debug("Generating statistics for " + count + " best selling ads for listing.");
		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.DATE,-2);
		Date twoDaysAgo = cal.getTime();
		cal.add(Calendar.DATE,-5);
		Date weekAgo = cal.getTime();
		List<Map<String,Object>> totalProdStat = this.orderDAO.getBestSellingProviderProdIds(52);
		Map<String,Long> totalMap = generateStatisticsMap(totalProdStat);
		List<Map<String,Object>> last3MonthsProdStat = this.orderDAO.getBestSellingProviderProdIds(12);
		Map<String,Long> last3MonthsMap = generateStatisticsMap(last3MonthsProdStat);
		List<Map<String,Object>> lastMonthProdStat = this.orderDAO.getBestSellingProviderProdIds(4);
		Map<String,Long> lastMonthMap = generateStatisticsMap(lastMonthProdStat);
		List<List<Map<String,Object>>> statLists = new ArrayList<List<Map<String,Object>>>();
		statLists.add(totalProdStat);
		statLists.add(last3MonthsProdStat);
		statLists.add(lastMonthProdStat);
		
		TreeMap<Long,List<Long>> scoreMap = new TreeMap<Long,List<Long>>(); //<score,list of adIds>
		long totalScore = 0;
		
		/* There are so many products that have only sold one item and if assiging a too low
		 * score to the best items, then most of the time a quite poor products will be chosen
		 * simply because there is zillions of poor products. The following weighting parameter
		 * determines how much the good products should be emphasized. Don't set it to lower
		 * than one (the higher value the more good products are emphasized).
		 * For weight = 2.2: around 30-40% are < pos 10 and 25% > pos 100
		 * For weight = 4.0: around 60% are < pos 10 and 10% > pos 100
		 * */
		double goodProductWeight = 1.9;
		
		/* The following parameters tell how much we should emphasize popular products during
		 * the last three months and during the last month respectively. The higher the value
		 * the more later products are emphasized. Note: lastMonthWeight should always be at
		 * least as great as lastThreeMonthsWeight (or greater, but not smaller). */
		double lastThreeMonthsWeight = 2;
		double lastMonthWeight = 3;

		/* Go through count items of each timespan. There will most likely be more than count
		 * adId's in scoreMap when this loop is done, but only the first count items will have
		 * a statistically correct value. */
		Map<Long,Provider> providerMap = new HashMap<Long,Provider>();
		long totNbrAdded = 0;
		Map<String,String> handledProviderProdIds = new HashMap<String,String>();
		for (List<Map<String,Object>> prodStatList : statLists) {
			long goneThrough = 0;
			long skipped = 0;
			for (Map<String,Object> listObj : prodStatList) {
				String providerProdId = (String)listObj.get("provider_prod_id");
				if (skipped >= 100) {
					break; //if too many are skipped, then we conclude that this seller doesn't have any ads to add
				}
				if (handledProviderProdIds.get(providerProdId) == null) {
					handledProviderProdIds.put(providerProdId,"");
					Long providerId = (Long)listObj.get("provider_id");
					String providerProdIdAndProviderId = providerProdId + "_" + providerId;
					long last3MonthsScore = 0;
					if (last3MonthsMap.get(providerProdIdAndProviderId) != null) { //is null if no sales during last three months
						last3MonthsScore = last3MonthsMap.get(providerProdIdAndProviderId);
					}
					long lastMonthScore = 0;
					if (lastMonthMap.get(providerProdIdAndProviderId) != null) { //is null if no sales during last month
						lastMonthScore = lastMonthMap.get(providerProdIdAndProviderId);
					}
					//long score = totalMap.get(providerProdId)*1 + last3MonthsScore*2 + lastMonthScore*3;
					double dscore = (long) Math.pow(totalMap.get(providerProdIdAndProviderId),goodProductWeight)*1
						+ Math.pow(last3MonthsScore,goodProductWeight)*lastThreeMonthsWeight
						+ Math.pow(lastMonthScore,goodProductWeight)*lastMonthWeight;
					long score = (long) dscore;

					/* Check if this one can be added. */
					Provider provider = providerMap.get(providerId);
					if (provider == null) {
						provider = providerDAO.get(providerId);
						providerMap.put(providerId, provider);
					}
					Product product = productDAO.getByProviderProdId(providerProdId, provider);
					if (product == null || product.getProductState().equals(Product.STATE_NO_LONGER_FOR_SALE)) {
						skipped++;
						continue;
					}
					Ad latestAd = this.adDAO.getLatestAdByProductAndSellerAndMarket(product,market,seller);
					if (latestAd == null || !latestAd.getAdTemplate().getActive()
							|| latestAd.getAdTemplate().getProduct().getCompensation()) {
						//logger.debug(" adList is empty or inactive for " + providerProdId);
						skipped++;
						continue;
					}
					
					/* If the ad isn't open, that might be simply because the lister hasn't
					 * yet had time to list the linked product. Or else it might be because
					 * the linked product is out of stock. If it was listed more than a week
					 * ago or closed more than two days ago, then we determine the product
					 * might be out of stock, so we decrease the score for this item. */
					if (latestAd.getDateFromMarket() != null && (twoDaysAgo.getTime() > latestAd.getDateFromMarket().getTime()
							|| weekAgo.getTime() > latestAd.getDateToMarket().getTime())) {
						long closedDaysAgo = (System.currentTimeMillis() - latestAd.getDateFromMarket().getTime()) / (1000*3600*24);
						if (closedDaysAgo > 0) {
							long oldScore = score;
							score /= closedDaysAgo;
							logger.debug("Decreasing score of ad " + latestAd.getId() + " because it might be out of stock"
									+ " (ad was closed " + closedDaysAgo + " days ago, oldScore = " + oldScore
									+ ", new score = " + score + ").");
						}
					}

					if (score < 1) {
						skipped++;
						continue;
					}

					// There might be several products having the same score, so put them in a list.
					List<Long> adList = new ArrayList<Long>();
					if (scoreMap.get(score) != null) {
						adList = scoreMap.get(score);
					}
					adList.add(latestAd.getId());
					scoreMap.put(score, adList);
					totalScore += score;
					totNbrAdded++;
				}
				goneThrough++;
				if (goneThrough % 50 == 0) {
					logger.info("Gone through " + goneThrough + "/" + count + " of the round.");
				}
				if (goneThrough >= count) {
					break;
				}
			}
			logger.debug("Gone through one round.");
		}
		logger.debug("Gone all rounds (totNbrAdded = " + totNbrAdded + ", totalScore = " + totalScore + ").");
		
		/* Now there might be more than "count" items added, but only the first "count" ones
		 * have a correct statistics. Remove the other after number "count". */
		TreeMap<Long,List<Long>> scoreMapFinal = new TreeMap<Long,List<Long>>(); //<score,list of adIds>
		Iterator<Long> iter = scoreMap.navigableKeySet().descendingIterator();
		long nbrAdded = 0;
		totalScore = 0;
		while (iter.hasNext()) {
			Long scoreKey = iter.next();
			List<Long> adIdList = scoreMap.get(scoreKey);
			List<Long> adIdListNew = new ArrayList<Long>(); 
			for (Long adId : adIdList) {
				if (nbrAdded < count) {
					adIdListNew.add(adId);
					nbrAdded++;
					totalScore += scoreKey;
				}
			}
			scoreMapFinal.put(scoreKey,adIdListNew);
			if (nbrAdded >= count) {
				break;
			}
		}
		
		/* Add the total score and total number of added using a hack... */
		List<Long> totalScoreHack = new ArrayList<Long>();
		totalScoreHack.add(totalScore);
		scoreMapFinal.put(-1L,totalScoreHack);
		List<Long> totalNbrAddedHack = new ArrayList<Long>();
		totalNbrAddedHack.add(nbrAdded);
		scoreMapFinal.put(-2L,totalNbrAddedHack);
		logger.debug("Done generating statistics.");
		return scoreMapFinal;
	}

	private Map<String, Long> generateStatisticsMap(List<Map<String, Object>> prodStat) {
		Map<String,Long> map = new HashMap<String,Long>();
		for (Map<String,Object> listObj : prodStat) {
			map.put((String)listObj.get("provider_prod_id") + "_" + (Long)listObj.get("provider_id"), (Long)listObj.get("count"));
		}
		return map;
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
	public List<Ad> getBestSellingsAds(int count, TreeMap<Long,List<Long>> prefilledScoreMap, boolean useRandom, List<AdTemplate> addedAdTemplates) {
		Long totalScore = prefilledScoreMap.get(-1L).get(0);
		Long totalNbrAds = prefilledScoreMap.get(-2L).get(0);
		
		/* Get suitable products based on the highest scores. */
		List<Ad> bestSellingAds = new ArrayList<Ad>();
		Map<Long,String> added = new HashMap<Long,String>();
		int i = 0;
		while (bestSellingAds.size() < count) {
			Long adId = getNextUnpicked(prefilledScoreMap,added,useRandom,totalScore,totalNbrAds);
			if (adId != null) {
				added.put(adId, "");
				Ad ad = adDAO.get(adId);
				if (!addedAdTemplates.contains(ad.getAdTemplate())) {
					bestSellingAds.add(ad);
					addedAdTemplates.add(ad.getAdTemplate());
				}
			}
			i++;
			if (i > count*10) {
				//if none of the best selling products have open ads, then abort
				logger.debug("Aborting search for best selling ads because they aren't on sale.");
				break;
			}
		}
		return bestSellingAds;
	}

	private Long getNextUnpicked(TreeMap<Long, List<Long>> scoreMap,
			Map<Long, String> added, boolean useRandom, long totalScore, long totalNbrAds) {
		if (totalScore == 0 || totalNbrAds == 0) {
			return null;
		}
		if (useRandom) {
			int count = 0;
			while (true) {
				int chosenScore = MathUtilities.getRandomNumberBetween(1, (int)totalScore);
				int currScore = 0;
				Iterator<Long> iter = scoreMap.navigableKeySet().descendingIterator();
				while (iter.hasNext()) {
					Long scoreKey = iter.next();
					List<Long> adList = scoreMap.get(scoreKey);
					if ((currScore + scoreKey*adList.size()) > chosenScore) {
						/* Ok, the chosen one is somewhere in this list. */
						for (Long adId : adList) {
							if ((currScore + scoreKey) > chosenScore) {
								if (added.get(adId) == null) {
									//logger.debug(" Score for adId " + adId + " = " + scoreKey);
									return adId;
								}
								break; //abort, the chosen one was already taken
							}
							currScore += scoreKey;
						}
						break; //abort, the chosen one was already taken
					} else {
						currScore += scoreKey*adList.size();
					}
				}
				count++;
				if (count == totalNbrAds || count > 20) {
					break; //abort, no products were found
				}
			}
		} else {
			Iterator<Long> iter = scoreMap.navigableKeySet().descendingIterator();
			while (iter.hasNext()) {
				Long scoreKey = iter.next();
				List<Long> adIdList = scoreMap.get(scoreKey);
				for (Long adId : adIdList) {
					if (added.get(adId) == null) {
						return adId;
					}
				}
			}
		}
		return null;
	}

}
