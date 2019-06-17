package fi.jonix.huutonet.market.huuto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import javax.mail.Message;

import org.apache.log4j.Logger;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import fi.jonix.huutonet.domain.VirtualCoin;
import fi.jonix.huutonet.domain.model.Ad;
import fi.jonix.huutonet.domain.model.AdTemplate;
import fi.jonix.huutonet.domain.model.Currency;
import fi.jonix.huutonet.domain.model.Customer;
import fi.jonix.huutonet.domain.model.LoginName;
import fi.jonix.huutonet.domain.model.Market;
import fi.jonix.huutonet.domain.model.Order;
import fi.jonix.huutonet.domain.model.Product;
import fi.jonix.huutonet.domain.model.Provider;
import fi.jonix.huutonet.domain.model.Seller;
import fi.jonix.huutonet.domain.model.SellerMarket;
import fi.jonix.huutonet.domain.model.User;
import fi.jonix.huutonet.domain.model.dao.AdDAO;
import fi.jonix.huutonet.domain.model.dao.AdTemplateDAO;
import fi.jonix.huutonet.domain.model.dao.CustomerDAO;
import fi.jonix.huutonet.domain.model.dao.LoginNameDAO;
import fi.jonix.huutonet.domain.model.dao.MarketDAO;
import fi.jonix.huutonet.domain.model.dao.MessageDAO;
import fi.jonix.huutonet.domain.model.dao.OrderDAO;
import fi.jonix.huutonet.domain.model.dao.SellerMarketDAO;
import fi.jonix.huutonet.domain.model.dao.UserDAO;
import fi.jonix.huutonet.email.EmailSender;
import fi.jonix.huutonet.exception.CriticalSectionInfo;
import fi.jonix.huutonet.exception.MarketLoggedOnAsWrongUserException;
import fi.jonix.huutonet.exception.MarketTemporarilyDownException;
import fi.jonix.huutonet.exception.ProcessEmailLaterException;
import fi.jonix.huutonet.exception.ProductNotFoundAtProviderException;
import fi.jonix.huutonet.exception.ProviderTemporarilyDownException;
import fi.jonix.huutonet.exception.SoldOutAtProviderException;
import fi.jonix.huutonet.exception.UnprocessableEmailException;
import fi.jonix.huutonet.market.SellerEmailFilter;
import fi.jonix.huutonet.order.OrderCreator;
import fi.jonix.huutonet.order.OrderInformation;
import fi.jonix.huutonet.robot.CustomWebDriver;
import fi.jonix.huutonet.robot.SeleniumRobot;
import fi.jonix.huutonet.tools.MathUtilities;
import fi.jonix.huutonet.tools.Statics;
import fi.jonix.huutonet.tools.StringUtilities;

/**
 * This class takes care of emails coming from huutonet.
 * 
 * @author john
 *
 */
@Component(value = "huutonetProcessor")
public class HuutonetProcessor extends SellerEmailFilter {

	public static final Logger logger = Logger.getLogger(HuutonetProcessor.class);

	private static final String AD_RECEIVED_ID_STRING = "Olemme vastaanottaneet Huuto.netiin ilmoittamasi kohteen";
	private static final String AD_CLOSED_UNSUCCESSFULLY_ID_STRING1 = "Valitettavasti kohteeseesi ei tehty tarjouksia";
	private static final String AD_CLOSED_UNSUCCESSFULLY_ID_STRING2 = "ei ylittänyt kohteelle määrittelemääsi hintavarausta";
	private static final String WON_SINGLE_BID_ID_STRING = "t huutokauppasi voittajan yhteystiedot. Tuotteen voittaneelle";
	private static final String WON_MULTI_BID_ID_STRING = "t huutokauppasi voittajien yhteystiedot. Tuotteen voittaneille";
	private static final String MESSAGE_FROM_USER_ID_STRING = "on lähettänyt sinulle yksityisviestin Huuto.net-palvelusta.";
	private static final String PUBLIC_QUESTION_ID_STRING = "Sinulle on saapunut julkinen kysymys koskien Huuto.netissä myymääsi";
	private static final String WON_OSTA_HETI_ID_STRING = "t ostajan yhteystiedot. Tuotteen ostajalle";

	private static final String HUUTONET_EMAIL_ADDRESS = "meklari@huuto.net";
	private static final String HUUTONET_WEBMASTER_ADDRESS = "webmaster@huuto.net";
	private static final String HUUTONET_NOREPLY_ADDRESS = "noreply@huuto.net";

	@Autowired
	private OrderDAO orderDAO;
	
	@Autowired
	private MarketDAO marketDAO;
	
	@Autowired
	private AdDAO adDAO;
	
	@Autowired
	private CustomerDAO customerDAO;
	
	@Autowired
	private HuutonetLister2 huutonetLister;
	
	@Autowired
	private HuutonetAnswerer huutonetAnswerer;
	
	@Autowired
	private AdTemplateDAO adTemplateDAO;
	
	@Autowired
	private UserDAO userDAO;
	
	@Autowired
	private SellerMarketDAO sellerMarketDAO;
	
	@Autowired
	private LoginNameDAO loginNameDAO;
	
	@Autowired
	private MessageDAO messageDAO;
	
	@Autowired
	private SeleniumRobot seleniumRobot;
	
	/* Processes ad received messages from huutonet. This method sets ad.confirmed to true. */
	private boolean processAdReceived(String contents, Seller seller) throws Exception {
		String marketSalesId = getFieldValue(contents,"Kohteen numero Huuto.netin tietokannassa on");
		if (marketSalesId.endsWith(".")) {
			marketSalesId = marketSalesId.replace(".","");
		}
		logger.info("Ad received notification received from huutonet for market sales id \"" + marketSalesId + "\"");
		Market market = marketDAO.getByName(Market.HUUTONET_MARKET_NAME);
		if (market == null) {
			throw new Exception("market " + Market.HUUTONET_MARKET_NAME + " not found in database.");
		}
		Ad ad = adDAO.getByMarketSalesId(marketSalesId,market,seller);
		if (ad == null) {
			/* Now and then it happens that huutonet sends out an email so quickly after listing that
			 * the listing thread hasn't yet stored the new ad to the database. In those cases it works
			 * if we try again a little later to process the same email. */
			throw new ProcessEmailLaterException("ad.marketSalesId " + marketSalesId + " not found in database (for huutonet).");
		}

		/* Make sure the amount is correct. */
		String amountStr = getFieldValue(contents,"Määrä",2);
		int amount = Integer.parseInt(amountStr);
		if (ad.getOriginalAmount().intValue() != amount) {
			logger.error("Wrong amount: amount according to ad: " + amount + " and amount according"
					+ " to the database: " + ad.getOriginalAmount());
			throw new UnprocessableEmailException("Wrong amount: amount according to ad: " + amount + " and amount according"
					+ " to the database: " + ad.getOriginalAmount());
		} else {
			logger.debug("Correct amount.");
		}
		
		ad.setConfirmed(true);
		adDAO.update(ad);
		adDAO.flush(); //do a flush in order to catch database errors right away
		return true;
	}

