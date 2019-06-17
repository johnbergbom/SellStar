package fi.jonix.huutonet.order.focalprice;

import java.io.File;
import java.math.BigDecimal;
import java.math.MathContext;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.Select;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import fi.jonix.huutonet.domain.VirtualCoin;
import fi.jonix.huutonet.domain.model.AdTemplate;
import fi.jonix.huutonet.domain.model.Currency;
import fi.jonix.huutonet.domain.model.Image;
import fi.jonix.huutonet.domain.model.Order;
import fi.jonix.huutonet.domain.model.OrderBundle;
import fi.jonix.huutonet.domain.model.Product;
import fi.jonix.huutonet.domain.model.Provider;
import fi.jonix.huutonet.domain.model.ProviderQuestion;
import fi.jonix.huutonet.domain.model.Tracking;
import fi.jonix.huutonet.domain.model.dao.CurrencyDAO;
import fi.jonix.huutonet.domain.model.dao.ImageDAO;
import fi.jonix.huutonet.domain.model.dao.OrderDAO;
import fi.jonix.huutonet.domain.model.dao.ProductDAO;
import fi.jonix.huutonet.domain.model.dao.ProviderDAO;
import fi.jonix.huutonet.domain.model.dao.ProviderQuestionDAO;
import fi.jonix.huutonet.domain.model.dao.SettingsDAO;
import fi.jonix.huutonet.domain.model.dao.TrackingDAO;
import fi.jonix.huutonet.email.EmailSender;
import fi.jonix.huutonet.email.processor.EmailFilter;
import fi.jonix.huutonet.exception.CriticalSectionInfo;
import fi.jonix.huutonet.exception.PlaceOrderException;
import fi.jonix.huutonet.exception.ProductNotFoundAtProviderException;
import fi.jonix.huutonet.exception.ProviderTemporarilyDownException;
import fi.jonix.huutonet.exception.SoldOutAtProviderException;
import fi.jonix.huutonet.order.OrderShippedHandler;
import fi.jonix.huutonet.order.Price;
import fi.jonix.huutonet.order.ProviderHandler;
import fi.jonix.huutonet.order.ProviderOrder;
import fi.jonix.huutonet.robot.CustomSelenium;
import fi.jonix.huutonet.robot.CustomWebDriver;
import fi.jonix.huutonet.robot.SeleniumRobot;
import fi.jonix.huutonet.tools.Encryption;
import fi.jonix.huutonet.tools.MathUtilities;
import fi.jonix.huutonet.tools.StringUtilities;

@Component(value = "focalPriceProvider")
public class FocalPriceProvider extends ProviderOrder {
	
	public static final Logger logger = Logger.getLogger(FocalPriceProvider.class);

	//public static String[] UPLOAD_FILE_HEADERS = { "ShipMethod", "IsRegister", "First Name", "Last Name", "Address", "City", "State", "ZIP", "CountryCode", "Your Order Number" };
	
	private static String INTIMATE_GADGETS = "Intimate Gadgets";
	private static String UNDERWEAR = "Underwear";
	private static String JEWELRY = "Jewelry";
	public static String WRONG_CURRENCY_IN_USE = "Wrong currency in use";

	@Autowired
	private SeleniumRobot seleniumRobot;
	
	@Autowired
	private ProviderDAO providerDAO;

	@Autowired
	private ProductDAO productDAO;

	@Autowired
	private SettingsDAO settingsDAO;

	@Autowired
	private ImageDAO imageDAO;
	
	@Autowired
	private TrackingDAO trackingDAO;
	
	@Autowired
	private ProviderQuestionDAO providerQuestionDAO;
	
	@Autowired
	private OrderDAO orderDAO;
	
	@Autowired
	private CurrencyDAO currencyDAO;
	
	@Override
	public boolean cancelOrder(Order order) throws Exception {
		OrderBundle bundle = order.getOrderBundle();
		//ProviderQuestion providerQuestion = providerQuestionDAO.getProviderQuestionForBundle(bundle);
		List<ProviderQuestion> providerQuestionList = providerQuestionDAO.getProviderQuestionForBundle(bundle);
		ProviderQuestion providerQuestion = null;
		if (providerQuestionList.size() > 0) {
			providerQuestion = providerQuestionList.get(0);
		}
		String userName = bundle.getSeller().getUser().getName();
		if (userName.indexOf(" ") > 0) {
			userName = userName.substring(0,userName.indexOf(" "));
		}
		addQuestionToProvider(providerQuestion, bundle,
				(providerQuestion != null ? null : "Please cancel this order"),
				"Hi,\n\nPlease cancel this one and give me a refund (as money, no coupon).\n\nRegards,\n" + userName,
				ProviderHandler.PROVIDER_CONVERSATION_TYPE_CANCELATION);
		return true;
	}

	@Override
	public void loadProductPage(String providerProductId) {
		loadProductPage(providerProductId,3,"60000");
	}

	@Override
	public void loadProductPage(String providerProductId, int retries,
			String timeout) {
		CustomSelenium selenium = seleniumRobot.getSelenium();
		// Try several times, because FocalPrice is sometimes down...
		for (int i = 0; i < retries; i++) {
			try{
				selenium.open(this.getProviderProductUrl(providerProductId), 1, timeout);
			} catch (Exception e){}
			selenium.waitForPageToLoad();
			//if (selenium.isElementPresent("//li[contains(@class, 'd_psku')]/span")) {
			if (selenium.isElementPresent("//em[@id='sku']")) {
				break;
			}
		}
	}

	private Price queryPriceThroughAPI(Product product) throws ProviderTemporarilyDownException, SoldOutAtProviderException {
		/* Call the AjaxPrice API. */
		HttpClient client = new DefaultHttpClient();
		HttpGet get = new HttpGet("http://www.focalprice.com/AjaxPrice?sku=" + product.getProviderProdId());
		String currencyStr = null;
		String unitPrice = null;
		String marketPrice = null;
		String jsonRespStr = "";
		try {
			logger.debug("Calling AjaxPrice for queryPriceThroughAPI");
			HttpResponse resp = client.execute(get);
			logger.debug("Returned from AjaxPrice for queryPriceThroughAPI");
			HttpEntity resEntity = resp.getEntity();
			jsonRespStr = EntityUtils.toString(resEntity);
			logger.debug("jsonRespStr = " + jsonRespStr);
			JSONObject jsonObj = new JSONObject(jsonRespStr);
			//currencyStr = jsonObj.getString("cruuenyValue");
			currencyStr = jsonObj.getString("currencyValue");
			logger.debug("currencyStr = " + currencyStr);
			unitPrice = jsonObj.getString("UnitPrice");
			logger.debug("unitPrice = " + unitPrice);
			try {
				marketPrice = jsonObj.getString("MarketPrice");
			} catch (JSONException e) {
				/* This is not a problem. The MarketPrice tag only exists for products
			       having a discount. */
			}
			logger.debug("marketPrice = " + marketPrice);
		} catch (JSONException e) {
			if (jsonRespStr.indexOf("An unhandled exception occurred during the execution of the current web request") >= 0) {
				logger.error("FocalPrice bailed out with unexpected error for AjaxPrice (for item " + product.getProviderProdId()
						+ ". This error normally comes if we call the AjaxPrice API using a non-existing product item."
						+ " However this error shouldn't have come here since non-existing products should have been"
						+ " already detected earlier.");
				throw new RuntimeException("FocalPrice bailed out with unexpected error1 for AjaxPrice.");
			} else {
				logger.error("FocalPrice bailed out with unexpected error for AjaxPrice (for item " + product.getProviderProdId()
						+ ". JSON parsing failed for string " + jsonRespStr + ". Error: ",e);
				throw new RuntimeException("FocalPrice bailed out with unexpected error2 for AjaxPrice.");
			}
		} catch (Exception e) {
			logger.error("Error getting price from FocalPrice for item " + product.getProviderProdId() + ": " + e.getMessage());
			logger.debug("Error getting price from FocalPrice for item " + product.getProviderProdId() + ": ", e);
			throw new ProviderTemporarilyDownException("FocalPrice temporarily down.");
		}
		
		/* Make some error checking. */
		Provider provider = providerDAO.getProviderByName(Provider.FOCAL_PRICE_PROVIDER_NAME);
		if (provider.getVatPercentage().compareTo(BigDecimal.ZERO) != 0) {
			throw new RuntimeException("Has the vat at " + provider.getName() + " changed?");
		}
		Currency expectedCurrency = provider.getCurrency();
		Long usedCurrency = null;
		if (currencyStr.trim().equals("US$")) {
			usedCurrency = Currency.DOLLAR;
		} else if (currencyStr.trim().equals("€")) {
			usedCurrency = Currency.EURO;
		}
		if (usedCurrency == null || !usedCurrency.equals(expectedCurrency.getId())) {
			logger.debug("currency '" + currencyStr + "' (expected = " + expectedCurrency.getName() + ")");
			throw new RuntimeException(WRONG_CURRENCY_IN_USE + " in getPrice.");
		}
	
		/* Always use the nominal price and not discounted prices. If marketPrice exists, then it
		 * tell's the nominal price (and the discounted price is in unitPrice). */
		String priceString = null;
		if (marketPrice != null && !marketPrice.trim().equals("")) {
			priceString = marketPrice;
			//priceString = priceString.substring(priceString.indexOf("$")+1);
		} else if (unitPrice != null && !unitPrice.trim().equals("")) {
			priceString = unitPrice;
		}
		logger.debug("Price of product is '" + priceString + "'");
		if (priceString == null || priceString.trim().equals("")) {
			throw new ProviderTemporarilyDownException("FocalPrice temporarily down.");
		}
		BigDecimal price = new BigDecimal(priceString);
		if (price == null || price.doubleValue() < 0.2) {
			throw new SoldOutAtProviderException("Item seems to be sold out because price is too low.");
		}
		
		return new Price(price,true,expectedCurrency,provider.getVatPercentage());
	}
	
	@Override
	public Price getPrice(Product product, boolean sloppyStockChecking) throws SoldOutAtProviderException, ProductNotFoundAtProviderException, ProviderTemporarilyDownException {
		try {
			/* For products that are sold offline we actually need to use getOfflinePrice, i.e.
			 * actually place an order without paying for it. The AjaxPrice and QueryStockStatus API's
			 * do return values also for offline items, however they keep doing it even if a product
			 * is no longer for sale. Therefore we throw an exception here to make sure getOfflinePrice
			 * is called below. */
			if (product.getOfflineSale()) {
				throw new ProductNotFoundAtProviderException("Dummy");
			}
			//productExistsAtProvider(providerProductId);
			//this.loadProductPage(providerProductId,1);
			//logger.debug("Setting page load timeout");
			//driver.manage().timeouts().pageLoadTimeout(7,TimeUnit.SECONDS);
			if (!this.isProductInStock(product,sloppyStockChecking)) {
				throw new SoldOutAtProviderException("Product is out of stock");
			}
			return queryPriceThroughAPI(product);
		} catch (ProductNotFoundAtProviderException e) {
			if (product.getOfflineSale() || product.getNotFoundCounter() > 0) {
				logger.debug("Check for offline sale for product " + product.getId() + " (" + product.getProviderProdId() + ").");
				Price price = getOfflinePrice(product,sloppyStockChecking);
				if (!product.getOfflineSale()) {
					logger.debug("Product " + product.getId() + " (" + product.getProviderProdId() + ") was moved to offline sales.");
					product.setOfflineSale(true);
				}
				return price;
			} else {
				logger.debug("Don't yet check for offline sale for product " + product.getId() + " (" + product.getProviderProdId() + ").");
				throw e;
			}
		}
	}

	private Currency checkCurrency(OrderBundle bundle) throws ProviderTemporarilyDownException {
		CustomWebDriver driver = seleniumRobot.getWebDriver();
		String currencyElementText = null;
		Currency expectedCurrency = null;
		Long usedCurrency = null;
		int i = 0;
		while (true) {
			WebElement currencyElement = null;
			try {
				/* In some cases the wrong currency is shown at page loading time and
				 * then the correct currency has been set when the page has loaded.
				 * Therefore we keep a small delay here. */
				try { Thread.sleep(1000); } catch (Exception e) { }
				currencyElement = driver.findElement(By.xpath("//a[@id='currentCurrency']/em"));
				currencyElementText = currencyElement.getText();
			} catch (NoSuchElementException e) {
				throw new ProviderTemporarilyDownException("Couldn't get current currency in use.");
			}
			
			/* Find out the expected currency. If we are actually ordering something, then the expected currency
			 * should be taken from bundle.getUsedProviderCurrency(), or otherwise from the provider. */
			if (bundle != null) {
				expectedCurrency = bundle.getUsedProviderCurrency();
			} else {
				expectedCurrency = providerDAO.getProviderByName(Provider.FOCAL_PRICE_PROVIDER_NAME).getCurrency();
			}
			
			if (currencyElementText.trim().equals("US$")) {
				usedCurrency = Currency.DOLLAR;
			} else if (currencyElementText.trim().equals("€")) {
				usedCurrency = Currency.EURO;
			}
			if (usedCurrency != null && usedCurrency.equals(expectedCurrency.getId())) {
				break;
			} else {
				if (i > 0) {
					/* The FocalPrice web pages sometimes keeps showing a currency other than dollars
					 * at the top of the page although we change it using the drop down list. However
					 * we can still return dollars here since the used currency is always checked in
					 * every step where monetary amounts are involved (and it's normally in dollars
					 * even when this currency change fails). */
					logger.debug(WRONG_CURRENCY_IN_USE + " in checkCurrency (currency '" + currencyElementText
							+ "', expected = " + expectedCurrency.getName() + "). Failed to automatically change currencies,"
							+ " however FocalPrice usually still uses dollars, so we can safely use dollars here since"
							+ " the used currency is checked in every place where monetary amounts are fetched from the"
							+ " web pages.");
					//throw new RuntimeException(WRONG_CURRENCY_IN_USE + " in checkCurrency (currency '" + currencyElementText
						//	+ "', expected = " + expectedCurrency.getName() + "). Failed to automatically change currencies.");
					usedCurrency = expectedCurrency.getId();
					break;
				}
				/* Sometimes the wrong currency is used, so here let's change the currency to the
				 * correct one. */
				logger.debug(WRONG_CURRENCY_IN_USE + " in checkCurrency (currency '" + currencyElementText
						+ "', expected = " + expectedCurrency.getName() + "). Trying to switch automatically.");
				
				/* In order to change currencies we need to press the USD link of a drop down
				 * menu that's only shown by javascript when hoovering over the menu. With
				 * selenium-2 we cannot press a hidden link, so therefore we first need to
				 * make the dropdown menu visible using javascript. */
				logger.debug("Showing currency drop down menu.");
		        String showDropDownElementScript = "function showDropDownElementHack() {"
		        	+ "  var obj = document.getElementById('currency');"
		        	+ "  for (var i = 0; i < obj.childNodes.length; i++) {"
		        	+ "    if (obj.childNodes[i].className == 'currency_menu down_men') {"
		        	+ "      obj.childNodes[i].style.opacity = 30;"
		        	+ "      obj.childNodes[i].style.display = '';"
		        	+ "    }"
		        	+ "  }"
		        	+ "} showDropDownElementHack();";
		        driver.executeScript(showDropDownElementScript);
		        try { Thread.sleep(500); } catch (Exception e) { }

				WebElement dollarLink = driver.findElement(By.xpath(".//*[@class='currency_menu down_men']/a[1]"));
				logger.debug("dollarLink.getAttribute(class) = " + dollarLink.getAttribute("class"));
				if (!dollarLink.getAttribute("class").equals("USD")) {
					throw new RuntimeException(WRONG_CURRENCY_IN_USE + " in checkCurrency (couldn't change automatically).");
				}
				dollarLink.click();
				try { Thread.sleep(2*1000); } catch (Exception e) { }
				logger.debug("Currency changed.");
			}
			i++;
		}
		
		if (usedCurrency == null || !usedCurrency.equals(expectedCurrency.getId())) {
			logger.debug("currency '" + currencyElementText + "' (expected = " + expectedCurrency.getName() + ")");
			throw new RuntimeException(WRONG_CURRENCY_IN_USE + " in checkCurrency.");
		}
		return expectedCurrency;
	}
	
	private Price getOfflinePrice(Product product, boolean sloppyStockChecking) throws ProductNotFoundAtProviderException, ProviderTemporarilyDownException {
		logger.debug("Getting offline price for product " + product.getId());
		boolean pressedFirstSubmit = false;
		boolean chosenShippingType = false;
		boolean possiblySystematicError = true;
		try {
			startPlaceOrders(null);
			List<Product> prodList = new ArrayList<Product>();
			prodList.add(product);
			int nbrUniqueProducts = addProductsForPlaceOrders(prodList);
			enterShippingDetailsForPlaceOrders("test", "test", "test 1", "test", "12345");
			pressedFirstSubmit = true;
			boolean useTracking = false;
			setShippingMethodForPlaceOrders(useTracking);
			chosenShippingType = true;
			Currency usedCurrency = checkCurrency(null);
	
			/* Get the price(s). */
			VirtualCoin totalSumInclVatPC = getGrandTotalForPlaceOrders(usedCurrency);
			VirtualCoin subTotal = getSubTotalForPlaceOrders(usedCurrency);
			VirtualCoin shippingCostInclVatPC = null;
			try {
				shippingCostInclVatPC = getShippingCostInclVatPCForPlaceorders(usedCurrency,useTracking,totalSumInclVatPC,subTotal);
			} catch (PlaceOrderException e) {
				throw new RuntimeException("Error in getOfflinePrice",e);
			}
			BigDecimal discountRate = getDiscountRate();
			/*BigDecimal totalSumEuroInclVat = currencyDAO.convertCurrency(totalSumInclVatPC.getCurrency(), Currency.EURO, totalSumInclVatPC.getValue());
			BigDecimal shippingCostEuroInclVat = currencyDAO.convertCurrency(shippingCostInclVatPC.getCurrency(), Currency.EURO, shippingCostInclVatPC.getValue());
			BigDecimal totalSumEuroExclVat = MathUtilities.getPriceExclVat(totalSumEuroInclVat, product.getProvider().getVatPercentage());*/
			
			String productId = getProductIdOfRowForPlaceOrders(0);
			if (!product.getProviderProdId().equals(productId)) {
				throw new RuntimeException("Error in getOfflinePrice: provProductId " + productId
						+ " is different than that of product " + product.getId() + " (" + product.getProviderProdId() + ").");
			}
			VirtualCoin unitPriceInclVatPC = getUnitPriceOfRowForPlaceOrders(0,usedCurrency);
			logger.debug("Offline price for product " + product.getId() + " (" + product.getProviderProdId() + "): "
					+ unitPriceInclVatPC.getValue().toPlainString());

			/* If we get here it means that all expected products showed up on the list, so we can conclude that
			 * there is most likely no systematic error for this one. (In case we have a multi-order bundle
			 * where some product no longer exists at the provider, then usually the existing products will
			 * show up on the list but the non-existing ones will not show up.) */
			possiblySystematicError = false;

			try {
				StockStatus stockStatus = queryStockStatusThroughAPI(product);
				//TODO: once we know better whether we can trust the QueryStockStatus stock information for
				//offline sales, then we can possibly start using it here.
				logger.debug("Stock status for offline item " + product.getId() + ": stockDescription = "
						+ stockStatus.getStockDescription() + ", shippingTime = " + stockStatus.getShippingTime()
						+ ", allowBuy = " + stockStatus.getAllowBuy()
						+ ", old product state = " + product.getProductState() + ", not found counter = " + product.getNotFoundCounter());
			} catch (Exception e) {
				logger.error("Couldn't do stock status query for offline sales item, still continuing since it was only done for debugging purposes: ",e);
			}

			try {
				Price price = queryPriceThroughAPI(product);
				//TODO: once we know better whether we can trust the AjaxPrice stock information for
				//offline sales, then we can possibly start using it here.
				logger.debug("AjaxPrice for offline item " + product.getId() + ": currency = "
						+ price.getCurrency() + ", price = " + price.getPriceInclVat()
						+ ", old product state = " + product.getProductState() + ", not found counter = " + product.getNotFoundCounter());
				if (price.getPriceInclVat().getValue().compareTo(unitPriceInclVatPC.getValue()) == 0) {
					logger.debug("AjaxPrice same for offline item " + product.getId());
				} else {
					logger.debug("AjaxPrice different for offline item " + product.getId() + ": "
							+ price.getPriceInclVat().getValue().toPlainString() + ", " + unitPriceInclVatPC.getValue().toPlainString());
				}
			} catch (Exception e) {
				logger.error("Couldn't do ajax price query for offline sales item, still continuing since it was only done for debugging purposes: ",e);
			}

			return new Price(unitPriceInclVatPC.getValue(),true,usedCurrency,product.getProvider().getVatPercentage());
		} catch (WebDriverException e) {
			handlePlaceOrderError(null, product, pressedFirstSubmit,
					chosenShippingType,possiblySystematicError,e);
			throw new ProductNotFoundAtProviderException("Product not found at FocalPrice");
		} finally {
			/* Remove dangling orders. */
			/*logger.debug("Removing dangling orders in getOfflinePrice for product " + product.getId());
			try {
				openDropShippingPageForPlaceOrders(null);
				removeDanglingOrdersForPlaceOrders();
				enterShippingDetailsForPlaceOrders("test", "test", "test 1", "test", "12345");
				logger.debug("Removed dangling orders in getOfflinePrice for product " + product.getId());
			} catch (Exception e) {
				// This is not such a big deal, so just print an error message here
				logger.error("Removal of dangling orders failed for product " + product.getId() + ": ", e);
			}
			logout();*/
		}
	}
	
