package fi.jonix.huutonet.order.hautala;

import java.io.File;
import java.math.BigDecimal;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.openqa.selenium.By;
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
import fi.jonix.huutonet.domain.model.Seller;
import fi.jonix.huutonet.domain.model.dao.AdTemplateDAO;
import fi.jonix.huutonet.domain.model.dao.CurrencyDAO;
import fi.jonix.huutonet.domain.model.dao.ImageDAO;
import fi.jonix.huutonet.domain.model.dao.OrderBundleDAO;
import fi.jonix.huutonet.domain.model.dao.ProductDAO;
import fi.jonix.huutonet.domain.model.dao.ProviderDAO;
import fi.jonix.huutonet.domain.model.dao.SellerDAO;
import fi.jonix.huutonet.domain.model.dao.SettingsDAO;
import fi.jonix.huutonet.email.EmailSender;
import fi.jonix.huutonet.email.processor.EmailFilter;
import fi.jonix.huutonet.exception.CriticalSectionInfo;
import fi.jonix.huutonet.exception.PayPalCaptchaException;
import fi.jonix.huutonet.exception.PlaceOrderException;
import fi.jonix.huutonet.exception.ProductNotFoundAtProviderException;
import fi.jonix.huutonet.exception.ProviderTemporarilyDownException;
import fi.jonix.huutonet.exception.SoldOutAtProviderException;
import fi.jonix.huutonet.order.Price;
import fi.jonix.huutonet.order.ProviderOrder;
import fi.jonix.huutonet.robot.CustomSelenium;
import fi.jonix.huutonet.robot.CustomWebDriver;
import fi.jonix.huutonet.robot.SeleniumRobot;
import fi.jonix.huutonet.tools.MathUtilities;
import fi.jonix.huutonet.tools.StringUtilities;

@Component(value = "hautalaProvider")
public class HautalaProvider extends ProviderOrder {

	public static final Logger logger = Logger.getLogger(HautalaProvider.class);

	private static String LOCATION = "Postin kautta kotiinkuljetettuna";
	private static String SLOGAN = "";
	private static String SHIPPING_DESCRIPTION = "Keskitetyn tilaus- ja toimitusprosessimme vuoksi, tilausten käsittely maksun saapumisesta postittamiseen on noin 5-8 arkipäivää, joten tuote tulee postitse noin 10:n arkipäivän kuluttua maksun saapumisesta.";
	private static String FINNISH_LANGUAGE = "fi_FI";
	
	@Autowired
	private SeleniumRobot seleniumRobot;
	
	@Autowired
	private ProviderDAO providerDAO;
	
	@Autowired
	private ProductDAO productDAO;
	
	@Autowired
	private SellerDAO sellerDAO;
	
	@Autowired
	private SettingsDAO settingsDAO;
	
	@Autowired
	private ImageDAO imageDAO;
	
	@Autowired
	private CurrencyDAO currencyDAO;
	
	@Autowired
	private OrderBundleDAO orderBundleDAO;
	
	@Autowired
	private AdTemplateDAO adTemplateDAO;
	
	@Override
	public boolean canHandleProvider(Provider provider) {
		return provider.getName().equals(Provider.HAUTALA_PROVIDER_NAME);
	}

	@Override
	public Price getPrice(Product product, boolean sloppyStockChecking)
			throws SoldOutAtProviderException, ProductNotFoundAtProviderException, ProviderTemporarilyDownException {
		logger.debug("Getting price for Hautala (product " + product.getId() + ").");
		if (!this.isProductInStock(product,sloppyStockChecking)) {
			throw new SoldOutAtProviderException("Product is out of stock");
		}
		Provider provider = providerDAO.getProviderByName(Provider.HAUTALA_PROVIDER_NAME);
		Currency expectedCurrency = provider.getCurrency();
		CustomWebDriver driver = seleniumRobot.getWebDriver();
		
		/* Find out the price incl. vat + make sure that the correct currency is used. */
		WebElement priceInclVatElement = driver.findElementOrNull(By.xpath("//div[@class='product-price']/strong"));
		Long usedCurrency = null;
		if (priceInclVatElement.getText().endsWith(" €")) {
			usedCurrency = Currency.EURO;
		}
		if (usedCurrency == null || !usedCurrency.equals(expectedCurrency.getId())) {
			logger.warn("Wrong currency in use for Hautala (price incl vat = '" + priceInclVatElement.getText()
					+ "', expected currency = " + expectedCurrency.getName() + ").");
			throw new RuntimeException("Wrong currency in use for Hautala (price incl vat = '" + priceInclVatElement.getText()
					+ "', expected currency = " + expectedCurrency.getName() + ").");
		}
		BigDecimal priceInclVat = new BigDecimal(priceInclVatElement.getText().replaceAll(" €","").replace(",","."));
		
		/* Find out the price excl. vat + make sure that the correct currency is used. */
		WebElement priceExclVatElement = driver.findElementOrNull(By.xpath("//div[@class='product-price-small']"));
		Long usedCurrencyExclVat = null;
		if (priceExclVatElement.getText().endsWith(" € alv. 0%")) {
			usedCurrencyExclVat = Currency.EURO;
		}
		if (usedCurrencyExclVat == null || !usedCurrencyExclVat.equals(expectedCurrency.getId())) {
			logger.warn("Wrong currency in use for Hautala (price excl vat = '" + priceExclVatElement.getText()
					+ "', expected currency = " + expectedCurrency.getName() + ").");
			throw new RuntimeException("Wrong currency in use for Hautala (price excl vat = '" + priceExclVatElement.getText()
					+ "', expected currency = " + expectedCurrency.getName() + ").");
		}
		BigDecimal priceExclVat = new BigDecimal(priceExclVatElement.getText().replaceAll(" € alv. 0%","").replace(",","."));
		
		/* Make sure that the correct vat percentage is used. */
		/*if (MathUtilities.getPriceExclVat(priceInclVat, provider.getVatPercentage()).compareTo(priceExclVat) != 0
				&& MathUtilities.getPriceInclVat(priceExclVat, provider.getVatPercentage()).compareTo(priceInclVat) != 0) {
			logger.warn("Wrong vat percentage for Hautala (product " + product.getId() + "): incl. vat according to web page: "
					+ priceInclVat.toPlainString() + " => with vat " + provider.getVatPercentage() + " the"
					+ " price excl. vat should be "
					+ MathUtilities.getPriceExclVat(priceInclVat, provider.getVatPercentage()).toPlainString()
					+ ", excl. vat according to web page: " + priceExclVat.toPlainString() + " => with vat "
					+ provider.getVatPercentage() + " the price incl. vat should be "
					+ MathUtilities.getPriceInclVat(priceExclVat, provider.getVatPercentage()));
			throw new RuntimeException("Vat percentage doesn't match for Hautala (for product " + product.getId() + ").");
		}*/
		
		/* Return the correct price. */
		VirtualCoin priceInclVatVC = new VirtualCoin(expectedCurrency,priceInclVat);
		VirtualCoin priceExclVatVC = new VirtualCoin(expectedCurrency,priceExclVat);
		return new Price(priceInclVatVC,priceExclVatVC,provider.getVatPercentage());
	}

	/*@Override
	public Price getPriceFromLoadedPage()
			throws ProviderTemporarilyDownException, SoldOutAtProviderException {
		throw new UnsupportedOperationException("This method isn't yet implemented");
	}*/

	@Override
	public void loadProductPage(String providerProductId) {
		throw new RuntimeException("Not yet implemented.");
	}

	@Override
	public void loadProductPage(String providerProductId, int retries,
			String timeout) {
		throw new RuntimeException("Not yet implemented.");
	}

	private Price getProviderShippingPriceInProviderCurrency(Product product) throws SoldOutAtProviderException, PlaceOrderException, ProductNotFoundAtProviderException, ProviderTemporarilyDownException {
		/* The only way we get the provider shipping price is by actually almost placing
		 * an order (the shipping price is shown toward the end of the order placing process)
		 * and then cancel the purchase. */
		logger.debug("Running getProviderShippingPriceInProviderCurrency");
		emptyShoppingCart();
		Price price = getPrice(product, true);
		addProductToShoppingCartFromProductPage(product);
		openShoppingCart();
		OrderBundle randomBundle = orderBundleDAO.get(1L);
		logger.debug("Loading random bundle.");
		enterShippingDetails(randomBundle);
		BigDecimal shippingPriceInclVatPC = getProviderShippingPrice();
		emptyShoppingCart();
		/*BigDecimal shippingPriceExclVatPC = MathUtilities.getPriceExclVat(shippingPriceInclVatPC, product.getProvider().getVatPercentage());
		VirtualCoin shippingPriceInclVatVC = new VirtualCoin(product.getProvider().getCurrency(),shippingPriceInclVatPC);
		VirtualCoin shippingPriceExclVatVC = new VirtualCoin(product.getProvider().getCurrency(),shippingPriceExclVatPC);*/
		return new Price(shippingPriceInclVatPC, true, product.getProvider().getCurrency(), product.getProvider().getVatPercentage());
	}
	
