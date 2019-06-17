package fi.jonix.huutonet.commandrow;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.net.UnknownHostException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.mail.FolderClosedException;
import javax.mail.MessagingException;
import javax.mail.SendFailedException;

import org.apache.log4j.Logger;
import org.openqa.selenium.WebDriverException;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.google.api.translate.Language;
import com.google.api.translate.Translate;
import com.thoughtworks.selenium.SeleniumException;

import fi.jonix.huutonet.bank.BankHelper;
import fi.jonix.huutonet.bank.CustomerPaymentInformer;
import fi.jonix.huutonet.bank.FaultyPaymentFixer;
import fi.jonix.huutonet.bank.MoneyTransferer;
import fi.jonix.huutonet.bank.Refunds;
import fi.jonix.huutonet.commandrow.option.CharSetOption;
import fi.jonix.huutonet.commandrow.option.DownloadImagesCommandHandler;
import fi.jonix.huutonet.commandrow.option.FillItemCommandHandler;
import fi.jonix.huutonet.commandrow.option.GetItemCommandHandler;
import fi.jonix.huutonet.domain.ApplicationContextPlaceholder;
import fi.jonix.huutonet.domain.ModuleNames;
import fi.jonix.huutonet.domain.VirtualCoin;
import fi.jonix.huutonet.domain.model.Ad;
import fi.jonix.huutonet.domain.model.AdTemplate;
import fi.jonix.huutonet.domain.model.Currency;
import fi.jonix.huutonet.domain.model.Market;
import fi.jonix.huutonet.domain.model.Order;
import fi.jonix.huutonet.domain.model.OrderBundle;
import fi.jonix.huutonet.domain.model.Product;
import fi.jonix.huutonet.domain.model.Provider;
import fi.jonix.huutonet.domain.model.ProviderQuestion;
import fi.jonix.huutonet.domain.model.Seller;
import fi.jonix.huutonet.domain.model.SellerMarket;
import fi.jonix.huutonet.domain.model.Settings;
import fi.jonix.huutonet.domain.model.Tracking;
import fi.jonix.huutonet.domain.model.dao.AdDAO;
import fi.jonix.huutonet.domain.model.dao.AdTemplateDAO;
import fi.jonix.huutonet.domain.model.dao.CurrencyDAO;
import fi.jonix.huutonet.domain.model.dao.MarketDAO;
import fi.jonix.huutonet.domain.model.dao.OrderBundleDAO;
import fi.jonix.huutonet.domain.model.dao.OrderDAO;
import fi.jonix.huutonet.domain.model.dao.ProductDAO;
import fi.jonix.huutonet.domain.model.dao.ProviderDAO;
import fi.jonix.huutonet.domain.model.dao.ProviderQuestionDAO;
import fi.jonix.huutonet.domain.model.dao.SellerDAO;
import fi.jonix.huutonet.domain.model.dao.SellerMarketDAO;
import fi.jonix.huutonet.domain.model.dao.SettingsDAO;
import fi.jonix.huutonet.domain.model.dao.TrackingDAO;
import fi.jonix.huutonet.email.EmailRobot;
import fi.jonix.huutonet.email.EmailSender;
import fi.jonix.huutonet.email.MultiProcessorEmailRobot;
import fi.jonix.huutonet.email.OperatorSummaryCreator;
import fi.jonix.huutonet.exception.CriticalSectionInfo;
import fi.jonix.huutonet.exception.ErrorHandler;
import fi.jonix.huutonet.exception.ListingException;
import fi.jonix.huutonet.exception.LoggedOutFromMarketException;
import fi.jonix.huutonet.exception.MarketLoggedOnAsWrongUserException;
import fi.jonix.huutonet.exception.MarketTemporarilyDownException;
import fi.jonix.huutonet.exception.PayPalCaptchaException;
import fi.jonix.huutonet.exception.PlaceOrderException;
import fi.jonix.huutonet.exception.PostTrackingDownException;
import fi.jonix.huutonet.exception.ProductNotFoundAtProviderException;
import fi.jonix.huutonet.exception.ProviderTemporarilyDownException;
import fi.jonix.huutonet.exception.SellStarException;
import fi.jonix.huutonet.exception.SellStarRuntimeException;
import fi.jonix.huutonet.exception.SoldOutAtProviderException;
import fi.jonix.huutonet.market.FeedbackGiver;
import fi.jonix.huutonet.market.FeedbackReader;
import fi.jonix.huutonet.market.ItemLister;
import fi.jonix.huutonet.market.MarketRobot;
import fi.jonix.huutonet.market.PaidAdsBookkeeper;
import fi.jonix.huutonet.order.OrderBundleHousekeeping;
import fi.jonix.huutonet.order.Price;
import fi.jonix.huutonet.order.ProviderHandler;
import fi.jonix.huutonet.order.ProviderOrder;
import fi.jonix.huutonet.order.ProviderHandler.ProductInfo;
import fi.jonix.huutonet.order.focalprice.FocalPriceProvider;
import fi.jonix.huutonet.post.GeneralPostService;
import fi.jonix.huutonet.robot.CustomWebDriver;
import fi.jonix.huutonet.robot.SeleniumRobot;
import fi.jonix.huutonet.tools.CategoryAdmin;
import fi.jonix.huutonet.tools.ConsistencyChecker;
import fi.jonix.huutonet.tools.ExchangeRateFetcher;
import fi.jonix.huutonet.tools.DxProductHelper;
import fi.jonix.huutonet.tools.Encryption;
import fi.jonix.huutonet.tools.ImageHelper;
import fi.jonix.huutonet.tools.ProductScoreGenerator;
import fi.jonix.huutonet.tools.StringUtilities;
import fi.jonix.huutonet.tools.TemplateTester;
import fi.jonix.huutonet.tools.TomisTester;

public class Test {

	public static final Logger logger = Logger.getLogger(Test.class);

	public static void main(String[] args) {
		// The correct version will only be displayed when running this as a jar.
		logger.info("Version " + Test.class.getPackage().getImplementationVersion());
		logger.info("Copyright (C) 2009-2010 John Bergbom & Tomi Laurell (QD-Tech Oy)");
		if (args == null || args.length == 0) {
			logger.warn("Command argument not found.");
			printUsage();
			return;
		} else if (args[0].equals("-h") || args[0].equals("--help")) {
			printUsage();
			return;
		}
		logger.debug("At start of run (" + args[0] + ")");
		
		Test t = new Test();
		String command = args[0];
		ErrorHandler errorHandler = null;
		try {
			t.initContext();
			errorHandler = (ErrorHandler) ApplicationContextPlaceholder.applicationContext.getBean("errorHandler");
			boolean globalError = errorHandler.getGlobalError(command);
			logger.debug("globalError = " + globalError);
			if (globalError && errorHandler.getProductionEnvironment()) {
				if (command.equals("checkemail") && errorHandler.isDatabaseInitialized()) {
					logger.info("Unacknowledged global error, only sending pending emails.");
				} else {
					logger.info("Unacknowledged global error, refusing to start.");
					return;
				}
			}
			
			if (errorHandler.isDatabaseInitialized()) {
				boolean commandError = errorHandler.getCommandError(command);
				logger.debug("commandError = " + commandError);
				if ((globalError || commandError) && !command.equals("checkemail")) {
					logger.info("Unacknowledged command error, refusing to start.");
					return;
				}
				SettingsDAO settingsDAO = (SettingsDAO) ApplicationContextPlaceholder.applicationContext.getBean("settingsDAO");
				t.startSeleniumRobot(settingsDAO.getValue("seleniumProfile"));

				if (command.equals("checkemail")) {
					t.checkEmail(globalError || commandError);
				} else if (command.equals("listitems")) {
					t.listItems(args);
				} else if (command.equals("payForHighlightedAds")) {
					t.payForHighlightedAds(args);
				} else if (command.equals("updatetechnicalspecs")) {
					t.updateTechnicalSpecsToItems(args);
				} else if (command.equals("handlereceivedfeedback")) {
					t.handleReceivedFeedback(args);
				} else if (command.equals("createOperatorSummaryReport")) {
					t.createOperatorSummaryReport(args);
				} else if (command.equals("getitems")) {
					GetItemCommandHandler getItemCommandHandler = (GetItemCommandHandler)
						ApplicationContextPlaceholder.applicationContext.getBean("getItemCommandHandler");
					getItemCommandHandler.work(args);
				} else if (command.equals("downloadimages")) {
					DownloadImagesCommandHandler downloadImagesCommandHandler =
						(DownloadImagesCommandHandler) ApplicationContextPlaceholder.applicationContext.getBean("downloadImagesCommandHandler");
					downloadImagesCommandHandler.work(args);
				} else if (command.equals(ModuleNames.FETCH_TRANSACTIONS)) {
					t.fetchTransactions(args);
				} else if (command.equals("fetchexchangerates")) {
					t.fetchExchangeRates();
				} else if (command.equals("placeorders")) {
					t.placeOrders(args);
				} else if (command.equals("updateProviderPrices")) {
					t.updateProviderPrices(args);
				} else if (command.equals("testtemplate")) {
					t.testTemplate(args);
				} else if (command.equals("fillitems")) {
					FillItemCommandHandler fillItemCommandHandler =
						(FillItemCommandHandler) ApplicationContextPlaceholder.applicationContext.getBean("fillItemCommandHandler");
					fillItemCommandHandler.work(args);
				} else if (command.equals("uploadimages")) {
					t.uploadImages(args);
				} else if (command.equals("givefeedback")) {
					t.giveFeedback(args);
				} else if (command.equals("encrypt")) {
					t.encrypt(args);
				} else if (command.equals("encryptkeyvaluecodes")) {
					t.encryptKeyValueCodes(args);
				//} else if (command.equals("decrypt")) {
					//System.out.println(Encryption.decrypt(args[1]));
				} else if (command.equals("confirmpayment")) {
					t.confirmPaymentToCustomer(args);
				} else if (command.equals("fixfaultypayments")) {
					t.fixFaultyPayments(args);
				} else if (command.equals("fixfaultytransactions")) {
					t.fixFaultyTransactions(args);
				//} else if (command.equals("makepayment")) {
					//t.makePayment(args);
				} else if (command.equalsIgnoreCase("updateStatusOfOrders")) {
					t.updateStatusOfOrders(args);
				} else if (command.equals("checkconsistency")) {
					t.checkConsistency();
				} else if (command.equals(ModuleNames.MAKE_REFUNDS)) {
					t.makeRefunds(args);
				} else if (command.equals(ModuleNames.TRANSFER_TO_QD)) {
					t.transferToQD(args);
				} else if (command.equals("bookkeepPaidAds")) {
					t.bookkeepPaidAds(args);
				//} else if (command.equals("closeoldorders")) {
					//t.closeOldOrders(args);
				} else if (command.equals("categoryconf")) {
					t.configCategories(args);
				} else if (command.equals("tomi")) {
					TomisTester.tomiTester(args);
				//} else if (command.equals("runbatch")) {
					//t.runBatch(args);
				} else if (command.equals("housekeeping")) {
					t.orderHousekeeping(args);
				} else if (command.equals("updateProviderCommunication")) {
					t.updateProviderCommunication(args);
				} else {
					logger.warn("Unknown argument: " + command);
					printUsage();
					return;
				}
				if (command.equals("checkemail") && (globalError || commandError)) {
					logger.debug("Not updating run timestamp nor acknowledging successful run.");
				} else {
					t.updateRunTimestamp(command);
					errorHandler.acknowledgeSuccessfulRun(command);
				}
			}
		} catch (SeleniumException e) {
			if (e.getMessage() != null && (e.getMessage().startsWith("Timed out after")
					|| e.getMessage().startsWith("ERROR: Command timed out"))) {
				logger.error("Selenium timed out");
				logger.debug("Selenium timed out: ",e);
			} else if (e.getMessage() != null && e.getMessage().startsWith("ERROR Server Exception: unexpected command json")
					&& e.getMessage().indexOf("in place before new command open could be added") > 0) {
				logger.error("Selenium or web browser bug");
				logger.debug("Selenium or web browser bug: ",e);
			} else {
				errorHandler.handleException(args,e);
			}
		} catch (Throwable e) {
			if (errorHandler == null) {
				//error handler is null only if the spring context wasn't initialized successfully
				logger.error("Application context wasn't initialized, cannot handle error: ", e);
			} else {
				errorHandler.handleException(args,e);
			}
		} finally {
			//error handler is null only if the spring context wasn't initialized successfully
			if (errorHandler != null) {
				t.stopSeleniumRobot();
			}
		}
		logger.debug("At end of run (" + command + ")");
	}

