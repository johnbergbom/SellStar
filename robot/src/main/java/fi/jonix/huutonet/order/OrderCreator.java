package fi.jonix.huutonet.order;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import fi.jonix.huutonet.bank.BankUtils;
import fi.jonix.huutonet.domain.ApplicationContextPlaceholder;
import fi.jonix.huutonet.domain.VirtualCoin;
import fi.jonix.huutonet.domain.model.Ad;
import fi.jonix.huutonet.domain.model.Currency;
import fi.jonix.huutonet.domain.model.Customer;
import fi.jonix.huutonet.domain.model.LoginName;
import fi.jonix.huutonet.domain.model.Market;
import fi.jonix.huutonet.domain.model.Order;
import fi.jonix.huutonet.domain.model.OrderBundle;
import fi.jonix.huutonet.domain.model.Product;
import fi.jonix.huutonet.domain.model.Provider;
import fi.jonix.huutonet.domain.model.Seller;
import fi.jonix.huutonet.domain.model.SellerMarket;
import fi.jonix.huutonet.domain.model.StateChange;
import fi.jonix.huutonet.domain.model.dao.AdDAO;
import fi.jonix.huutonet.domain.model.dao.CurrencyDAO;
import fi.jonix.huutonet.domain.model.dao.CustomerDAO;
import fi.jonix.huutonet.domain.model.dao.LoginNameDAO;
import fi.jonix.huutonet.domain.model.dao.MarketDAO;
import fi.jonix.huutonet.domain.model.dao.MessageDAO;
import fi.jonix.huutonet.domain.model.dao.OrderBundleDAO;
import fi.jonix.huutonet.domain.model.dao.OrderDAO;
import fi.jonix.huutonet.domain.model.dao.SellerMarketDAO;
import fi.jonix.huutonet.domain.model.dao.SettingsDAO;
import fi.jonix.huutonet.email.EmailSender;
import fi.jonix.huutonet.exception.ProcessEmailLaterException;
import fi.jonix.huutonet.exception.ProductNotFoundAtProviderException;
import fi.jonix.huutonet.exception.ProviderTemporarilyDownException;
import fi.jonix.huutonet.exception.SoldOutAtProviderException;
import fi.jonix.huutonet.exception.UnprocessableEmailException;
import fi.jonix.huutonet.market.BaseAdCreator;
import fi.jonix.huutonet.market.ListerStrategy;
import fi.jonix.huutonet.market.ListerTools;
import fi.jonix.huutonet.order.ContactInfoChecker.AddressFault;
import fi.jonix.huutonet.order.ProviderHandler.ProductInfo;
import fi.jonix.huutonet.tools.CurrencyFormatter;
import fi.jonix.huutonet.tools.MathUtilities;
import fi.jonix.huutonet.tools.Statics;
import fi.jonix.huutonet.tools.StringUtilities;

/**
 * This class creates a new order.
 * 
 * @author john
 *
 */
public class OrderCreator {

	public static final Logger logger = Logger.getLogger(OrderCreator.class);

	public static Ad createNewOrders(String firstName, String lastName, String address, String postCode, String city,
			String customerEmail, String phone, String loginName, String marketName, String marketSalesId, Seller seller,
			int amount, VirtualCoin finalSalesPriceInclVatMC) throws Exception {
		/* First of all validate the email address. */
		String emailAddressError = ContactInfoChecker.checkEmail(customerEmail);
		if (emailAddressError != null) {
			throw new UnprocessableEmailException("Faulty email address of customer: " + emailAddressError);
		}
		
		/* Then get the necessary spring beans. */
		MarketDAO marketDAO = (MarketDAO) ApplicationContextPlaceholder.applicationContext.getBean("marketDAO");
		AdDAO adDAO = (AdDAO) ApplicationContextPlaceholder.applicationContext.getBean("adDAO");
		MessageDAO messageDAO = (MessageDAO) ApplicationContextPlaceholder.applicationContext.getBean("messageDAO");

		/* Then make sure that the marketSalesId is valid (figure out correct ad) as well as warn the
		 * operator if the seller has changed. */
		Market market = marketDAO.getByName(marketName);
		if (market == null) {
			throw new Exception("market " + marketName + " not found in database.");
		}
		Ad ad = adDAO.getByMarketSalesId(marketSalesId,market,seller);
		if (ad == null) {
			/* Check if the product has moved to a different seller, and if it has, then let's still use the old seller
			 * for this one (it would mess up the customer if he would get a "thx for the order" from a _different_
			 * seller than what he expected). */
			ad = adDAO.getByMarketSalesIdWithoutSeller(marketSalesId,market);
			if (ad != null) {
				String warnMsg = "Order for product " + ad.getAdTemplate().getProduct().getId() + " going to old seller "
					+ seller.getName() + " although this product has moved to seller " + ad.getAdTemplate().getSeller().getName();
				logger.warn(warnMsg);
				EmailSender.sendOperationProblemReport("GoogleOrder.processOrderReceived",warnMsg);
			}
		}
		if (ad == null) {
			/* The customer has specified a non-existing marketSalesId. */
			String faultyMarketSalesIdEmail = messageDAO.getText("faultyMarketSalesIdEmail", seller, market.getLanguage(),
					"MARKET_SALES_ID",marketSalesId,
					"SIGNATURE",seller.getSignature());
			EmailSender.sendEmailBatch(seller.getSystemEmail(),customerEmail,"Virheellinen kohteen tunnus",faultyMarketSalesIdEmail);
			return null;
		} else if (ad.getAdTemplate().getActive().booleanValue() == false || ad.getAdTemplate().getProduct().getCompensation().booleanValue() == true
				|| ad.getAdTemplate().getProduct().getProductState().equals(Product.STATE_NO_LONGER_FOR_SALE)) {
			if (finalSalesPriceInclVatMC != null //finalSalesPrice is null when calling this from google-order
					&& !ad.getAdTemplate().getProduct().getProductState().equals(Product.STATE_NO_LONGER_FOR_SALE)) {
				/* Note: allow the purchase even if the adtemplate is marked as inactive. Because basically an adtemplate
				 * should still be active until all of its corresponding ads have been closed. Same thing goes when
				 * product.compensation is true. */
				if (ad.getAdTemplate().getActive().booleanValue() == false) {
					logger.info("Allowing purchase for inactive adtemplate " + ad.getAdTemplate().getId() + ".");
				}
				if (ad.getAdTemplate().getProduct().getCompensation().booleanValue() == true) {
					logger.info("Allowing purchase for compensation adtemplate " + ad.getAdTemplate().getId() + ".");
				}
			} else {
				/* The product no longer exists or is no longer active. Inform the customer that the product is no longer for sale. */
				logger.debug("Disallowing purchase of ad " + ad.getId() + " since ad template is inactive"
						+ " or the product is no longer for sale at provider.");
				String notForSaleEmail = null;
				if (finalSalesPriceInclVatMC == null) { //finalSalesPrice is null when calling this from google-order
					notForSaleEmail = messageDAO.getText("noLongerForSale", seller, ad.getAdTemplate().getLanguage(),
							"MARKET_SALES_ID",marketSalesId,
							"AD_HEADLINE",ad.getAdTemplate().getHeadline(),
							"SIGNATURE",seller.getSignature());
				} else {
					notForSaleEmail = messageDAO.getText("noLongerForSalePurchaseFromMarket", seller, ad.getAdTemplate().getLanguage(),
							"MARKET_SALES_ID",marketSalesId,
							"AD_HEADLINE",ad.getAdTemplate().getHeadline(),
							"SIGNATURE",seller.getSignature());
				}
				EmailSender.sendEmailBatch(seller.getSystemEmail(),customerEmail,"Tuote ei ole enää myynnissä",notForSaleEmail);
				return ad;
			}
		}
		createNewOrders(firstName,lastName,address,postCode,city,
				customerEmail,phone,loginName,ad,seller,amount,finalSalesPriceInclVatMC);
		return ad;
	}
	
