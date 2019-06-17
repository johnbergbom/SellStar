package fi.jonix.huutonet.tools;

import java.io.File;
import java.math.BigDecimal;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import fi.jonix.huutonet.domain.VirtualCoin;
import fi.jonix.huutonet.domain.model.Currency;
import fi.jonix.huutonet.domain.model.Image;
import fi.jonix.huutonet.domain.model.Product;
import fi.jonix.huutonet.domain.model.Provider;
import fi.jonix.huutonet.domain.model.dao.CurrencyDAO;
import fi.jonix.huutonet.domain.model.dao.ImageDAO;
import fi.jonix.huutonet.domain.model.dao.ProductDAO;
import fi.jonix.huutonet.domain.model.dao.ProviderDAO;
import fi.jonix.huutonet.domain.model.dao.SettingsDAO;
import fi.jonix.huutonet.order.Price;
import fi.jonix.huutonet.order.ProviderHandler;
import fi.jonix.huutonet.robot.SeleniumRobot;

@Component(value = "dxProductHelper")
public class DxProductHelper {

	// TODO TOMI: User manual spec rows are not included
	// http://www.dealextreme.com/details.dx/sku.20074

	public static final Logger logger = Logger.getLogger(DxProductHelper.class);

	private static String BASE_URL = "http://www.dealextreme.com/details.dx/";
	private static long MIN_FILE_SIZE = 500;
	private static String CATEGORY_NASTY_GADGETS = "Naughty Gadgets";

	@Autowired
	private ProviderDAO providerDAO;

	@Autowired
	private ImageDAO imageDAO;
	
	@Autowired
	private ProductDAO productDAO;
	
	@Autowired
	private SeleniumRobot seleniumRobot;
	
	@Autowired
	private SettingsDAO settingsDAO;
	
	@Autowired
	private ProviderHandler providerHandler;
	
	@Autowired
	private CurrencyDAO currencyDAO;

	public void updateTechnicalSpecsOfProduct(Product product) throws Exception {
		logger.info("Updating item " + product.getProviderProdId());
		this.seleniumRobot.getSelenium().open(BASE_URL + product.getProviderProdId());
		String newSpecs = getTechnicalSpecs();
		if (!newSpecs.equals(product.getTechnicalSpecs())) {
			logger.info("New specs found so product is to be updated");
			product.setTechnicalSpecs(newSpecs);
			this.productDAO.save(product);
		} else {
			logger.info("No changes found for this item");
		}
	}

	/**
	 * 
	 * @param dxProductId
	 * @param createdFiles
	 *            A list of files that were created. If the transaction was
	 *            rolled back, it's the responsibility of the caller to remove
	 *            the files in this list.
	 * @return
	 * @throws Exception
	 */
	public Product createNewProduct(String dxProductId, List<File> createdFiles, boolean override, VirtualCoin fromPriceExclVat,
			VirtualCoin toPriceExclVat, boolean oldWaterMark) throws Exception {
		logger.info("Getting item " + dxProductId);
		Product product = this.getProduct(dxProductId, override, fromPriceExclVat, toPriceExclVat);
		if (product != null) {

			product = productDAO.save(product);
			productDAO.flush(); // do a flush in order to catch database errors
			// right away
			logger.debug("Getting images to item " + dxProductId);
			this.saveImage(dxProductId, product, createdFiles, oldWaterMark);
			// logger.debug("Creates base ad to item " + dxProductId);
			// this.createEmptyAdd(product);
			logger.info("Done getting item " + dxProductId);
		}
		return product;
	}