	private void emptyShoppingCart() {
		CustomWebDriver driver = seleniumRobot.getWebDriver();
		logger.debug("Emptying shopping cart.");
		driver.get("http://kauppa.hautalaservice.com/cart/cartINIT");
		try { Thread.sleep(1000*5); } catch (Exception e) { } //sleep to make it easier for operator to follow
	}
	
	private void openShoppingCart() {
		logger.debug("Opening shopping cart page.");
		CustomWebDriver driver = seleniumRobot.getWebDriver();
		driver.get("http://kauppa.hautalaservice.com/client/accept");
		try { Thread.sleep(1000*5); } catch (Exception e) { } //sleep to make it easier for operator to follow
	}
	
	/* This method has some problems, use addProductToShoppingCartFromProductPage instead (for example product
	 * "11-D-25-" cannot be added using this method). */
	/*private void addProductToShoppingCartFromShoppingCartPage(Product product) throws SoldOutAtProviderException, PlaceOrderException {
		CustomWebDriver driver = seleniumRobot.getWebDriver();
		WebElement providerProdIdElement = driver.findElement(By.id("ProductProductId"));
		driver.setValueWithJavascript(providerProdIdElement, product.getProviderProdId());
		WebElement addToCartElement = driver.findElement(By.xpath("//*[@id='container']//input[@type='submit' and contains(@value,'koriin')]"));
		addToCartElement.click();
		/* Check if the requested amount of products are in stock. /
		WebElement infoElement = driver.findElementOrNull(By.id("flashMessage"));
		if (infoElement != null && infoElement.getText().indexOf("uotetta ei ole varastossa") >= 0) {
			logger.info("Product " + product.getProviderProdId() + " not in stock: " + infoElement.getText());
			throw new SoldOutAtProviderException("Product " + product.getProviderProdId() + " not in stock.");
		} else if (infoElement == null || infoElement.getText().indexOf("ostoskoriin") < 0) {
			throw new PlaceOrderException("Product " + product.getProviderProdId() + " couldn't be ordered at Hautala.");
		}
	}*/

	/* Certain products cannot be added to the shopping cart through the shopping cart page. For example
	 * product "11-D-25-" can only be added through the "Add to cart" button at the product page. Therefore
	 * we use this method instead of addProductToShoppingCartFromShoppingCartPage. */
	private void addProductToShoppingCartFromProductPage(Product product) throws SoldOutAtProviderException, PlaceOrderException {
		logger.debug("Adding product " + product.getId() + " to shopping cart.");
		CustomWebDriver driver = seleniumRobot.getWebDriver();
		WebElement quantityElement = driver.findElement(By.id("ProductQuantity"));
		quantityElement.clear();
		quantityElement.sendKeys("1");
		WebElement addToCartElement = driver.findElement(By.xpath("//*[@id='container']//input[@type='submit' and contains(@value,'koriin')]"));
		addToCartElement.click();
		/* Check if the requested amount of products are in stock. */
		WebElement infoElement = driver.findElementOrNull(By.id("flashMessage"));
		if (infoElement != null && infoElement.getText().indexOf("uotetta ei ole varastossa") >= 0) {
			logger.info("Product " + product.getProviderProdId() + " not in stock: " + infoElement.getText());
			throw new SoldOutAtProviderException("Product " + product.getProviderProdId() + " not in stock.");
		} else if (infoElement == null || infoElement.getText().indexOf("ostoskoriin") < 0) {
			throw new PlaceOrderException("Product " + product.getProviderProdId() + " couldn't be ordered at Hautala.");
		}
	}

	private void enterShippingDetails(OrderBundle bundle) {
		logger.debug("Running enterShippingDetails for bundle " + bundle.getId() + " (last name = " + bundle.getLastName() + ").");
		
		/* Hautala got in touch with us on 4.6.2012 and asked why we had made many
		 * orders that had gone through half-way. The orders where placed in the
		 * name Etunimi Sukunimi, which corresponds to order_bundle.id = 1.
		 * This apparently comes from getProviderShippingPriceInProviderCurrency
		 * which uses "randomBundle = orderBundleDAO.get(1L);". Here make sure that
		 * we bail out if this comes again (just to make sure, since I couldn't
		 * figure out HOW this code has been ran lately). */
		if (bundle.getId().equals(1L) || bundle.getLastName().toUpperCase().indexOf("VARJON") > 0) {
			throw new RuntimeException("Bailing out since we shouldn't make these testing things.");
		}
		
		CustomWebDriver driver = seleniumRobot.getWebDriver();
		WebElement acceptElement = driver.findElement(By.xpath("//*[@id='cart-accept']/a"));
		logger.debug("Will press submit 1");
		acceptElement.click();
		try { Thread.sleep(1000*5); } catch (Exception e) { } //sleep to make it easier for operator to follow
		WebElement companyName = driver.findElement(By.id("OrderDeliveryCompany"));
		companyName.clear();
		String companyNameStr = settingsDAO.getValue("company.name");
		companyName.sendKeys(companyNameStr);
		WebElement organizationNumber = driver.findElement(By.id("OrderVATNumber"));
		organizationNumber.clear();
		String orgNumberStr = settingsDAO.getValue("company.organization.number");
		organizationNumber.sendKeys(orgNumberStr);
		WebElement firstName = driver.findElement(By.id("OrderDeliveryFirstName"));
		firstName.clear();
		//firstName.sendKeys(StringUtilities.toUSASCIICharacters(bundle.getFirstName()));
		firstName.sendKeys(bundle.getFirstName());
		WebElement lastName = driver.findElement(By.id("OrderDeliveryLastName"));
		lastName.clear();
		//lastName.sendKeys(StringUtilities.toUSASCIICharacters(bundle.getLastName()));
		lastName.sendKeys(bundle.getLastName());
		WebElement address = driver.findElement(By.id("OrderDeliveryAddress1"));
		address.clear();
		//address.sendKeys(StringUtilities.toUSASCIICharacters(bundle.getAddress()));
		address.sendKeys(bundle.getAddress());
		WebElement country = driver.findElement(By.id("OrderDeliveryCountry"));
		country.clear();
		country.sendKeys("Finland");
		WebElement city = driver.findElement(By.id("OrderDeliveryCity"));
		city.clear();
		//city.sendKeys(StringUtilities.toUSASCIICharacters(bundle.getCity()));
		city.sendKeys(bundle.getCity());
		WebElement postCode = driver.findElement(By.id("OrderDeliveryPostalCode"));
		postCode.clear();
		postCode.sendKeys(bundle.getPostcode());
		WebElement email = driver.findElement(By.id("OrderDeliveryEmail"));
		email.clear();
		email.sendKeys(bundle.getProvider().getSystemEmail().getEmailAddress());
		WebElement phone = driver.findElement(By.id("OrderDeliveryPhone"));
		phone.clear();
		phone.sendKeys("+35845123456"); //John's phone number
		WebElement message = driver.findElement(By.id("OrderMessageClient"));
		message.clear();
		message.sendKeys("Moi,\n\nTilauksen laskutusosoite on ERI kuin toimitusosoite eikä yrityksen nimi saisi lukea toimitusosoitteessa.\n\nLähettäkää lasku seuraavaan osoitteeseen:\nQD-Tech Oy\nJohn Bergbom\nJokukatu 55\n12345 Helsinki\n\nTerveisin,\nJohn Bergbom");
		try { Thread.sleep(1000*5); } catch (Exception e) { } //sleep to make it easier for operator to follow
		logger.debug("Will press submit 2");
		firstName.submit();
		try { Thread.sleep(1000*5); } catch (Exception e) { } //sleep to make it easier for operator to follow
	}

