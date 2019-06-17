package fi.jonix.huutonet.commandrow.option;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;

import fi.jonix.huutonet.domain.model.Product;
import fi.jonix.huutonet.domain.model.Provider;
import fi.jonix.huutonet.domain.model.Seller;
import fi.jonix.huutonet.domain.model.User;
import fi.jonix.huutonet.domain.model.dao.AdTemplateDAO;
import fi.jonix.huutonet.domain.model.dao.MarketDAO;
import fi.jonix.huutonet.domain.model.dao.ProductDAO;
import fi.jonix.huutonet.domain.model.dao.ProviderDAO;
import fi.jonix.huutonet.domain.model.dao.SellerDAO;
import fi.jonix.huutonet.domain.model.dao.SettingsDAO;
import fi.jonix.huutonet.domain.model.dao.UserDAO;
import fi.jonix.huutonet.email.EmailSender;
import fi.jonix.huutonet.exception.FillItemsQuitException;
import fi.jonix.huutonet.exception.FillItemsSkipException;
import fi.jonix.huutonet.order.ProviderHandler;
import fi.jonix.huutonet.order.ProviderOrder;
import fi.jonix.huutonet.tools.CmAdCreator;
import fi.jonix.huutonet.tools.CmAdCreator.TranslationMode;
import fi.jonix.huutonet.tools.PrintTools;
import fi.jonix.huutonet.tools.StringUtilities;
import fi.jonix.huutonet.tools.UserInputHelper;

public class FillItemCommandHandler extends CommandHandler {
	
	public static final Logger logger = Logger.getLogger(FillItemCommandHandler.class);

	@Autowired
	CmAdCreator cmAdCreator;

	@Autowired
	ProductDAO productDAO;

	@Autowired
	MarketDAO marketDAO;

	@Autowired
	AdTemplateDAO adTemplateDAO;

	@Autowired
	ProviderDAO providerDAO;
	
	@Autowired
	SettingsDAO settingsDAO;
	
	@Autowired
	ProviderHandler providerHandler;
	
	@Autowired
	UserDAO userDAO;
	
	@Autowired
	SellerDAO sellerDAO;
	