	private Product getProduct(String dxProductId, boolean override, VirtualCoin fromPriceExclVat, VirtualCoin toPriceExclVat) throws Exception {
		throw new RuntimeException("This method isn't yet fixed (getProduct).");
		/*Provider provider = providerDAO.getProviderByName(Provider.DEAL_EXTREME_PROVIDER_NAME);
		Product product = productDAO.getByProviderProdId(dxProductId, provider);
		if (product != null) {
			if (!override) {
				logger.info(provider.getName() + " product " + dxProductId + " already exists in the database and is skipped");
				return null;
			} else
				logger.info("Overrides product " + provider.getName() + " product " + dxProductId);
			// TODO: If override is used, then the old images should be deleted!
			throw new Exception("We need to fix that old images are deleted before this can be ran!");
		} else {
			product = new Product();
		}
		product.setCompensation(false);
		product.setProviderProdId(dxProductId);
		product.setProvider(provider);

		String name = null;
		Price providerPrice = null;
		try {
			providerPrice = providerHandler.getPrice(provider,dxProductId,false,false);
		} catch (Exception e) {
			logger.info("Item not available");
			return null;
		}

		if (fromPriceExclVat != null || toPriceExclVat != null) {
			BigDecimal providerPriceInEuroExclVat = currencyDAO.convertCurrency(providerPrice.getCurrency(),
					Currency.EURO, providerPrice.getPriceExclVat().getValue());
			if (toPriceExclVat != null) {
				BigDecimal toPriceEuroExclVat = currencyDAO.convertCurrency(toPriceExclVat.getCurrency(),
						Currency.EURO, toPriceExclVat.getValue());
				if (providerPriceInEuroExclVat.compareTo(toPriceEuroExclVat) > 0) {
					logger.info("Price is too high - product skipped");
					return null;
				}
			}
			if (fromPriceExclVat != null) {
				BigDecimal fromPriceEuroExclVat = currencyDAO.convertCurrency(fromPriceExclVat.getCurrency(),
						Currency.EURO, fromPriceExclVat.getValue());
				if (providerPriceInEuroExclVat.compareTo(fromPriceEuroExclVat) < 0) {
					logger.info("Price is too low - product skipped");
					return null;
				}
			}
		}

		this.seleniumRobot.getSelenium().open(BASE_URL + dxProductId);

		try {
			name = this.seleniumRobot.getSelenium().getText("//span[@id='_ctl0_content_HeadLine1']");
		} catch (Exception e) {
			logger.info("Item does not exist");
			return null;
		}

		String providerCategory = this.seleniumRobot.getSelenium().getText("//table[@id='Table3']/tbody/tr/td[1]/span/span/a");
		if (providerCategory.indexOf(CATEGORY_NASTY_GADGETS) > -1) {
			logger.info("Item from nasty gadget category will be skipped");
			return null;
		}

		product.setTechnicalSpecs(this.getTechnicalSpecs());
		product.setProviderCategory(providerCategory);

		if (name != null & name.length() > 0)
			product.setName(name);
		else
			throw new Exception("Name not found");
		if (product.getTechnicalSpecs() == null)
			product.setTechnicalSpecs("");

		return product;*/
	}

	/* Must be in dx product page before calling this method */
	private String getTechnicalSpecs() {
		String technicalSpecs = null;
		if (this.seleniumRobot.getSelenium().isElementPresent("//div[@id='tabPageOverview']/div/table")) {
			List<String> technicalRows = new ArrayList<String>();
			int index = 1;
			while (this.seleniumRobot.getSelenium().isElementPresent("//div[@id='tabPageOverview']/div/table/tbody/tr[" + index + "]")) {
				String row = this.seleniumRobot.getSelenium().getText("//div[@id='tabPageOverview']/div/table/tbody/tr[" + index + "]");
				technicalRows.add(row + "\n");
				index++;
			}
			technicalSpecs = this.getTechnicalSpecsFromTable(technicalRows).trim();
		}
		if (technicalSpecs == null) {
			try {
				String basicTechicalDetail = "";
				int index = 1;
				while (this.seleniumRobot.getSelenium().isElementPresent("//div[@id='tabPageOverview']/div[" + index + "]")) {
					basicTechicalDetail += this.seleniumRobot.getSelenium().getText("//div[@id='tabPageOverview']/div[" + index + "]")
							+ "\n";
					index++;
				}
				int liIndex = 1;
				while (this.seleniumRobot.getSelenium().isElementPresent("//div[@id='tabPageOverview']//li[" + liIndex + "]")) {
					basicTechicalDetail += "- "
							+ this.seleniumRobot.getSelenium().getText("//div[@id='tabPageOverview']//li[" + liIndex + "]") + "\n";
					liIndex++;
				}
				technicalSpecs = this.getTechnicalSpecs(basicTechicalDetail).trim();
			} catch (Exception e) {
			}
		}
		return technicalSpecs;
	}

	private String getTechnicalSpecsFromTable(List<String> techicalRows) {
		String technicalSpecs = "";
		if (techicalRows != null) {
			for (String row : techicalRows)
				technicalSpecs += row.replaceAll(":", ": ").trim() + "\n";
		}
		return technicalSpecs;
	}