	private static void createNewOrders(String firstName, String lastName, String address, String postCode, String city,
			String customerEmail, String phone, String loginName, Ad ad, Seller seller,
			int amount, VirtualCoin finalSalesPriceInclVatMC) throws Exception {
		/* First get the necessary spring beans. */
		MessageDAO messageDAO = (MessageDAO) ApplicationContextPlaceholder.applicationContext.getBean("messageDAO");
		SellerMarketDAO sellerMarketDAO = (SellerMarketDAO) ApplicationContextPlaceholder.applicationContext.getBean("sellerMarketDAO");
		ListerStrategy strategy = (ListerStrategy) ApplicationContextPlaceholder.applicationContext.getBean("strategy");
		BaseAdCreator baseAdCreator = (BaseAdCreator) ApplicationContextPlaceholder.applicationContext.getBean("baseAdCreator");
		ProviderHandler providerHandler = (ProviderHandler) ApplicationContextPlaceholder.applicationContext.getBean("providerHandler");
		
		String providerProdId = ad.getAdTemplate().getProduct().getProviderProdId();
		boolean fromGoogleOrder = (finalSalesPriceInclVatMC == null); //finalSalesPrice is null when calling this from google-order

		/* Check if the product still exists at provider + if it's is out of stock. If this new order comes
		 * from google-order, then use checkDataBase = true, otherwise use checkDataBase = false. */
		boolean outOfStock = false;
		ProductInfo prodInfo = providerHandler.getProductInfo(ad.getAdTemplate().getProduct(), fromGoogleOrder);
		if (prodInfo.getProductState().equals(Product.STATE_UNKNOWN)) {
			logger.warn("Provider temporarily down, or else the product is no longer for sale at provider, but the not found counter isn't yet great enough.");
			throw new ProcessEmailLaterException("New order received from google-order, but the price cannot"
					+ " be determined because provider is temporarily down (or possibly no longer for sale).");
		} else if (prodInfo.getProductState().equals(Product.STATE_NO_LONGER_FOR_SALE)) {
			String notForSaleEmail = null;
			if (fromGoogleOrder) {
				notForSaleEmail = messageDAO.getText("noLongerForSale", seller, ad.getAdTemplate().getLanguage(),
						"MARKET_SALES_ID",ad.getMarketSalesId(),
						"AD_HEADLINE",ad.getAdTemplate().getHeadline(),
						"SIGNATURE",seller.getSignature());
			} else {
				notForSaleEmail = messageDAO.getText("noLongerForSalePurchaseFromMarket", seller, ad.getAdTemplate().getLanguage(),
						"MARKET_SALES_ID",ad.getMarketSalesId(),
						"AD_HEADLINE",ad.getAdTemplate().getHeadline(),
						"SIGNATURE",seller.getSignature());
			}
			EmailSender.sendEmailBatch(seller.getSystemEmail(),customerEmail,"Tuote ei ole enää myynnissä",notForSaleEmail);
			return;
		} else if (prodInfo.getProductState().equals(Product.STATE_OUT_OF_STOCK)) {
			logger.warn("Sold out.");
			//String unacceptedEmail = "Moi,\n\nTuote " + (providerStr != null ? providerProdId : ad.getAdTemplate().getHeadline())
			if (fromGoogleOrder) {
				String unacceptedEmail = "Moi,\n\nTuote " + ad.getAdTemplate().getHeadline()
					+ " on loppunut varastolta eikä voi sen takia ottaa tilausta vastaan.\n\n"
					+ "Ystävällisin terveisin,\n" + seller.getSignature();
				EmailSender.sendEmailBatch(seller.getSystemEmail(),customerEmail,"Tuote " + ad.getMarketSalesId() + " on loppunut varastolta",unacceptedEmail);
				return;
			}
			outOfStock = true;
		}
		
		Currency usedCustomerCurrency = ad.getMarket().getCurrency();
		SellerMarket sellerMarket = sellerMarketDAO.getBySellerAndMarket(seller, ad.getMarket());
		BigDecimal maxBundleSizeExclVatMC = sellerMarket.getMaxBundleSizeInMarketCurrencyExclVat();
		
		/* Figure out the final sales price and the shipping cost. */
		//BigDecimal shippingCostEuro = null;
		VirtualCoin shippingCostInclVatMC = null;
		if (fromGoogleOrder) {
			/* finalSalesPrice is null when calling this from google-order and in that case the
			 * product price should be calculated according to the current provider price. Here
			 * we use the same logic for calculating the final sales price as what the lister
			 * uses in BaseAdCreator. */
			BigDecimal minSellingPriceInclVatMC = baseAdCreator.calculateMinSellingPrice(sellerMarket,
					prodInfo.getCurrentPrice(), ad.getAdTemplate().getProduct().getProvider().getVatPercentage());
			finalSalesPriceInclVatMC = new VirtualCoin(usedCustomerCurrency,minSellingPriceInclVatMC);
			shippingCostInclVatMC = strategy.getCustomerShippingCostInclVat(ad.getAdTemplate().getProduct(),sellerMarket);
		} else {
			/* If this is an ad that's closing, then the price should be gotten from the ad itself. */
			shippingCostInclVatMC = new VirtualCoin(usedCustomerCurrency,ad.getShippingCostInMarketCurrencyInclVat());
		}

		/* Error check. */
		if (!usedCustomerCurrency.getId().equals(finalSalesPriceInclVatMC.getCurrency())) {
			throw new RuntimeException("usedCustomerCurrency doesn't match currency of finalSalesPriceMC");
		}

		/* Don't allow this purchase if the VAT/customs fee limit is exceeded. Note that
		 * this should never get triggered for purchases coming from a normal market place
		 * because we don't even list items that are too expensive. However this can be
		 * triggered when the customer buys something through Google-order. */
		BigDecimal totalPriceInclVatMC = finalSalesPriceInclVatMC.getValue().add(shippingCostInclVatMC.getValue());
		BigDecimal totalPriceExclVatMC = MathUtilities.getPriceExclVat(totalPriceInclVatMC, seller.getCustomerVatPercentage());
		if (totalPriceExclVatMC.compareTo(maxBundleSizeExclVatMC) > 0) {
			logger.warn("Purchase not allowed for product " + providerProdId + " since price is too high!");
			VirtualCoin totalPriceInclVatMCVC = new VirtualCoin(Currency.EURO,totalPriceInclVatMC);
			VirtualCoin totalPriceExclVatMCVC = new VirtualCoin(Currency.EURO,totalPriceExclVatMC);
			String unacceptedEmail = "Moi,\n\nTuotteen " + ad.getAdTemplate().getHeadline()
				+ " hinta on liian korkea (alvillinen hinta on " + CurrencyFormatter.print(totalPriceInclVatMCVC, Statics.FINNISH)
				+ " postikuluineen ja ilman alvia " + CurrencyFormatter.print(totalPriceExclVatMCVC, Statics.FINNISH)
				+ ") - ALV/tulli-raja on ylittynyt eikä voi sen takia ottaa tilausta vastaan.\n\n"
				+ "Ystävällisin terveisin,\n" + seller.getSignature();
			EmailSender.sendEmailBatch(seller.getSystemEmail(),customerEmail,"Tulli/ALV-raja on ylittynyt",unacceptedEmail);
			return;
		}
		
		/* Generate the actual order(s). */
		for (int i = 0; i < amount; i++) {
			OrderCreator.createSingleNewOrder(firstName,lastName,address,postCode,city,customerEmail,phone,loginName,
					ad,finalSalesPriceInclVatMC,shippingCostInclVatMC,seller,outOfStock,maxBundleSizeExclVatMC,fromGoogleOrder);
		}
	}
	