	private void handlePlaceOrderError(OrderBundle bundle, Product product, boolean pressedFirstSubmit,
			boolean chosenShippingType, boolean possiblySystematicError, Throwable e) throws ProviderTemporarilyDownException {
		if (!pressedFirstSubmit) {
			logger.info("Provider seems temporarily down, first submit not pressed (for "
					+ (bundle != null ? "bundle " + bundle.getId() : "product " + product.getId()
							+ ", " + product.getProviderProdId()) + ") - doesn't require any special action): ",e);
			throw new ProviderTemporarilyDownException("FocalPrice temporarily down.");
		}
		/* In some cases pressing the submit button returns the same page. This usually
		 * mean that there is some error with the product and that retrying later
		 * won't necessarily help. Usually it means that a non-existing provider product
		 * id was provided (=the product is no longer for sale at FocalPrice) or that
		 * the product is out of stock (having the message "Out of Stock") so that
		 * FocalPrice doesn't accept placing orders for that item. Determine if this is
		 * the case. */
		boolean likelySystematicError = false;
		if (!chosenShippingType) {
			try {
				CustomWebDriver driver = seleniumRobot.getWebDriver();
				WebElement firstName = driver.findElement(By.name("FirstName"));
				/* We are at the starting page if we get here. */
				//throw new PlaceOrderException("FocalPrice doesn't accept ordering for bundle " + bundle.getId(),false);
				likelySystematicError = true;
			} catch (Exception e2) {
				// We are not at the starting page if we get here, so this seems like a normal temporary error.
				if (bundle != null) {
					logger.info("Provider seems temporarily down (for bundle "
							+ bundle.getId() + ") - doesn't require any special action): ",e);
				} else {
					logger.info("Provider seems temporarily down (for product "
							+ product.getId() + ", " + product.getProviderProdId() + ") - doesn't require any special action): ",e);
				}
				throw new ProviderTemporarilyDownException("FocalPrice temporarily down.");
			}
		}

		if (likelySystematicError) {
			if (bundle != null) {
				logger.info("Likely systematic error for bundle "
						+ bundle.getId() + " ( - doesn't require any special action): ",e);
			} else {
				logger.info("Likely systematic error for product "
						+ product.getId() + " (" + product.getProviderProdId() + " - doesn't require any special action): ",e);
			}
		} else if (possiblySystematicError) {
			if (bundle != null) {
				logger.info("Possibly systematic error for bundle "
						+ bundle.getId() + " ( - doesn't require any special action): ",e);
			} else {
				logger.info("Possibly systematic error for product "
						+ product.getId() + " (" + product.getProviderProdId() + " - doesn't require any special action): ",e);
			}
		} else {
			throw new ProviderTemporarilyDownException("FocalPrice temporarily down.");
		}
	}

	private void startPlaceOrders(OrderBundle bundle) throws ProviderTemporarilyDownException {
		/*if (bundle != null) {
			login(bundle.getProvider(),3);
		} else {
			Provider provider = providerDAO.getProviderByName(Provider.FOCAL_PRICE_PROVIDER_NAME);
			login(provider,3);
		}*/
		openDropShippingPageForPlaceOrders(bundle);
		removeDanglingOrdersForPlaceOrders();
	}
	
	private void openDropShippingPageForPlaceOrders(OrderBundle bundle) throws ProviderTemporarilyDownException {
		CustomWebDriver driver = seleniumRobot.getWebDriver();
		logger.info("Opening drop shipping page");
		driver.get("http://www.focalprice.com/DropShopping/Place/Single",By.name("FirstName"),5);
		checkCurrency(bundle);
	}
	
	private void removeDanglingOrdersForPlaceOrders() throws ProviderTemporarilyDownException {
		/* Remove old products from the list (if any). */
		CustomWebDriver driver = seleniumRobot.getWebDriver();
		WebElement removeProductElement = driver.findElementOrNull(By.className("Remove"));
		int nbrRemoved = 0;
		while (removeProductElement != null) {
			logger.debug("Old row exists, removing.");
			removeProductElement.click();
			try { Thread.sleep(1*1000); } catch (Exception e) { } //give time for javascript
			removeProductElement = driver.findElementOrNull(By.className("Remove"));
			nbrRemoved++;
			if (nbrRemoved > 10) {
				logger.error("Removing too many elements (suspicious): " + nbrRemoved);
				throw new RuntimeException("Removing too many elements (suspicious): " + nbrRemoved);
			}
		}

		WebElement skuElement = driver.findElement(By.xpath("//*[@id='ListTable']//tbody/tr[1]/td[2]/input"));
		skuElement.clear();
		skuElement.sendKeys("");
		WebElement quantityElement = driver.findElement(By.xpath("//*[@id='ListTable']//tbody/tr[1]/td[4]/input"));
		quantityElement.clear();
		quantityElement.sendKeys("1");
		
		if (nbrRemoved > 0) {
			logger.debug("Removed " + nbrRemoved + " elements.");
		}
	}
	
	private int addProductsForPlaceOrders(List<Product> prodList) {
		/* Add the orders. If several identical products are ordered, then those need to be placed
		 * on the same line with quantity > 1. */
		CustomWebDriver driver = seleniumRobot.getWebDriver();
		WebElement addProductElement = driver.findElement(By.id("addProduct"));
		Map<String,Long> productCountMap = new HashMap<String,Long>();
		for (Product product : prodList) {
			if (productCountMap.get(product.getProviderProdId()) == null) {
				productCountMap.put(product.getProviderProdId(), new Long(0));
			}
			Long amount = productCountMap.get(product.getProviderProdId());
			productCountMap.put(product.getProviderProdId(), new Long(amount.longValue()+1));
		}
		int nbrUniqueProducts = 0;
		for (Product product : prodList) {
			if (productCountMap.get(product.getProviderProdId()) != null) {
				if (nbrUniqueProducts > 0) {
					/* Add another product row. */
					logger.debug("Adding another product row.");
					addProductElement.click();
					try { Thread.sleep(1*1000); } catch (Exception e) { } //give time for javascript
				}
				WebElement skuElement = driver.findElement(By.xpath("//*[@id='ListTable']//tbody/tr[" + (nbrUniqueProducts+1) + "]/td[2]/input"));
				skuElement.clear();
				skuElement.sendKeys(product.getProviderProdId());
				WebElement quantityElement = driver.findElement(By.xpath("//*[@id='ListTable']//tbody/tr[" + (nbrUniqueProducts+1) + "]/td[4]/input"));
				quantityElement.clear();
				quantityElement.sendKeys(productCountMap.get(product.getProviderProdId()).toString());
				nbrUniqueProducts++;
				productCountMap.put(product.getProviderProdId(), null);
			}
		}
		return nbrUniqueProducts;
	}
	
	/* Enter the shipping details. */
	private void enterShippingDetailsForPlaceOrders(String firstName, String lastName, String address, String city, String postCode) {
		CustomWebDriver driver = seleniumRobot.getWebDriver();
		WebElement firstNameElem = driver.findElement(By.name("FirstName"));
		firstNameElem.clear();
		firstNameElem.sendKeys(StringUtilities.toUSASCIICharacters(firstName));
		WebElement lastNameElem = driver.findElement(By.name("LastName"));
		lastNameElem.clear();
		lastNameElem.sendKeys(StringUtilities.toUSASCIICharacters(lastName));
		WebElement addressElem = driver.findElement(By.name("Address1"));
		addressElem.clear();
		addressElem.sendKeys(StringUtilities.toUSASCIICharacters(address));
		Select selectElem = new Select(driver.findElement(By.name("CountryCode")));
		//select.deselectAll();
		selectElem.selectByVisibleText("Finland");
		WebElement cityElem = driver.findElement(By.name("City"));
		cityElem.clear();
		cityElem.sendKeys(StringUtilities.toUSASCIICharacters(city));
		//WebElement province = driver.findElement(By.name("Province"));
		//province.clear();
		//province.sendKeys(StringUtilities.toUSASCIICharacters(bundle.getCity()));
		WebElement postCodeElem = driver.findElement(By.name("PostCode"));
		postCodeElem.clear();
		postCodeElem.sendKeys(postCode);
		logger.info("Will press submit");
		firstNameElem.submit();
	}
	
	/* Set the correct shipping method. */
	private void setShippingMethodForPlaceOrders(boolean useTracking) {
		CustomWebDriver driver = seleniumRobot.getWebDriver();
		WebElement shippingMethod = null;
		if (useTracking) {
			shippingMethod = driver.findElement(By.id("RegisterAirmail"));
		} else {
			shippingMethod = driver.findElement(By.id("Airmail"));
		}
		if (!shippingMethod.isSelected()) {
			shippingMethod.click();
			try { Thread.sleep(7*1000); } catch (Exception e) { } //wait for ajax to load a new page
			driver.waitForElement(By.id("GrandTotal")); //wait for page to load
		}
	}
	
	/* Get the grand total. */
	private VirtualCoin getGrandTotalForPlaceOrders(Currency usedCurrency) {
		CustomWebDriver driver = seleniumRobot.getWebDriver();
		WebElement grandTotalElement = driver.findElement(By.id("GrandTotal"));
		String grandTotalStr = grandTotalElement.getText();
		logger.debug("grandTotalStr = \"" + grandTotalStr + "\"");
		if ((usedCurrency.getId().equals(Currency.DOLLAR) && grandTotalStr.indexOf("US$") < 0)
				|| grandTotalStr.indexOf(usedCurrency.getSymbol()) < 0) {
			throw new RuntimeException(WRONG_CURRENCY_IN_USE + " in getGrandTotalForPlaceOrders.");
		}
		grandTotalStr = grandTotalStr.substring(grandTotalStr.indexOf(usedCurrency.getSymbol())+1); //.replaceFirst("US\\$","");
		return new VirtualCoin(usedCurrency,new BigDecimal(grandTotalStr.trim()));
	}
	
	private VirtualCoin getSubTotalForPlaceOrders(Currency usedCurrency) {
		CustomWebDriver driver = seleniumRobot.getWebDriver();
		WebElement subTotalElement = driver.findElement(By.id("ItemsSubtotal"));
		String subTotalElementStr = subTotalElement.getText();
		if ((usedCurrency.getId().equals(Currency.DOLLAR) && subTotalElementStr.indexOf("US$") < 0)
				|| subTotalElementStr.indexOf(usedCurrency.getSymbol()) < 0) {
			logger.debug("expected currency = " + usedCurrency.getName() + " but still subTotalElementStr = " + subTotalElementStr);
			throw new RuntimeException(WRONG_CURRENCY_IN_USE + " in getSubTotalForPlaceOrders.");
		}
		String subTotalStr = subTotalElementStr.substring(subTotalElementStr.indexOf(usedCurrency.getSymbol())+1); //.replaceFirst("US\\$","");
		logger.debug("subTotalStr = \"" + subTotalStr + "\"");
		return new VirtualCoin(usedCurrency,new BigDecimal(subTotalStr.trim()));
	}
	
	private VirtualCoin getShippingCostInclVatPCForPlaceorders(Currency usedCurrency, boolean useTracking, VirtualCoin totalSumInclVatPC, VirtualCoin subTotal) throws PlaceOrderException {
		CustomWebDriver driver = seleniumRobot.getWebDriver();
		WebElement shippingCostPCElement = driver.findElement(By.id("ShippingCharges"));
		String shippingCostPCElementStr = shippingCostPCElement.getText();
		if ((usedCurrency.getId().equals(Currency.DOLLAR) && shippingCostPCElementStr.indexOf("US$") < 0)
				|| shippingCostPCElementStr.indexOf(usedCurrency.getSymbol()) < 0) {
			logger.debug("expected currency = " + usedCurrency.getName() + " but still shippingCostInDollarsElementStr = " + shippingCostPCElementStr);
			throw new RuntimeException(WRONG_CURRENCY_IN_USE + " in getShippingCostInclVatPCForPlaceorders.");
		}
		String shippingCostPCStr = shippingCostPCElementStr.substring(shippingCostPCElementStr.indexOf(usedCurrency.getSymbol())+1); //.replaceFirst("US\\$","");
		logger.debug("shippingCostPCStr = \"" + shippingCostPCStr + "\"");
		VirtualCoin shippingCostInclVatPC = new VirtualCoin(usedCurrency,new BigDecimal(shippingCostPCStr.trim()));
		/* Error checking. */
		if (!useTracking && shippingCostInclVatPC.getValue().compareTo(BigDecimal.ZERO) != 0) {
			/* Note: at least with the current implementation it's not even possible to choose unregistered
			 * airmail if the price BEFORE discount is >= 20 dollars and the price after the discount is < 20 dollars
			 * (the radiobutton for this exists, but even after pressing this we still get registered airmail), so
			 * this code could possibly crash on that. */
			throw new PlaceOrderException("Have shipping charges changed at FocalPrice? (" + shippingCostInclVatPC.getValue().toPlainString()
					+ " - shipping cost is non zero although we didn't use tracking).");
		} else if (subTotal.getValue().add(shippingCostInclVatPC.getValue()).compareTo(totalSumInclVatPC.getValue()) != 0) {
			throw new PlaceOrderException("Error: subTotal (" + subTotal.getValue().toPlainString() + ") + shippingCharges ("
					+ shippingCostInclVatPC.getValue().toPlainString() + ") != totalSumPC (" + totalSumInclVatPC.getValue() + ").");
		}
		return shippingCostInclVatPC;
	}
	
	/* Get the used discount. */
	private BigDecimal getDiscountRate() {
		CustomWebDriver driver = seleniumRobot.getWebDriver();
		WebElement discountElement = driver.findElement(By.id("Discount"));
		String discountStr = discountElement.getText();
		logger.debug("discountStr1 = " + discountStr);
		discountStr = discountStr.substring(0,discountStr.indexOf("%"));
		logger.debug("discountStr2 = " + discountStr);
		BigDecimal discount = new BigDecimal(discountStr.trim());
		BigDecimal discountRate = (new BigDecimal("100")).subtract(discount).divide(new BigDecimal("100"),MathContext.DECIMAL64);
		logger.debug("discountRate = " + discountRate);
		return discountRate;
	}
	
	private void logInfoForPlaceOrders(VirtualCoin totalSumInclVatPC, VirtualCoin shippingCostInclVatPC, BigDecimal totalSumEuroInclVat, BigDecimal shippingCostEuroInclVat, Currency currency) {
		BigDecimal discountedTotalProviderPricePC = totalSumInclVatPC.getValue().subtract(shippingCostInclVatPC.getValue());
		//BigDecimal discountedTotalProviderPriceEuro = currencyChecker.getDollar2Euro(discountedTotalProviderPricePC);
		BigDecimal discountedTotalProviderPriceEuro = currencyDAO.convertCurrency(totalSumInclVatPC.getCurrency(), Currency.EURO, discountedTotalProviderPricePC);
		BigDecimal discountedTotalProviderPriceMC = currencyDAO.convertCurrency(totalSumInclVatPC.getCurrency(), currency.getId(), discountedTotalProviderPricePC);
		BigDecimal shippingCostMC = currencyDAO.convertCurrency(shippingCostInclVatPC.getCurrency(), currency.getId(), shippingCostInclVatPC.getValue());
		logger.debug("Provider total discounted bundle price = " + totalSumInclVatPC.getValue() + " dollars (= " + totalSumEuroInclVat + " euros)"
				+ ", shipping cost = " + shippingCostInclVatPC.getValue() + " dollars (= "
				+ shippingCostEuroInclVat.toPlainString() + " euros, " + shippingCostMC.toPlainString()
				+ " " + currency.getSymbol() + ")"
				+ ", discounted bundle price without shipping costs = " + discountedTotalProviderPricePC.toPlainString()
				+ " dollars (= " + discountedTotalProviderPriceEuro.toPlainString() + " euros, " + discountedTotalProviderPriceMC.toPlainString()
				+ " " + currency.getSymbol() + ")");
	}
	
	private String getProductIdOfRowForPlaceOrders(int rowNbr) {
		CustomWebDriver driver = seleniumRobot.getWebDriver();
		WebElement productElement = driver.findElement(By.xpath("//*[@id='order_table']/tbody/tr[" + (rowNbr+1) + "]/td[1]"));
		String productId = productElement.getText();
		logger.debug("productId = " + productId);
		return productId;
	}
	