	private String getTechnicalSpecs(String rawText) {
		if (rawText == null || rawText.trim().length() == 0) {
			return "";
		} else {
			String technicalSpecs = "";
			String[] lines = rawText.split("\n");
			for (String line : lines) {
				if (technicalSpecs.length() > 0)
					technicalSpecs += "\n";
				if (line.trim().startsWith("-")) {
					technicalSpecs += line.trim().substring(1).trim();
				} else {
					// technicalSpecs += line.trim(); All the correct rows start
					// with '-'
				}
			}
			return technicalSpecs;
		}
	}

	private void saveImage(String dxProductId, Product product, List<File> createdFiles, boolean oldWaterMark) throws Exception {
		this.seleniumRobot.getSelenium().open(BASE_URL + dxProductId);
		int i = 0;

		List<File> rawImageFiles = new ArrayList<File>();
		List<File> smallImageFiles = new ArrayList<File>();

		try {
			String basePath = settingsDAO.getValue("base.path");
			String tempStr = settingsDAO.getValue("fi.jonix.huutonet.tools.DxProductHelper.productImagePath");
			File productImagePath = new File(basePath, tempStr);
			while (true) {
				File smallFile = null;
				File rawFile = null;
				try {
					if (!this.seleniumRobot.getSelenium().isAttributePresent("//img[@id='imgGallery" + i + "']@src"))
						break;
					String urlToImage = "http:" + this.seleniumRobot.getSelenium().getAttribute("//img[@id='imgGallery" + i + "']@src");
					logger.debug("Downloading image from: " + urlToImage);
					String smallFilePath = product.getId() + "_small_" + i + ".jpg";
					smallFile = new File(productImagePath, smallFilePath);
					FileUtils.copyURLToFile(new URL(urlToImage), smallFile);
					logger.debug("Size of small image: " + smallFile.length());
					if (smallFile.length() < MIN_FILE_SIZE) {
						FileUtils.forceDelete(smallFile);
					} else {
						smallImageFiles.add(smallFile);
					}

					logger.debug("Downloading image from: " + urlToImage.replace("_small", ""));
					String rawFilePath = product.getId() + "_raw_" + i + ".jpg";
					rawFile = new File(productImagePath, rawFilePath);
					FileUtils.copyURLToFile(new URL(urlToImage.replace("_small", "")), rawFile);
					logger.debug("Size of big image: " + rawFile.length());
					if (rawFile.length() < MIN_FILE_SIZE) {
						FileUtils.forceDelete(rawFile);
					} else {
						rawImageFiles.add(rawFile);
					}
					i++;
				} catch (com.thoughtworks.selenium.SeleniumException e) {
					break;
				} catch (Exception e) {
					/*
					 * Remove possibly created files and then throw an
					 * exception.
					 */
					if (smallFile != null && smallFile.exists()) {
						FileUtils.forceDelete(smallFile);
					}
					if (rawFile != null && rawFile.exists()) {
						FileUtils.forceDelete(rawFile);
					}
					throw new Exception("Error creating image files in DxProductHelper:", e);
				}
			}

			List<Long> thumbnailIds = new ArrayList<Long>();
			
			for (int k = 0; k < smallImageFiles.size(); k++) {
				logger.debug("Creating small image");
				Image image = new Image();
				image.setImageOrder(k);
				image.setLocalFileUrl(smallImageFiles.get(k).getPath());
				image.setProduct(product);
				logger.debug("Saves image");
				image = imageDAO.save(image);
				imageDAO.flush(); // do a flush in order to catch database
				// errors right away
				thumbnailIds.add(image.getId());
				logger.debug("Image saved");
			}
			
			List<File> processedImages = new ArrayList<File>();
			List<File> imagesToProcess = new ArrayList<File>();
			
			if (!oldWaterMark) {

				tempStr = settingsDAO.getValue("fi.jonix.huutonet.tools.DxProductHelper.coverMaskFile");
				File coverMask = new File(basePath, tempStr);
				tempStr = settingsDAO.getValue("fi.jonix.huutonet.tools.DxProductHelper.maskFile");
				File pathToMask = new File(basePath, tempStr);
				for (File file : rawImageFiles) {
					imagesToProcess.add(file);
					if (imagesToProcess.size() > 4) {
						List<File> maskedImages = this.getProcessedImages(imagesToProcess, coverMask, product, productImagePath,
								processedImages.size(), "masked_", createdFiles);
						processedImages.addAll(this.getProcessedImages(maskedImages, pathToMask, product, productImagePath, processedImages
								.size(), "", createdFiles));
						imagesToProcess.clear();
					}
				}
				if (imagesToProcess.size() > 0) {
					List<File> maskedImages = this.getProcessedImages(imagesToProcess, coverMask, product, productImagePath,
							processedImages.size(), "masked_", createdFiles);
					processedImages.addAll(this.getProcessedImages(maskedImages, pathToMask, product, productImagePath, processedImages
							.size(), "", createdFiles));
				}
				
				for (int k = 0; k < processedImages.size(); k++) {
					logger.debug("Creating image");
					Image image = new Image();
					image.setImageOrder(k);
					image.setLocalFileUrl(processedImages.get(k).getPath());
					image.setProduct(product);
					image.setThumbnailImageId(thumbnailIds.get(k));
					logger.debug("Saves image");
					imageDAO.save(image);
					imageDAO.flush(); // do a flush in order to catch database
					// errors right away
					logger.debug("Image saved");
				}
				
			}

			logger.debug("Tulos: " + smallImageFiles.size());
		} catch (Exception e) {
			throw e;
		} finally {
			for (File file : rawImageFiles) {
				createdFiles.add(file);
			}
			for (File file : smallImageFiles) {
				createdFiles.add(file);
			}
			/* Remove temporary files. */
			for (File file : createdFiles) {
				if (file.isFile() && file.exists() && (file.getName().indexOf("masked_") != -1 || file.getName().indexOf("raw_") != -1)) {
					file.delete();
				}
			}
		}
	}