	private BigDecimal getProviderShippingPrice() throws PlaceOrderException {
		logger.debug("Getting provider shipping price.");
		CustomWebDriver driver = seleniumRobot.getWebDriver();
		WebElement shippingErrorText = driver.findElementOrNull(By.xpath("//*[@id='checkoutForm']/p"));
		if (shippingErrorText != null && shippingErrorText.getText().indexOf("Ei toimitustapoja saatavilla") >= 0) {
			throw new RuntimeException("Order too heavy or big. Not possible to ship.");
		}
		List<WebElement> deliveryWays = driver.findElements(By.xpath("//*[@id='checkoutForm']//input[@type='radio']"));
		logger.debug("Number of delivery ways: " + deliveryWays.size());
		for (WebElement deliveryWay : deliveryWays) {
			WebElement mailDeliveryText = driver.findElement(By.xpath("//*[@for='" + deliveryWay.getAttribute("id") + "']"));
			logger.debug("Delivery way for " + deliveryWay.getAttribute("id") + ": " + mailDeliveryText.getText());
			BigDecimal weight = null;
			BigDecimal shippingPriceInclVatPC = null;
			if (mailDeliveryText.getText().startsWith("Nouto")) {
				logger.debug("Skipping shipping way " + mailDeliveryText.getText());
				continue;
			} else if (!mailDeliveryText.getText().startsWith("Rahtimaksu")) {
				throw new RuntimeException("Cannot determine shipping cost for Hautala (placeOrders). Has the web page changed?");
			} else if (mailDeliveryText.getText().equals("Rahtimaksu 0-5kg 9.00 €")) {
				weight = BigDecimal.ZERO;
				shippingPriceInclVatPC = new BigDecimal("9.00");
			} else if (mailDeliveryText.getText().equals("Rahtimaksu 5 -10kg 15.00 €")) {
				weight = new BigDecimal("5.00");
				shippingPriceInclVatPC = new BigDecimal("15.00");
			} else if (mailDeliveryText.getText().equals("Rahtimaksu 10kg - 20.00 €")) {
				weight = new BigDecimal("10.00");
				shippingPriceInclVatPC = new BigDecimal("20.00");
			} else if (mailDeliveryText.getText().indexOf("Rahtimaksu 2kg, erikoispitk") >= 0
					&& mailDeliveryText.getText().indexOf("t tuotteet 17.00 €") >= 0) {
				weight = new BigDecimal("2.00");
				shippingPriceInclVatPC = new BigDecimal("17.00");
			} else {
				logger.info("Cannot interpret shipping information " + mailDeliveryText.getText());
				throw new PlaceOrderException("Cannot interpret shipping information " + mailDeliveryText.getText());
			}
			logger.debug("Shipping weight: " + weight.toPlainString() + ", shipping cost = " + shippingPriceInclVatPC.toPlainString());
			//WebElement mailDelivery = driver.findElement(By.id(mailDeliveryText.getAttribute("id")));
			deliveryWay.click();
			try { Thread.sleep(1000*5); } catch (Exception e) { } //sleep to make it easier for operator to follow
			logger.debug("Will press submit 3");
			deliveryWay.submit();
			return shippingPriceInclVatPC;
		}
		throw new PlaceOrderException("Cannot get shipping information.");
	}

	@Override
	public void placeOrder(OrderBundle bundle, List<Order> bundleOrders, String user, String password, CriticalSectionInfo section) throws PlaceOrderException,
			ProviderTemporarilyDownException, SoldOutAtProviderException,
			ProductNotFoundAtProviderException {
		logger.info("Placing bundle order for bundle.id " + bundle.getId() + " to Hautala.");
		CustomWebDriver driver = seleniumRobot.getWebDriver();
		boolean resending = bundleOrders.get(0).getState().equals(Order.STATE_SHOULD_RESEND);
		Provider provider = providerDAO.getProviderByName(Provider.HAUTALA_PROVIDER_NAME);
		Currency usedCurrency = provider.getCurrency();
		BigDecimal finalPriceWithShippingInclVatPC;
		
		try {
			/* Start by emptying the shopping cart. */
			emptyShoppingCart();

			/* Load the web pages of the bundle and get the price. The reason for this is that the
			 * price checker also makes sure that the url of the product still points to the correct
			 * products. */
			//Map<Long,Price> prices = new HashMap<Long,Price>();
			BigDecimal totalPriceInclVat = BigDecimal.ZERO;
			BigDecimal totalPriceExclVat = BigDecimal.ZERO;
			for (Order order : bundleOrders) {
				Price price = getPrice(order.getProduct(), true);
				//prices.put(order.getId(),price);
				totalPriceInclVat = totalPriceInclVat.add(price.getPriceInclVat().getValue());
				totalPriceExclVat = totalPriceExclVat.add(price.getPriceExclVat().getValue());
				logger.debug("Price for order " + order.getId() + ": " + price.getPriceInclVat().getValue()
						+ " (" + price.getPriceExclVat().getValue() + " excl. vat).");
				//WebElement addToCartElement = driver.findElement(By.xpath("//*[@id='product-detail']//input[@type='submit']"));
				//addToCartElement.click();
				VirtualCoin unitPriceInclVatPCVC = new VirtualCoin(usedCurrency,price.getPriceInclVat().getValue());
				setFinalProviderPriceAtOrderingFromProvider(order,unitPriceInclVatPCVC);
				addProductToShoppingCartFromProductPage(order.getProduct());
			}
			logger.debug("Total price for bundle " + bundle.getId() + " (excl. shipping): " + totalPriceInclVat.toPlainString()
					+ " (" + totalPriceExclVat.toPlainString() + " excl. vat).");
	
			/* Add the new products to the shopping cart. */
			/*openAddToCart();
			for (Order order : bundleOrders) {
				addProductToShoppingCartFromShoppingCartPage(order.getProduct());
			}
			logger.debug("Done adding products to cart");
			try { Thread.sleep(1000*5); } catch (Exception e) { } //sleep to make it easier for operator to follow
			*/
	
			/* Make sure that the total price is equal to the price of the sum of the individual products. */
			openShoppingCart();
			WebElement totalPriceInclVatElement = driver.findElement(By.id("cart-total-price"));
			WebElement totalPriceExclVatElement = driver.findElement(By.id("cart-total-price-notax"));
			if (!totalPriceInclVatElement.getText().endsWith(" €") || !totalPriceExclVatElement.getText().endsWith(" € alv. 0%")) {
				throw new RuntimeException("Unexpected currency in placeOrders.");
			}
			BigDecimal totInclWeb = new BigDecimal(totalPriceInclVatElement.getText().replaceAll(" €",""));
			BigDecimal totExclWeb = new BigDecimal(totalPriceExclVatElement.getText().replaceAll(" € alv. 0%",""));
			if (totalPriceInclVat.compareTo(totInclWeb) != 0 || totalPriceExclVat.compareTo(totExclWeb) != 0) {
				throw new RuntimeException("Faulty total price: incl vat = " + totalPriceInclVat.toPlainString()
						+ ", excl vat = " + totalPriceExclVat.toPlainString()
						+ " vs. " + totInclWeb.toPlainString() + "/" + totExclWeb.toPlainString());
			}
	
			/* Accept the order and enter the shipping details. */
			enterShippingDetails(bundle);
			
			/* Get the provider shipping price + choose shipping way. */
			BigDecimal shippingPriceInclVatPC = getProviderShippingPrice();
			try { Thread.sleep(1000*5); } catch (Exception e) { } //sleep to make it easier for operator to follow
			
			/* Make sure that the final price is correct. */
			WebElement finalPriceWithShippingInclVatElem = driver.findElement(By.id("cart_total_price"));
			logger.debug("finalPriceWithShippingInclVatElem: " + finalPriceWithShippingInclVatElem.getText());
			finalPriceWithShippingInclVatPC = new BigDecimal(finalPriceWithShippingInclVatElem.getText().replace("€", "").replace(",","."));
			if (finalPriceWithShippingInclVatPC.compareTo(totalPriceInclVat.add(shippingPriceInclVatPC)) != 0) {
				logger.info("Final price (" + finalPriceWithShippingInclVatPC.toPlainString() + ") differs from"
						+ " the sum of individual products (" + totalPriceInclVat.toPlainString()
						 + ") plus the shipping cost (" + shippingPriceInclVatPC.toPlainString());
				throw new PlaceOrderException("Wrong final price for bundle " + bundle.getId());
			}
			logger.debug("Final price incl vat + shipping: " + finalPriceWithShippingInclVatPC.toPlainString());

			BigDecimal finalPriceWithShippingExclVatPC = MathUtilities.getPriceExclVat(finalPriceWithShippingInclVatPC, bundle.getProvider().getVatPercentage());
			BigDecimal finalPriceWithShippingExclVatEuro = currencyDAO.convertCurrency(usedCurrency.getId(), Currency.EURO, finalPriceWithShippingExclVatPC);
			checkTheTotalSum(finalPriceWithShippingExclVatEuro, bundle, bundleOrders);

			/* Set the shipping cost of the bundle. */
			VirtualCoin shippingCostInclVatPC = new VirtualCoin(usedCurrency,shippingPriceInclVatPC);
			setShippingCostAtOrderingFromProvider(bundle,shippingCostInclVatPC,resending);
			
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

			logger.info("First part of ordering bundle " + bundle.getId() + " went through successfully.");
			try { Thread.sleep(1000*5); } catch (Exception e) { } //sleep to make it easier for operator to follow
		} catch (WebDriverException e) {
			logger.debug("Ordering problem for bundle " + bundle.getId() + " (doesn't require any special action): ",e);
			throw new ProviderTemporarilyDownException("FocalPrice temporarily down.");
		} catch (PlaceOrderException e) {
			logger.debug("Ordering problem for bundle " + bundle.getId() + " (doesn't require any special action): ",e);
			throw e;
		} catch (ProviderTemporarilyDownException e) {
			logger.debug("Ordering problem for bundle " + bundle.getId() + " (doesn't require any special action): ",e);
			throw e;
		} catch (SoldOutAtProviderException e) {
			logger.debug("Ordering problem for bundle " + bundle.getId() + " (doesn't require any special action): ",e);
			throw e;
		} catch (ProductNotFoundAtProviderException e) {
			logger.debug("Ordering problem for bundle " + bundle.getId() + " (doesn't require any special action): ",e);
			throw e;
		} catch (Exception e) {
			logger.debug("Ordering problem for bundle " + bundle.getId() + " (doesn't require any special action): ",e);
			throw new PlaceOrderException("Some error ocurred at place order.");
		}

		/* Now make the final accept. */
		int newState = Order.STATE_ORDERED_FROM_PROVIDER;
		try {
			WebElement orderButton = driver.findElement(By.xpath("//*[@id='bill_order']/a"));
			logger.debug("Will press submit 4 (final)");
			orderButton.click();
		} catch (Exception e) {
			logger.error("Ordering of bundle " + bundle.getId() + " at Hautala failed. Selenium failed on the final \"accept\".", e);
			newState = Order.STATE_OTHER_ERROR;
			String email = "Problem placing order(s) at provider Hautala for bundle " + bundle.getId() + ". Selenium failed"
				+ " on the final \"accept\". Unsure if the order went through or not. Setting the order(s) to state"
				+ " otherError.\n\nError message: "+ e.getMessage() + "\n";
			EmailSender.sendOperationProblemReport("Ordering problem",email,bundle.getProvider().getSystemEmail());
		}
		
		/* Now extract the order number. */
		logger.debug("Extracting provider order id");
		String providerOrderId = null;
		try {
			try {
				WebElement someElement = driver.findElementOrNull(By.xpath("//*[@id='container']/div[2]"));
				logger.debug("All order information = " + someElement.getText());
			} catch (Exception e) {
				logger.error("Couldn't print out all order information:",e);
			}
			WebElement providerOrderIdElement = driver.findElement(By.xpath("//*[@id='container']/div[2]/p[4]"));
			logger.debug("providerOrderIdElement = " + providerOrderIdElement.getText());
			if (providerOrderIdElement.getText().indexOf("Tilausnumero: ") >= 0) {
				providerOrderId = providerOrderIdElement.getText().replaceAll("Tilausnumero: ", "");
				logger.info("providerOrderId = " + providerOrderId);
				//bundle.setPayPalId(providerOrderId); //PayPal isn't used for Hautala
				bundle.setProviderOrderId(providerOrderId);
			} else {
				logger.error("Couldn't get provider order id for bundle " + bundle.getId());
				int i = 2;
				WebElement someElement = driver.findElementOrNull(By.xpath("//*[@id='container']/div[2]/p[" + i + "]"));
				while (someElement != null) {
					logger.debug("someElement (i = " + i + ") = " + someElement.getText());
					i++;
					someElement = driver.findElementOrNull(By.xpath("//*[@id='container']/div[2]/p[" + i + "]"));
				}
				throw new Exception("Couldn't get provider order id.");
			}
		} catch (Exception e) {
			logger.error("Ordering of bundle " + bundle.getId() + " at Hautala failed. Provider order id could not be fetched: ",e);
			try {
				logger.debug("html source: " + driver.getPageSource());
			} catch (Exception e2) {
				logger.error("Couldn't even print out the html source.",e2);
			}
			logger.error("Ordering of bundle " + bundle.getId() + " at Hautala failed. Selenium failed on the final \"accept\".", e);
			newState = Order.STATE_OTHER_ERROR;
			String email = "Problem placing order(s) at provider Hautala for bundle " + bundle.getId() + ". Selenium failed"
				+ " on the final \"accept\". Provider order id could not be fetched. Setting the order(s) to state"
				+ " otherError.\n\nError message: "+ e.getMessage() + "\n";
			EmailSender.sendOperationProblemReport("Ordering problem",email,bundle.getProvider().getSystemEmail());
		}
		
		for (Order order : bundleOrders) {
			order.setStateWithHistory(newState);
		}
		
		logger.info("Bundle " + bundle.getId() + " ordered.");
	}