	/**
	 * Updates the timestamp for when the unit was last ran.
	 * 
	 * @param runUnit
	 */
	private void updateRunTimestamp(String runUnit) {
		/* Update the usage */
		SettingsDAO settingsDAO = (SettingsDAO) ApplicationContextPlaceholder.applicationContext.getBean("settingsDAO");
		Settings lastRun = settingsDAO.get(runUnit + ".lastRun");
		DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		String lastRunStr = dateFormat.format(new Date());
		if (lastRun == null) {
			lastRun = new Settings(runUnit + ".lastRun", lastRunStr);
		} else {
			lastRun.setValue(lastRunStr);
		}
		settingsDAO.save(lastRun);
		settingsDAO.flush(); // do a flush in order to catch database errors
								// right away
	}
	
	/*private Calendar getRunTimestamp(String runUnit) {
		SettingsDAO settingsDAO = (SettingsDAO) ApplicationContextPlaceholder.applicationContext.getBean("settingsDAO");
		Settings lastRun = settingsDAO.get(runUnit + ".lastRun");
		if (lastRun == null) {
			/*Calendar cal = Calendar.getInstance();
			cal.set(Calendar.YEAR,1970);
			cal.set(Calendar.m,);
			cal.set(Calendar.,);
			cal.set(Calendar.,);
			cal.set(Calendar.,);
			cal.set(Calendar.,);
			cal.set(Calendar.,);
			cal.set(Calendar.,);/
			return null;
		} else {
			DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
			Calendar cal = Calendar.getInstance();
			try {
				cal.setTime(dateFormat.parse(lastRun.getValue()));
			} catch (ParseException pe) {
				throw new RuntimeException("Error getting run timestamp for " + runUnit);
			}
			return cal;
		}
	}*/

	private static void printUsage() {
		logger.info("usage: Test <options>\n");
		logger.info("options");
		logger.info("  -h, --help                         Prints this help text.");
		//logger.info("  runbatch                           Runs email checker, lister, feedback receiver");
		//logger.info("                                     and feedback giver.");
		//logger.info("  listitems [ADTEMPLATEID]           Lists item ADTEMPLATEID to huutonet, or");
		logger.info("  listitems [market [remainder]]     Lists items to the specified market or");
		logger.info("                                     to all markets if no market is specified.");
		logger.info("                                     remainder is a comma separated list of adTemplate.id");
		logger.info("                                     remainders that should be listed (list only those ad");
		logger.info("                                     templates whose id % 10 = remainder). For example");
		logger.info("                                     if remainder is \"2,7\", then adtemplates 3452 and");
		logger.info("                                     127 will be listed but not 5474.");
		logger.info("  getitems                           Fetches the specified product series from");
		logger.info("                                     provider.");
		logger.info("  fillitems                          Fills out information for ad templates");
		logger.info("                                     for products created with \"getitems\"");
		logger.info("  checkemail                         Checks email.");
		logger.info("  " + ModuleNames.FETCH_TRANSACTIONS + " USER PWD CODES   Fetches bank transactions.");
		logger.info("                                     USER = username for bank,");
		logger.info("                                     PWD = password for bank,");
		logger.info("                                     CODES = list of codes");
		logger.info("  fetchexchangerates                 Fetches the latest exchange rates.");
		logger.info("  placeorders USER PWD               Orders stuff from the provider for");
		logger.info("                                     USER = paypal username,");
		logger.info("                                     PWD  = paypal password");
		logger.info("                                     orders where the customer has paid");
		logger.info("  updateProviderPrices               Updates the provider prices - NOTE: might interfer with");
		logger.info("                                     placeOrders, don't run at the same time.");
		logger.info("  testtemplate [ADTEMPLATEID]        Creates a test template (first active");
		logger.info("                                     if none specified).");
		logger.info("  uploadimages                       Goes through the database and for each image");
		logger.info("                                     not existing on any image host, upload it.");
		logger.info("  givefeedback                       Gives feedback to customers.");
		logger.info("  confirmpayment                     Confirms to the customer that their payment");
		logger.info("                                     was received.");
		logger.info("  updatetechnicalspecs               Updates technical specs for products that");
		logger.info("                                     aren't yet translated.");
		logger.info("  handlereceivedfeedback             Closes orders that are shipped and have");
		logger.info("                                     received positive feedback.");
		logger.info("  fixfaultypayments                  Attempts to fix faulty payments.");
		logger.info("  fixfaultytransactions [true]       Attempts to fix faulty transactions.");
		logger.info("                                     If parameter = true, then send stuff to operator,");
		logger.info("                                     otherwise just print the faulty transactions to screen.");
		/*logger.info("  makepayment PARAMETERS             Makes a payment unrelated to any order. The following parameters");
		logger.info("                                     are required:");
		logger.info("                                     sum = sum in euro to be paid");
		logger.info("                                     accountNumber = the account to which the payment is made");
		logger.info("                                     receiverName = name of the receiver");
		logger.info("                                     message = what to put in the message field");
		logger.info("                                     bankUser = username for bank");
		logger.info("                                     bankPassword = password for bank");
		logger.info("                                     bankCodes = list of codes");*/
		logger.info("  checkconsistency                   Checks for different types of problems/inconsistencies.");
		logger.info("  " + ModuleNames.MAKE_REFUNDS + " USER PWD CODES         Makes refunds to customers for canceled orders");
		logger.info("                                     USER = username for bank,");
		logger.info("                                     PWD = password for bank,");
		logger.info("                                     CODES = list of codes");
		//logger.info("  closeoldorders                     Closes old unpaid orders and old shipped orders without feedback.");
		logger.info("  categoryconf                       Configures categories.");
		//logger.info("  confirmstockshortage               Confirms to the customer that their order");
		//logger.info("                                     has stock shortage.");
		logger.info("  updateStatusOfOrders               Updates the status of orders by checking at the provider.");
		logger.info("  downloadimages                     Downloads images of products.");
		logger.info("  housekeeping                       Does order housekeeping + informs customers about order status.");
		logger.info("  updateProviderCommunication        Updates ongoing conversations with the provider.");
		logger.info("  payForHighlightedAds               Pays for highlighted ads.");
		logger.info("  createOperatorSummaryReport        Creates a summary report for the operator.");
		//Note: don't show this setting, because it's a "hidden" feature.
		//logger.info("  categoryconf true                  Configures categories with admin rights.");
		//Note: don't show this setting, because others could use this to figure out our codes.
		//logger.info("  encrypt STRING                     Encrypts a test string.");
	}

	/**
	 * This method initiates the spring context using xml definitions.
	 */
	public void initContext() {
		String[] configLocations = new String[5];
		configLocations[0] = "applicationContext-ds.xml";
		configLocations[1] = "applicationContext-dao.xml";
		configLocations[2] = "applicationContext-dao-tx.xml";
		configLocations[3] = "applicationContext-cr-msg.xml";
		configLocations[4] = "applicationContext-robot.xml";
		ClassPathXmlApplicationContext ctx = new ClassPathXmlApplicationContext(configLocations);
		ctx.refresh();
		ApplicationContextPlaceholder.applicationContext = ctx;
	}

	private void startSeleniumRobot(String seleniumProfile) throws Exception {
		SeleniumRobot seleniumRobot = (SeleniumRobot) ApplicationContextPlaceholder.applicationContext.getBean("seleniumRobot");
		seleniumRobot.init(seleniumProfile);
		seleniumRobot.getSelenium().setTimeout("120000");
	}

	private void stopSeleniumRobot() {
		SeleniumRobot seleniumRobot = (SeleniumRobot) ApplicationContextPlaceholder.applicationContext.getBean("seleniumRobot");
		try {
			seleniumRobot.tearDown();
		} catch (Exception e) {
			logger.error("Couldn't shut down selenium robot: " + e);
			e.printStackTrace();
		}
	}
	
	public void encrypt(String[] args) throws Exception {
		if (args.length < 2) {
			logger.info("You need to give a string to encrypt.");
		} else {
			//Note: this is printed only to the console and not to the log
			System.out.println(Encryption.encrypt(args[1]));
		}
	}

	/**
	 * This method expects a file with rows on the form "key value" and gives
	 * as output "key=encryptedvalue".
	 */
	public void encryptKeyValueCodes(String[] args) throws Exception {
		if (args.length < 2) {
			logger.info("You need to give a file containing key value pairs to encrypt.");
		} else {
			BufferedReader in = new BufferedReader(new FileReader(args[1]));
			String row = in.readLine();
			int i = 0;
			while (row != null) {
				if (!row.trim().equals("")) {
					String key = row.substring(0,row.indexOf(" "));
					String value = row.substring(row.indexOf(" ")+1).trim();
					//Note: this is printed only to the console and not to the log
					if (i > 0) {
						System.out.print(",");
					}
					System.out.print(key + "=" + Encryption.encrypt(value));
					i++;
				}
				row = in.readLine();
			}
			if (i > 0) {
				System.out.println();
			}
			in.close();
		}
	}