	/* Processes messages from huutonet telling that the target is closed and noone bought
	 * the product. The only thing it does is to set the ad.dateFromMarket field to the
	 * current date. */
	private boolean processAdClosedFail(String contents, Seller seller, boolean acceptNotFound,
			CriticalSectionInfo processingInfo) throws Exception {
		String itemNumber = getFieldValue(contents,"kohde numero");
		if (itemNumber.endsWith(",")) {
			itemNumber = itemNumber.replace(",","");
		}
		logger.info("Ad unsuccessfully closed notification received from huutonet for item number \"" + itemNumber + "\"");
		processingInfo.setCanProcessEmailOnlyOnce(false); //for this one it doesn't matter if the email is processed twice
		Market market = marketDAO.getByName(Market.HUUTONET_MARKET_NAME);
		if (market == null) {
			throw new Exception("market " + Market.HUUTONET_MARKET_NAME + " not found in database.");
		}
		Ad ad = adDAO.getByMarketSalesId(itemNumber,market,seller);
		if (ad == null) {
			/* Check if this is an ad that has been moved to a different seller after the listing
			 * but before the closing of the ad. */
			ad = adDAO.getByMarketSalesIdWithoutSeller(itemNumber, market);
			if (ad != null) {
				String warnMsg = "Ad " + ad.getId() + " has been moved from seller " + seller.getName()
					+ " to seller " + ad.getAdTemplate().getSeller().getName() + " after the listing.";
				logger.warn(warnMsg);
			} else {
				if (acceptNotFound) {
					EmailSender.sendOperationSummaryReport("Ad not found at closed fail",
							"ad.marketSalesId " + itemNumber + " not found in database at closed fail (for huutonet). However"
							+ " this isn't a big deal since the product wasn't bought anyway.");
					return true;
				} else {
					throw new UnprocessableEmailException("ad.marketSalesId " + itemNumber + " not found in database at closed fail (for huutonet).");
				}
			}
		}
		if (ad.getConfirmed().booleanValue() == false) {
			//sometimes the confirmation message is lost on the way - here we know that the ad was actually listed
			ad.setConfirmed(true);
		}
		
		/* Close the ad and print statistics in case this was a paid ad. When getting a "closed failed" message, then
		 * we know that the ad is definitely closed, no matter if this is a quantity = 1 type or quantity > 1 type
		 * of ad. (If quantity > 1, then it's possible that some items have been bought for this one although we now
		 * got a "closed failed". If the total sold amount < advertised amount at listing (=quantity) at the time the
		 * ad was closed at Huutonet, then we still get a "closed failed" for this one, in addition to the previously
		 * received "won buyouts" that had a cloned ad.) */
		ad.setDateFromMarket(new Date());
		if (ad.getVisibilityType() > 0 || ad.getAdvertisementCostInEuroInclVat().compareTo(BigDecimal.ZERO) > 0) {
			printStatisticsOfClosedPaidAd(ad);
		}
		
		adDAO.update(ad);
		adDAO.flush(); //do a flush in order to catch database errors right away
		return true;
	}

	/**
	 * This method prints statistics regarding paid ads that were closed.
	 * 
	 * Note: we don't print totally accurate statistics by actually checking
	 * all orders and checking final sales prices/provider prices (that wouldn't
	 * even be possible because right after closing there are unpaid orders
	 * that don't yet have any final provider price set.
	 * 
	 * Instead we approximate the profit/loss by using the buyout price of the
	 * ad + the provider price at listing time - the cost of the listing of
	 * the ad.
	 * 
	 * NOTE: we don't consider shipping costs here, so the real profit might be
	 * a little bigger if we charge our customers more for shipping than what
	 * the provider charges us.
	 * 
	 * With the following query we can easily get how profitable different display window
	 * products are:
	 * select ad_template_id, sum(bought_amount * buyout_price_euro_inc_vat - bought_amount * prov_price_at_listing_euro_inc_vat
	 * - advert_cost_euro_inc_vat) from ad where visibility_type > 0 group by ad_template_id order by sum desc;
	 * 
	 * With the following query we can see how profitable all display window products have been:
	 * select sum(bought_amount * buyout_price_euro_inc_vat - bought_amount * prov_price_at_listing_euro_inc_vat
	 * - advert_cost_euro_inc_vat) from ad where visibility_type > 0;
	 */
	private void printStatisticsOfClosedPaidAd(Ad ad) {
		int soldItems = ad.getBoughtAmount();
		int originalAmount = ad.getOriginalAmount();
		BigDecimal totalSalesPriceEuro = BigDecimal.ZERO;
		BigDecimal totalProviderPriceEuro = BigDecimal.ZERO;
		if (soldItems > 0) {
			totalSalesPriceEuro = ad.getBuyOutPriceInEuroInclVat().multiply(new BigDecimal(soldItems));
			totalProviderPriceEuro = ad.getProviderPriceAtListingTimeEuroInclVat().multiply(new BigDecimal(soldItems));
		}
		BigDecimal profit = totalSalesPriceEuro.subtract(totalProviderPriceEuro).subtract(ad.getAdvertisementCostInEuroInclVat());
		String status = "profitable";
		if (profit.compareTo(BigDecimal.ZERO) < 0) {
			status = "loss";
		}
		logger.debug("The paid ad " + ad.getId() + " (ad template " + ad.getAdTemplate().getId()
				+ ") was closed. Status: " + status + ", original amount: " + originalAmount
				+ ", sold items: " + soldItems + ", profit: " + profit.toPlainString()
				+ " (totalSalesPrice of sold items = " + totalSalesPriceEuro.toPlainString()
				+ ", totalProviderPrice of sold items = " + totalProviderPriceEuro.toPlainString()
				+ ", advertisement cost = " + ad.getAdvertisementCostInEuroInclVat().toPlainString()
				+ ", visibility type = " + ad.getVisibilityType()
				+ ", product name = \"" + ad.getAdTemplate().getHeadline() + "\").");
	}
	