	private VirtualCoin getUnitPriceOfRowForPlaceOrders(int rowNbr, Currency usedCurrency) {
		CustomWebDriver driver = seleniumRobot.getWebDriver();
		WebElement unitPriceElement = driver.findElement(By.xpath("//*[@id='order_table']/tbody/tr[" + (rowNbr+1) + "]/td[3]"));
		String unitPriceElementStr = unitPriceElement.getText();
		if ((usedCurrency.getId().equals(Currency.DOLLAR) && unitPriceElementStr.indexOf("US$") < 0)
				|| unitPriceElementStr.indexOf(usedCurrency.getSymbol()) < 0) {
			logger.debug("expected currency = " + usedCurrency.getName() + " but still unitPriceElementStr = " + unitPriceElementStr);
			throw new RuntimeException(WRONG_CURRENCY_IN_USE + " in getUnitPriceOfRowForPlaceOrders.");
		}
		String unitPriceStr = unitPriceElementStr.substring(unitPriceElementStr.indexOf(usedCurrency.getSymbol())+1); //.replaceFirst("US\\$","");
		logger.debug("unitPriceStr = \"" + unitPriceStr + "\"");
		VirtualCoin unitPrice = new VirtualCoin(usedCurrency,new BigDecimal(unitPriceStr.trim()));
		return unitPrice;
	}
	
	@Override
	public void placeOrder(OrderBundle bundle, List<Order> bundleOrders, String user, String password, CriticalSectionInfo section) throws PlaceOrderException, ProviderTemporarilyDownException {
		logger.info("Placing bundle order for bundle.id " + bundle.getId() + " to FocalPrice.");
		CustomWebDriver driver = seleniumRobot.getWebDriver();

		/* For this part it doesn't matter if the ordering bails out. */
		boolean resending = bundleOrders.get(0).getState().equals(Order.STATE_SHOULD_RESEND);
		boolean pressedFirstSubmit = false;
		boolean chosenShippingType = false;
		boolean possiblySystematicError = true;
		try {
			startPlaceOrders(bundle);
			List<Product> prodList = new ArrayList<Product>();
			for (Order order : bundleOrders) {
				prodList.add(order.getProduct());
			}
			int nbrUniqueProducts = addProductsForPlaceOrders(prodList);
			enterShippingDetailsForPlaceOrders(bundle.getFirstName(), bundle.getLastName(), bundle.getAddress(), bundle.getCity(), bundle.getPostcode());
			pressedFirstSubmit = true;
			setShippingMethodForPlaceOrders(bundle.getSeller().getTrackingAlways());
			chosenShippingType = true;
			Currency usedCurrency = checkCurrency(bundle);
			
			/* Get the price(s). */
			VirtualCoin totalSumInclVatPC = getGrandTotalForPlaceOrders(usedCurrency);
			VirtualCoin subTotal = getSubTotalForPlaceOrders(usedCurrency);
			VirtualCoin shippingCostInclVatPC = getShippingCostInclVatPCForPlaceorders(usedCurrency,bundle.getSeller().getTrackingAlways(),totalSumInclVatPC,subTotal);
			BigDecimal discountRate = getDiscountRate();
			BigDecimal totalSumEuroInclVat = currencyDAO.convertCurrency(totalSumInclVatPC.getCurrency(), Currency.EURO, totalSumInclVatPC.getValue());
			BigDecimal shippingCostEuroInclVat = currencyDAO.convertCurrency(shippingCostInclVatPC.getCurrency(), Currency.EURO, shippingCostInclVatPC.getValue());
			
			/* Set the shipping cost of the bundle. */
			setShippingCostAtOrderingFromProvider(bundle,shippingCostInclVatPC,resending);

			logInfoForPlaceOrders(totalSumInclVatPC,shippingCostInclVatPC,totalSumEuroInclVat,shippingCostEuroInclVat,bundle.getUsedCustomerCurrency());

			
			
			BigDecimal totalSumEuroExclVat = MathUtilities.getPriceExclVat(totalSumEuroInclVat, bundle.getProvider().getVatPercentage());
			checkTheTotalSum(totalSumEuroExclVat, bundle, bundleOrders);

			/* Go through each order row (which will be _less_ than the number of orders
			 * in bundleOrders if some product has a quantity > 1) and set the final provider
			 * price for each ordered product. */
			//WebElement orderTableElement = driver.findElement(By.id("order_table"));
			//List<WebElement> productRowElementList = orderTableElement.findElements(By.xpath("//*[@id='order_table']/tbody/tr"));
			//int nbrProductRows = productRowElementList.size();
			Map<Long,String> orderIdMap = new HashMap<Long,String>();
			BigDecimal totalDiscountedUnitPricePC = BigDecimal.ZERO;
			BigDecimal totalUnitPricePC = BigDecimal.ZERO;
			//for (WebElement productRowElement : productRowElementList) {
			for (int i = 0; i < nbrUniqueProducts; i++) {
				String productId = getProductIdOfRowForPlaceOrders(i);
				VirtualCoin unitPrice = getUnitPriceOfRowForPlaceOrders(i,usedCurrency);
				/*WebElement quantityElement = driver.findElement(By.xpath("//*[@id='order_table']/tbody/tr[" + (i+1) + "]/td[4]"));
				String quantity = quantityElement.getText();
				logger.debug("quantity = " + quantity);*/
				BigDecimal discountedUnitPricePCBD = new BigDecimal(MathUtilities.roundTo2Decimals
						(unitPrice.getValue().multiply(discountRate)));
				VirtualCoin discountedUnitPriceInclVatPC = new VirtualCoin(usedCurrency,discountedUnitPricePCBD);
				//BigDecimal discountedUnitPriceInEuro = currencyChecker.getDollar2Euro(discountedUnitPricePC);
				BigDecimal discountedUnitPriceInEuro = currencyDAO.convertCurrency(usedCurrency.getId(), Currency.EURO, discountedUnitPriceInclVatPC.getValue());

				/* Go through all orders and update the provider price for the
				 * ones matching the above order row (more than one order will
				 * match if quantity > 1). */
				int nbrOrdersSet = 0;
				for (Order order : bundleOrders) {
					if (order.getProduct().getProviderProdId().equals(productId)) {
						if (orderIdMap.get(order.getId()) != null) {
							throw new PlaceOrderException("Error: trying to go through order " + order.getId() + " twice"
									+ " (for " + productId + ").");
						}
						orderIdMap.put(order.getId(),"");
						totalDiscountedUnitPricePC = totalDiscountedUnitPricePC.add(discountedUnitPriceInclVatPC.getValue());
						totalUnitPricePC = totalUnitPricePC.add(unitPrice.getValue());
						logger.debug("For order " + order.getId() + ": discountedUnitPricePC = "
								+ discountedUnitPriceInclVatPC.getValue() + ", discountedUnitPriceInEuro = " + discountedUnitPriceInEuro);
						setFinalProviderPriceAtOrderingFromProvider(order,discountedUnitPriceInclVatPC);
						nbrOrdersSet++;
					}
				}
			}
			/* If we get here it means that all expected products showed up on the list, so we can conclude that
			 * there is most likely no systematic error for this one. (In case we have a multi-order bundle
			 * where some product no longer exists at the provider, then usually the existing products will
			 * show up on the list but the non-existing ones will not show up.) */
			possiblySystematicError = false;
			logger.debug("Non-discounted total price (excl. shipping) = " + totalUnitPricePC);
			logger.debug("Discounted total price (excl. shipping) = " + totalDiscountedUnitPricePC);
			
			/* Safety check: make sure that the total discounted unit price is equal to the subtotal.
			 * =>UPDATE: don't check this, because it can be different because of rounding errors
			 * and that's corrected below. */
			/*if (subTotal.compareTo(totalDiscountedUnitPricePC) != 0) {
				throw new PlaceOrderException("Error: subTotal (" + subTotal.toPlainString() + ")"
						+ " != totalDiscountedUnitPricePC (" + totalDiscountedUnitPricePC + ").");
			}*/
			
			/* Error check: make sure that all orders in the bundle are gone through. */
			if (orderIdMap.size() != bundleOrders.size()) {
				throw new PlaceOrderException("Error: orderIdMap.size = " + orderIdMap.size()
						+ " and bundleOrder.size = " + bundleOrders.size());
			}
			
			/* When deciding if we get the shipping for free FocalPrice checks if the price BEFORE the
			 * discount is >= 20 dollars. */
			//if ((bundle.getSeller().getTrackingAlways() && totalDiscountedUnitPricePC.compareTo(new BigDecimal("20.00")) < 0 && shippingCostInDollars.compareTo(new BigDecimal("1.7")) != 0)
				//	|| (bundle.getSeller().getTrackingAlways() && totalDiscountedUnitPricePC.compareTo(new BigDecimal("20.00")) >= 0 && shippingCostInDollars.compareTo(BigDecimal.ZERO) != 0)) {
			if ((bundle.getSeller().getTrackingAlways() && totalUnitPricePC.compareTo
					(new BigDecimal("20.00")) < 0 && shippingCostInclVatPC.getValue().compareTo(new BigDecimal("1.7")) != 0)
				|| (bundle.getSeller().getTrackingAlways() && totalUnitPricePC.compareTo(new BigDecimal("20.00")) >= 0
						&& shippingCostInclVatPC.getValue().compareTo(BigDecimal.ZERO) != 0)) {
				throw new PlaceOrderException("Have shipping charges changed at FocalPrice? ("
						+ shippingCostInclVatPC.getValue().toPlainString() + ", discounted total unit price = "
						+ totalDiscountedUnitPricePC + ", undiscounted total unit price = "
						+ totalUnitPricePC + ", tracking = " + bundle.getSeller().getTrackingAlways() + ").");
			}
			
			/* Because of rounding to the closest cent, the total order sum isn't necessarily equal to
			 * the sum of the individual products. Correct this if necessary. */
			BigDecimal diff = totalDiscountedUnitPricePC.add(shippingCostInclVatPC.getValue()).subtract(totalSumInclVatPC.getValue());
			if (diff.compareTo(BigDecimal.ZERO) != 0) {
				/* There seems to be a bug in the logic at FocalPrice for calculating discounts that
				 * in some cases gives us the wrong discount. If diff is positive, then that means we
				 * got the product cheaper than expected (most likely because of a temporary product
				 * discount). If the diff is negative, then it means the product cost more than expected,
				 * which can happen if the product prices have increased. Make sure that the diff isn't
				 * too large. */
				if (diff.compareTo(new BigDecimal("-0.25")) < 0 || diff.compareTo(new BigDecimal("2.00")) > 0) {
					// Fishy: how can the rounding error be so large?
					throw new PlaceOrderException("Rounding error: diff = " + diff.toPlainString());
				} else if (diff.compareTo(new BigDecimal("-0.1")) < 0) {
					logger.info("Bug at FocalPrice regarding calculation of discounts: diff = "
							+ diff.toPlainString() + " for bundle " + bundle.getId() + " - accepting anyway.");
				} else if (diff.compareTo(new BigDecimal("0.1")) > 0) {
					logger.info("Bug at FocalPrice regarding calculation of discounts, or else the product"
							+ " is temporarily discounted: diff = " + diff.toPlainString() + " for bundle "
							+ bundle.getId() + " - accepting anyway.");
				}
				Order order = bundleOrders.get(0);
				VirtualCoin adjustedPriceInclVat = null;
				if (resending) {
					//adjustedPrice = currencyChecker.getEuros2Dollar(order.getResendLossInEuro()).subtract(diff);
					adjustedPriceInclVat = new VirtualCoin(Currency.EURO,order.getResendLossInEuroInclVat().subtract(diff));
				} else {
					adjustedPriceInclVat = new VirtualCoin(usedCurrency,order.getFinalProviderPriceInProviderCurrencyInclVat().subtract(diff));
				}
				logger.debug("Sum of individual orders + shipping cost: " + totalDiscountedUnitPricePC.add(shippingCostInclVatPC.getValue())
						+ ", total price = " + totalSumInclVatPC.getValue() + " - subtracting " + diff + " from the price of the "
						+ "first order (first order old price = " + order.getFinalProviderPriceInProviderCurrencyInclVat()
						+ " and new price = " + adjustedPriceInclVat.getValue());
				//BigDecimal adjustedPriceInEuro = currencyChecker.getDollar2Euro(adjustedPrice);
				BigDecimal adjustedPriceInclVatPC = currencyDAO.convertCurrency(adjustedPriceInclVat.getCurrency(), usedCurrency.getId(), adjustedPriceInclVat.getValue());
				BigDecimal adjustedPriceInclVatEuro = currencyDAO.convertCurrency(adjustedPriceInclVat.getCurrency(), Currency.EURO, adjustedPriceInclVat.getValue());
				BigDecimal adjustedPriceExclVatPC = MathUtilities.getPriceExclVat(adjustedPriceInclVatPC, bundle.getProvider().getVatPercentage());
				BigDecimal adjustedPriceExclVatEuro = MathUtilities.getPriceExclVat(adjustedPriceInclVatEuro, bundle.getProvider().getVatPercentage());
				if (resending) {
					order.setResendLossInEuroInclVat(adjustedPriceInclVatEuro);
					order.setResendLossInEuroExclVat(adjustedPriceExclVatEuro);
				} else {
					order.setFinalProviderPriceInProviderCurrencyInclVat(adjustedPriceInclVatPC);
					order.setFinalProviderPriceInProviderCurrencyExclVat(adjustedPriceExclVatPC);
					order.setFinalProviderPriceInEuroInclVat(adjustedPriceInclVatEuro);
					order.setFinalProviderPriceInEuroExclVat(adjustedPriceExclVatEuro);
				}
			}
			
			/* Finally verify that the above code properly assigned individual product prices. */
			BigDecimal totalDiscountedUnitPriceInclVat = BigDecimal.ZERO;
			if (resending) {
				for (Order order : bundleOrders) {
					logger.debug("Verification: order " + order.getId() + " has resend loss " + order.getResendLossInEuroInclVat());
					totalDiscountedUnitPriceInclVat = totalDiscountedUnitPriceInclVat.add(order.getResendLossInEuroInclVat());
				}
				//BigDecimal shippingCostInEuro = currencyDAO.convertCurrency(shippingCostPC.getCurrency(), Currency.EURO, shippingCostPC.getValue());
				logger.debug("Verification: bundle " + bundle.getId() + " has shipping cost pc " + shippingCostInclVatPC.getValue()
						+ " and shipping cost euro " + shippingCostEuroInclVat + " and total sum euro " + totalSumEuroInclVat);
				diff = totalDiscountedUnitPriceInclVat.add(shippingCostEuroInclVat).subtract(totalSumEuroInclVat);
			} else {
				for (Order order : bundleOrders) {
					totalDiscountedUnitPriceInclVat = totalDiscountedUnitPriceInclVat.add(order.getFinalProviderPriceInProviderCurrencyInclVat());
				}
				diff = totalDiscountedUnitPriceInclVat.add(shippingCostInclVatPC.getValue()).subtract(totalSumInclVatPC.getValue());
			}
			/* Allow a slight variation for resendings because for those there might be a small
			 * variation due to roundings. */
			if ((!resending && diff.compareTo(BigDecimal.ZERO) != 0)
					|| (resending && diff.abs().compareTo(new BigDecimal("0.009")) > 0)) {
				logger.debug("Rounding error in verification: diff = " + diff + " (resending = " + resending + ")");
				throw new PlaceOrderException("Error assigning individual product prices.");
			}
			
			/* Set the current provider vat information. */
			if (resending) {
				bundle.setProviderShippingResendingVatPercentage(bundle.getProvider().getVatPercentage());
				for (Order order : bundleOrders) {
					order.setProviderResendingVatPercentage(bundle.getProvider().getVatPercentage());
				}
			} else {
				bundle.setProviderShippingVatPercentage(bundle.getProvider().getVatPercentage());
				for (Order order : bundleOrders) {
					order.setProviderVatPercentage(bundle.getProvider().getVatPercentage());
				}
			}
			
			logger.info("First part of ordering bundle " + bundle.getId() + " went through successfully.");
		} catch (WebDriverException e) {
			handlePlaceOrderError(bundle, null, pressedFirstSubmit,
					chosenShippingType,possiblySystematicError,e);
			//logout();
			throw new ProviderTemporarilyDownException("FocalPrice temporarily down.");
		}

		/* If we get this far, then we need to take some special action if the
		 * ordering bails out. */
		try {
			logger.info("Logging on to PayPal.");
			loginToPayPal(driver, user, password);

			/* Set payPalId to null in case we cannot fetch it below due to for example
			 * selenium errors. This is of significance only for resendings (since for
			 * first orderings the payPalId is always null). Otherwise it could happen
			 * that the old payPalId is still set and then we bail out _without_ rolling
			 * back the transaction (going to the error state) and the new providerId
			 * is set. */
			logger.debug("Old payPayId for bundle " + bundle.getId() + ": " + bundle.getPayPalId());
			bundle.setPayPalId(null);
			logger.debug("Old providerOrderId for bundle " + bundle.getId() + ": " + bundle.getProviderOrderId());
			bundle.setProviderOrderId(null);
			driver.switchTo().defaultContent(); //this is a workaround due to a firefox bug (http://code.google.com/p/selenium/issues/detail?id=1438)
			driver.waitForElement(By.id("continue")); //wait for page to load
		} catch (Exception e) {
			logger.debug("Ordering problem: ",e);
			//logout();
			/* If we get here, then an order has been created at FocalPrice but it's put in the
			 * state Unpaid. Usually it works if we try again, so here let's inform the operator
			 * about a likely Unpaid order that should be canceled, and then throw a
			 * ProviderTemporarilyDownException. */
			EmailSender.sendOperationSummaryReport("Possibly Unpaid order at FocalPrice",
					"Bundle " + bundle.getId() + " couldn't be ordered from FocalPrice, and this might"
					+ " have caused an order at FocalPrice having state Unpaid.",bundle.getProvider().getSystemEmail());
			throw new ProviderTemporarilyDownException(e.getMessage());
		}
		
		
		/* If the ordering bails out after this, then it's bad because then the order will be in a "hanging" state. */
		int newState = Order.STATE_ORDERED_FROM_PROVIDER;
		try {
			/* Do the final submit. */
			WebElement continueElement = driver.findElement(By.id("continue"));
			section.setInCriticalSection(true);
			section.setTransactionMustGoThrough(true);
			continueElement.click();
			
			/* Get the provider order id. For the new pages FocalPrice gives a PayPal id that's the same as the provider
			 * order id. The PayPal id is what's used when PayPal informs about the payment. */
			driver.waitForElement(By.id("OrderID")); //wait for page to load
			WebElement providerOrderIdElement = driver.findElement(By.id("OrderID"));
			String providerOrderId = providerOrderIdElement.getText();
			logger.debug("Provider order id = " + providerOrderId);
			bundle.setPayPalId(providerOrderId);
			bundle.setProviderOrderId(providerOrderId);
			
			/* Safety check in case the web pages have changed. */
			if (providerOrderId.indexOf(" ") >= 0) {
				logger.debug("Have the web pages changed? Informing the operator.");
				String pageSource = driver.getPageSource();
				logger.debug("pageSource = \"" + pageSource + "\"");
				//throw new PlaceOrderException("Weird paypalId (" + paypalId + "). Has the web page changed?");
				String email = "Problem placing order at provider FocalPrice for bundle " + bundle.getId() + "."
					+ " Weird providerOrderId (" + providerOrderId + "). Has the web page changed?"
					+ " Still letting the order go through.";
				EmailSender.sendOperationProblemReport("Ordering problem - check manually",email,bundle.getProvider().getSystemEmail());
			}
			logger.debug("Bundle " + bundle.getId() + " ordered.");
			section.setInCriticalSection(false);
		} catch (Exception e) {
			logger.error("Ordering error: ",e);
			String pageSource = driver.getPageSource();
			logger.debug("pageSource = \"" + pageSource + "\"");
			if (e instanceof TimeoutException || e instanceof NoSuchElementException
					|| (e instanceof WebDriverException && e.getMessage().indexOf("this.getWindow() is null") >= 0)) {
				/* Set the order to state otherError and inform the operator
				 * (but don't roll back the transaction). */
				logger.debug("Setting state to \"other error\" for bundle " + bundle.getId());
				newState = Order.STATE_OTHER_ERROR;
				String email = "Problem placing order(s) at provider FocalPrice for bundle " + bundle.getId() + ". PayPalId"
					+ " couldn't be fetched. Setting the order(s) to state otherError.\n\nError message: "+ e.getMessage()
					+ "\n";
				EmailSender.sendOperationProblemReport("Ordering problem - check manually",email,bundle.getProvider().getSystemEmail());
				section.setInCriticalSection(false);
			} else {
				logger.debug("Rolling back transaction.");
				throw new PlaceOrderException("Problem finishing ordering at provider.");
			}
		/*} finally {
			logout();*/
		}

		for (Order order : bundleOrders) {
			order.setStateWithHistory(newState);
		}
	}
	