	private static boolean tryToBundle(Provider provider) {
		if (!provider.getBundleSupport()) {
			return false;
		}
		logger.info("Try to bundle order.");
		return true;
	}
	
	private static void createSingleNewOrder(String firstName, String lastName, String address, String postCode, String city,
			String email, String phone, String loginName, Ad ad, VirtualCoin finalSalesPriceInclVatMC,
			VirtualCoin shippingCostInclVatMC, Seller seller, boolean outOfStock, BigDecimal maxBundleSizeExclVatMC,
			boolean fromGoogleOrder) throws Exception {
		/* First fetch the needed DAO's. */
		CustomerDAO customerDAO = (CustomerDAO) ApplicationContextPlaceholder.applicationContext.getBean("customerDAO");
		OrderDAO orderDAO = (OrderDAO) ApplicationContextPlaceholder.applicationContext.getBean("orderDAO");
		LoginNameDAO loginNameDAO = (LoginNameDAO) ApplicationContextPlaceholder.applicationContext.getBean("loginNameDAO");
		OrderBundleHandler orderBundleHandler = (OrderBundleHandler) ApplicationContextPlaceholder.applicationContext.getBean("orderBundleHandler");
		CurrencyDAO currencyDAO = (CurrencyDAO) ApplicationContextPlaceholder.applicationContext.getBean("currencyDAO");

		/* Update ad statistics. If this is a cloned ad, then we should update the bought amount
		 * of the original ad rather than the bought amount of the cloned ad. */
		if (ad.getOriginalAd() != null) {
			Ad originalAd = ad.getOriginalAd();
			logger.debug("Increasing bought amount for originalAd " + originalAd.getId() + " (cloned ad = " + ad.getId()
					+ ", adTemplate = " + originalAd.getAdTemplate().getId() + ") from "
					+ originalAd.getBoughtAmount() + " to " + (originalAd.getBoughtAmount()+1));
			originalAd.setBoughtAmount(originalAd.getBoughtAmount()+1);
		} else {
			if (ad.getVisibilityType() > 0 || ad.getAdvertisementCostInEuroInclVat().compareTo(BigDecimal.ZERO) > 0) {
				logger.debug("Increasing bought amount for ad " + ad.getId() + " (adTemplate = "
						+ ad.getAdTemplate().getId() + ") from " + ad.getBoughtAmount() + " to " + (ad.getBoughtAmount()+1));
			}
			ad.setBoughtAmount(ad.getBoughtAmount()+1);
		}
		
		/* Trim customer information strings. */
		if (email != null) {
			email = email.trim();
		}
		if (phone != null) {
			phone = phone.trim();
		}
		if (loginName != null) {
			loginName = loginName.trim();
		}
		
		/* Then to a few simple checks and set some variables. */
		if (email == null || email.trim().equals("")) {
			throw new Exception("Email address cannot be empty.");
		}
		if (shippingCostInclVatMC.getValue().doubleValue() < 0 || shippingCostInclVatMC.getValue().doubleValue() > 15) {
			throw new Exception("Faulty shipping cost.");
		}
		email = email.toLowerCase();
		String referenceNumber = BankUtils.getSecurityCode(MathUtilities.getRandomLongNumber(12));
		address = address.replaceAll(" +"," "); //turn multiple successive spaces into one

		/* Then create/update the customer information. */
		LoginName ln = null;
		Customer customer = customerDAO.getCustomerByEmailAddress(email);
		if (customer == null) {
			/* If the customer has changed email address, then we can still search for him
			 * by searching by login name. */
			if (loginName != null && !loginName.equals("")) {
				ln = loginNameDAO.getByLoginNameAndMarket(loginName, ad.getMarket());
			}
			if (ln == null) {
				logger.debug("Creating new customer for " + email);
				customer = new Customer();
				customer.setConfirmationCode(MathUtilities.getRandomLongNumber(12));
			} else {
				logger.debug("Customer not found in database for email address " + email
						+ " but was found with login name " + loginName);
				customer = ln.getCustomer();
			}
		} else {
			logger.debug("Customer found in database for email address " + email);
			ln = loginNameDAO.getByCustomerAndMarket(customer,ad.getMarket());
		}
		customer.setDateKicked(new Date()); //always zero this one when a new order is placed
		customer.setNbrTimesKicked(0); //always zero this one when a new order is placed
		customer.setInformedAboutTooBigPayment(false); //always zero this one when a new order is placed
		customer.setPhoneNumber(phone);
		customer.setEmail(email);
		customerDAO.save(customer);

		if (loginName != null && !loginName.equals("")) {
			if (ln == null) {
				ln = new LoginName();
				ln.setCustomer(customer);
				ln.setMarket(ad.getMarket());
			}
			ln.setLoginName(loginName);
			loginNameDAO.save(ln);
		}
		
		/* Then create the new order. */
		Order order = new Order();
		order.setMarket(ad.getMarket());
		//order.setProviderProdId(providerProdId);
		order.setProduct(ad.getAdTemplate().getProduct());
		//order.setDateKicked(new Date()); //set this in order to make sure that the customer isn't reminded right away about missing payment
		order.setAd(ad);
		order.setFeedbackGiven(false);
		order.setCustomerReferenceNumber(referenceNumber);
		order.setTargetedPaidInMarketCurrency(BigDecimal.ZERO);
		order.setOtherLossInEuro(BigDecimal.ZERO);
		order.setFinalSalesPriceInMarketCurrencyInclVat(finalSalesPriceInclVatMC.getValue());
		BigDecimal finalSalesPriceExclVatMC = MathUtilities.getPriceExclVat(finalSalesPriceInclVatMC.getValue(), seller.getCustomerVatPercentage());
		order.setFinalSalesPriceInMarketCurrencyExclVat(finalSalesPriceExclVatMC);
		BigDecimal finalSalesPriceInclVatEuro = currencyDAO.convertCurrency(finalSalesPriceInclVatMC.getCurrency(), Currency.EURO, finalSalesPriceInclVatMC.getValue());
		BigDecimal finalSalesPriceExclVatEuro = currencyDAO.convertCurrency(finalSalesPriceInclVatMC.getCurrency(), Currency.EURO, finalSalesPriceExclVatMC);
		order.setFinalSalesPriceInEuroInclVat(finalSalesPriceInclVatEuro);
		order.setFinalSalesPriceInEuroExclVat(finalSalesPriceExclVatEuro);
		order.setRefundingMarketCurrency(BigDecimal.ZERO);
		order.setSendCount(0);
		order.setResendLossInEuroInclVat(BigDecimal.ZERO);
		order.setResendLossInEuroExclVat(BigDecimal.ZERO);
   		order.setCustomerVatPercentage(seller.getCustomerVatPercentage());
   		order.setTransferedToVerification(false);
   		order.setOldVerification(false);
   		order.setGoogleOrder(fromGoogleOrder);
		
		/* Some people have a faulty address specified at huutonet. Here try to determine if the
		 * address is faulty and if so, then set the state to faulty address. */
		address = ContactInfoChecker.fixAddress(address);
		postCode = ContactInfoChecker.fixPostCode(postCode);
		List<AddressFault> faultyContactInfo = ContactInfoChecker.checkContactInfo(firstName, lastName,
				address, postCode, city, email, fromGoogleOrder);
		if (faultyContactInfo.size() > 0) {
			order.setStateWithHistory(Order.STATE_FAULTY_CONTACT_INFO);
		} else if (outOfStock) {
			order.setStateWithHistory(Order.STATE_NEW_OUT_OF_STOCK);
		} else {
			order.setStateWithHistory(Order.STATE_NEW);
		}

		/* Temporarily set the shipping price according to was was previously calculated.
		 * However this is possibly changed when the order is added to a bundle. */
		//order.setShippingCostInEuro(shippingCostEuro);
		//order.setShippingCostInMarketCurrency(shippingCostMC);

		Provider provider = ad.getAdTemplate().getProduct().getProvider();
		order.setTryToBundle(tryToBundle(provider));
		OrderBundle bundle = orderBundleHandler.getBundleForNewOrder(firstName,lastName,
				address,postCode,city,customer,provider,seller,order,shippingCostInclVatMC,
				ad.getMarket().getCurrency(),maxBundleSizeExclVatMC);
		//order.setOrderBundle(bundle);
		//orderBundleHandler.addOrderToBundle(order,bundle);
		orderDAO.save(order);

		if (faultyContactInfo.size() > 0) {
			/* If the email address was faulty, then we cannot even inform the customer that
			 * there was something wrong with his address information, so then lets throw a
			 * */
			if (!ContactInfoChecker.correctEmailAddress(customer.getEmail())) {
				throw new UnprocessableEmailException("New order having an illegal email address (" + customer.getEmail() + ")");
			}
			MessageDAO messageDAO = (MessageDAO) ApplicationContextPlaceholder.applicationContext.getBean("messageDAO");
			String contextPath = messageDAO.getText("contextPath", bundle.getSeller(),order.getMarket().getLanguage());
			String reason = "";
			for (AddressFault addressFault : faultyContactInfo) {
				reason += messageDAO.getText(addressFault.getField(), bundle.getSeller(),
						order.getMarket().getLanguage());
				reason += ": " + messageDAO.getText(addressFault.getError(), bundle.getSeller(),
						order.getMarket().getLanguage()) + "\n";
			}
			String orderError = messageDAO.getText("faultyContactInfoForOrderX", bundle.getSeller(), order.getMarket().getLanguage(),
					"REASON",reason,
					"ORDER_ID",""+order.getId(),
					"REFERENCE_NUMBER",referenceNumber,
					"CUSTOMER_FIRST_NAME",firstName,
					"CUSTOMER_LAST_NAME",lastName,
					"CUSTOMER_ADDRESS",address,
					"CUSTOMER_POSTCODE",postCode,
					"CUSTOMER_CITY",city,
					"GOOGLE_ORDER_URL",bundle.getSeller().getStoreUrl(),
					"CONTEXT_PATH",contextPath,
					"SIGNATURE_URL_ENC",StringUtilities.escapeString2URL(seller.getSignature()),
					"FIRST_NAME_URL_ENC",StringUtilities.escapeString2URL(bundle.getFirstName()),
					"LAST_NAME_URL_ENC",StringUtilities.escapeString2URL(bundle.getLastName()),
					"CUSTOMER_ADDRESS_URL_ENC",StringUtilities.escapeString2URL(bundle.getAddress()),
					"CUSTOMER_POSTCODE_URL_ENC",StringUtilities.escapeString2URL(bundle.getPostcode()),
					"CUSTOMER_CITY_URL_ENC",StringUtilities.escapeString2URL(bundle.getCity()),
					"CUSTOMER_EMAIL_URL_ENC",StringUtilities.escapeString2URL(bundle.getCustomer().getEmail()));
			String errorEmail = messageDAO.getText("faultyContactInfoAtNewOrder", bundle.getSeller(), order.getMarket().getLanguage(),
					"CUSTOMER_EMAIL",email,
					"FAULTY_CONTACT_INFO_FOR_ORDER_X",orderError,
					"SIGNATURE",bundle.getSeller().getSignature());
			EmailSender.sendEmailBatch(seller.getSystemEmail(),customer.getEmail(),"Tilausvirhe: virheelliset yhteystiedot (tilaus " + order.getId() + ")",errorEmail);
		} else {
			//EmailSender.sendEmailBatch(seller.getSystemEmail(),customer.getEmail(),"Tilaus vastaanotettu",
				//	generatePaymentInformationEmail(order));
			EmailSender.sendEmailBatch(seller.getSystemEmail(),customer.getEmail(),"Tilaus vastaanotettu",
					generatePaymentInformationEmailForBundle(bundle,order));
		}
	}
	