	/* Processes private questions from huutonet. */
	private boolean processPrivateQuestion(String contents, Seller seller) throws Exception {
		String email = getFieldValue(contents,"Voit vastata viestiin joko suoraan sähköpostitse");
		if (email != null) {
			if (email.startsWith("(")) {
				email = email.replace("(","");
			}
			if (email.endsWith(")")) {
				email = email.replace(")","");
			}
		}
		logger.debug("email = " + email);
		int startIndex = contents.indexOf("======= =======");
		if (startIndex < 0) {
			return false;
		}
		String str = contents.substring(startIndex + "======= =======".length());
		int endIndex = str.indexOf("======= =======");
		if (endIndex < 0) {
			return false;
		}
		
		/* If the email address was found in the customer database, then information should be included in the email to the operator. */
		Customer customer = null;
		if (email != null) {
			customer = customerDAO.getCustomerByEmailAddress(email);
		}
		if (customer == null) {
			/* If email was null and/or if no customer was found, then check if we can get the user based on the login name. */
			String loginName = getFieldValue(contents,"Käyttäjätunnus");
			LoginName ln = loginNameDAO.getByLoginNameAndMarket(loginName, marketDAO.getByName(Market.HUUTONET_MARKET_NAME));
			if (ln != null) {
				customer = ln.getCustomer();
				email = customer.getEmail();
				logger.debug("We got customer from the login name (email = " + email + ")");
			}
		}
		StringBuffer strBuf = new StringBuffer();
		if (customer != null) {
			strBuf = getOrderListForCustomer(customer, seller, email, true);
		}

		String question = str.substring(0,endIndex);
		logger.debug("question = " + question);
		String forwardPrivateQuestionsTo = seller.getForwardPrivateQuestions();
		if (forwardPrivateQuestionsTo == null) {
			forwardPrivateQuestionsTo = seller.getUser().getEmailAddress();
		}
		String newEmail = "<ANSWER>\nMoi,\n\n\n\nYstävällisin terveisin,\n" + seller.getSignature() + "\n</ANSWER>\n\n"
			+ "<ACTION></ACTION>"
			+ "<QUESTION>" + question + "</QUESTION>\n\n"
			+ "<INFORMATION>The above private question has arrived\n"
			+ (email != null ? "By answering to this email the question will be automatically answered.\n\n"
					: "NOTE: NO EMAIL WAS FOUND FOR THE USER, PLEASE DEAL MANUALLY WITH THIS QUESTION!!!\n\n")
			+ strBuf.toString() + "</INFORMATION>\n\n\n"
			+ "<FROM_EMAIL_ADDRESS>" + email + "</FROM_EMAIL_ADDRESS>\n\n\n"
			+ "<OLD_EMAIL>" + contents + "</OLD_EMAIL>";
		EmailSender.sendEmailBatch(seller.getSystemEmail(),forwardPrivateQuestionsTo,
				"Private question from Huutonet (" + email + ") - " + seller.getSignature(),newEmail);
		logger.info("Private question from Huutonet received.");
		return true;
	}
	
