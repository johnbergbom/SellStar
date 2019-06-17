package fi.jonix.huutonet.market.mikko;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.log4j.Logger;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.thoughtworks.selenium.SeleniumException;

import fi.jonix.huutonet.domain.ApplicationContextPlaceholder;
import fi.jonix.huutonet.domain.model.Ad;
import fi.jonix.huutonet.domain.model.AdTemplate;
import fi.jonix.huutonet.domain.model.Category;
import fi.jonix.huutonet.domain.model.Image;
import fi.jonix.huutonet.domain.model.Market;
import fi.jonix.huutonet.domain.model.Order;
import fi.jonix.huutonet.domain.model.SellerMarket;
import fi.jonix.huutonet.domain.model.dao.MarketDAO;
import fi.jonix.huutonet.domain.model.dao.SellerMarketDAO;
import fi.jonix.huutonet.exception.CategoryNotFoundException;
import fi.jonix.huutonet.exception.CriticalSectionInfo;
import fi.jonix.huutonet.exception.ListingException;
import fi.jonix.huutonet.exception.LoggedOutFromMarketException;
import fi.jonix.huutonet.exception.MarketLoggedOnAsWrongUserException;
import fi.jonix.huutonet.exception.MarketTemporarilyDownException;
import fi.jonix.huutonet.exception.ProviderTemporarilyDownException;
import fi.jonix.huutonet.market.ListerStrategy;
import fi.jonix.huutonet.market.MarketAdDecorator;
import fi.jonix.huutonet.market.MarketRobot;
import fi.jonix.huutonet.market.OrderFeedback;
import fi.jonix.huutonet.robot.CustomSelenium;
import fi.jonix.huutonet.robot.CustomWebDriver;
import fi.jonix.huutonet.robot.SeleniumRobot;
import fi.jonix.huutonet.tools.Encryption;
import fi.jonix.huutonet.tools.MathUtilities;
import fi.jonix.huutonet.tools.StringUtilities;

@Component(value = "mikkoLister")
public class MikkofiLister extends MarketRobot {
	
	static public String SALES_TYPE_AUCTION = "AUCTION";
	static public String SALES_TYPE_SELL = "SELL";
	
	public static final Logger logger = Logger.getLogger(MikkofiLister.class);
	
	private static String BASE_URL = "http://www.mikko.fi/announcements/management/add-unclassified.action";
	private static DateFormat CLOSING_DATE_FORMAT = new SimpleDateFormat("dd.MM.yyyy");
	private static DateFormat CLOSING_DATE_TIME_FORMAT = new SimpleDateFormat("dd.MM.yyyy HH:mm");
	
	@Autowired
	private SeleniumRobot seleniumRobot;
	
	@Autowired
	private MikkoCategoryHelper mikkoCategoryHelper;

	@Autowired
	private MarketDAO marketDAO;

	@Autowired
	private SellerMarketDAO sellerMarketDAO;

	/*@Override
	public SellerMarket isAlreadyLoggedOnAs() {
		CustomSelenium selenium = seleniumRobot.getSelenium();
		selenium.open("https://www.mikko.fi");
		selenium.waitForPageToLoad();
		if (!selenium.isElementPresent("//a[@id='loginLink']")) {
			// We are already logged on as somebody. Let's figure out as who.
			List<SellerMarket> sellerMarkets = sellerMarketDAO.loadAll();
			String loggedOnUser = selenium.getText("//div[@id='loginAs']/div[2]/strong");
			for (SellerMarket sellerMarket : sellerMarkets) {
				if (loggedOnUser.equals(sellerMarket.getUserName())) {
					return sellerMarket;
				}
			}
			throw new RuntimeException("ERROR: logged on as an unknown user. This should never happen.");
		}
		return null;
	}*/

