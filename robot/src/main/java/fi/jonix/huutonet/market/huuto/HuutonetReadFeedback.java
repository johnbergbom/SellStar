package fi.jonix.huutonet.market.huuto;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.thoughtworks.selenium.SeleniumException;

import fi.jonix.huutonet.domain.model.Market;
import fi.jonix.huutonet.domain.model.Order;
import fi.jonix.huutonet.domain.model.SellerMarket;
import fi.jonix.huutonet.domain.model.dao.MarketDAO;
import fi.jonix.huutonet.domain.model.dao.OrderDAO;
import fi.jonix.huutonet.market.OrderFeedback;
import fi.jonix.huutonet.robot.CustomSelenium;
import fi.jonix.huutonet.robot.SeleniumRobot;

@Component(value = "huutonetFeedbackReader")
public class HuutonetReadFeedback {

	static private String FEEDBACK_TYPE_POSITIVE = "Positiivinen";
	static private String FEEDBACK_TYPE_NEUTRAL = "Neutraali";
	static private String FEEDBACK_TYPE_NEGATIVE = "Negatiivinen";
	public static final Logger logger = Logger.getLogger(HuutonetReadFeedback.class);

	@Autowired
	private SeleniumRobot seleniumRobot;
	
	@Autowired
	private OrderDAO orderDAO;

	@Autowired
	private MarketDAO marketDAO;

	public List<OrderFeedback> readFeedback(SellerMarket sellerMarket) throws Exception {
		logger.info("Start reading feedback");
		List<OrderFeedback> list = new ArrayList<OrderFeedback>();

		CustomSelenium selenium = seleniumRobot.getSelenium();
		selenium.open("http://www.huuto.net/fi/showfeedback.php3?fbuser=" + sellerMarket.getUserName());
		selenium.waitForPageToLoad();
		
		// NOTE: here we don't need to check if we are still logged in or if we are logged in as the wrong person
		// because reading feedback doesn't require a login at huutonet.
		
		Thread.sleep(2 * 1000);
		String[] links = selenium.getAllLinks();
		logger.debug("links.length = " + links.length);
		Market market = marketDAO.getByName(Market.HUUTONET_MARKET_NAME);
		for (int i = 0; i < (links.length-1); i++) {
			String link = null;
			try {
				link = selenium.getAttribute("document.links[" + i + "]@href");
			} catch (SeleniumException se) {
				if (se.getMessage().indexOf("Element") > -1 && se.getMessage().endsWith("not found")) {
					break;
				} else {
					throw se;
				}
			}
			//logger.debug("link = " + link + ", i = " + i);
			if (!link.matches("/kohteet/\\d+")) {
				continue;
			}
			String marketSalesId = link.substring("/kohteet/".length());
			
			String feedbackType = null;
			try {
				feedbackType = selenium.getText("//a[@href='/kohteet/" + marketSalesId + "']/../../td[2]/span");
			} catch (Exception e) {
				// This is ignored - wrong link
				continue;
			}

			List<Order> orderList = orderDAO.getByMarketSalesId(marketSalesId,sellerMarket.getSeller(),market);
			int ft = 0;
			if (FEEDBACK_TYPE_POSITIVE.equalsIgnoreCase(feedbackType)) {
				ft = OrderFeedback.POSITIVE;
			} else if (FEEDBACK_TYPE_NEUTRAL.equalsIgnoreCase(feedbackType)) {
				ft = OrderFeedback.NEUTRAL;
			} else if (FEEDBACK_TYPE_NEGATIVE.equalsIgnoreCase(feedbackType)) {
				ft = OrderFeedback.NEGATIVE;
			} else {
				throw new Exception("Unknown feedback type for Huutonet");
			}
			if (orderList.size() == 0) {
				/* It's not an error if no order was found. This can happen if the product was no longer
				 * for sale at the provider by the time the ad was closed, because in that case no
				 * order was created, but rather the customer was informed that "bad luck, product no
				 * longer exists". */
				logger.debug("Order not found for marketSalesId " + marketSalesId + ".");
			} else {
				list.add(new OrderFeedback(orderList, ft));
			}
		}
		logger.info("Done reading feedback");
		return list;
	}

}