	/* Processes public questions from huutonet. */
	private boolean processPublicQuestion(String contents, Seller seller) throws Exception {
		String itemNumber = getFieldValue(contents,"kohdetta numero");
		if (itemNumber.endsWith(",")) {
			itemNumber = itemNumber.replace(",","");
		}
		logger.debug("itemNumber = " + itemNumber);
		Market market = marketDAO.getByName(Market.HUUTONET_MARKET_NAME);
		if (market == null) {
			throw new Exception("market " + Market.HUUTONET_MARKET_NAME + " not found in database.");
		}
		Ad ad = adDAO.getByMarketSalesId(itemNumber,market,seller);
		if (ad == null) {
			/* Check if the product has moved to a different seller, and if it has, then let's still
			 * use the old seller for this one. */
			ad = adDAO.getByMarketSalesIdWithoutSeller(itemNumber,market);
			if (ad == null) {
				throw new UnprocessableEmailException("ad.marketSalesId " + itemNumber + " not found in database (for huutonet).");
			} else {
				String warnMsg = "Public question for ad " + ad.getId() + " which has moved to seller "
					+ ad.getAdTemplate().getSeller().getName() + " - using old seller " + seller.getName()
					+ " for this one.";
				logger.warn(warnMsg);
			}
		}
		logger.info("Public question from Huutonet received.");
		
		/* Fetch the question from huutonet. */
		String question = null;
		List<String> userName = new ArrayList<String>();
		try {
			SellerMarket sellerMarket = sellerMarketDAO.getBySellerAndMarket(seller,market);
			huutonetLister.login(sellerMarket);
			question = huutonetAnswerer.getQuestion(ad,sellerMarket,userName);
		} catch (MarketTemporarilyDownException e) {
			throw new ProcessEmailLaterException("Cannot fetch public question because market is down.");
		} catch (MarketLoggedOnAsWrongUserException e) {
			throw new ProcessEmailLaterException("Cannot fetch public question because market is in use by a different seller.");
		}
		huutonetLister.logout();
		if (question == null) {
			throw new UnprocessableEmailException("Couldn't fetch public question from huutonet");
		}
		logger.debug("Public question: " + question);

		//String productURL = "http://www.dealextreme.com/details.dx/" + ad.getAdTemplate().getProduct().getProviderProdId();
		//String productURL = "http://www.focalprice.com/" + ad.getAdTemplate().getProduct().getProviderProdId() + "/something.html";
		String productURL = ad.getAdTemplate().getProduct().getProviderUrl();
		String forwardPublicQuestionsTo = seller.getForwardPublicQuestions();
		if (forwardPublicQuestionsTo == null) {
			forwardPublicQuestionsTo = seller.getUser().getEmailAddress();
		}

		/* Sometimes it happens that a customer that exists in our customer database asks a public question. In this
		 * case let's show their order history in the email to the operator. */
		StringBuffer strBuf = new StringBuffer();
		LoginName ln = loginNameDAO.getByLoginNameAndMarket(userName.get(0), marketDAO.getByName(Market.HUUTONET_MARKET_NAME));
		if (ln != null) {
			logger.debug("Found customer " + ln.getCustomer().getId() + " for public question for " + ad.getMarketSalesId() + ".");
			strBuf = getOrderListForCustomer(ln.getCustomer(), seller, ln.getCustomer().getEmail(), true);
		}

		StringBuffer prodInfo = new StringBuffer();
		prodInfo.append("Offline sales: " + ad.getAdTemplate().getProduct().getOfflineSale() + "\n");
		prodInfo.append("Product state: ");
		if (ad.getAdTemplate().getProduct().getProductState().equals(Product.STATE_NO_LONGER_FOR_SALE)) {
			prodInfo.append("NO LONGER FOR SALE");
		} else if (ad.getAdTemplate().getProduct().getProductState().equals(Product.STATE_OUT_OF_STOCK)) {
			prodInfo.append("OUT OF STOCK");
		} else if (ad.getAdTemplate().getProduct().getProductState().equals(Product.STATE_IN_STOCK)) {
			prodInfo.append("in stock");
		} else if (ad.getAdTemplate().getProduct().getProductState().equals(Product.STATE_UNKNOWN)) {
			prodInfo.append("UNKNOWN");
		} else {
			throw new UnprocessableEmailException("Unknown state for product " + ad.getAdTemplate().getProduct().getId());
		}
		if (ad.getAdTemplate().getProduct().getStatusFetchDate() != null) {
			prodInfo.append(" (last fetched on " + Statics.DATE_FORMAT.format(ad.getAdTemplate().getProduct().getStatusFetchDate()) + ")\n");
		}
		if (ad.getAdTemplate().getProduct().getCurrentProviderPriceProviderCurrencyInclVat() != null) {
			prodInfo.append("Provider price: " + ad.getAdTemplate().getProduct().getCurrentProviderPriceProviderCurrencyInclVat().toPlainString());
		} else {
			prodInfo.append("Provider price: unknown");
		}
		prodInfo.append("\n");
	
		String newEmail = "<ANSWER></ANSWER>\n\n"
				+ "<QUESTION>" + question + "</QUESTION>\n\n"
				+ "<HEADLINE>" + ad.getAdTemplate().getHeadline() + "</HEADLINE>\n"
				+ (ad.getAdTemplate().getTechnicalSpecs() != null ?
						"<ADTEMPLATE_TECHNICAL_SPECS>" + ad.getAdTemplate().getTechnicalSpecs() + "</ADTEMPLATE_TECHNICAL_SPECS>\n"
						: "<PRODUCT_TECHNICAL_SPECS>" + ad.getAdTemplate().getProduct().getTechnicalSpecs() + "</PRODUCT_TECHNICAL_SPECS>\n")
				+ "<DETAILS>" + ad.getAdTemplate().getDetails() + "</DETAILS>\n"
				+ "<SUMMARY>" + ad.getAdTemplate().getSummary() + "</SUMMARY>\n\n"
				+ "Product info:\n" + prodInfo.toString() + "\n"
				+ "<INFORMATION>The above public question has arrived\n"
				+ "The product can be found at the following url: " + productURL + "\n"
				+ "By answering to this email the question will be automatically answered.\n"
				+ (ad.getVisibilityType() > 0 ? "\nNOTE: this is a DISPLAY WINDOW ad!\n\n" : "(Normal ad, not display window)\n")
				+ strBuf.toString() + "</INFORMATION>\n\n\n"
				+ "<OLD_EMAIL>" + contents + "</OLD_EMAIL>";
		EmailSender.sendEmailBatch(seller.getSystemEmail(),forwardPublicQuestionsTo,
				"Public question from Huutonet received for " + itemNumber + " - " + seller.getSignature(),newEmail);
		return true;
	}

	/* Processes messages from huutonet telling that the target is closed and that somebody
	 * bought the product (bid is won). The ad.dateFromMarket field is set to the current
	 * date and a new row is added to the order table. */
	private boolean processWonBidSingle(String contents, Seller seller) throws Exception {
		/* Safety measure: we should always be getting prices in euros from Huutonet,
		 * but make sure we bail out in case we don't. */
		Long currency = null;
		if (!matchEmailContents(contents,"EUR - kohteeseen teki k")) {
			throw new Exception("Unknown currency for huutonet won bid.");
		} else {
			currency = Currency.EURO;
		}
		String itemNumber = getFieldValue(contents,"kohde numero");
		if (itemNumber.endsWith(",")) {
			itemNumber = itemNumber.replace(",","");
		}
		logger.info("Won bid notification received from huutonet for item number \"" + itemNumber + "\"");
		BigDecimal finalSalesPriceInclVatBD = new BigDecimal(getFieldValue(contents,"Voittotarjouksen -"));
		VirtualCoin finalSalesPriceInclVatMC = new VirtualCoin(currency,finalSalesPriceInclVatBD);
		return processNewOrder(contents,itemNumber,finalSalesPriceInclVatMC,seller,1);
	}
	
	private boolean processWonBidMulti(String contents, Seller seller) throws Exception {
		/* We don't use auctions where the quantity is greater than one, so this
		 * one should never come. For an example of what these look like, search for
		 * 213510050 in the mail box of hkmyynti@gmail.com. */
		throw new UnprocessableEmailException("Not yet supported (multi-product bid for huutonet).");
	}
	
