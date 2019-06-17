package fi.jonix.huutonet.order.dx;

import java.io.File;
import java.math.BigDecimal;
import java.util.List;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.thoughtworks.selenium.SeleniumException;

import fi.jonix.huutonet.domain.VirtualCoin;
import fi.jonix.huutonet.domain.model.AdTemplate;
import fi.jonix.huutonet.domain.model.Currency;
import fi.jonix.huutonet.domain.model.Order;
import fi.jonix.huutonet.domain.model.OrderBundle;
import fi.jonix.huutonet.domain.model.Product;
import fi.jonix.huutonet.domain.model.Provider;
import fi.jonix.huutonet.domain.model.ProviderQuestion;
import fi.jonix.huutonet.domain.model.Tracking;
import fi.jonix.huutonet.domain.model.dao.OrderDAO;
import fi.jonix.huutonet.domain.model.dao.SettingsDAO;
import fi.jonix.huutonet.domain.model.dao.TrackingDAO;
import fi.jonix.huutonet.email.EmailSender;
import fi.jonix.huutonet.exception.CriticalSectionInfo;
import fi.jonix.huutonet.exception.PayPalCaptchaException;
import fi.jonix.huutonet.exception.PlaceOrderException;
import fi.jonix.huutonet.exception.ProductNotFoundAtProviderException;
import fi.jonix.huutonet.exception.ProviderTemporarilyDownException;
import fi.jonix.huutonet.exception.SoldOutAtProviderException;
import fi.jonix.huutonet.order.OrderShippedHandler;
import fi.jonix.huutonet.order.Price;
import fi.jonix.huutonet.order.ProviderOrder;
import fi.jonix.huutonet.robot.CustomSelenium;
import fi.jonix.huutonet.robot.SeleniumRobot;
import fi.jonix.huutonet.tools.Encryption;

@Component(value = "dealExtremeProvider")
public class DealExtremeProvider extends ProviderOrder {

	public static final Logger logger = Logger.getLogger(DealExtremeProvider.class);

	private static String BASE_URL = "http://www.dealextreme.com/details.dx/";
	private static String CLEAR_SHOPPING_CART = "http://www.dealextreme.com/shoppingcart.dx/c.CLEAR";
	private static String DROP_SHIPMENT_PRODUCT = "sku.1888";
	private static String PHONE_NUMBER = "+35845123456"; //John's phone number
	private static String CANCEL_DESCRIPTION_SETTINGS = "fi.jonix.huutonet.robot.order.DealExtremeProvider.cancelDescription";
	
	@Autowired
	private SeleniumRobot seleniumRobot;
	
	@Autowired
	private OrderDAO orderDAO;
	
	@Autowired
	private SettingsDAO settingsDAO;

	@Autowired
	private TrackingDAO trackingDAO;
	