	@Override
	public void login(SellerMarket sellerMarket) throws MarketTemporarilyDownException, MarketLoggedOnAsWrongUserException {
		logger.debug("Log in to www.mikko.fi as " + sellerMarket.getUserName());
		CustomWebDriver driver = seleniumRobot.getWebDriver();
		try {
			driver.get("http://www.mikko.fi",By.id("loginLink"),2);
			WebElement loginLinkElement = driver.findElementOrNull(By.id("loginLink"));
			if (loginLinkElement == null) {
				// We are already logged on as somebody.
				WebElement userNameElement = driver.findElementOrNull(By.xpath("//div[@id='loginAs']/div[2]/strong"));
				String loggedOnUser = userNameElement.getText();
				if (loggedOnUser.equals(sellerMarket.getUserName()) || loggedOnUser.equals(sellerMarket.getSeller().getSignature())) {
					/* We are already logged on as user sellerMarket. */
					logger.info("Already logged on as " + sellerMarket.getUserName());
					return;
				} else {
					/* We are logged on as somebody else. This can happen for example in the following case:
					 * Lister is running as TeamLauber and then a public question comes for another user,
					 * which requires us to log on as a different user. Don't switch user here (because that
					 * might screw up the other process), but rather throw an exception.
					 * =>UPDATE: I think this should never happen because different SellStar instances use
					 * separate browser sessions. */
					throw new MarketLoggedOnAsWrongUserException("Logged on to Mikko as the wrong user (loggedOnUser = "
							+ loggedOnUser + ", but we SHOULD have been logged on as " + sellerMarket.getUserName()
							+ " (or as " + sellerMarket.getSeller().getSignature() + "))");
				}
			} else {
				//driver.get("http://www.mikko.fi/main/show-login.action?redirectUri=http://www.mikko.fi/main/index.action",
					//	By.id("login"),2);
				loginLinkElement.click();
				try { Thread.sleep(1*1000); } catch (Exception e) { } //give time for javascript to do its things
				driver.waitForElement(By.id("simplemodal-container"));
				try { Thread.sleep(1*1000); } catch (Exception e) { } //give time for javascript to do its things
				driver.switchTo().frame("loginFrame");
				try { Thread.sleep(1*1000); } catch (Exception e) { } //give time for javascript to do its things
				WebElement loginNameElem = driver.findElement(By.id("login"));
				loginNameElem.clear();
				loginNameElem.sendKeys(sellerMarket.getUserName());
				WebElement passwordElem = driver.findElement(By.id("password"));
				passwordElem.clear();
				passwordElem.sendKeys(Encryption.decrypt(sellerMarket.getPassword()));
				passwordElem.submit();
				driver.switchTo().defaultContent(); //switch back to the main frame
			}
		} catch (NoSuchElementException e) {
			logger.debug("Problem logging on to Mikko: ",e);
			throw new MarketTemporarilyDownException(sellerMarket.getMarket().getName() + " temporarily down (tried to logon)");
		} catch (TimeoutException e) {
			logger.debug("Problem logging on to Mikko: ",e);
			throw new MarketTemporarilyDownException(sellerMarket.getMarket().getName() + " temporarily down (tried to logon)");
		} catch (WebDriverException e2) {
			logger.debug("Problem logging on to Mikko: ",e2);
			throw new MarketTemporarilyDownException(sellerMarket.getMarket().getName() + " temporarily down (tried to logon)");
		}
	}
	
	@Override
	public void logout() {
		logger.debug("Logging out from Mikko.");
		CustomSelenium selenium = seleniumRobot.getSelenium();
		try {
			selenium.open("http://www.mikko.fi/account/logout.action");
		} catch (SeleniumException e) {
			logger.warn("Couldn't log out from mikko");
			logger.debug("Couldn't log out from mikko: ", e);
		}
	}