	@Override
	public boolean cancelOrder(Order order) throws Exception {
		throw new RuntimeException("Not yet implemented.");
	}

	@Override
	public void updateOrderStatus(OrderBundle bundle, List<Order> bundleOrders, CriticalSectionInfo section)
			throws Exception {
		//TODO: här måste man nog göra så att ifall någon order är i tila STATE_STOCK_SHORTAGE_BEFORE_ORDER,
		//niin pitää lähettää operaattorille ilmoitus, että "potki hautalaa, että ne tilaa varastoon tuote xxx".
		//TODO: eventuellt får man inte från hautala någon information om att produkten är skickad, så vi borde
		//då eventuellt här uppdatera status från STATE_ORDERED_FROM_PROVIDER/STATE_ORDER_CONFIRMED_BY_PROVIDER till
		//STATE_SHIPPED_BY_PROVIDER efter tex. en vecka.
		logger.debug("updateOrderStatus not yet implemented for Hautala");
	}

	@Override
	public void updateTechnicalSpecs(Product product) throws Exception {
		logger.debug("Running updateTechnicalSpecs");
		/*Price shippingPrice = getProviderShippingPriceInProviderCurrency(product);
		product.setMinProviderShippingCostProviderCurrencyIncVat(shippingPrice.getPriceInclVat().getValue());
		logger.debug("Provider shipping price for product " + product.getProviderProdId() + " ( "
				+ product.getProviderUrl() + " ) is " + shippingPrice.getPriceInclVat().getValue().toPlainString());*/

		/* Open the product page. */
		try {
			getPrice(product, true);
		} catch (SoldOutAtProviderException e) {
			//doesn't matter if the product is out of stock
		}
		
		/* Extract the technical specs. */
		CustomWebDriver driver = seleniumRobot.getWebDriver();
		String specs = ""; //    //p[@class='product-number']
		WebElement productDescriptionElement = driver.findElementOrNull(By.xpath("//div[@id='product-detail']/div[2]/p[1]"));
		if (productDescriptionElement.getAttribute("class").equals("product-number")) {
			logger.warn("No technical specs found for " + product.getProviderUrl());
			//throw new Exception("Has the product page of Hautala changed?");
		} else {
			specs = extractTechnicalSpecs(productDescriptionElement, product.getProviderUrl());
		}
		String dimensions = extractDimensions(specs, product.getProviderUrl());
		logger.info("Old technical specs for product " + product.getProviderUrl() + " : " + product.getTechnicalSpecs());
		logger.info("New technical specs for product " + product.getProviderUrl() + " : " + specs);
		logger.info("Old dimensions for product " + product.getProviderUrl() + " : " + product.getDimensions());
		logger.info("New dimensions for product " + product.getProviderUrl() + " : " + dimensions);
		product.setTechnicalSpecs(specs);
		product.setDimensions(dimensions);
		
		/* For hautala the language is Finnish, so we can update the adtemplates as well. */
		List<AdTemplate> adTemplateList = adTemplateDAO.findByProduct(product);
		for (AdTemplate adTemplate : adTemplateList) {
			adTemplate.setTechnicalSpecs(specs);
		}
	}

	public void getManufacturerProductUrlsForWolfGarten() {
		/* All products don't seem to exist on the English pages, so we fetch
		 * product information also from the german pages. */
		logger.info("Fetching manufacturer urls from the english pages.");
		getManufacturerProductUrlsForWolfGarten("http://www.wolf-garten.org/"); //english
		logger.info("Fetching manufacturer urls from the german pages.");
		getManufacturerProductUrlsForWolfGarten("http://www.wolf-garten.de/produkte/"); //german
	}
	
