package fi.jonix.huutonet.market.huuto;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.xml.bind.JAXB;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.transform.stream.StreamSource;

import org.apache.commons.lang.CharSet;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.FileEntity;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.openqa.selenium.Alert;
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.Select;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.sun.xml.bind.marshaller.NamespacePrefixMapper;
import com.thoughtworks.selenium.SeleniumException;

import fi.jonix.huutonet.domain.ApplicationContextPlaceholder;
import fi.jonix.huutonet.domain.VirtualCoin;
import fi.jonix.huutonet.domain.model.Ad;
import fi.jonix.huutonet.domain.model.Category;
import fi.jonix.huutonet.domain.model.Currency;
import fi.jonix.huutonet.domain.model.Image;
import fi.jonix.huutonet.domain.model.Market;
import fi.jonix.huutonet.domain.model.Order;
import fi.jonix.huutonet.domain.model.SellerMarket;
import fi.jonix.huutonet.domain.model.dao.SellerMarketDAO;
import fi.jonix.huutonet.email.EmailSender;
import fi.jonix.huutonet.email.processor.EmailFilter;
import fi.jonix.huutonet.exception.CategoryNotFoundException;
import fi.jonix.huutonet.exception.CriticalSectionInfo;
import fi.jonix.huutonet.exception.ListingException;
import fi.jonix.huutonet.exception.LoggedOutFromMarketException;
import fi.jonix.huutonet.exception.MarketLoggedOnAsWrongUserException;
import fi.jonix.huutonet.exception.MarketTemporarilyDownException;
import fi.jonix.huutonet.exception.SellStarRuntimeException;
import fi.jonix.huutonet.market.ListerStrategy;
import fi.jonix.huutonet.market.MarketAdDecorator;
import fi.jonix.huutonet.market.MarketRobot;
import fi.jonix.huutonet.market.OrderFeedback;
import fi.jonix.huutonet.market.huuto.xml.ad09beta.BuyNowPrice;
import fi.jonix.huutonet.market.huuto.xml.ad09beta.BuyerRequirements;
import fi.jonix.huutonet.market.huuto.xml.ad09beta.Condition;
import fi.jonix.huutonet.market.huuto.xml.ad09beta.Content;
import fi.jonix.huutonet.market.huuto.xml.ad09beta.CurrentPrice;
import fi.jonix.huutonet.market.huuto.xml.ad09beta.DeliveryMethod;
import fi.jonix.huutonet.market.huuto.xml.ad09beta.DeliveryMethods;
import fi.jonix.huutonet.market.huuto.xml.ad09beta.Entry;
import fi.jonix.huutonet.market.huuto.xml.ad09beta.Intention;
import fi.jonix.huutonet.market.huuto.xml.ad09beta.Location;
import fi.jonix.huutonet.market.huuto.xml.ad09beta.MinimumIncrease;
import fi.jonix.huutonet.market.huuto.xml.ad09beta.PaymentMethod;
import fi.jonix.huutonet.market.huuto.xml.ad09beta.PaymentMethods;
import fi.jonix.huutonet.market.huuto.xml.ad09beta.Price;
import fi.jonix.huutonet.market.huuto.xml.ad09beta.StartingPrice;
import fi.jonix.huutonet.robot.CustomSelenium;
import fi.jonix.huutonet.robot.CustomWebDriver;
import fi.jonix.huutonet.robot.SeleniumRobot;
import fi.jonix.huutonet.tools.CurrencyFormatter;
import fi.jonix.huutonet.tools.Encryption;
import fi.jonix.huutonet.tools.MathUtilities;
import fi.jonix.huutonet.tools.Statics;
import fi.jonix.huutonet.tools.StringUtilities;

/**
 * This class is the new listing form of Huutonet.
 */
@Component(value = "huutonetLister")
public class HuutonetLister2 extends MarketRobot {

	public static final Logger logger = Logger.getLogger(HuutonetLister2.class);

	static public int SALES_TYPE_AUCTION = 1;
	static public int SALES_TYPE_AUCTION_OR_BUY = 2;
	static public int SALES_TYPE_BUY = 3;

	static private String CLASSIFICATION_NEW = "Uusi";
	static private int ACCOUNT_TRANSFER = 1;
	static private int NET_PAYMENT = 5;
	static private int PAYSON_SECURITY = 6;
	static private int DELIVERY_STYLE_POSTI = 1;

	static public int SELENIUM_LISTING = 1;
	static public int API_LISTING = 2;
	
	/* Workaround for a Huutonet bug is needed in some situations. */
	public static String HUUTONET_BUG_EXTRA = "0.05";

	public static DateFormat CLOSING_DATE_FORMAT = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
	
	@Autowired
	private SeleniumRobot seleniumRobot;
	
	@Autowired
	private HuutonetFeedbackGiver feedbackGiver;
	
	@Autowired
	private HuutonetReadFeedback huutonetFeedbackReader;
	
	@Autowired
	private HuutonetAnswerer huutonetAnswerer;

	@Autowired
	private SellerMarketDAO sellerMarketDAO;

	@Override
	public void closeAdAtMarket(Ad adToRemove) {
		logger.debug("Attempting to automatically close ad " + adToRemove.getId() + ".");
		SellerMarket sellerMarket = sellerMarketDAO.getBySellerAndMarket(adToRemove.getAdTemplate().getSeller(),adToRemove.getMarket());
		DefaultHttpClient client = new DefaultHttpClient();
		HttpDelete delete = null;
		try {
			//delete = new HttpDelete("http://api.huuto.net/somt/0.9-beta/items/" + adToRemove.getMarketSalesId());
			delete = new HttpDelete("https://salattu.huuto.net/somt/0.9-beta/items/" + adToRemove.getMarketSalesId());
			client.getCredentialsProvider().setCredentials(
					//new AuthScope("api.huuto.net", 80),
					new AuthScope("salattu.huuto.net", 443),
					//new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT),
					//AuthScope.ANY,
					new UsernamePasswordCredentials(sellerMarket.getUserName(), Encryption.decrypt(sellerMarket.getPassword())));
			logger.debug("Calling huutonet API service (closing listed ad).");
			HttpResponse resp = client.execute(delete);
			logger.debug("Returned from call of huutonet API service (closing listed ad).");
			StatusLine statusLine = resp.getStatusLine();
			logger.debug("statusLine = " + statusLine);
			Header[] headers = resp.getAllHeaders();
			/*for (int i = 0; i < headers.length; i++) {
				logger.debug("headers[" + i + "] = " + headers[i]);
			}*/
			HttpEntity respEntity = resp.getEntity();
			//String body = EntityUtils.toString(respEntity);
			//logger.debug("body = " + body);

			if (statusLine.getStatusCode() != 204 || !statusLine.getReasonPhrase().equals("No Content")) {
				logger.info("Problem closing ad: statusLine.reasonPhrase = " + statusLine.getReasonPhrase()
						+ ", statusCode = " + statusLine.getStatusCode());
				if (statusLine.getReasonPhrase().equals("Service Unavailable")) {
					throw new MarketTemporarilyDownException(sellerMarket.getMarket().getName() + " temporarily down (maintenance break)");
				} else if (statusLine.getReasonPhrase().equals("Unauthorized")) {
					logger.error("Faulty username or password when adding image to listed item to Huutonet (unauthorized).");
					throw new LoggedOutFromMarketException();
				} else {
					logger.error("Unknown problem closing ad " + adToRemove.getMarketSalesId()/* + ": " + body*/);
					throw new RuntimeException("Unknown problem closing ad " + adToRemove.getMarketSalesId());
				}
			}

			EntityUtils.consume(respEntity);
			logger.info("Ad " + adToRemove.getId() + " was automatically closed using API.");
		} catch (Exception e) {
			if (delete != null) {
				delete.abort();
			}
			logger.info("Failed to automatically close ad " + adToRemove.getId() + " (marketSalesId = "
					+ adToRemove.getMarketSalesId() + ") at Huutonet.");
			logger.debug("Failed to automatically close ad " + adToRemove.getId() + " (marketSalesId = "
					+ adToRemove.getMarketSalesId() + ") at Huutonet.",e);
			EmailSender.sendOperationProblemReport("Automatic closing of ad " + adToRemove.getId() + " (marketSalesId = "
					+ adToRemove.getMarketSalesId() + ") failed",
					"Automatic closing of ad failed (" + e.getMessage() + ")."
					+ " Please close it by hand.",
					adToRemove.getAdTemplate().getProduct().getProvider().getSystemEmail());
		} finally {
			// Shut down connection manager to immediately deallocate all system resources
			client.getConnectionManager().shutdown();
		}
	}
	