	private void checkEmail(boolean inErrorState) {
		CriticalSectionInfo section = new CriticalSectionInfo();
		try {
			MultiProcessorEmailRobot emailProcessor = (MultiProcessorEmailRobot) ApplicationContextPlaceholder.applicationContext
					.getBean("multiProcessorEmailRobot");
			EmailRobot emailRobot = new EmailRobot();
			Map<String,String> emailMap = new HashMap<String,String>();
			SellerDAO sellerDAO = (SellerDAO) ApplicationContextPlaceholder.applicationContext.getBean("sellerDAO");
			List<Seller> sellers = sellerDAO.loadAll();
			for (Seller seller : sellers) {
				/* Several sellers and providers can share the same address, so make sure we only go through
				 * each email address once. */
				if (emailMap.get(seller.getSystemEmail().getEmailAddress()) == null) {
					emailMap.put(seller.getSystemEmail().getEmailAddress(), "");
					if (seller.getSystemEmail().getCheckEmail()) {
						logger.info("Processing inbox for seller " + seller.getSystemEmail().getEmailAddress());
						emailRobot.processInbox(emailProcessor,seller,inErrorState,section);
						logger.info("Done processing inbox for seller.");
					} else {
						logger.info("Only sending pending emails for " + seller.getSystemEmail().getEmailAddress());
						emailRobot.processInbox(emailProcessor,seller,true,section);
						logger.info("Done sending pending emails for seller.");
					}
				} else {
					logger.debug("S: not going through " + seller.getSystemEmail().getEmailAddress() +  " again.");
				}
			}
			ProviderDAO providerDAO = (ProviderDAO) ApplicationContextPlaceholder.applicationContext.getBean("providerDAO");
			List<Provider> providers = providerDAO.loadAll();
			for (Provider provider : providers) {
				/* Several sellers and providers can share the same address, so make sure we only go through
				 * each email address once. */
				if (emailMap.get(provider.getSystemEmail().getEmailAddress()) == null) {
					emailMap.put(provider.getSystemEmail().getEmailAddress(), "");
					if (provider.getSystemEmail().getCheckEmail()) {
						logger.info("Processing inbox for provider " + provider.getSystemEmail().getEmailAddress());
						emailRobot.processInbox(emailProcessor,provider,inErrorState,section);
						logger.info("Done processing inbox for provider.");
					} else {
						logger.info("Only sending pending emails for provider " + provider.getSystemEmail().getEmailAddress());
						emailRobot.processInbox(emailProcessor,provider,true,section);
						logger.info("Done sending pending emails for provider.");
					}
				} else {
					logger.debug("P: not going through " + provider.getSystemEmail().getEmailAddress() +  " again.");
				}
			}
			//createCategorySuggestions();
		} catch (Exception e) {
			int allowedErrors = 10; //default
			if (section.isInCriticalSection()) {
				logger.error("Exception when reading email (IN CRITICAL SECTION): ",e);
				allowedErrors = 0;
			} else {
				logger.error("Exception when reading email (not in critical section): " + e.getMessage());
			}
			throw new SellStarRuntimeException(e.getMessage(), null, allowedErrors, e);
		}
	}

	private MarketRobot getListerForMarket(Market market) {
		List<MarketRobot> listers = (List<MarketRobot>) ApplicationContextPlaceholder.applicationContext.getBean("listers");
		for (MarketRobot lister : listers) {
			if (lister.canHandleMarket(market)) {
				return lister;
			}
		}
		return null;
	}

	private void createOperatorSummaryReport(String[] args) {
		logger.info("Creating operator summary report.");
		OperatorSummaryCreator operatorSummaryCreator = (OperatorSummaryCreator) ApplicationContextPlaceholder.applicationContext.getBean("operatorSummaryCreator");
		operatorSummaryCreator.makeOperatorSummaryReport();
		logger.info("Done creating operator summary report.");
	}
	
	private void payForHighlightedAds(String[] args) {
		logger.info("Paying for highlighted ads.");
		CriticalSectionInfo section = new CriticalSectionInfo();
		ItemLister itemLister = (ItemLister) ApplicationContextPlaceholder.applicationContext.getBean("itemLister");
		AdDAO adDAO = (AdDAO) ApplicationContextPlaceholder.applicationContext.getBean("adDAO");
		SellerMarketDAO sellerMarketDAO = (SellerMarketDAO) ApplicationContextPlaceholder.applicationContext.getBean("sellerMarketDAO");
		List<SellerMarket> sellerMarkets = sellerMarketDAO.loadAll();
		for (SellerMarket sellerMarket : sellerMarkets) {
			if (!sellerMarket.getDoListing()) {
				logger.info("Skipping highlighting of ads for seller market " + sellerMarket.getId() + " (seller "
						+ sellerMarket.getSeller().getName() + ", market " + sellerMarket.getMarket().getName() + ")"
						+ " because it has doListing = false");
				continue;
			}
			MarketRobot lister = getListerForMarket(sellerMarket.getMarket());
			try {
				lister.login(sellerMarket);
			} catch (MarketTemporarilyDownException e) {
				logger.info("Cannot pay for highlighting of ads because market is down: " + e.getMessage());
				//In some cases the login can go through but we still get here because of
				//selenium errors, so logout just in case.
				lister.logout();
				throw new SellStarRuntimeException(e.getMessage(), null, 5, e);
			} catch (MarketLoggedOnAsWrongUserException e) {
				//This usually means that the huutonet web page is temporarily screwed up
				throw new SellStarRuntimeException(e.getMessage(), null, 5, e);
			} catch (Exception e) {
				//This usually means that the huutonet web page is temporarily screwed up
				throw new SellStarRuntimeException("Unexpected error logging on to market in payForHighlightedAds", e.getMessage(), 1, e);
			}
			try {
				/* Pay for the products having visibilityType > 0. */
				List<Ad> adsForDisplayWindow = adDAO.findAdsForDisplayWindow(sellerMarket.getSeller());
				logger.debug("Number of ads to add to the display window: " + adsForDisplayWindow.size());
				if (adsForDisplayWindow.size() > 0) {
					try {
						itemLister.payForHighlightedAds(lister, adsForDisplayWindow, section);
						section.setTransactionMustGoThrough(false); //set this to false after the transaction is done
					} catch (SellStarRuntimeException e) {
						if (section.isInCriticalSection() || section.isTransactionMustGoThrough() || e.getNbrAllowedErrors() == 0) {
							e.setNbrAllowedErrors(0);
						}
						throw e;
					} catch (Exception e) {
						if (section.isInCriticalSection() || section.isTransactionMustGoThrough()) {
							logger.error("Exception when paying for highlighted ads (IN CRITICAL SECTION/transaction must go through/nbrAllowedErrors is zero): ",e);
							throw new SellStarRuntimeException(e.getMessage(), null, 0, e);
						} else {
							logger.error("Exception when paying for highlighted ads (not in critical section): " + e.getMessage());
							String ads = "";
							for (Ad ad : adsForDisplayWindow) {
								ads += ad.getMarketSalesId() + ",";
							}
							/*EmailSender.sendOperationProblemReport("Problem paying for highlighting of products at Huutonet",
									"Hightlighting of products failed (doesn't require any special action). The following"
									+ " ads where not highlighted: " + ads);*/
							throw new SellStarRuntimeException("Problem paying for highlighting of products",
									"Hightlighting of products failed (doesn't require any special action). The following"
									+ " ads where not highlighted: " + ads, 2, e);
						}
					}
				}
			} finally {
				logger.debug("Logging out after highlighting of ads for sellerMarket " + sellerMarket.getId());
				lister.logout();
			}
		}
		logger.info("Done paying for highlighted ads.");
	}
	
