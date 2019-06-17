package fi.jonix.huutonet.tools;

import java.io.BufferedReader;
import java.io.Console;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.api.translate.Language;
import com.google.api.translate.Translate;

import fi.jonix.huutonet.domain.model.AdTemplate;
import fi.jonix.huutonet.domain.model.Category;
import fi.jonix.huutonet.domain.model.Product;
import fi.jonix.huutonet.domain.model.Seller;
import fi.jonix.huutonet.domain.model.Translation;
import fi.jonix.huutonet.domain.model.dao.AdTemplateDAO;
import fi.jonix.huutonet.domain.model.dao.CategoryDAO;
import fi.jonix.huutonet.domain.model.dao.ProductDAO;
import fi.jonix.huutonet.domain.model.dao.SettingsDAO;
import fi.jonix.huutonet.domain.model.dao.TranslationDAO;
import fi.jonix.huutonet.domain.model.dao.TranslationDAOImpl;
import fi.jonix.huutonet.exception.FillItemsQuitException;
import fi.jonix.huutonet.exception.FillItemsSkipException;
import fi.jonix.huutonet.exception.ProductNotFoundAtProviderException;
import fi.jonix.huutonet.exception.ProviderTemporarilyDownException;
import fi.jonix.huutonet.order.ProviderOrder;
import fi.jonix.huutonet.robot.PageOpenerThread;
import fi.jonix.huutonet.robot.SeleniumRobot;
import fi.jonix.huutonet.tools.translation.HtmlProductPageCreator;

@Component(value = "cmAdCreator")
@Deprecated
public class CmAdCreator {

	private static String SHIPPING_DESCRIPTION = "Keskitetyn tilaus- ja toimitusprosessimme vuoksi, tilausten käsittely maksun saapumisesta postittamiseen on noin 5-8 arkipäivää, joten tuote tulee postitse noin 10:n arkipäivän kuluttua maksun saapumisesta.";
	private static String LOCATION = "Postin kautta kotiinkuljetettuna";
	private static String SLOGAN = "";

	public static final Logger logger = Logger.getLogger(CmAdCreator.class);

	private static String INPUT_GO_BACKWARD = "-1";
	private static String INPUT_GO_TO_BEGINING = "-10";
	private static String INPUT_DO_NOT_SAVE_TRANSLATION = "Q ";
	private static String INPUT_ROBOT_TYPE = "1";
	private static String DELETE_PRODUCT = "DELETE";
	private static String DELETE_LINE = "D";
	private static String QUIT = "QUIT";
	private static String SKIP = "SKIP";

	private static int INDEX_HEADLINE = 0;
	private static int INDEX_SUMMARY = 1;
	private static int INDEX_DETAILS = 2;
	private static int INDEX_TECHNICAL_SPECS = 3;
	private static int INDEX_CATEGORY = 4;
	private static int INDEX_SHOW_REPORT = 6;
	private static int INDEX_FINNISH = 7;
	private static int INDEX_DELETE = -100;
	private static int INDEX_QUIT = -200;
	private static int INDEX_SKIP = -300;

	public static int RETURN_SUCCESS = 1;
	public static int RETURN_FAILURE = 2;
	public static int RETURN_DELETE = 3;
	public static int RETURN_QUIT = 4;
	public static int RETURN_SKIP = 5;

	private static boolean SUMMARY_IN_USE = false;
	private static boolean DETAILS_IN_USE = true;

	public enum TranslationMode {
		ALL, HEADLINE_AND_DETAILS
	};

	private Console console;
	private Keyboard keyboard;

	@Autowired
	private TranslationDAO translationDAO;

	@Autowired
	private SettingsDAO settingsDAO;

	@Autowired
	private AdTemplateDAO adTemplateDAO;

	// @Autowired
	// private MarketDAO marketDAO;

	@Autowired
	private ProductDAO productDAO;

	@Autowired
	private CategoryDAO categoryDAO;

	@Autowired
	CategoryHelper categoryHelper;

	@Autowired
	private CategoryResolverThreadBean categoryResolverThreadBean;

	@Autowired
	private HtmlProductPageCreator htmlProductPageCreator;

	@Autowired
	private SeleniumRobot seleniumRobot;

	private AdTemplate siblingAdTemplate;

	private PrintWriter out;

	// Stupid hacking
	enum TranslationStatus {
		FOUND, PARTLY_FOUND, NOT_FOUND
	}

	TranslationStatus translationStatus = TranslationStatus.NOT_FOUND;