	@Override
	public Price getPrice(Product product, boolean sloppyStockChecking)
		throws SoldOutAtProviderException, ProductNotFoundAtProviderException, ProviderTemporarilyDownException {
		throw new RuntimeException("This method is no longer maintained (multi currency support not fixed).");
		/*try {
			logger.debug("Fetching price for: " + BASE_URL + dxProductId); //TODO: start using the field product.provider_url instead
			CustomSelenium selenium = seleniumRobot.getSelenium();
			selenium.open(BASE_URL + dxProductId); //TODO: start using the field product.provider_url instead
			if (selenium.isElementPresent("//img[@alt='Sold out']")) {
				throw new SoldOutAtProviderException("Item is sold out");
			}
			if (selenium.isElementPresent("//img[@alt='Back Ordered']")) {
				throw new SoldOutAtProviderException("Item is back ordered");
			}
			if (selenium.getBodyText().indexOf("the page or product could not be found") >= 0) {
				throw new ProductNotFoundAtProviderException("Product " + dxProductId + " not found at DX");
			}
			String bodyText = null;
			bodyText = selenium.getBodyText();
			if (bodyText == null || bodyText.trim().equals("") || selenium.getHtmlSource() == null || selenium.getHtmlSource().trim().equals("")
					|| bodyText.indexOf("504 Gateway Time-out") >= 0
					|| (bodyText.indexOf("We are temporarily unable to process your request.") >= 0
							&& bodyText.indexOf("Please try again in a few minutes") >= 0)
					|| bodyText.indexOf("The server is temporarily down") >= 0
					|| bodyText.indexOf("Server Error") >= 0) {
				throw new ProviderTemporarilyDownException("DealExtreme temporarily down.");
			}
			//logger.debug("bodyText = \"" + bodyText + "\"");
			//logger.debug("htmlSource = \"" + selenium.getHtmlSource() + "\"");
			String price;
			try {
				price = selenium.getText("//img[@alt='free shipping']/../span");
			} catch (SeleniumException e) {
				//logger.debug("bodyText = \"" + selenium.getBodyText() + "\"");
				//logger.debug("htmlSource = \"" + selenium.getHtmlSource() + "\"");
				//throw e;
				//It seems like sometimes selenium isn't able to find this tag although
				//there is nothing wrong with the page, so let's just throw a temporarily
				//down exception here.
				throw new ProviderTemporarilyDownException("Free shipping tag not found.");
			}
			BigDecimal d = new BigDecimal(price.substring(1));
			if (d == null || d.doubleValue()  < 0.2) {
				throw new SoldOutAtProviderException("Item seems to be sold out because price is too low.");
			}
			
			//TODO: make sure that we check the database field provider.currency_id here and make sure that
			//the web page displays the prices in the same currency.
			//Long usedCurrency = Currency.DOLLAR;
			//return new VirtualCoin(usedCurrency,d);
			throw new RuntimeException("Not yet fixed multi currency support.");
		} catch (SeleniumException e) {
			logger.warn("Provider temporarily down: ", e);
			if (e.getMessage() != null && (e.getMessage().indexOf("Command timed out") >= 0
					|| e.getMessage().indexOf("Timed out after") >= 0)) {
				throw new ProviderTemporarilyDownException("DealExtreme temporarily down.");
			} else {
				throw e;
			}
		}*/
	}
	
	@Override
	public void placeOrder(OrderBundle bundle, List<Order> bundleOrders, String user, String password, CriticalSectionInfo section) throws PlaceOrderException, ProviderTemporarilyDownException, SoldOutAtProviderException, PayPalCaptchaException, ProductNotFoundAtProviderException {
		logger.info("Placing bundle order for bundle.id " + bundle.getId() + " to DealExtreme.");
		if (bundleOrders.size() > 1) {
			throw new PlaceOrderException("Not yet implemented multi-order bundle handling in DealExtremeProvider.placeBundleOrder.");
		} else {
			placeSingleOrder(bundle, bundleOrders, user, password);
		}
	}
	