	private void loginToPayPal(CustomWebDriver driver, String user, String password) throws /*PayPalCaptchaException, */ProviderTemporarilyDownException {
		try {
			WebElement payPalOrderButton = driver.findElement(By.id("CreateOrderAndPay"));
			payPalOrderButton.click();
			
			/*if (selenium.getBodyText().indexOf("Type the characters you see in the image for security purposes.") >= 0) {
				throw new PayPalCaptchaException("Cannot solve PayPal captcha.");
			}*/

			/* Wait as short time as possible for page to load. */
			try { Thread.sleep(5*1000); } catch (Exception e) { }
			WebElement loginEmailElement = driver.findElementOrNull(By.id("login_email"));
			if (loginEmailElement == null) {
				try { Thread.sleep(20*1000); } catch (Exception e) { }
				loginEmailElement = driver.findElementOrNull(By.id("login_email"));
				if (loginEmailElement == null) {
					try { Thread.sleep(20*1000); } catch (Exception e) { }
				}
			}

			/* Sometimes the PayPal login login is hidden, so then we need to open it first. */
			loginEmailElement = driver.findElementOrNull(By.id("login_email"));
			WebElement loadLoginElement = driver.findElementOrNull(By.id("loadLogin"));
			if (loginEmailElement == null && loadLoginElement != null) {
				logger.debug("PayPal login was hidden, opening.");
				loadLoginElement.click();
				driver.waitForElement(By.id("login_email")); //wait for page to load
				loginEmailElement = driver.findElement(By.id("login_email")); //load this one again, because the old instance is stale
			} else {
				logger.debug("PayPal login was already open.");
			}

			loginEmailElement.clear();
			loginEmailElement.sendKeys(user);
			WebElement passwordElement = driver.findElement(By.id("login_password"));
			passwordElement.clear();
			passwordElement.sendKeys(Encryption.decrypt(password));
			WebElement submitButton = driver.findElement(By.id("submitLogin"));
			submitButton.click();
		} catch (Exception e) {
			//Now and then the login_email field at PayPal is not found.
			//In that case throw a ProviderTemporaryDownException which won't set SellStar
			//into the error state, but rather continue with the next order.
			logger.debug("Couldn't logon to PayPal: ", e);
			throw new ProviderTemporarilyDownException("Temporary error: couldn't logon to PayPal.");
		}
	}
	
	@Override
	public void login(Provider provider, int tryCount) throws ProviderTemporarilyDownException {
		Date startDate = new Date();
		for (int i = 1; i < (tryCount + 1); i++) {
			try {
				loginInternal(provider);
				if (i > 1) {
					logger.debug("Logon succeeded at attempt number " + i + ".");
				}
				/* In some cases we can get a problem if FocalPrice is very slow. For example if reloading
				 * of the logon page succeeds only at the fifth attempt, then we might get a transaction
				 * timeout for example at placeOrders. Therefore we abort if it took too long to log on. */
				if (startDate.getTime() + 1000*60*5 < System.currentTimeMillis()) {
					logger.info("Logon to FocalPrice took too long, aborting.");
					logout(provider,false);
					throw new ProviderTemporarilyDownException("Couldn't log on to provider");
				}
				return;
			} catch (ProviderTemporarilyDownException e) {
				logger.debug("Logon failed at attempt number " + i + ".");
				if (i >= tryCount) {
					throw e;
				}
			}
		}
	}
	
	private void loginInternal(Provider provider) throws ProviderTemporarilyDownException {
		CustomWebDriver driver = seleniumRobot.getWebDriver();
		/* Sometimes the previous logout failed and in that case there won't be any
		 * login screen (since we are already logged on). */
		boolean alreadyLoggedOn = false;
		logger.debug("Opening signin page");
		WebElement loginForm = null;
		try {
			driver.get("http://www.focalprice.com/signin",By.id("loginForm"),5);
			loginForm = driver.findElementOrNull(By.id("loginForm"));
		} catch (TimeoutException e) {
			throw new ProviderTemporarilyDownException("Couldn't log on to provider");
		}
		if (loginForm == null) {
			WebElement welcomeElement = driver.findElementOrNull(By.xpath("//*[@id='after_sign']/em"));
			WebElement loggedOnAs = driver.findElementOrNull(By.id("nickName"));
			if (welcomeElement != null && welcomeElement.getText().indexOf("Welcome") >= 0
					&& loggedOnAs != null && loggedOnAs.getText().indexOf("Welcome, " + provider.getUserName().substring(0,4)) >= 0) {
				logger.debug("Already logged on");
				alreadyLoggedOn = true;
			} else {
				throw new ProviderTemporarilyDownException("Couldn't log on to provider");
			}
		}
		if (!alreadyLoggedOn) {
			logger.debug("Logging on");
			WebElement emailElement = loginForm.findElement(By.id("emailAddress"));
			emailElement.clear();
			emailElement.sendKeys(provider.getUserName());
			logger.debug("Typing in password");
			WebElement passwordElement = loginForm.findElement(By.id("password"));
			passwordElement.clear();
			passwordElement.sendKeys(Encryption.decrypt(provider.getPassword()));
			logger.debug("Pressing \"Sign in\"");
			loginForm.submit();
		}
		
		/* Safety check: make sure that the correct currency is used. */
		checkCurrency(null);
	}
	
	@Override
	public void logout(Provider provider, boolean removeDanglingOrders) {
		if (removeDanglingOrders) {
			logger.debug("Removing dangling orders at logout.");
			try {
				openDropShippingPageForPlaceOrders(null);
				removeDanglingOrdersForPlaceOrders();
				enterShippingDetailsForPlaceOrders("test", "test", "test 1", "test", "12345");
				logger.debug("Removed dangling orders at logout.");
			} catch (Exception e) {
				// This is not such a big deal, so just print an error message here
				logger.error("Removal of dangling orders failed at logout.", e);
			}
		}
		CustomWebDriver driver = seleniumRobot.getWebDriver();
		try {
			driver.get("http://www.focalprice.com/signout");
		} catch (Exception e) {
			logger.info("Not possible to log out from FocalPrice.");
		}
	}
	
	@Override
	public ProviderQuestion updateConversationAtProvider(OrderBundle bundle, ProviderQuestion providerQuestion) throws Exception {
		logger.debug("Updating conversation at provider for bundle " + bundle.getId() + " with providerOrderId " + bundle.getProviderOrderId());
		CustomWebDriver driver = seleniumRobot.getWebDriver();
		try {
			login(bundle.getProvider(),1);
			/* If the provider conversation ticket id is known, then it's easier to find the conversation. */
			if (providerQuestion != null) {
				driver.get("http://www.focalprice.com/membermessagedetails/" + providerQuestion.getProviderTicketId(),3);
			} else {
				openExistingConversation(bundle);
			}
			providerQuestion = updateConversationAtProviderInternal(bundle, providerQuestion, null, null);
		} catch (WebDriverException e) {
			logger.debug("Update conversation at provider problem: ",e);
			throw new ProviderTemporarilyDownException("FocalPrice temporarily down.");
		} finally {
			logout(bundle.getProvider(),false);
		}
		return providerQuestion;
	}
	
	private void openExistingConversation(OrderBundle bundle) throws Exception {
		CustomWebDriver driver = seleniumRobot.getWebDriver();
		driver.get("http://www.focalprice.com/membermessage", 3);
		String orderId = null;
		int i = 0;
		int pagesGoneThrough = 0;
		while (true) {
			for (i = 1;; i++) {
				WebElement messageRow = driver.findElementOrNull(By.xpath("//*[@id='order_table']/tbody/tr[" + i + "]/td[2]"));
				if (messageRow != null) {
					orderId = messageRow.getText();
					if (orderId.equals(bundle.getProviderOrderId())) {
						break;
					}
				} else {
					break;
				}
			}
			/*List<WebElement> messageRows = driver.findElements(By.xpath("//*[@id='order_table']//*[@class='ticket_orderid']"));
			if (messageRows.size() > 1) {
				logger.debug("Found " + messageRows.size() + " messages");
				for (i = 0; i < messageRows.size(); i++) {
					WebElement messageRow = messageRows.get(i);
					orderId = messageRow.getText();
					if (orderId.equals(bundle.getProviderOrderId())) {
						break;
					}
				}
			}*/
			if (orderId != null && orderId.equals(bundle.getProviderOrderId())) {
				break;
			}
			WebElement nbrPagesElement = driver.findElement(By.xpath("//html/body/div[5]/div[2]/div/div[4]/span[1]"));
			String pageString = nbrPagesElement.getText().replace("Pages: ","");
			String currPage = pageString.substring(0, pageString.indexOf("/")).trim();
			String lastPage = pageString.substring(pageString.indexOf("/")+1).trim();
			//logger.debug("currPage = \"" + currPage + "\", lastPage = \"" + lastPage + "\"");
			if (currPage.trim().equals(lastPage.trim())) {
				break;
			} else {
				logger.debug("Conversation not found on page " + currPage + ". Going to next page.");
				WebElement nextPageElement = driver.findElement(By.xpath("//html/body/div[5]/div[2]/div/div[4]/a[4]"));
				nextPageElement.click();
				pagesGoneThrough++;
			}
			if (pagesGoneThrough > 20) {
				break;
			}
		}
		if (orderId == null) {
			throw new Exception("Conversation not found for bundle " + bundle.getId()
					+ " (providerOrderId " + bundle.getProviderOrderId() + ").");
		} else {
			logger.debug("Opening existing conversation for bundle " + bundle.getId());
			//List<WebElement> messageRows = driver.findElements(By.xpath("//*[@id='order_table']//*[@class='ticket_orderid']"));
			//WebElement messageRow = messageRows.get(i);
			WebElement messageRow = driver.findElement(By.xpath("//*[@id='order_table']/tbody/tr[" + i + "]/td[1]/a"));
			messageRow.click();
			//Don't use selenium.waitForPageToLoad here, because often message the page keeps "loading"
			//forever and then we don't get done until we get a selenium timeout. Rather wait for
			//ten seconds.
			//selenium.waitForPageToLoad();
			Thread.sleep(1000*10);
		}
	}
	
	/* This method assumes that the conversation page is already open. */
	private ProviderQuestion updateConversationAtProviderInternal(OrderBundle bundle, ProviderQuestion providerQuestion,
			String subject, String firstQuestion) throws Exception {
		/* Update (or create) the conversation in the database. */
		CustomWebDriver driver = seleniumRobot.getWebDriver();
		StringBuffer conversation = new StringBuffer();
		WebElement subjectElement = driver.findElement(By.id("subject_text"));
		String convSubject = subjectElement.getText();
		WebElement ticketIdElement = driver.findElement(By.id("ticket_id"));
		String ticketId = ticketIdElement.getText();
		WebElement dateElement = driver.findElement(By.id("create_time"));
		String dateString = dateElement.getText();
		logger.debug("dateString = \"" + dateString + "\"");
		//DateFormat provDateFormat = new SimpleDateFormat("dd/MM/yyyy hh:mm:ss");
		//DateFormat provDateFormat = new SimpleDateFormat("MMM dd,yyyy HH:mm:ss z");
		DateFormat provDateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
		DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		Date date = provDateFormat.parse(dateString);
		WebElement authorElement = driver.findElement(By.id("message_author"));
		String addedBy = authorElement.getText();
		WebElement firstMessageElement = driver.findElement(By.id("message_content"));
		String firstMessage = firstMessageElement.getText();
		if (subject != null && !StringUtilities.sloppyMatch(convSubject,subject)) {
			throw new Exception("Subject in argument different from subject at provider (\""
					+ subject + "\" vs. \"" + convSubject + "\").");
		}
		conversation.append("Added by " + addedBy + " on " + dateFormat.format(date) + ":\n\n"
				+ firstMessage + "\n\n----------\n\n");
		List<ProvMsg> otherMessages = getSubsequentMessages();
		boolean addNewToDatabase = false;
		if (providerQuestion == null) {
			if (firstQuestion != null && !StringUtilities.sloppyMatch(firstQuestion,firstMessage)) {
				throw new Exception("Question in argument different from question at provider (\""
						+ firstQuestion + "\" vs. \"" + firstMessage + "\").");
			}
			providerQuestion = new ProviderQuestion();
			providerQuestion.setOrderBundle(bundle);
			providerQuestion.setLastOwnComment(date);
			providerQuestion.setProviderTicketId(ticketId);
			providerQuestion.setSubject(convSubject);
			providerQuestion.setOpen(true);
			addNewToDatabase = true;
		}
		
		if (!providerQuestion.getProviderTicketId().equals(ticketId)) {
			throw new Exception("Ticket id in database is different from ticket id at provider (\""
					+ providerQuestion.getProviderTicketId() + "\" vs. \"" + ticketId + "\").");
		}
		if (!providerQuestion.getSubject().equals(convSubject)) {
			throw new Exception("Subject in database different from subject at provider (\""
					+ providerQuestion.getSubject() + "\" vs. \"" + convSubject + "\").");
		}
		if (providerQuestion.getNbrMessages() != null && (otherMessages.size() + 1) < providerQuestion.getNbrMessages()) {
			throw new Exception("Too few messages in the conversation (expecting at least "
					+ providerQuestion.getNbrMessages() + " but only found "
					+ (otherMessages.size() + 1) + ". Has the conversation shrunk since last time we checked?");
		}
		providerQuestion.setNbrMessages(otherMessages.size() + 1);
		if (otherMessages.size() > 0) {
			Date lastOwnComment = providerQuestion.getLastOwnComment();
			Date lastProviderComment = providerQuestion.getLastProviderComment();
			for (ProvMsg provMsg : otherMessages) {
				if (provMsg.ownComment) {
					lastOwnComment = provMsg.date;
				} else {
					lastProviderComment = provMsg.date;
				}
				conversation.append("Added by " + provMsg.addedBy + " on " + dateFormat.format(provMsg.date) + ":\n\n"
						+ provMsg.contents + "\n\n----------\n\n");
			}
			if (lastProviderComment != null && providerQuestion.getLastProviderComment() != null
					&& lastProviderComment.getTime() < providerQuestion.getLastProviderComment().getTime()) {
				throw new Exception("Last provider comment timestamp in database later than last provider comment"
						+ " timestamp at provider (\""
						+ lastProviderComment + "\" vs. \"" + providerQuestion.getLastProviderComment() + "\").");
			}
			if (lastProviderComment == null && providerQuestion.getLastProviderComment() != null) {
				throw new Exception("Has previous provider comment disappeared? (lastProviderComment = "
						+ lastProviderComment + " and providerQuestion.getLastProviderComment = "
						+ providerQuestion.getLastProviderComment() + ").");
			}
			if (lastOwnComment.getTime() < providerQuestion.getLastOwnComment().getTime()) {
				throw new Exception("Last own comment timestamp in database later than last own comment"
						+ " timestamp at provider (\""
						+ lastOwnComment + "\" vs. \"" + providerQuestion.getLastOwnComment() + "\").");
			}
			providerQuestion.setLastOwnComment(lastOwnComment);
			providerQuestion.setLastProviderComment(lastProviderComment);
		}
		providerQuestion.setContents(conversation.toString());

		if (addNewToDatabase) {
			providerQuestionDAO.save(providerQuestion);
		} else {
			//do nothing, the updated providerQuestion will be automatically stored when the transaction ends
		}
		return providerQuestion;
	}
	
	@Override
	public ProviderQuestion addQuestionToProvider(ProviderQuestion providerQuestion, OrderBundle bundle,
			String subject, String question, String conversationType) throws Exception {
		logger.debug("Adding question to provider for bundle " + bundle.getId() + " with providerOrderId " + bundle.getProviderOrderId());
		CustomWebDriver driver = seleniumRobot.getWebDriver();
		try {
			login(bundle.getProvider(),1);
			/* First check if we need to create a new conversation or if we can add a reply to an
			 * existing conversation. A new creation needs to be created if no conversation exists,
			 * or if the existing conversation has been closed at the provider (because then no
			 * more replies are accepted). */
			boolean createNew = true;
			if (providerQuestion != null) {
				driver.get("http://www.focalprice.com/membermessagedetails/" + providerQuestion.getProviderTicketId(),
						/*By.xpath("//*[@id='reply_rate_content']/a[1]"),*/3);
				WebElement replyButton = driver.findElementOrNull(By.xpath("//*[@id='reply_rate_content']/a[1]"));
				if (replyButton != null) {
					createNew = false;
				} else {
					logger.debug("Provider conversation " + providerQuestion.getProviderTicketId()
							+ " for bundle " + bundle.getId() + " was closed by provider, create a new conversation.");
					logger.debug("Closing old conversation");
					providerQuestion.setOpen(false);
				}
			}
			
			if (createNew) {
				/* Create a new conversation. */
				driver.get("http://www.focalprice.com/membermessage",By.id("remark"),3);
				List<WebElement> categorySelectionList = driver.findElements(By.id("categoryid"));
				if (categorySelectionList.size() != 2) {
					throw new RuntimeException("Weird, categorySelectionList.size = " + categorySelectionList.size());
				}
				WebElement categorySelection = categorySelectionList.get(1);
				Select select = new Select(categorySelection);
				select.selectByVisibleText(conversationType);
				WebElement subjectElement = driver.findElement(By.id("subjecttext"));
				subjectElement.clear();
				subjectElement.sendKeys(StringUtilities.toUSASCIICharacters(subject));
				WebElement orderElement = driver.findElement(By.id("orderid"));
				orderElement.clear();
				orderElement.sendKeys(bundle.getProviderOrderId());
				WebElement remark = driver.findElement(By.id("remark"));
				remark.clear();
				remark.sendKeys(StringUtilities.toUSASCIICharacters(question));
				remark.submit();
				providerQuestion = null;
				Thread.sleep(3000);
				//We then need to open this page again because here we get back to the main view
				openExistingConversation(bundle);
			} else {
				/* Add the question to an existing conversation. */
				WebElement replyButton = driver.findElement(By.xpath("//*[@id='reply_rate_content']/a[1]"));
				replyButton.click();
				try { Thread.sleep(1*1000); } catch (Exception e) { } //pause to let the javascript do its things
				WebElement remark = driver.findElement(By.id("remark"));
				remark.clear();
				remark.sendKeys(StringUtilities.toUSASCIICharacters(question));
				remark.submit();
				Thread.sleep(3000);
			}
			
			try {
				providerQuestion = updateConversationAtProviderInternal(bundle, providerQuestion, subject,
						(providerQuestion == null ? question : null));
			} catch (Exception e) {
				logger.error("Error checking just added question: ", e);
				if (providerQuestion == null) {
					throw new Exception("A new question was added to provider for bundle " + bundle.getId() + " but the database wasn't updated since updateConversationAtProviderInternal failed.");
				} else {
					//Not such a big deal, because this one will be updated later since it already exists in the database.
					logger.info("New conversation was added but we failed to check the added question."
							+ " Not a big deal however since it already exists in the database (so it will be done later).");
				}
			}
		} catch (WebDriverException e) {
			logger.debug("Add question to provider problem: ",e);
			throw new ProviderTemporarilyDownException("FocalPrice temporarily down (" + e.getMessage() + ").");
		} finally {
			logout(bundle.getProvider(),false);
		}
		return providerQuestion;
	}
	