	@Override
	public void payForHighlightedAds(List<Ad> highlightedAds, CriticalSectionInfo section) {
		Date startDate = new Date();
		/* TODO: possibly we could ad an extra check in this method that makes sure that we don't
		 * buy the same product twice. This could be done by checking the following xpath:
		 * driver.findElements(By.xpath("//*[@class='productNotice']"). The values to check for:
		 * first element (display list): "Osastolistan 1. sija nyt" => not bought
		 * first element (display list): "Arvoa asetettu yht." => already bought
		 * second element (image): "Ominaisuus on automaattisesti" => not necessary (nor even possible)
		 *   to buy because we already have this one automatically
		 * third element (background color): "Olet jo ostanut" => already bought
		 * third element (background color): "" (empty) => not bought
		 * fourth element (highlighted headline): "Olet jo ostanut" => already bought
		 * fourth element (highlighted headline): "" (empty) => not bought
		 * fifth element (main section display window): => unkown because I haven't tried this one
		 * sixth element (front page display window): => unkown because I haven't tried this one
		 * => However this kind of extra check isn't necessarily needed because:
		 * 1.) database transactions make sure that the database is consistent
		 * 2.) it's not even possible to buy the same highlighting product twice since the
		 *     checkboxes are disabled for already bought products
		 * => There is actually one case that can possibly cause problems: for the display window
		 *    product it's possible to add MORE money to get higher up. If the transaction took so
		 *    long that the actual payment went through but the transaction rolled back, then in
		 *    the next run we'll try to buy the same product again (and it might actually succeed).
		 *    However in that case sellstar will go to the error state and in that case we need to
		 *    carefully check if the transaction went through or not. 
		 */
		
		/* First make a safety check. */
		//BigDecimal totalCostEuroInclVat = BigDecimal.ZERO;
		for (Ad ad : highlightedAds) {
			//totalCostEuroInclVat = totalCostEuroInclVat.add(ad.getAdvertisementCostInEuroInclVat());
			if (ad.getAdvertisementCostInEuroExclVat().compareTo(BigDecimal.ZERO) != 0
					|| ad.getAdvertisementCostInEuroInclVat().compareTo(BigDecimal.ZERO) != 0) {
				throw new SellStarRuntimeException("Ad " + ad.getId() + " has non-zero advertisement cost already.", null, 1, null);
			}
			logger.debug("Highlighting ad " + ad.getId());
		}
		if (/*totalCostEuroInclVat.compareTo(BigDecimal.ZERO) == 0 || */highlightedAds.size() == 0) {
			logger.warn("No products to highlight.");
			throw new SellStarRuntimeException("No products to highlight.", null, 1, null);
		}

		//TODO: don't hardcode "23%" here. Rather create a market specific vatPerc field that is used when buying things
		//from the market itself.
		String expectedVatPercentage = "23%";

		CustomWebDriver driver = seleniumRobot.getWebDriver();
		try {
			/* Load the cart page. */
			driver.get("https://salattu.huuto.net/omasivu/maksut/cart");
			try {
				driver.waitForElement(By.xpath("//*[@id='products']")); //wait for page to load
			} catch (TimeoutException te) {
				logger.debug("Product list not found, maybe it's empty.");
			}
			//driver.waitForElement(By.xpath("//*[@id='products']/table/tfoot/tr/td[5]/strong")); //wait for page to load

			/* Bail out if the cart is empty. */
			WebElement emptyCartWarning = driver.findElementOrNull(By.xpath("//*[@id='products']"));
			if ((emptyCartWarning != null && emptyCartWarning.getText().length() < 30
					&& emptyCartWarning.getText().indexOf("i tuotteita") >= 0)
					|| driver.getPageSource().indexOf("Sinulla ei ole maksamattomia") >= 0) {
				logger.info("Products found to highlight but cart is empty.");
				//throw new RuntimeException("Products found to highlight but cart is empty.");
				for (Ad ad : highlightedAds) {
					logger.debug("ad " + ad.getId() + " was not found in the cart, adding.");
					driver.get(getUrlForMarketSalesId(ad.getMarketSalesId()));
					addToDisplayWindow(ad,true);
				}
				driver.get("https://salattu.huuto.net/omasivu/maksut/cart");
				driver.waitForElement(By.xpath("//*[@id='products']")); //wait for page to load
			}

			/* Make sure that the list contains all expected ads and no unexpected ads. */
			Map<Long,String> ids = null;
			for (int j = 0; j < 2; j++) {
				ids = new HashMap<Long,String>();
				int counter = 0;
				WebElement adElement = driver.findElementOrNull(By.xpath("//*[@id='products']/table/tbody/tr[" + (counter+1) + "]/td[2]/a"));
				while (adElement != null) {
					/* Safety check: make sure the expected vat percentage is used. */
					WebElement vatPercElement = driver.findElement(By.xpath("//*[@id='products']/table/tbody/tr[" + (counter+1) + "]/td[3]"));
					if (!vatPercElement.getText().equals(expectedVatPercentage)) {
						throw new SellStarRuntimeException("Expected vat percentage: " + expectedVatPercentage
								+ ", found vat percentage: " + vatPercElement.getText(), null, 1, null);
					}

					/* Find out the price. */
					WebElement vatCostElement = driver.findElement(By.xpath("//*[@id='products']/table/tbody/tr[" + (counter+1) + "]/td[4]"));
					BigDecimal vatCost = new BigDecimal(vatCostElement.getText().replaceAll(" €","").replaceAll(",", "."));
					WebElement costInclVatElement = driver.findElement(By.xpath("//*[@id='products']/table/tbody/tr[" + (counter+1) + "]/td[5]"));
					BigDecimal costInclVat = new BigDecimal(costInclVatElement.getText().replaceAll(" €","").replaceAll(",", ".")).negate();
					// Make sure that the signs are correct
					if (vatCost.compareTo(BigDecimal.ZERO) < 0 || costInclVat.compareTo(BigDecimal.ZERO) < 0) {
						throw new SellStarRuntimeException("Wrong signs: vatCost = " + vatCost.toPlainString()
								+ ", costInclVat = " + costInclVat.toPlainString(), null, 1, null);
					}
					BigDecimal costExclVat = costInclVat.subtract(vatCost);

					String href = adElement.getAttribute("href");
					String marketSalesId = href.substring(href.lastIndexOf("/")+1);
					logger.debug("marketSalesId " + marketSalesId + " found in cart");
					boolean found = false;
					for (Ad ad : highlightedAds) {
						if (ad.getMarketSalesId().equals(marketSalesId)) {
							if (ids.get(ad.getId()) != null) {
								//This can actually happen if we have ordered several highlighting
								//products (i.e. visibilityType >= 10), because in that case we'll get one
								//row in the cart for each highlighting product.
								//TODO: make sure that this works also if visibilityType >= 10
								logger.info("marketSalesId " + marketSalesId + " (ad.id = " + ad.getId()
										+ ") was found multiple times in cart.");
								throw new SellStarRuntimeException("marketSalesId " + marketSalesId + " (ad.id = " + ad.getId()
										+ ") was found multiple times in cart.", null, 1, null);
							}
							ids.put(ad.getId(), "");
							logger.debug("marketSalesId " + marketSalesId + " belongs to ad " + ad.getId());

							// Update the ad with the correct price.
							ad.setAdvertisementCostInEuroExclVat(costExclVat);
							ad.setAdvertisementCostInEuroInclVat(costInclVat);
							found = true;
							break;
						}
					}
					if (!found) {
						// If this one comes now and then for natural reasons, then we could maybe add an automatic
						// removal of these ones. => In fact this CAN come sometimes for natural reasons: we only
						// pay for such ads that are confirmed. Marking for highlighting (=adding to cart) is done
						// at listing time but confirmation is done when an email comes, so it's possible that some
						// ad that was put in the cart isn't yet confirmed when we call this method.
						logger.info("marketSalesId " + marketSalesId + " was found in cart but not included in the list.");
						throw new SellStarRuntimeException("marketSalesId " + marketSalesId + " was found in cart but not included in the list.", null, 1, null);
					}
					counter++;
					adElement = driver.findElementOrNull(By.xpath("//*[@id='products']/table/tbody/tr[" + (counter+1) + "]/td[2]/a"));
				}
				
				/* Add ads missing from cart. */
				boolean hadToAdd = false;
				if (highlightedAds.size() > ids.size()) {
					for (Ad ad : highlightedAds) {
						if (ids.get(ad.getId()) == null) {
							hadToAdd = true;
							logger.debug("ad " + ad.getId() + " was found in list but not in cart. Adding this one to cart.");
							driver.get(getUrlForMarketSalesId(ad.getMarketSalesId()));
							addToDisplayWindow(ad,true);
							ids.put(ad.getId(), "");
						}
					}
				}
				logger.debug("Round " + (j+1) + " gone through.");
				if (!hadToAdd) {
					logger.debug("Successfully gone through the cart without finding errors.");
					break;
				} else if (j == 1) {
					logger.debug("Had to manually add ads on round " + (j+1) + ".");
					/* If we had to manually add ads also on the second run, then clear
					 * the ids hashmap to make sure that the validation after the loop fails. */
					ids.clear();
				}
			}
			
			if (highlightedAds.size() != ids.size()) {
				logger.info("The ads in SellStar marked for highlighting doesn't correspond to"
						+ " the ads in the cart (highlightedAds.size = " + highlightedAds.size() + ", ids.size = " + ids.size() + ").");
				throw new SellStarRuntimeException("The ads in SellStar marked for highlighting doesn't correspond to"
						+ " the ads in the cart.", null, 1, null);
			}

			/* Make sure that the individual items add up to the total price
			 * + make sure that all ads have the advertisement cost set (they
			 * should all be set if we get this far). */
			BigDecimal totalCostEuroInclVat = BigDecimal.ZERO;
			BigDecimal totalCostEuroExclVat = BigDecimal.ZERO;
			for (Ad ad : highlightedAds) {
				totalCostEuroInclVat = totalCostEuroInclVat.add(ad.getAdvertisementCostInEuroInclVat());
				totalCostEuroExclVat = totalCostEuroExclVat.add(ad.getAdvertisementCostInEuroExclVat());
				logger.debug("Highlighting cost for ad " + ad.getId() + ": excl vat = "
						+ ad.getAdvertisementCostInEuroExclVat() + ", incl vat = "
						+ ad.getAdvertisementCostInEuroInclVat());
				if (ad.getAdvertisementCostInEuroExclVat().compareTo(BigDecimal.ZERO) == 0
						|| ad.getAdvertisementCostInEuroInclVat().compareTo(BigDecimal.ZERO) == 0) {
					throw new SellStarRuntimeException("Ad " + ad.getId() + " still has a zero advertisement cost.", null, 1, null);
				}
			}
			
			/* Make sure that the correct currency is used. */
			WebElement totalPriceInclVatElement = driver.findElement(By.xpath("//*[@id='products']/table/tfoot/tr/td[5]/strong"));
			WebElement totalPriceVatPriceElement = driver.findElement(By.xpath("//*[@id='products']/table/tfoot/tr/td[4]/strong"));
			Long usedCurrency = null;
			if (totalPriceInclVatElement.getText().endsWith(" €")) {
				usedCurrency = Currency.EURO;
			}
			Currency expectedCurrency = highlightedAds.get(0).getMarket().getCurrency();
			if (usedCurrency == null || !usedCurrency.equals(expectedCurrency.getId())) {
				logger.warn("Wrong currency in use for Huutonet in payForHighlightedAds (price incl vat = '"
						+ totalPriceInclVatElement.getText() + "', expected currency = " + expectedCurrency.getName() + ").");
				throw new SellStarRuntimeException("Wrong currency in use for Huutonet in payForHighlightedAds (price incl vat = '"
						+ totalPriceInclVatElement.getText() + "', expected currency = " + expectedCurrency.getName() + ").", null, 1, null);
			}
			
			/* Make sure that the price in the cart is equal to the expected price
			 * and correct any rounding errors. */
			BigDecimal priceOnWebPageInclVat = new BigDecimal(totalPriceInclVatElement.getText().replaceAll(" €","").replaceAll(",", "."));
			priceOnWebPageInclVat = priceOnWebPageInclVat.negate();
			BigDecimal vatPriceOnWebPage = new BigDecimal(totalPriceVatPriceElement.getText().replaceAll(" €","").replaceAll(",", "."));
			BigDecimal priceOnWebPageExclVat = priceOnWebPageInclVat.subtract(vatPriceOnWebPage);
			if (priceOnWebPageInclVat.compareTo(totalCostEuroInclVat) != 0
					|| priceOnWebPageExclVat.compareTo(totalCostEuroExclVat) != 0) {
				BigDecimal diffExclVat = totalCostEuroExclVat.subtract(priceOnWebPageExclVat);
				if (diffExclVat.abs().compareTo(new BigDecimal("0.20")) < 0) {
					/* The prices are calculated based on the price including vat so we can get
					 * rounding errors for the price excluding vat. Let's correct it here. */
					logger.debug("Correcting small rounding error: incl vat = " + priceOnWebPageInclVat.toPlainString()
							+ " (expected price incl vat = " + totalCostEuroInclVat.toPlainString() + "), excl vat = "
							+ priceOnWebPageExclVat.toPlainString() + " (expected price excl vat = "
							+ totalCostEuroExclVat.toPlainString() + ") - difference is only " + diffExclVat.toPlainString());
					Ad ad = highlightedAds.get(0);
					ad.setAdvertisementCostInEuroExclVat(ad.getAdvertisementCostInEuroExclVat().subtract(diffExclVat));
					totalCostEuroExclVat = totalCostEuroExclVat.subtract(diffExclVat);
					if (ad.getAdvertisementCostInEuroExclVat().compareTo(BigDecimal.ZERO) < 0) {
						/* We probably won't ever get here. It only happens if the difference is greater
						 * than the cost of the highlighting product itself. */
						throw new SellStarRuntimeException("Error correcting rounding error: negative cost excl vat for ad " + ad.getId(), null, 1, null);
					}
				}
			}
			if (priceOnWebPageInclVat.compareTo(totalCostEuroInclVat) != 0
					|| priceOnWebPageExclVat.compareTo(totalCostEuroExclVat) != 0) {
				logger.info("Wrong total price in cart: incl vat = " + priceOnWebPageInclVat.toPlainString()
						+ " (expected price incl vat = " + totalCostEuroInclVat.toPlainString() + "), excl vat = "
						+ priceOnWebPageExclVat.toPlainString() + " (expected price excl vat = "
						+ totalCostEuroExclVat.toPlainString() + ")");
				throw new SellStarRuntimeException("Wrong total price in cart.", null, 1, null);
			}

			/* Make sure that the total price isn't too high (average price is not
			 * allowed to exceed five euros). */
			BigDecimal maxAllowedTotalPrice = new BigDecimal("5.00").multiply(new BigDecimal(highlightedAds.size()));
			if (totalCostEuroInclVat.compareTo(maxAllowedTotalPrice) > 0) {
				logger.info("Total price too high (max allowed price for " + highlightedAds.size()
						+ " ads is " + maxAllowedTotalPrice.toPlainString() + ").");
				throw new SellStarRuntimeException("Total price too high.", null, 1, null);
			}
			
			/* Make sure that the balance of the wallet is large enough. */
			WebElement submitButton = driver.findElement(By.xpath("//*[@class='bluebutton']"));
			submitButton.click();
			driver.waitForElement(By.xpath("//*[@id='innerContent']/div[1]/table[2]/tbody/tr[1]/th[1]")); //wait for page to load
			WebElement wallet = driver.findElement(By.xpath("//*[@id='innerContent']/div[1]/table[2]/tbody/tr[1]/th[1]"));
			if (wallet.getText().indexOf("(saldo ") < 0 || !wallet.getText().endsWith(" €)")) {
				logger.info("Couldn't parse wallet balance (" + wallet.getText() + ").");
				throw new SellStarRuntimeException("Couldn't parse wallet balance.", null, 1, null);
			}
			String balanceStr = EmailFilter.getFieldValue(wallet.getText(),"saldo");
			BigDecimal walletBalance = new BigDecimal(balanceStr.replaceAll(",","."));
			logger.debug("Wallet balance: " + walletBalance.toPlainString() + ", total cost incl vat: "
					+ totalCostEuroInclVat.toPlainString());
			if (walletBalance.compareTo(totalCostEuroInclVat) < 0) {
				logger.debug("Balance of wallet not enough to pay for highlighting of products (balance = "
						+ walletBalance.toPlainString() + ", cost = " + totalCostEuroInclVat + ").");
				throw new SellStarRuntimeException("Balance of wallet not enough to pay for highlighting of products.",
						"Wallet balance: " + walletBalance.toPlainString() + ", total cost incl vat: "
						+ totalCostEuroInclVat.toPlainString(), 0, null);
			}
			
			/* Decrease the risk that the payment went through and then the transaction rolls back because
			 * of transaction timeout. If we here determine that too much time was spent in this method,
			 * then abort the whole thing. */
			if (startDate.getTime() + 1000*60*5 < System.currentTimeMillis()) {
				logger.info("Purchase of ad highlighting products took too long, aborting.");
				throw new SellStarRuntimeException("Purchase of ad highlighting products took too long, aborting.", null, 1, null);
			}

			/* Make the payment. If there is not enough money, then there will be a popup window
			 * displaying "Kukkarossasi ei ole tarpeeksi saldoa!" (i.e. this warning text is not
			 * displayed on the normal html page). However we should never get this since above
			 * we make sure that there is indeed enough money in the wallet. */
			submitButton = driver.findElement(By.xpath("//*[@id='innerContent']/div[1]/table[2]/tbody/tr[1]/th[2]//*[@id='submit']"));
			section.setInCriticalSection(true);
			submitButton.click();
			Alert alert = driver.switchTo().alert();
			String alertText = alert.getText();
			if (alertText.indexOf("Suoritetaanko maksu") >= 0) {
				alert.accept();
				logger.info("Payment of highlighting done for " + highlightedAds.size() + " ads.");
				try { Thread.sleep(3*1000); } catch (Exception e) { }
				section.setTransactionMustGoThrough(true);
				section.setInCriticalSection(false);
			} else {
				logger.info("Unknown alert when attempting to pay for highlighting (canceling payment): " + alertText);
				alert.dismiss();
				section.setInCriticalSection(false);
				try { Thread.sleep(3*1000); } catch (Exception e) { }
				throw new SellStarRuntimeException("Unknown alert text when attempting to pay for highlighting (canceling payment).", null, 1, null);
			}
		} catch (WebDriverException e) {
			logger.error("Payment problem: ",e);
			if (section.isInCriticalSection()) {
				String pageSource = driver.getPageSource();
				logger.debug("pageSource = \"" + pageSource + "\"");
				logger.error("Final step of payForHighlightedAds failed (unsure if the payment went through): ",e);
				EmailSender.sendOperationProblemReport("Problem paying for highlighting of products at Huutonet",
						"Hightlighting of products MIGHT have failed. TRANSACTION WAS ROLLED BACK, PLEASE EXAMINE BY HAND!!!");
				//throw e;
				throw new SellStarRuntimeException("Final step of payForHighlightedAds failed (unsure if the payment went through).",
						null, 0, e);
			} else {
				//throw new RuntimeException("Payment problem (doesn't require any special action).");
				throw new SellStarRuntimeException("Payment problem (doesn't require any special action).",
						null, 5, e);
			}
		}
	}
	