	private void placeSingleOrder(OrderBundle bundle, List<Order> bundleOrders, String user, String password)
		throws PlaceOrderException, ProviderTemporarilyDownException, SoldOutAtProviderException, PayPalCaptchaException, ProductNotFoundAtProviderException {
		throw new RuntimeException("This method is no longer maintained (multi currency support not fixed).");
		/*CustomSelenium selenium = seleniumRobot.getSelenium();
		logger.info("Placing order for single ordered bundle = " + bundle.getId());
		Order order = bundleOrders.get(0);
		boolean resending = order.getState().equals(Order.STATE_SHOULD_RESEND);
		
		if (bundleOrders.size() > 1) {
			throw new PlaceOrderException("Not allowed: cannot handle multi-order bundles in DealExtremeProvider.placeSingleOrder.");
		}

		/* First clear the shopping cart, in case a previous run has bailed out and left something in the cart. /
		try {
			selenium.open(CLEAR_SHOPPING_CART);
		} catch (SeleniumException e) {
			if (e.getMessage() != null && e.getMessage().indexOf("XHR ERROR") >= 0) {
				throw new ProviderTemporarilyDownException("DealExtreme temporarily down.");
			} else {
				throw e;
			}
		}
		
		/* Then add a dropshipment order to chart. This is the thing that removes the
		 * DX logo from the package. /
		selenium.open(BASE_URL + DROP_SHIPMENT_PRODUCT);
		selenium.click("//img[@alt='Add to cart']/..");
		selenium.waitForPageToLoad();

		if (selenium.isElementPresent("//*[@id='content']/div/div/font/b")) {
			String text = selenium.getText("//*[@id='content']/div/div/font/b");
			if (text.equals("the page or product could not be found")) {
				//throw a PlaceOrderException here (which will put SellStar in the error state),
				//because it's a serious error if the drop shipment product isn't found
				throw new PlaceOrderException("Product not found at DX");
			}
		}
		String sumStr = selenium.getText("//span[contains(@id,'GrandTotal')]").replaceFirst("\\$","");
		BigDecimal dropShippingCostInDollars = new BigDecimal(sumStr);

		String bodyText = selenium.getBodyText();
		if (bodyText == null || bodyText.trim().equals("") || selenium.getHtmlSource() == null || selenium.getHtmlSource().trim().equals("")
				|| bodyText.indexOf("504 Gateway Time-out") >= 0
				|| (bodyText.indexOf("We are temporarily unable to process your request.") >= 0
						&& bodyText.indexOf("Please try again in a few minutes") >= 0)
				|| bodyText.indexOf("The server is temporarily down") >= 0
				|| bodyText.indexOf("Server Error") >= 0) {
			throw new ProviderTemporarilyDownException("DealExtreme temporarily down.");
		}

		//Order tracking
		BigDecimal trackingCostInDollars = BigDecimal.ZERO;
		if (bundle.getSeller().getTrackingAlways()) {
			selenium.open("http://www.dealextreme.com/shoppingcart.dx/c.TRACKING");
			try { Thread.sleep(2000L); } catch (Exception e) {}
			sumStr = selenium.getText("//span[contains(@id,'GrandTotal')]").replaceFirst("\\$","");
			trackingCostInDollars = (new BigDecimal(sumStr)).subtract(dropShippingCostInDollars);
		}
		BigDecimal shippingCostInDollars = dropShippingCostInDollars.add(trackingCostInDollars);

		// Product order
		selenium.open(BASE_URL + order.getProviderProdId()); //TODO: start using the field product.provider_url instead
		if (selenium.isElementPresent("//*[@id='content']/div/div/font/b")) {
			String text = selenium.getText("//*[@id='content']/div/div/font/b");
			if (text.equals("the page or product could not be found")) {
				throw new ProductNotFoundAtProviderException("Product not found at DX");
			}
		}
		try {
			selenium.click("//img[@alt='Add to cart']/..");
		} catch (Exception e) {
			throw new SoldOutAtProviderException("Product sold out at provider");
		}
		selenium.waitForPageToLoad();

		/* Set the shipping cost of the bundle. /
		setShippingCostAtOrderingFromProvider(bundle,shippingCostInDollars,resending);
		
		// Check the total sum
		sumStr = selenium.getText("//span[contains(@id,'GrandTotal')]").replaceFirst("\\$","");
		BigDecimal totalSumInDollars = new BigDecimal(sumStr);
		BigDecimal totalSumInEuro = currencyChecker.getDollar2Euro(totalSumInDollars);

		BigDecimal productProviderPriceInDollars = totalSumInDollars.subtract(shippingCostInDollars);
		BigDecimal productProviderPriceInEuro = currencyChecker.getDollar2Euro(productProviderPriceInDollars);
		logger.debug("Provider total price = " + totalSumInDollars + " dollars (= " + totalSumInEuro + " euros)"
				+ ", shipping cost = " + shippingCostInDollars + " dollars (= "
				+ currencyChecker.getDollar2Euro(shippingCostInDollars) + " euros)"
				+ ", product cost = " + productProviderPriceInDollars + " dollars (= " + productProviderPriceInEuro + " euros)");
		checkTheTotalSum(totalSumInEuro, bundle, bundleOrders);
		setFinalProviderPriceAtOrderingFromProvider(order,productProviderPriceInDollars,productProviderPriceInEuro);
		
		/* Set transactionId and providerOrderId to null in case we cannot fetch it below
		 * due to for example selenium errors. This is of significance only for resendings
		 * (since for first orderings the transactionId/providerOrderId is always null). This
		 * is done to make sure that nothing of the old order is left if we get the case
		 * where the ordering bailed out due to selenium errors but the transaction still
		 * wasn't rolled back. /
		logger.debug("Old providerOrderId for bundle " + bundle.getId() + ": " + bundle.getProviderOrderId());
		bundle.setProviderOrderId(null);
		logger.debug("Old transactionId for bundle " + bundle.getId() + ": " + bundle.getTransactionId());
		bundle.setTransactionId(null);

		//Login to PayPal. Now and then the login_email field at PayPal is not found.
		//In that case throw a ProviderTemporaryDownException which won't set SellStar
		//into the error state, but rather continue with the next order.
		selenium.click("//input[contains(@name,'PayPalExpressCheckout')]");
		selenium.waitForPageToLoad();
		if (selenium.getBodyText().indexOf("Type the characters you see in the image for security purposes.") >= 0) {
			throw new PayPalCaptchaException("Cannot solve PayPal captcha.");
		}
		/* Sometimes the PayPal login login is hidden, so then we need to open it first. /
		if (!selenium.isElementPresent("//input[@id='login_email']") && selenium.isElementPresent("//input[@id='loadLogin']")) {
			logger.debug("PayPal login was hidden, opening.");
			selenium.click("//input[@id='loadLogin']");
		} else {
			logger.debug("PayPal login was already open.");
		}
		try {
			selenium.type("//input[@id='login_email']",user);
		} catch (SeleniumException se) {
			if (se.getMessage() != null && se.getMessage().indexOf("Element") > -1 && se.getMessage().endsWith("not found")) {
				logger.debug("bodyText = \"" + selenium.getBodyText() + "\"");
				logger.debug("htmlSource = \"" + selenium.getHtmlSource() + "\"");
			}
			throw new ProviderTemporarilyDownException("Temporary error: couldn't logon to PayPal.");
		}
		selenium.type("//input[@id='login_password']",Encryption.decrypt(password));
		selenium.click("//input[@id='submitLogin']");
		selenium.waitForPageToLoad();
		selenium.click("//input[@id='continue']");
		selenium.waitForPageToLoad();
		
		/* Fill out customer information as shipping address details. Here we give the customer information
		 * with one exception: the phone number is hardcoded to be Tomi's phone. It's better if PayPal
		 * contacts us in case of problems than that they would call our customer. */
		/*selenium.type("//table[@id='table1']/tbody/tr/td[1]/table/tbody/tr[9]/td[2]/input",PHONE_NUMBER);
		selenium.type("//table[@id='table1']/tbody/tr/td[1]/table/tbody/tr[7]/td[2]/input", order.getOrderBundle().getFirstName());
		selenium.type("//table[@id='table1']/tbody/tr/td[1]/table/tbody/tr[7]/td[4]/input", order.getOrderBundle().getAddress());
		selenium.type("//table[@id='table1']/tbody/tr/td[1]/table/tbody/tr[8]/td[2]/input", order.getOrderBundle().getLastName());
		selenium.type("//table[@id='table1']/tbody/tr/td[1]/table/tbody/tr[8]/td[4]/input", order.getOrderBundle().getCity());
		selenium.type("//table[@id='table1']/tbody/tr/td[1]/table/tbody/tr[9]/td[4]/input", order.getOrderBundle().getCity());
		selenium.type("//table[@id='table1']/tbody/tr/td[1]/table/tbody/tr[10]/td[3]/input", order.getOrderBundle().getPostcode());/
		selenium.type("//input[@id='ctl00_content_Phone']",PHONE_NUMBER);
		selenium.type("//input[@id='ctl00_content_FirstName']", bundle.getFirstName());
		selenium.type("//input[@id='ctl00_content_Address']", bundle.getAddress());
		selenium.type("//input[@id='ctl00_content_LastName']", bundle.getLastName());
		selenium.type("//input[@id='ctl00_content_City']", bundle.getCity());
		selenium.type("//input[@id='ctl00_content_State']", bundle.getCity());
		selenium.type("//input[@id='ctl00_content_Zip']", bundle.getPostcode());
		selenium.click("//input[@value='Confirm Order']");
		selenium.waitForPageToLoad();
		
		/* Check for errors in the processing of the order. In some cases dx cannot handle the order and displays an
		 * error message. The following types of errors are recognized:
		 * 1.) "This transaction cannot be processed at this time. Please try again later."
		 * 2.) "Apologies -- transaction cannot be completed.
		 *      Your PayPal or Credit Card is not charged and the order is not created because of
		 *      PayPal instruction: A successful transaction has already been completed for this token. [code: pp_do]
		 *      If you don't know what it is, remember the following ID and contact us at our forum:
		 *      EC-8WJ905449H561500W"
		 * 
		 * For type #1 we roll back the transaction and try again later.
		 * 
		 * For type #2 one of two things can happen:
		 * a.) We get an email of type "Your order #1201AKG8 with DealExtreme" shortly after the transaction AND
		 *     the order shows up at the transaction list at DX (requires a logon).
		 * b.) We never get any email of type "Your order #1201AKG8 with DealExtreme" nor does the transaction
		 *     show up at the transaction list at DX, but instead we get an email of type "Please Confirm Your
		 *     Shipping Address" a few days later.
		 * In both of these cases the orders are fine. But possibly (?) there is also a third possibility: that the
		 * order didn't go through after all. We are not totally sure about this.
		 * 
		 * In order to deal with this problem we do the following:
		 * If this error occurs, then we don't set providerOrderId nor transactionId and set the state of
		 * the order to STATE_ORDERED_FROM_PROVIDER_NO_ID. If a "Your order #1201AKG8 with DealExtreme" or a
		 * "Please Confirm Your Shipping Address" comes in within a week, then we update the order automatically
		 * to STATE_ORDERED_FROM_PROVIDER / STATE_ORDER_CONFIRMED_BY_PROVIDER. If neither type of email has come
		 * within a week, then we set the state to STATE_CUSTOMER_PAID_INFORMED so that the product will be
		 * automatically ordered again. /
		bodyText = selenium.getBodyText();
		if (bodyText.indexOf("transaction cannot be completed") >= 0) {
			String headline = null;
			String id = null;
			String errorMessage = null;
			String infoMessage = null;
			try { headline = selenium.getText("//*[@id='ctl00_content_pError']/div/table/tbody/tr[1]/td/span/strong"); } catch (Exception e) { }
			try { id = selenium.getText("//*[@id='ctl00_content_pError']/div/table/tbody/tr[2]/td/span/b[3]"); } catch (Exception e) { }
			try { errorMessage = selenium.getText("//*[@id='ctl00_content_lblErrorMessage']"); } catch (Exception e) { }
			try { infoMessage = selenium.getText("//*[@id='ctl00_content_pError']/div/table/tbody/tr[2]/td/span"); } catch (Exception e) { }
			logger.debug("headline = " + headline);
			logger.debug("id = " + id);
			logger.debug("errorMessage = " + errorMessage);
			logger.debug("infoMessage = " + infoMessage);
			logger.debug("bodyText = \"" + bodyText + "\"");
			logger.debug("htmlSource = \"" + selenium.getHtmlSource() + "\"");
			/* If there is a message about trying again later, then quit the placing of orders without going to
			 * the error state, otherwise go to the error state. /
			if (bodyText.indexOf("This transaction cannot be processed at this time. Please try again later.") >= 0) {
				throw new PlaceOrderException("Transaction cannot be completed at dx, try again later.", false);
			} else if (bodyText.indexOf("Your PayPal or Credit Card is not charged and the order is not created because of PayPal instruction") >= 0
					&& bodyText.indexOf("The transaction cannot complete successfully.") >= 0
					&& bodyText.indexOf("Instruct the customer to use an alternative payment method.") >= 0) {
				/* This can come if the credit card is disabled for example because a bill wasn't paid. /
				throw new PlaceOrderException("Transaction cannot be completed at dx - something wrong with the credit card?");
			} else if (bodyText.indexOf("Your PayPal or Credit Card is not charged and the order is not created because of PayPal instruction") >= 0
					&& bodyText.indexOf("A successful transaction has already been completed for this token") >= 0) {
				order.setStateWithHistory(Order.STATE_ORDERED_FROM_PROVIDER_NO_ID);
				String email = "Problem placing order at provider DealExtreme for bundle " + bundle.getId() + ".\n"
							+ "The problem is due to a buggy DX.\nheadline = " + headline + "\n"
							+ "id = " + id + "\nerrorMessage = " + errorMessage + "\ninfoMessage " + infoMessage + "\n\n"
							+ "Not setting transactionId nor providerOrderId and setting status to " + Order.STATE_ORDERED_FROM_PROVIDER_NO_ID;
				EmailSender.sendOperationProblemReport("Ordering problem",email,bundle.getProvider().getSystemEmail());
				return;
			} else {
				throw new PlaceOrderException("Transaction cannot be completed at dx.");
			}
		}

		// Receipt
		int newState = Order.STATE_ORDERED_FROM_PROVIDER;
		try {
			String dxTransactionId = selenium.getText("//*[@id='ctl00_content_lblTxn_ID']");
			logger.debug("DealExtreme TransactionId = " + dxTransactionId);
			bundle.setTransactionId(dxTransactionId);
			String providerOrderId = selenium.getText("//*[@id='ctl00_content_lblOrderNumber']");
			logger.debug("Provider order id = " + providerOrderId);
			bundle.setProviderOrderId(providerOrderId);
			//we don't get any payPalId from DealExtreme

			/* Safety check in case the web pages have changed. /
			if (dxTransactionId.indexOf(" ") >= 0 || providerOrderId.indexOf(" ") >= 0) {
				logger.debug("Weird page?: " + selenium.getHtmlSource());
				throw new PlaceOrderException("Weird dxTransactionId (" + dxTransactionId + ") or providerOrderId ("
						+ providerOrderId + "). Has the web page changed?");
			}
		} catch (SeleniumException e) {
			logger.error("Ordering error: ",e);
			/* In some cases dx bails out after placing an order. If so, then set the order in state otherError
			 * and inform the operator (but don't roll back the transaction). /
			if (e.getMessage() != null && e.getMessage().indexOf("Element") > -1 && e.getMessage().endsWith("not found")) {
				logger.debug("bodyText = \"" + selenium.getBodyText() + "\"");
				logger.debug("htmlSource = \"" + selenium.getHtmlSource() + "\"");
				newState = Order.STATE_OTHER_ERROR;
				String email = "Problem placing order at provider DealExtreme for bundle " + bundle.getId()
					+ ". TransactionId and providerOrderId couldn't be fetched. Setting the order to state otherError.\n\n"
					+ e.getMessage();
				EmailSender.sendOperationProblemReport("Ordering problem",email,bundle.getProvider().getSystemEmail());
			} else {
				throw e;
			}
		}

		//NOTE: No log out needed
		
		order.setStateWithHistory(newState);*/
	}