	private void getManufacturerProductUrlsForWolfGarten(String siteUrl) {
		logger.debug("Starting getting manufacturer url's for products from Wolf-garten.");
		Provider provider = providerDAO.getProviderByName(Provider.HAUTALA_PROVIDER_NAME);
		CustomWebDriver driver = seleniumRobot.getWebDriver();
		driver.get(siteUrl);
		int count = 0;
		int mainCategoryItem = 1;
		//Here we have three levels: Lawn, Soil, Trees and Shrubs
		WebElement mainCategoryElement = driver.findElementOrNull(By.xpath(".//*[@id='SUBMENU']/ul/li[" + mainCategoryItem + "]/a"));
		while (mainCategoryElement != null) {
			mainCategoryElement.click();
			try { Thread.sleep(1000*2); } catch (Exception e) { }
			int subCategoryItem = 1;
			//Here we have 11 levels for Lawn, 9 levels for Soil, 7 levels for Trees and Shrubs
			WebElement subCategoryElement = driver.findElementOrNull(By.xpath(".//*[@id='SUBMENU']/ul/li[" + mainCategoryItem + "]/ul/li[" + subCategoryItem + "]/a"));
			while (subCategoryElement != null) {
				subCategoryElement.click();
				try { Thread.sleep(1000*2); } catch (Exception e) { }

				/* Now we are at the page where several products are listed. Open them all in turn. */
				int productItem = 1;
				WebElement productElement = driver.findElementOrNull(By.xpath(".//*[@id='PRODUCTLIST']/form/dl[" + productItem + "]/dt/a"));
				if (productElement != null) {
					while (productElement != null) {
						productElement.click();
						try { Thread.sleep(1000*2); } catch (Exception e) { }
						count++;
						logger.debug("Starting on mainCategoryItem = " + mainCategoryItem + ", subCategoryItem = " + subCategoryItem
								+ ", productItem = " + productItem);
						extractManufacturerUrl(provider);
						driver.navigate().back();
						try { Thread.sleep(1000*2); } catch (Exception e) { }
						productItem++;
						productElement = driver.findElementOrNull(By.xpath(".//*[@id='PRODUCTLIST']/form/dl[" + productItem + "]/dt/a"));
					}
				} else {
					/* In this case there is yet another sub-level. */
					int subSubCategoryItem = 1;
					WebElement subSubCategoryElement = driver.findElementOrNull(By.xpath(".//*[@id='SUBMENU']/ul/li[" + mainCategoryItem + "]/ul/li[" + subCategoryItem + "]/ul/li[" + subSubCategoryItem + "]/a"));
					while (subSubCategoryElement != null) {
						subSubCategoryElement.click();
						try { Thread.sleep(1000*2); } catch (Exception e) { }

						productItem = 1;
						productElement = driver.findElementOrNull(By.xpath(".//*[@id='PRODUCTLIST']/form/dl[" + productItem + "]/dt/a"));
						while (productElement != null) {
							productElement.click();
							try { Thread.sleep(1000*2); } catch (Exception e) { }
							count++;
							logger.debug("Starting on mainCategoryItem = " + mainCategoryItem + ", subCategoryItem = " + subCategoryItem
									+ ", subSubCategoryItem = " + subSubCategoryItem + ", productItem = " + productItem);
							extractManufacturerUrl(provider);
							driver.navigate().back();
							try { Thread.sleep(1000*2); } catch (Exception e) { }
							productItem++;
							productElement = driver.findElementOrNull(By.xpath(".//*[@id='PRODUCTLIST']/form/dl[" + productItem + "]/dt/a"));
						}
						
						driver.navigate().back();
						try { Thread.sleep(1000*2); } catch (Exception e) { }
						subSubCategoryItem++;
						subSubCategoryElement = driver.findElementOrNull(By.xpath(".//*[@id='SUBMENU']/ul/li[" + mainCategoryItem + "]/ul/li[" + subCategoryItem + "]/ul/li[" + subSubCategoryItem + "]/a"));
					}
				}

				driver.navigate().back();
				try { Thread.sleep(1000*2); } catch (Exception e) { }
				subCategoryItem++;
				subCategoryElement = driver.findElementOrNull(By.xpath(".//*[@id='SUBMENU']/ul/li[" + mainCategoryItem + "]/ul/li[" + subCategoryItem + "]/a"));
			}
			driver.navigate().back();
			try { Thread.sleep(1000*2); } catch (Exception e) { }
			mainCategoryItem++;
			mainCategoryElement = driver.findElementOrNull(By.xpath(".//*[@id='SUBMENU']/ul/li[" + mainCategoryItem + "]/a"));
		}
		logger.debug("Done getting manufacturer url's for Wolf-garten (gone through " + count + " products at Wolf-garten).");
	}
	
	private void extractManufacturerUrl(Provider provider) {
		CustomWebDriver driver = seleniumRobot.getWebDriver();
		WebElement providerProdIdHeadlineElement = driver.findElementOrNull(By.xpath(".//*[@id='PRODUCTDETAILLEFT']/div[2]//tr[1]/td[1]"));
		if (providerProdIdHeadlineElement == null) {
			logger.debug("Couldn't find headline element for page " + driver.getCurrentUrl());
		} else if (providerProdIdHeadlineElement.getText().toLowerCase().indexOf("artikelnummer") < 0 //german
				&& providerProdIdHeadlineElement.getText().toLowerCase().indexOf("rticle") < 0) { //english
			logger.debug("Headline element was \"" + providerProdIdHeadlineElement.getText() + "\" for page " + driver.getCurrentUrl());
		} else {
			/* Ok, seems to be a correct page. */
			WebElement providerProdIdElement = driver.findElementOrNull(By.xpath(".//*[@id='PRODUCTDETAILLEFT']/div[2]//tr[1]/td[2]"));
			String providerProdId = providerProdIdElement.getText();
			Product product = productDAO.getByProviderProdId(providerProdId, provider);
			if (product == null) {
				logger.debug("Product " + providerProdId + " wasn't found in SellStar's database. Skipping.");
			} else {
				logger.debug("extractManufacturerUrl: UPDATE product set manufacturer_url = '" + driver.getCurrentUrl()
						+ "' WHERE id = " + product.getId() + ";");
			}
		}
	}

	public void getBiggerImagesFromWolfGarten() {
		logger.debug("Starting fetching images from Wolf-garten.");
		Provider provider = providerDAO.getProviderByName(Provider.HAUTALA_PROVIDER_NAME);
		CustomWebDriver driver = seleniumRobot.getWebDriver();
		driver.get("http://www.wolf-garten.de/produkte/");
		int count = 0;
		int mainCategoryItem = 1;
		//Here we have four levels: Rasen, Boden, Geholze, Winter
		WebElement mainCategoryElement = driver.findElementOrNull(By.xpath(".//*[@id='SUBMENU']/ul/li[" + mainCategoryItem + "]/a"));
		while (mainCategoryElement != null) {
			mainCategoryElement.click();
			try { Thread.sleep(1000*2); } catch (Exception e) { }
			int subCategoryItem = 1;
			//Here we have 11 levels for Rasen, 7 levels for Boden, 7 levels for Geholze and 2 levels for Winter
			WebElement subCategoryElement = driver.findElementOrNull(By.xpath(".//*[@id='SUBMENU']/ul/li[" + mainCategoryItem + "]/ul/li[" + subCategoryItem + "]/a"));
			while (subCategoryElement != null) {
				subCategoryElement.click();
				try { Thread.sleep(1000*2); } catch (Exception e) { }

				/* Now we are at the page where several products are listed. Open them all in turn. */
				int productItem = 1;
				WebElement productElement = driver.findElementOrNull(By.xpath(".//*[@id='PRODUCTLIST']/form/dl[" + productItem + "]/dt/a"));
				if (productElement != null) {
					while (productElement != null) {
						productElement.click();
						try { Thread.sleep(1000*2); } catch (Exception e) { }
						count++;
						logger.debug("Starting on mainCategoryItem = " + mainCategoryItem + ", subCategoryItem = " + subCategoryItem
								+ ", productItem = " + productItem);
						fetchBigImage(provider);
						driver.navigate().back();
						try { Thread.sleep(1000*2); } catch (Exception e) { }
						productItem++;
						productElement = driver.findElementOrNull(By.xpath(".//*[@id='PRODUCTLIST']/form/dl[" + productItem + "]/dt/a"));
					}
				} else {
					/* In this case there is yet another sub-level. */
					int subSubCategoryItem = 1;
					WebElement subSubCategoryElement = driver.findElementOrNull(By.xpath(".//*[@id='SUBMENU']/ul/li[" + mainCategoryItem + "]/ul/li[" + subCategoryItem + "]/ul/li[" + subSubCategoryItem + "]/a"));
					while (subSubCategoryElement != null) {
						subSubCategoryElement.click();
						try { Thread.sleep(1000*2); } catch (Exception e) { }

						productItem = 1;
						productElement = driver.findElementOrNull(By.xpath(".//*[@id='PRODUCTLIST']/form/dl[" + productItem + "]/dt/a"));
						while (productElement != null) {
							productElement.click();
							try { Thread.sleep(1000*2); } catch (Exception e) { }
							count++;
							logger.debug("Starting on mainCategoryItem = " + mainCategoryItem + ", subCategoryItem = " + subCategoryItem
									+ ", subSubCategoryItem = " + subSubCategoryItem + ", productItem = " + productItem);
							fetchBigImage(provider);
							driver.navigate().back();
							try { Thread.sleep(1000*2); } catch (Exception e) { }
							productItem++;
							productElement = driver.findElementOrNull(By.xpath(".//*[@id='PRODUCTLIST']/form/dl[" + productItem + "]/dt/a"));
						}
						
						driver.navigate().back();
						try { Thread.sleep(1000*2); } catch (Exception e) { }
						subSubCategoryItem++;
						subSubCategoryElement = driver.findElementOrNull(By.xpath(".//*[@id='SUBMENU']/ul/li[" + mainCategoryItem + "]/ul/li[" + subCategoryItem + "]/ul/li[" + subSubCategoryItem + "]/a"));
					}
				}
				
				driver.navigate().back();
				try { Thread.sleep(1000*2); } catch (Exception e) { }
				subCategoryItem++;
				subCategoryElement = driver.findElementOrNull(By.xpath(".//*[@id='SUBMENU']/ul/li[" + mainCategoryItem + "]/ul/li[" + subCategoryItem + "]/a"));
			}
			driver.navigate().back();
			try { Thread.sleep(1000*2); } catch (Exception e) { }
			mainCategoryItem++;
			mainCategoryElement = driver.findElementOrNull(By.xpath(".//*[@id='SUBMENU']/ul/li[" + mainCategoryItem + "]/a"));
		}
		logger.debug("Done fetching images from Wolf-garten (gone through " + count + " products at Wolf-garten).");
	}
	