	private static String getNewBatchCameInfo(OrderBundle bundle, Order order) {
		MessageDAO messageDAO = (MessageDAO) ApplicationContextPlaceholder.applicationContext.getBean("messageDAO");
		OrderDAO orderDAO = (OrderDAO) ApplicationContextPlaceholder.applicationContext.getBean("orderDAO");
		return messageDAO.getText("newBatchReceived", bundle.getSeller(), order.getMarket().getLanguage(),
			"ORDER_ID",""+order.getId(),
			"AD_HEADLINE",order.getAd().getAdTemplate().getHeadline());
	}
	
	private static String getHeaderThx(OrderBundle bundle, Order order) {
		MessageDAO messageDAO = (MessageDAO) ApplicationContextPlaceholder.applicationContext.getBean("messageDAO");
		return messageDAO.getText("headerThanksForOrder", bundle.getSeller(), order.getMarket().getLanguage());
	}
	
	private static String getApology(OrderBundle bundle, Order order) {
		MessageDAO messageDAO = (MessageDAO) ApplicationContextPlaceholder.applicationContext.getBean("messageDAO");
		OrderDAO orderDAO = (OrderDAO) ApplicationContextPlaceholder.applicationContext.getBean("orderDAO");
		return messageDAO.getText("bidWonOutOfStock", bundle.getSeller(), order.getMarket().getLanguage(),
				"ORDER_ID",""+order.getId(),
				"AD_HEADLINE",order.getAd().getAdTemplate().getHeadline(),
				"REFERENCE_NUMBER",order.getCustomerReferenceNumber());
	}
	