	// List items to supported markets
	private int listItems(String[] args) {
		CriticalSectionInfo section = new CriticalSectionInfo();
		/*Long adtemplateId = null;
		if (args.length > 1) {
			adtemplateId = Long.parseLong(args[1]);
			logger.info("Listing adtemplateId " + adtemplateId);
			//TODO: this can be fixed so that here we get the seller from the adtemplate
			throw new UnsupportedOperationException("This operation is no longer supported.");
		}*/
		
		/* Start by parsing the arguments (if any). */
		MarketDAO marketDAO = (MarketDAO) ApplicationContextPlaceholder.applicationContext.getBean("marketDAO");
		Market hardcodedMarket = null;
		String remainderStr = null;
		List<Long> remainderList = new ArrayList<Long>();
		if (args.length > 3) {
			logger.warn("Too many arguments to " + args[0]);
			return 0;
		} else if (args.length > 2) {
			hardcodedMarket = marketDAO.getByName(args[1]);
			remainderStr = args[2];
			String[] split = remainderStr.split(",");
			for (String subStr : split) {
				/* Make sure they are all integers and then put them in the list (as a string). */
				try {
					Long remainder = Long.parseLong(subStr);
					if (remainder < 0 || remainder > 9) {
						logger.warn("Faulty argument to " + args[0] + ": remainder has to be between 0 and 9 (is " + remainder + ").");
						return 0;
					}
					remainderList.add(remainder);
				} catch (NumberFormatException e) {
					logger.warn("Faulty arguments to " + args[0] + ": remainder " + subStr + " in " + args[2] + " is not a valid number.");
					return 0;
				}
			}

		} else if (args.length > 1) {
			hardcodedMarket = marketDAO.getByName(args[1]);
		}
		if (args.length > 1 && hardcodedMarket == null) {
			logger.warn("Faulty argument to " + args[0] + ": market " + args[1] + " not found.");
			return 0;
		}
		if (hardcodedMarket == null) {
			logger.debug("hardcodedMarket = " + hardcodedMarket);
		} else {
			logger.debug("hardcodedMarket = " + hardcodedMarket.getName());
		}
		logger.debug("remainderStr = " + remainderStr);
		
		ItemLister itemLister = (ItemLister) ApplicationContextPlaceholder.applicationContext.getBean("itemLister");
		logger.info("Listing items.");
		SellerDAO sellerDAO = (SellerDAO) ApplicationContextPlaceholder.applicationContext.getBean("sellerDAO");
		List<Seller> sellers = sellerDAO.loadAll();
		logger.debug("Total number of sellers = " + sellers.size());
		AdTemplateDAO adTemplateDAO = (AdTemplateDAO) ApplicationContextPlaceholder.applicationContext.getBean("adTemplateDAO");
		AdDAO adDAO = (AdDAO) ApplicationContextPlaceholder.applicationContext.getBean("adDAO");
		SeleniumRobot seleniumRobot = (SeleniumRobot) ApplicationContextPlaceholder.applicationContext.getBean("seleniumRobot");
		List<AdTemplate> adTemplates;
		/*if (adtemplateId != null) {
			AdTemplate adTemplate = adTemplateDAO.get(adtemplateId);
			adTemplates = new ArrayList<AdTemplate>();
			adTemplates.add(adTemplate);
		} else {
			adTemplates = adTemplateDAO.loadAll();
		}*/
		Map<String,Integer> nonExistentCategories = new HashMap<String,Integer>();
		Map<String,Integer> nonExistentCategoryMappings = new HashMap<String,Integer>();
		Map<Long,Integer> adTemplatesWithoutCategory = new HashMap<Long,Integer>();
		SellerMarketDAO sellerMarketDAO = (SellerMarketDAO) ApplicationContextPlaceholder.applicationContext.getBean("sellerMarketDAO");
		ProductScoreGenerator productScoreGenerator = (ProductScoreGenerator) ApplicationContextPlaceholder.applicationContext.getBean("productScoreGenerator");
		List<SellerMarket> sellerMarkets = sellerMarketDAO.loadAll();
		int totalListedCount = 0;
		try {
			for (SellerMarket sellerMarket : sellerMarkets) {
				if (!sellerMarket.getDoListing()) {
					logger.info("Skipping listing for seller market " + sellerMarket.getId() + " (seller "
							+ sellerMarket.getSeller().getName() + ", market " + sellerMarket.getMarket().getName() + ")"
							+ " because it has doListing = false");
					continue;
				} else if (hardcodedMarket != null && !sellerMarket.getMarket().getId().equals(hardcodedMarket.getId())) {
					logger.info("Skipping listing for market " + sellerMarket.getMarket().getName()
							+ " because configured to only list for " + hardcodedMarket.getName());
					continue;
				}
				
				/* Do listing of the adtemplates. */
				//TODO: it would probably be good to order the adtemplates in a "most sold first" order while listing
				MarketRobot lister = getListerForMarket(sellerMarket.getMarket());
				adTemplates = adTemplateDAO.findBySeller(sellerMarket.getSeller());
				//adTemplates = new ArrayList<AdTemplate>();
				//adTemplates.add(adTemplateDAO.get(6365L));
				logger.info("Total number of adTemplates = " + adTemplates.size() + " (for market " + sellerMarket.getMarket().getName()
						+ " and seller " + sellerMarket.getSeller().getName() + ").");
				//boolean alreadyLoggedOn = false;
				if (adTemplates.size() > 0) {
					try {
						lister.login(sellerMarket);
					} catch (MarketTemporarilyDownException e) {
						logger.info("Cannot list because market is down: " + e.getMessage());
						//In some cases the login can go through but we still get here because of
						//selenium errors, so logout just in case.
						lister.logout();
						//continue;
						throw new SellStarRuntimeException("Cannot list because market is down", "at logon: " + e.getMessage(), 3, e);
					} catch (MarketLoggedOnAsWrongUserException e) {
						//logger.info("Cannot list because market is in use by a different seller.");
						//This usually means that the huutonet web page is temporarily screwed up
						throw new SellStarRuntimeException(e.getMessage(), null, 3, e);
					} catch (Exception e) {
						//This usually means that the huutonet web page is temporarily screwed up
						throw new SellStarRuntimeException("Unexpected error logging on to market in listItems", e.getMessage(), 1, e);
					}
					int failed = 0;
					int transactionTimeout = 0;
					int listedCount = 0;
					int adTemplateCount = 0;
					try {
						/* Do listing. */
						TreeMap<Long,List<Long>> preFilledScoreMap = productScoreGenerator.
							prefillSellingStatisticsWithValidAdIdsForListing(200, sellerMarket.getMarket(), sellerMarket.getSeller());
						//long startingPoint = System.currentTimeMillis();
						for (AdTemplate adTemplate : adTemplates) {
							//if (remainder != null && adTemplate.getId() % 2 != remainder) {
							if (remainderStr != null && !matchedReminder(adTemplate.getId(),remainderList)) {
								// skip listing if this run should only list some of the ad templates
								continue;
							}
							adTemplateCount++;
							boolean success = true;
							int categoryNumber = 0;
							try {
								/* Call listItem with increasing categoryNumber until it doesn't list anything more. */
								while (success) {
									/* Stop the listing at 23:30 each night. This is done because sometimes the web browser
									 * stops working properly (jumping firefox icon in the dock at the mac + popup window
									 * with "faulty parameter" or something similar). The browser starts working properly
									 * again when we click at the ok button manually. However this cannot be fixed
									 * programmatically. Therefore we stop listing at midnight to avoid too long listing
									 * sessions in case we are having this browser problem in the current listing session.
									 * 
									 * Earlier there was also a problem regarding rotation of logs at midnight when several
									 * concurrently running SellStar instances tried to rotate the log at the same time.
									 * However that problem seems to have disappeared now when a new logging library is
									 * taken into use + made sure that we write straight to debug.log.yyyy-mm-dd.
									 * =>Update: this log rotation problem seems to still exist in some cases, so this
									 * is good to have also for that reason. */
									Calendar cal = Calendar.getInstance();
									if (cal.get(Calendar.HOUR_OF_DAY) == 23 && cal.get(Calendar.MINUTE) >= 30) {
										logger.debug("Stopping listing because it's late.");
										return totalListedCount;
									}
									success = itemLister.listItem(adTemplate, false/*(adtemplateId != null)*/, categoryNumber, sellerMarket,
											nonExistentCategories, nonExistentCategoryMappings,adTemplatesWithoutCategory,lister,preFilledScoreMap);
									if (success) {
										listedCount++;
										categoryNumber++;
										totalListedCount++;
									}
								}
							} catch (WebDriverException e) {
								/* There is a problem with FireFox that now and then causes problems. Let's do a workaround
								 * for it here. More information can be found at http://code.google.com/p/selenium/issues/detail?id=1438 */
								if (e.getMessage().indexOf("getWindow() is null") >= 0) {
									logger.debug("Workaround for bug in firefox for the following error: ", e);
									CustomWebDriver driver = seleniumRobot.getWebDriver();
									driver.switchTo().defaultContent();
									failed++;
								}
							} catch (SeleniumException e) {
								/* There is a problem with FireFox that now and then causes problems. Let's do a workaround
								 * for it here. More information can be found at http://code.google.com/p/selenium/issues/detail?id=1438 */
								if (e.getMessage().indexOf("win is null; duration or timeout") >= 0) {
									logger.debug("Workaround for bug in firefox for the following error: ", e);
									CustomWebDriver driver = seleniumRobot.getWebDriver();
									driver.switchTo().defaultContent();
									failed++;
								}
							} catch (RuntimeException e) {
								if (e.getMessage() != null && e.getMessage().startsWith(FocalPriceProvider.WRONG_CURRENCY_IN_USE)) {
									logger.debug("Stopping listing because of currency conversion problem (temporary)", e);
									logger.info("Stopping listing because of currency conversion problem (temporary): " + e.getMessage());
									//For some reason we often get an error "already logged on as a different user even
									//though the finally-clause above logs us out, so just to make sure log out here as well.
									lister.logout();
									//break;
									throw new SellStarRuntimeException(e.getMessage(), null, 5, e);
								} else if (e.getMessage() != null && (e.getMessage().startsWith("transaction timeout expired")
										|| (e.getMessage().startsWith("Could not commit Hibernate transaction"))
										|| (e.getMessage().startsWith("Could not roll back Hibernate transaction"))
										/*|| (e.getMessage().startsWith("Transaction rolled back because it has been marked as rollback-only"))*/)) {
									logger.info("Got a transaction timeout for listing, still continuing listing.");
									transactionTimeout++;
								} else {
									throw e;
								}
							} catch (LoggedOutFromMarketException e) {
								/* This one comes now and then for Mikko. Simply stop the listing here. */
								//break;
								throw new SellStarRuntimeException(e.getMessage(), null, 5, e);
							} catch (MarketTemporarilyDownException e) {
								logger.info("Cannot list because market is down: " + e.getMessage());
								//In some cases the login can go through but we still get here because of
								//selenium errors, so logout just in case.
								lister.logout();
								//continue;
								throw new SellStarRuntimeException("Cannot list because market is down", "in listing: " + e.getMessage(), 3, e);
							} catch (ListingException le) {
								if (le.isToErrorState()) {
									//throw le;
									throw new SellStarRuntimeException(le.getMessage(), null, 0, le);
								} else {
									failed++;
									logger.debug("Listing failed with non-fatal error: " + le.getMessage());
									logger.debug("Number of failed for " + sellerMarket.getMarket().getName()
											+ "/" + sellerMarket.getSeller().getName() + ": " + failed);
								}
							} catch (Exception e) {
								throw new SellStarRuntimeException(e.getMessage(), null, 0, e);
							}
							if (categoryNumber > 1) {
								logger.info("Listed totally " + categoryNumber + " ads for adtemplate " + adTemplate.getId()
										+ " (market = " + sellerMarket.getMarket().getName() + ", seller = " + sellerMarket.getSeller().getName() + ")");
							}
							if (/*listedCount == 0 && */(failed > 5 && (failed > adTemplates.size() / 5 || failed > 500)) || transactionTimeout > 5) {
								/*throw new Exception("Too many failures (failed = " + failed + ", transactionTimeout = " + transactionTimeout
										+ ") while listing items for " + sellerMarket.getMarket().getName()
										+ "/" + sellerMarket.getSeller().getName() + ". Bailing out.");*/
								throw new SellStarRuntimeException("Too many failures while listing items for " + sellerMarket.getMarket().getName()
										+ "/" + sellerMarket.getSeller().getName() + ". Bailing out.", "failed = " + failed + ", transactionTimeout = " + transactionTimeout, 4, null);
							}
							if (adTemplateCount % 10 == 0) {
								logger.info("Gone through " + adTemplateCount + "/" + adTemplates.size() + " of the adtemplates for "
										+ sellerMarket.getMarket().getName() + "/" + sellerMarket.getSeller().getName()
										+ " (" + listedCount + " actually listed" + (failed > 0 ? ", failed = " + failed : "")
										+ (transactionTimeout > 0 ? ", transactionTimeout = " + transactionTimeout : "") + ")");
							}
						}
					} finally {
						logger.debug("Logging out after listing for sellerMarket " + sellerMarket.getId());
						lister.logout();
					}
				}
			}
		} finally {
			/* Send email to the operator about all categories that weren't found at markets + all ads that couldn't be listed
			 * because of missing category mappings + report if there are over 50 adtemplates that couldn't be listed because
			 * the corresponding product didn't have any category defined. */
			int totalAmountOfSkipped = 0;
			if (nonExistentCategories.size() > 0 || nonExistentCategoryMappings.size() > 0 || adTemplatesWithoutCategory.size() > 50) {
				StringBuffer strBuf = new StringBuffer();
				if (nonExistentCategories.size() > 0) {
					strBuf.append("The following listings were skipped because the defined categories weren't\n");
					strBuf.append("found at markets (categories have probably changed at the markets).\n\n");
					Iterator<String> iter = nonExistentCategories.keySet().iterator();
					while (iter.hasNext()) {
						String key = iter.next();
						int count = nonExistentCategories.get(key);
						totalAmountOfSkipped += count;
						strBuf.append("Listing skipped for " + count + " products for the following category:\n");
						strBuf.append(key + "\n\n");
					}
				}
				if (nonExistentCategoryMappings.size() > 0) {
					strBuf.append("\nThe following listings were skipped because the defined sellstar categories aren't\n");
					strBuf.append("mapped to any category at market. Please create category mappings for these.\n\n");
					Iterator<String> iter = nonExistentCategoryMappings.keySet().iterator();
					while (iter.hasNext()) {
						String key = iter.next();
						int count = nonExistentCategoryMappings.get(key);
						totalAmountOfSkipped += count;
						strBuf.append("Listing skipped for " + count + " products of the following:\n");
						strBuf.append(key + "\n\n");
					}
				}
				if (adTemplatesWithoutCategory.size() > 50) {
					strBuf.append("\n" + adTemplatesWithoutCategory.size() + " weren't listed because the corresponding\n");
					strBuf.append("product doesn't have any category defined. Please define categories for these.\n\n");
				}
				if (totalAmountOfSkipped > 50 || adTemplatesWithoutCategory.size() > 50) {
					EmailSender.sendOperationSummaryReport("listing",strBuf.toString());
				} else {
					/*logger.info(totalAmountOfSkipped + " products skipped at listing because of missing market category and "
							+ adTemplatesWithoutCategory.size() + " products skipped because of missing product.category, but don't"
							+ " inform the operator because the number of skipped is so low.");*/
					logger.info(strBuf.toString());
				}
			}
		}
		logger.info("Done listing items.");
		return totalListedCount;
	}

	private boolean matchedReminder(Long number, List<Long> validReminders) {
		for (Long remainder : validReminders) {
			if (number.longValue() % 10 == remainder.longValue()) {
				return true;
			}
		}
		return false;
	}

	private void fetchTransactions(String[] args) throws Exception {
		BankHelper bankHelper = (BankHelper) ApplicationContextPlaceholder.applicationContext.getBean("bankHelper");
		logger.info("Fetching transactions from the bank.");
		bankHelper.getNewTransactions(args);
		logger.info("Done fetching transactions.");
	}

