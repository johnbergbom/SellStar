package fi.jonix.huutonet.market.huuto;

import java.util.List;

import org.apache.log4j.Logger;

import fi.jonix.huutonet.domain.model.Order;
import fi.jonix.huutonet.domain.model.SellerMarket;
import fi.jonix.huutonet.domain.model.dao.OrderDAO;
import fi.jonix.huutonet.domain.model.dao.SettingsDAO;
import fi.jonix.huutonet.exception.LoggedOutFromMarketException;
import fi.jonix.huutonet.exception.MarketLoggedOnAsWrongUserException;
import fi.jonix.huutonet.robot.CustomSelenium;
import fi.jonix.huutonet.robot.SeleniumRobot;

public class HuutonetFeedbackGiver {
	
	private static String BASE_URL = "http://www.huuto.net/kohteet/";
	public static final Logger logger = Logger.getLogger(HuutonetFeedbackGiver.class);
	
	static private String FEEDBACK_POSITIVE = "fi.jonix.huutonet.robot.lister.HuutonetFeedback.positive";
	
	private SeleniumRobot seleniumRobot;
	private SettingsDAO settingsDAO;
	private OrderDAO orderDAO;
	
	public void givePositiveFeedback(List<Order> sameAdOrders, SellerMarket sellerMarket) throws LoggedOutFromMarketException, MarketLoggedOnAsWrongUserException {
			//logger.debug("Start giving feedback item");
			CustomSelenium selenium = seleniumRobot.getSelenium();
			selenium.open(BASE_URL + sameAdOrders.get(0).getAd().getMarketSalesId());
			selenium.waitForPageToLoad();
			
			/* Make sure that we aren't logged out. */
			if (!selenium.isElementPresent("//a[contains(@href,'givefeedback')]") || selenium.getBodyText().indexOf("Kirjaudu sisään") >= 0) {
				throw new LoggedOutFromMarketException();
			}
			
			/* Make sure that we are logged on as the correct user. */
			/*String loggedOnUser = selenium.getText("//a[@id='log_out']/../a[1]/b");
			logger.debug("loggedOnUser = " + loggedOnUser);
			if (!loggedOnUser.equals(sellerMarket.getUserName())) {
				throw new MarketLoggedOnAsWrongUserException("Logged on to Huutonet as " + loggedOnUser + " although we SHOULD have been logged on as " + sellerMarket.getUserName());
			}*/
			
			selenium.click("//a[contains(@href,'givefeedback')]");
			selenium.waitForPageToLoad();
			selenium.click("//input[@name='feedbacknum' and @value='1']");
			String feedback = settingsDAO.getValue(FEEDBACK_POSITIVE);
			selenium.type("//textarea[@name='feedbacktext']", feedback);
			selenium.click("//input[@value='Anna palaute']");
	
			/* Let's also check if the just loaded page contains the feedback of users. If not, then
			 * some kind of error has occurred. */
			selenium.waitForPageToLoad();
			String description = selenium.getText("//h2");
			if (description == null || description.indexOf("Muiden antama") < 0) {
				logger.info("Couldn't give feedback.");
				throw new RuntimeException("givePositiveFeedback failed");
				//return false;
			}
	
			/* Note: it's possible that the feedback was actually accepted by Huutonet but that it bails out with
			 * Selenium timeout exception before we get here. That doesn't matter however, because that just means
			 * that it will try to give the same feedback again later, which will just overwrite the old feedback
			 * with a new identical string. */
			for (Order order : sameAdOrders) {
				order.setFeedbackGiven(Boolean.TRUE);
				//this.orderDAO.update(order);
			}
			//orderDAO.flush(); //do a flush in order to catch database errors right away
		//return true;
	}
	
	public void setSeleniumRobot(SeleniumRobot seleniumRobot) {
		this.seleniumRobot = seleniumRobot;
	}

	public void setSettingsDAO(SettingsDAO settingsDAO) {
		this.settingsDAO = settingsDAO;
	}

	public void setOrderDAO(OrderDAO orderDAO) {
		this.orderDAO = orderDAO;
	}
	
}