	public int fillItem(Product product, String encoding, String addedBy,
			ProviderOrder providerOrder, boolean linuxConsole,
			boolean useLocalTempHtml, TranslationMode translationMode, Seller seller, StringBuffer translations)
			throws Exception {
		throw new RuntimeException("This method is no longer maintained (obsolete).");
		/* Quit early if product wasn't found. */
		/*try {
			providerOrder.productExistsAtProvider(product);
		} catch (ProviderTemporarilyDownException e) {
			/* Skip this product for now if provider was temporarily down. /
			logger.info("Provider temporarily down, couldn't check if product " + product.getId() + " exists. Skipping for now.");
			throw new FillItemsSkipException();
		} catch (ProductNotFoundAtProviderException e) {
			logger.info("Item not available");
			// Delete product
			this.productDAO.delete(product);
			return RETURN_FAILURE;
		}

		AdTemplate adTemplate = new AdTemplate();
		adTemplate.setSeller(seller);
		adTemplate.setLanguage("fi_FI");
		adTemplate.setProduct(product);

		File temp = File.createTempFile("sellstar", "html");

		PageOpenerThread pageOpener = null;
		if (!useLocalTempHtml) {
			pageOpener = new PageOpenerThread(providerOrder,
					product.getProviderProdId());
		} else {
			this.htmlProductPageCreator.createHtmlProductPage(temp, product);
			pageOpener = new PageOpenerThread(this.seleniumRobot.getSelenium(),
					"file:///" + temp.getAbsolutePath());
		}

		pageOpener.start();

		categoryResolverThreadBean.initialize();
		CategoryResolverThread categoryResolverThread = new CategoryResolverThread(
				adTemplate);
		categoryResolverThread.start();

		int value = this.getInputFromUser(adTemplate, product, encoding,
				linuxConsole, translationMode, seller, translations);
		temp.deleteOnExit();
		categoryResolverThreadBean.stopWork();
		pageOpener.stopWork();
		
		/* Wait for the page opener thread if necessary and then get the price. */
		/*if (!pageOpener.isDone()) {
			printToOutput("Waiting for page opener...");
			while (!pageOpener.isDone()) {
				Thread.sleep(50);
			}
			printToOutput("done");
		}
		if (!pageOpener.isSuccess()) {
			printToOutput("Page opener failed, skipping this product.");
			value = RETURN_SKIP;
		} else /if (!useLocalTempHtml) {
			try {
				providerOrder.getPriceFromLoadedPage(false);
			} catch (Exception e) {
				logger.info("Item not available");
				// Delete product
				this.productDAO.delete(product);
				return RETURN_FAILURE;
			}
		}
		if (value == RETURN_SUCCESS) {
			this.setHardCodedStuff(adTemplate, addedBy);
			logger.debug("adTemplate.id = " + adTemplate.getId());
			if (adTemplate.getProduct().getCategory() != null)
				logger.debug(", adTemplate.product.category.id = "
						+ adTemplate.getProduct().getCategory().getId());
			adTemplateDAO.save(adTemplate);
			adTemplateDAO.flush();
		} else if (value == RETURN_DELETE) {
			// Delete product
			this.productDAO.delete(product);
		} else if (value == RETURN_QUIT) {
			/*
			 * We shouldn't just return on quit because that will persist
			 * already made changes, so let's throw an exception here.
			 /
			throw new FillItemsQuitException();
		} else if (value == RETURN_SKIP) {
			/*
			 * We shouldn't just return on skip because that will persist
			 * already made changes, so let's throw an exception here.
			 /
			throw new FillItemsSkipException();
		}
		return value;*/
	}

	private void setHardCodedStuff(AdTemplate adTemplate, String addedBy) {
		adTemplate.setActive(true);
		adTemplate.setLocation(LOCATION);
		adTemplate.setBeanName("templateGenerator");
		// Market market = marketDAO.getByName(Market.HUUTONET_MARKET_NAME);
		//adTemplate.setMinBidIncreaseInEuro(new BigDecimal("0.1"));
		//adTemplate.setMinBidIncreaseInMarketCurrency(new BigDecimal("0.1"));
		adTemplate.setOpenDays(5);
		adTemplate.setPaymentWay(1);
		adTemplate.setQuantity(1);
		adTemplate.setSlogan(SLOGAN);
		adTemplate.setShippingDescription(StringUtilities
				.escapeString2HTML(SHIPPING_DESCRIPTION));
		if (adTemplate.getSummary() == null) {
			adTemplate.setSummary(""); // cannot be null
		}
		if (adTemplate.getDetails() == null) {
			adTemplate.setDetails(""); // cannot be null
		}
		adTemplate.setShippingWay(1);
		adTemplate.setCreatedTimestamp(new Date());
		adTemplate.setAddedBy(addedBy);
	}

	private int getInputFromUser(AdTemplate adTemplate, Product product,
			String encoding, boolean linuxConsole,
			TranslationMode translationMode, Seller seller, StringBuffer translations) throws Exception {
		this.console = System.console();
		if (linuxConsole) {
			this.keyboard = new Keyboard(Keyboard.PLATFORM_LINUX);
		} else {
			this.keyboard = new Keyboard();
		}
		String usedEncoding = encoding;
		if (usedEncoding == null)
			usedEncoding = this.settingsDAO
					.getValue("fi.jonix.huutonet.tools.DxProductHelper.consoleEncoding");
		try {
			out = new PrintWriter(new OutputStreamWriter(System.out,
					usedEncoding));
		} catch (UnsupportedEncodingException e1) {
			e1.printStackTrace();
			System.exit(1);
		}
		Translate.setHttpReferrer("jonix");
		BufferedReader br = null;
		try {
			br = new BufferedReader(new InputStreamReader(System.in,
					usedEncoding));
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
			System.exit(1);
		}
		this.printHelp();
		this.setSiblingAdTemplate(product);
		int index = 0;
		while (true) {
			if (index == INDEX_DELETE)
				return RETURN_DELETE;
			else if (index == INDEX_QUIT)
				return RETURN_QUIT;
			else if (index == INDEX_SKIP)
				return RETURN_SKIP;
			if (index < 0)
				index++;
			else if (index == INDEX_HEADLINE)
				index = this.handleHeadline(adTemplate, index, product,
						translationMode, seller, translations);
			else if (index == INDEX_SUMMARY)
				index = this.handleSummary(adTemplate, index, translationMode, seller, translations);
			else if (index == INDEX_DETAILS)
				index = this.handleDetails(adTemplate, index, product,
						translationMode, seller, translations);
			else if (index == INDEX_TECHNICAL_SPECS)
				index = this.handleTechnicalSpecs(adTemplate, index, product,
						translationMode, seller, translations);
			else if (index == INDEX_CATEGORY)
				index = this.handleCategories(adTemplate, br, index,
						translationMode);
			else if (index == INDEX_SHOW_REPORT)
				index = this.showReport(adTemplate, br, index, translationMode);
			else if (index == INDEX_FINNISH)
				break;
		}
		return RETURN_SUCCESS;
	}