	/* Processes messages from huutonet telling that the target is closed and that somebody
	 * bought the product (osta heti). The ad.dateFromMarket field is set to the current
	 * date and a new row is added to the order table.
	 * 
	 * These emails are of two different types:
	 * 1.) One bought item (for an example of this one search for 212433955 in hkmyynti@gmail.com's mailbox)
	 * 2.) Multiple bought items (for an example of this one search for 212435229 in hkmyynti@gmail.com's mailbox)
	 * */
	private boolean processWonOstaHeti(String contents, Seller seller) throws Exception {
		/* Safety measure: we should always be getting prices in euros from Huutonet,
		 * but make sure we bail out in case we don't. */
		Long currency = null;
		if (!matchEmailContents(contents,"eur/kpl")) {
			throw new UnprocessableEmailException("Unknown currency for huutonet won bid.");
		} else {
			currency = Currency.EURO;
		}
		String itemNumber = null;
		int amount = 1;
		if (contents.indexOf("on ostanut myymäsi Osta") >= 0
				|| (contents.indexOf("on ostanut myymäsi") >= 0 && contents.indexOf("Osta heti -kohteen") >= 0)) {
			itemNumber = getFieldValue(contents,"kohteen numero");
		} else if (contents.indexOf("on ostanut") >= 0 && contents.indexOf("kpl myymääsi Osta") >= 0) {
			itemNumber = getFieldValue(contents,"kohdetta numero");
			String amountStr = getFieldValue(contents,"on ostanut");
			amount = Integer.parseInt(amountStr);
		}
		
		if (itemNumber == null) {
			throw new UnprocessableEmailException("Couldn't figure out itemNumber for email.");
		}
		if (itemNumber.endsWith(",")) {
			itemNumber = itemNumber.replace(",","");
		}
		logger.info("Won osta heti notification received from huutonet for item number \"" + itemNumber + "\"");
		BigDecimal finalSalesPriceInclVatBD = new BigDecimal(getFieldValue(contents,"Kauppahinta on"));
		VirtualCoin finalSalesPriceInclVatMC = new VirtualCoin(currency,finalSalesPriceInclVatBD);
		return processNewOrder(contents,itemNumber,finalSalesPriceInclVatMC,seller,amount);
	}
	
	private boolean processNewOrder(String contents, String marketSalesId, VirtualCoin finalSalesPriceInclVatMC, Seller seller, int amount) throws Exception {
		String names = null;
		String firstName = "";
		String lastName = null;
		String address = null;
		String postCodeAndCounty = null;
		String phone = null;
		String email = null;
		String loginName = null;

		if (amount == 1) {
			String baseRow = "Osoite:";
			names = getRowValue(contents,baseRow,1);
			address = getRowValue(contents,baseRow,2);
			postCodeAndCounty = getRowValue(contents,baseRow,3);
			phone = getRowValue(contents,baseRow,5);
			email = getRowValue(contents,baseRow,6);
			loginName = getFieldValueToEndOfRow(contents,"Tunnus:").trim();
		} else {
			/* The email looks different if the amount is greater than one. */
			// Find the loginName
			int indStart = contents.indexOf(" tunnuksella");
			int indEnd = contents.indexOf(" on ostanut");
			if (indStart >= 0 && indEnd >= 0) {
				loginName = contents.substring(indStart+" tunnuksella".length(), indEnd).trim();
			}
			// Then find the rest
			String baseRow = "(tarjosi " + getFieldValue(contents,"Kauppahinta on")/*MathUtilities.roundTo2Decimals(finalSalesPriceInclVatMC.getValue())*/ + " EUR)";
			System.out.println("baseRow = \"" + baseRow + "\"");
			names = getRowValue(contents,baseRow,1);
			logger.debug("names before = \"" + names + "\"");
			names = names.replaceAll("\\(" + loginName + "\\)","").trim();
			logger.debug("names after = \"" + names + "\"");
			email = getRowValue(contents,baseRow,2);
			address = getRowValue(contents,baseRow,3);
			//Post code and city is in opposite order for these ones
			postCodeAndCounty = getRowValue(contents,baseRow,4).trim();
			int spaceInd = postCodeAndCounty.indexOf(" ");
			logger.debug("postCodeAndCounty before = \"" + postCodeAndCounty + "\"");
			postCodeAndCounty = postCodeAndCounty.substring(spaceInd+1) + " " + postCodeAndCounty.substring(0,spaceInd);
			logger.debug("postCodeAndCounty after = \"" + postCodeAndCounty + "\"");
			phone = getRowValue(contents,baseRow,6);
			phone = phone.replaceAll("matkapuh.","").trim();
		}

		/* For Huutonet if we have a buyout (osta heti) ad that has a quantity X which is greater than one and
		 * if someone buys a quantity Y (which is less than X), then Huutonet clones the ad and then closes
		 * the cloned ad. This cloned ad naturally doesn't exist in our database, so SellStar also needs to
		 * clone the ad. This needs to be done every time we get here independent of the bought amount (one or
		 * several ones) except for the LAST item that was bought (because the last item always has the same
		 * marketSalesId as the one that was actually listed and that one exists in SellStar's database). (i.e.
		 * cloning is NEVER done if quantity at listing time was one) */
		cloneAd(marketSalesId,seller,contents,amount);
		
		/* Some people have several first names, and there can be several spaces between the names. */
		String names2 = StringUtilities.trimMultipleSpaces(names).trim();
		int spInd = names2.lastIndexOf(" ");
		if (spInd >= 0) {
			// Sometimes (very rarely) there is just one single name on the name row (someemail@email.com).
			firstName = names2.substring(0,spInd);
		}
		lastName = names2.substring(spInd+1);

		logger.debug("names = " + names + ", firstName = " + firstName + ", lastName = " + lastName);
		logger.debug("address = " + address);
		logger.debug("postCodeAndCounty = " + postCodeAndCounty);
		logger.debug("phone = " + phone);
		logger.debug("email = " + email);
		logger.debug("loginName = " + loginName);

		String[] split = postCodeAndCounty.split(" ");
		String postCode = null;
		String city = null;
		if (split.length < 2) {
			/* Some people only have postcode defined and the city is missing. */
			postCode = split[0];
			city = ""; //this will set the order into faulty contact info state
		} else if (split.length == 2) {
			/* This is the normal case. */
			postCode = split[0];
			city = split[1];
		} else if (split.length > 2) {
			/* Some customers have weird addresses, for example
			 * Etunimi Sukunimi
			 * Kivi 3 f 13
			 * 12345 Tampere 90
			 * This code attempts to resolve those ones automatically. */
			postCode = split[0];
			city = split[1];
		}
		logger.debug("postCode = " + postCode);
		logger.debug("city = " + city);
		
		Ad ad = OrderCreator.createNewOrders(firstName,lastName,address,postCode,city,
				email,phone,loginName,Market.HUUTONET_MARKET_NAME,marketSalesId,seller,amount,finalSalesPriceInclVatMC);
		if (ad.getConfirmed().booleanValue() == false) {
			//sometimes the confirmation message is lost on the way - here we know that the ad was actually listed
			ad.setConfirmed(true);
		}
		
		/* If this was an original ad (not cloned), then we know that this was either a quantity = 1 item
		 * that was sold, or else the LAST item for a quantity > 1 ad. In both cases the ad should be closed.
		 * Note: the last condition in the if case below " || orgAd.boughtAmount >= orgAd.orgAmount" shouldn't
		 * ever be necessary, but leave it just in case Huutonet doesn't always behave as expected. */
		if (ad.getOriginalAd() == null || ad.getOriginalAd().getBoughtAmount() >= ad.getOriginalAd().getOriginalAmount()) {
			ad.setDateFromMarket(new Date());
			if (ad.getVisibilityType() > 0 || ad.getAdvertisementCostInEuroInclVat().compareTo(BigDecimal.ZERO) > 0) {
				printStatisticsOfClosedPaidAd(ad);
			}
		}
		return true;
	}
	