	@Override
	public boolean answerPrivateQuestion(String subject, String question, String answer, String url, SellerMarket sellerMarket) throws LoggedOutFromMarketException, MarketLoggedOnAsWrongUserException {
		logger.debug("Start answering the question");
		CustomSelenium selenium = seleniumRobot.getSelenium();
		selenium.open(url);
		selenium.waitForPageToLoad();
		try { Thread.sleep(2 * 1000); } catch (Exception e) { }
		
		/* Sometimes it doesn't work to first logon and then go to the answer page, so
		 * here let's login again if necessary. */
		String bodyText = selenium.getBodyText();
		if (!selenium.isElementPresent("//textarea[@id='answer']") || bodyText.indexOf("vanhenemaan ja sinun on kirjauduttava uudestaan") > 0) {
			selenium.type("//input[@id='username']", sellerMarket.getUserName());
			selenium.type("//input[@id='password']", Encryption.decrypt(sellerMarket.getPassword()));
			selenium.click("//*[@id='basicForm']/form[1]/fieldset/div[3]/input");
			try { Thread.sleep(1000); } catch (Exception e) { }
			selenium.waitForPageToLoad();
		}
		
		/* Make sure that we are logged on as the correct user. */
		String loggedOnUser = selenium.getText("//div[@id='loginAs']/div[2]/strong");
		if (!loggedOnUser.equals(sellerMarket.getUserName()) && !loggedOnUser.equals(sellerMarket.getSeller().getSignature())) {
			throw new MarketLoggedOnAsWrongUserException("Logged on to Mikko as the wrong user (loggedOnUser = "
					+ loggedOnUser + ", but we SHOULD have been logged on as " + sellerMarket.getUserName()
					+ " (or as " + sellerMarket.getSeller().getSignature() + "))");
		}
		
		selenium.type("//textarea[@id='answer']", answer);
		try { Thread.sleep(1000); } catch (Exception e) { }
		selenium.click("//*[@id='basicFormButtons']/input[1]");
		try { Thread.sleep(1000); } catch (Exception e) { }
		selenium.waitForPageToLoad();
		logger.debug("Done answering the question");
		return true;
	}