	private boolean isQuit(String input) {
		if (input != null && input.equals(QUIT))
			return true;
		return false;
	}

	private boolean isSkip(String input) {
		if (input != null && input.equals(SKIP))
			return true;
		return false;
	}

	private boolean isBackward(String input) {
		if (input != null && input.equals(INPUT_GO_BACKWARD))
			return true;
		return false;
	}

	private boolean isDelete(String input) {
		if (input != null && input.trim().equalsIgnoreCase(DELETE_LINE))
			return true;
		return false;
	}

	private boolean isDeleteProduct(String input) {
		if (input != null && input.equals(DELETE_PRODUCT))
			return true;
		return false;
	}

	private boolean isToBegining(String input) {
		if (input != null && input.equals(INPUT_GO_TO_BEGINING))
			return true;
		return false;
	}

	private boolean isDontSaveTranslation(String input) {
		if (input != null && input.startsWith(INPUT_DO_NOT_SAVE_TRANSLATION))
			return true;
		return false;
	}

	private void printHelp() {
		this.printlnToOutput("\n\n\n\n************INSTRUCTIONS************\n");
		this.printlnToOutput("\"" + INPUT_DO_NOT_SAVE_TRANSLATION
				+ "\" starts new row that is not to be saved to translations");
		this.printlnToOutput("\"" + INPUT_GO_BACKWARD + "\" moves backward");
		this.printlnToOutput("\"" + INPUT_GO_TO_BEGINING
				+ "\" moves to the begining");
		this.printlnToOutput("\"" + DELETE_PRODUCT
				+ "\" removes the product. Works for headline.");
		this.printlnToOutput("\"" + DELETE_LINE
				+ "\" removes specification row.");
		this.printlnToOutput("\"" + QUIT + "\" quits.");
		this.printlnToOutput("\"" + SKIP + "\" skips the current product.");
	}

	private int showReport(AdTemplate adTemplate, BufferedReader br, int index,
			TranslationMode translationMode) {
		this.printlnToOutput("\n\n\n\n************TULOKSET************");
		this.printlnToOutput("\n\n------------Headline----------\n\n"
				+ adTemplate.getHeadline());
		if (DETAILS_IN_USE)
			this.printlnToOutput("\n\n------------Details-----------\n\n"
					+ adTemplate.getDetails());
		this.printlnToOutput("\n\n------------Technical specs---\n\n"
				+ adTemplate.getTechnicalSpecs());
		if (SUMMARY_IN_USE)
			this.printlnToOutput("\n\n------------Summary-----------\n\n"
					+ adTemplate.getSummary());
		if (adTemplate.getProduct().getCategory() == null) {
			this.printlnToOutput("\n\n------------Category----------\n\n"
					+ "NO CATEGORY DEFINED");
		} else {
			this.printlnToOutput("\n\n------------Category----------\n\n"
					+ categoryHelper.extractPath(categoryHelper
							.getWholePathFromRootFor(adTemplate.getProduct()
									.getCategory())));
		}
		this.printlnToOutput(""); // new row
		boolean accept = UserInputHelper.getBoolean(
				"Accept and store to database?", null, br);
		if (accept) {
			return this.getForward(index, translationMode);
		} else {
			return INDEX_HEADLINE;
		}
	}