	private String getListingXML(int categoryId, String html, Ad ad, SellerMarket sellerMarket, ListerStrategy strategy,
			BigDecimal huutonetBugAdjustment) throws Exception {
		String currencyString = null;
		if (sellerMarket.getMarket().getCurrency().getName().equals("EURO")) {
			currencyString = "EUR";
		} else {
			throw new RuntimeException("Unknown currency.");
		}
		Entry entry = new Entry();
		entry.setTitle(StringUtilities.escapeString2HTML(StringUtilities.normalizer(ad.getAdTemplate().getHeadline())));
		fi.jonix.huutonet.market.huuto.xml.ad09beta.Category category = new fi.jonix.huutonet.market.huuto.xml.ad09beta.Category();
		category.setScheme("http://api.huuto.net/somt/0.9-beta/categories/" + categoryId);
		entry.setCategory(category);
		Content content = new Content();
		content.setContent(html);
		entry.setContent(content);

		Intention intention = new Intention();
		if (ad.getSalesType() == SALES_TYPE_AUCTION) {
			intention.setType("AUCTION");
		} else if (ad.getSalesType() == SALES_TYPE_AUCTION_OR_BUY) {
			intention.setType("AUCTION_AND_BUY_NOW");
		} else if (ad.getSalesType() == SALES_TYPE_BUY) {
			intention.setType("BUY_NOW");
		}
		entry.setIntention(intention);

		Calendar closingCal = Calendar.getInstance();
		closingCal.add(Calendar.DATE,ad.getOpenDays());
		XMLGregorianCalendar expirationTime = DatatypeFactory.newInstance().newXMLGregorianCalendar((GregorianCalendar)closingCal);
		entry.setExpirationTime(expirationTime);
		Condition condition = new Condition();
		condition.setType("NEW");
		entry.setCondition(condition);
		Location location = new Location();
		location.setCity(StringUtilities.escapeString2HTML(strategy.getLocation(ad)));
		entry.setLocation(location);
		
		/* Set a start price if applicable. */
		Price price = new Price();
		if (ad.getSalesType() == SALES_TYPE_AUCTION || ad.getSalesType() == SALES_TYPE_AUCTION_OR_BUY) {
			//the currentPrice might be the reservation price, I'm not sure
			CurrentPrice currentPrice = new CurrentPrice();
			currentPrice.setCurrency(currencyString);
			currentPrice.setValue(ad.getStartPriceInMarketCurrencyInclVat());
			price.setCurrentPrice(currentPrice);
			StartingPrice startingPrice = new StartingPrice();
			startingPrice.setCurrency(currencyString);
			startingPrice.setValue(ad.getStartPriceInMarketCurrencyInclVat());
			price.setStartingPrice(startingPrice);
		}

		/* Set a min increase value if applicable. */
		if (ad.getSalesType() == SALES_TYPE_AUCTION || ad.getSalesType() == SALES_TYPE_AUCTION_OR_BUY) {
			MinimumIncrease minimumIncrease = new MinimumIncrease();
			minimumIncrease.setCurrency(currencyString);
			minimumIncrease.setValue(sellerMarket.getMinBidIncreaseInMarketCurrencyInclVat());
			price.setMinimumIncrease(minimumIncrease);
		}
		
		/* Set a buyout price if applicable. */
		if (ad.getSalesType() == SALES_TYPE_BUY || ad.getSalesType() == SALES_TYPE_AUCTION_OR_BUY) {
			BuyNowPrice buyNowPrice = new BuyNowPrice();
			buyNowPrice.setCurrency(currencyString);
			
			/* Because of a bug in Huutonet it's not always possible to have
			 * startingPrice + minimumIncrease = buyNowPrice (it fails around
			 * 10% of the time). Therefore we need to add 5 cents to the
			 * buyNowPrice in certain situations. */
			if (ad.getStartPriceInMarketCurrencyInclVat().add(sellerMarket.
					getMinBidIncreaseInMarketCurrencyInclVat()).compareTo(ad.getBuyOutPriceInMarketCurrencyInclVat()) == 0) {
				buyNowPrice.setValue(ad.getBuyOutPriceInMarketCurrencyInclVat().add(huutonetBugAdjustment));
			} else {
				buyNowPrice.setValue(ad.getBuyOutPriceInMarketCurrencyInclVat());
			}
			price.setBuyNowPrice(buyNowPrice);
		}
		price.setTaxPercentage(sellerMarket.getSeller().getCustomerVatPercentage().multiply(new BigDecimal("100.00")));
		entry.setPrice(price);

		entry.setQuantity(new BigDecimal(ad.getOriginalAmount()).toBigIntegerExact());
		BuyerRequirements buyerRequirements = new BuyerRequirements();
		buyerRequirements.setVerificationRequired(false);
		entry.setBuyerRequirements(buyerRequirements);
		DeliveryMethods deliveryMethods = new DeliveryMethods();
		DeliveryMethod deliveryMethod = new DeliveryMethod();
		deliveryMethod.setType("Posti");
		deliveryMethod.setCost(ad.getShippingCostInMarketCurrencyInclVat());
		deliveryMethod.setCurrency(currencyString);
		String deliveryText = ad.getAdTemplate().getSeller().getDeliveryText();
		String deliveryTextEscaped = StringUtilities.escapeString2HTML(deliveryText.replaceAll("\n", "NEWLINE"/*"&#13;&#10;"*/));
		deliveryMethod.setDescription(deliveryTextEscaped);
		deliveryMethods.setDeliveryMethod(deliveryMethod);
		entry.setDeliveryMethods(deliveryMethods);
		PaymentMethods paymentMethods = new PaymentMethods();
		PaymentMethod paymentMethod = new PaymentMethod();
		paymentMethod.setType("ACCOUNT");
		paymentMethods.setPaymentMethod(paymentMethod);
		entry.setPaymentMethods(paymentMethods);
		
		/*ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		JAXB.marshal(entry,outStream);
		return outStream.toString();*/
		
		/* The Huutonet implementation seems to parse the XML using some archaic means
		 * and requires the namespaces to have certain prefixes. For that we use a
		 * custom namespace prefix mapper. */
		JAXBContext jc = JAXBContext.newInstance("fi.jonix.huutonet.market.huuto.xml.ad09beta");
		Marshaller m = jc.createMarshaller();
		m.setProperty("com.sun.xml.bind.namespacePrefixMapper",
				       new HuutonetNamespacePrefixMapper());
		//m.setProperty("jaxb.encoding", "UTF-8"); //output encoding
		//m.setProperty("jaxb.formatted.output", true);
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		m.marshal(entry, outStream);
		return outStream.toString().replaceAll("NEWLINE", "&#13;&#10;");
	}
	