	/**
	 * This method handles refunds by repeatedly calling refunds.refundToCustomer(...) until it returns zero.
	 * 
	 * @param args
	 * @throws Exception
	 */
	private void makeRefunds(String[] args) throws Exception {
		logger.debug("Running makeRefunds.");
		/* Extract the arguments. */
		String user = args[1];
		String password = args[2];
		Map<String,String> bankCodes = StringUtilities.keyValueArray2Map(args[3]);

		Refunds refunds = (Refunds) ApplicationContextPlaceholder.applicationContext.getBean("refunds");
		int nbrRefundsMade = 0;
		Map<String,BigDecimal> totalRefundSumInEuro = new HashMap<String,BigDecimal>();
		totalRefundSumInEuro.put("totalRefundSumInEuro", BigDecimal.ZERO);
		try {
			while (refunds.refundToCustomer(user,password,bankCodes,totalRefundSumInEuro) > 0) {
				//keep going until refundToCustomer returns zero
				nbrRefundsMade++;
				if (totalRefundSumInEuro.get("totalRefundSumInEuro").compareTo(new BigDecimal("90.0")) > 0) {
					throw new Exception("Too much money was refunded (" + totalRefundSumInEuro.get("totalRefundSumInEuro") + " euro).");
				}
			}
			if (totalRefundSumInEuro.get("totalRefundSumInEuro").compareTo(new BigDecimal("90.0")) > 0) {
				throw new Exception("Too much money was refunded (" + totalRefundSumInEuro.get("totalRefundSumInEuro") + " euro).");
			}
		} catch (Exception e) {
			if (nbrRefundsMade > 0) {
				throw new Exception("Make refunds failed. THERE ARE AT LEAST " + nbrRefundsMade + " _UNCONFIRMED_ PAYMENTS. PLEASE"
						+ " FIX MANUALLY. NOTE: LOG ON TO THE BANK MANUALLY AND IF THERE ARE _MORE_ THAN " + nbrRefundsMade
						+ " REFUNDS DUE, THEN SOME TRANSACTION WAS ROLLED BACK IN THE DATABASE AND SHOULD THUS ALSO"
						+ " BE REMOVED IN THE BANK.",e);
			} else {
				throw e;
			}
		}
		logger.info("Made a total of " + nbrRefundsMade + " refunds (total sum was " + totalRefundSumInEuro.get("totalRefundSumInEuro") + ").");
		refunds.finishRefunds(user,password,bankCodes,nbrRefundsMade);
		logger.debug("Done running makeRefunds.");
	}

	private void transferToQD(String[] args) throws Exception {
		logger.debug("Running transferToQD.");
		/* Extract the arguments. */
		String user = args[1];
		String password = args[2];
		Map<String,String> bankCodes = StringUtilities.keyValueArray2Map(args[3]);
		MoneyTransferer moneyTransferer = (MoneyTransferer) ApplicationContextPlaceholder.applicationContext.getBean("moneyTransferer");
		try {
			if (moneyTransferer.transferToQD(user, password, bankCodes)) {
				Refunds refunds = (Refunds) ApplicationContextPlaceholder.applicationContext.getBean("refunds");
				refunds.finishRefunds(user,password,bankCodes,1);
			}
		} catch (RuntimeException e) {
			if (e.getMessage() != null && e.getMessage().equals("Skipping money transfer.")) {
				logger.info("Money transfer was skipped.");
			} else {
				throw e;
			}
		}
	}
	
	private void bookkeepPaidAds(String[] args) throws Exception {
		logger.debug("Running bookkeepPaidAds.");
		PaidAdsBookkeeper paidAdsBookkeeper = (PaidAdsBookkeeper) ApplicationContextPlaceholder.applicationContext.getBean("paidAdsBookkeeper");
		try {
			paidAdsBookkeeper.createVerification();
		} catch (RuntimeException e) {
			if (e.getMessage() != null && e.getMessage().equals("Skipping createVerification.")) {
				logger.info("Skipping sending paid ads to bookkeeping.");
			} else {
				throw e;
			}
		}
	}
	
	/* Makes a payment unrelated to any order. */
	/*private void makePayment(String[] args) throws Exception {
		BankHelper bankHelper = (BankHelper) ApplicationContextPlaceholder.applicationContext.getBean("bankHelper");
		BigDecimal sum = null;
		try {
			sum = new BigDecimal(args[1]);
		} catch (NumberFormatException nfe) {
			logger.info(args[1] + " is not a valid sum");
			return;
		}
		String accountNumber = args[2];
		String receiverName = args[3];
		String message = args[4];
		if (args.length != 95) { //1 command (=makepayment) + 4 arguments + 1 bank user + 1 bank password + 88 bank codes = 95
			logger.info("Faulty number of parameters - did you forget some necessary parameter?");
			return;
		}
		bankHelper.makePayment(sum,accountNumber,receiverName,message,args);
		throw new Exception("Should this method ever be used?");
	}*/

	private void updateProviderPrices(String[] args) throws Exception {
		logger.info("Updating provider prices.");
		ProviderHandler providerHandler = (ProviderHandler) ApplicationContextPlaceholder.applicationContext.getBean("providerHandler");
		ProductDAO productDAO = (ProductDAO) ApplicationContextPlaceholder.applicationContext.getBean("productDAO");
		ProviderDAO providerDAO = (ProviderDAO) ApplicationContextPlaceholder.applicationContext.getBean("providerDAO");
		List<Provider> providers = providerDAO.loadAll();
		for (Provider provider : providers) {
			if (!provider.getName().equals(Provider.FOCAL_PRICE_PROVIDER_NAME)) {
				logger.debug("Skipping updating of provider prices for provider " + provider.getName());
				continue;
			}
			List<Product> productsToUpdate = productDAO.getProductsForUpdateProviderPrice(provider);
			//List<Product> productsToUpdate = new ArrayList<Product>();
			//productsToUpdate.add(productDAO.get(6824L));
			if (productsToUpdate.size() > 0) {
				logger.info(productsToUpdate.size() + " products have prices to be updated for provider " + provider.getName() + ".");
				try {
					providerHandler.login(provider,3);
				} catch (Exception e) {
					logger.info("Cannot logon to provider " + provider.getName() + " - aborting.");
					try {
						providerHandler.logout(provider,true);
					} catch (Exception e2) {
						logger.error("Problem logging out from provider at start of updateProviderPrices",e2);
					}
					//continue;
					if (e instanceof ProviderTemporarilyDownException) {
						throw new SellStarRuntimeException("Cannot logon to provider " + provider.getName() + ": " + e.getMessage(), null, 3, e);
					}
					throw new SellStarRuntimeException("Cannot logon to provider " + provider.getName() + ": " + e.getMessage(), null, 1, e);
				}
				try {
					int i = 0;
					int nbrTransactionErrors = 0;
					int nbrOtherErrors = 0;
					for (Product product : productsToUpdate) {
						/* Stop at 23:30 each night. Reason: see listitems. */
						Calendar cal = Calendar.getInstance();
						if (cal.get(Calendar.HOUR_OF_DAY) == 23 && cal.get(Calendar.MINUTE) >= 30) {
							logger.debug("Stopping updateProviderPrices because it's late.");
							return;
						}
						if (!product.getProductState().equals(Product.STATE_NO_LONGER_FOR_SALE)) {
							try {
								providerHandler.updateProviderPrice(product,true);
							} catch (Exception e) {
								logger.debug("Exception in updateProviderPrices: ", e);
								if (e.getMessage() != null && (e.getMessage().startsWith("transaction timeout expired")
										|| (e.getMessage().startsWith("Could not commit Hibernate transaction"))
										|| (e.getMessage().startsWith("Could not roll back Hibernate transaction"))
										/*|| (e.getMessage().startsWith("Transaction rolled back because it has been marked as rollback-only"))*/)) {
									logger.info("Got a transaction timeout for updateProviderPrices, still continuing ("
											+ e.getMessage() + ").");
									nbrTransactionErrors++;
								} else {
									nbrOtherErrors++;
								}
								//throw new SellStarRuntimeException(e.getMessage(), null, 0, e);
							}
						}
						i++;
						if (nbrTransactionErrors > 20) {
							throw new SellStarRuntimeException("Too many failures in updateProviderPrices",
									"nbrTransactionErrors = " + nbrTransactionErrors + ", nbrOtherErrors = " + nbrOtherErrors, 3, null);
						}
						if (nbrOtherErrors > 2) {
							throw new SellStarRuntimeException("Too many failures in updateProviderPrices",
									"nbrTransactionErrors = " + nbrTransactionErrors + ", nbrOtherErrors = " + nbrOtherErrors, 2, null);
						}
						if (i % 10 == 0) {
							logger.info("Gone through " + i + "/" + productsToUpdate.size()
									+ " of the products for updateProviderPrices"
									+ (nbrTransactionErrors > 0 || nbrOtherErrors > 0 ? "(nbrTransactionErrors = "
											+ nbrTransactionErrors + ", nbrOtherErrors = " + nbrOtherErrors + ")" : "") + ".");
						}
					}
				} finally {
					logger.debug("Logging out after updating provider prices for provider " + provider.getName());
					try {
						providerHandler.logout(provider,true);
					} catch (Exception e) {
						logger.error("Problem logging out from provider at end of updateProviderPrices",e);
					}
				}
			}
		}
		logger.info("Done updating provider prices.");
	}
	