	private int handleTechnicalSpecs(AdTemplate adTemplate, int index,
			Product product, TranslationMode translationMode, Seller seller, StringBuffer translations) {
		this.out.print("\n\n\n\n\n***************TECHNICAL SPECIFICATIONS***************\n\n");
		String specs = "";
		String[] specRows = null;
		if (adTemplate.getTechnicalSpecs() != null) {
			specRows = adTemplate.getTechnicalSpecs().split("\n");
		} else if (product.getTechnicalSpecs() != null)
			specRows = product.getTechnicalSpecs().split("\n");
		else {
			if (this.siblingAdTemplate != null
					&& this.siblingAdTemplate.getTechnicalSpecs() != null)
				specRows = this.siblingAdTemplate.getTechnicalSpecs().split(
						"\n");
		}
		if (specRows == null)
			specRows = new String[0];

		for (int i = 0; i < specRows.length + 1; i++) {
			if (i < 0)
				return this.getBackward(index, translationMode);
			if (i >= specRows.length) {
				this.printToOutput("Free text row: ");
				String specRow = this.readUserInput();
				if (this.isBackward(specRow)) {
					i -= 2;
					specs = this.cutLastLine(specs);
				} else {
					if (specs.length() > 0)
						specs += "\n";
					if (this.isInputWritten(specRow)) {
						specs += this.getFormattedSentence(specRow);
						specRows = specs.split("\n");
					} else
						break;
				}

			} else {
				this.printToOutput("Original text: " + specRows[i] + "\n\n");
				String translated = this.getTranslation(specRows[i].trim(),translations);
				this.printToOutput("Translation: " + translated + "\n\n");
				this.printToOutput("Enter technical specs row: ");
				String specRow = this.readUserInput(translated);
				if (this.isBackward(specRow)) {
					i -= 2;
					specs = this.cutLastLine(specs);
				} else if (this.isDelete(specRow)) {
					this.printToOutput("Deleting row: " + translated + "\n\n");
					String newSpecs = "";
					String[] alreadyWrittenInput = specs.split("\n");
					for (int j = 0; j < alreadyWrittenInput.length; j++) {
						if (i != j)
							newSpecs += alreadyWrittenInput[j] + "\n";
					}
					for (int j = alreadyWrittenInput.length; j < specRows.length; j++) {
						if (i != j)
							newSpecs += specRows[j] + "\n";
					}
					specRows = newSpecs.split("\n");
					i--;
				} else {
					if (specs.length() > 0)
						specs += "\n";
					if (this.isInputWritten(specRow)) {
						specs += this.updateTranslation(specRow,
								specRows[i].trim(), seller);
					} else
						specs += translated;
				}
			}
			this.printToOutput("\n---------------------------------------\n\n");
		}

		adTemplate.setTechnicalSpecs(specs.trim());
		return this.getForward(index, translationMode);
	}

	private String cutLastLine(String text) {
		if (text == null)
			return "";
		if (text.trim().length() == 0)
			return "";
		if (text.trim().lastIndexOf("\n") <= 0)
			return text.trim();
		return text.substring(0, text.trim().lastIndexOf("\n"));

	}

	private int handleDetails(AdTemplate adTemplate, int index,
			Product product, TranslationMode translationMode, Seller seller, StringBuffer translations) {
		this.out.print("\n\n\n\n\n***************DETAILS / CONTAINS*****************\n\n");
		String specs = "";
		String[] specRows = null;
		if (adTemplate.getDetails() != null) {
			specRows = adTemplate.getDetails().split("\n");
		} else if (product.getContents() != null)
			specRows = product.getContents().split("\n");
		else {
			if (this.siblingAdTemplate != null
					&& this.siblingAdTemplate.getDetails() != null)
				specRows = this.siblingAdTemplate.getDetails().split("\n");
		}
		if (specRows == null)
			specRows = new String[0];

		for (int i = 0; i < specRows.length + 1; i++) {
			if (i < 0)
				return this.getBackward(index, translationMode);
			if (i >= specRows.length) {
				this.printToOutput("Free text row: ");
				String specRow = this.readUserInput();
				if (this.isBackward(specRow)) {
					i -= 2;
					specs = this.cutLastLine(specs);
				} else {
					if (specs.length() > 0)
						specs += "\n";
					if (this.isInputWritten(specRow)) {
						specs += this.getFormattedSentence(specRow);
						specRows = specs.split("\n");
					} else
						break;
				}

			} else {
				this.printToOutput("Original text: " + specRows[i] + "\n\n");
				String translated = this.getTranslation(specRows[i].trim(),translations);
				this.printToOutput("Translation: " + translated + "\n\n");
				this.printToOutput("Enter technical specs row: ");
				String specRow = this.readUserInput(translated);
				if (this.isBackward(specRow)) {
					i -= 2;
					specs = this.cutLastLine(specs);
				} else if (this.isDelete(specRow)) {
					this.printToOutput("Deleting row: " + translated + "\n\n");
					String newSpecs = "";
					String[] alreadyWrittenInput = specs.split("\n");
					for (int j = 0; j < alreadyWrittenInput.length; j++) {
						if (i != j)
							newSpecs += alreadyWrittenInput[j] + "\n";
					}
					for (int j = alreadyWrittenInput.length; j < specRows.length; j++) {
						if (i != j)
							newSpecs += specRows[j] + "\n";
					}
					specRows = newSpecs.split("\n");
					i--;
				} else {
					if (specs.length() > 0)
						specs += "\n";
					if (this.isInputWritten(specRow)) {
						specs += this.updateTranslation(specRow,
								specRows[i].trim(), seller);
					} else
						specs += translated;
				}
			}
			this.printToOutput("\n---------------------------------------\n\n");
		}

		adTemplate.setDetails(specs.trim());
		return this.getForward(index, translationMode);
	}