	private class HuutonetNamespacePrefixMapper extends NamespacePrefixMapper {

		@Override
		public String getPreferredPrefix(String namespaceUri, String suggestion, boolean requirePrefix) {
			if (namespaceUri.equals("http://somt.sanoma.fi/somt")) {
				return "somt";
			} else if (namespaceUri.equals("http://dev.huuto.net/somt")) {
				return "huuto";
			}
			return "";
		}
		
	}

	private void listItemForSaleAPI(Ad ad, ListerStrategy strategy, Map<String,Integer> nonExistentCategories,
			SellerMarket sellerMarket, TreeMap<Long, List<Long>> preFilledScoreMap, String adHtml,
			String categoryIdentification) throws Exception {
		/* First do basic listing. */
		String xmlContents = getListingXML(ad.getMarketCategory().getMarketSpecId(),adHtml,ad,sellerMarket,
				strategy,new BigDecimal("0.00"));
		//logger.debug("listItemForSaleAPI.xmlContents = " + xmlContents);
		try {
			listAd(ad,sellerMarket,xmlContents,nonExistentCategories,categoryIdentification);
			logger.debug("Listing worked directly.");
		} catch (ListingException e) {
			// The listing might have failed because of a Huutonet bug
			if (!e.isToErrorState() && ad.getStartPriceInMarketCurrencyInclVat().add(sellerMarket.
					getMinBidIncreaseInMarketCurrencyInclVat()).compareTo(ad.getBuyOutPriceInMarketCurrencyInclVat()) == 0) {
				logger.debug("Listing failed, trying again with fix for Huutonet bug.");
				xmlContents = getListingXML(ad.getMarketCategory().getMarketSpecId(),adHtml,ad,sellerMarket,
						strategy,new BigDecimal(HUUTONET_BUG_EXTRA));
				listAd(ad,sellerMarket,xmlContents,nonExistentCategories,categoryIdentification);
				logger.debug("Listing worked when fix for Huutonet bug was applied.");
			} else {
				throw e;
			}
		}
		
		/* Then fetch the exact dateToMarket and dateClosing for the listed ad (it's important
		 * to get the correct date to market date since for multiple buyout purchases we need
		 * to figure out the ad based on this date in some cases (see also
		 * HuutonetProcessor.cloneAd)). Try 3 times because sometimes this fails. */
		boolean success = false;
		for (int i = 0; i < 3; i++) {
			// Sleep for a short time, because if we too quickly fetch information regarding the
			// just listed item, then Huutonet sometimes returns "not found".
			Thread.sleep(1*1000);
			success = getDateToMarketAndDateClosing(ad,sellerMarket);
			if (success) {
				if (i > 0) {
					logger.debug("Got the dates at attempt number " + i);
				}
				break;
			}
		}
		if (!success) {
			logger.error("Couldn't fetch dateToMarket nor dateClosing for listed item " + ad.getMarketSalesId());
			logger.debug("Setting an approximate dateToMarket and dateClosing for item " + ad.getMarketSalesId());
			ad.setDateToMarket(new Date());
			Calendar cal = Calendar.getInstance();
			cal.add(Calendar.DATE,ad.getOpenDays());
			ad.setDateClosing(cal.getTime());
			if (ad.getVisibilityType() > 0) {
				/* If this is a display window ad, then we need to fix this manually, since the dateToMarket
				 * is used for cloning (see also HuutonetProcessor.cloneAd for more information).
				 * =>Actually this is necessary to fix only if someone actually buys this product,
				 * since cloning is done only when someone buys a display window product. */
				logger.debug("Informing the operator about wrong dateToMarket/dateClosing since this is a display window ad.");
				EmailSender.sendOperationProblemReport("Problem fetching dateToMarket for item",
					"Fetching dateToMarket failed for ad with marketSalesId " + ad.getMarketSalesId() + ". This"
					+ " is a display window ad and should therefore be fixed by hand.\n\nNOTE: We only need to fix"
					+ " this if somebody actually purchases this item, so save this email for reference.");
			}
		}
		
		/* Finally ad the main image to the listed ad. */
		List<Image> imageList = ad.getAdTemplate().getProduct().getImagesInOrder();
		if (imageList != null && imageList.size() > 0) {
			Image image = imageList.get(0);
			success = false;
			for (int i = 0; i < 4; i++) {
				success = adImage(ad,image,sellerMarket);
				if (success) {
					if (i > 0) {
						logger.debug("Added image at attempt number " + i);
					}
					break;
				}
				// Sleep for a short time, because if we too quickly try to add an image regarding the
				// just listed item, then Huutonet sometimes returns "not found".
				Thread.sleep(1*1000);
			}
			if (!success) {
				logger.error("Couldn't add main image for listed item " + ad.getMarketSalesId());
				logger.debug("Informing the operator about problem.");
				EmailSender.sendOperationSummaryReport("Problem adding main image for item",
						"Couldn't add main image for ad with marketSalesId " + ad.getMarketSalesId() + ". Please"
						+ " do it by hand using the following command:\n"
						+ "curl -D - --data-binary @" + image.getLocalFileUrl() + " --user username:password"
						+ " https://salattu.huuto.net/somt/0.9-beta/items/" + ad.getMarketSalesId() + "/images\n");
			}
		}
		
		/* It seems like we don't get emails from Huutonet confirming a listing for products
		 * listed through the API, so we set the ad to confirmed already in this method. */
		ad.setConfirmed(true);
	}
	
	private void listAd(Ad ad, SellerMarket sellerMarket, String xmlContents, Map<String, Integer> nonExistentCategories, String categoryIdentification) throws Exception {
		DefaultHttpClient client = new DefaultHttpClient();
		HttpPost post = null;
		boolean madeCall = false;
		boolean extractedMarketSalesId = false;
		boolean extractedBody = false;
		try {
			StringEntity reqEntity = new StringEntity(xmlContents,HTTP.UTF_8);
			//reqEntity.setContentType("binary/octet-stream");
			reqEntity.setContentType("application/x-www-form-urlencoded");
			//reqEntity.setChunked(true);
			client.getCredentialsProvider().setCredentials(
					////new AuthScope("api.huuto.net", 80),
					new AuthScope("salattu.huuto.net", 443),
					//new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT),
					//AuthScope.ANY,
					new UsernamePasswordCredentials(sellerMarket.getUserName(), Encryption.decrypt(sellerMarket.getPassword())));
			//post = new HttpPost("http://api.huuto.net/somt/0.9-beta/items");
			post = new HttpPost("https://salattu.huuto.net/somt/0.9-beta/items");
			//post.getParams().setParameter(CoreProtocolPNames.USE_EXPECT_CONTINUE,Boolean.TRUE);
			//post.addHeader("Accept","*/*");
			post.setEntity(reqEntity);
			logger.debug("Calling huutonet API service (listing ad).");
			HttpResponse resp = client.execute(post);
			logger.debug("Returned from call of huutonet API service (listing ad).");
			madeCall = true;
			StatusLine statusLine = resp.getStatusLine();
			logger.debug("statusLine = " + statusLine);
			Header[] headers = resp.getAllHeaders();
			/*for (int j = 0; j < headers.length; j++) {
				logger.debug("headers[" + j + "] = " + headers[j]);
			}*/
			Header locationHeader = resp.getFirstHeader("Location");
			if (locationHeader != null) {
				String locHeaderValue = locationHeader.getValue();
				logger.debug("locHeaderValue = " + locHeaderValue);
				String marketSalesId = locHeaderValue.substring(locHeaderValue.lastIndexOf("/") + 1);
				logger.debug("marketSalesId = " + marketSalesId);
				ad.setMarketSalesId(marketSalesId);
				extractedMarketSalesId = true;
			}
			HttpEntity respEntity = resp.getEntity();
			String body = EntityUtils.toString(respEntity);
			//logger.debug("body = " + body);
			extractedBody = true;

			if (statusLine.getStatusCode() != 201 || !statusLine.getReasonPhrase().equals("Created")) {
				logger.info("Listing problem: statusLine.reasonPhrase = " + statusLine.getReasonPhrase()
						+ ", statusCode = " + statusLine.getStatusCode());
				/*if (statusLine.getStatusCode() == 404 && statusLine.getReasonPhrase().equals("Not Found")) {
						if (i == 0) {
							logger.debug("Failed at i = " + i + ". Sleeping for 10 seconds.");
							Thread.sleep(1000*10);
							logger.debug("Woke up, trying again.");
							continue;
						} else {
							logger.debug("Failed at i = " + i + ". Giving up: " + body);
							throw new ListingException("Listing failed for some reason", false);
						}
					} else */if (statusLine.getReasonPhrase().equals("Service Unavailable")) {
						throw new MarketTemporarilyDownException(sellerMarket.getMarket().getName() + " temporarily down (maintenance break)");
					} else if (statusLine.getReasonPhrase().equals("Unauthorized")) {
						logger.error("Faulty username or password when listing to Huutonet (unauthorized).");
						throw new LoggedOutFromMarketException();
					}
					if (body.indexOf("Kategoria on poistettu k") >= 0) {
						logger.debug("this category failed for the first time during this listing run");
						nonExistentCategories.put(categoryIdentification,1);
						throw new CategoryNotFoundException(categoryIdentification);
					} else {
						// In all other cases print out the body and throw a listing exception
						logger.debug("Huutonet gave the following error at API-listing (doesn't require any special action): " + body);
						//logger.debug("xmlContents: " + xmlContents);
						throw new ListingException("Listing failed for some reason", false);
					}
			}
			EntityUtils.consume(respEntity);
			logger.debug("Ad listed successfully using API");
		} catch (MarketTemporarilyDownException e) {
			throw new ListingException("Market temporarily down: " + e.getMessage(), false);
		} catch (LoggedOutFromMarketException e) {
			throw e;
		} catch (CategoryNotFoundException e) {
			throw e;
		} catch (ListingException e) {
			throw e;
		} catch (Exception e) {
			if (post != null) {
				post.abort();
			}
			if (!madeCall) {
				logger.debug("Huutonet gave the following error at API-listing (doesn't require any special action): ",e);
				throw new ListingException("Listing failed for some reason", false);
			} else if (!extractedMarketSalesId || !extractedBody) {
				logger.error("Huutonet gave the following error at API-listing (unsure if the listing went"
						+ " through - rolling back transaction): ",e);
				throw new ListingException("Listing failed for some reason", true);

			}
			logger.error("Unknown problem contacting huutonet API service: ",e);
			throw e;
		} finally {
			// Shut down connection manager to immediately deallocate all system resources
			//try {
				client.getConnectionManager().shutdown();
			/*} catch (Exception e) {
				logger.debug("Problem shutting down connection manager (not a big deal): ",e);
			}*/
		}
	}