	@Override
	public boolean answerPublicQuestion(Ad ad, String question, String answer, String url, SellerMarket sellerMarket) throws LoggedOutFromMarketException, MarketLoggedOnAsWrongUserException {
		logger.debug("Start answering the question");
		CustomSelenium selenium = seleniumRobot.getSelenium();
		selenium.open(url);
		selenium.waitForPageToLoad();
		try { Thread.sleep(2 * 1000); } catch (Exception e) { }
		
		/* Error check. Don't check if sellerMarket.seller = ad.adTemplate.seller, because it's possible
		 * that the adtemplate has been moved to a different seller, and in that case the old seller
		 * should be used. */
		if (!sellerMarket.getMarket().getId().equals(ad.getMarket().getId())) {
			throw new RuntimeException("Internal error in MikkofiLister.answerPublicQuestion");
		}
		
		/* Sometimes it doesn't work to first logon and then go to the answer page, so
		 * here let's login again if necessary. */
		/*if (selenium.getBodyText().indexOf("ksesi ilmoittajalta sinun tulee olla kirjautunut palveluun") > 0
				&& selenium.isElementPresent("//a[@id='loginLink']")) {
			Market market = marketDAO.getByName(Market.MIKKO_MARKET_NAME);
			SellerMarket sellerMarket = sellerMarketDAO.getBySellerAndMarket(ad.getAdTemplate().getSeller(), market);
			selenium.click("//a[@id='loginLink']");
			selenium.type("//input[@id='login']", sellerMarket.getUserName());
			selenium.type("//input[@id='password']", Encryption.decrypt(sellerMarket.getPassword()));
			selenium.click("//form[@id='newLogin']//input[@value='Kirjaudu']");
			Thread.sleep(1000);
			selenium.waitForPageToLoad();
		}*/
		
		try { Thread.sleep(3*1000); } catch (Exception e) { } //delay so that the java scripts of the page have time to do their things
		
		/* Make sure that we aren't logged out. */
		if (!selenium.isElementPresent("//a[@id='registerAndLogout']/span/a")) {
			throw new LoggedOutFromMarketException();
		}
		
		/* Make sure that we are logged on as the correct user. */
		String loggedOnUser = selenium.getText("//div[@id='loginAs']/div[2]/strong");
		logger.debug("loggedOnUser = " + loggedOnUser);
		if (!loggedOnUser.equals(sellerMarket.getUserName())) {
			throw new MarketLoggedOnAsWrongUserException("Logged on to Mikko as " + loggedOnUser + " although we SHOULD have been logged on as " + sellerMarket.getUserName());
		}
		
		boolean answered = false;
		String sloppyQuestion = StringUtilities.replaceNewLinesWithSpace(StringUtilities.removeAllExceptAZLettersAndDigits(question)).trim();
		int rounds = 0;
		while (true) {
			int i = 1;
			while (selenium.isElementPresent("//*[@id='itemComments']/tbody/tr[" + i + "]/td[1]")) {
				String pageQuestion = selenium.getText("//*[@id='itemComments']/tbody/tr[" + i + "]/td[1]");
				logger.debug("pageQuestion = " + pageQuestion);
				String sloppyPageQuestion = StringUtilities.replaceNewLinesWithSpace(StringUtilities.removeAllExceptAZLettersAndDigits(pageQuestion)).trim();
				if (sloppyPageQuestion.trim().endsWith(sloppyQuestion.trim())) {
					selenium.click("//*[@id='itemComments']/tbody/tr[" + i + "]/td[2]/a");
					try { Thread.sleep(500); } catch (Exception e) { }
					selenium.type("//*[@id='itemComments']/tbody/tr[" + (i+1) + "]/td[1]/textarea", answer);
					selenium.click("//*[@id='itemComments']/tbody/tr[" + (i+1) + "]/td[1]/input[1]");
					try { Thread.sleep(1000); } catch (Exception e) { }
					answered = true;
					break;
				}
				i += 2;
			}
			if (answered || rounds > 0) {
				break;
			}
			rounds++;
			/* Sometimes the questions don't load properly so try loading the page again. */
			selenium.open(url);
			selenium.waitForPageToLoad();
			try { Thread.sleep(3 * 1000); } catch (Exception e) { }
		}
		if (answered) {
			logger.debug("Done answering the public question for mikko.");
		} else {
			logger.info("Failed to answer the public questioon for mikko.");
		}
		return answered;
	}