	private static String getFooter(OrderBundle bundle, Order order) {
		MessageDAO messageDAO = (MessageDAO) ApplicationContextPlaceholder.applicationContext.getBean("messageDAO");
		return messageDAO.getText("footer", bundle.getSeller(), order.getMarket().getLanguage(),
				"SIGNATURE",bundle.getSeller().getSignature());
	}
	
	private static String getBundleInformation(OrderBundle bundle, Order order) {
		MessageDAO messageDAO = (MessageDAO) ApplicationContextPlaceholder.applicationContext.getBean("messageDAO");
		return messageDAO.getText("bundleInformation", bundle.getSeller(), order.getMarket().getLanguage());
	}
	
	private static String getYourOrderNumberIs(OrderBundle bundle, Order order) {
		MessageDAO messageDAO = (MessageDAO) ApplicationContextPlaceholder.applicationContext.getBean("messageDAO");
		OrderDAO orderDAO = (OrderDAO) ApplicationContextPlaceholder.applicationContext.getBean("orderDAO");
		return messageDAO.getText("yourOrderNumberIs", bundle.getSeller(), order.getMarket().getLanguage(),
				"ORDER_ID",""+order.getId(),
				"AD_HEADLINE",order.getAd().getAdTemplate().getHeadline());
	}
	
	private static String getGeneralPaymentInfoHeader(OrderBundle bundle, Order order) {
		MessageDAO messageDAO = (MessageDAO) ApplicationContextPlaceholder.applicationContext.getBean("messageDAO");
		return messageDAO.getText("generalPaymentInfoHeader", bundle.getSeller(), order.getMarket().getLanguage());
	}
	
	private static String getAccountAndPaymentReferenceInfo(OrderBundle bundle, Order order, String accountNumberIban,
			String accountNumberBIC) {
		MessageDAO messageDAO = (MessageDAO) ApplicationContextPlaceholder.applicationContext.getBean("messageDAO");
		return messageDAO.getText("accountAndPaymentReferenceInfo", bundle.getSeller(), order.getMarket().getLanguage(),
				"SIGNATURE",bundle.getSeller().getSignature(),
				"ACCOUNT_NUMBER_IBAN",accountNumberIban,
				"ACCOUNT_NUMBER_BIC",accountNumberBIC,
				"REFERENCE_NUMBER",order.getCustomerReferenceNumber());
	}
	
	private static String getSumForSingleProduct(Order order) {
		OrderBundle bundle = order.getOrderBundle();
		MessageDAO messageDAO = (MessageDAO) ApplicationContextPlaceholder.applicationContext.getBean("messageDAO");
		BigDecimal totalPriceInclVat = order.getFinalSalesPriceInMarketCurrencyInclVat().add(bundle.getCustomerShippingCostInMarketCurrencyInclVat());
		VirtualCoin totalPriceInclVatVC = new VirtualCoin(bundle.getUsedCustomerCurrency(),totalPriceInclVat);
		VirtualCoin finalSalesPriceInclVatVC = new VirtualCoin(bundle.getUsedCustomerCurrency(),order.getFinalSalesPriceInMarketCurrencyInclVat());
		VirtualCoin shippingCostInclVatVC = new VirtualCoin(bundle.getUsedCustomerCurrency(),bundle.getCustomerShippingCostInMarketCurrencyInclVat());

		/* In case the vat percentage is different for the product and the shipping, then the payment message
		 * should probably be formatted differently and that's not implemented in this method, so let's throw
		 * an exception here if that's the case. */
		if (order.getCustomerVatPercentage().compareTo(bundle.getCustomerShippingVatPercentage()) != 0) {
			throw new RuntimeException("Vat different for order and shipping for order " + order.getId() + " - need different formating of payment information.");
		}
		
		if (bundle.getCustomerShippingVatPercentage().compareTo(BigDecimal.ZERO) > 0
				|| order.getCustomerVatPercentage().compareTo(BigDecimal.ZERO) > 0) {
			/* Display vat information as well. */
			BigDecimal shippingVatAmount = bundle.getCustomerShippingCostInMarketCurrencyInclVat().
				subtract(bundle.getCustomerShippingCostInMarketCurrencyExclVat());
			BigDecimal productVatAmount = order.getFinalSalesPriceInMarketCurrencyInclVat().
				subtract(order.getFinalSalesPriceInMarketCurrencyExclVat());
			VirtualCoin totalVatAmountVC = new VirtualCoin(bundle.getUsedCustomerCurrency(),shippingVatAmount.add(productVatAmount));
			BigDecimal alvPercentage = bundle.getCustomerShippingVatPercentage().multiply(new BigDecimal("100"));
			return messageDAO.getText("sumForSingleProductWithVat", bundle.getSeller(), order.getMarket().getLanguage(),
					"TOTAL_PRICE_INCL_VAT",""+CurrencyFormatter.print(totalPriceInclVatVC, Statics.FINNISH),
					"PRODUCT_FINAL_SALES_PRICE_INCL_VAT",CurrencyFormatter.print(finalSalesPriceInclVatVC, Statics.FINNISH),
					"SHIPPING_COST_INCL_VAT",""+CurrencyFormatter.print(shippingCostInclVatVC, Statics.FINNISH),
					"ALV_PERCENTAGE",""+MathUtilities.roundTo2Decimals(alvPercentage).replace(".",","),
					"TOTAL_VAT_AMOUNT",""+CurrencyFormatter.print(totalVatAmountVC, Statics.FINNISH));
		} else {
			/* Don't display vat information. */
			return messageDAO.getText("sumForSingleProduct", bundle.getSeller(), order.getMarket().getLanguage(),
					"TOTAL_PRICE",""+CurrencyFormatter.print(totalPriceInclVatVC, Statics.FINNISH),
					"FINAL_SALES_PRICE",CurrencyFormatter.print(finalSalesPriceInclVatVC, Statics.FINNISH),
					"SHIPPING_COST",""+CurrencyFormatter.print(shippingCostInclVatVC, Statics.FINNISH));
		}
	}
	