	private boolean getDateToMarketAndDateClosing(Ad ad, SellerMarket sellerMarket) throws MarketTemporarilyDownException, LoggedOutFromMarketException, ListingException {
		DefaultHttpClient client = new DefaultHttpClient();
		HttpGet get = null;
		try {
			get = new HttpGet("http://api.huuto.net/somt/0.9-beta/items/" + ad.getMarketSalesId());
			logger.debug("Calling huutonet API service (getting info regarding listed ad).");
			HttpResponse resp = client.execute(get);
			logger.debug("Returned from call of huutonet API service (getting info regarding listed ad).");
			StatusLine statusLine = resp.getStatusLine();
			logger.debug("statusLine = " + statusLine);
			Header[] headers = resp.getAllHeaders();
			/*for (int i = 0; i < headers.length; i++) {
				logger.debug("headers[" + i + "] = " + headers[i]);
			}*/
			HttpEntity respEntity = resp.getEntity();
			String body = EntityUtils.toString(respEntity);
			//logger.debug("body = " + body);
			
			if (statusLine.getStatusCode() != 200 || !statusLine.getReasonPhrase().equals("OK")) {
				logger.info("Problem getting info regarding listed ad: statusLine.reasonPhrase = "
						+ statusLine.getReasonPhrase() + ", statusCode = " + statusLine.getStatusCode());
				if (statusLine.getReasonPhrase().equals("Service Unavailable")) {
					throw new MarketTemporarilyDownException(sellerMarket.getMarket().getName() + " temporarily down (maintenance break)");
				} else if (statusLine.getReasonPhrase().equals("Unauthorized")) {
					logger.error("Faulty username or password when listing to Huutonet (unauthorized).");
					throw new LoggedOutFromMarketException();
				}
				if (body.indexOf("Kohdetta ei l") >= 0 || statusLine.getStatusCode() == 404) {
					// This one comes now and then, and in this case it's the responsibility of
					// upper level code to call this method again, so just return false here.
					logger.error("Listed item " + ad.getMarketSalesId() + " not found at Huutonet: " + body);
					return false;
				} else if (statusLine.getReasonPhrase().equals("Internal Server Error")) {
					// Most likely some temporary problem at Huutonet
					logger.warn("Got Internal Server Error when attempting to get dateToMarket and"
							+ " dateClosing for Listed item " + ad.getMarketSalesId() + ": " + body);
					return false;
				} else {
					// In all other cases print out the body and throw a listing exception
					logger.error("Problem getting info regarding listed item " + ad.getMarketSalesId() + ": " + body);
					throw new ListingException("Problem getting info regarding listed item " + ad.getMarketSalesId(),true);
				}
			}
			EntityUtils.consume(respEntity);
			
			/* Unmarshal the xml into java objects and then get the desired dates. */
			/*Charset charset = Charset.forName("UTF-8");
			ByteArrayInputStream inStream = new ByteArrayInputStream(body.getBytes(charset));
			Entry entry = JAXB.unmarshal(inStream,Entry.class);*/
			Entry entry = JAXB.unmarshal(new StreamSource(new StringReader(body)),Entry.class);
			XMLGregorianCalendar dateToMarketCal = entry.getUpdated();
			//logger.debug("dateToMarket (xml) = " + dateToMarketCal);
			Date dateToMarket = dateToMarketCal.toGregorianCalendar().getTime();
			logger.debug("dateToMarket (date) = " + dateToMarket);
			XMLGregorianCalendar dateClosingCal = entry.getExpirationTime();
			//logger.debug("dateClosing (xml) = " + dateClosingCal);
			Date dateClosing = dateClosingCal.toGregorianCalendar().getTime();
			logger.debug("dateClosing (date) = " + dateClosing);
			ad.setDateToMarket(dateToMarket);
			ad.setDateClosing(dateClosing);
			logger.debug("dateToMarket fetched successfully using API");
			return true;
		} catch (MarketTemporarilyDownException e) {
			throw e;
		} catch (LoggedOutFromMarketException e) {
			throw e;
		} catch (ListingException e) {
			throw e;
		} catch (Exception e) {
			if (get != null) {
				get.abort();
			}
			logger.debug("Couldn't fetch dateToMarket nor dateClosing for listed item " + ad.getMarketSalesId() + ": ", e);
		} finally {
			// Shut down connection manager to immediately deallocate all system resources
			//try {
				client.getConnectionManager().shutdown();
			/*} catch (Exception e) {
				logger.debug("Problem shutting down connection manager (not a big deal): ",e);
			}*/
		}
		return false;
	}

	private boolean adImage(Ad ad, Image image, SellerMarket sellerMarket) throws MarketTemporarilyDownException, LoggedOutFromMarketException, ListingException {
		DefaultHttpClient client = new DefaultHttpClient();
		HttpPost post = null;
		try {
			//FileEntity reqEntity = new FileEntity(new File(image.getLocalFileUrl()), "binary/octet-stream");
			FileEntity reqEntity = new FileEntity(new File(image.getLocalFileUrl()), "application/x-www-form-urlencoded");
			client.getCredentialsProvider().setCredentials(
					//new AuthScope("api.huuto.net", 80),
					new AuthScope("salattu.huuto.net", 443),
					//new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT),
					//AuthScope.ANY,
					new UsernamePasswordCredentials(sellerMarket.getUserName(), Encryption.decrypt(sellerMarket.getPassword())));
			//post = new HttpPost("http://api.huuto.net/somt/0.9-beta/items/" + ad.getMarketSalesId() + "/images");
			post = new HttpPost("https://salattu.huuto.net/somt/0.9-beta/items/" + ad.getMarketSalesId() + "/images");
			post.setEntity(reqEntity);
			logger.debug("Calling huutonet API service (adding main image).");
			HttpResponse resp = client.execute(post);
			logger.debug("Returned from call of huutonet API service (adding main image).");
			StatusLine statusLine = resp.getStatusLine();
			logger.debug("statusLine = " + statusLine);
			Header[] headers = resp.getAllHeaders();
			/*for (int i = 0; i < headers.length; i++) {
				logger.debug("headers[" + i + "] = " + headers[i]);
			}*/
			HttpEntity respEntity = resp.getEntity();
			String body = EntityUtils.toString(respEntity);
			//logger.debug("body = " + body);

			if (statusLine.getStatusCode() != 201 || !statusLine.getReasonPhrase().equals("Created")) {
				logger.info("Problem adding main image: statusLine.reasonPhrase = " + statusLine.getReasonPhrase()
						+ ", statusCode = " + statusLine.getStatusCode());
				if (statusLine.getReasonPhrase().equals("Service Unavailable")) {
					throw new MarketTemporarilyDownException(sellerMarket.getMarket().getName() + " temporarily down (maintenance break)");
				} else if (statusLine.getReasonPhrase().equals("Unauthorized")) {
					logger.error("Faulty username or password when adding image to listed item to Huutonet (unauthorized).");
					throw new LoggedOutFromMarketException();
				}
				if (body.indexOf("Kohteeseen on jo lis") >= 0) {
					logger.error("Problem adding main image for listed item " + ad.getMarketSalesId()
							+ ": Image already added: " + body);
					throw new ListingException("Main image already added for listed item " + ad.getMarketSalesId(),true);
				} else {
					// In all other cases print out the body and return false (this one comes
					// now and then, and in this case it's the responsibility of upper level
					// code to call this method again).
					logger.error("Problem adding main image for listed item " + ad.getMarketSalesId() + ": " + body);
					return false;
				}
			}

			EntityUtils.consume(respEntity);
			logger.debug("Main image successfully added using API");
			return true;
		} catch (MarketTemporarilyDownException e) {
			throw e;
		} catch (LoggedOutFromMarketException e) {
			throw e;
		} catch (ListingException e) {
			throw e;
		} catch (Exception e) {
			if (post != null) {
				post.abort();
			}
			logger.debug("Couldn't add main image for listed item " + ad.getMarketSalesId() + ": ", e);
		} finally {
			// Shut down connection manager to immediately deallocate all system resources
			//try {
				client.getConnectionManager().shutdown();
			/*} catch (Exception e) {
				logger.debug("Problem shutting down connection manager (not a big deal): ",e);
			}*/
		}
		return false;
	}