	List<File> getProcessedImages(List<File> rawImageFiles, File pathToMask, Product product, File path, int index, String prefix,
			List<File> createdFiles) throws Exception {
		this.seleniumRobot.getSelenium().open("http://picmarkr.com/index.php");
		if (this.seleniumRobot.getSelenium().isElementPresent("//a[contains(text(),'to other images')]")) {
			this.seleniumRobot.getSelenium().click("//a[contains(text(),'to other images')]");
			this.seleniumRobot.getSelenium().waitForPageToLoad("60000");
		}

		logger.debug("1");
		for (int j = 0; j < rawImageFiles.size(); j++) {
			seleniumRobot.getSelenium().type("//input[@name='file[" + (j + 1) + "]']", rawImageFiles.get(j).getPath());
		}

		logger.debug("2");
		seleniumRobot.getSelenium().click("//form[@name='f1']/div/div/div/a");

		logger.debug("3");
		seleniumRobot.getSelenium().click("//input[@name='watermark' and @value='2']");

		logger.debug("Mask: " + pathToMask);

		logger.debug("4");
		seleniumRobot.getSelenium().type("//form[@id='ajaxUploadForm']/label/input", pathToMask.getAbsolutePath());

		logger.debug("5");
		seleniumRobot.getSelenium().click("//div[@id='pic_form']/p/input");

		String style = seleniumRobot.getSelenium().getAttribute("//div[@id='pic_copyright']@style");

		logger.debug("6");

		seleniumRobot.getSelenium().click("//div[@id='butt_cont']/div/div/a");

		logger.debug("7");

		String linkDiv = "";
		while (true) {
			linkDiv = seleniumRobot.getSelenium().getText("//div[@id='links']");
			logger.debug("linkDiv on " + linkDiv);
			if (linkDiv.trim().length() > 0)
				break;
			Thread.sleep(1 * 1000);
		}

		List<File> processedImages = new ArrayList<File>();
		for (int j = 0; j < rawImageFiles.size(); j++) {
			String urlToImage = seleniumRobot.getSelenium().getAttribute("//div[@id='links']//li[" + (j + 1) + "]/a@href");
			String filePath = prefix + product.getId() + "_" + (j + index) + ".jpg";
			File file = new File(path, filePath);
			createdFiles.add(file);
			FileUtils.copyURLToFile(new URL(urlToImage), file);
			logger.debug("File size: " + file.length());
			if (file.length() < MIN_FILE_SIZE) {
				FileUtils.forceDelete(file);
			} else {
				processedImages.add(file);
			}
		}

		seleniumRobot.getSelenium().click("//div[@id='save']/div[last()]/a");
		return processedImages;
	}

}