	private static String getGeneralPaymentInfoFooter(OrderBundle bundle, Order order, String contextPath) {
		MessageDAO messageDAO = (MessageDAO) ApplicationContextPlaceholder.applicationContext.getBean("messageDAO");
		return messageDAO.getText("generalPaymentInfoFooter", bundle.getSeller(), order.getMarket().getLanguage(),
				"GOOGLE_ORDER_URL",bundle.getSeller().getStoreUrl(),
				"CONTEXT_PATH",contextPath,
				"SIGNATURE_URL_ENC",StringUtilities.escapeString2URL(bundle.getSeller().getSignature()),
				"CUSTOMER_EMAIL_URL_ENC",StringUtilities.escapeString2URL(bundle.getCustomer().getEmail()));
	}
	
	private static String getSendToAddress(OrderBundle bundle, Order order, String contextPath) {
		MessageDAO messageDAO = (MessageDAO) ApplicationContextPlaceholder.applicationContext.getBean("messageDAO");
		return messageDAO.getText("sendToAddress", bundle.getSeller(), order.getMarket().getLanguage(),
				"CUSTOMER_NAME",bundle.getFirstName() + " " + bundle.getLastName(),
				"CUSTOMER_ADDRESS",bundle.getAddress(),
				"CUSTOMER_POSTCODE",bundle.getPostcode(),
				"CUSTOMER_CITY",bundle.getCity(),
				"GOOGLE_ORDER_URL",bundle.getSeller().getStoreUrl(),
				"CONTEXT_PATH",contextPath,
				"SIGNATURE_URL_ENC",StringUtilities.escapeString2URL(bundle.getSeller().getSignature()),
				"REFERENCE_NUMBER",order.getCustomerReferenceNumber(),
				"FIRST_NAME_URL_ENC",StringUtilities.escapeString2URL(bundle.getFirstName()),
				"LAST_NAME_URL_ENC",StringUtilities.escapeString2URL(bundle.getLastName()),
				"CUSTOMER_ADDRESS_URL_ENC",StringUtilities.escapeString2URL(bundle.getAddress()),
				"CUSTOMER_POSTCODE_URL_ENC",StringUtilities.escapeString2URL(bundle.getPostcode()),
				"CUSTOMER_CITY_URL_ENC",StringUtilities.escapeString2URL(bundle.getCity()),
				"CUSTOMER_EMAIL_URL_ENC",StringUtilities.escapeString2URL(bundle.getCustomer().getEmail()));
	}
	
	private static String getFollowingOrderIsAddedToBundle(OrderBundle bundle, Order order, String previousOrdersInfo, String addedOrderInfo) {
		MessageDAO messageDAO = (MessageDAO) ApplicationContextPlaceholder.applicationContext.getBean("messageDAO");
		VirtualCoin shippingCostInclVatVC = new VirtualCoin(bundle.getUsedCustomerCurrency(),bundle.getCustomerShippingCostInMarketCurrencyInclVat());
		return messageDAO.getText("followingOrderIsAddedToBundle", bundle.getSeller(), order.getMarket().getLanguage(),
				"ORDER_BUNDLE_ID",""+bundle.getId(),
				"SHIPPING_COST",""+CurrencyFormatter.print(shippingCostInclVatVC, Statics.FINNISH),
				"PREVIOUS_ORDERS",previousOrdersInfo,
				"ADDED_ORDER",addedOrderInfo);
	}
	
	private static String getPreviousOrdersInfo(OrderBundle bundle, List<Order> orderList, Order lastOrderInBundle) {
		String info = "";
		for (Order order : orderList) {
			if (!order.equals(lastOrderInBundle)) {
				info += getAddedOrderInfo(bundle, order);
			}
		}
		return info;
	}
	
	private static String getAllOrdersInfo(OrderBundle bundle, List<Order> orderList) {
		String info = "";
		for (Order order : orderList) {
			info += getAddedOrderInfo(bundle, order);
		}
		return info;
	}
	
	private static String getAddedOrderInfo(OrderBundle bundle, Order order) {
		MessageDAO messageDAO = (MessageDAO) ApplicationContextPlaceholder.applicationContext.getBean("messageDAO");
		OrderDAO orderDAO = (OrderDAO) ApplicationContextPlaceholder.applicationContext.getBean("orderDAO");
		String key;
		// As far as this method is concerned "new" and "customer paid" is the same thing. See a more thorough
		// explanation for this in generatePaymentInformationEmailForBundle.
		if (order.getState().equals(Order.STATE_NEW_OUT_OF_STOCK)) {
			key = "oneOrderOutOfStockSimple";
		} else if (order.getState().equals(Order.STATE_NEW) || order.getState().equals(Order.STATE_CUSTOMER_PAID)
				|| order.getState().equals(Order.STATE_PAID_TOO_LITTLE_ERROR)) {
			key = "oneOrderNewBatchCameSimple";
		} else {
			throw new RuntimeException("Unexpected state for order " + order.getId() + " in bundle " + bundle.getId());
		}

		VirtualCoin finalSalesPriceInclVatVC = new VirtualCoin(bundle.getUsedCustomerCurrency(),order.getFinalSalesPriceInMarketCurrencyInclVat());
		VirtualCoin targetedPaidVC = new VirtualCoin(bundle.getUsedCustomerCurrency(),order.getTargetedPaidInMarketCurrency());
		if (order.getCustomerVatPercentage().compareTo(BigDecimal.ZERO) > 0) {
			/* Display vat information as well. */
			key += "WithVat";
			BigDecimal productVatAmount = order.getFinalSalesPriceInMarketCurrencyInclVat().
				subtract(order.getFinalSalesPriceInMarketCurrencyExclVat());
			VirtualCoin productVatAmountVC = new VirtualCoin(bundle.getUsedCustomerCurrency(),productVatAmount);
			return messageDAO.getText(key, bundle.getSeller(), order.getMarket().getLanguage(),
					"ORDER_ID",""+order.getId(),
					"PRODUCT_FINAL_SALES_PRICE_INCL_VAT",CurrencyFormatter.print(finalSalesPriceInclVatVC, Statics.FINNISH),
					"ALV_PERCENTAGE",""+order.getCustomerVatPercentage().toPlainString().replace(".",","),
					"PRODUCT_VAT_AMOUNT",""+CurrencyFormatter.print(productVatAmountVC, Statics.FINNISH),
					"ALREADY_PAID",""+CurrencyFormatter.print(targetedPaidVC, Statics.FINNISH),
					"AD_HEADLINE",order.getAd().getAdTemplate().getHeadline()) + "\n";
		} else {
			/* Don't display vat information. */
			return messageDAO.getText(key, bundle.getSeller(),order.getMarket().getLanguage(),
					"ORDER_ID",""+order.getId(),
					"FINAL_SALES_PRICE",""+CurrencyFormatter.print(finalSalesPriceInclVatVC, Statics.FINNISH),
					//"SHIPPING_COST",""+CurrencyFormatter.print(order.getShippingCostInMarketCurrency(), Statics.FINNISH),
					"ALREADY_PAID",""+CurrencyFormatter.print(targetedPaidVC, Statics.FINNISH),
					"AD_HEADLINE",order.getAd().getAdTemplate().getHeadline()) + "\n";
		}
	}
	