	/**
	 * @return True if the cancellation went through and false if it failed.
	 */
	@Override
	public boolean cancelOrder(Order order) throws Exception {
		/* There is some problem with opening the status page of dealextreme using selenium.
		 * At least if it's done programmatically on the mac (by hand it works). The problem
		 * might be that the html generated by dealextreme is malformed, or else there is
		 * some bug in selenium/firefox. In any case we need to turn off status update
		 * checks for deal extreme until this is fixed.
		 * 
		 * TODO: fix this. */
		logger.info("Order " + order.getId() + " cannot be automatically canceled at dx because of selenium problem (cancelOrder).");
		String f = "g";
		if (!f.equals("dasasf")) {
			return false;
		}

		logger.debug("Canceling order");
		CustomSelenium selenium = seleniumRobot.getSelenium();
		selenium.open("http://www.dealextreme.com/accounts/default.dx");
		selenium.type("//input[contains(@name,'OrderNumber')]",order.getOrderBundle().getProviderOrderId());
		selenium.type("//input[contains(@name,'EMail')]",order.getOrderBundle().getProvider().getUserName());
		selenium.click("//input[@value='rbUpdateOrder']");
		selenium.click("//input[contains(@name,'Track')]");
		selenium.waitForPageToLoad();
		
		// if "Continue to Final step"-button is disabled, the order cannot be canceled
		if(selenium.isElementPresent("//input[contains(@name,'btnContinue')  and @disabled='disabled']"))
			return false;
		
		selenium.click("//input[contains(@name,'btnContinue')]");
		selenium.waitForPageToLoad();
		selenium.click("//input[contains(@name,'listConfirmation') and contains(@value,'Yes')]");
		selenium.type("//input[contains(@name,'txtNotes')]",this.settingsDAO.getValue(CANCEL_DESCRIPTION_SETTINGS));
		selenium.click("//input[contains(@name,'btnCancelOrder')]");
		selenium.waitForPageToLoad();
		order.setStateWithHistory(Order.STATE_PROVIDER_CANCEL_PENDING);
		this.orderDAO.save(order);
		logger.debug("Order canceled successfully at provider");
		return true;
	}
	