	/*
	 * private int handleDetails(AdTemplate adTemplate, BufferedReader br, int
	 * index) {
	 * this.printToOutput("\n\n\n\n\n***************DETAILS***************\n\n"
	 * ); List<String> detailRows = new ArrayList<String>(); if
	 * (adTemplate.getDetails() != null) { String[] rows =
	 * adTemplate.getDetails().split("\\p{Punct} "); for (String row : rows) {
	 * detailRows.add(row); } }
	 * 
	 * for (int i = 0; i < detailRows.size() + 1; i++) { if (i < 0) return
	 * this.getBackward(index); if (i >= detailRows.size()) {
	 * this.printToOutput("Free text row: "); String detailRow =
	 * this.readUserInput(br); if (this.isBackward(detailRow)) { i -= 2; } else
	 * { if (this.isInputWritten(detailRow)) { detailRows.add(detailRow.trim());
	 * } else break; }
	 * 
	 * } else { this.printToOutput("Original text: " + detailRows.get(i) +
	 * "\n\n"); String translated =
	 * this.getTranslation(detailRows.get(i).trim());
	 * this.printToOutput("Enter detail row: [" + translated + "]: "); String
	 * detailRow = this.readUserInput(br, translated); if
	 * (this.isBackward(detailRow)) { i -= 2; } else { if
	 * (this.isInputWritten(detailRow)) { detailRows.set(i,
	 * this.updateTranslation(detailRow, detailRows.get(i).trim())); } else {
	 * detailRows.set(i, translated); } } }
	 * this.printToOutput("\n---------------------------------------\n\n"); }
	 * String details = ""; for (String row : detailRows) { if (row == null ||
	 * row.length() < 2) continue; if (details.length() > 0) details += " ";
	 * String adRow = this.getFormattedSentence(row); details += adRow; }
	 * 
	 * if (details.trim().length() > 1) { adTemplate.setDetails(details.trim());
	 * } else adTemplate.setDetails(""); return this.getForward(index); }
	 */

	private int handleSummary(AdTemplate adTemplate, int index,
			TranslationMode translationMode, Seller seller, StringBuffer translations) {
		this.printToOutput("\n\n\n\n\n***************SUMMARY***************\n\n");
		List<String> summaryRows = new ArrayList<String>();
		if (adTemplate.getSummary() != null) {
			String[] rows = adTemplate.getSummary().split("\\p{Punct} ");
			for (String row : rows) {
				summaryRows.add(row);
			}
		}

		for (int i = 0; i < summaryRows.size() + 1; i++) {
			if (i < 0)
				return this.getBackward(index, translationMode);
			if (i >= summaryRows.size()) {
				this.printToOutput("Free text row: ");
				String summaryRow = this.readUserInput();
				if (this.isBackward(summaryRow)) {
					i -= 2;
				} else {
					if (this.isInputWritten(summaryRow)) {
						summaryRows.add(summaryRow.trim());
					} else
						break;
				}

			} else {
				this.printToOutput("Original text: " + summaryRows.get(i)
						+ "\n\n");
				String translated = this.getTranslation(summaryRows.get(i)
						.trim(),translations);
				this.printToOutput("Translation: " + translated + "\n\n");
				this.printToOutput("Enter summary row: ");
				String summaryRow = this.readUserInput(translated);
				if (this.isBackward(summaryRow)) {
					i -= 2;
				} else {
					if (this.isInputWritten(summaryRow)) {
						summaryRows.set(i, this.updateTranslation(summaryRow,
								summaryRows.get(i).trim(), seller));
					} else {
						summaryRows.set(i, translated);
					}
				}
			}
			this.printToOutput("\n---------------------------------------\n\n");
		}
		String summarys = "";
		for (String row : summaryRows) {
			if (row == null || row.length() < 2)
				continue;
			if (summarys.length() > 0)
				summarys += " ";
			String adRow = this.getFormattedSentence(row);
			summarys += adRow;
		}

		if (summarys.trim().length() > 1) {
			adTemplate.setSummary(summarys.trim());
		} else
			adTemplate.setSummary("");
		return this.getForward(index, translationMode);
	}

	private int getForward(int index, TranslationMode translationMode) {
		if (index == INDEX_HEADLINE) {
			if (SUMMARY_IN_USE)
				return INDEX_SUMMARY;
			else
				return this.getForward(INDEX_SUMMARY, translationMode);
		} else if (index == INDEX_SUMMARY) {
			if (DETAILS_IN_USE)
				return INDEX_DETAILS;
			else
				return this.getForward(INDEX_DETAILS, translationMode);
		} else if (index == INDEX_DETAILS) {
			if (translationMode == TranslationMode.ALL)
				return INDEX_TECHNICAL_SPECS;
			else
				return this.getForward(INDEX_TECHNICAL_SPECS, translationMode);
		} else if (index == INDEX_TECHNICAL_SPECS) {
			if (translationMode == TranslationMode.ALL)
				return INDEX_CATEGORY;
			else
				return this.getForward(INDEX_CATEGORY, translationMode);
		} else if (index == INDEX_CATEGORY) {
			return INDEX_SHOW_REPORT;
		} else if (index == INDEX_SHOW_REPORT) {
			return INDEX_FINNISH;
		}
		return 0;
	}