	@Override
	public boolean listItemForSale(Ad ad, ListerStrategy strategy, Map<String,Integer> nonExistentCategories,
			SellerMarket sellerMarket, TreeMap<Long, List<Long>> preFilledScoreMap) throws Exception {
		/* First check if this is a non existing category that has already been tried
		 * during this listing round. If so then abort right away before even logging in. */
		Category category = ad.getMarketCategory();
		Category subCategory = category.getParentCategory();
		Category mainCategory = subCategory.getParentCategory();
		String categoryIdentification = Market.MIKKO_MARKET_NAME + ": ";
		if (mainCategory.getParentCategory() != null) {
			categoryIdentification += mainCategory.getMarketSpecId() + "/" + subCategory.getMarketSpecId()
				+ "/" + category.getMarketSpecId() + " (" + mainCategory.getName() + "/" + subCategory.getName() + "/" + category.getName() + ")";
		} else {
			categoryIdentification += subCategory.getMarketSpecId() + "/" + category.getMarketSpecId()
				+ " (" + subCategory.getName() + "/" + category.getName() + ")";
		}
		if (nonExistentCategories.get(categoryIdentification) != null) {
			//this category has already failed during this listing run, so don't try it again
			logger.debug("this category has already failed during this listing run, so don't try it again");
			Integer count = nonExistentCategories.get(categoryIdentification);
			nonExistentCategories.put(categoryIdentification,count.intValue()+1);
			throw new CategoryNotFoundException(categoryIdentification);
		}

		MarketAdDecorator marketAdDecorator = (MarketAdDecorator) ApplicationContextPlaceholder.applicationContext.getBean(sellerMarket.getAdDecorator());
		ad.setUsedBeanName(ad.getAdTemplate().getBeanName());
		ad.setUsedBeanVersion(marketAdDecorator.getBeanVersion());
		String description = marketAdDecorator.getFormatedBody(ad, sellerMarket, true, preFilledScoreMap);
		CustomWebDriver driver = seleniumRobot.getWebDriver();
		CustomSelenium selenium = seleniumRobot.getSelenium();
		try {
			selenium.open(BASE_URL);
			selenium.waitForPageToLoad();
	
			/* Sometimes it doesn't work to first logon and then go to the answer page, so
			 * here let's login again if necessary. */
			/*if (selenium.getBodyText().indexOf("vanhenemaan ja sinun on kirjauduttava uudestaan") > 0) {
				selenium.type("//input[@id='username']", sellerMarket.getUserName());
				selenium.type("//input[@id='password']", Encryption.decrypt(sellerMarket.getPassword()));
				selenium.click("//*[@id='basicForm']/form[1]/fieldset/div[3]/input");
				Thread.sleep(1000);
				selenium.waitForPageToLoad();
			}*/
	
			/* Make sure that we aren't logged out. We can be logged out when we get here, for example in the following
			 * case: One process is fetching a public question from mikko, then listing starts, the public question fetching
			 * process logs out and then finally we get here. */
			String bodyText = selenium.getBodyText();
			if (bodyText.indexOf("vanhenemaan ja sinun on kirjauduttava uudestaan") > 0) {
				throw new LoggedOutFromMarketException();
			}
			
			Thread.sleep(3*1000); //delay so that the java scripts of the page have time to do their things
			
			/* Make sure that we are logged on as the correct user. */
			String htmlSource = selenium.getHtmlSource();
			if (htmlSource.indexOf(sellerMarket.getSeller().getSystemEmail().getEmailAddress()) < 0) {
				logger.debug("bodyText = \"" + bodyText + "\"");
				logger.debug("htmlSource = \"" + htmlSource + "\"");
				throw new MarketLoggedOnAsWrongUserException("Logged on to Mikko as the wrong user (we SHOULD have been logged on as " + sellerMarket.getUserName() + ")");
			}
			
			/* In some cases the body text and html text doesn't seem to be in sync (might be a bug in selenium that causes this). */
			if (bodyText.indexOf(sellerMarket.getSeller().getSystemEmail().getEmailAddress()) < 0) {
				logger.debug("bodyText = \"" + bodyText + "\"");
				logger.debug("htmlSource = \"" + htmlSource + "\"");
				logger.info("Body text doesn't contain seller's email.");
			}
			
			logger.debug("Trying first auction type");
			selenium.select("//select[@id='tradeType']", "value=" + SALES_TYPE_AUCTION);
			
			/* Set the categories. */
			try {
				driver.waitForElement(By.id("mainCategoryId"));
				Thread.sleep(2000); //wait for javascript
				if (mainCategory.getParentCategory() != null) {
					//logger.debug("1Setting main category: " + mainCategory.getMarketSpecId());
					selenium.select("//select[@id='mainCategoryId']", "value=" + mainCategory.getMarketSpecId());
					driver.waitForElement(By.id("category1Id"));
					Thread.sleep(2000);
					
					//logger.debug("1Setting sub category: " + subCategory.getMarketSpecId());
					selenium.select("//select[@id='category1Id']", "value=" + subCategory.getMarketSpecId());
					driver.waitForElement(By.id("category2Id"));
					Thread.sleep(2000);
	
					//logger.debug("1Setting sub sub category: " + category.getMarketSpecId());
					selenium.select("//select[@id='category2Id']", "value=" + category.getMarketSpecId());
				} else {
					//logger.debug("2Setting main category: " + subCategory.getMarketSpecId());
					selenium.select("//select[@id='mainCategoryId']", "value=" + subCategory.getMarketSpecId());
					driver.waitForElement(By.id("category1Id"));
					Thread.sleep(2000);
	
					//logger.debug("2Setting sub category: " + category.getMarketSpecId());
					selenium.select("//select[@id='category1Id']", "value=" + category.getMarketSpecId());
				}
			} catch (Exception e) {
				logger.debug("this category failed for the first time during this listing run");
				nonExistentCategories.put(categoryIdentification,1);
				throw new CategoryNotFoundException(categoryIdentification);
			}
			//here we need a longer delay, because here javascript changes the page
			driver.waitForElement(By.id("startingPrice"));
			Thread.sleep(3000);
	
			//logger.debug("Setting headline");
			selenium.type("//input[@id='annoHeader']", ad.getAdTemplate().getHeadline());
			//Thread.sleep(1000);
	
			//logger.debug("Setting location");
			String location = strategy.getLocation(ad);
			if (!location.equals("Hong Kong")) {
				throw new RuntimeException("Unsupported location in MikkofiLister.");
			} else {
				location = "HK";
			}
			selenium.select("//select[@id='countrySelect']", "value=" + location);
			////Thread.sleep(1000);
			
			/* Entering the description using sendKeys takes forever since it's done key-by-key, so
			 * here we set the value using Javascript instead. */
			//selenium.type("//textarea[@id='descriptionTextArea']", description);
			WebElement textArea = driver.findElement(By.xpath("//textarea[@id='descriptionTextArea']"));
			textArea.clear();
			driver.setValueWithJavascript(textArea, description);

			/* For Mikko it's possible to set a bid with the same price as the starting price, so therefore we cannot
			 * set the starting price to ad.getStartPriceInMarketCurrency() like at Huutonet. Rather we need to set
			 * it to the same as the price reservation. */
			//selenium.type("//input[@id='startingPrice']", MathUtilities.roundTo2Decimals(ad.getStartPriceInMarketCurrency()).replace(".",","));
			selenium.type("//input[@id='startingPrice']", MathUtilities.roundTo2Decimals(ad.getMinSellingPriceInMarketCurrencyInclVat()).replace(".",","));
	
			selenium.type("//input[@id='minimumRaise']", MathUtilities.roundTo2Decimals(sellerMarket.getMinBidIncreaseInMarketCurrencyInclVat()).replace(".",","));
	
			selenium.type("//input[@id='priceReservation']", MathUtilities.roundTo2Decimals(ad.getMinSellingPriceInMarketCurrencyInclVat()).replace(".",","));
	
			selenium.type("//input[@id='buyoutPrice']", MathUtilities.roundTo2Decimals(ad.getBuyOutPriceInMarketCurrencyInclVat()).replace(".",","));
	
			//logger.debug("Setting open days");
			String dateString = selenium.getValue("//input[@id='startDate']");
			//logger.debug("dateString = " + dateString);
			Date dateClosing = CLOSING_DATE_FORMAT.parse(dateString);
			Calendar cal = Calendar.getInstance();
			cal.setTime(dateClosing);
			cal.add(Calendar.DATE,ad.getOpenDays());
			String dateClosingString = CLOSING_DATE_FORMAT.format(cal.getTime());
			selenium.type("//input[@id='endDate']", dateClosingString);
			//Thread.sleep(1000);
			//String val = selenium.getSelectedValue("//select[@id='endTime']");
			//logger.debug("val = " + val);
			String closingTime = selenium.getSelectedLabel("//select[@id='endTime']");
			logger.debug("closingTime = " + closingTime);
			ad.setDateClosing(CLOSING_DATE_TIME_FORMAT.parse(dateClosingString + " " + closingTime));
			ad.setDateToMarket(new Date());
	
			/* Upload the images. For image uploading we need to resort to a hack since it's
			 * not possible to directly use the file chooser of the operating system: if
			 * we put the name of the file in the "//input[@name='image_file']" tag, then
			 * ajax will load the file to the server. However with selenium-2 we cannot write
			 * directly to this tag since it's hidden, so therefore we first need to make it
			 * visible using javascript. */
	        String showImageFileElementScript = "function showImageFileElementHack() {"
	        	+ "  var testObj = document.getElementsByName('image_file');"
	        	+ "  for (var i = 0; i < testObj.length; i++) {"
	        	+ "    testObj[i].style.opacity = 30;"
	        	+ "    testObj[i].style.display = '';"
	        	+ "  }"
	        	+ "} showImageFileElementHack();";
			List<Image> imageList = ad.getAdTemplate().getProduct().getImagesInOrder();
			boolean imageUploaded = false;
			int i = 0;
			for (Image image : imageList) {
				if (image.getThumbnailImageId() != null) { //don't upload thumbnails
					if (i > 0) {
				    	try { Thread.sleep(500); } catch (Exception e) { }
				    	int j = 0;
				        while (!driver.isElementPresent(By.name("image_file"))) {
				        	try { Thread.sleep(500); } catch (Exception e) { }
				        	j++;
				        	if (j > 10) {
				        		break;
				        	}
				        }
					}
			        driver.executeScript(showImageFileElementScript);
			        try { Thread.sleep(500); } catch (Exception e) { }
					WebElement imageFile = driver.findElement(By.name("image_file"));
					imageFile.clear();
					imageFile.sendKeys(image.getLocalFileUrl());
					i++;
					imageUploaded = true;
				}
			}
			if (!imageUploaded) {
				/* If there are only thumbnails, then use those. */
				i = 0;
				for (Image image : imageList) {
					//selenium.type("//input[@name='image_file']", image.getLocalFileUrl());
					if (i > 0) {
				    	try { Thread.sleep(500); } catch (Exception e) { }
				    	int j = 0;
				        while (!driver.isElementPresent(By.name("image_file"))) {
				        	try { Thread.sleep(500); } catch (Exception e) { }
				        	j++;
				        	if (j > 10) {
				        		break;
				        	}
				        }
					}
			        driver.executeScript(showImageFileElementScript);
			        try { Thread.sleep(500); } catch (Exception e) { }
					WebElement imageFile = driver.findElement(By.name("image_file"));
					imageFile.clear();
					imageFile.sendKeys(image.getLocalFileUrl());
					i++;
				}
			}
			//wait a little for the uploading of pictures (this might not even be necessary)
	        try { Thread.sleep(3*1000); } catch (Exception e) { }
			
			/* Add link for buying this product from the web store. */
	        //int nbrAddedLinks = 0;
			selenium.click("//input[@id='add_link_button']");
			Thread.sleep(1000); //wait for javascript
			selenium.type("//input[@id='link_url']", marketAdDecorator.getWebStorePlainURL(ad));
			//selenium.type("//input[@id='link_title']", "Tilaa tämä tuote verkkokaupastamme");
			selenium.type("//input[@id='link_title']", "Tilaa tuote suoraan verkkokaupastamme");
			selenium.click("//input[@id='save_link_button']");
			//while (driver.findElements(By.xpath("//*[@id='addedLinks']/tbody/tr")).size() <= nbrAddedLinks) {
			//}
			Thread.sleep(1000); //wait for javascript
			
			/* Add links to other interesting products. */
			List<AdTemplate> addedAdTemplates = new ArrayList<AdTemplate>();
			addedAdTemplates.add(ad.getAdTemplate());
			List<Ad> adList = marketAdDecorator.getLinksToOtherAds(ad,addedAdTemplates);
			if (adList != null) {
				for (Ad adItem : adList) {
					selenium.click("//input[@id='add_link_button']");
					/*WebElement saveButton = driver.findElement(By.id("save_link_button"));
					int j = 0;
					while (!saveButton.isDisplayed()) {
						Thread.sleep(100);
						j++;
						if (j > 15) {
							break;
						}
					}*/
					//Thread.sleep(1000); //wait for javascript
					driver.waitForElement(By.id("save_link_button"));
					//selenium.type("//input[@id='link_url']", "http://www.mikko.fi/kohde/" + adItem.getMarketSalesId());
					selenium.type("//input[@id='link_url']", marketAdDecorator.getBounceToMarketURL(adItem));
					selenium.type("//input[@id='link_title']", adItem.getAdTemplate().getHeadline());
					selenium.click("//input[@id='save_link_button']");
					Thread.sleep(1000); //wait for javascript
				}
			}
			
			//logger.debug("Setting shipping way");
			//selenium.click("//input[@name='deliveryMethods.postDelivery' and @value='true']");
			selenium.click("//input[@id='post_packet' and @value='true']");
			logger.debug("shipping cost = " + ad.getShippingCostInMarketCurrencyInclVat().toPlainString().replace(".",","));
			selenium.type("//input[@id='packet_price']", MathUtilities.roundTo2Decimals(ad.getShippingCostInMarketCurrencyInclVat()).replace(".",","));
		} catch (WebDriverException e) {
			logger.debug("Listing problem (doesn't require any special action): ",e);
			throw new ListingException("Listing failed for some reason", false);
		}

		logger.debug("Publishing");
		//Thread.sleep(1000);
		//selenium.click("//*[@src='/images/announcement/button_publish.gif']"); //there are two of these, so I don't know if this one can be used
		selenium.click("//*[@id='announcementForm']/div[2]/input[4]");
		//logger.debug("Efter click");
		//Thread.sleep(1000);
		selenium.waitForPageToLoad();
		//logger.debug(selenium.getBodyText());
		if (selenium.getBodyText().indexOf("Kiitos, ilmoituksesi on julkaistu onnistuneesti.") > 0) {
			logger.debug("Listing succeeded");
			String marketIdStr = selenium.getText("//*[@id='summary_wrapper']/div[2]/div[2]");
			//logger.debug("marketIdStr = " + marketIdStr);
			int index = marketIdStr.lastIndexOf("(");
			String marketId = marketIdStr.substring(index+1);
			//logger.debug("marketId = " + marketId);
			marketId = marketId.substring(0,marketId.length()-2);
			logger.debug("marketId = " + marketId);
			//Thread.sleep(1000);
			ad.setMarketSalesId(marketId);
			return true;
		} else {
			logger.info("Listing failed");
			//logger.debug("bodyText = " + selenium.getBodyText());
			//logger.debug("htmlSource = " + selenium.getHtmlSource());
			return false;
		}
	}