	@Override
	protected void handleCommand() {
		TranslationMode translationMode = null;
		if (arguments.indexOf("TranslationMode=ALL") >= 0) {
			logger.info("Translation mode: all");
			translationMode = TranslationMode.ALL;
		} else if (arguments.indexOf("TranslationMode=HEADLINE_AND_DETAILS") >= 0) {
			logger.info("Translation mode: headline and details");
			translationMode = TranslationMode.HEADLINE_AND_DETAILS;
		} else {
			logger.info("Default translation mode: headline and details");
			// use a default of only headline and details if no arguments are specified
			translationMode = TranslationMode.HEADLINE_AND_DETAILS;
		}
		boolean linuxConsole = (arguments.indexOf("--linux") >= 0);
		if (linuxConsole) {
			System.out.println("Using Linux console");
		} else {
			System.out.println("Using Windows console");
		}
		String encoding = CharSetOption.getEncoding();
		Provider provider = providerDAO.getProviderByName(this.getValue("provider"));
		
		/* Check if the user exists + find a suitable seller. */
		String userStr = this.getValue("user");
		User user = userDAO.getByLoginName(userStr);
		if (user == null) {
			System.out.println("User " + userStr + " not found.");
			return;
		}
		Seller seller = null;
		List<Seller> sellers = sellerDAO.getSellersByUser(user);
		if (sellers.isEmpty()) {
			//if the user has no sellers, then use default seller with id 1
			System.out.println("User " + user.getLoginName() + " has no sellers, using default.");
			seller = sellerDAO.get(1L);
			if (seller == null) {
				System.out.println("Default seller not found.");
				return;
			}
		} else if (sellers.size() == 1) {
			System.out.println("User " + user.getLoginName() + " has one seller.");
			seller = sellers.get(0);
		} else {
			System.out.println("User " + user.getLoginName() + " has several sellers.");
			for (int i = 0; i < sellers.size(); i++) {
				System.out.println((i+1) + ".) " + sellers.get(i).getName());
			}
			int index = UserInputHelper.getOneNumber("Choose one seller to use",1,sellers.size(),
					false,false,new BufferedReader(new InputStreamReader(System.in)));
			seller = sellers.get(index-1);
		}
		System.out.println("Using seller " + seller.getName());
		
		ProviderOrder providerOrder = providerHandler.getProviderOrder(provider);
		int count = 100;
		if (this.hasValue("count")) {
			try {
				count = Integer.parseInt(this.getValue("count"));
			} catch (NumberFormatException e) {
				System.out.println(this.getValue("count") + " is not a valid value for count");
				return;
			}
		}
		
		List<Product> products = new ArrayList<Product>();

		if (this.hasValue("products")) {
			String[] productIds = this.getValue("products").split(",");
			for (String providerProdId : productIds) {
				products.add(productDAO.getByProviderProdId(providerProdId, provider));
			}
		} else {
			try {
				/*
				List<AdTemplate> adTProblemList = adTemplateDAO.getAdTemplatesWithoutCategory();
				if (adTProblemList != null && adTProblemList.size() > 0) {
					logger.info("******************** There are " + adTProblemList.size()
							+ " translations that are missing a category ********************");
					logger.info("******************** This needs to be fixed first ********************");
					for (AdTemplate at : adTProblemList) {
						System.out.println("at.id = " + at.getId());
						cmAdCreator.fillItem(at, encoding, user, providerOrder);
					}
				}
				*/
				products.addAll(productDAO.getProductsWithoutAdTemplate(provider));
				if (products.size() == 0) {
					EmailSender.sendOperationProblemReport("Out of products to translate",
							user.getLoginName() + " informs that there are no more products to translate - please run getitems!");
				}
			} catch (Exception e2) {
				logger.error("fillItems failed", e2);
			}
		}

		System.out.println("Products to be translated " + products.size());

		Calendar startCalendar = Calendar.getInstance();
		int countOfProducts = products.size();
		System.out.println("\nA total of " + countOfProducts + " products awaiting translations");

		int successCount = 0;
		int deleteCount = 0;
		//int errorCount = 0;
		int failureCount = 0;
		int skippedCount = 0;
		boolean quit = false;

		for (Product product : products) {
			StringBuffer translations = new StringBuffer();
			try {
				System.out.println("Product id " + product.getId());
				int value = cmAdCreator.fillItem(product, encoding, user.getLoginName(), providerOrder,
						linuxConsole, true, translationMode, seller, translations);
				if (value == CmAdCreator.RETURN_SUCCESS) {
					successCount++;
					if (successCount >= count) {
						break;
					}
				}/* else if (value == CmAdCreator.RETURN_QUIT) {
					return;
				}*/ else if (value == CmAdCreator.RETURN_DELETE) {
					deleteCount++;
				} else if (value == CmAdCreator.RETURN_FAILURE) {
					failureCount++;
				}/* else if (value == CmAdCreator.RETURN_SKIP) {
					skippedCount++;
				}*/
			} catch (FillItemsQuitException e) {
				quit = true;
			} catch (FillItemsSkipException e) {
				skippedCount++;
			} catch (Exception e) {
				//errorCount++;
				logger.error("fillItems failed", e);
				quit = true;
				/* Send bug report if the user isn't an operator. */
				if (!user.getOperator()) {
					EmailSender.sendBugReport("fillitems", "User: " + user.getLoginName()
							+  ", product: " + product.getId() + "\n\nTranslations:\n" + translations.toString() + "\n\n" + StringUtilities.getStackTrace(e)); 
				}
			}
			Calendar now = Calendar.getInstance();
			System.out.println("\n\n");
			System.out.println("##  " + "--------STATISTICS---------");
			System.out.println("##  " + (successCount + deleteCount/* + errorCount*/) + " / " + countOfProducts);
			System.out.println("##  " + successCount + " products translated");
			System.out.println("##  " + deleteCount + " products deleted");
			System.out.println("##  " + skippedCount + " products skipped");
			//System.out.println("##  " + errorCount + " failed in program error");
			System.out.println("##  " + failureCount + " failed and deleted");
			System.out.println("##  " + "\n");
			System.out.println("##  " + "Time used " + PrintTools.getFormattedTimeDifference(startCalendar, now));
			System.out.println("##  " + "Average translation time per product "
					+ PrintTools.getFormattedAverageTime(startCalendar, now, successCount + deleteCount/* + errorCount*/));
			System.out.println("##  " + "---------------------------\n");
			if (quit) {
				break;
			}
		}
		logger.info("Done filling items.");
	}

	@Override
	protected void initializeOptionList() {
		this.options.add(new Option(true, "provider", "Provider of the products", "Please enter provider name", /*Provider.DEAL_EXTREME_PROVIDER_NAME,*/ Provider.FOCAL_PRICE_PROVIDER_NAME));
		this.options.add(new Option(false, "products", "Product ids separated with comma like sku1,sku2,sku3 (or leave empty)", "Please enter product ids"));
		this.options.add(new Option(false, "count", "Count of products to be translated", "Please enter count (or leave empty)"));
		this.options.add(new Option(true, "user", "Name of the translator", "Please enter your name"));
	}

}