	private void placeOrders(String[] args) {
		logger.info("Placing orders.");
		CriticalSectionInfo section = new CriticalSectionInfo();
		OrderBundleDAO orderBundleDAO = (OrderBundleDAO) ApplicationContextPlaceholder.applicationContext.getBean("orderBundleDAO");
		ProviderHandler providerHandler = (ProviderHandler) ApplicationContextPlaceholder.applicationContext
				.getBean("providerHandler");
		
		ProviderDAO providerDAO = (ProviderDAO) ApplicationContextPlaceholder.applicationContext.getBean("providerDAO");
		List<Provider> providers = providerDAO.loadAll();
		for (Provider provider : providers) {
			List<OrderBundle> bundleList = orderBundleDAO.getBundlesForPlaceOrder(provider);
			if (bundleList.size() > 0) {
				logger.info(bundleList.size() + " bundles are awaiting ordering for provider " + provider.getName() + ".");
				try {
					providerHandler.login(provider,3);
				} catch (Exception e) {
					logger.info("Cannot logon to provider " + provider.getName() + " - aborting.");
					try {
						providerHandler.logout(provider,true);
					} catch (Exception e2) {
						logger.error("Problem logging out from provider at start of placeOrders",e2);
					}
					//continue;
					if (e instanceof ProviderTemporarilyDownException) {
						throw new SellStarRuntimeException("Cannot logon to provider " + provider.getName() + ": " + e.getMessage(), null, 3, e);
					}
					throw new SellStarRuntimeException("Cannot logon to provider " + provider.getName() + ": " + e.getMessage(), null, 1, e);
				}
				try {
					int nbrSellStarExceptions = 0;
					int otherError = 0;
					int succeeded = 0;
					for (OrderBundle bundle : bundleList) {
						/* Stop at 23:30 each night. Reason: see listitems. */
						Calendar cal = Calendar.getInstance();
						if (cal.get(Calendar.HOUR_OF_DAY) == 23 && cal.get(Calendar.MINUTE) >= 30) {
							logger.debug("Stopping placeOrders because it's late.");
							return;
						}
						try {
							// Sometimes DX bails out when placing orders and the problem _possibly_ comes if we place
							// several orders in rapid succession, so let's wait here for a minute before placing
							// the next order in an attempt to avoid this buggy behaviour of DX.
							//logger.debug("Going to sleep for one minute because of bug in DX.");
							//Thread.sleep(60*1000);
							//logger.debug("Woke up from sleep");
							providerHandler.placeOrder(bundle, args, section);
							section.setTransactionMustGoThrough(false); //set this to false after the transaction is done
							succeeded++;
						} catch (SellStarException sse) {
							nbrSellStarExceptions++;
							if (section.isInCriticalSection() || section.isTransactionMustGoThrough()) {
								logger.error("Exception when placing order (IN CRITICAL SECTION/transaction must go through/nbrAllowedErrors is zero): ",sse);
								throw new SellStarRuntimeException("Exception when placing order", "for bundle " + bundle.getId(), 0, sse);
							} else if (sse instanceof SoldOutAtProviderException && !sse.isToErrorState()) {
								logger.info("Failed to place order at provider (sold out) for bundle " + bundle.getId()
										+ " (" + sse.getMessage() + " - number of nbrSellStarExceptions: " + nbrSellStarExceptions + ")");
							} else if (sse instanceof ProviderTemporarilyDownException && !sse.isToErrorState()) {
								logger.info("Failed to place order at provider (provider down) for bundle " + bundle.getId()
										+ " (" + sse.getMessage() + " - number of nbrSellStarExceptions: " + nbrSellStarExceptions + ")");
							} else if (sse instanceof PayPalCaptchaException && !sse.isToErrorState()) {
								logger.info("Failed to place order at provider (paypal captcha) for bundle " + bundle.getId()
										+ " (" + sse.getMessage() + " - number of nbrSellStarExceptions: " + nbrSellStarExceptions + ")");
								//If this occurs we need to wait a while before trying again, because usually the
								//captcha comes again if we try again without any pause in between.
								logger.info("Stopping ordering batch and trying again later.");
								break;
							} else if (sse instanceof PlaceOrderException && !sse.isToErrorState()) {
								//If this occurs we need to wait a while before trying again.
								logger.info("Failed to place order at provider (place order exception) for bundle " + bundle.getId()
										+ " (" + sse.getMessage() + " - number of nbrSellStarExceptions: " + nbrSellStarExceptions + ").");
								logger.info("Stopping ordering batch and trying again later.");
								break;
							} else {
								throw new SellStarRuntimeException("Exception when placing order: " + sse.getMessage(), null, 0, sse);
							}
						} catch (Exception e) {
							if (section.isInCriticalSection() || section.isTransactionMustGoThrough() || (e instanceof SellStarRuntimeException && ((SellStarRuntimeException)e).getNbrAllowedErrors() == 0)) {
								logger.error("Exception when placing order (IN CRITICAL SECTION/transaction must go through/nbrAllowedErrors is zero): ",e);
								throw new SellStarRuntimeException("Exception when placing order: " + e.getMessage(), "in critical section", 0, e);
							} else {
								logger.debug("Exception when placing order (not in critical section): ", e);
								logger.error("Exception when placing order (not in critical section): " + e.getMessage());
								//throw new SellStarRuntimeException("Exception when placing order: " + e.getMessage(), null, allowedErrors, e);
								otherError++;
							}
						}
						if ((nbrSellStarExceptions > 10 && succeeded == 0) || nbrSellStarExceptions > 20) {
							throw new SellStarRuntimeException("Too many errors in place order, bailing out", "succeeded = "
									+ succeeded + ", nbrSellStarExceptions = " + nbrSellStarExceptions + ", otherError = " + otherError, 3, null);
						}
						if (otherError > 2) {
							throw new SellStarRuntimeException("Too many errors in place order, bailing out", "succeeded = "
									+ succeeded + ", nbrSellStarExceptions = " + nbrSellStarExceptions + ", otherError = " + otherError, 1, null);
						}
					}
					//If there is some serious problem with ordering, then we want to make sure that we go to the error
					//state (for example if we keep getting captcha error for every single order). 
					if (succeeded == 0 && bundleList.size() > 10) {
						//throw new PlaceOrderException("Going to error state because problem in ordering (0 bundles gone through and "
							//	+ bundleList.size() + " awaiting bundles).");
						throw new SellStarRuntimeException("Going to error state because problem in ordering", "0 bundles gone through and "
								+ bundleList.size() + " awaiting bundles", 0, null);
					}
				} finally {
					logger.debug("Logging out after updating provider prices for provider " + provider.getName());
					try {
						providerHandler.logout(provider,true);
					} catch (Exception e) {
						logger.error("Problem logging out from provider at end of placeOrders",e);
					}
				}
			}
		}
		logger.info("Done placing orders.");
	}

	private void fetchExchangeRates() throws Exception {
		ExchangeRateFetcher exchangeRateFetcher = (ExchangeRateFetcher) ApplicationContextPlaceholder.applicationContext
				.getBean("exchangeRateFetcher");
		CurrencyDAO currencyDAO = (CurrencyDAO) ApplicationContextPlaceholder.applicationContext
			.getBean("currencyDAO");
		logger.debug("Fetching exchange rates.");
		//BigDecimal oneDollarInEuro = currencyChecker.getDollar2Euro(new BigDecimal("1.0"));
		BigDecimal oneDollarInEuro = currencyDAO.convertCurrency(Currency.DOLLAR,Currency.EURO, new BigDecimal("1.0"));
		logger.debug("Before fetch: 1.0 dollar is " + oneDollarInEuro + " euros.");
		//BigDecimal oneEuroInDollar = currencyChecker.getEuros2Dollar(new BigDecimal("1.0"));
		BigDecimal oneEuroInDollar = currencyDAO.convertCurrency(Currency.EURO,Currency.DOLLAR, new BigDecimal("1.0"));
		logger.debug("Before fetch: 1.0 euro is " + oneEuroInDollar + " dollar.");
		exchangeRateFetcher.fetchExchangeRates();
		//oneDollarInEuro = currencyChecker.getDollar2Euro(new BigDecimal("1.0"));
		oneDollarInEuro = currencyDAO.convertCurrency(Currency.DOLLAR,Currency.EURO, new BigDecimal("1.0"));
		logger.debug("After fetch: 1.0 dollar is " + oneDollarInEuro + " euros.");
		//oneEuroInDollar = currencyChecker.getEuros2Dollar(new BigDecimal("1.0"));
		oneEuroInDollar = currencyDAO.convertCurrency(Currency.EURO,Currency.DOLLAR, new BigDecimal("1.0"));
		logger.debug("After fetch: 1.0 euro is " + oneEuroInDollar + " dollar.");
		logger.debug("Done fetching exchange rates.");
	}

	private void checkConsistency() throws Exception {
		logger.info("Running consistency checker.");
		ConsistencyChecker consistencyChecker = (ConsistencyChecker) ApplicationContextPlaceholder.applicationContext.getBean("consistencyChecker");
		consistencyChecker.run();
		logger.info("Done running consistency checker.");
	}

	/*private void getItems(String[] args) throws Exception {
		logger.info("Getting items");
		ProviderOrder providerOrder = null;
		DxProductHelper dxProductHelper = (DxProductHelper) ApplicationContextPlaceholder.applicationContext.getBean("dxProductHelper");
		ImageHelper imageHelper = (ImageHelper) ApplicationContextPlaceholder.applicationContext.getBean("imageHelper");
		/*OptionParser parser = new OptionParser() {
            {
            	accepts( "operator", "Operator select" ).withRequiredArg();
                accepts( "type", "Fetch type" ).withOptionalArg();
            }
        };

		OptionSet options = parser.parse( args );
		
		if (options.has("operator") && options.hasArgument("operator")){
			String providerName = options.valueOf("operator").toString();
			System.out.println("providerName: " + providerName);
		}/
		
		if (args.length < 2) {
			logger.warn("You need to give a list of items to fetch.");
			printUsage();
			return;
		}

		logger.info("Checking items");
		
		boolean oldWaterMark = false;
		
		List<String> items = new ArrayList<String>();
		for (int i = 1; i < args.length; i++) {
			if (args[i].indexOf("-") > 0) {
				int start = Integer.parseInt((args[i].substring(0, args[i].indexOf("-"))).substring(4));
				int end = Integer.parseInt((args[i].substring(args[i].indexOf("-") + 1)).substring(4));
				for (int j = start; j <= end; j++) {
					items.add("sku." + j);
					//logger.debug("Item " + "sku." + j + " added to list");
				}
			} else if (args[i].equals("old")){
				oldWaterMark = true;
			}
			else {	
				items.add(args[i]);
				logger.info("Item " + args[i] + " added to list");
			}
		}

		int errors = 0;
		List<File> createdFiles = null;
		for (String item : items) {
			try {
				createdFiles = new ArrayList<File>();
				//TODO: take the value of the maxPriceExclVat parameter to getGoodProductIds from market.maxBundleSize
				Product product = dxProductHelper.createNewProduct(item, createdFiles,
						true, null, new VirtualCoin(Currency.EURO,new BigDecimal("41.0")), oldWaterMark);
				if (product != null) {
					imageHelper.checkProductImages(product);
				}
			} catch (Exception e) {
				errors++;
				logger.debug("Error in getItems:", e);
				/* DON'T remove the created files from the harddisk, because they will at some later point be
				 * uploaded again by the uploadImages-module! */
				/*logger.error("Error in getItems, removing created files.", e);
				for (File file : createdFiles) {
					if (file.exists()) {
						FileUtils.forceDelete(file);
					}
				}/
				if (errors > 10) {
					throw new Exception("More than ten errors in getItems. Stopping.");
				}
			}
		}
	}*/
	
	private void handleReceivedFeedback(String[] args) throws Exception {
		logger.info("Handling received feedback");
		FeedbackReader feedbackReader = (FeedbackReader) ApplicationContextPlaceholder.applicationContext.getBean("feedbackReader");
		SellerMarketDAO sellerMarketDAO = (SellerMarketDAO) ApplicationContextPlaceholder.applicationContext.getBean("sellerMarketDAO");
		List<SellerMarket> sellerMarkets = sellerMarketDAO.loadAll();
		for (SellerMarket sellerMarket : sellerMarkets) {
			MarketRobot lister = getListerForMarket(sellerMarket.getMarket());
			if (lister == null) {
				throw new Exception("Reading feedback skipped for market " + sellerMarket.getMarket().getName() + " because no lister was found.");
			}
			logger.info("Reading feedback for market " + sellerMarket.getMarket().getName()
					+ " and seller " + sellerMarket.getSeller().getName() + ").");
			 //TODO: uncomment the following:
			/*try {
				lister.login(sellerMarket);
			} catch (MarketTemporarilyDownException e) {
				logger.info("Cannot read feedback because market is down.");
				//In some cases the login can go through but we still get here because of
				//selenium errors, so logout just in case.
				lister.logout();
				continue;
			} catch (MarketLoggedOnAsWrongUserException e) {
				logger.info("Cannot read feedback because market is in use by a different seller.");
				continue;
			} catch (Exception e) {
				//This usually means that the huutonet web page is temporarily screwed up
				throw new SellStarRuntimeException("Unexpected error logging on to market in handleReceivedFeedback", e.getMessage(), 1, e);
			}*/
			
			try {
				feedbackReader.readFeedback(lister,sellerMarket);
			} catch (MarketLoggedOnAsWrongUserException e) {
				/* We should never get here so throw an exception here. */
				throw e;
			} catch (LoggedOutFromMarketException e) {
				/* We could possibly get here if we at some point start checking for logged out
				 * errors (for huutonet you don't need to be logged in to read feedback so the
				 * huutonet lister never throws this and for mikko feedback reading isn't yet implemented).
				 * For now let's throw an error if we get here. */
				throw e;
			} finally {
				logger.debug("Logging out after reading feedback for sellerMarket " + sellerMarket.getId());
				//lister.logout(); //TODO: uncomment this row
			}
		}
		logger.info("Done handling received feedback");
	}
	