	public class ProvMsg {
		public Date date;
		public String addedBy;
		public String contents;
		public boolean ownComment;
	}
	
	public class ProvMsgComparator implements Comparator<ProvMsg> {
		public int compare(ProvMsg obj1, ProvMsg obj2) {
			if (obj1 != null && obj2 != null) {
				return obj1.date.compareTo(obj2.date);
			} else if (obj1 == null) {
				return -1;
			} else {
				return 1;
			}
		}
	}
	
	private List<ProvMsg> getSubsequentMessages() throws Exception {
		CustomWebDriver driver = seleniumRobot.getWebDriver();
		//DateFormat provDateFormat = new SimpleDateFormat("dd/MM/yyyy hh:mm:ss");
		//DateFormat provDateFormat = new SimpleDateFormat("MMM dd,yyyy HH:mm:ss z");
		DateFormat provDateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
		List<ProvMsg> subsequentMessages = new ArrayList<ProvMsg>();
		for (int i = 2;; i++) {
			//if (selenium.isElementPresent("//div[@id='breadcrumb']/../div[contains(@class, 'message')][" + i + "]")) {
			if (driver.isElementPresent(By.xpath("//*[@id='content']/div/div[" + i + "]/div[1]/span"))) {
				ProvMsg provMsg = new ProvMsg();
				WebElement dateElement = driver.findElement(By.xpath("//*[@id='content']/div/div[" + i + "]/div[1]/span"));
				String dateString = dateElement.getText();
				provMsg.date = provDateFormat.parse(dateString);
				WebElement addedByElement = driver.findElement(By.xpath("//*[@id='content']/div/div[" + i + "]/div[1]/b"));
				provMsg.addedBy = addedByElement.getText();
				WebElement contentsElement = driver.findElement(By.xpath("//*[@id='content']/div/div[" + i + "]/div[2]"));
				provMsg.contents = contentsElement.getText();
				provMsg.ownComment = (provMsg.addedBy.equals("You"));
				subsequentMessages.add(provMsg);
			} else {
				break;
			}
		}
		Collections.sort(subsequentMessages, new ProvMsgComparator());
		return subsequentMessages;
	}
	
	@Override
	public void updateOrderStatus(OrderBundle bundle, List<Order> bundleOrders, CriticalSectionInfo section) throws Exception {
		if (!bundle.getProvider().getName().equals(Provider.FOCAL_PRICE_PROVIDER_NAME)) {
			throw new RuntimeException("Can only handle FocalPrice.");
		}
		
		logger.debug("Updating status of bundle " + bundle.getId() + " with providerOrderId " + bundle.getProviderOrderId());
		CustomWebDriver driver = seleniumRobot.getWebDriver();
		Integer newState = null;
		List<Order> newShippedOrders = null;
		try {
			//login(bundle.getProvider(),3);
			driver.get("http://www.focalprice.com/My/Order/Detail/" + bundle.getProviderOrderId(),By.id("order_table"),5);
			
			/* FocalPrice is sometimes buggy and screws up the order, so here let's make
			 * sure that the address information and product information is correct.
			 * TODO: check also that the country is correct. */
			boolean faultyAddress = false;
			WebElement firstName = driver.findElement(By.name("FirstName"));
			WebElement lastName = driver.findElement(By.name("LastName"));
			WebElement address = driver.findElement(By.name("Address1"));
			WebElement city = driver.findElement(By.name("City"));
			WebElement postCode = driver.findElement(By.name("PostCode"));
			if (!firstName.getAttribute("value").equals(StringUtilities.toUSASCIICharacters(bundle.getFirstName()))
					|| !lastName.getAttribute("value").equals(StringUtilities.toUSASCIICharacters(bundle.getLastName()))
					|| !address.getAttribute("value").equals(StringUtilities.toUSASCIICharacters(bundle.getAddress()))
					|| !city.getAttribute("value").equals(StringUtilities.toUSASCIICharacters(bundle.getCity()))
					|| !postCode.getAttribute("value").equals(bundle.getPostcode())) {
				faultyAddress = true;
			}
			boolean faultyProduct = false;
			for (int i = 0; i < 10; i++) {
				WebElement productElement = driver.findElementOrNull(By.xpath("//*[@id='order_table']/tbody/tr[" + (i+1) + "]/td[1]"));
				if (productElement != null) {
					String productId = productElement.getText();
					/* Only check non-shipped orders, because shipped products won't even be in bundleOrders
					 * since ProviderHandler.updateOrderStatus only includes unshipped orders. */
					WebElement shippingDateElement = driver.findElement(By.xpath("//*[@id='order_table']/tbody/tr[" + (i+1) + "]/td[7]"));
					String shippingDateStr = shippingDateElement.getText();
					if (shippingDateStr.length() <= 5) { //shippingDateStr.equals("--")) {
						logger.debug("Looking for product \"" + productId + "\"");
						boolean found = false;
						for (Order o : bundleOrders) {
							if (o.getProduct().getProviderProdId().equals(productId)) {
								found = true;
							}
						}
						if (!found) {
							faultyProduct = true;
						}
					} else {
						logger.debug("Not looking for product \"" + productId + "\" because it's already shipped.");
					}
				} else {
					break;
				}
			}
			if (faultyAddress || faultyProduct) {
				logger.debug("Faulty address and/or product information for bundle " + bundle.getId());
				String expectedAddress = StringUtilities.toUSASCIICharacters(bundle.getFirstName())
					+ " " + StringUtilities.toUSASCIICharacters(bundle.getLastName()) + "\n"
					+ StringUtilities.toUSASCIICharacters(bundle.getAddress()) + "\n"
					+ bundle.getPostcode() + " " + StringUtilities.toUSASCIICharacters(bundle.getCity());
				String actualAddress = firstName.getAttribute("value") + " " + lastName.getAttribute("value") + "\n"
					+ address.getAttribute("value") + "\n" + postCode.getAttribute("value") + " " + city.getAttribute("value");
				String addressProblem = (faultyAddress ? "Expected address:\n" + expectedAddress + "\n\n"
					+ "Actual address:\n" + actualAddress : "");
				EmailSender.sendOperationProblemReport("updateStatusOfOrder",
						"Bundle " + bundle.getId() + " (" + bundle.getProviderOrderId() + ") has faultyAddress = "
						+ faultyAddress + ", faultyProduct = " + faultyProduct + " when checking the order"
						+ " at provider. Please check this one by hand.\n\n"
						+ addressProblem + "\n");
			} else {
				logger.debug("Correct information for bundle " + bundle.getId());
			}
			
			/* It seems like the new FocalPrice system only uses the following statuses:
			 * Paid&Unpacked, All Shipped, Partially Shipped, Cancelled, Invalid. I don't know what's the difference
			 * is between canceled and invalid. Possibly the order goes into into state invalid if the _customer_
			 * cancels the order (or cancels one product in a multi-order bundle, at least that's the case for
			 * order D9110005) and goes into state canceled if FocalPrice cancels the order because it's no longer
			 * for sale.
			 * =>UPDATE: it seems like FocalPrice has made an update so that "Partially Shipped" is no longer
			 * in use, or at least for some orders it can be so that the status is "Paid&Unpacked" although
			 * some order in the bundle is shipped. Therefore we make an extra check in order to see if some
			 * order has been shipped. */
			//WebElement bundleStatus = driver.findElement(By.xpath("//body/div[5]/div[2]/div[1]/span[4]/em"));
			WebElement bundleStatus = driver.findElement(By.xpath("//div[@field='OrderInfo']//em[@field='Status']"));
			List<WebElement> shippingColumns = driver.findElements(By.xpath("//*[@id='order_table']/tbody/tr/td[7]"));
			boolean someShipped = false;
			for (WebElement shippingColumn : shippingColumns) {
				if (shippingColumn.getText().indexOf("--") < 0) {
					someShipped = true;
					if (bundleStatus.getText().equals("Paid&Unpacked")) {
						logger.debug("Some order is shipped for bundle " + bundle.getId() + " although"
								+ " the bundle status is \"Paid&Unpacked\".");
					}
					break;
				}
			}
			if (bundleStatus.getText().equals("All Shipped") || bundleStatus.getText().equals("Partially Shipped") || someShipped) {
				newState = Order.STATE_SHIPPED_BY_PROVIDER;
				newShippedOrders = handleShipped(bundle,bundleOrders);
				if (newShippedOrders.size() == 0) {
					//No new order was shipped, so let's just return. This can happen if some orders are
					//partially shipped and no new orders have been shipped since last time we checked.
					return;
				}
			} else if (bundleStatus.getText().equals("Paid&Unpacked")) {
				newState = Order.STATE_ORDER_CONFIRMED_BY_PROVIDER;
			/*} else if (bundleStatus.getText().equals("Invaild")) {
				newState = Order.STATE_PROVIDER_CANCEL_CONFIRMED;*/
			} else if (bundleStatus.getText().equals("Invalid") || bundleStatus.getText().equals("Cancelled")) {
				/* This is alright if all orders in bundleOrders are in state STATE_PROVIDER_CANCEL_PENDING. */
				boolean allCanceled = true;
				for (Order order : bundleOrders) {
					if (order.getState().intValue() != Order.STATE_PROVIDER_CANCEL_PENDING) {
						allCanceled = false;
					}
				}
				if (allCanceled) {
					//don't change the state in this case
					newState = Order.STATE_PROVIDER_CANCEL_PENDING;
				} else {
					EmailSender.sendOperationProblemReport("updateStatusOfOrder",
							"Bundle " + bundle.getId() + " unexpectedly has status " + bundleStatus.getText() + " at provider.\n"
							+ "Please check what's the reason for this. Provider order id = " + bundle.getProviderOrderId());
					return;
				}
			} /*else if (bundleStatus.getText().equals("Unpaid")) {
				EmailSender.sendOperationProblemReport("Bundle " + bundle.getId() + " isn't paid.",
						"Bundle " + bundle.getId() + " isn't paid.");
				return;
			}*/ else {
				throw new Exception("Unknown FocalPrice state for bundle " + bundle.getId() + ": \"" + bundleStatus.getText() + "\"");
			}
		} catch (WebDriverException e) {
			logger.debug("Update order status problem: ",e);
			throw new ProviderTemporarilyDownException("FocalPrice temporarily down.");
		/*} finally {
			logout(bundle.getProvider(),false);*/
		}

		int state = bundleOrders.get(0).getState();
		if (state != newState.intValue()) {
			logger.info("Updating bundle " + bundle.getId() + " (" + bundle.getProviderOrderId() + ") from state "
					+ state + " to state " + newState);
			if (newState.equals(Order.STATE_SHIPPED_BY_PROVIDER)) {
				OrderShippedHandler.handleOrderBundleShippedByProvider(newShippedOrders.get(0).getTracking(),
						newShippedOrders, bundleOrders, section);
			} else if (newState.equals(Order.STATE_ORDER_CONFIRMED_BY_PROVIDER)) {
				orderBundleConfirmedByProvider(bundle, bundleOrders);
			} else {
				setStateOfBundle(newState, bundle, bundleOrders);
			}
		}
	}
	
	/**
	 * Returns a list of (new) orders that are shipped. Normally this method returns
	 * all orders in the bundle, but in case a multi-order bundle is sent using several
	 * separate packages, then this method returns only those orders that have been
	 * shipped after this method was called last time.
	 */
	private List<Order> handleShipped(OrderBundle bundle, List<Order> bundleOrders) throws Exception {
		CustomWebDriver driver = seleniumRobot.getWebDriver();
		
		/* We don't totally trust the web page of FocalPrice since sometimes it gives the wrong information,
		 * so here make sure that we are actually viewing the correct page. */
		/*String providerOrderId = selenium.getText("//span[@id='txtOrderNum']");
		logger.debug("handleShipped: providerOrderId = " + providerOrderId);
		if (providerOrderId == null || !providerOrderId.equals(bundle.getProviderOrderId())) {
			logger.debug("Temporary error: got wrong page for handleShipped.");
			throw new ProviderTemporarilyDownException("Temporary error: got wrong page for handleShipped.");
		}*/
		
		/* Check if any new orders are shipped. */
		List<Order> newShipped = new ArrayList<Order>();
		boolean someOrderIsShipped = false;
		int totalNumberOfChecked = 0;
		//DateFormat provDateFormat = new SimpleDateFormat("MM/dd/yyyy hh:mm a");
		//DateFormat provDateFormat = new SimpleDateFormat("MMM dd,yyyy HH:mm:ss z");
		DateFormat provDateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
		//the total number of orders in the bundle might be greater that bundleOrders.size
		int totalNbrOrdersInBundle = orderDAO.getNbrOrdersInBundle(bundle);
		Date shippingDate = null;
		for (int i = 0; i < totalNbrOrdersInBundle; i++) {
			try {
				WebElement productElement = driver.findElement(By.xpath("//*[@id='order_table']/tbody/tr[" + (i+1) + "]/td[1]"));
				String productId = productElement.getText();
				logger.debug("productId = " + productId);
				WebElement quantityStrElement = driver.findElement(By.xpath("//*[@id='order_table']/tbody/tr[" + (i+1) + "]/td[5]"));
				String quantityStr = quantityStrElement.getText();
				logger.debug("quantityStr = " + quantityStr);
				WebElement shippingDateElement = driver.findElement(By.xpath("//*[@id='order_table']/tbody/tr[" + (i+1) + "]/td[7]"));
				String shippingDateStr = shippingDateElement.getText();
				logger.debug("shippingDateStr = " + shippingDateStr);
				if (!shippingDateStr.equals("--")) {
					someOrderIsShipped = true;
					for (Order order : bundleOrders) {
						logger.debug("checking order " + order.getId());
						if (order.getProduct().getProviderProdId().equals(productId)) {
							totalNumberOfChecked++;
							if (isInUpdatableState(order)) {
								/* In some cases two products in the same bundle can be sent so close in time
								 * that updateStatusOfOrders hasn't ran in between. To account for that we only
								 * set the oldest new shipping during this run (the second oldest will then be
								 * set on the next run).
								 * Note: in some cases the shipping time of the products doesn't have the exact
								 * same timestamp even though the products are sent in the same packet (for example
								 * for bundle 5281 (FPDDD76876KX) and 5421 (FPDE191390YC)), so we determine that
								 * if the shipment timestamp is less than five minutes apart, then the products
								 * are shipped together. */
								Date tempShippingDate = provDateFormat.parse(shippingDateStr);
								if (shippingDate == null || tempShippingDate.getTime() < (shippingDate.getTime() - 1000*60*5)) {
									/* This one is at least five minutes earlier than the earliest one found so far, so
									 * then skip the ones found so far by zeroing the found ones so far. */
									if (newShipped.size() > 0) {
										logger.debug("Seems to be several shippings since the last run of updateStatusOfOrders for bundle " + bundle.getId());
									}
									newShipped.clear();
									shippingDate = tempShippingDate;
								}
								if ((tempShippingDate.getTime() - 1000*60*5) > shippingDate.getTime()) {
									/* This one is shipped at least five minutes later than the earliest one
									 * found so far, so skip adding this one. */
									//logger.debug("skipping " + shippingDateStr);
								} else {
									newShipped.add(order);
								}
							}
						}
					}
				}
			} catch (NoSuchElementException e) {
				break;
			}
		}
		
		if (!someOrderIsShipped) {
			logger.error("No order is shipped for bundle " + bundle.getId()
					+ " according to the details page although at least one order should be shipped.");
			throw new PlaceOrderException("No order is shipped for bundle " + bundle.getId()
					+ " according to the details page although at least one order should be shipped.");
		}
		
		if (totalNumberOfChecked > bundleOrders.size()) {
			/* Something weird: SellStar's database and FocalPrice's bundle information
			 * doesn't seem to be in sync. totalNumberOfChecked should be <= bundleOrders.size()
			 * because there should be one checked for each shipped order. */
			logger.debug("totalNumberOfChecked = " + totalNumberOfChecked + ", bundleOrders.size = " + bundleOrders.size());
			throw new PlaceOrderException("Something weird for bundle " + bundle.getId() + " in handleShipped."
					+ " Is SellStar's database in sync with FocalPrice?");
		}

		if (newShipped.size() > 0) {
			/* Get the tracking code  for the newly shipped products if this one was shipped by air mail.
			 * Sometimes orders are shipped without a tracking code although they should have had one.
			 * A quite common case is that the first part of a "partly shipped" has a tracking code but
			 * the second part doesn't. */
			String trackingCode = null;
			//WebElement trackingInfoElement = driver.findElement(By.xpath("//body/div[5]/div[2]/div[3]/div[1]/div/p"));
			WebElement trackingInfoElement = driver.findElement(By.xpath("//div[@class='order_track_lists']/p"));
			if (trackingInfoElement.getText().indexOf("has no tracking number") >= 0) {
				logger.debug("No tracking number exists.");
			} else {
				/* There might be several tracking codes in the list (in case the packet was shipped using several
				 * batches). Use the first one that's not already in the database. */
				Thread.sleep(1*1000); //short delay to make sure that the tracing code elements are found
				//List<WebElement> trackingCodeElements = driver.findElements(By.xpath("//body/div[5]/div[2]/div[3]/div[1]/div/ul/li/span"));
				List<WebElement> trackingCodeElements = driver.findElements(By.xpath("//div[@class='order_track_lists']/ul/li/span"));
				Thread.sleep(1*1000); //short delay to make sure that the tracing code elements are found
				Long otherBundleId = null;
				for (WebElement trackingCodeElement : trackingCodeElements) {
					String token = trackingCodeElement.getText();
					logger.debug("token = " + token);
					if (token.length() >= 8 && (token.endsWith("CN") || token.endsWith("HK"))) {
						Tracking oldTrackingObject = trackingDAO.getTrackingObject(token);
						if (oldTrackingObject == null) {
							logger.debug("trackingCode does not exist: " + token);
							trackingCode = token;
							break;
						} else {
							/* Sometimes FocalPrice screws up and gives the same tracking code for
							 * several different packets. Here we check if that's the case. */
							if (!oldTrackingObject.getOrderBundle().getId().equals(bundle.getId())) {
								otherBundleId = oldTrackingObject.getOrderBundle().getId();
								logger.debug("Duplicated tracking code " + token + " detected for bundle " + bundle.getId()
										+ " and bundle " + otherBundleId);
							}
						}
					} else {
						logger.error("Faulty tracking code for bundle " + bundle.getId() + ": " + token + ". Not using this one.");
						//trackingCode = null;
						//throw new ProviderTemporarilyDownException("Faulty tracking code for bundle " + bundle.getId() + ": " + token + ". Not using this one.");
						//throw new PlaceOrderException("Faulty tracking code for bundle " + bundle.getId() + ": " + token + ". Not using this one.");
						/*EmailSender.sendOperationProblemReport("updateStatusOfOrder",
								"Bundle " + bundle.getId() + " has a faulty tracking code: " + token
								+ ". Not using this one (provider order id = " + bundle.getProviderOrderId() + ").");*/
					}
				}
				logger.debug("trackingCode = " + trackingCode);
				
				
				if (trackingCode == null || trackingCode.length() < 8 || trackingCode.indexOf(",") >= 0
						|| (!trackingCode.endsWith("CN") && !trackingCode.endsWith("HK"))) {
					/* Sometimes the tracking code information is written in chinese. Don't go to the error
					 * state then, but rather inform the operator. */
					if (otherBundleId != null) {
						logger.debug("Duplicated tracking code (" + trackingCode + ") detected for bundle " + bundle.getId());
						EmailSender.sendOperationSummaryReport("Duplicated tracking code detected",
								"Duplicated tracking code (" + trackingCode + ") detected for bundle " + bundle.getId()
								+ " (provider order id = " + bundle.getProviderOrderId() + "). The same tracking code"
								+ " was used also for bundle " + otherBundleId + ". This trackingCode might then be"
								+ " invalid also for bundle " + otherBundleId + ". Please correct by hand if necessary.",
								bundle.getProvider().getSystemEmail());
					} else {
						logger.debug("Unparsable tracking code (" + trackingCode + ") detected for bundle " + bundle.getId());
						EmailSender.sendOperationSummaryReport("Unparsable tracking code detected",
								"Unparsable tracking code (" + trackingCode + ") detected for bundle " + bundle.getId()
								+ " (provider order id = " + bundle.getProviderOrderId() + "). Most likely it was"
								+ " shipped without a tracking code.\n\n",
								bundle.getProvider().getSystemEmail());
					}
					trackingCode = null;
				}
			}

			/* Create a new tracking object if tracking code is available. */
			Tracking tracking = null;
			if (trackingCode != null) {
				/* Create a new tracking object and point the newly shipped orders to it.
				 * NOTE: we don't update the state of the newly shipped orders here, because
				 * that's done in the calling method. */
				tracking = new Tracking();
				tracking.setOrderBundle(bundle);
				tracking.setTrackingCode(trackingCode);
				tracking.setFollow(true);
				trackingDAO.save(tracking);
			}
			
			/* Associate the new tracking object (if any) with the order and remove the old one
			 * if this is a resending.
			 * => UPDATE: old tracking objects are removed already in OperatorProcessor.processResendOrder. */
			for (Order order : newShipped) {
				//Tracking oldTrackingObject = order.getTracking();
				if (order.getTracking() != null) {
					throw new Exception("Error: old tracking object exists when detecting \"shipped\" (bundle "
							+ bundle.getId() + ", order " + order.getId() + ").");
				}
				order.setTracking(tracking); //tracking can be null here, if no tracking code was available

				/* If this is a resending, then let's remove the old tracking object. */
				/*if (oldTrackingObject != null) {
					if (isResend(order)) {
						logger.debug("Removing old tracking object " + oldTrackingObject.getId()
								+ " (" + oldTrackingObject.getTrackingCode() + ") for bundle "
								+ bundle.getId() + " because of resending.");
						//TODO: hmm, this will probably result in an exception if one product in a
						//multi-order bundle was resent since the other orders in the bundle still
						//point to the removed tracking object. This should maybe be fixed by removing
						//the tracking object already at "Resend order" in OperatorProcessor (or else
						//we should here make sure that it's not removed in case non-resent other
						//orders in this bundle still point to this tracking object).
						trackingDAO.delete(oldTrackingObject);
					} else {
						throw new Exception("Error: old tracking object exists although not resending (bundle "
								+ bundle.getId() + ", order " + order.getId() + ").");
					}
				}*/
			}
		}
		return newShipped;
	}