	@Override
	public void updateOrderStatus(OrderBundle bundle, List<Order> bundleOrders, CriticalSectionInfo section) throws Exception {
		/* There is some problem with opening the status page of dealextreme using selenium.
		 * At least if it's done programmatically on the mac (by hand it works). The problem
		 * might be that the html generated by dealextreme is malformed, or else there is
		 * some bug in selenium/firefox. In any case we need to turn off status update
		 * checks for deal extreme until this is fixed.
		 * 
		 * TODO: fix this. */
		logger.info("Bundle " + bundle.getId() + " cannot get its status updated at dx because of selenium problem (updateStatusOfOrders).");
		String f = "g";
		if (!f.equals("dasasf")) {
			return;
		}
		
		if (!bundle.getProvider().getName().equals(Provider.DEAL_EXTREME_PROVIDER_NAME)) {
			throw new RuntimeException("Can only handle DealExtreme.");
		}

		logger.debug("Updating status of bundle " + bundle.getId() + " with providerOrderId " + bundle.getProviderOrderId());
		CustomSelenium selenium = seleniumRobot.getSelenium();
		selenium.open("http://www.dealextreme.com/accounts/default.dx", 3, "30000");
		selenium.type("//input[contains(@name,'OrderNumber')]",bundle.getProviderOrderId());
		selenium.type("//input[contains(@name,'EMail')]",bundle.getProvider().getUserName());
		selenium.click("//input[@value='rbTrackStatus']");
		selenium.click("//input[contains(@name,'Track')]");
		selenium.waitForPageToLoad();
		String dxStatus = selenium.getText("//*[@id='Table3']/tbody/tr[2]/td[2]/font");
		String trackingCode = null;
		Integer newState = null;

		if (dxStatus.indexOf("Temporary stock shortage") > -1) {
			newState = Order.STATE_STOCK_SHORTAGE;
		} else if (dxStatus.indexOf("Processing - Awaiting stock") > -1) {
			newState = Order.STATE_AWAITING_STOCK;
		} else if (dxStatus.indexOf("Waiting for Supplier") > -1) {
			if (bundleOrders.get(0).getState().equals(Order.STATE_ORDERED_FROM_PROVIDER)) {
				/* Normally we get an email from dx telling that the order was confirmed, but in case
				 * this email didn't come or if it was delayed, then this method can also set the state
				 * to "confirmed by provider". */
				newState = Order.STATE_ORDER_CONFIRMED_BY_PROVIDER;
			} else {
				newState = Order.STATE_WAITING_FOR_SUPPLIER;
			}
		} else if (dxStatus.indexOf("Packaging") > -1) {
			newState = Order.STATE_FULFILMENT_CENTER;
		} else if (dxStatus.indexOf("Cancelled") > -1) {
			newState = Order.STATE_PROVIDER_CANCEL_CONFIRMED;
		} else if (dxStatus.indexOf("Shipped") > -1) {
			/* Usually dx sends out emails when a product is shipped, but in case the email never
			 * came we can handle it here as well. */
			trackingCode = selenium.getText("//*[@id='ctl00_content_lblTrackingNumber']");
			logger.debug("trackingCode = \"" + trackingCode + "\"");
			newState = Order.STATE_SHIPPED_BY_PROVIDER;
		} else {
			throw new Exception("Unknown status " + dxStatus + " for dx (bundle " + bundle.getId() + ")");
		}
		logger.debug("Status for bundle " + bundle.getId() + ": " + dxStatus);
		int state = bundleOrders.get(0).getState();
		if (state != newState.intValue()) {
			if (newState.equals(Order.STATE_STOCK_SHORTAGE)/* && order.getState().equals(Order.STATE_STOCK_SHORTAGE_INFORMED)*/) {
				//do nothing, from this method's perspective stock shortage and stock shortage confirmed is the same thing
			} else {
				logger.info("Updating bundle " + bundle.getId() + " from state " + state + " to state " + newState);
				if (newState.equals(Order.STATE_SHIPPED_BY_PROVIDER)) {
					Tracking tracking = null;
					if (trackingCode != null && !trackingCode.trim().equals("")) {
						tracking = new Tracking();
						tracking.setOrderBundle(bundle);
						tracking.setTrackingCode(trackingCode);
						tracking.setFollow(true);
						trackingDAO.save(tracking);
					}
					
					/* Associate the new tracking object (if any) with the order and remove the old one
					 * if this is a resending.
					 * => UPDATE: old tracking objects are removed already in OperatorProcessor.processResendOrder. */
					for (Order order : bundleOrders) {
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
								trackingDAO.delete(oldTrackingObject);
							} else {
								throw new Exception("Error: old tracking object exists although not resending (bundle "
										+ bundle.getId() + ", order " + order.getId() + ").");
							}
						}*/
					}

					OrderShippedHandler.handleOrderBundleShippedByProvider(tracking, bundleOrders, bundleOrders, section);
				} else if (newState.equals(Order.STATE_ORDER_CONFIRMED_BY_PROVIDER)) {
					orderBundleConfirmedByProvider(bundle, bundleOrders);
				} else {
					setStateOfBundle(newState, bundle, bundleOrders);
				}
			}
		}
	}
	
	@Override
	public Product createProduct(String providerProductId) throws Exception {
		throw new UnsupportedOperationException("This method isn't yet implemented");
	}

	@Override
	public boolean isProductInStock(Product product, boolean sloppyStockChecking) throws ProductNotFoundAtProviderException, ProviderTemporarilyDownException {
		throw new UnsupportedOperationException("This method isn't yet implemented");
	}

	@Override
	public List<String> getGoodProductIds(int count, VirtualCoin minPriceExclVat, VirtualCoin maxPriceExclVat, BigDecimal percentsOfProductsInSameCategory) throws Exception {
		throw new UnsupportedOperationException("This method isn't yet implemented");
	}

	/*@Override
	public Price getPriceFromLoadedPage() throws ProviderTemporarilyDownException, SoldOutAtProviderException {
		throw new UnsupportedOperationException("This method isn't yet implemented");
	}*/

	@Override
	public void loadProductPage(String providerProductId) {
		CustomSelenium selenium = seleniumRobot.getSelenium();
		selenium.open(BASE_URL + providerProductId); //TODO: start using the field product.provider_url instead
	}

	@Override
	public void downloadImages(Product product, File basePath) throws Exception {
		throw new UnsupportedOperationException("This method isn't yet implemented");
	}

	@Override
	public void productExistsAtProvider(Product product)
			throws ProductNotFoundAtProviderException, ProviderTemporarilyDownException {
		throw new UnsupportedOperationException("This method isn't yet implemented");
	}

	@Override
	public boolean canHandleProvider(Provider provider) {
		return provider.getName().equals(Provider.DEAL_EXTREME_PROVIDER_NAME);
	}

	@Override
	public void loadProductPage(String providerProductId, int retries,
			String timeout) {
	}

	@Override
	public void updateTechnicalSpecs(Product product) throws Exception {
		throw new UnsupportedOperationException("This method isn't yet implemented: move the code from dxProductHelper.updateTechnicalSpecsOfProduct to this method!");
	}

	@Override
	public ProviderQuestion updateConversationAtProvider(OrderBundle bundle,
			ProviderQuestion providerQuestion) throws Exception {
		throw new UnsupportedOperationException("This method isn't yet implemented");
	}

	@Override
	public ProviderQuestion addQuestionToProvider(ProviderQuestion providerQuestion, OrderBundle bundle, String subject,
			String question, String conversationType) throws Exception {
		throw new UnsupportedOperationException("This method isn't yet implemented");
	}
	
	@Override
	public AdTemplate createAutoTemplate(Product product) {
		//For DealExtreme we cannot automatically create ad templates because
		//the original language is English, so creation of new adtemplates
		//is done through the operator view of google-order.
		return null;
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