	private void updateTechnicalSpecsToItems(String[] args) {
		logger.info("Updating items");
		//DxProductHelper dxProductHelper = (DxProductHelper) ApplicationContextPlaceholder.applicationContext.getBean("dxProductHelper");
		//FocalPriceProvider focalPriceProvider = (FocalPriceProvider) ApplicationContextPlaceholder.applicationContext.getBean("focalPriceProvider");
		ProviderHandler providerHandler = (ProviderHandler) ApplicationContextPlaceholder.applicationContext.getBean("providerHandler");
		ProductDAO productDAO = (ProductDAO) ApplicationContextPlaceholder.applicationContext.getBean("productDAO");
		ProviderDAO providerDAO = (ProviderDAO) ApplicationContextPlaceholder.applicationContext.getBean("providerDAO");
		int i = 0;
		//List<Product> productsToUpdate = productDAO.getProductsWithoutAdTemplate(providerDAO.getProviderByName(Provider.DEAL_EXTREME_PROVIDER_NAME));
		//List<Product> productsToUpdate = productDAO.getProductsWithEmptyTechnicalSpecs(providerDAO.getProviderByName(Provider.FOCAL_PRICE_PROVIDER_NAME));
		//List<Product> productsToUpdate = productDAO.getProducts(providerDAO.getProviderByName(Provider.HAUTALA_PROVIDER_NAME));
		List<Product> productsToUpdate = productDAO.getProducts(providerDAO.getProviderByName(Provider.FOCAL_PRICE_PROVIDER_NAME));

		logger.info("Updating technical specs for " + productsToUpdate.size() + " products.");
		try {
			for (Product product : productsToUpdate) {
				//dxProductHelper.updateTechnicalSpecsOfProduct(product);
				//focalPriceProvider.updateTechnicalSpecs(product);
				try {
					providerHandler.updateTechnicalSpecs(product);
					logger.info("-------");
					i++;
				} catch (Exception e) {
					logger.info("updateTechnicalSpecs failed for product " + product.getId() + " (" + product.getProviderProdId() + "): ", e);
				}
				if (i % 10 == 0) {
					logger.info("Gone through " + i + "/" + productsToUpdate.size()
							+ " of the products for updateTechnicalSpecs.");
				}
			}
		} catch (Exception e) {
			logger.error("updateTechnicalSpecsToItems failed", e);
		}
		logger.info("Done updating items (" + i + "/" + productsToUpdate.size() + " items updated).");
	}

	private void testTemplate(String[] args) {
		TemplateTester templateTester = (TemplateTester) ApplicationContextPlaceholder.applicationContext.getBean("templateTester");
		MarketDAO marketDAO = (MarketDAO) ApplicationContextPlaceholder.applicationContext.getBean("marketDAO");
		Market huutonetMarket = marketDAO.getByName(Market.HUUTONET_MARKET_NAME);
		if (args.length < 2) {
			logger.info("No adtemplate specified, using the first active one.");
			templateTester.createTemplate(huutonetMarket);
		} else {
			logger.info("Using adtemplate having adTemplateId = " + args[1]);
			templateTester.createTemplate(Long.parseLong(args[1]),huutonetMarket);
		}
	}
	
	private void uploadImages(String[] args) throws Exception {
		logger.info("Uploading images");
		EmailSender.sendOperationProblemReport("uploadimages doesn't work","Upload images should be modified now that the pictures are at sellstar.fi");

		/*ImageHelper imageHelper = (ImageHelper) ApplicationContextPlaceholder.applicationContext.getBean("imageHelper");
		ProductDAO productDAO = (ProductDAO) ApplicationContextPlaceholder.applicationContext.getBean("productDAO");
		List<Product> products = new ArrayList<Product>();
		
		if (args.length > 1) {
			if (args[1].startsWith("sku.")) {
				ProviderDAO providerDAO = (ProviderDAO) ApplicationContextPlaceholder.applicationContext.getBean("providerDAO");
				Product product = productDAO
					.getByProviderProdId(args[1], providerDAO.getProviderByName(Provider.DEAL_EXTREME_PROVIDER_NAME));
				if (product == null) {
					throw new Exception("Product with provider id " + args[1] + " could not be found");
				} else {
					products.add(product);
				}
			} else {
				products = productDAO.getByProductId(args[1]);
				if (products.size() == 0) {
					throw new Exception("Product with provider id " + args[1] + " could not be found.");
				} else if (products.size() > 1) {
					throw new Exception("Several products with provider id " + args[1] + " were found.");
				}
			}
		} else {
			products = productDAO.loadAll();
		}
		
		int failed = 0;
		int productCount = 0;
		for (Product product : products) {
			productCount++;
			if (!imageHelper.checkProductImages(product)) {
				failed++;
			}
			if (failed > products.size() / 20) {
				throw new Exception("Too many failures while uploading images. Bailing out.");
			}
			if (productCount % 10 == 0) {
				logger.info("Gone through " + productCount + "/" + products.size() + " of the products");
			}
		}*/
		logger.info("Done uploading images");
	}
	
	private void confirmPaymentToCustomer(String[] args) throws Exception {
		//OrderDAO orderDAO = (OrderDAO) ApplicationContextPlaceholder.applicationContext.getBean("orderDAO");
		//List<Order> orderList = orderDAO.getPaidOrdersWhereCustomerIsNotInformed();
		OrderBundleDAO orderBundleDAO = (OrderBundleDAO) ApplicationContextPlaceholder.applicationContext.getBean("orderBundleDAO");
		List<OrderBundle> bundleList = orderBundleDAO.getPaidOrPartlyPaidBundlesWhereCustomerIsNotInformed();
		CustomerPaymentInformer customerPaymentInformer = (CustomerPaymentInformer) ApplicationContextPlaceholder.applicationContext.getBean("customerPaymentInformer");
		for (OrderBundle bundle : bundleList) {
			customerPaymentInformer.confirmPaymentToCustomer(bundle);
		}
	}
	
	private void fixFaultyPayments(String[] args) throws Exception {
		FaultyPaymentFixer faultyPaymentFixer = (FaultyPaymentFixer) ApplicationContextPlaceholder.applicationContext.getBean("faultyPaymentFixer");
		try {
			faultyPaymentFixer.fixFaultyPayments(true);
		} catch (RuntimeException e) {
			if (e.getMessage() != null && (e.getMessage().startsWith("transaction timeout expired")
					|| (e.getMessage().startsWith("Could not commit Hibernate transaction"))
					|| (e.getMessage().startsWith("Could not roll back Hibernate transaction"))
					/*|| (e.getMessage().startsWith("Transaction rolled back because it has been marked as rollback-only"))*/)) {
				logger.info("Got a transaction timeout for fixFaultyPayments.");
				throw new SellStarRuntimeException(e.getMessage(), null, 3, e);
			} else {
				throw new SellStarRuntimeException(e.getMessage(), null, 0, e);
			}
		}
	}
	
	private void fixFaultyTransactions(String[] args) throws Exception {
		FaultyPaymentFixer faultyPaymentFixer = (FaultyPaymentFixer) ApplicationContextPlaceholder.applicationContext.getBean("faultyPaymentFixer");
		boolean sendEmails = false;
		if (args.length > 1) {
			sendEmails = Boolean.parseBoolean(args[1]);
		}
		logger.info("Fixing faulty transactions (send emails = " + sendEmails + ")");
		faultyPaymentFixer.fixFaultyTransactions(sendEmails);
	}
	
	private void giveFeedback(String[] args) throws Exception {
		FeedbackGiver feedbackGiver = (FeedbackGiver) ApplicationContextPlaceholder.applicationContext.getBean("feedbackGiver");
		OrderDAO orderDAO = (OrderDAO) ApplicationContextPlaceholder.applicationContext.getBean("orderDAO");
		
		SellerMarketDAO sellerMarketDAO = (SellerMarketDAO) ApplicationContextPlaceholder.applicationContext.getBean("sellerMarketDAO");
		List<SellerMarket> sellerMarkets = sellerMarketDAO.loadAll();
		for (SellerMarket sellerMarket : sellerMarkets) {
			MarketRobot lister = getListerForMarket(sellerMarket.getMarket());
			if (lister == null) {
				throw new Exception("Giving feedback skipped for market " + sellerMarket.getMarket().getName() + " because no lister was found.");
			}
			try {
				lister.login(sellerMarket);
			} catch (MarketTemporarilyDownException e) {
				logger.info("Cannot give feedback because market is down: " + e.getMessage());
				//In some cases the login can go through but we still get here because of
				//selenium errors, so logout just in case.
				lister.logout();
				continue;
			} catch (MarketLoggedOnAsWrongUserException e) {
				logger.info("Cannot give feedback because market is in use by a different seller.");
				continue;
			} catch (Exception e) {
				//This usually means that the huutonet web page is temporarily screwed up
				throw new SellStarRuntimeException("Unexpected error logging on to market in giveFeedback", e.getMessage(), 1, e);
			}
			List<Order> ordersWithoutFeedback = orderDAO.getOrdersWithoutFeedbackInPaidOrLaterStates(sellerMarket);
			int inListAtStart = ordersWithoutFeedback.size();
			logger.info("Number of orders to give feedback for: " + inListAtStart);
			int count = 0;
			try {
				int failed = 0;
				boolean hasMore = true;
				while (hasMore) {
					try {
						int inList = ordersWithoutFeedback.size();
						hasMore = feedbackGiver.givePositiveFeedback(ordersWithoutFeedback,lister,sellerMarket);
						count += inList - ordersWithoutFeedback.size();
						logger.info("Gone through " + count + "/" + inListAtStart + " of the orders for giveFeedback.");
						inList = ordersWithoutFeedback.size();
					} catch (MarketLoggedOnAsWrongUserException e) {
						/* We should never get here so throw an exception here. */
						throw e;
					} catch (LoggedOutFromMarketException e) {
						/* We can get here if some other process has logged us out. */
						//logger.info("giveFeedback: we got logged out, logging in again as " + sellerMarket.getSeller().getName());
						//lister.login(sellerMarket);
						throw e;
					} catch (Exception e) {
						failed++;
						logger.debug("giveFeedback failed", e);
						// Don't inform the operator about this, because usually it works if we try again.
						// TODO: Add a new field order.feedback_given_attempts which is a counter which is incremented
						// each time we try to give feedback and inform the operator if this counter reaches three.
						//EmailSender.sendOperationProblemReport("give feedback","giveFeedback failed for order "
							//	+ order.getId() + "\n\n" + getStackTrace(e));
					}
					if (failed > inListAtStart / 2) {
						//throw new Exception("Too many failures while giving feedback. Bailing out.");
						throw new SellStarRuntimeException("Too many failures while giving feedback. Bailing out.",
								"failed = " + failed + ", inListAtStart = " + inListAtStart, 3, null);
					}
				}
			} finally {
				logger.debug("Logging out after giving feedback for sellerMarket " + sellerMarket.getId());
				lister.logout();
			}
		}
	}
	