	private void fetchBigImage(Provider provider) {
		CustomWebDriver driver = seleniumRobot.getWebDriver();
		WebElement providerProdIdHeadlineElement = driver.findElementOrNull(By.xpath(".//*[@id='PRODUCTDETAILLEFT']/div[2]//tr[1]/td[1]"));
		if (providerProdIdHeadlineElement == null) {
			logger.debug("Couldn't find headline element for page " + driver.getCurrentUrl());
		} else if (providerProdIdHeadlineElement.getText().toLowerCase().indexOf("artikelnummer") < 0) {
			logger.debug("Headline element was \"" + providerProdIdHeadlineElement.getText() + "\" for page " + driver.getCurrentUrl());
		} else {
			/* Ok, seems to be a correct page. */
			WebElement providerProdIdElement = driver.findElementOrNull(By.xpath(".//*[@id='PRODUCTDETAILLEFT']/div[2]//tr[1]/td[2]"));
			String providerProdId = providerProdIdElement.getText();
			Product product = productDAO.getByProviderProdId(providerProdId, provider);
			if (product == null) {
				logger.debug("Product " + providerProdId + " wasn't found in SellStar's database. Skipping.");
			} else {
				WebElement imageLink = driver.findElementOrNull(By.xpath(".//*[@id='PRODUCTIMAGE']/a"));
				if (imageLink == null) {
					logger.debug("Image link not found for product \"" + providerProdId + "\"");
				} else {
					File directory = new File("/tmp/images");
					File imageFile = null;
					try {
						imageFile = downloadImage(imageLink.getAttribute("href"),product,1,directory);
					} catch (Exception e) {
						logger.debug("Image for product \"" + providerProdId + "\" couldn't be downloaded: ", e);
					}
					if (imageFile == null) {
						logger.debug("Image for product \"" + providerProdId + "\" couldn't be downloaded.");
					} else {
						logger.debug("Bigger image fetched for product \"" + providerProdId + "\"");
					}
				}
			}
		}
	}

	/* Extract the dimensions from the product description. */
	private String extractDimensions(String techSpecs, String providerProductUrl) {
		StringBuffer dimensions = new StringBuffer();
		String weight1 = extractMeasure(techSpecs,"Tyhjäpaino","Tyhjäpaino",dimensions,providerProductUrl,"kg","g");
		String weight2 = extractMeasure(techSpecs,"Paino täytettynä","Paino täytettynä",dimensions,providerProductUrl,"kg","g");
		String weight3 = extractMeasure(techSpecs,"Paino yhteensä","Paino yhteensä",dimensions,providerProductUrl,"kg","g");
		String weight4 = extractMeasure(techSpecs,"Paino vain","Paino vain",dimensions,providerProductUrl,"kg","g");
		if (weight1 == null && weight2 == null && weight3 == null && weight4 == null) {
			extractMeasure(techSpecs,"Paino","Paino",dimensions,providerProductUrl,"kg","g");
		}
		String length = extractMeasure(techSpecs,"Terän pituus","Terän pituus",dimensions,providerProductUrl,"cm","mm","m");
		if (length == null) {
			String length2 = extractMeasure(techSpecs,"Pituus","Pituus",dimensions,providerProductUrl,"cm","mm","m");
			if (length2 == null) {
				extractMeasure(techSpecs,"Pit","Pituus",dimensions,providerProductUrl,"cm","mm","m");
			}
		}
		String width1 = extractMeasure(techSpecs,"Leikkuuleveys","Leikkuuleveys",dimensions,providerProductUrl,"cm","mm","m");
		String width2 = extractMeasure(techSpecs,"Leveys","Leveys",dimensions,providerProductUrl,"cm","mm","m");
		if (width1 == null && width2 == null) {
			extractMeasure(techSpecs,"Työleveys","Työleveys",dimensions,providerProductUrl,"cm","mm","m");
		}
		extractMeasure(techSpecs,"Korkeus","Korkeus",dimensions,providerProductUrl,"cm","mm","m");
		extractMeasure(techSpecs,"Syvyys","Syvyys",dimensions,providerProductUrl,"cm","mm","m");
		String diameter = extractMeasure(techSpecs,"Halkaisija","Halkaisija",dimensions,providerProductUrl,"cm","mm","m");
		if (diameter == null) {
			extractMeasure(techSpecs,"halk","Halkaisija",dimensions,providerProductUrl,"cm","mm","m");
		}
		extractMeasure(techSpecs,"Latausaika","Latausaika",dimensions,providerProductUrl,"minuuttia","min","h");
		extractMeasure(techSpecs,"Käyttöaika yli","Käyttöaika yli",dimensions,providerProductUrl,"minuuttia","min","h");
		extractMeasure(techSpecs,"Käyttöaika","Käyttöaika",dimensions,providerProductUrl,"minuuttia","min","h");
		extractMeasure(techSpecs,"Teräleveys","Teräleveys",dimensions,providerProductUrl,"cm","mm","m");
		extractMeasure(techSpecs,"Varsi","Varsi",dimensions,providerProductUrl,"cm","mm","m");
		extractMeasure(techSpecs,"Tilavuus","Tilavuus",dimensions,providerProductUrl,"ml","cl","l","L");

		logger.debug("From specs \"" + techSpecs + "\" we got dimensions \"" + dimensions.toString() + "\".");
		if (dimensions.toString().trim().length() > 0) {
			return dimensions.toString();
		}
		return null;
	}
	
	private String extractTechnicalSpecs(WebElement techSpecsElement, String providerProductUrl) {
		String specs = techSpecsElement.getText();
		/* Remove "Tukkupakkaus X" if it exists. */
		int index = specs.indexOf("Tukkupakkaus");
		if (index < 0) {
			index = specs.indexOf("tukkupakkaus");
		}
		if (index < 0) {
			index = specs.indexOf("Tukkupakk");
		}
		if (index < 0) {
			index = specs.indexOf("tukkupakk");
		}
		if (index < 0) {
			int temp = specs.indexOf("Tukkup.");
			if (temp > 0 && temp + 13 > specs.length()) {
				index = temp;
			}
		}
		if (index < 0) {
			int temp = specs.indexOf("tukkup.");
			if (temp > 0 && temp + 13 > specs.length()) {
				index = temp;
			}
		}
		if (index >= 0) {
			int index2 = specs.substring(index).indexOf(" ");
			int index3 = specs.substring(index+index2+1).indexOf(" ");
			int index4 = index + index2 + index3 + 1;
			String removal;
			if (index3 <= 0) {
				removal = specs.substring(index);
			} else {
				removal = specs.substring(index,index4);
			}
			if (specs.length() - 20 > index) {
				logger.debug("Tukkupakkaus is not at the end of the string (index = " + index + ", specs.length = " + specs.length() + ")"
						+ " for product " + providerProductUrl);
			}
			logger.debug("Removing \"" + removal + "\" from specs.");
			logger.debug("specs before =  \"" + specs + "\" from specs.");
			//specs = specs.replaceAll(removal, "").trim();
			if (index3 <= 0) {
				specs = specs.substring(0,index);
			} else {
				specs = specs.substring(0,index) + specs.substring(index+removal.length());
			}
			logger.debug("specs after =  \"" + specs + "\" from specs.");
		} else {
			logger.debug("Tukkupakkaus is missing from product " + providerProductUrl);
		}
		return specs;
	}
	
	@Override
	public Product createProduct(String providerProductUrl) throws Exception {
		logger.info("createProduct");
		CustomWebDriver driver = seleniumRobot.getWebDriver();
		driver.get(providerProductUrl);

		/* Set general information for product. */
		Provider provider = this.providerDAO.getProviderByName(Provider.HAUTALA_PROVIDER_NAME);
		Product product = new Product();
		product.setCompensation(false);
		product.setProvider(provider);
		product.setProviderUrl(providerProductUrl);
		product.setContents(null);
		
		if (!providerProductUrl.startsWith(provider.getUrl())) {
			logger.error("Faulty url of product " + product.getId() + " (" + product.getProviderUrl() + ")."
					+ " Should always start with " + product.getProvider().getUrl());
			throw new RuntimeException("Faulty url of product " + product.getId() + " (" + product.getProviderUrl() + ").");
		}
		
		/* Extract the technical specs. */
		String specs = ""; //    //p[@class='product-number']
		WebElement productDescriptionElement = driver.findElementOrNull(By.xpath("//div[@id='product-detail']/div[2]/p[1]"));
		if (productDescriptionElement.getAttribute("class").equals("product-number")) {
			logger.warn("No technical specs found for " + providerProductUrl);
			//throw new Exception("Has the product page of Hautala changed?");
		} else {
			specs = extractTechnicalSpecs(productDescriptionElement, providerProductUrl);
		}
		product.setTechnicalSpecs(specs);
		product.setDimensions(extractDimensions(specs, providerProductUrl));
		
		
		/* Extract the provider product id. */
		WebElement provProdElement = driver.findElementOrNull(By.xpath("//p[@class='product-number']"));
		if (provProdElement == null) {
			logger.warn("Has the product page of Hautala changed (no provProductId for " + providerProductUrl + ")?");
			throw new Exception("Has the product page of Hautala changed?");
		}
		String provProductId = provProdElement.getText().replaceAll("Tuotekoodi", "").replaceAll(":","").replaceAll(" ", "");
		product.setProviderProdId(provProductId);
		
		// Abort if this product already exists (at least one product exists under two different categories).
		Product prod = productDAO.getByProviderProdId(
				provProductId, provider);
		if (prod != null) {
			logger.debug("Product " + provProductId + " already exists, skipping this one (url för existing = "
					+ prod.getProviderUrl() + ", url for new one = " + providerProductUrl);
			return null;
		}

		/* Extract the product name. */
		WebElement nameElement = driver.findElementOrNull(By.xpath("//*[@class='content']/h2"));
		product.setName(nameElement.getText());
		logger.debug("name = " + nameElement.getText());
		
		/* Extract the provider category. */
		String endOfUrl = providerProductUrl.substring(providerProductUrl.lastIndexOf("/"));
		//logger.debug("endOfUrl = " + endOfUrl);
		driver.get("http://kauppa.hautalaservice.com/client/index/" + endOfUrl);
		WebElement categoryElement = driver.findElementOrNull(By.xpath("//*[@id='productlist']/../h2"));
		product.setProviderCategory(categoryElement.getText());
		logger.debug("category = " + categoryElement.getText());
		
		return product;
	}