	@Override
	public void updateTechnicalSpecs(Product product) throws Exception {
		/*if (!product.getTechnicalSpecs().trim().equals("")) {
			return;
		}*/
		if (product.getProductState().equals(Product.STATE_NO_LONGER_FOR_SALE)) {
			logger.debug("Skipping product " + product.getProviderProdId() + " because it's no longer for sale.");
			return;
		}
		if (product.getProductLinkId() != null) {
			/* Don't update products that are linked to other products, because these might have
			 * some text manually added to them, for example "HUOM: Jenkkimallinen latauspistoke". */
			logger.debug("Skipping product " + product.getProviderProdId() + " because it has a product link.");
			return;
		}
		if (productExistsAtProviderInternal(product) == null) {
			/* Just do a quick check using productExistsAtProviderInternal and
			 * not the slower productExistsAtProvider, because here it's not
			 * a very big deal if some product update is skipped. */
			return;
		}
		//productExistsAtProvider(product);
		String oldName = product.getName();
		String oldProviderCategory = product.getProviderCategory();
		String oldDimensions = product.getDimensions();
		String oldContents = product.getContents();
		String oldTechSpecs = product.getTechnicalSpecs();
		updateProductInfoInternal(product);
		String newName = product.getName();
		String newProviderCategory = product.getProviderCategory();
		String newDimensions = product.getDimensions();
		String newContents = product.getContents();
		String newTechSpecs = product.getTechnicalSpecs();
		boolean changed = false;
		if (!StringUtilities.sloppyMatch(oldName,newName)) {
			logger.debug("Changed name for product " + product.getId() + " (" + product.getProviderProdId() + ")"
					+ ": oldName = " + oldName + ", newName = " + newName);
			changed = true;
		}
		if (!StringUtilities.sloppyMatch(oldProviderCategory,newProviderCategory)) {
			logger.debug("Changed provider category for product " + product.getId() + " (" + product.getProviderProdId() + ")"
					+ ": oldProviderCategory = " + oldProviderCategory + ", newProviderCategory = " + newProviderCategory);
			changed = true;
		}
		if (!StringUtilities.sloppyMatch(oldDimensions,newDimensions)) {
			logger.debug("Changed dimensions for product " + product.getId() + " (" + product.getProviderProdId() + ")"
					+ ": oldDimensions = " + oldDimensions + ", newDimensions = " + newDimensions);
			changed = true;
		}
		if (!StringUtilities.sloppyMatch(oldContents,newContents)) {
			logger.debug("Changed contents for product " + product.getId() + " (" + product.getProviderProdId() + ")"
					+ ": oldContents = " + oldContents + ", newContents = " + newContents);
			changed = true;
		}
		if (!StringUtilities.sloppyMatch(oldTechSpecs,newTechSpecs)) {
			logger.debug("Changed techSpecs for product " + product.getId() + " (" + product.getProviderProdId() + ")"
					+ ": oldTechSpecs = " + oldTechSpecs + ", newTechSpecs = " + newTechSpecs);
			changed = true;
		}
		if (!changed) {
			logger.debug("Product " + product.getId() + " (" + product.getProviderProdId() + ")"
					+ " was left unchanged.");
		}
	}
	
	/**
	 * There are certain strings that we don't want to include:
	 * "Weekend deal! Expires soon!",
	 * "Because of increasing in production costs, price rise from $19.82  - 11/5/2008"
	 * "In stock!".
	 * "FocalPrice"
	 */
	private boolean safeRow(String row, Product product) {
		String upper = row.toUpperCase();
		if (upper.indexOf("WEEKEND DEAL") >= 0 || upper.indexOf("EXPIRES SOON") >= 0
				|| upper.indexOf("FOCALP") >= 0 || upper.indexOf("FOCAL P") >= 0
				|| upper.indexOf("IN STOCK") >= 0 || upper.indexOf("PRODUCTION COST") >= 0
				|| upper.indexOf("WEEKENDDEAL") >= 0 || upper.indexOf("WEEKEND PROMOTION") >= 0) {
			logger.debug("Row \"" + row + "\" unsafe for product " + product.getProviderProdId());
			return false;
		}
		return true;
	}

	//TODO: this code probably doesn't support product CE053W
	private void updateProductInfoInternal(Product product) {
		logger.info("updateProductInfoInternal for product " + product.getId());
		CustomWebDriver driver = seleniumRobot.getWebDriver();
		driver.get(product.getProviderUrl(),3);

		/* Figure out the provider category. The last element is the product name and don't include that. */
		String providerCategory = "";
		String providerCategorySecondLast = null;
		for (int i = 1;; i++) {
			WebElement providerCategoryElem = driver.findElementOrNull(By.xpath("//*[@id='breadcrumbs']/div/h3/span/span/a[" + i + "]"));
			if (providerCategoryElem != null) {
				String row = StringUtilities.normalizer(providerCategoryElem.getText().trim());
				if (safeRow(row,product)) {
					providerCategorySecondLast = providerCategory;
					providerCategory = (!providerCategory.equals("") ? providerCategory + " --- " : "") + row;
				}
			} else {
				break;
			}
		}
		if (providerCategorySecondLast != null && !providerCategorySecondLast.trim().equals("")) {
			if (providerCategorySecondLast.indexOf(INTIMATE_GADGETS) > -1
					|| (providerCategorySecondLast.indexOf(UNDERWEAR) > -1 && providerCategorySecondLast.toUpperCase().indexOf("WOMAN") > -1)
					|| providerCategorySecondLast.indexOf(JEWELRY) > -1) {
				logger.info("Item from dumb product category will be skipped (product = " + product.getProviderProdId()
						+ ", category = " + providerCategorySecondLast + ").");
				throw new RuntimeException("Item from dumb product category will be skipped");
				//logger.info("Item from dumb product category is included (product = " + product.getProviderProdId()
					//	+ ", category = " + providerCategorySecondLast + ").");
			}
			if (!safeRow(providerCategorySecondLast,product)) {
				logger.info("Provider category \"" + providerCategorySecondLast + "\" unsafe for product " + product.getProviderProdId());
				throw new RuntimeException("Provider category \"" + providerCategorySecondLast + "\" unsafe for product " + product.getProviderProdId());
			}
			product.setProviderCategory(providerCategorySecondLast);
		}
		
		String productName = driver.findElement(By.xpath("//*[@id='productName']")).getText();
		if (!safeRow(productName,product)) {
			logger.info("Product name \"" + productName + "\" unsafe for product " + product.getProviderProdId());
			throw new RuntimeException("Product name \"" + productName + "\" unsafe for product " + product.getProviderProdId());
		}
		product.setName(productName);

		/* Figure out the product details. */
		String packageDetails = "";
		for (int i = 1;; i++) {
			WebElement packageDetailsElem = driver.findElementOrNull(By.xpath("//*[@id='productDetail']/span[" + i + "]"));
			if (packageDetailsElem != null) {
				String row = packageDetailsElem.getText();
				row = StringUtilities.normalizer(row.trim());
				packageDetails = (!packageDetails.equals("") ? packageDetails + "\n" : "") + row;
			} else {
				break;
			}
		}
		if (!packageDetails.trim().equals("")) {
			product.setDimensions(packageDetails);
		}
		
		/*  Figure out the package contents. */
		WebElement contentsElem = driver.findElementOrNull(By.xpath("//*[@id='productPackContent']"));
		if (contentsElem != null && contentsElem.getText() != null && !contentsElem.getText().trim().equals("")) {
			String contents = contentsElem.getText();
			contents = contents.replaceAll("\u00D7", "x");
			contents = StringUtilities.normalizer(contents);
			product.setContents(contents);
		}
		
		/* There are a few different types of page looks in use at FocalPrice. Let's try them in turn.
		 * First try a generic one that should match most types. Make sure this one works with the following types:
		 * http://www.focalprice.com/HL010W/something.html
		 * http://www.focalprice.com/DS216B/something.html
		 * http://www.focalprice.com/HJ785L/something.html
		 * http://www.focalprice.com/EB094T/something.html
		 * http://www.focalprice.com/MF084B/something.html
		 * http://www.focalprice.com/YWA27W/something.html
		 * http://www.focalprice.com/CK013B/something.html
		 * http://www.focalprice.com/MS523T/something.html
		 * http://www.focalprice.com/ERQ66S/something.html
		 * http://www.focalprice.com/HE051S/something.html
		 * http://www.focalprice.com/HLA28X/something.html
		 * */
		String techSpecs = "";
		String size = null;
		for (int i = 1;; i++) {
			WebElement descElem = driver.findElementOrNull(By.xpath("//*[@id='Description']/div/div[1]//li[" + i + "]"));
			if (descElem != null) {
				String row = StringUtilities.normalizer(descElem.getText().trim());
				if (safeRow(row,product)) {
					techSpecs = (!techSpecs.equals("") ? techSpecs + "\n" : "") + row;
				}
			} else {
				break;
			}
		}
		
		/* Then try another generic type that matches with most ones not matched by the above query. Make sure this one works with the following types:
		 * http://www.focalprice.com/IPH02T/something.html
		 * http://www.focalprice.com/IP880R/something.html
		 * http://www.focalprice.com/IP605U/something.html
		 * */
		if (techSpecs.trim().equals("")) {
			/* Some products have several elements, such as http://www.focalprice.com/FW0046X/something.html,
			 * so pick the first one here. */
			List<WebElement> tableElementList = driver.findElements(By.xpath("//*[@id='Description']/div/div[1]//table/tbody"));
			if (tableElementList.size() > 0) {
				WebElement firstTable = tableElementList.get(0);
				for (int i = 1;; i++) {
					WebElement col1Elem = null;
					try {
						col1Elem = firstTable.findElement(By.xpath(".//tr[" + i + "]/td[1]"));
					} catch (NoSuchElementException e) {
					}
					WebElement col2Elem = null;
					try {
						col2Elem = firstTable.findElement(By.xpath(".//tr[" + i + "]/td[2]"));
					} catch (NoSuchElementException e) {
					}
					if (col1Elem != null) {
						String row = null;
						if (col2Elem != null && !StringUtilities.normalizer(col2Elem.getText().trim()).equals("")) {
							if (col1Elem.getText().trim().equals("Size")) {
								size = col2Elem.getText().trim().replaceAll("#", "");
							}
							row = StringUtilities.normalizer(col1Elem.getText().trim()) + ": " + StringUtilities.normalizer(col2Elem.getText().trim());
						} else {
							row = StringUtilities.normalizer(col1Elem.getText().trim());
							if (row.length() < 20) {
								row = row.toUpperCase();
							}
						}
						if (safeRow(row,product)) {
							techSpecs = (!techSpecs.equals("") ? techSpecs + "\n" : "") + row.trim();
						}
					} else {
						break;
					}
				}
			}
		}
		
		/* If the above didn't work, then try with the following one. This one works for example for http://www.focalprice.com/MS970B/something.html */
		if (techSpecs.trim().equals("")) {
			for (int i = 1;; i++) {
				WebElement descElem = driver.findElementOrNull(By.xpath("//*[@id='Description']/div/div[1]/span[" + i + "]/span"));
				if (descElem != null) {
					String row = StringUtilities.normalizer(descElem.getText().trim());
					if (safeRow(row,product)) {
						techSpecs = (!techSpecs.equals("") ? techSpecs + "\n" : "") + row;
					}
				} else {
					break;
				}
			}
		}

		/* There are a few products that don't work with the above ones, for example
		 * http://www.focalprice.com/CX139B/something.html */
		if (techSpecs.trim().equals("")) {
			List<WebElement> prodOverviewElementList = driver.findElements(By.xpath("//*[@id='Description']/div/div[1]//span"));
			if (prodOverviewElementList.size() > 0) {
				for (int i = 0; i < prodOverviewElementList.size(); i++) {
					WebElement descElem = prodOverviewElementList.get(i);
					if (descElem.getText() != null) {
						String row = StringUtilities.normalizer(descElem.getText().trim());
						if (safeRow(row,product) && product.getDimensions().indexOf(row) < 0
								&& product.getContents().indexOf(row) < 0 && !row.startsWith("Package Details")
								&& !row.startsWith("Package Includes") && techSpecs.indexOf(row) < 0) {
							techSpecs = (!techSpecs.equals("") ? techSpecs + "\n" : "") + row;
						}
					} else {
						break;
					}
				}
			}
		}

		/* For some products there is also a table with sizes included, for example for http://www.focalprice.com/FW0063X/something.html */
		List<WebElement> tableElementList = driver.findElements(By.xpath("//table[@class='ke-zeroborder']"));
		if (tableElementList.size() > 0) {
			/* Product http://www.focalprice.com/FW0063X/something.html has two elements
			 * and product http://www.focalprice.com/FW0064B/something.html has only one
			 * element, so we should always take the last one here. */
			WebElement tableElem = tableElementList.get(tableElementList.size()-1);
			logger.debug("tableElem found for product " + product.getProviderProdId() + ": " + tableElem.getText());
			List<WebElement> tableRows = tableElem.findElements(By.xpath(".//tbody/tr"));
			
			/* Some products have the measurements of several sizes in one single table, so in
			 * this case we need to figure out which one to use. For example see product
			 * http://www.focalprice.com/FM0064G/something.html */
			int i = 0;
			int sizeColumn = -1;
			String model = null;
			if (size != null) {
				try {
					outer: for (i = 0; i < tableRows.size(); i++) {
						WebElement row = tableRows.get(i);
						List<WebElement> rowColumns = row.findElements(By.xpath(".//td"));
						logger.debug("row found: " + row.getText());
						if (rowColumns.size() > 0) {
							WebElement col = rowColumns.get(0);
							String colText = col.getText();
							//logger.debug("colText = \"" + colText + "\"");
							if (colText.equals("Size")) {
								for (int j = 1; j < rowColumns.size(); j++) {
									col = rowColumns.get(j);
									if (col.getText().equals(size)) {
										sizeColumn = j;
										logger.debug("sizeColumn is " + sizeColumn);
										break outer;
									}
								}
								break;
							}
						}
					}
					if (sizeColumn < 0) {
						logger.debug("Couldn't find sizeColumn (size = " + size + " probably doesn't correspond to any string in the table).");
					}
					/* There might be a model string describing the product, for example
					 * product http://www.focalprice.com/FW0046X/something.html has "160/84B". */
					if (sizeColumn >= 0) {
						WebElement row = tableRows.get(i+1);
						List<WebElement> rowColumns = row.findElements(By.xpath(".//td"));
						WebElement col = rowColumns.get(sizeColumn-1);
						String colText = col.getText();
						if (colText.matches("\\d+/\\d+B")) {
							model = colText;
							logger.debug("model is " + model);
						}
					}
				} catch (Exception e) {
					logger.error("Failed getting sizeColumn and/or model (sizeColumn = " + sizeColumn + ", model = " + model + ").");
				}
			}
			
			/* Make sure that this is a type of table that we recognize by making sure
			 * that there is one row telling the measurements in centimeters an in inches. */
			i = 0;
			//for (WebElement row : tableRows) {
			boolean cmFound = false;
			boolean inchesFound = false;
			String firstMeasurement = null;
			for (i = 0; i < tableRows.size(); i++) {
				WebElement row = tableRows.get(i);
				List<WebElement> rowColumns = row.findElements(By.xpath(".//td"));
				cmFound = false;
				inchesFound = false;
				firstMeasurement = null;
				for (WebElement col : rowColumns) {
					if (col.getText().equals("cm")) {
						cmFound = true;
						if (firstMeasurement == null) {
							firstMeasurement = "cm";
						}
					} else if (col.getText().equals("inches")) {
						inchesFound = true;
						if (firstMeasurement == null) {
							firstMeasurement = "inches";
						}
					}
				}
				if (cmFound && inchesFound) {
					break;
				}
			}

			Map<String,String> cmMeasurements = new HashMap<String,String>();
			if (!cmFound || !inchesFound) {
				logger.debug("Couldn't interpret tableElem for product " + product.getProviderProdId());
			} else {
				/* Check which column is used for the particular product (not all columns are
				 * always in use, for example see http://www.focalprice.com/FW0046X/something.html). */
				for (; i < tableRows.size(); i++) {
					WebElement row = tableRows.get(i); //this is the row following the cm/inches row
					List<WebElement> rowColumns = row.findElements(By.xpath(".//td"));
					WebElement col = rowColumns.get(0);
					String colText = col.getText();
					if (colText.equals("Shoulder") || colText.equals("Sleeve")
							|| colText.equals("Length") || colText.equals("Bust")
							|| colText.equals("Waist") || colText.equals("Hips")  || colText.equals("Hip")
							|| colText.equals("Front Length") || colText.equals("Back Length")
							|| colText.equals("Thigh") || colText.equals("Thigh ")) {
						//boolean cmToggle = (firstMeasurement.equals("cm"));
						for (int j = 1; j < rowColumns.size(); j++) {
							col = rowColumns.get(j);
							if (col.getText().indexOf("-") < 0) {
								if (j % 2 == 1 && firstMeasurement.equals("cm")
										|| j % 2 == 0 && firstMeasurement.equals("inches")) {
									if (cmMeasurements.get(colText) != null) {
										/* This table apparently has measurements of several different sizes
										 * in the same table, so then we need to figure out which column to use. */
										if ((j+1) / 2 == sizeColumn) {
											logger.debug("Several columns containing centimeter measurements, using column "
													+ sizeColumn + " (j = " + j + ").");
											cmMeasurements.put(colText,col.getText());
										} else {
											//don't overwrite the already added one
											logger.debug("Several columns containing centimeter measurements, don't overwrite"
													+ " (sizeColumn = "+ sizeColumn + ", j = " + j + ").");
										}
									} else {
										cmMeasurements.put(colText,col.getText());
									}
								}
							}
						}
					} else {
						logger.debug("Unrecognized measurement type: \"" + colText + "\" (row = " + row.getText() + ").");
					}
				}
			}
			
			if (cmMeasurements.size() > 0) {
				techSpecs = (!techSpecs.equals("") ? techSpecs + "\n" : "") + "SIZE";
				Iterator<String> iter = cmMeasurements.keySet().iterator();
				while (iter.hasNext()) {
					String key = iter.next();
					techSpecs = techSpecs + "\n" + key + ": " + cmMeasurements.get(key) + " cm";
				}
				if (model != null) {
					techSpecs = techSpecs + "\nModel: " + model;
				}
			}
		}
		
		/* Then get the product overview. This text might actually have been fetched already, so only include it
		 * if it's not already included. */
		String overview = "";
		List<WebElement> prodOverviewElementList = driver.findElements(By.xpath("//*[@id='Description']/div/div[1]//span"));
		if (prodOverviewElementList.size() > 0) {
			for (int i = 0; i < prodOverviewElementList.size(); i++) {
				WebElement overviewRow = prodOverviewElementList.get(i);
				if (overviewRow.getText() != null && overviewRow.getText().trim().toUpperCase().indexOf("THE") >= 0) {
					overview = StringUtilities.normalizer(overviewRow.getText().trim());
					logger.debug("overview for product " + product.getProviderProdId() + ": " + overview);
					break;
				} else {
					logger.debug("overview temp row for product " + product.getProviderProdId() + ": " + overviewRow.getText());
				}
			}
		}
		if (!techSpecs.trim().equals("") && !overview.equals("")) {
			if (techSpecs.indexOf(overview.substring(0,Math.min(overview.length(),50))) < 0 && overview.split("\n").length < 5) {
				if (safeRow(overview,product)) {
					logger.debug("Adding overview to techSpecs for product " + product.getProviderProdId());
					techSpecs = "Overview: " + overview + "\n\n" + techSpecs;
				}
			} else {
				logger.debug("Overview is already included for product " + product.getProviderProdId());
			}
		}

		if (!techSpecs.trim().equals("")) {
			product.setTechnicalSpecs(techSpecs);
			return;
		}

		return;
	}
	