	private static String getSumForBundleWithMultipleProducts(OrderBundle bundle, Order order, VirtualCoin alreadyPaid,
			VirtualCoin leftToPay) {
		MessageDAO messageDAO = (MessageDAO) ApplicationContextPlaceholder.applicationContext.getBean("messageDAO");
		return messageDAO.getText("sumForBundleWithMultipleProducts", bundle.getSeller(), order.getMarket().getLanguage(),
				"ALREADY_PAID",CurrencyFormatter.print(alreadyPaid, Statics.FINNISH),
				"LEFT_TO_PAY",CurrencyFormatter.print(leftToPay, Statics.FINNISH));
	}
	
	private static String getBillInfoOnlyForProductsInStock(OrderBundle bundle, Order order) {
		MessageDAO messageDAO = (MessageDAO) ApplicationContextPlaceholder.applicationContext.getBean("messageDAO");
		return messageDAO.getText("billInfoOnlyForProductsInStock", bundle.getSeller(), order.getMarket().getLanguage());
	}
	
	private static String getFollowingOrdersAreInBundle(OrderBundle bundle, Order order, String bundledOrdersInfo) {
		MessageDAO messageDAO = (MessageDAO) ApplicationContextPlaceholder.applicationContext.getBean("messageDAO");
		return messageDAO.getText("followingOrdersAreInBundle", bundle.getSeller(), order.getMarket().getLanguage(),
				"ORDER_BUNDLE_ID",""+bundle.getId(),
				"BUNDLE_ORDERS",bundledOrdersInfo);
	}
	