	private String extractMeasure(String specs, String fieldName, String prefix, StringBuffer dimensions,
			String providerProductUrl, String... units) {
		String fieldNameLowerCase = fieldName.toLowerCase();
		specs = specs.toLowerCase();
		String quantity = null;
		String suffix = null;
		int fieldStartIndex = specs.lastIndexOf(fieldNameLowerCase);
		if (fieldStartIndex >= 0 && (fieldStartIndex == 0 || specs.charAt(fieldStartIndex-1) == ','
				|| specs.charAt(fieldStartIndex-1) == ' ' || specs.charAt(fieldStartIndex-1) == '.')) {
			//logger.debug("fieldNameLowerCase = \"" + fieldNameLowerCase + "\"");
			//logger.debug("prefix = \"" + prefix + "\"");
			String temp = specs.substring(fieldStartIndex);
			//logger.debug("temp = \"" + temp + "\"");
			quantity = EmailFilter.getFieldValue(temp, fieldNameLowerCase, "(,| |\\.)+");
			if (quantity == null) {
				//we're at the end of the row, so no dimensions exist.
				return null;
			}
			quantity.trim();
			//logger.debug("quantity1 = \"" + quantity + "\"");
			if (StringUtilities.removeAllExceptAZLetters(quantity.substring(0,1)).length() > 0) {
				/* If the first character isn't a number, then stop here. */
				return null;
			}

			/* If the next field contains a number, then the quantity probably has a decimal place. */
			String quantityTest = EmailFilter.getFieldValue(temp, fieldNameLowerCase, 2, "(,| |\\.)+");
			if (quantityTest != null) { //is null if we're at the end of the row
				quantityTest = quantityTest.trim();
				//logger.debug("quantityTest = \"" + quantityTest + "\"");
				if (StringUtilities.removeAllExceptDigits(quantityTest).trim().length() > 0) {
					int ind = temp.indexOf(quantityTest) - 1;
					//logger.debug("ind = " + ind);
					if (temp.charAt(ind) == ',' || temp.charAt(ind) == '.') {
						//logger.debug("has decimal place");
						quantity += "," + quantityTest;
					}
				}
			}
			//logger.debug("quantity2 = \"" + quantity + "\"");

			for (int i = 0; i < units.length; i++) {
				/* Sometimes it written like "30g" and sometimes "30 g". */
				if (quantity.indexOf(units[i]) > 0) {
					quantity = quantity.replaceFirst(units[i], "");
					suffix = units[i];
					break;
				}
			}
			//logger.debug("suffix1 = \"" + suffix + "\"");
			if (suffix == null) {
				suffix = EmailFilter.getFieldValue(temp, fieldNameLowerCase, 2, "(,| |\\.)+");
			}
			//logger.debug("suffix2 = \"" + suffix + "\"");
		}
		if (quantity != null && suffix != null) {
			suffix = StringUtilities.removeAllExceptAZLettersAndDigits(suffix);
			if (StringUtilities.removeAllExceptDigits(quantity).trim().equals("")
					|| suffix.length() > 3) {
				/* Skip adding length information if the heuristics failed. */
				logger.debug("Couldn't figure out quantity for \"" + quantity + "\" (fieldName = "
						+ fieldName + ", prefix = " + prefix + ", suffix = " + suffix
						+ ", providerProductUrl = " + providerProductUrl + ")");
			} else {
				String dimension = prefix + ": " + quantity + " " + suffix;
				if (dimensions.length() > 0) {
					dimensions.append("\n");
				}
				dimensions.append(dimension);
				return dimension;
			}
		}
		return null;
	}

	@Override
	public boolean isProductInStock(Product product,
			boolean sloppyStockChecking)
			throws ProductNotFoundAtProviderException,
			ProviderTemporarilyDownException {
		productExistsAtProvider(product);
		CustomWebDriver driver = seleniumRobot.getWebDriver();
		WebElement stockStatusElement = driver.findElementOrNull(By.xpath(".//*[@class='stockstatus']/../div"));
		if (stockStatusElement.getAttribute("title").equals("Varastossa")) {
			return true;
		} else if (stockStatusElement.getAttribute("title").equals("Ei varastossa")) {
			return false;
		}
		throw new ProviderTemporarilyDownException("Hautala temporarily down.");
	}

	private void searchForProductUrl(Product product) throws ProductNotFoundAtProviderException {
		Provider provider = providerDAO.getProviderByName(Provider.HAUTALA_PROVIDER_NAME);
		CustomWebDriver driver = seleniumRobot.getWebDriver();
		/*driver.get(provider.getUrl());
		WebElement searchElement = driver.findElement(By.id("searchbox"));
		searchElement.clear();
		searchElement.sendKeys(product.getProviderProdId());
		searchElement.submit();*/
		//String oldProductUrl = product.getProviderUrl();
		driver.get(provider.getUrl() + "/client/search_results/" + product.getProviderProdId());
		int count = 1;
		WebElement resultElement = driver.findElementOrNull(By.xpath("//*[@class='product-wrap'][" + count + "]/div[2]//a"));
		boolean found = false;
		while (resultElement != null) {
			resultElement.click();
			try { Thread.sleep(1000*3); } catch (Exception e) { }
			WebElement provProdElement = driver.findElementOrNull(By.xpath("//p[@class='product-number']"));
			if (provProdElement == null) {
				logger.warn("Has the product page of Hautala changed (" + product.getProviderUrl()
						+ " doesn't correspond to product " + product.getProviderProdId() + ")?");
				throw new RuntimeException("Has the product page of Hautala changed?");
			} else {
				String provProductId = provProdElement.getText().replaceAll("Tuotekoodi", "").replaceAll(":","").replaceAll(" ", "");
				if (provProductId.equals(product.getProviderProdId())) {
					found = true;
					break;
				}
			}
			driver.navigate().back();
			try { Thread.sleep(1000*2); } catch (Exception e) { }
			count++;
			resultElement = driver.findElementOrNull(By.xpath("//*[@class='product-wrap'][" + count + "]/div[2]//a"));
		}
		if (found) {
			if (!product.getProviderUrl().startsWith(product.getProvider().getUrl())) {
				/* The code above should make sure that we don't get to http://asiakas.trimedia.fi
				 * by sending the search query straight to http://kauppa.hautalaservice.com, but if
				 * there is some bug somewhere, then we'll get here. */
				logger.error("Faulty url of product " + product.getId() + " (" + product.getProviderUrl() + ")."
						+ " Should always start with " + product.getProvider().getUrl());
				throw new RuntimeException("Faulty url of product " + product.getId() + " (" + product.getProviderUrl() + ").");
			}
			String newUrl = driver.getCurrentUrl();
			logger.info("Updating url of product " + product.getId() + " from " + product.getProviderUrl() + " to " + newUrl);
			product.setProviderUrl(newUrl);
			return;
		}
		throw new ProductNotFoundAtProviderException("Product not found at Hautala (not even after searching).");
	}

	@Override
	public void productExistsAtProvider(Product product)
			throws ProductNotFoundAtProviderException,
			ProviderTemporarilyDownException {
		boolean searched = false;
		while (true) {
			try {
				if (!product.getProviderUrl().startsWith(product.getProvider().getUrl())) {
					logger.error("Faulty url of product " + product.getId() + " (" + product.getProviderUrl() + ")."
							+ " Should always start with " + product.getProvider().getUrl());
					throw new RuntimeException("Faulty url of product " + product.getId() + " (" + product.getProviderUrl() + ").");
				}
		
				CustomWebDriver driver = seleniumRobot.getWebDriver();
				driver.get(product.getProviderUrl());
				if (product.getProviderUrl().toLowerCase().equals(driver.getCurrentUrl().toLowerCase())) {
					/* For Hautala the url is not related to the providerProductId, so if Hautala changes their
					 * pages, then a product could move to a different url. Let's make sure that the page contains
					 * the correct provider product id. */
					WebElement provProdElement = driver.findElementOrNull(By.xpath("//p[@class='product-number']"));
					boolean possiblyWrongProduct = false;
					if (provProdElement == null) {
						possiblyWrongProduct = true;
					} else {
						String provProductId = provProdElement.getText().replaceAll("Tuotekoodi", "").replaceAll(":","").replaceAll(" ", "");
						if (!provProductId.equals(product.getProviderProdId())) {
							possiblyWrongProduct = true;
						}
					}
					if (possiblyWrongProduct) {
						logger.warn("Has the product page of Hautala changed (" + product.getProviderUrl()
								+ " doesn't correspond to product " + product.getProviderProdId() + ")?");
						throw new RuntimeException("Has the product page of Hautala changed?");
					}
				} else if (driver.getCurrentUrl().toLowerCase().equals("http://kauppa.hautalaservice.com")
						|| driver.getCurrentUrl().toLowerCase().equals("http://kauppa.hautalaservice.com/")) {
					logger.info("Product " + product.getProviderUrl() + " (" + product.getProviderProdId()
							+ ") not found at Hautala.");
					throw new ProductNotFoundAtProviderException("Product not found at Hautala");
				} else {
					throw new ProviderTemporarilyDownException("Hautala temporarily down.");
				}
				break; //break right away if found
			} catch (ProductNotFoundAtProviderException e) {
				if (!searched) {
					/* The product url's of hautala aren't bound to the provider product id's,
					 * so it's possible that the url has changed. */
					logger.debug("Product " + product.getId() + " not found at Hautala, searching for it.");
					searchForProductUrl(product);
					searched = true;
					continue;
				}
			}
			break;
		}
	}