	/**
	 * This method creates a new Product object fetching the information from the web page of the provider.
	 * The object is NOT stored to the database. No images are fetched by this method.
	 */
	@Override
	public Product createProduct(String providerProductId) throws Exception {
		Provider provider = providerDAO.getProviderByName(Provider.FOCAL_PRICE_PROVIDER_NAME);
		if (productDAO.getByProviderProdId(providerProductId, provider) != null) {
			logger.debug("Product " + providerProductId + " already exists, skipping this one.");
			return null;
		} else {
			logger.debug("Product " + providerProductId + " doesn't exist, creating this one.");
		}

		Product product = new Product();
		product.setCompensation(false);
		product.setProviderProdId(providerProductId);
		product.setProvider(provider);
		product.setProviderUrl(getProviderProductUrl(providerProductId));
		product.setMinProviderShippingCostProviderCurrencyIncVat(BigDecimal.ZERO);
		product.setNotFoundCounter(0);
		product.setProductState(Product.STATE_UNKNOWN);
		CustomWebDriver driver = seleniumRobot.getWebDriver();

		// Try to open several times, because FocalPrice is sometimes hard to reach.
		//driver.get(product.getProviderUrl(),By.xpath("//*[@id='Description' and @class='description mt10']"),5);

		// Check if product is out of stock
		/*if (!isProductInStock(product,true)) {
			logger.info("Product " + providerProductId + " is out of stock and is skipped");
			return null;
		}*/
		
		if (productExistsAtProviderInternal(product) == null) {
			logger.info("Product " + providerProductId + " doesn't exist and is skipped.");
			return null;
		}

		updateProductInfoInternal(product);
		return product;
	}

	//TODO: start using the field product.provider_url instead
	String getProviderProductUrl(String providerProductId) {
		return "http://www.focalprice.com/" + providerProductId + "/something.html";
	}

	public class StockStatus {
		private String stockDescription = null;
		private String shippingTime = null;
		private Boolean allowBuy = null;
		public StockStatus() {
		}
		public String getStockDescription() {
			return stockDescription;
		}
		public void setStockDescription(String stockDescription) {
			this.stockDescription = stockDescription;
		}
		public String getShippingTime() {
			return shippingTime;
		}
		public void setShippingTime(String shippingTime) {
			this.shippingTime = shippingTime;
		}
		public Boolean getAllowBuy() {
			return allowBuy;
		}
		public void setAllowBuy(Boolean allowBuy) {
			this.allowBuy = allowBuy;
		}
	}
	
	private StockStatus queryStockStatusThroughAPI(Product product) throws ProviderTemporarilyDownException {
		/*HttpClient client = new DefaultHttpClient();
		//HttpGet get = new HttpGet("http://api.huuto.net/somt/0.9-beta/items/" + ad.getMarketSalesId());
		HttpPost post = new HttpPost("www.focalprice.com/QueryStockStatus?sku=" + product.getProviderProdId());
        //JSONObject jsonParams = JSONObject.fromObject("{'lat':" + latitude + ",'lon':" + longitude + ",'k':1}");
        //post.setEntity(new StringEntity(jsonParams.toString()));
        post.setHeader("Accept", "application/json");
        post.setHeader("Content-type", "application/json");*/

		/* The QueryStockStatus API returns something also for products that are not for sale.
		 * For example http://www.focalprice.com/QueryStockStatus?sku=asdasd returns:
		 * {"shippingDate":"","stockStatus":"Out Of Stock","allowBuy":false} */
		StockStatus stockStatus = new StockStatus();
		HttpClient client = new DefaultHttpClient();
		HttpGet get = new HttpGet("http://www.focalprice.com/QueryStockStatus?sku=" + product.getProviderProdId());
		String jsonRespStr = null;
		try {
			logger.debug("Calling QueryStockStatus for isProductInStock");
			HttpResponse resp = client.execute(get);
			logger.debug("Returned from QueryStockStatus for isProductInStock");
			HttpEntity resEntity = resp.getEntity();
			jsonRespStr = EntityUtils.toString(resEntity);
			logger.debug("jsonRespStr = " + jsonRespStr);
			JSONObject jsonObj = new JSONObject(jsonRespStr);
			stockStatus.setStockDescription(jsonObj.getString("stockStatus"));
			//logger.debug("stockDescription = " + stockStatus.getStockDescription());
			stockStatus.setShippingTime(jsonObj.getString("shippingDate"));
			//logger.debug("shippingTime = " + stockStatus.getShippingTime());
			stockStatus.setAllowBuy(jsonObj.getBoolean("allowBuy"));
			//logger.debug("allowBuy = " + stockStatus.getAllowBuy());
		} catch (JSONException e) {
			logger.error("FocalPrice bailed out with unexpected error for QueryStockStatus (for item " + product.getProviderProdId()
					+ ". JSON parsing failed for string " + jsonRespStr + ". Error: ",e);
			if (jsonRespStr != null && jsonRespStr.indexOf("Bad Gateway") >= 0) {
				throw new ProviderTemporarilyDownException("FocalPrice temporarily down (queryStockStatusThroughAPI)");
			}
			throw new RuntimeException("FocalPrice bailed out with unexpected error for QueryStockStatus.");
		} catch (Exception e) {
			logger.error("Error checking isProductInStock from FocalPrice for item " + product.getProviderProdId() + ": " + e.getMessage());
			logger.debug("Error checking isProductInStock from FocalPrice for item " + product.getProviderProdId() + ": ", e);
			throw new ProviderTemporarilyDownException("FocalPrice temporarily down.");
		}
		return stockStatus;
	}
	
	@Override
	public boolean isProductInStock(Product product, boolean sloppyStockChecking)
		throws ProductNotFoundAtProviderException, ProviderTemporarilyDownException {

		/* Here we cannot rely _only_ on the FocalPrice API call for checking stock status, for two reasons:
		 * 1.) Products that really are no longer for sale are still returned as having a sellable
		 *     status. Therefore we need to check if opening the page gives a 404/not found error.
		 * 2.) There is no API call for checking for express deliveries (items that need to be
		 *     shipped through a special shipping company). This information however exists directly
		 *     on the web page. */
		logger.debug("Getting page source for isProductInStock");
		String productPage = productExistsAtProviderInternal(product);
		logger.debug("Got page source for isProductInStock");
		if (productPage == null) {
			throw new ProductNotFoundAtProviderException("Product not found at FocalPrice");
		}
		String shippingType = getFieldValueFromHtml(productPage,"id","free_shipping");
		String expressDelivery = getFieldValueFromHtml(productPage,"id","express_delivery");
		if ((shippingType != null && shippingType.equals("Express Delivery"))
				|| (expressDelivery != null && expressDelivery.equals("Express Delivery"))) {
			/* Certain items are oversize/overweight and can therefore only be shipped using Express Delivery
			 * which is much more expensive. We don't want to sell such products, so return false here. */
			logger.debug("This item is oversize or overweight and can only be shipped"
					+ " using Express Delivery. Skipping this one (product " + product.getId() + ").");
			return false;
		}
		
		StockStatus stockStatus = queryStockStatusThroughAPI(product);
		logger.debug("stockDescription = " + stockStatus.getStockDescription() + ", shippingTime = " + stockStatus.getShippingTime() + ", allowBuy = " + stockStatus.getAllowBuy());
		if (!stockStatus.getAllowBuy()) {
			return false;
		}

		/*if (shippingTime == null || stockDescription == null) {
			throw new ProviderTemporarilyDownException("FocalPrice temporarily down.");
		}*/
		if (stockStatus.getStockDescription().equals("In Stock") && (stockStatus.getShippingTime().indexOf("Shipping within 24 hours") >= 0
				|| stockStatus.getShippingTime().indexOf("Usually will be shipped within 48 hours") >= 0)) {
			 /* shippingTime = "Shipping within 24 hours": means that the product is in stock
			  * shippingTime = "Usually will be shipped within 48 hours": means that only a few items
			  * are left in stock, but the product can be re-stocked quickly. */
			return true;
		} else if (stockStatus.getShippingTime().indexOf("Usually will be shipped within 3-5 days") >= 0
				|| stockStatus.getShippingTime().indexOf("Usually will be shipped within 2-3 days") >= 0) {
			//I'm not totally sure about this one - should we return true or false here...
			return true;
		} else if (stockStatus.getStockDescription().equals("Temporarily out of stock")) {
			return false;
		} else if (stockStatus.getStockDescription().trim().equals("") && stockStatus.getShippingTime().indexOf("Usually will be shipped within 48 hours") >= 0) {
			/* It seems like FocalPrice has three levels for stock information
			 * 1.) "In Stock" => there is plenty of products in stock
			 * 2.) "Usually will be shipped within 48 hours" => there are a few items in stock, or the product can be re-stocked quickly
			 * 3.) "Out Of Stock" => product not available.
			 * If sloppy stock checking is in use, then we return true also for case #2 above. */
			return sloppyStockChecking;
		} /*else if (stockDescription.getText().trim().equals("") && shippingTime.getText().indexOf("It will be shipped after Feb") >= 0) {
			return true;
		} */else if (stockStatus.getStockDescription().indexOf("Out Of Stock") >= 0 && stockStatus.getShippingTime().trim().equals("")) {
			return false;
		}
		logger.debug("Unsure stock information for " + product.getProviderProdId() + ": stockDescription = "
				+ stockStatus.getStockDescription() + ", shippingTime = " + stockStatus.getShippingTime());
		throw new ProviderTemporarilyDownException("FocalPrice temporarily down.");
	}

	@Override
	public List<String> getGoodProductIds(int count, VirtualCoin minPriceExclVat,
			VirtualCoin maxPriceExclVat, BigDecimal percentsOfProductsInSameCategory)
			throws Exception {
		int maxPages = 10;
		//int maxNewProdsPerCategory = 20;
		//Random random = new Random();
		
		CustomWebDriver driver = seleniumRobot.getWebDriver();
		logger.info("Fetching " + count + " good products");
		List<String> ids = new ArrayList<String>();
		Provider provider = this.providerDAO
				.getProviderByName(Provider.FOCAL_PRICE_PROVIDER_NAME);
		//WebElement priceNow = driver.findElementOrNull(By.id("nowprice"));

		/* First get the global top sellers. */
		//int maxNewProdsPerCategory = 200;
		driver.get("http://www.focalprice.com/topsellers",5);
		if (getProductsOnPages(maxPages,ids,count,200,provider,minPriceExclVat,maxPriceExclVat)) {
			return ids;
		}
		//maxNewProdsPerCategory = 20;
		
		/* Load the sitemap. */
		boolean loaded = false;
		for (int i = 0; i < 5; i++) {
			try {
				driver.get("http://www.focalprice.com/SiteMap",5);
				driver.waitForElement(By.xpath("//*[@id='allcate']/div[1]/ul[1]/h4/a"));
				loaded = true;
				break;
			} catch (Exception e) {
				logger.error("Couldn't load sitemap: " + e.getMessage());
			}
		}
		if (!loaded) {
			throw new RuntimeException("Couldn't load sitemap.");
		}

		// Calculate category count
		int mainCategoryCount = 0;
		for (int i = 1;; i++) {
			if (driver.isElementPresent(By.xpath("//*[@id='allcate']/div[1]/ul[" + i + "]/h4/a"))) {
				mainCategoryCount++;
			} else {
				break;
			}
		}
		logger.info(mainCategoryCount + " main categories were found");
		
		// Select randomly one of the main categories
		/*List<Integer> usedCategories = new ArrayList<Integer>();
		int selectedMainCategory = random.nextInt(mainCategoryCount) + 1;
		if (usedCategories.contains(selectedMainCategory)) {
			continue;
		} else {
			usedCategories.add(selectedMainCategory);
		}*/

		for (int mainCategoryNbr = 1; mainCategoryNbr <= mainCategoryCount; mainCategoryNbr++) {
			WebElement mainCategoryElem = driver.findElement(By.xpath("//*[@id='allcate']/div[1]/ul[" + mainCategoryNbr + "]/h4/a"));
			String mainCategoryName = mainCategoryElem.getText();
			logger.info("***** SELECTING MAIN CATEGORY '" + mainCategoryName + "' *****");

			// Calculate sub category count
			int subCategoryCount = 0;
			for (int i = 1;; i++) {
				if (driver.isElementPresent(By.xpath("//*[@id='allcate']/div[1]/ul[" + mainCategoryNbr + "]/li[" + i + "]/a"))) {
					subCategoryCount++;
				} else {
					break;
				}
			}
			logger.info(subCategoryCount + " sub categories were found");
			for (int subCategoryNbr = 1; subCategoryNbr <= subCategoryCount; subCategoryNbr++) {
				WebElement subCatElem = driver.findElement(By.xpath("//*[@id='allcate']/div[1]/ul[" + mainCategoryNbr + "]/li[" + subCategoryNbr + "]/a"));
				logger.info("***** selecting sub category '" + subCatElem.getText() + "' (for '" + mainCategoryName + "') *****");
				subCatElem.click();

				/* Sort by top seller. */
				logger.info("Selecting hot sort");
				String url = driver.getCurrentUrl();
				String provCategory = url.substring(url.lastIndexOf("/")+1);
				if (provCategory.startsWith("ca-")) {
					provCategory = provCategory.substring(3);
				}
				if (provCategory.indexOf(".html") >= 0) {
					provCategory = provCategory.substring(0,provCategory.indexOf(".html"));
				}
				logger.debug("provCategory = \"" + provCategory + "\"");
				//logger.info("url = " + url + ", provCategory = " + provCategory);
				driver.get("http://www.focalprice.com/categorylist/" + provCategory + "?orderby=hot");
				
				/* Figure out provider top category and sub category. */
				String provCategoryStr = "";
				for (int i = 0; i < 3; i++) {
					try {
						List<WebElement> categoryElemList = driver.findElements(By.xpath("//*[@id='breadcrumbs']/div/h3//a"));
						for (int j = 1; j < categoryElemList.size(); j++) {
							WebElement providerCategoryElem = categoryElemList.get(j);
							String row = StringUtilities.normalizer(providerCategoryElem.getText().trim());
							provCategoryStr = (!provCategoryStr.equals("") ? provCategoryStr + " --- " : "") + row;
						}
					} catch (Exception e) {
						logger.debug("Failed getting provCategoryStr.");
					}
					if (!provCategoryStr.equals("")) {
						break;
					}
					if (i < (3-1)) {
						logger.debug("Trying again to get provCategoryStr.");
						Thread.sleep(1*1000);
					}
				}
				if (provCategoryStr.equals("")) {
					logger.error("Failed getting products from category " + provCategory);
				}

				/* Figure out how many items to fetch. */
				logger.debug("provCategoryStr = " + provCategoryStr);
				int nbrExisting = productDAO.getNbrProductsLikeProviderCategory(provider,provCategoryStr + "%");
				int totItemCount = -1;
				for (int i = 0; i < 3; i++) {
					try {
						WebElement itemCountElem = driver.findElement(By.id("all_item_count"));
						totItemCount = Integer.parseInt(itemCountElem.getText());
					} catch (Exception e) {
						logger.debug("Failed getting all item count.");
					}
					if (totItemCount >= 0) {
						break;
					}
					if (i < (3-1)) {
						logger.debug("Trying again to get all item count.");
						Thread.sleep(1*1000);
					}
				}
				if (totItemCount < 0) {
					logger.error("Failed getting products from category " + provCategoryStr);
				}
				int maxCountOfTheSameCategory = (int) (totItemCount * percentsOfProductsInSameCategory.doubleValue());
				int maxNewCount = maxCountOfTheSameCategory - nbrExisting;
				logger.info("nbrExisting = " + nbrExisting + ", totItemCount = " + totItemCount + ", percentsOfProductsInSameCategory = "
						+ percentsOfProductsInSameCategory + ", maxCountOfTheSameCategory = " + maxCountOfTheSameCategory
						+ ", maxNewCount = " + maxNewCount);
		
				if (maxNewCount > 0) {
					if (getProductsOnPages(maxPages,ids,count,maxNewCount,provider,minPriceExclVat,maxPriceExclVat)) {
						return ids;
					}
				} else {
					logger.debug("Skipping fetching from category " + provCategoryStr
							+ " because we already have many products for this category.");
				}
				
				/* Load the sitemap. */
				loaded = false;
				for (int i = 0; i < 5; i++) {
					try {
						driver.get("http://www.focalprice.com/SiteMap",5);
						driver.waitForElement(By.xpath("//*[@id='allcate']/div[1]/ul[1]/h4/a"));
						loaded = true;
						break;
					} catch (Exception e) {
						logger.error("Couldn't load sitemap: " + e.getMessage());
					}
				}
				if (!loaded) {
					throw new RuntimeException("Couldn't load sitemap.");
				}
			}
		}
		return ids;
	}