	public static String generatePaymentInformationEmailForBundle(OrderBundle bundle, Order justHandledOrder) {
		if (!bundle.getId().equals(justHandledOrder.getOrderBundle().getId())) {
			throw new RuntimeException("bundle.id != justHandledOrder.orderBundle.id");
		}
		MessageDAO messageDAO = (MessageDAO) ApplicationContextPlaceholder.applicationContext.getBean("messageDAO");
		SettingsDAO settingsDAO = (SettingsDAO) ApplicationContextPlaceholder.applicationContext.getBean("settingsDAO");
		OrderDAO orderDAO = (OrderDAO) ApplicationContextPlaceholder.applicationContext.getBean("orderDAO");
		
		/* Go through all orders in the bundle and classify them into four different categories:
		 * a.) new out of stock
		 * b.) now new but has previously been out of stock
		 * c.) new and has never been out of stock
		 * d.) canceled orders
		 * 
		 * In addition check which order was the last one added to the bundle + get the sum of the
		 * sales price and shipping cost.
		 * 
		 * The following special case is also handled: in some cases (very rarely) the customer
		 * has paid also for items that are out of stock. In these cases it can be so that one
		 * order in the bundle has state "new out of stock" and the other orders have the state
		 * "customer paid" (NOT "customer paid informed"), because faultyPaymentFixer then sets
		 * the state to "customer paid" for all new orders but CustomerPaymentInformer doesn't
		 * set the state to "customer paid informed" until all orders of the bundle have
		 * "customer paid". So as far as this method is concerned "new" and "customer paid" is
		 * the same thing.
		 * */
		List<Order> newOutOfStockOrders = new ArrayList<Order>();
		List<Order> previouslyOutOfStockOrders = new ArrayList<Order>();
		List<Order> newOrders = new ArrayList<Order>();
		List<Order> allNonCanceledOrders = new ArrayList<Order>();
		List<Order> canceledOrders = new ArrayList<Order>();
		List<Order> ordersInBundle = orderDAO.getOrdersInBundle(bundle);
		//BigDecimal shippingCostForInStock = BigDecimal.ZERO;
		BigDecimal salesPriceForInStockInclVat = BigDecimal.ZERO;
		BigDecimal targetedPaidInclVat = BigDecimal.ZERO;
		for (Order bundleOrder : ordersInBundle) {
			if (bundleOrder.getState().equals(Order.STATE_NEW_OUT_OF_STOCK)) {
				newOutOfStockOrders.add(bundleOrder);
				// Sometimes (very rarely) the customer has paid also for orders that are out of stock,
				// so we need to check the targeted paid also for those.
				targetedPaidInclVat = targetedPaidInclVat.add(bundleOrder.getTargetedPaidInMarketCurrency());
			} else if (bundleOrder.getState().equals(Order.STATE_NEW) || bundleOrder.getState().equals(Order.STATE_CUSTOMER_PAID)
					 || bundleOrder.getState().equals(Order.STATE_PAID_TOO_LITTLE_ERROR)) {
				//shippingCostForInStock = shippingCostForInStock.add(bundleOrder.getShippingCostInMarketCurrency());
				salesPriceForInStockInclVat = salesPriceForInStockInclVat.add(bundleOrder.getFinalSalesPriceInMarketCurrencyInclVat());
				targetedPaidInclVat = targetedPaidInclVat.add(bundleOrder.getTargetedPaidInMarketCurrency());
				boolean hasHadNewOutOfStock = false;
				for (StateChange sc : bundleOrder.getStateChanges()) {
					if (sc.getState().equals(Order.STATE_NEW_OUT_OF_STOCK)) {
						hasHadNewOutOfStock = true;
					}
				}
				if (hasHadNewOutOfStock) {
					previouslyOutOfStockOrders.add(bundleOrder);
				} else {
					newOrders.add(bundleOrder);
				}
			} else if (bundleOrder.getState().equals(Order.STATE_CLOSED_ORDER_CANCELED)) {
				canceledOrders.add(bundleOrder);
			} else {
				throw new RuntimeException("Unexpected: bundle " + bundle.getId() + " has order "
						+ bundleOrder.getId() + " which is in an unexpected state.");
			}
			if (!bundleOrder.getState().equals(Order.STATE_CLOSED_ORDER_CANCELED)) {
				allNonCanceledOrders.add(bundleOrder);
			}
		}
		/* Add the shipping cost (which is a bundle specific cost). */
		salesPriceForInStockInclVat = salesPriceForInStockInclVat.add(bundle.getCustomerShippingCostInMarketCurrencyInclVat());
		targetedPaidInclVat = targetedPaidInclVat.add(bundle.getTargetedPaidShippingCostInMarketCurrency());
		//BigDecimal totalPriceForInStock = salesPriceForInStock.add(shippingCostForInStock);
		
		/* Generate an email for the orders in the bundle. */
		//String accountNumber = settingsDAO.getValue("bankAccountNumber");
		String accountNumberIban = settingsDAO.getValue("bankAccountNumberIban");
		String accountNumberBIC = settingsDAO.getValue("bankAccountNumberBIC");
		String contextPath = messageDAO.getText("contextPath", bundle.getSeller(),justHandledOrder.getMarket().getLanguage());
		String header = getHeaderThx(bundle, justHandledOrder);
		String footer = getGeneralPaymentInfoFooter(bundle, justHandledOrder, contextPath)
			+ "\n\n" + getSendToAddress(bundle, justHandledOrder, contextPath)
			+ "\n\n" + getBundleInformation(bundle, justHandledOrder)
			+ "\n\n" + getFooter(bundle, justHandledOrder);
		String bidWonEmail = "";
		if (ordersInBundle.size() == 1) {
			if (justHandledOrder.getState().equals(Order.STATE_NEW_OUT_OF_STOCK)) {
				/* Single-order bundle and the product is out of stock. */
				logger.debug("Single-ordered bundle and product out of stock.");
				String apology = getApology(bundle, justHandledOrder);
				bidWonEmail += apology;
				footer = getFooter(bundle, justHandledOrder);
			} else {
				/* Single-order bundle and the product is in stock. */
				String newBatchCameInfo = "";
				if (previouslyOutOfStockOrders.size() > 0) {
					logger.debug("Single-ordered bundle: new batch came.");
					newBatchCameInfo = getNewBatchCameInfo(bundle,justHandledOrder) + "\n\n";
				} else {
					logger.debug("Single-ordered bundle and product in stock.");
				}
				String yourOrderNumberIs = getYourOrderNumberIs(bundle, justHandledOrder);
				String generalPaymentInfoHeader = getGeneralPaymentInfoHeader(bundle, justHandledOrder);
				String accountAndPaymentReferenceInfo = getAccountAndPaymentReferenceInfo(bundle, justHandledOrder,
						accountNumberIban, accountNumberBIC);
				String sumForSingleProduct = getSumForSingleProduct(justHandledOrder);
				bidWonEmail += newBatchCameInfo + yourOrderNumberIs + "\n\n" + generalPaymentInfoHeader
					+ "\n\n" + accountAndPaymentReferenceInfo + "\n" + sumForSingleProduct;
			}
		} else { //more than one order in the bundle
			String previousOrdersInfo = getPreviousOrdersInfo(bundle, allNonCanceledOrders, justHandledOrder);
			String addedOrderInfo = getAddedOrderInfo(bundle, justHandledOrder);
			String bundleInfo = getFollowingOrderIsAddedToBundle(bundle, justHandledOrder, previousOrdersInfo, addedOrderInfo);
			String paymentInfoHeader = getGeneralPaymentInfoHeader(bundle, justHandledOrder);
			String accountAndPaymentReferenceInfo = getAccountAndPaymentReferenceInfo(bundle, justHandledOrder,
					accountNumberIban, accountNumberBIC);
			VirtualCoin targetedPaidVC = new VirtualCoin(bundle.getUsedCustomerCurrency(),targetedPaidInclVat);
			VirtualCoin salesPriceForInStockInclVatVC = new VirtualCoin(bundle.getUsedCustomerCurrency(),
					salesPriceForInStockInclVat.subtract(targetedPaidInclVat));
			String sumForBundleWithMultipleProducts = getSumForBundleWithMultipleProducts(bundle, justHandledOrder,
					targetedPaidVC,salesPriceForInStockInclVatVC);
			String newBatchCameInfo = ""; 
			if (ordersInBundle.size() == (newOrders.size() + canceledOrders.size())) {
				/* All orders ok and no order has ever been in state "new out of stock". */
				logger.debug("Multi-order bundle and no order ever in \"new out of stock\".");
			} else if (justHandledOrder.getState().equals(Order.STATE_NEW_OUT_OF_STOCK)) {
				/* New order is out of stock. */
				logger.debug("Multi-order bundle and new order out of stock.");
				bundleInfo += "\n\n" + getApology(bundle, justHandledOrder);
				paymentInfoHeader = getBillInfoOnlyForProductsInStock(bundle, justHandledOrder);
			} else if (justHandledOrder.getState().equals(Order.STATE_NEW) && previouslyOutOfStockOrders.contains(justHandledOrder)
					&& newOutOfStockOrders.size() == 0) {
				/* New batch came and now all orders are ok. */
				logger.debug("Multi-order bundle and all is finally ok.");
				String allOrdersInfo = getAllOrdersInfo(bundle, allNonCanceledOrders);
				bundleInfo = getFollowingOrdersAreInBundle(bundle, justHandledOrder, allOrdersInfo);
				newBatchCameInfo = getNewBatchCameInfo(bundle,justHandledOrder) + "\n\n";
			} else if (justHandledOrder.getState().equals(Order.STATE_NEW) && previouslyOutOfStockOrders.contains(justHandledOrder)
					&& newOutOfStockOrders.size() > 0) {
				/* New batch came but still not all orders are ok. */
				logger.debug("Multi-order bundle: new batch came but some not ok.");
				String allOrdersInfo = getAllOrdersInfo(bundle, allNonCanceledOrders);
				bundleInfo = getFollowingOrdersAreInBundle(bundle, justHandledOrder, allOrdersInfo);
				newBatchCameInfo = getNewBatchCameInfo(bundle,justHandledOrder) + "\n\n";
				paymentInfoHeader = getBillInfoOnlyForProductsInStock(bundle, justHandledOrder);
			} else if (justHandledOrder.getState().equals(Order.STATE_NEW) && !previouslyOutOfStockOrders.contains(justHandledOrder)
					&& newOutOfStockOrders.size() > 0) {
				/* New order added to bundle alright, but some order is not ok. */
				logger.debug("Multi-order bundle: new order came but some not ok.");
				paymentInfoHeader = getBillInfoOnlyForProductsInStock(bundle, justHandledOrder);
			} else if (justHandledOrder.getState().equals(Order.STATE_NEW) && !previouslyOutOfStockOrders.contains(justHandledOrder)
					&& newOutOfStockOrders.size() == 0 && ordersInBundle.size() == (newOrders.size() + previouslyOutOfStockOrders.size() + canceledOrders.size())) {
				/* The just added order has never been out of stock but some other order
				 * has _previously_ been out of stock and now all orders are okay. */
				logger.debug("Multi-order bundle and some order was _previously_ in \"new out of stock\".");
			} else {
				throw new RuntimeException("Unexpected situation when generating payment information email for bundle " + bundle.getId());
			}
			bidWonEmail += newBatchCameInfo + bundleInfo + "\n" + paymentInfoHeader + "\n\n"
				+ accountAndPaymentReferenceInfo + "\n" + sumForBundleWithMultipleProducts; 
		}
		return header + "\n\n" + bidWonEmail + "\n\n" + footer; 
	}
	
}
