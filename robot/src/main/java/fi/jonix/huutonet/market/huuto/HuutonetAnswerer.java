package fi.jonix.huutonet.market.huuto;

import java.util.List;

import org.apache.log4j.Logger;

import fi.jonix.huutonet.domain.model.Ad;
import fi.jonix.huutonet.domain.model.SellerMarket;
import fi.jonix.huutonet.exception.LoggedOutFromMarketException;
import fi.jonix.huutonet.exception.MarketLoggedOnAsWrongUserException;
import fi.jonix.huutonet.exception.UnprocessableEmailException;
import fi.jonix.huutonet.robot.CustomSelenium;
import fi.jonix.huutonet.robot.SeleniumRobot;
import fi.jonix.huutonet.tools.StringUtilities;

public class HuutonetAnswerer {

	private static String BASE_URL = "http://www.huuto.net/fi/askseller.php3?itemid=";
	public static final Logger logger = Logger.getLogger(HuutonetAnswerer.class);
	
	private SeleniumRobot seleniumRobot;

	public String getQuestion(Ad ad, SellerMarket sellerMarket, List<String> userName) throws LoggedOutFromMarketException, MarketLoggedOnAsWrongUserException {
		logger.debug("Start fetching the question");
		CustomSelenium selenium = seleniumRobot.getSelenium();
		selenium.open(BASE_URL + ad.getMarketSalesId());
		selenium.waitForPageToLoad();
		try { Thread.sleep(2 * 1000); } catch (Exception e) { }

		/* Error check. Don't check if sellerMarket.seller = ad.adTemplate.seller, because it's possible
		 * that the adtemplate has been moved to a different seller, and in that case the old seller
		 * should be used. */
		if (!sellerMarket.getMarket().getId().equals(ad.getMarket().getId())) {
			throw new RuntimeException("Internal error in HuutonetAnswerer.getQuestion");
		}
		
		/* Make sure that we aren't logged out. */
		if (selenium.isElementPresent("//a[@id='log_in']/b") || selenium.getBodyText().indexOf("Kirjaudu sisään") >= 0) {
			throw new LoggedOutFromMarketException();
		}
		
		/* Make sure that we are logged on as the correct user. */
		/*String loggedOnUser = selenium.getText("//a[@id='log_out']/../a[1]/b");
		logger.debug("loggedOnUser = " + loggedOnUser);
		if (!loggedOnUser.equals(sellerMarket.getUserName())) {
			throw new MarketLoggedOnAsWrongUserException("Logged on to Huutonet as " + loggedOnUser + " although we SHOULD have been logged on as " + sellerMarket.getUserName());
		}*/
		
		String nbrQ = selenium.getText("//div[contains(@class, 'cont')]//h3");
		logger.debug("nbrQ = " + nbrQ);
		if (!nbrQ.startsWith("Kysymyks")) {
			return null;
		}
		int totalNumberOfQuestions = -1;
		try {
			totalNumberOfQuestions = Integer.parseInt(nbrQ.substring(nbrQ.indexOf("(")+1,nbrQ.lastIndexOf(" ")));
		} catch (Exception e) {
			return null;
		}
		logger.debug("totalNumberOfQuestions = " + totalNumberOfQuestions
				+ ", ad.nbrPublicQuestions = " + ad.getNumberOfPublicQuestions());
		//int index = 2;
		int index = 1 + totalNumberOfQuestions - ad.getNumberOfPublicQuestions(); //new questions are added at the top
		try {
			String question = selenium.getText("//table//table//div/table[" + index + "]/tbody/tr[2]/td");
			String uName = selenium.getText("//table//table//div/table[" + index + "]/tbody/tr[1]/th/a");
			String answer = selenium.getText("//table//table//div/table[" + index + "]//textarea");
			logger.debug("question = " + question);
			logger.debug("userName = " + uName);
			logger.debug("answer = " + answer);
			if (answer != null && answer.trim().length() > 0) {
				logger.error("This question is already answered (ad " + ad.getId() + ")!");
				return null;
			}
			ad.setNumberOfPublicQuestions(ad.getNumberOfPublicQuestions()+1);
			userName.add(uName);
			return question;
		} catch (Exception e) {
		}
		logger.debug("Couldn't fetch the question");
		return null;
	}