	private int getBackward(int index, TranslationMode translationMode) {
		if (index == INDEX_SUMMARY) {
			return INDEX_HEADLINE;
		} else if (index == INDEX_DETAILS) {
			if (SUMMARY_IN_USE)
				return INDEX_SUMMARY;
			else
				return this.getBackward(INDEX_SUMMARY, translationMode);
		} else if (index == INDEX_TECHNICAL_SPECS) {
			if (DETAILS_IN_USE)
				return INDEX_DETAILS;
			else
				this.getBackward(INDEX_DETAILS, translationMode);
		} else if (index == INDEX_CATEGORY) {
			if (translationMode == TranslationMode.ALL)
				return INDEX_TECHNICAL_SPECS;
			else
				return this.getBackward(INDEX_TECHNICAL_SPECS, translationMode);
		} else if (index == INDEX_SHOW_REPORT) {
			if (translationMode == TranslationMode.ALL)
				return INDEX_CATEGORY;
			else
				return this.getBackward(INDEX_CATEGORY, translationMode);
		}
		return 0;
	}

	private int handleHeadline(AdTemplate adTemplate, int index,
			Product product, TranslationMode translationMode, Seller seller, StringBuffer translations) {
		this.out.print("\n\n\n\n\n***************HEADLINE***************\n\n");
		this.printToOutput("Original text: " + product.getName() + "\n\n");
		String translated = this.getFormattedSentenceWithoutDot(this
				.getTranslation(product.getName(),translations));
		this.printToOutput("Translation: " + translated + "\n\n");
		this.printToOutput("Enter headline: ");
		String headline = this.readUserInput(translated);
		if (this.isBackward(headline)) {
			return this.getBackward(index, translationMode);
		} else if (this.isDeleteProduct(headline)) {
			return INDEX_DELETE;
		} else if (this.isQuit(headline)) {
			return INDEX_QUIT;
		} else if (this.isSkip(headline)) {
			return INDEX_SKIP;
		}
		if (this.isInputWritten(headline)) {
			adTemplate.setHeadline(this.updateTranslation(
					this.getFormattedSentenceWithoutDot(headline),
					product.getName(), seller));
		} else {
			adTemplate.setHeadline(translated);
		}
		return this.getForward(index, translationMode);
	}

	private void setSiblingAdTemplate(Product product) {
		this.siblingAdTemplate = null;
		Translation translation = this.translationDAO.getCloseTranslation(
				product.getName(), Statics.FINNISH_LANGUAGE);
		if (translation == null)
			return;
		String siblingHeadline = translation.getTranslatedText();
		List<AdTemplate> siblingAdTemplates = this.adTemplateDAO
				.findByHeadline(siblingHeadline);
		if (siblingAdTemplates == null || siblingAdTemplates.isEmpty())
			return;
		this.siblingAdTemplate = siblingAdTemplates.get(0);
	}