	/* This method returns all products that exist at Hautala. */
	@Override
	public List<String> getGoodProductIds(int count,
			VirtualCoin minPriceExclVat, VirtualCoin maxPriceExclVat,
			BigDecimal percentsOfProductsInSameCategory) throws Exception {
		if (minPriceExclVat != null || maxPriceExclVat != null) {
			throw new Exception("No price restrictions should be used for Hautala.");
		}
		if (percentsOfProductsInSameCategory.compareTo(new BigDecimal("100")) != 0) {
			throw new Exception("For hautala 100% of the products should be fetched.");
		}
		List<String> returnList = new ArrayList<String>();
		logger.info("getGoodProductIds");
		CustomWebDriver driver = seleniumRobot.getWebDriver();
		driver.get("http://kauppa.hautalaservice.com");
		Thread.sleep(1000*2);
		//WebElement mainMenu = driver.findElementOrNull(By.xpath("//*[@id='nav-products'][1]"));
		int mainCategoryItem = 1;
		WebElement mainCategoryElement = driver.findElementOrNull(By.xpath(".//*[@id='nav-products']/li[" + mainCategoryItem + "]/div/a"));
		while (mainCategoryElement != null) {
			mainCategoryElement.click();
			Thread.sleep(1000*2);
			int subCategoryItem = 1;
			WebElement subCategoryElement = driver.findElementOrNull(By.xpath(".//*[@id='nav-products']/li[" + mainCategoryItem + "]/ul/li[" + subCategoryItem + "]/div/a"));
			int productsAdded = 0;
			if (subCategoryElement == null) {
				logger.debug("Straight under main.");
				/* Now we are at the page where several products are listed. Open them all in turn. */
				List<WebElement> productElements = driver.findElements(By.xpath(".//*[@id='productlist']/div/div[2]/h3/a"));
				for (WebElement productElement : productElements) {
					String url = productElement.getAttribute("href");
					logger.debug("url is " + url + " for " + productElement.getText());
					returnList.add(url);
					if (returnList.size() >= count) {
						return returnList;
					}
					productsAdded++;
				}
			} else {
				/* Individual products are hidden under a sublevel. */
				logger.debug("Not straight under main, rather under subnode.");
				while (subCategoryElement != null) {
					//String currentUrl = driver.getCurrentUrl(); //store current url
					//logger.debug("Current url is " + currentUrl);
					//driver.get(currentUrl); //restore url
					subCategoryElement.click();
					Thread.sleep(1000*2);
					
					/* Now we are at the page where several products are listed. Open them all in turn. */
					List<WebElement> productElements = driver.findElements(By.xpath(".//*[@id='productlist']/div/div[2]/h3/a"));
					for (WebElement productElement : productElements) {
						String url = productElement.getAttribute("href");
						logger.debug("url is " + url + " for " + productElement.getText());
						returnList.add(url);
						if (returnList.size() >= count) {
							return returnList;
						}
						productsAdded++;
					}
					
					driver.navigate().back();
					Thread.sleep(1000*2);
					subCategoryItem++;
					subCategoryElement = driver.findElementOrNull(By.xpath(".//*[@id='nav-products']/li[" + mainCategoryItem + "]/ul/li[" + subCategoryItem + "]/div/a"));
				}
			}
			if (productsAdded == 0) {
				throw new RuntimeException("No products added for mainCategoryItem = " + mainCategoryItem + ", subCategoryItem = " + subCategoryItem);
			}
			driver.navigate().back();
			Thread.sleep(1000*2);
			mainCategoryItem++;
			mainCategoryElement = driver.findElementOrNull(By.xpath(".//*[@id='nav-products']/li[" + mainCategoryItem + "]/div/a"));
		}
		return returnList;
	}

	@Override
	public void downloadImages(Product product, File basePath) throws Exception {
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
		CustomWebDriver driver = seleniumRobot.getWebDriver();
		driver.get(product.getProviderUrl());
		logger.info("Starting to find images for product " + product.getProviderUrl());

		/* First download the original large images. */
		WebElement mainPicLink = driver.findElementOrNull(By.xpath("//*[@id='product-detail']//img"));
		int imgNumber = 1;
		logger.info("Downloading image " + imgNumber);
		File imageFile = downloadImage(mainPicLink.getAttribute("src"),product,imgNumber,productImagePath);
		if (imageFile != null) {
			imageFiles.add(imageFile);
		}
		imgNumber++;

		/* Load also possible other images. */
		for (int i = 1;; i++) {
			WebElement subPicLink = driver.findElementOrNull(By.xpath("//*[@id='container']/div[2]/a[" + i + "]"));
			if (subPicLink != null) {
				logger.info("Downloading image " + imgNumber);
				imageFile = downloadImage(subPicLink.getAttribute("href"),product,imgNumber,productImagePath);
				if (imageFile != null) {
					imageFiles.add(imageFile);
				}
			} else {
				break;
			}
			imgNumber++;
		}

		logger.info("Found " + imageFiles.size() + " images");
		for (File file : imageFiles) {
			logger.info("Creating image");
			Image image = new Image();
			image.setImageOrder(imageFiles.indexOf(file));
			image.setLocalFileUrl(file.getPath());
			image.setImageHostUrl("http://www.sellstar.fi/images/products/" + subDirPrefix + "/" + file.getName());
			image.setProduct(product);
			logger.info("Saves image");
			imageDAO.save(image);
			imageDAO.flush(); // do a flush in order to catch database
			// errors right away
			logger.info("Image saved");
		}
	}

	private File downloadImage(String imageUrl, Product product, int counter, File productImagePath) throws Exception {
		logger.info("Downloading image from: " + imageUrl);
		String fileName = product.getId() + "_" + counter + ".jpg";
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
			return imageFile;
		}
		return null;
	}

	@Override
	public ProviderQuestion updateConversationAtProvider(OrderBundle bundle,
			ProviderQuestion providerQuestion) throws Exception {
		throw new RuntimeException("Not yet implemented.");
	}

	@Override
	public ProviderQuestion addQuestionToProvider(
			ProviderQuestion providerQuestion, OrderBundle bundle,
			String subject, String question, String conversationType)
			throws Exception {
		throw new RuntimeException("Not yet implemented.");
	}

	@Override
	public AdTemplate createAutoTemplate(Product product) {
		Seller seller = sellerDAO.get(3L); //default seller for Hautala is #3
		AdTemplate adTemplate = new AdTemplate();
		adTemplate.setSeller(seller);
		adTemplate.setLanguage(FINNISH_LANGUAGE);
		adTemplate.setProduct(product);
		adTemplate.setHeadline(product.getName());
		adTemplate.setDetails(product.getContents());
		adTemplate.setTechnicalSpecs(product.getTechnicalSpecs());

		adTemplate.setActive(true);
		adTemplate.setLocation(LOCATION);
		adTemplate.setBeanName("templateGenerator");
		adTemplate.setOpenDays(5);
		adTemplate.setPaymentWay(1);
		adTemplate.setQuantity(1);
		adTemplate.setSlogan(SLOGAN);
		adTemplate.setShippingDescription(StringUtilities.escapeString2HTML(SHIPPING_DESCRIPTION));
		if (adTemplate.getSummary() == null) {
			adTemplate.setSummary(""); // cannot be null
		}
		if (adTemplate.getDetails() == null) {
			adTemplate.setDetails(""); // cannot be null
		}
		adTemplate.setShippingWay(1);
		adTemplate.setCreatedTimestamp(new Date());
		adTemplate.setAddedBy("SellStar");

		return adTemplate;
	}

	@Override
	public void login(Provider provider, int tryCount)
			throws ProviderTemporarilyDownException {
		throw new UnsupportedOperationException("This method isn't yet implemented");
	}

	@Override
	public void logout(Provider provider, boolean removeDanglingOrders) {
		throw new UnsupportedOperationException("This method isn't yet implemented");
	}

}