	private void cloneAd(String marketSalesId, Seller seller, String contents, int amount) throws ProcessEmailLaterException {
		Market market = marketDAO.getByName(Market.HUUTONET_MARKET_NAME);
		SellerMarket sellerMarket = sellerMarketDAO.getBySellerAndMarket(seller, market);
		Ad ad = adDAO.getByMarketSalesId(marketSalesId,market,seller);
		if (ad != null || adDAO.getByMarketSalesIdWithoutSeller(marketSalesId,market) != null) {
			//No cloning needs to be done in this case
			logger.debug("Ad found, no cloning needs to be done.");
		} else {
			/* Now we actually need to open the page and find out the dateToMarket of the ad
			 * in question and then see if such an ad was found in the database.
			 * TODO: move this code to HuutonetLister2.java - or possibly move the whole cloneAd
			 * method to HuutonetLister2.java */
			String url = "http://www.huuto.net/kohteet/" + marketSalesId;
			logger.debug("Searching for ad to clone (" + url + ").");
			CustomWebDriver driver = seleniumRobot.getWebDriver();
			try {
				driver.get(url);
				WebElement sellerElement = driver.findElement(By.xpath("//table[3]/tbody/tr[2]/td[2]/table/tbody/tr[1]/td[2]/table/tbody/tr/td[2]/a"));
				WebElement dateToMarketHeader = driver.findElement(By.xpath("//table[3]/tbody/tr[2]/td[2]/table/tbody/tr[6]/td[1]"));
				WebElement dateToMarketElement = driver.findElement(By.xpath("//table[3]/tbody/tr[2]/td[2]/table/tbody/tr[6]/td[2]"));
				WebElement dateFromMarketElement = driver.findElement(By.xpath("//table[3]/tbody/tr[2]/td[2]/table/tbody/tr[7]/td[2]"));
				logger.debug("sellerElement = " + sellerElement.getText());
				logger.debug("dateToMarketHeader = " + dateToMarketHeader.getText());
				logger.debug("dateToMarketElement = " + dateToMarketElement.getText());
				logger.debug("dateFromMarketElement = " + dateFromMarketElement.getText());
				if (!dateToMarketHeader.getText().equals("Ilmoitettu")) {
					throw new Exception("Got wrong header: " + dateToMarketHeader.getText());
				}
				if (sellerElement.getText().indexOf(sellerMarket.getUserName()) != 0) {
					throw new Exception("Got wrong seller: " + sellerElement.getText());
				}
				Date dateToMarket = HuutonetLister2.CLOSING_DATE_FORMAT.parse(dateToMarketElement.getText());
				ad = adDAO.getByDateToMarketWithoutSeller(dateToMarket,market);
				if (ad != null) {
					/* Ok, clone this ad. */
					logger.debug("Found ad to clone: marketSalesId " + marketSalesId + " is a clone of marketSalesId " + ad.getMarketSalesId());
					Ad newAd = new Ad();
					String dateFromMarketString = dateFromMarketElement.getText();
					Date dateFromMarket = HuutonetLister2.CLOSING_DATE_FORMAT.parse(dateFromMarketString.
							substring(0, dateFromMarketString.indexOf("\n")).trim());
					newAd.setDateFromMarket(dateFromMarket);
					newAd.setDateToMarket(ad.getDateToMarket());
					newAd.setMarketSalesId(marketSalesId);
					newAd.setUsedBeanName(ad.getUsedBeanName());
					newAd.setUsedBeanVersion(ad.getUsedBeanVersion());
					newAd.setMarket(ad.getMarket());
					newAd.setAdTemplate(ad.getAdTemplate());
					newAd.setConfirmed(ad.getConfirmed());
					newAd.setSalesType(ad.getSalesType());
					newAd.setDateClosing(ad.getDateClosing());
					newAd.setMarketCategory(ad.getMarketCategory());
					newAd.setStartPriceInMarketCurrencyInclVat(ad.getStartPriceInMarketCurrencyInclVat());
					newAd.setStartPriceInEuroInclVat(ad.getStartPriceInEuroInclVat());
					newAd.setBuyOutPriceInEuroInclVat(ad.getBuyOutPriceInEuroInclVat());
					newAd.setMinSellingPriceInMarketCurrencyInclVat(ad.getMinSellingPriceInMarketCurrencyInclVat());
					newAd.setMinSellingPriceInEuroInclVat(ad.getMinSellingPriceInEuroInclVat());
					newAd.setProviderPriceAtListingTimeProviderCurrencyInclVat(ad.getProviderPriceAtListingTimeProviderCurrencyInclVat());
					newAd.setProviderPriceAtListingTimeEuroInclVat(ad.getProviderPriceAtListingTimeEuroInclVat());
					newAd.setShippingCostInMarketCurrencyInclVat(ad.getShippingCostInMarketCurrencyInclVat());
					newAd.setShippingCostInEuroInclVat(ad.getShippingCostInEuroInclVat());
					newAd.setNumberOfPublicQuestions(0);
					newAd.setBuyOutPriceInMarketCurrencyInclVat(ad.getBuyOutPriceInMarketCurrencyInclVat());
					//newAd.setVisibilityType(ad.getVisibilityType());
					newAd.setVisibilityType(0);
					newAd.setOriginalAd(ad);
					newAd.setOpenDays(0);
					newAd.setAdvertisementCostInEuroExclVat(BigDecimal.ZERO);
					newAd.setAdvertisementCostInEuroInclVat(BigDecimal.ZERO);
					newAd.setTransferedToVerification(false);
					
					/* Note: set original amount and bought amount of the cloned ad to zero. This is
					 * done for statistical purposes. We aren't interested in how many bought items
					 * we got for cloned ads, rather we want to know how many bought items we got for
					 * the original ad (and that's set in OrderCreator). */
					newAd.setOriginalAmount(0);
					newAd.setBoughtAmount(0);
					adDAO.save(newAd);
				} else {
					logger.error("Found no ad to clone for marketSalesId " + marketSalesId
							+ " (" + url + " not found in the database).");
					EmailSender.sendOperationProblemReport("Found no ad to clone",
							"Found no ad to clone for marketSalesId " + marketSalesId + " (" + url
							+ " not found in the database). Please examine if there are such API"
							+ " listings done for which we couldn't fetch the dateToMarket and if"
							+ " so fix these by hand.\n\nNOTE: this purchase attempt will subsequently"
							+ " fail with a null pointer exception (will work once the faulty"
							+ " dateToMarket is fixed if the email is left in the inbox).");
				}
			} catch (Exception e) {
				logger.debug("Failed getting the dateToMarket date for unknown ad " + marketSalesId
						+ ". Trying again later.",e);
				throw new ProcessEmailLaterException("Failed getting the dateToMarket date for unknown ad " + marketSalesId);
			}
		}
	}