	private int handleCategories(AdTemplate adTemplate, BufferedReader br,
			int index, TranslationMode translationMode) throws Exception {
		this.out.print("\n\n\n\n\n***************CATEGORY***************\n\n");

		/*
		 * If the product already has a category, then just display it to the
		 * user and then stop. This can happen for example if a product is
		 * translated into Swedish which has previously been translated into
		 * Finnish.
		 */
		Category category = adTemplate.getProduct().getCategory();
		if (category != null) {
			printToOutput("This product already has a category defined:\n");
			categoryDAO.refresh(category);
			printToOutput("Category: "
					+ this.categoryHelper.extractPath(this.categoryHelper
							.getWholePathFromRootFor(category)) + "\n");
			boolean boolChoice = UserInputHelper.getBoolean(
					"Do you accept this category?", true, br);
			if (boolChoice) {
				return this.getForward(index, translationMode);
			}
		}

		/* Wait for the category resolver to get done. */
		if (!categoryResolverThreadBean.isDone()) {
			printToOutput("Waiting for category resolver...");
			while (!categoryResolverThreadBean.isDone()) {
				Thread.sleep(50);
			}
			printToOutput("done\n");
		}

		/*
		 * First get categories that have the same provider category AND matches
		 * by the headline.
		 */
		List<Category> listHeadLineAndProvCat = this.categoryResolverThreadBean
				.getListHeadLineAndProvCat();

		/* Then get categories that match by SellStar category and headline. */
		List<Category> listHeadlineAndSellStarCat = this.categoryResolverThreadBean
				.getListHeadlineAndSellStarCat();

		List<Category> listHeadline = this.categoryResolverThreadBean
				.getListHeadline();

		int i = 1;
		List<Category> finalCandidates = new ArrayList<Category>();
		if (listHeadLineAndProvCat.size() > 0) {
			printToOutput("Hits based on headline and provider category:\n");
			for (Category c : listHeadLineAndProvCat) {
				categoryDAO.refresh(c);
				if (!finalCandidates.contains(c)) {
					printToOutput("  "
							+ (i++)
							+ ".) "
							+ this.categoryHelper
									.extractPath(this.categoryHelper
											.getWholePathFromRootFor(c))
							+ " (id = " + c.getId() + ")\n");
					finalCandidates.add(c);
				}
			}
		}

		List<Category> disjointCategories = this.categoryResolverThreadBean
				.getDisjointCategories();
		if (disjointCategories.size() > 0) {
			printToOutput("Hits based on headline and (provider->)SellStar category:\n");
			for (Category c : disjointCategories) {
				categoryDAO.refresh(c);
				printToOutput("  "
						+ (i++)
						+ ".) "
						+ this.categoryHelper.extractPath(this.categoryHelper
								.getWholePathFromRootFor(c)) + " (id = "
						+ c.getId() + ")\n");
				finalCandidates.add(c);
			}
		}
		if (listHeadLineAndProvCat.size() == 0
				&& disjointCategories.size() == 0) {
			printToOutput("Hits based on plain headline:\n");
			for (Category c : listHeadline) {
				categoryDAO.refresh(c);
				printToOutput("  "
						+ (i++)
						+ ".) "
						+ this.categoryHelper.extractPath(this.categoryHelper
								.getWholePathFromRootFor(c)) + " (id = "
						+ c.getId() + ")\n");
				finalCandidates.add(c);
			}
		}

		if (finalCandidates.size() == 1) {
			boolean boolChoice = UserInputHelper.getBoolean(
					"Do you accept #1?", true, br);
			if (boolChoice) {
				adTemplate.getProduct().setCategory(finalCandidates.get(0));
				return this.getForward(index, translationMode);
			}
		} else if (finalCandidates.size() > 1) {
			int choice = UserInputHelper.getOneNumber(
					"Choose one (0 for more alternatives, -1 to go backward)",
					-1, finalCandidates.size(), false, false, br);
			if (choice == -1) {
				return this.getBackward(index, translationMode);
			} else if (choice > 0) {
				adTemplate.getProduct().setCategory(
						finalCandidates.get(choice - 1));
				return this.getForward(index, translationMode);
			}
		}

		List<CategoryHits> disjointCategoryHits = this.categoryResolverThreadBean
				.getDisjointCategoryHits();
		if (disjointCategoryHits.size() > 0) {
			printToOutput("Hits based on provider->SellStar category:\n");
			for (CategoryHits chi : disjointCategoryHits) {
				Category c = chi.getSellStarCategory();
				categoryDAO.refresh(c);
				// System.out.println("category " + c.getId() + " has " +
				// chi.getCount() + " hits");
				printToOutput("  "
						+ (i++)
						+ ".) "
						+ this.categoryHelper.extractPath(this.categoryHelper
								.getWholePathFromRootFor(c)) + " (id = "
						+ c.getId() + ", hits = " + chi.getCount() + ")\n");
				finalCandidates.add(c);
			}
			int choice = UserInputHelper.getOneNumber(
					"Choose one (0 for manual search, -1 to go backward)", -1,
					finalCandidates.size(), false, false, br);
			if (choice == -1) {
				return this.getBackward(index, translationMode);
			} else if (choice > 0) {
				adTemplate.getProduct().setCategory(
						finalCandidates.get(choice - 1));
				return this.getForward(index, translationMode);
			}
		} else {
			printToOutput("No more alternatives were found, you'll have to make a manual search.");
		}

		/*
		 * If we get here, then none of the alternatives were suitable, so then
		 * make a manual search.
		 */
		CategoryAdmin categoryAdmin = new CategoryAdmin();
		category = categoryAdmin.run(false, br, out, null);
		if (category != null) {
			adTemplate.getProduct().setCategory(category);
		} else {
			printToOutput("No category set, returning to previous step.");
			return this.getBackward(index, translationMode);
		}
		return this.getForward(index, translationMode);
	}

	private String readUserInput(String candidate) {
		/*
		 * if (translationStatus.equals(TranslationStatus.FOUND)) {
		 * this.keyboard.type(candidate,out); } else
		 * if(translationStatus.equals(TranslationStatus.PARTLY_FOUND)) {
		 * this.keyboard.type(candidate,out); }
		 */
		this.keyboard.type(candidate);
		String input = this.readUserInput();
		if (input != null && input.equals(INPUT_ROBOT_TYPE)) {
			this.keyboard.type(candidate);
			return this.readUserInput();
		}
		return input.trim();
	}

	private String readUserInput() {
		long time = System.currentTimeMillis();
		String input = console.readLine();
		if (System.currentTimeMillis() - time < 200) {
			return this.readUserInput();
		}
		return input.trim();
	}

	private boolean isInputWritten(String input) {
		if (input != null && input.trim().length() > 0)
			return true;
		return false;
	}