	/* Update orders that have been for a certain time in a certain state and/or inform
	 * the customer if necessary. */
	private void orderHousekeeping(String[] args) throws Exception {
		OrderBundleDAO orderBundleDAO = (OrderBundleDAO) ApplicationContextPlaceholder.applicationContext.getBean("orderBundleDAO");
		List<OrderBundle> allOpenBundles = orderBundleDAO.getAllOpenBundles();
		OrderBundleHousekeeping orderBundleHousekeeping = (OrderBundleHousekeeping) ApplicationContextPlaceholder.applicationContext.getBean("orderBundleHousekeeping");
		logger.info("Doing housekeeping for " + allOpenBundles.size() + " bundles.");
		int count = 0;
		for (OrderBundle bundle : allOpenBundles) {
			orderBundleHousekeeping.housekeeping(bundle);
			count++;
			if (count % 10 == 0) {
				logger.info("Gone through " + count + "/" + allOpenBundles.size() + " of the bundles for housekeeping.");
			}
		}
	}
	
	private void updateStatusOfOrders(String[] args) throws Exception {
		/* First fetch updated status information from provider for unshipped orders. */
		CriticalSectionInfo section = new CriticalSectionInfo();
		OrderBundleDAO orderBundleDAO = (OrderBundleDAO) ApplicationContextPlaceholder.applicationContext.getBean("orderBundleDAO");
		TrackingDAO trackingDAO = (TrackingDAO) ApplicationContextPlaceholder.applicationContext.getBean("trackingDAO");
		ProviderDAO providerDAO = (ProviderDAO) ApplicationContextPlaceholder.applicationContext.getBean("providerDAO");
		ProviderHandler providerHandler = (ProviderHandler) ApplicationContextPlaceholder.applicationContext.getBean("providerHandler");
		List<Provider> providers = providerDAO.loadAll();
		for (Provider provider : providers) {
			List<OrderBundle> openBundles = orderBundleDAO.getBundlesForStatusUpdate(provider);
			if (openBundles.size() > 0) {
				logger.info("Updating info for " + openBundles.size() + " bundles (ordered and unshipped)"
						+ " for provider " + provider.getName() + ".");
				try {
					providerHandler.login(provider,3);
				} catch (Exception e) {
					logger.info("Cannot logon to provider " + provider.getName() + " - aborting.");
					providerHandler.logout(provider,false);
					if (e instanceof ProviderTemporarilyDownException) {
						throw new SellStarRuntimeException("Cannot logon to provider " + provider.getName() + ": " + e.getMessage(), null, 3, e);
					}
					throw new SellStarRuntimeException("Cannot logon to provider " + provider.getName() + ": " + e.getMessage(), null, 1, e);
				}
				try {
					int nbrFailed = 0;
					int nbrPostTrackingFailed = 0;
					int transactionTimeout = 0;
					int count = 0;
					for (OrderBundle bundle : openBundles) {
						Exception badError = null;
						try {
							count++;
							providerHandler.updateOrderStatus(bundle,section);
							section.setTransactionMustGoThrough(false); //set this to false after the transaction is done
						} catch (PostTrackingDownException e) {
							logger.debug("Problem updating status of bundle " + bundle.getId() + " (post tracking is down): " + e.getMessage());
							nbrPostTrackingFailed++;
							if (section.isInCriticalSection() || section.isTransactionMustGoThrough()) {
								badError = e;
							}
						} catch (ProviderTemporarilyDownException e) {
							logger.info("Problem updating status of bundle " + bundle.getId() + " (provider temporarily down).");
							logger.debug("Problem updating status of bundle " + bundle.getId() + " (provider temporarily down): ",e);
							nbrFailed++;
							if (section.isInCriticalSection() || section.isTransactionMustGoThrough()) {
								badError = e;
							}
						} catch (Exception e) {
							if (section.isInCriticalSection() || section.isTransactionMustGoThrough() || (e instanceof SellStarRuntimeException && ((SellStarRuntimeException)e).getNbrAllowedErrors() == 0)) {
								badError = e;
							}
							if (e.getMessage() != null && (e.getMessage().startsWith("transaction timeout expired")
									|| (e.getMessage().startsWith("Could not commit Hibernate transaction"))
									|| (e.getMessage().startsWith("Could not roll back Hibernate transaction"))
									/*|| (e.getMessage().startsWith("Transaction rolled back because it has been marked as rollback-only"))*/)) {
								logger.info("Got a transaction error for updateStatusOfOrders1, still continuing.");
								transactionTimeout++;
							} else {
								throw new SellStarRuntimeException(e.getMessage(), null, 0, e);
							}
						}
						if (badError != null) {
							logger.error("Exception when updating status of bundle (IN CRITICAL SECTION/transaction must go through/nbrAllowedErrors is zero): ",badError);
							throw new SellStarRuntimeException("Exception when updating status of bundle: " + badError.getMessage(), "in critical section", 0, badError);
						}
						if (nbrFailed >= 30 || nbrPostTrackingFailed >= 10 || transactionTimeout > 1) {
							//throw new Exception("Too many errors in updateStatusOfOrders1 (nbrFailed = " + nbrFailed + ", nbrPostTrackingFailed = " + nbrPostTrackingFailed + ")");
							throw new SellStarRuntimeException("Too many errors in updateStatusOfOrders1",
									"nbrFailed = " + nbrFailed + ", nbrPostTrackingFailed = " + nbrPostTrackingFailed
									+ ", transactionTimeout = " + transactionTimeout, 3, null);
						}
						if (count % 10 == 0) {
							logger.info("Gone through " + count + "/" + openBundles.size()
									+ " of the bundles for updateStatusOfOrders.");
						}
					}
				} finally {
					logger.debug("Logging out after updating info of orders for provider " + provider.getName());
					providerHandler.logout(provider,false);
				}
			}
		}

		/* Then fetch updated tracking information for shipped orders. */
		int nbrFailed = 0;
		int nbrSucceeded = 0;
		GeneralPostService generalPostService = (GeneralPostService) ApplicationContextPlaceholder.applicationContext.getBean("generalPostService");
		List<Tracking> locationTrackingList = trackingDAO.getTrackingObjectsForLocationTracking();
		logger.info("Updating info for " + locationTrackingList.size() + " tracking objects (shipped and not closed).");
		int count = 0;
		for (Tracking trackingObject : locationTrackingList) {
			try {
				count++;
				generalPostService.updateShippingStatus(trackingObject);
				nbrSucceeded++;
			} catch (PostTrackingDownException e) {
				nbrFailed++;
				logger.info("Problem updating status of tracking object " + trackingObject.getId() + " (post tracking is down) - "
						+ nbrFailed + " failed: " + e.getMessage());
			} catch (Exception e) {
				if (e.getMessage() != null && (e.getMessage().startsWith("transaction timeout expired")
						|| (e.getMessage().startsWith("Could not commit Hibernate transaction"))
						|| (e.getMessage().startsWith("Could not roll back Hibernate transaction"))
						/*|| (e.getMessage().startsWith("Transaction rolled back because it has been marked as rollback-only"))*/)) {
					nbrFailed++;
					logger.info("Got a transaction error for updateStatusOfOrders2, still continuing.");
				} else {
					throw new SellStarRuntimeException(e.getMessage(), null, 0, e);
				}
			}
			if (count % 10 == 0) {
				logger.info("Gone through " + count + "/" + locationTrackingList.size()
						+ " of the tracking objects for updateStatusOfOrders.");
			}
		}
		
		if ((nbrFailed >= 10 && nbrSucceeded == 0) || nbrFailed >= 10) {
			//throw new Exception("Too many errors in updateStatusOfOrders2 (nbrFailed="
				//	+ nbrFailed + ",nbrSucceeded=" + nbrSucceeded + ")");
			throw new SellStarRuntimeException("Too many errors in updateStatusOfOrders2",
					"nbrFailed=" + nbrFailed + ",nbrSucceeded=" + nbrSucceeded, 3, null);
		}
	}

	private void updateProviderCommunication(String[] args) throws Exception {
		ProviderQuestionDAO providerQuestionDAO = (ProviderQuestionDAO) ApplicationContextPlaceholder.applicationContext
			.getBean("providerQuestionDAO");
		List<ProviderQuestion> conversationList = providerQuestionDAO.getOpenConversations();
		ProviderHandler providerHandler = (ProviderHandler) ApplicationContextPlaceholder.applicationContext
			.getBean("providerHandler");
		logger.info("Updating info for " + conversationList.size() + " open conversations.");
		int nbrFailed = 0;
		int count = 0;
		for (ProviderQuestion conversation : conversationList) {
			try {
				count++;
				providerHandler.updateConversationAtProvider(null,conversation);
			} catch (SeleniumException e) {
				if (e.getMessage() != null && (e.getMessage().indexOf("Command timed out") >= 0
						|| e.getMessage().indexOf("Timed out after") >= 0
						|| (e.getMessage().indexOf("Element") > -1 && e.getMessage().endsWith("not found")))) {
					nbrFailed++;
					logger.info("Problem updating provider conversation " + conversation.getId() + " (" + nbrFailed + " failed):", e);
				} else {
					throw e;
				}
			} catch (ProviderTemporarilyDownException e) {
				logger.info("Problem updating provider conversation " + conversation.getId() + " (provider temporarily down).");
				logger.debug("Problem updating provider conversation " + conversation.getId() + " (provider temporarily down): ",e);
				nbrFailed++;
			}
			if (nbrFailed >= 5) {
				throw new Exception("Too many errors in updateProviderCommunication (" + nbrFailed + ")");
			}
			if (count % 10 == 0) {
				logger.info("Gone through " + count + "/" + conversationList.size()
						+ " of the conversations for updateProviderCommunication.");
			}
		}
	}
	
	private void configCategories(String[] args) throws Exception {
		CategoryAdmin categoryAdmin = new CategoryAdmin();
		boolean admin = false;
		if (args.length > 1) {
			admin = new Boolean(args[1]);
		}
		String encoding = CharSetOption.getEncoding();
		BufferedReader br = null;
		try {
			br = new BufferedReader(new InputStreamReader(System.in, encoding));
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
			System.exit(1);
		}
		PrintWriter out = null;
		try {
			out = new PrintWriter(new OutputStreamWriter(System.out, encoding));
		} catch (UnsupportedEncodingException e1) {
			e1.printStackTrace();
			System.exit(1);
		}
		categoryAdmin.run(admin,br,out,null);
	}
	
	private void createCategorySuggestions() throws Exception {
		logger.info("Creating category suggestions.");
		CategoryAdmin categoryAdmin = new CategoryAdmin();
		String encoding = "UTF-8";
		BufferedReader br = null;
		try {
			br = new BufferedReader(new InputStreamReader(System.in, encoding));
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
			System.exit(1);
		}
		PrintWriter out = null;
		try {
			out = new PrintWriter(new OutputStreamWriter(System.out, encoding));
		} catch (UnsupportedEncodingException e1) {
			e1.printStackTrace();
			System.exit(1);
		}
		categoryAdmin.run(true,br,out,"createcategorysuggestions");
		logger.info("Done creating category suggestions.");
	}
	
}