	private boolean getProductsOnPages(int maxPages, List<String> ids, int count, int maxNewProdsPerCategory, Provider provider,
			VirtualCoin minPriceExclVat, VirtualCoin maxPriceExclVat) {
		CustomWebDriver driver = seleniumRobot.getWebDriver();
		int nbrAddedForCategory = 0;
		//while (currPage <= maxPages && nbrAddedForCategory < maxNewProdsPerCategory) {
		int nbrTooExpensives = 0;
		for (int currPage = 0; currPage < maxPages; currPage++) {
			for (int i = 1;; i++) { //go through all the products on the current page
				logger.info("Total count of product ids now "
						+ ids.size() + " of " + count);
				if (ids.size() >= count) {
					return true;
				}
				logger.info("Total count of product ids for current category is "
						+ nbrAddedForCategory + " of " + maxNewProdsPerCategory);
				if (nbrAddedForCategory >= maxNewProdsPerCategory) {
					return false;
				}
				/*if (countOfCategoryProducts >= maxCountOfTheSameCategory) {
					logger.info("MAX COUNT OF PRODUCTS IN THE SAME CATEGORY "
							+ countOfCategoryProducts + " HAS BEEN REACHED.");
					break pages;
				}*/
				WebElement prodUrl = driver.findElementOrNull(By.xpath("//*[@id='list_content']/div[" + i + "]/ul/li[1]/a"));
				if (prodUrl == null) {
					// No more products exist on this page (either we need to go to the next page, or
					// else there are no more products for this category).
					break; 
				}
				String id = this.getIdFromUrl(prodUrl.getAttribute("href"));

				if (ids.contains(id)) {
					continue;
				}
				
				// Check if product exists already
				Product product = this.productDAO.getByProviderProdId(id, provider);
				if (product != null) {
					logger.debug("Product " + id + " already exists, skipping this one.");
					continue;
				}
				logger.info("Id found: \"" + id + "\"");

				// Check the price range
				WebElement priceElem = driver.findElementOrNull(By.xpath("//*[@id='list_content']/div[" + i + "]/ul/li[3]/span[1]"));
				if (!priceElem.getText().startsWith("US$ ")) {
					throw new RuntimeException("Wrong currency, bailing out: " + priceElem.getText());
				}
				BigDecimal totalSumInDollars = new BigDecimal(priceElem.getText().substring(4));
				logger.debug("priceElem " + priceElem.getText() + " gives " + totalSumInDollars.toPlainString());
				if (minPriceExclVat != null) {
					BigDecimal minPrice = currencyDAO.convertCurrency(minPriceExclVat.getCurrency(), Currency.DOLLAR, minPriceExclVat.getValue());
					if (totalSumInDollars.compareTo(minPrice) < 0) {
						logger.info("Price " + totalSumInDollars
								+ " is less than min price " + minPrice);
						continue;
					}
				}

				if (maxPriceExclVat != null) {
					BigDecimal maxPrice = currencyDAO.convertCurrency(maxPriceExclVat.getCurrency(), Currency.DOLLAR, maxPriceExclVat.getValue());
					if (totalSumInDollars.compareTo(maxPrice) > 0) {
						logger.info("Price " + totalSumInDollars
								+ " is higher than max price " + maxPrice);
						nbrTooExpensives++;
						if (nbrTooExpensives >= 100 && nbrAddedForCategory == 0) {
							logger.debug("This category has too many expensive items.");
							return false;
						}
						continue;
					}
				}

				// Check the rank range, which is not found for all products
				WebElement nbrReviewsElem = driver.findElementOrNull(By.xpath("//*[@id='list_content']/div[" + i + "]/ul/li[5]/span[2]/a"));
				if (nbrReviewsElem != null && !nbrReviewsElem.getText().startsWith("0 review")) {
					WebElement rankElem = driver.findElement(By.xpath("//*[@id='list_content']/div[" + i + "]/ul/li[5]/span[1]/span"));
					BigDecimal rank = new BigDecimal(rankElem.getText());
					double lowestRank = 3.5;
					if (rank.doubleValue() < lowestRank) {
						logger.info("Rank " + rank
								+ " is lower than min rank "
								+ lowestRank);
						continue;
					}
				}
				
				logger.info("Adding product " + id + " with price "
						+ totalSumInDollars + " to the id list");
				ids.add(id.trim());
				nbrAddedForCategory++;
			}
			// Go to next page
			if (((currPage + 1) == maxPages)) {
				logger.info("Reached max pages for category");
				break;
			}
			WebElement nextPageElem = driver.findElementOrNull(By.xpath("//*[@class='next']"));
			if (nextPageElem == null) {
				logger.info("No more pages for category.");
				break; //no more pages
			}
			logger.info("Go to the next page");
			nextPageElem.click();
		}
		return false;
	}

	private String getIdFromUrl(String url) {
		String base = "http://www.focalprice.com/";
		String id = url.substring(base.length(),
				url.indexOf('/', base.length() + 1));
		return id;
	}

	/*private int getMaxProductCountOfSameCategory(String pagesCountText,
			BigDecimal percentsOfProductsInSameCategory) {
		int productOnThePage = 48;
		String totalPagesString = pagesCountText.substring(
				pagesCountText.indexOf("/") + 1, pagesCountText.indexOf("|"));
		int totalPages = Integer.parseInt(totalPagesString.trim());
		int maxCount = (int) ((productOnThePage * totalPages) * percentsOfProductsInSameCategory
				.doubleValue());
		return maxCount;
	}*/

	@Override
	public void downloadImages(Product product, File basePath)
			throws Exception {
		List<File> imageFiles = new ArrayList<File>();
		String productImagePathStr = settingsDAO.getValue("fi.jonix.huutonet.tools.DxProductHelper.productImagePath");
		File upperProductImagePath = new File(basePath, productImagePathStr);
		String subDirPrefix = null;
		if (product.getId() < 1000) {
			subDirPrefix = "0000";
		} else {
			String idStr = product.getId().toString();
			subDirPrefix = idStr.substring(0,idStr.length()-3) + "000";
		}
		File productImagePath = new File(upperProductImagePath, subDirPrefix);
		try {
			/* First download the original large images. */
			//CustomSelenium selenium = seleniumRobot.getSelenium();
			CustomWebDriver driver = seleniumRobot.getWebDriver();
			//selenium.open("http://www.focalprice.com/detailpic_" + product.getProviderProdId() + ".html", 5);
			driver.get(product.getProviderUrl(),5);
			logger.info("Starting to find images");
			int i = 1;
			List<String> imageUrlList = new ArrayList<String>();
			List<WebElement> imgElemList = driver.findElements(By.xpath("//*[@id='imgs']/li/img"));
			for (WebElement imgElem : imgElemList) {
				//imageUrlList.add(imgElem.getAttribute("src"));
				imageUrlList.add(imgElem.getAttribute("jqimg"));
			}
			/* The clothes often have an extra image. */
			WebElement clothesExtraImage = driver.findElementOrNull(By.xpath("//table[@class='ke-zeroborder']/../../td[3]//img"));
			if (clothesExtraImage != null) {
				imageUrlList.add(clothesExtraImage.getAttribute("src"));
				logger.debug("Found extra image for clothes for product " + product.getId() + " (" + product.getProviderProdId() + ")");
			}
			
			for (String imageUrl : imageUrlList) {
				logger.info("Downloading image " + i + " (" + imageUrl + ")");
				//String fileName = product.getId() + "_" + i + ".jpg";
				String fileName = product.getId() + "_" + i + imageUrl.substring(imageUrl.lastIndexOf("."));
				File imageFile = new File(productImagePath, fileName);
				for (int j = 0; j < 3; j++) {
					try {
						FileUtils.copyURLToFile(new URL(imageUrl), imageFile);
						break;
					} catch (Exception e) {
						logger.info("Image could not be found at " + j + " try again");
					}
				}
				logger.info("Size of small image: " + imageFile.length());
				if (imageFile.length() < 500) {
					FileUtils.forceDelete(imageFile);
				} else {
					imageFiles.add(imageFile);
				}
				i++;
			}
			logger.info("Found " + imageFiles.size() + " images");

			// Add mask to imageFiles
			/*String maskPathStr = settingsDAO.getValue("fi.jonix.huutonet.order.focalprice.FocalPriceProvider.maskFile");
			File maskPath = new File(basePath, maskPathStr);
			logger.info("Mask file path: " + maskPath.getPath());
			logger.info("Mask file found: " + maskPath.exists());
			List<File> fileStore = imageFiles;
			for (int i = 0; i < 3; i++) {
				fileStore = this.picmarkrImageMasker.getProcessedImages(
						fileStore, maskPath, productImagePath, product, PicmarkrImageMasker.WatermarkPosition.CENTER);
			}*/

			for (File imageFile : imageFiles) { // imageFiles -> fileStore with watermark removal tool
				logger.info("Creating image");
				Image image = new Image();
				image.setImageOrder(imageFiles.indexOf(imageFile)); // imageFiles -> fileStore with watermark removal tool
				image.setLocalFileUrl(imageFile.getPath());
				image.setImageHostUrl("http://www.sellstar.fi/images/products/" + subDirPrefix + "/" + imageFile.getName());
				image.setProduct(product);
				logger.info("Saves image");
				imageDAO.save(image);
				imageDAO.flush(); // do a flush in order to catch database
				// errors right away
				logger.info("Image saved");
			}
		} finally {
			/* Remove temporary files. */
			// User only with watermark removal tool
			/*for (File file : imageFiles) {
				if (file.isFile() && file.exists()) {
					file.delete();
				}
			}*/
		}
	}

	@Override
	public void productExistsAtProvider(Product product)
			throws ProductNotFoundAtProviderException, ProviderTemporarilyDownException {
		if (productExistsAtProviderInternal(product) == null) {
			/* Sometimes (rarely) we get a false indication that the product is no longer for sale
			 * although it's actually still for sale. The problem might be due to temporary problems
			 * at the provider. In this case wait for some time and then check again. Sometimes
			 * we might actually get several false alarms in a row, so check several times
			 * to minimize the risk of faulty flagging a product as "no longer exists at provider".
			 * Note: even with these retries we sometimes get false positives. Therefore we have
			 * a counter in upper level code (ProviderHandler.updateProviderPrice) that doesn't
			 * set the product to exists = false. Only if this method SEVERAL times returns
			 * "product not found" the product is set to exists = false. Therefore it doesn't
			 * matter so much if this code now and then returns false positives. */
			int i = 0;
			int retryAttempts = 0; //TODO: Possibly restore this one to "2" => Perhaps not necessary after all since we now also have offline sales
			boolean productFound = false;
			while (i < retryAttempts && !productFound) {
				logger.debug("Got an indication that product " + product.getProviderProdId() + " is no longer for sale (i = " + i + ").");
				try { Thread.sleep(1000*60*1); } catch (Exception e2) { }
				productFound = (productExistsAtProviderInternal(product) != null);
				i++;
			}
			if (productFound) {
				logger.info("False alarm: product " + product.getProviderProdId() + " IS indeed still for sale (i = " + i + ").");
				//EmailSender.sendOperationProblemReport("False alarm",
					//	"False alarm: product " + providerProductId + " IS indeed still for sale (i = " + i + ").");
			} else {
				logger.debug("Also successive checks determine that product " + product.getProviderProdId()
						+ " is no longer for sale at provider (i = " + i + ").");
				throw new ProductNotFoundAtProviderException("Product not found at FocalPrice");
			}
		}
	}
	
	private String getPageSource(String url) throws ProviderTemporarilyDownException {
		String body = null;
		try {
			DefaultHttpClient client = new DefaultHttpClient();
			HttpGet get = new HttpGet(url);
			HttpResponse resp = client.execute(get);
			HttpEntity respEntity = resp.getEntity();
			body = EntityUtils.toString(respEntity);
		} catch (Exception e) {
			throw new ProviderTemporarilyDownException("FocalPrice temporarily down.");
		}
		return body;
	}
	
	private String getFieldValueFromHtml(String html, String type, String key) {
		int index = html.indexOf(type + "=\"" + key + "\"");
		if (index >= 0) {
			int index2 = html.substring(index).indexOf(">");
			String value = EmailFilter.getFieldValue(html, html.substring(index,index+index2),">");
			if (value != null) {
				if (value.indexOf("<") >= 0) {
					value = value.substring(0,value.indexOf("<"));
				}
				return value;
			}
		}
		return null;
	}
	
	/**
	 * @return Html of the product page if found or else null.
	 * @throws ProviderTemporarilyDownException
	 */
	private String productExistsAtProviderInternal(Product product)
			throws ProviderTemporarilyDownException {
		//TODO: ev. lägg till en till räknare: product.prov_down_counter och ifall denna är 100 så
		//ska man även testa trySecondCheck. Product http://www.focalprice.com/HP495W/something.html nimittäin
		//keeps failing with an error when trying to access the page.
		//Alt: kör _alltid_ trySecondCheck. => Jag fixade så att trySecondCheck körs även då man får error
		//"An unhandled exception was generated...", men en ev. generellare lösning skulle vara att fixa
		//en product.prov_down_counter-räknare.
		//Product www.focalprice.com/MA095B/something.html ALWAYS redirect to http://www.focalprice.com/, so
		//also for this product we would need a product.prov_down_counter

		String body = getPageSource(product.getProviderUrl());
		
		/* Unfortunately we cannot use java's xpath since the pages of FocalPrice
		 * aren't well-formed, so the following code is commented out. */
		/*XPathFactory xpathFactory = XPathFactory.newInstance();
		XPath xpath = xpathFactory.newXPath();
		ByteArrayInputStream inputStream = new ByteArrayInputStream(body.getBytes());
		InputSource source = new InputSource(inputStream);
		String value = xpath.evaluate("//*[@id='errer_body']/div/div",source);*/

		String skuVal = getFieldValueFromHtml(body,"id","sku");
		if (skuVal != null) {
			if (skuVal.equals(product.getProviderProdId())) {
				return body;
			} else {
				throw new RuntimeException("Url " + product.getProviderUrl()
						+ " seems to point to the wrong product (" + skuVal + ").");
			}
		}

		boolean trySecondCheck = false;
		if (body.indexOf("errer_body") >= 0 && body.indexOf("Error") >= 0 && body.indexOf("404 Not found") >= 0) {
			/* Ok, so the product MIGHT not exist anymore. Let's then do another check to make sure. */
			trySecondCheck = true;
		} else {
			/* In some cases we get a different kind of error page. Note: in some cases we get
			 * a stack trace error when searching for a product that's no longer for sale at FocalPrice,
			 * for example for "http://www.focalprice.com/HP495W/something.html". Therefore we try the
			 * second check also in this case ("An unhandled exception was generated..."). */
			if ((body.indexOf("The resource cannot be found") >= 0
					&& body.indexOf("Please review the following URL and make sure that it is spelled correctly") >= 0)
					|| body.indexOf("An unhandled exception was generated during the execution of the current web request") >= 0) {
				trySecondCheck = true;
			}
		}
		if (trySecondCheck) {
			/* Make a search at FocalPrice for the provider product id in question. */
			body = getPageSource("http://www.focalprice.com/search.html?keyword=" + product.getProviderProdId());
			if (body.indexOf("errer_body") < 0) {
				throw new ProviderTemporarilyDownException("FocalPrice temporarily down.");
			}
			if (body.indexOf("Error") >= 0 && body.indexOf("404 Not found") >= 0) {
				return null;
			}
		}
		throw new ProviderTemporarilyDownException("FocalPrice temporarily down.");
	}

	@Override
	public boolean canHandleProvider(Provider provider) {
		return provider.getName().equals(Provider.FOCAL_PRICE_PROVIDER_NAME);
	}

	@Override
	public AdTemplate createAutoTemplate(Product product) {
		//For FocalPrice we cannot automatically create ad templates because
		//the original language is English, so creation of new adtemplates
		//is done through the operator view of google-order.
		return null;
	}

}