	private String getTranslation(String text, StringBuffer translations) {
		this.translationStatus = TranslationStatus.NOT_FOUND;
		if (text == null || text.length() == 0)
			return "";
		String translatedText = "";
		String[] sentences = text.split(": ");
		int sentenceIndex = 0;
		for (String sentence : sentences) {
			String[] parts2 = sentence
					.split(TranslationDAOImpl.TRANSLATION_PART_SEPARATORS);
			String[] parts = new String[parts2.length];
			for (int i = 0; i < parts.length; i++) {
				parts[i] = parts2[i].trim();
			}

			List<String> splitters = this.getSplitters(sentence);
			/*
			 * System.out.println("OSAT"); for (String part : parts) {
			 * System.out.println("'" + part + "'"); }
			 * System.out.println("JAKAJAT"); for (String part : splitters) {
			 * System.out.println("'" + part + "'"); }
			 */

			int index = 0;
			for (String part : parts) {
				Translation translation = this.translationDAO.getTranslation(
						part.trim(), Statics.FINNISH_LANGUAGE);
				if (translation != null) {
					translations.append("*old exact*> " + part.trim() + " -> " + translation.getTranslatedText() + "\n");
					this.printlnToOutput("*old exact*> " + part.trim() + " -> " + translation.getTranslatedText() + "\n");
					this.translationStatus = TranslationStatus.FOUND;
					translatedText += translation.getTranslatedText();
				} else {
					translation = this.translationDAO.getCloseTranslation(
							part.trim(), Statics.FINNISH_LANGUAGE);
					if (translation != null) {
						translations.append("*old close*> " + part.trim() + " -> " + translation.getTranslatedText() + "\n");
						this.printlnToOutput("*old close*> " + part.trim() + " -> " + translation.getTranslatedText() + "\n");
						this.translationStatus = TranslationStatus.PARTLY_FOUND;
						translatedText += translation.getTranslatedText();
					} else {
						try {
							String googleTranslation = Translate.execute(part,
									Language.ENGLISH, Language.FINNISH);
							translatedText += googleTranslation;
							translations.append("*google*   > " + part + " -> " + googleTranslation + "\n");
							this.printlnToOutput("*google*   > " + part + " -> " + googleTranslation + "\n");
						} catch (Exception e) {
							logger.debug("Google translation failed", e);
						}
					}
				}
				if (splitters.size() > index)
					translatedText += splitters.get(index);
				index++;
			}
			if (sentences.length > 1 && sentenceIndex % 2 == 0)
				translatedText += ": ";
			sentenceIndex++;
		}
		return translatedText.trim().replaceAll("  ", " ");
	}

	private String updateTranslation(String translatedText, String originalText, Seller seller) {
		if (this.isDontSaveTranslation(translatedText)) {
			return translatedText.substring(INPUT_DO_NOT_SAVE_TRANSLATION
					.length());
		}
		String returnText = "";
		String[] originalTextParts = originalText
				.split(TranslationDAOImpl.TRANSLATION_PART_SEPARATORS);
		String[] parts2 = translatedText
				.split(TranslationDAOImpl.TRANSLATION_PART_SEPARATORS);
		String[] translatedTextParts = new String[parts2.length];
		for (int i = 0; i < translatedTextParts.length; i++) {
			translatedTextParts[i] = parts2[i].trim();
		}
		// TODO : Later you may want to save full sentences also
		if (originalTextParts.length != translatedTextParts.length)
			return translatedText;
		List<String> translatedTextSplitters = this
				.getSplitters(translatedText);
		int index = 0;
		for (int i = 0; i < originalTextParts.length; i++) {
			if (i == translatedTextParts.length)
				break;
			translatedTextParts[i] = translatedTextParts[i].trim();
			originalTextParts[i] = originalTextParts[i].trim();
			if (originalTextParts[i] != null
					&& originalTextParts[i].length() > 1
					&& translatedTextParts[i] != null
					&& translatedTextParts[i].length() > 1) {
				translatedTextParts[i] = Character
						.toUpperCase(translatedTextParts[i].charAt(0))
						+ translatedTextParts[i].substring(1);
				Translation translation = this.translationDAO.getTranslation(
						originalTextParts[i], Statics.FINNISH_LANGUAGE);
				if (translation != null) {
					translation.setTranslatedText(translatedTextParts[i]);
					this.translationDAO.save(translation);
				} else {
					translation = new Translation(originalTextParts[i],
							translatedTextParts[i], Statics.FINNISH_LANGUAGE, seller);
					this.translationDAO.save(translation);
				}
			}
			returnText += translatedTextParts[i];
			if (translatedTextSplitters.size() > index)
				returnText += translatedTextSplitters.get(index);
			index++;
		}
		return returnText.trim().replaceAll("  ", " ");
	}

	private void printToOutput(String text) {
		this.out.print(text);
		this.out.flush();
	}

	private void printlnToOutput(String text) {
		this.printToOutput("\n" + text);
	}

	private String getFormattedSentence(String row) {
		if (row == null || row.length() < 2)
			return row;
		String sentence = Character.toUpperCase(row.charAt(0))
				+ row.substring(1);
		if (!sentence.endsWith("."))
			sentence += ".";
		return sentence;
	}

	private String getFormattedSentenceWithoutDot(String row) {
		if (row == null || row.length() < 2)
			return row;
		String sentence = Character.toUpperCase(row.charAt(0))
				+ row.substring(1);
		return sentence;
	}

	private List<String> getSplitters(String originalString) {
		List<String> splitters = new ArrayList<String>();
		
		Pattern pattern = Pattern
				.compile(TranslationDAOImpl.TRANSLATION_PART_SEPARATORS);
		Matcher matcher = pattern.matcher(originalString);

		while (matcher.find()) {
			String splitter = matcher.group();
			if (matcher.start() > 0) {
				for (int i = matcher.start() - 1; i >= 0; i--) {
					if (Character.isWhitespace(originalString.charAt(i))) {
						splitter = originalString.charAt(i) + splitter;
					} else {
						break;
					}
				}
			}
			for (int i = matcher.end(); i < originalString.length(); i++) {
				if (Character.isWhitespace(originalString.charAt(i))) {
					splitter = splitter + originalString.charAt(i);
				} else {
					break;
				}
			}
			splitters.add(splitter);
		}
		return splitters;
	}

}