	private void listItemForSaleSelenium(Ad ad, ListerStrategy strategy, Map<String,Integer> nonExistentCategories,
			SellerMarket sellerMarket, TreeMap<Long, List<Long>> preFilledScoreMap, String adHtml,
			String categoryIdentification, String mainCategoryValue, String subCategoryValue, String categoryValue) throws Exception {
		CustomWebDriver driver = seleniumRobot.getWebDriver();
		try {
			driver.get("http://www.huuto.net/fi/newitem3.php?has_js=1");
			//selenium.waitForPageToLoad();
			driver.waitForElement(By.name("title")); //wait for page to load
	
			/* Make sure that we aren't logged out. We can be logged out when we get here, for example in the following
			 * case: One process is fetching a public question from huutonet, then listing starts, the public question fetching
			 * process logs out and then finally we get here. */
			WebElement titleElement = driver.findElementOrNull(By.name("title"));
			if (driver.isElementPresent(By.name("userid")) || titleElement == null || driver.getPageSource().indexOf("Et ole kirjautunut palveluun") >= 0) {
				throw new LoggedOutFromMarketException();
			}
			loggedOnAsCorrectUser(sellerMarket);
			
			/* Make sure that we are logged on as the correct user. */
			/*WebElement loggedOnUser = driver.findElementOrNull(By.xpath("//a[@id='log_out']/../a[1]/b"));
			if (loggedOnUser != null) {
				logger.debug("loggedOnUser = " + loggedOnUser.getText());
			}
			if (loggedOnUser == null || !loggedOnUser.getText().equals(sellerMarket.getUserName())) {
				throw new MarketLoggedOnAsWrongUserException("Logged on to Huutonet as " + loggedOnUser + " although we SHOULD have been logged on as " + sellerMarket.getUserName());
			}*/
			
			// Note: Don't html escape the headline because it seems
			// like huutonet processes html stuff in a way that in some
			// cases breaks the escaping (and in addition the name that
			// comes in the email gets screwed up if the headline is
			// escaped).
			driver.setValueWithJavascript(titleElement, ad.getAdTemplate().getHeadline());
	
			try {
				Thread.sleep(200);
				Select select = new Select(driver.findElement(By.id("maincategory")));
				select.selectByValue(mainCategoryValue);
				int i = 0;
				Thread.sleep(200);
				while (true) {
					try {
						Select selectSub = new Select(driver.findElement(By.id("subcategory")));
						selectSub.selectByValue(subCategoryValue);
						break;
					} catch (Exception e) {
						i++;
						Thread.sleep(200);
					}
					if (i > 10) {
						throw new Exception("Sub category not found");
					}
				}
				i = 0;
				Thread.sleep(200);
				while (true) {
					try {
						Select selectSub = new Select(driver.findElement(By.id("category")));
						selectSub.selectByValue(categoryValue);
						break;
					} catch (Exception e) {
						i++;
						Thread.sleep(200);
					}
					if (i > 10) {
						throw new Exception("Sub category not found");
					}
				}
			} catch (Exception e) {
				logger.debug("this category failed for the first time during this listing run");
				nonExistentCategories.put(categoryIdentification,1);
				throw new CategoryNotFoundException(categoryIdentification);
			}
	
			//selenium.click("//span[@id='cke_18_label']/../../a");
			WebElement changeHtml = driver.findElement(By.xpath("//span[@id='cke_18_label']/../../a"));
			changeHtml.click();
			driver.waitForElement(By.xpath("//td[@id='cke_contents_description']/textarea")); //wait for javascript
			/* Entering the description using sendKeys takes forever since it's done key-by-key, so
			 * here we set the value using Javascript instead. */
			WebElement textArea = driver.findElement(By.xpath("//td[@id='cke_contents_description']/textarea"));
			//textArea.clear();
			//textArea.sendKeys(description);
			driver.setValueWithJavascript(textArea, adHtml);
			changeHtml.click();
			//driver.waitForElement(By.xpath("//td[@id='cke_contents_description']/iframe")); //wait for javascript
			
			//selenium.select("//select[@name='classification']", CLASSIFICATION_NEW);
			Select classification = new Select(driver.findElement(By.name("classification")));
			classification.selectByVisibleText(CLASSIFICATION_NEW);

			WebElement location = driver.findElement(By.name("location"));
			driver.setValueWithJavascript(location, StringUtilities.escapeString2HTML(strategy.getLocation(ad)));

			WebElement quantity = driver.findElement(By.id("quantity"));
			driver.setValueWithJavascript(quantity, ad.getOriginalAmount().toString());
	
			List<Image> imageList = ad.getAdTemplate().getProduct().getImagesInOrder();
			if (imageList != null && imageList.size() > 0) {
				Image image = imageList.get(0);
				//selenium.type("//input[@name='image']", image.getLocalFileUrl());
				WebElement imageElement = driver.findElement(By.name("image"));
				//imageElement.clear();
				imageElement.sendKeys(image.getLocalFileUrl());
				//driver.setValueWithJavascript(imageElement, image.getLocalFileUrl());
			}

			WebElement salesType = driver.findElement(By.id("sellstyle" + ad.getSalesType().toString()));
			salesType.click();
			
			WebElement openDays = driver.findElement(By.id("opendays"));
			driver.setValueWithJavascript(openDays, ad.getOpenDays().toString());

			/* Set a start price if applicable. */
			if (ad.getSalesType() == SALES_TYPE_AUCTION || ad.getSalesType() == SALES_TYPE_AUCTION_OR_BUY) {
				WebElement startPrice = driver.findElement(By.id("startprice"));
				driver.setValueWithJavascript(startPrice, MathUtilities.roundTo2Decimals
						(ad.getStartPriceInMarketCurrencyInclVat()).replace(".",","));
			}

			/* Set a buyout price if applicable. */
			if (ad.getSalesType() == SALES_TYPE_BUY || ad.getSalesType() == SALES_TYPE_AUCTION_OR_BUY) {
				WebElement buyoutPrice = driver.findElement(By.id("price"));
				
				/* Because of a bug in Huutonet it's not always possible to have
				 * startingPrice + minimumIncrease = buyNowPrice (it fails around
				 * 10% of the time). Therefore we need to add 5 cents to the
				 * buyNowPrice in certain situations. */
				if (ad.getStartPriceInMarketCurrencyInclVat().add(sellerMarket.
						getMinBidIncreaseInMarketCurrencyInclVat()).compareTo(ad.getBuyOutPriceInMarketCurrencyInclVat()) == 0) {
					//driver.setValueWithJavascript(buyoutPrice, MathUtilities.roundTo2Decimals
						//	(ad.getBuyOutPriceInMarketCurrencyInclVat().add(new BigDecimal(HUUTONET_BUG_EXTRA))).replace(".",","));
					throw new RuntimeException("Not implemented: we should first try without correcting the Huutonet bug and only if listing fails we should adjust for that.");
				} else {
					driver.setValueWithJavascript(buyoutPrice, MathUtilities.roundTo2Decimals
							(ad.getBuyOutPriceInMarketCurrencyInclVat()).replace(".",","));
				}
			}
			
			/* Setting of price reservation is disabled because of problems with the javascript.
			 * When changing the value of the start price, then a javascript is executed that sets
			 * the min bid increase. Now and then the javascript sets it AFTER we have set it in
			 * the code. On these occations huutonet gives an error message saying that "the minimum
			 * price reservation is starting price + minimum bid increase". I've tried out different
			 * solutions to this but I haven't found any that always works. As a workaround we don't
			 * set any price reservation at all. This has the effect that now and then the minimum
			 * bid increase will be the default of huutonet's javascript and not what we set it to.
			 * However that's not a problem. */
			/*if (ad.getSalesType() == SALES_TYPE_AUCTION || ad.getSalesType() == SALES_TYPE_AUCTION_OR_BUY) {
				WebElement minSalesPricePrice = driver.findElement(By.id("reserve"));
				driver.setValueWithJavascript(minSalesPricePrice, MathUtilities.roundTo2Decimals
						(ad.getMinSellingPriceInMarketCurrencyInclVat()).replace(".",","));
			}*/
			
			/* Set a min increase value if applicable. */
			if (ad.getSalesType() == SALES_TYPE_AUCTION || ad.getSalesType() == SALES_TYPE_AUCTION_OR_BUY) {
				WebElement increase = driver.findElement(By.id("increase"));
				driver.setValueWithJavascript(increase, MathUtilities.roundTo2Decimals
						(sellerMarket.getMinBidIncreaseInMarketCurrencyInclVat()).replace(".",","));
			}

			//selenium.type("//input[@name='alv']", "0");
			
			//selenium.click("//input[@name='deliverystyle[]' and @value='" + DELIVERY_STYLE_POSTI + "']");
			WebElement deliveryStyle = driver.findElement(By.id("deliverystyle" + DELIVERY_STYLE_POSTI));
			deliveryStyle.clear();
			deliveryStyle.click();
			
			WebElement shippingCost = driver.findElement(By.id("deliveryprice"));
			driver.setValueWithJavascript(shippingCost, MathUtilities.roundTo2Decimals(ad.getShippingCostInMarketCurrencyInclVat()).replace(".",","));
			
			String deliveryText = ad.getAdTemplate().getSeller().getDeliveryText();
			String deliveryTextEscaped = StringUtilities.escapeString2HTML(deliveryText);
			WebElement deliveryTextElement = driver.findElement(By.name("deliveryother"));
			/* Entering the description using sendKeys takes forever since it's done key-by-key, so
			 * here we set the value using Javascript instead. */
			driver.setValueWithJavascript(deliveryTextElement, deliveryTextEscaped);
	
			//selenium.check("//input[@name='buystyle[]' and @value='" + ACCOUNT_TRANSFER + "']");
			WebElement accountTransfer = driver.findElement(By.id("buystyle" + ACCOUNT_TRANSFER));
			if (!accountTransfer.isSelected()) {
				accountTransfer.click();
			}
			
			//Make sure we DON'T use Nettimaksu
			//selenium.uncheck("//input[@name='buystyle[]' and @value='" + NET_PAYMENT + "']"); //Make sure we DON'T use Nettimaksu
			WebElement netPayment = driver.findElement(By.id("buystyle" + NET_PAYMENT));
			if (netPayment.isSelected()) {
				netPayment.click();
			}
			
			//Make sure we DON'T use Payson-turva
			//selenium.uncheck("//input[@name='buystyle[]' and @value='" + PAYSON_SECURITY + "']"); //Make sure we DON'T use Payson-turva
			WebElement paysonPayment = driver.findElement(By.id("buystyle" + PAYSON_SECURITY));
			if (paysonPayment.isSelected()) {
				paysonPayment.click();
			}
	
			//selenium.click("//input[@value='Jatka esikatseluun']");
			driver.waitForElement(By.xpath("//input[@value='Jatka esikatseluun']"));
			WebElement continueToPreview = driver.findElement(By.xpath("//input[@value='Jatka esikatseluun']"));
			continueToPreview.click();
			driver.waitForElement(By.xpath("//form[@name='acceptitem']")); //wait for page to load

			WebElement idElement;
			WebElement dateToMarketElement;
			WebElement dateClosingElement;
			String bodyText = driver.getPageSource();
			WebElement dateToMarketHeader;
			if (bodyText.indexOf("on Tehomyyjä") >= 0) {
				//huutonetId = selenium.getText("//table[4]/tbody/tr[2]/td[2]/table/tbody/tr[3]/td[2]");
				idElement = driver.findElement(By.xpath("//table[4]/tbody/tr[2]/td[2]/table/tbody/tr[3]/td[2]"));
				//dateString = selenium.getText("//table[4]/tbody/tr[2]/td[2]/table/tbody/tr[7]/td[2]");
				dateToMarketHeader = driver.findElement(By.xpath("//table[4]/tbody/tr[2]/td[2]/table/tbody/tr[6]/td[1]"));
				dateToMarketElement = driver.findElement(By.xpath("//table[4]/tbody/tr[2]/td[2]/table/tbody/tr[6]/td[2]"));
				dateClosingElement = driver.findElement(By.xpath("//table[4]/tbody/tr[2]/td[2]/table/tbody/tr[7]/td[2]"));
			} else {
				//huutonetId = selenium.getText("//table[4]/tbody/tr[2]/td[2]/table/tbody/tr[2]/td[2]");
				idElement = driver.findElement(By.xpath("//table[3]/tbody/tr[2]/td[2]/table/tbody/tr[2]/td[2]"));
				//dateString = selenium.getText("//table[4]/tbody/tr[2]/td[2]/table/tbody/tr[6]/td[2]");
				dateToMarketHeader = driver.findElement(By.xpath("//table[3]/tbody/tr[2]/td[2]/table/tbody/tr[5]/td[1]"));
				dateToMarketElement = driver.findElement(By.xpath("//table[3]/tbody/tr[2]/td[2]/table/tbody/tr[5]/td[2]"));
				dateClosingElement = driver.findElement(By.xpath("//table[3]/tbody/tr[2]/td[2]/table/tbody/tr[6]/td[2]"));
			}
			String huutonetId = idElement.getText();
			logger.debug("huutonetId = " + huutonetId);
			if (!dateToMarketHeader.getText().equals("Ilmoitettu")) {
				/* It's important to get the correct date to market date since for multiple buyout
				 * purchases we need to figure out the ad based on this date in some cases (see also
				 * HuutonetProcessor.cloneAd). So here print a warning message if we got the wrong date. */
				logger.warn("Possibly got the wrong date for date to market for ad " + huutonetId);
			}
			String dateClosingString = dateClosingElement.getText();
			//logger.debug("dateClosing = " + dateString.substring(0, dateString.indexOf("\n")).trim());
			Date dateToMarket = CLOSING_DATE_FORMAT.parse(dateToMarketElement.getText());
			//ad.setDateToMarket(new Date());
			ad.setDateToMarket(dateToMarket);
			Date dateClosing = CLOSING_DATE_FORMAT.parse(dateClosingString.substring(0, dateClosingString.indexOf("\n")).trim());
			ad.setDateClosing(dateClosing);
			ad.setMarketSalesId(huutonetId);
		} catch (WebDriverException e) {
			logger.debug("Listing problem (doesn't require any special action): ",e);
			try {
				WebElement errorElement = driver.findElement(By.className("error"));
				logger.debug("Huutonet gave the following error: " + errorElement.getText());
				WebElement startPriceElement = driver.findElement(By.id("startprice"));
				WebElement increaseElement = driver.findElement(By.id("increase"));
				WebElement minSalesPricePriceElement = driver.findElement(By.id("reserve"));
				logger.debug("On the error page: startPrice = " + startPriceElement.getText()
						+ ", increase = " + increaseElement.getText() + ", min sales price = "
						+ minSalesPricePriceElement.getText());
			} catch (Exception e2) { }
			throw new ListingException("Listing failed for some reason", false);
		}
		
		WebElement submitElement = driver.findElement(By.xpath("//form[@name='acceptitem']"));
		submitElement.submit();
	}
	