	public boolean answerQuestion(Ad ad, String answer, String question, SellerMarket sellerMarket) throws LoggedOutFromMarketException, MarketLoggedOnAsWrongUserException {
		logger.debug("Start answering the question");
		CustomSelenium selenium = seleniumRobot.getSelenium();
		selenium.open(BASE_URL + ad.getMarketSalesId());
		selenium.waitForPageToLoad();
		try { Thread.sleep(2 * 1000); } catch (Exception e) { }

		/* Error check. Don't check if sellerMarket.seller = ad.adTemplate.seller, because it's possible
		 * that the adtemplate has been moved to a different seller, and in that case the old seller
		 * should be used. */
		if (!sellerMarket.getMarket().getId().equals(ad.getMarket().getId())) {
			throw new RuntimeException("Internal error in HuutonetAnswerer.getQuestion");
		}
		
		/* Make sure that we aren't logged out. */
		if (selenium.isElementPresent("//a[@id='log_in']/b") || selenium.getBodyText().indexOf("Kirjaudu sisään") >= 0) {
			throw new LoggedOutFromMarketException();
		}
		
		/* Make sure that we are logged on as the correct user. */
		/*String loggedOnUser = selenium.getText("//a[@id='log_out']/../a[1]/b");
		logger.debug("loggedOnUser = " + loggedOnUser);
		if (!loggedOnUser.equals(sellerMarket.getUserName())) {
			throw new MarketLoggedOnAsWrongUserException("Logged on to Huutonet as " + loggedOnUser + " although we SHOULD have been logged on as " + sellerMarket.getUserName());
		}*/
		
		String nbrQ = selenium.getText("//div[contains(@class, 'cont')]//h3");
		logger.debug("nbrQ = " + nbrQ);
		if (!nbrQ.startsWith("Kysymyks")) {
			return false;
		}
		int totalNumberOfQuestions = -1;
		try {
			totalNumberOfQuestions = Integer.parseInt(nbrQ.substring(nbrQ.indexOf("(")+1,nbrQ.lastIndexOf(" ")));
		} catch (Exception e) {
			return false;
		}
		logger.debug("totalNumberOfQuestions = " + totalNumberOfQuestions);

		/* Go through the questions starting from the bottom (since new questions are added at the top) and
		 * answer to the first unanswered question that matches the question given as a parameter to this
		 * method. */
		//int index = 2;
		int index = 1 + totalNumberOfQuestions; //new questions are added at the top, so start from the bottom
		logger.debug("Parameter question = " + question);
		while (index >= 2) {
			String madeQuestion = "";
			String ansTemp = "";
			try {
				madeQuestion = selenium.getText("//table//table//div/table[" + index + "]/tbody/tr[2]/td");
				ansTemp = selenium.getText("//table//table//div/table[" + index + "]//textarea");
				logger.debug("ansTemp = " + ansTemp);
			} catch (Exception e) {
				break;
			}
			logger.debug("madeQuestion = " + madeQuestion);
			if ((ansTemp == null || ansTemp.trim().length() == 0) && StringUtilities.sloppyMatch(madeQuestion, question)) {
				selenium.type("//table//table//div/table[" + index + "]//textarea", answer);
				//note: when running on the mac it seems like "SUBMIT" is needed but
				//when running on my own computer "submit" with lower case is needed
				//selenium.click("//table//table//div/table[" + index + "]//input[@type='SUBMIT']");
				selenium.click("//table//table//div/table[" + index + "]//input[@type='submit']");
				selenium.waitForPageToLoad();
				logger.debug("Done answering to the question");
				return true;
			}
			index--;
		}
		logger.info("Couldn't answer the question (either all questions were already answered or else the question wasn't found).");
		return false;
	}

	public void setSeleniumRobot(SeleniumRobot seleniumRobot) {
		this.seleniumRobot = seleniumRobot;
	}

}