	@Override
	public boolean canHandleMarket(Market market) {
		return market.getName().equals(Market.MIKKO_MARKET_NAME);
	}

	@Override
	public void givePositiveFeedback(List<Order> sameAdOrders, SellerMarket sellerMarket) throws LoggedOutFromMarketException, MarketLoggedOnAsWrongUserException {
		//TODO: implement giving of feedback for mikko
		//return true;
	}

	@Override
	public List<OrderFeedback> readFeedback(SellerMarket sellerMarket) {
		//TODO: implement reading of feedback for mikko
		//NOTE: when this is done for mikko, then IF mikko requires a logon in order
		//to access the feedback, then we need to make sure that it bails out if we aren't
		//logged on or if we are logged on as the wrong user.
		return new ArrayList<OrderFeedback>();
	}

	@Override
	public List<Category> getCategories(SellerMarket sellerMarket) throws LoggedOutFromMarketException, MarketLoggedOnAsWrongUserException {
		return mikkoCategoryHelper.getCategories(sellerMarket);
	}

	@Override
	public String getMarketName() {
		return Market.MIKKO_MARKET_NAME;
	}

	@Override
	public String getUrlForMarketSalesId(String marketSalesId) {
		return "http://www.mikko.fi/kohde/" + marketSalesId;
	}

	@Override
	public void closeAdAtMarket(Ad adToRemove) {
		throw new UnsupportedOperationException("This method isn't yet implemented");
	}

	@Override
	public void payForHighlightedAds(List<Ad> highlightedAds, CriticalSectionInfo section) {
		throw new UnsupportedOperationException("This method isn't yet implemented");
	}

}