	@Override
	public boolean listItemForSale(Ad ad, ListerStrategy strategy, Map<String,Integer> nonExistentCategories,
			SellerMarket sellerMarket, TreeMap<Long, List<Long>> preFilledScoreMap) throws Exception {
		/* First check if this is a non existing category that has already been tried
		 * during this listing round. If so then abort right away. */
		String categoryValue = "" + ad.getMarketCategory().getMarketSpecId();
		String categoryName = ad.getMarketCategory().getName();
		String subCategoryValue = "" + ad.getMarketCategory().getParentCategory().getMarketSpecId();
		String subCategoryName = ad.getMarketCategory().getParentCategory().getName();
		String mainCategoryValue = "" + ad.getMarketCategory().getParentCategory().getParentCategory().getMarketSpecId();
		String mainCategoryName = ad.getMarketCategory().getParentCategory().getParentCategory().getName();
		String categoryIdentification = Market.HUUTONET_MARKET_NAME + ": " + mainCategoryValue + "/" + subCategoryValue + "/" + categoryValue
			+ " (" + mainCategoryName + "/" + subCategoryName + "/" + categoryName + ")";
		if (nonExistentCategories.get(categoryIdentification) != null) {
			//this category has already failed during this listing run, so don't try it again
			logger.debug("this category has already failed during this listing run, so don't try it again");
			Integer count = nonExistentCategories.get(categoryIdentification);
			nonExistentCategories.put(categoryIdentification,count.intValue()+1);
			throw new CategoryNotFoundException(categoryIdentification);
		}
		
		/* Safety check: huutonet doesn't allow "auction or buyout" if the
		 * quantity is greater than one */
		if (ad.getOriginalAmount() > 1 && ad.getSalesType() == SALES_TYPE_AUCTION_OR_BUY) {
			logger.error("Huutonet doesn't allow quantity > 1 for \"auction or buyout\""
					+ " (for adTemplate " + ad.getAdTemplate().getId() + ").");
			throw new RuntimeException("Error: huutonet doesn't allow quantity > 1 for \"auction or buyout\".");
		}
		
		MarketAdDecorator marketAdDecorator = (MarketAdDecorator) ApplicationContextPlaceholder.applicationContext.getBean(sellerMarket.getAdDecorator());
		ad.setUsedBeanName(ad.getAdTemplate().getBeanName());
		ad.setUsedBeanVersion(marketAdDecorator.getBeanVersion());
		String adHtml = marketAdDecorator.getFormatedBody(ad, sellerMarket, true, preFilledScoreMap);

		int listingType = API_LISTING;
		if (listingType == SELENIUM_LISTING) {
			listItemForSaleSelenium(ad,strategy,nonExistentCategories,sellerMarket,preFilledScoreMap,adHtml,
					categoryIdentification,mainCategoryValue,subCategoryValue,categoryValue);
		} else {
			listItemForSaleAPI(ad,strategy,nonExistentCategories,sellerMarket,preFilledScoreMap,adHtml,
					categoryIdentification);
		}

		/* If this ad has visibility > 0, then try to put this into the shopping chart. It's not a big
		 * deal if this fails, because if it fails here, then we'll try it again in payForHighlightedAds. */
		if (ad.getVisibilityType() > 0) {
			logger.debug("Ad " + ad.getId() + " has visibility " + ad.getVisibilityType() + ", trying to add to shopping cart.");
			addToDisplayWindow(ad,(listingType == SELENIUM_LISTING));
		}
		
		return true;
	}

	private void addToDisplayWindow(Ad ad, boolean pageAlreadyOpen) {
		CustomWebDriver driver = seleniumRobot.getWebDriver();
		boolean addedToShoppingCart = false;
		try {
			if (!pageAlreadyOpen) {
				// Open the correct web page if it isn't opened yet
				driver.get(getUrlForMarketSalesId(ad.getMarketSalesId()));
			}
			WebElement addProductsSection = driver.findElementOrNull(By.id("add-products"));
			if (addProductsSection != null) {
				if (ad.getVisibilityType().intValue() == 1) {
					addToNormalDisplayWindowHeadlineHighlighted();
				} else if (ad.getVisibilityType().intValue() == 2) {
					throw new Exception("Value 2 for visibility type not supported (because we already get images for free).");
				} else if (ad.getVisibilityType().intValue() == 3) {
					addToNormalDisplayWindowBGColor();
				} else if (ad.getVisibilityType().intValue() == 4) {
					addToNormalDisplayWindow();
				} else if (ad.getVisibilityType().intValue() == 10) {
					addToNormalDisplayWindow();
					addToNormalDisplayWindowHeadlineHighlighted();
				} else if (ad.getVisibilityType().intValue() == 11) {
					throw new Exception("Value 11 for visibility type not supported (because we already get images for free).");
				} else if (ad.getVisibilityType().intValue() == 12) {
					addToNormalDisplayWindow();
					addToNormalDisplayWindowBGColor();
				} else if (ad.getVisibilityType().intValue() == 20) {
					throw new Exception("Value 20 for visibility type not supported (because we already get images for free).");
				} else if (ad.getVisibilityType().intValue() == 21) {
					addToNormalDisplayWindow();
					addToNormalDisplayWindowHeadlineHighlighted();
					addToNormalDisplayWindowBGColor();
				} else if (ad.getVisibilityType().intValue() == 22) {
					throw new Exception("Value 22 for visibility type not supported (because we already get images for free).");
				} else if (ad.getVisibilityType().intValue() == 30) {
					addToMainSectionDisplayWindow();
				} else if (ad.getVisibilityType().intValue() == 40) {
					addToFrontPageDisplayWindow();
				} else {
					throw new Exception("Unsupported visibility type not supported (" + ad.getVisibilityType() + " not recognized).");
				}
				WebElement submitButton = driver.findElementOrNull(By.id("submit"));
				logger.debug("Submitting display window information.");
				submitButton.click();
				driver.waitForElement(By.xpath("//*[@class='bluebutton']")); //wait for page to load
				String pageSource = driver.getPageSource();
				if (pageSource.indexOf("Maksutapahtuma") < 0) {
					WebElement errorElement = driver.findElement(By.className("error"));
					throw new Exception("Huutonet gave the following error: " + errorElement.getText());
				} else {
					addedToShoppingCart = true;
				}
			}
		} catch (Exception e) {
			logger.debug("Adding to shopping cart failed with the following error: ", e);
			addedToShoppingCart = false;
		}
		if (!addedToShoppingCart) {
			logger.debug("Adding to shopping cart failed.");
		}
	}

	private void addToNormalDisplayWindow() {
		CustomWebDriver driver = seleniumRobot.getWebDriver();
		WebElement displayWindow = driver.findElementOrNull(By.id("GalleryList-add"));
		if (!displayWindow.isSelected()) {
			logger.debug("Choosing normal display window.");
			displayWindow.click();
		}
		WebElement displayWindowAmount = driver.findElementOrNull(By.id("GalleryList-amount"));
		BigDecimal value = new BigDecimal(displayWindowAmount.getAttribute("value").replaceAll(",", "."));
		BigDecimal valueToAddToDefault = new BigDecimal("0.12");
		value = value.add(valueToAddToDefault);
		displayWindowAmount.clear();
		displayWindowAmount.sendKeys(MathUtilities.roundTo2Decimals(value).replace(".",","));
	}

	private void addToNormalDisplayWindowBGColor() {
		CustomWebDriver driver = seleniumRobot.getWebDriver();
		WebElement displayWindow = driver.findElementOrNull(By.id("Background-add"));
		if (!displayWindow.isSelected()) {
			logger.debug("Choosing extra bg color.");
			displayWindow.click();
		}
	}

	private void addToNormalDisplayWindowHeadlineHighlighted() {
		CustomWebDriver driver = seleniumRobot.getWebDriver();
		WebElement displayWindow = driver.findElementOrNull(By.id("Highlight-add"));
		if (!displayWindow.isSelected()) {
			logger.debug("Choosing highlighted headline.");
			displayWindow.click();
		}
	}

	private void addToMainSectionDisplayWindow() {
		CustomWebDriver driver = seleniumRobot.getWebDriver();
		WebElement displayWindow = driver.findElementOrNull(By.id("MainGallery-add"));
		if (!displayWindow.isSelected()) {
			logger.debug("Choosing main section display window.");
			displayWindow.click();
		}
	}

	private void addToFrontPageDisplayWindow() {
		CustomWebDriver driver = seleniumRobot.getWebDriver();
		WebElement displayWindow = driver.findElementOrNull(By.id("FrontGallery-add"));
		if (!displayWindow.isSelected()) {
			logger.debug("Choosing front page display window.");
			displayWindow.click();
		}
	}

	/*@Override
	public SellerMarket isAlreadyLoggedOnAs() {
		CustomSelenium selenium = seleniumRobot.getSelenium();
		selenium.open("http://www.huuto.net/fi/own_menu.php3");
		selenium.waitForPageToLoad();
		if (!selenium.isElementPresent("//input[@name='userid']")) {
			// We are already logged on as somebody. Let's figure out as who.
			List<SellerMarket> sellerMarkets = sellerMarketDAO.loadAll();
			String bodyText = selenium.getBodyText();
			for (SellerMarket sellerMarket : sellerMarkets) {
				if (bodyText.indexOf("Henkilökohtainen osio - " + sellerMarket.getUserName()) >= 0) {
					return sellerMarket;
				}
			}
			throw new RuntimeException("ERROR: logged on as an unknown user. This should never happen.");
		}
		return null;
	}*/