	private boolean processAnswerToPrivateQuestion(String contents, String operatorEmailAddress, Seller seller) throws Exception {
		String question = getTagValue(contents, "QUESTION");
		if (question == null) {
			return false;
		}
		logger.debug("question = " + question);
		String answer = getTagValue(contents, "ANSWER");
		if (answer == null) {
			return false;
		}
		logger.debug("answer = " + answer);
		String oldEmail = getTagValue(contents, "OLD_EMAIL");
		if (oldEmail == null) {
			return false;
		}
		String email = getTagValue(contents, "FROM_EMAIL_ADDRESS");
		if (email == null) {
			return false;
		} 
		email = StringUtilities.replaceNewLinesWithSpace(email).replaceAll(" ","");
		logger.debug("email = " + email);
		logger.info("Answer to private question from Huutonet received.");
		if (email.equals("null")) {
			String newEmail = "Email for the private question wasn't found so it's not possible to automatically\n"
				+ "answer this question. Please deal with the question manually.\n\n"
				+ "Old email:\n" + oldEmail;
			EmailSender.sendEmailBatch(seller.getSystemEmail(),operatorEmailAddress,"Cannot answer private question (email missing)",newEmail);
			return true;
		}

		/* Make a check in order to avoid sending empty answers to the customers. */
		boolean answered = true;
		if (StringUtilities.sloppyMatch("",answer.replaceAll("Moi","").replaceAll("Ystävällisin terveisin","").replaceAll(seller.getSignature(),""))) {
			String newEmail = "You tried to send an empty answer to a private question from " + email + ".\n"
				+ "Remember that the answer MUST be within the <ANSWER></ANSWER> tag!\n\n"
				+ "Please answer again!!";
			EmailSender.sendEmailBatch(seller.getSystemEmail(),operatorEmailAddress,"Cannot send empty answer to private question from Huutonet",newEmail);
		} else {
			String newEmail = answer
				+ "\n\n\n"
				+ "----------------\n"
				+ oldEmail;
			Market market = marketDAO.getByName(Market.HUUTONET_MARKET_NAME);
			SellerMarket sellerMarket = sellerMarketDAO.getBySellerAndMarket(seller,market);
			//no need to login/logout because for huutonet private questions are answered by sending an email
			answered = huutonetLister.answerPrivateQuestion("Vastaus kyselyyn", question, newEmail, email, sellerMarket);

			if (!answered) {
				throw new UnprocessableEmailException("Couldn't answer private question from huutonet");
			}
		}
		return answered;
	}
	
	private boolean processAnswerToPublicQuestion(String contents, String operatorEmailAddress, Seller seller) throws Exception {
		String itemNumber = getFieldValue(contents,"kohdetta numero");
		if (itemNumber.endsWith(",")) {
			itemNumber = itemNumber.replace(",","");
		}

		Market market = marketDAO.getByName(Market.HUUTONET_MARKET_NAME);
		if (market == null) {
			throw new Exception("market " + Market.HUUTONET_MARKET_NAME + " not found in database.");
		}
		Ad ad = adDAO.getByMarketSalesId(itemNumber,market,seller);
		if (ad == null) {
			/* Check if the product has moved to a different seller, and if it has, then let's still
			 * use the old seller for this one. */
			ad = adDAO.getByMarketSalesIdWithoutSeller(itemNumber,market);
			if (ad == null) {
				throw new UnprocessableEmailException("ad.marketSalesId " + itemNumber + " not found in database.");
			} else {
				String warnMsg = "Public question for ad " + ad.getId() + " which has moved to seller "
					+ ad.getAdTemplate().getSeller().getName() + " - using old seller " + seller.getName()
					+ " for this one.";
				logger.warn(warnMsg);
			}
		}
		AdTemplate adTemplate = ad.getAdTemplate();

		logger.debug("itemNumber = " + itemNumber);
		String url = getFieldValue(contents,"Voit lukea kysymyksen ja vastata siihen Huuto.netin sivulla");
		logger.debug("url = " + url);
		String answer = this.getTagValue(contents,"ANSWER");
		if (answer == null) {
			return false;
		}
		logger.debug("answer = " + answer);
		logger.info("Answer to public question received.");

		/* Make a check in order to avoid sending empty answers to public questions. */
		boolean answered = true;
		if (StringUtilities.sloppyMatch("",answer.replaceAll("Moi","").replaceAll("Ystävällisin terveisin","").replaceAll(seller.getSignature(),""))) {
			String newEmail = "You tried to send an empty answer to a public question for marketSalesId " + itemNumber + ".\n"
				+ "Remember that the answer MUST be within the <ANSWER></ANSWER> tag!\n\n"
				+ "Please answer again!!";
			EmailSender.sendEmailBatch(seller.getSystemEmail(),operatorEmailAddress,"Cannot send empty answer to public question",newEmail);
		} else {
			String question = this.getTagValue(contents,"QUESTION");
			if (question == null)
				return false;
			logger.debug("question = " + question);
			
			// Saves the new specifications to the AdTemplate
			adTemplateDAO.refresh(adTemplate);
			
			String headline = this.getTagValue(contents,"HEADLINE");
			String adTemplateTechnicalSpecs = this.getTagValue(contents,"ADTEMPLATE_TECHNICAL_SPECS");
			String productTechnicalSpecs = this.getTagValue(contents,"PRODUCT_TECHNICAL_SPECS");
			String details = this.getTagValue(contents,"DETAILS");
			String summary = this.getTagValue(contents,"SUMMARY");
			
			if (headline != null) {
				adTemplate.setHeadline(StringUtilities.replaceNewLinesWithSpace(headline));
			}
			if (adTemplateTechnicalSpecs != null) {
				adTemplate.setTechnicalSpecs(adTemplateTechnicalSpecs);
			}
			if (productTechnicalSpecs != null) {
				adTemplate.getProduct().setTechnicalSpecs(productTechnicalSpecs);
			}
			if (details != null) {
				adTemplate.setDetails(details);
			}
			if (summary != null) {
				adTemplate.setSummary(summary);
			}
			
			adTemplateDAO.update(adTemplate);
			adTemplateDAO.flush();
			answered = false;
			try {
				SellerMarket sellerMarket = sellerMarketDAO.getBySellerAndMarket(seller,market);
				huutonetLister.login(sellerMarket);
				answered = huutonetLister.answerPublicQuestion(ad, question, answer, null, sellerMarket);
			} catch (MarketTemporarilyDownException e) {
				throw new ProcessEmailLaterException("Cannot answer public question because market is down.");
			} catch (MarketLoggedOnAsWrongUserException e) {
				throw new ProcessEmailLaterException("Cannot answer public question because market is in use by a different seller.");
			}
			huutonetLister.logout();
		}
		if (!answered) {
			throw new UnprocessableEmailException("Couldn't answer public question from huutonet");
		}
		return answered;
	}
	
