package fi.jonix.huutonet.market;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import fi.jonix.huutonet.domain.model.Ad;
import fi.jonix.huutonet.domain.model.Category;
import fi.jonix.huutonet.domain.model.Market;
import fi.jonix.huutonet.domain.model.Order;
import fi.jonix.huutonet.domain.model.Seller;
import fi.jonix.huutonet.domain.model.SellerMarket;
import fi.jonix.huutonet.exception.CriticalSectionInfo;
import fi.jonix.huutonet.exception.LoggedOutFromMarketException;
import fi.jonix.huutonet.exception.MarketLoggedOnAsWrongUserException;
import fi.jonix.huutonet.exception.MarketTemporarilyDownException;

public abstract class MarketRobot {

	public abstract boolean canHandleMarket(Market market);
	//public abstract SellerMarket isAlreadyLoggedOnAs();
	public abstract void login(SellerMarket sellerMarket) throws MarketTemporarilyDownException, MarketLoggedOnAsWrongUserException;
	public abstract void logout();
	public abstract boolean listItemForSale(Ad ad, ListerStrategy strategy, Map<String,Integer> nonExistentCategories,
			SellerMarket sellerMarket, TreeMap<Long, List<Long>> preFilledScoreMap) throws Exception;
	public abstract void givePositiveFeedback(List<Order> sameAdOrders, SellerMarket sellerMarket) throws LoggedOutFromMarketException, MarketLoggedOnAsWrongUserException;
	public abstract List<OrderFeedback> readFeedback(SellerMarket sellerMarket) throws Exception;
	public abstract boolean answerPrivateQuestion(String subject, String question, String answer, String answerAddress,
			SellerMarket sellerMarket) throws LoggedOutFromMarketException, MarketLoggedOnAsWrongUserException;
	public abstract boolean answerPublicQuestion(Ad ad, String question, String answer, String answerAddress,
			SellerMarket sellerMarket) throws LoggedOutFromMarketException, MarketLoggedOnAsWrongUserException;
	public abstract List<Category> getCategories(SellerMarket sellerMarket) throws LoggedOutFromMarketException, MarketLoggedOnAsWrongUserException, MarketTemporarilyDownException;
	public abstract String getMarketName();
	public abstract String getUrlForMarketSalesId(String marketSalesId);
	public abstract void closeAdAtMarket(Ad adToRemove);
	public abstract void payForHighlightedAds(List<Ad> highlightedAds, CriticalSectionInfo section);

}