	@Override
	public void login(SellerMarket sellerMarket) throws MarketTemporarilyDownException, MarketLoggedOnAsWrongUserException {
		logger.debug("Log in to www.huuto.net as " + sellerMarket.getUserName());
		CustomWebDriver driver = seleniumRobot.getWebDriver();
		driver.get("http://www.huuto.net/fi/own_menu.php3");
		String bodySource = driver.getPageSource();
		WebElement userLogonElement = driver.findElementOrNull(By.xpath("//input[@name='userid']"));
		if (userLogonElement == null) {
			// We are already logged on as somebody.
			if (bodySource.indexOf("Henkilökohtainen osio - " + sellerMarket.getUserName()) >= 0) {
				/* We are already logged on as user sellerMarket. */
				logger.info("Already logged on as " + sellerMarket.getUserName());
				return;
			} else {
				if (bodySource.indexOf("Henkilökohtainen osio") > 0) {
					logger.debug("Already logged on as different user than " + sellerMarket.getUserName() + ": "
							+ bodySource.substring(bodySource.indexOf("Henkilökohtainen osio"), bodySource.indexOf("Henkilökohtainen osio") + 40));
				} else if (bodySource.indexOf("Huoltokatko") >= 0) {
					throw new MarketTemporarilyDownException(sellerMarket.getMarket().getName() + " temporarily down (maintenance break)");
				}
				/* We are logged on as somebody else. This can happen for example in the following case:
				 * Lister is running as TeamLauber and then a public question comes for another user,
				 * which requires us to log on as a different user. Don't switch user here (because that
				 * might screw up the other process), but rather throw an exception. */
				//logger.debug("bodyText = " + bodyText);
				logger.debug("htmlSource = " + bodySource);
				throw new MarketLoggedOnAsWrongUserException();
			}
			/* Ok, so we are already logged on as somebody. Logout the current user. */
			/*logger.info("First logging out before we can login to " + sellerMarket.getMarket().getName()
					+ " as " + sellerMarket.getSeller().getName());
			//selenium.open("https://salattu.huuto.net/fi/logout.php3");
			selenium.click("//a[@id='log_out']");*/
		} else {
			try {
				userLogonElement.clear();
				userLogonElement.sendKeys(sellerMarket.getUserName());
				WebElement userPasswordElement = driver.findElementOrNull(By.xpath("//input[@name='password']"));
				userPasswordElement.clear();
				userPasswordElement.sendKeys(Encryption.decrypt(sellerMarket.getPassword()));
				userPasswordElement.submit();
			} catch (WebDriverException e) {
				logger.debug("Problem logging on to Huutonet: ", e);
				throw new MarketTemporarilyDownException(sellerMarket.getMarket().getName() + " temporarily down (tried to logon)");
			}
		}
		driver.waitForElement(By.xpath("//a[@id='log_out']"));
		loggedOnAsCorrectUser(sellerMarket);
	}
	
	/* Makes sure that we are logged on as the correct user. If the shopping cart isn't empty
	 * the logon link will be farther down. */
	private void loggedOnAsCorrectUser(SellerMarket sellerMarket) throws MarketTemporarilyDownException, MarketLoggedOnAsWrongUserException {
		CustomWebDriver driver = seleniumRobot.getWebDriver();
		String loggedOnUser = null;
		WebElement firstLink = driver.findElementOrNull(By.xpath("//a[@id='log_out']/../a[1]/b"));
		WebElement signatureLink = null;
		if (firstLink == null) {
			firstLink = driver.findElementOrNull(By.xpath("//a[@id='log_out']/../a[1]"));
			if (firstLink == null || !firstLink.getText().startsWith("Ostoskori")) {
				logger.debug("Problem figuring out if we are logged on as the correct user: "
						+ (firstLink == null ? "firstLink = " + firstLink : " firstLink.getText = " + firstLink.getText()));
				throw new MarketTemporarilyDownException(sellerMarket.getMarket().getName() + " temporarily down (tried to logon)");
			}
			signatureLink = driver.findElementOrNull(By.xpath("//a[@id='log_out']/../a[2]/b"));
		} else {
			signatureLink = firstLink;
		}
		loggedOnUser = signatureLink.getText();
		logger.debug("loggedOnUser = " + loggedOnUser);
		if (!loggedOnUser.equals(sellerMarket.getUserName())) {
			throw new MarketLoggedOnAsWrongUserException("Logged on to Huutonet as " + loggedOnUser + " although we SHOULD have been logged on as " + sellerMarket.getUserName());
		}
	}

	@Override
	public void logout() {
		logger.debug("Logging out from Huutonet.");
		CustomSelenium selenium = seleniumRobot.getSelenium();
		try {
			selenium.open("https://salattu.huuto.net/fi/logout.php3");
		} catch (SeleniumException e) {
			logger.warn("Couldn't log out from huutonet");
			logger.debug("Couldn't log out from huutonet: ", e);
		}
	}

	@Override
	public boolean canHandleMarket(Market market) {
		return market.getName().equals(Market.HUUTONET_MARKET_NAME);
	}

	@Override
	public void givePositiveFeedback(List<Order> sameAdOrders, SellerMarket sellerMarket) throws LoggedOutFromMarketException, MarketLoggedOnAsWrongUserException {
		feedbackGiver.givePositiveFeedback(sameAdOrders,sellerMarket);
	}

	@Override
	public List<OrderFeedback> readFeedback(SellerMarket sellerMarket) throws Exception {
		return huutonetFeedbackReader.readFeedback(sellerMarket);
	}

	@Override
	public boolean answerPrivateQuestion(String subject, String question, String answer,
			String answerAddress, SellerMarket sellerMarket) throws LoggedOutFromMarketException, MarketLoggedOnAsWrongUserException {
		EmailSender.sendEmailBatch(sellerMarket.getSeller().getSystemEmail(),answerAddress,subject,answer);
		return true;
	}

	@Override
	public boolean answerPublicQuestion(Ad ad, String question, String answer,
			String answerAddress, SellerMarket sellerMarket) throws LoggedOutFromMarketException, MarketLoggedOnAsWrongUserException {
		return huutonetAnswerer.answerQuestion(ad, answer, question, sellerMarket);
	}

	@Override
	public List<Category> getCategories(SellerMarket sellerMarket) throws LoggedOutFromMarketException, MarketLoggedOnAsWrongUserException, MarketTemporarilyDownException {
		//StringBuffer strBuf = new StringBuffer();
		List<Category> categories = new ArrayList<Category>();
		CustomSelenium selenium = seleniumRobot.getSelenium();
		selenium.open("http://www.huuto.net/fi/newitem3.php?has_js=1");
		selenium.waitForPageToLoad();
		selenium.setTimeout(1000);
		
		/* Make sure that we aren't logged out. We can be logged out when we get here, for example in the following
		 * case: One process is fetching a public question from huutonet, then listing starts, the public question fetching
		 * process logs out and then finally we get here. */
		if (selenium.isElementPresent("//input[@name='userid']") || selenium.getBodyText().indexOf("Et ole kirjautunut palveluun") >= 0) {
			throw new LoggedOutFromMarketException();
		}
		loggedOnAsCorrectUser(sellerMarket);

		/* Make sure that we are logged on as the correct user. */
		/*String loggedOnUser = selenium.getText("//a[@id='log_out']/../a[1]/b");
		logger.debug("loggedOnUser = " + loggedOnUser);
		if (!loggedOnUser.equals(sellerMarket.getUserName())) {
			throw new MarketLoggedOnAsWrongUserException("Logged on to Huutonet as " + loggedOnUser + " although we SHOULD have been logged on as " + sellerMarket.getUserName());
		}*/

		String value;
		String label;
		int i = 1;
		//strBuf.append("cd /Huutonet\n");
		try {
			while (true) {
				try { Thread.sleep(1000); } catch (Exception e) {}
				selenium.select("//select[@id='maincategory']", "index=" + i);
				try { Thread.sleep(2000); } catch (Exception e) {}
				value = selenium.getSelectedValue("//select[@id='maincategory']");
				label = selenium.getSelectedLabel("//select[@id='maincategory']");
				if (value.equals("Valitse pääosasto") || label.equals("")) {
					i++;
					continue;
				}
				Category mainCategory = new Category();
				mainCategory.setMarketSpecId(Integer.parseInt(value.trim()));
				mainCategory.setName(label);
				//strBuf.append("mkdir -" + value + " " + label + "\n");
				//strBuf.append("cd " + label + "\n");
				int j = 1;
				try {
					try { Thread.sleep(2000); } catch (Exception e) {}
					while (true) {
						try { Thread.sleep(300); } catch (Exception e) {}
						selenium.select("//select[@id='subcategory']", "index=" + j);
						try { Thread.sleep(1000); } catch (Exception e) {}
						value = selenium.getSelectedValue("//select[@id='subcategory']");
						label = selenium.getSelectedLabel("//select[@id='subcategory']");
						if (value.equals("Valitse osasto") || label.equals("")) {
							j++;
							continue;
						}
						Category sub1Category = new Category();
						sub1Category.setParentCategory(mainCategory);
						sub1Category.setMarketSpecId(Integer.parseInt(value.trim()));
						sub1Category.setName(label);
						//strBuf.append("mkdir -" + value + " " + label + "\n");
						//strBuf.append("cd " + label + "\n");
						int k = 1;
						try {
							try { Thread.sleep(2000); } catch (Exception e) {}
							while (true) {
								try { Thread.sleep(300); } catch (Exception e) {}
								selenium.select("//select[@id='category']", "index=" + k);
								try { Thread.sleep(800); } catch (Exception e) {}
								value = selenium.getSelectedValue("//select[@id='category']");
								label = selenium.getSelectedLabel("//select[@id='category']");
								if (value.equals("Valitse kategoria") || label.equals("")) {
									k++;
									continue;
								}
								Category sub2Category = new Category();
								sub2Category.setParentCategory(sub1Category);
								sub2Category.setMarketSpecId(Integer.parseInt(value.trim()));
								sub2Category.setName(label);
								//strBuf.append("mkdir -" + value + " " + label + "\n");
								categories.add(sub2Category);
								k++;
							}
						} catch (SeleniumException se) {
							if (se.getMessage().indexOf("Illegal Index") < 0 && se.getMessage().indexOf("Index out of range") < 0) {
								throw se;
							}
						}
						if (k == 1) {
							categories.add(sub1Category);
						}
						//strBuf.append("cd ..\n");
						j++;
					}
				} catch (SeleniumException se) {
					if (se.getMessage().indexOf("Illegal Index") < 0 && se.getMessage().indexOf("Index out of range") < 0) {
						throw se;
					}
				}
				//strBuf.append("cd ..\n");
				i++;
			}
		} catch (SeleniumException se) {
			if (se.getMessage().indexOf("Illegal Index") < 0 && se.getMessage().indexOf("Index out of range") < 0) {
				throw se;
			}
		}
		logger.debug("Done fetching categories for Huutonet.");
		//logger.debug(strBuf.toString());
		return categories;
	}

	@Override
	public String getMarketName() {
		return Market.HUUTONET_MARKET_NAME;
	}

	@Override
	public String getUrlForMarketSalesId(String marketSalesId) {
		return "http://www.huuto.net/kohteet/" + marketSalesId;
	}

}