	@Override
	public boolean processSellerEmail(Message message, Seller seller,
			CriticalSectionInfo processingInfo) throws Exception {
		/* Check if it's an ad received message from Huutonet. */
		String contents = matchEmail(message,HUUTONET_EMAIL_ADDRESS,"Kohde numero ",true,AD_RECEIVED_ID_STRING);
		if (contents != null) {
			return processAdReceived(contents,seller);
		}

		/* Check if it's an ad closed unsuccessfully message from Huutonet that didn't get any bids. */
		contents = matchEmail(message,HUUTONET_EMAIL_ADDRESS,"Huutokauppa ",true,AD_CLOSED_UNSUCCESSFULLY_ID_STRING1);
		if (contents != null) {
			return processAdClosedFail(contents,seller,true,processingInfo);
		}

		/* Check if it's an ad closed unsuccessfully message from Huutonet that did
		 * get bids but no bid reached the reserved price (varaushinta). */
		contents = matchEmail(message,HUUTONET_EMAIL_ADDRESS,"Huutokauppa ",true,AD_CLOSED_UNSUCCESSFULLY_ID_STRING2);
		if (contents != null) {
			return processAdClosedFail(contents,seller,false,processingInfo);
		}

		/* Check if it's a won bid from Huutonet (for one product). */
		contents = matchEmail(message,HUUTONET_EMAIL_ADDRESS,"Huutokauppa ",true,WON_SINGLE_BID_ID_STRING);
		if (contents != null) {
			return processWonBidSingle(contents,seller);
		}

		/* Check if it's a won bid from Huutonet (for quantity greater than one). */
		contents = matchEmail(message,HUUTONET_EMAIL_ADDRESS,"Huutokauppa ",true,WON_MULTI_BID_ID_STRING);
		if (contents != null) {
			return processWonBidMulti(contents,seller);
		}

		/* Check if it's a "osta heti" from Huutonet. */
		contents = matchEmail(message,HUUTONET_EMAIL_ADDRESS,"Huuto.net osta heti",true,WON_OSTA_HETI_ID_STRING);
		if (contents != null) {
			return processWonOstaHeti(contents,seller);
		}

		/* Check if it's a "private message from user" from Huutonet.
		 * Processes messages from huutonet telling that some user sent a question about a product. */
		contents = matchEmail(message,HUUTONET_NOREPLY_ADDRESS,"Huuto.net: viesti k",true,MESSAGE_FROM_USER_ID_STRING);
		if (contents != null) {
			return processPrivateQuestion(contents,seller);
		}

		/* Check if it's a "public question" from Huutonet. */
		contents = matchEmail(message,HUUTONET_EMAIL_ADDRESS,"Sinulle on esitetty kysymys kohteesta",true,PUBLIC_QUESTION_ID_STRING);
		if (contents != null) {
			return processPublicQuestion(contents,seller);
		}
		
		/* Check if this is an email from operator that has to do with Huutonet. */
		List<User> operators = userDAO.getOperators();
		for (User oper : operators) {
			String email = oper.getEmailAddress();
			
			/* Check if an answer to a private question from an operator. */
			contents = matchEmail(message,email,"Private question from Huutonet",false,null);
			if (contents != null) {
				return processAnswerToPrivateQuestion(contents,email,seller);
			}

			/* Check if an answer to a public question from an operator. */
			contents = matchEmail(message,email,"Public question from Huutonet",false,null);
			if (contents != null) {
				return processAnswerToPublicQuestion(contents,email,seller);
			}

			/* Check if it's a fake "osta heti" from operator. It has happened (very rarely) that after
			 * a won bid huutonet sent a message to the buyer but not to SellStar (or else the email was
			 * sent out but got lost on the way). If that happens, then the operator can send a fake
			 * "osta heti" email in order to correct the situation. */
			contents = matchEmail(message,email,"Huuto.net osta heti",true,WON_OSTA_HETI_ID_STRING);
			if (contents != null) {
				logger.info("Fake osta-heti received");
				return processWonOstaHeti(contents,seller);
			}
			/* Do a similar check for fake "won bid" emails. */
			contents = matchEmail(message,email,"Huutokauppa ",true,WON_SINGLE_BID_ID_STRING);
			if (contents != null) {
				return processWonBidSingle(contents,seller);
			}

		}

		return false;
	}

}
