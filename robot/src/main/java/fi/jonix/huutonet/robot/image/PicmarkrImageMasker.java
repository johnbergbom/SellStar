package fi.jonix.huutonet.robot.image;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import fi.jonix.huutonet.domain.model.Product;
import fi.jonix.huutonet.robot.SeleniumRobot;

@Component(value = "picmarkrImageMasker")
public class PicmarkrImageMasker {

	public static final Logger logger = Logger
			.getLogger(PicmarkrImageMasker.class);
	private static long MIN_FILE_SIZE = 500;

	public static enum WatermarkPosition {
		LEFT_UP, CENTER
	};

	@Autowired
	private SeleniumRobot seleniumRobot;

	public List<File> getProcessedImages(List<File> imageFiles,
			File maskPath, File productImagePath, Product product,
			WatermarkPosition position) throws Exception {
		if (!maskPath.exists()) {
			throw new Exception("Mask image not found.");
		}
		List<File> processedFiles = new ArrayList<File>();
		logger.info("Total " + imageFiles.size() + " images");
		List<File> imagesToProcess = new ArrayList<File>();
		for (File imageFile : imageFiles) {
			imagesToProcess.add(imageFile);
			if (imagesToProcess.size() > 4) {
				processedFiles.addAll(this.processedImages(imagesToProcess,
						maskPath, productImagePath, product.getId() + "_", position));
				imagesToProcess.clear();
			}
		}
		if (imagesToProcess.size() > 0) {
			processedFiles.addAll(this.processedImages(imagesToProcess,
					maskPath, productImagePath, product.getId() + "_", position));
		}
		return processedFiles;
	}

	private List<File> processedImages(List<File> imageFiles, File maskPath,
			File productImagePath, String prefix, WatermarkPosition position)
			throws Exception {
		logger.info("Processing " + imageFiles.size() + " images");
		List<File> processedFiles = new ArrayList<File>();
		this.seleniumRobot.getSelenium().open("http://picmarkr.com/index.php");
		if (this.seleniumRobot.getSelenium().isElementPresent(
				"//a[contains(text(),'to other images')]")) {
			this.seleniumRobot.getSelenium().click(
					"//a[contains(text(),'to other images')]");
			this.seleniumRobot.getSelenium().waitForPageToLoad("60000");
		}

		logger.debug("1");
		for (int j = 0; j < imageFiles.size(); j++) {
			seleniumRobot.getSelenium().type(
					"//input[@name='file[" + (j + 1) + "]']",
					imageFiles.get(j).getPath());
		}

		logger.debug("2");
		seleniumRobot.getSelenium().click("//form[@name='f1']/div/div/div/a");

		Thread.sleep(2000);

		logger.debug("3");

		while (seleniumRobot.getSelenium()
				.getAttribute("//div[@id='img2']@class").indexOf("active") < 0) {
			seleniumRobot.getSelenium().click(
					"//input[@name='watermark' and @value='2']");
			Thread.sleep(500);
		}

		logger.debug("Mask: " + maskPath);

		logger.debug("4");
		seleniumRobot.getSelenium().type(
				"//form[@id='ajaxUploadForm']/label/input",
				maskPath.getAbsolutePath());

		Thread.sleep(2000);

		if (position == WatermarkPosition.CENTER) {
			while (seleniumRobot.getSelenium()
					.getAttribute("//div[@id='cm2']@class").indexOf("active") < 0) {
				logger.debug("click centre");
				seleniumRobot.getSelenium().click("//div[@id='cm2']/img");
				Thread.sleep(1000);
			}
		}

		logger.debug("5");

		seleniumRobot.getSelenium().click("//div[@id='pic_form']/p/input");

		Thread.sleep(2000);

		String style = seleniumRobot.getSelenium().getAttribute(
				"//div[@id='pic_copyright']@style");

		logger.debug("6");

		seleniumRobot.getSelenium().click("//div[@id='butt_cont']/div/div/a");

		logger.debug("7");

		String linkDiv = "";
		for (int i = 0; i < 100; i++) {
			if (seleniumRobot.getSelenium().isElementPresent(
					"//div[@id='links']")) {
				linkDiv = seleniumRobot.getSelenium().getText(
						"//div[@id='links']");
				logger.info("linkDiv on " + linkDiv);
				if (linkDiv.trim().length() > 0)
					break;
			}
			Thread.sleep(1 * 1000);
			logger.info("8 - waiting");
		}
		if(linkDiv.trim().length() == 0)
			throw new Exception("Something wrong with //div[@id='links'].");

		logger.debug("9");

		for (int j = 0; j < imageFiles.size(); j++) {
			String urlToImage = seleniumRobot.getSelenium().getAttribute(
					"//div[@id='links']//li[" + (j + 1) + "]/a@href");
			String filePath = prefix + j + ".jpg";
			File file = new File(productImagePath, filePath);
			FileUtils.copyURLToFile(new URL(urlToImage), file);
			// FileUtils.copyURLToFile(new URL(urlToImage), new File(path,
			// "temp_" + System.currentTimeMillis() + ".jpg"));
			logger.info("File size: " + file.length());
			if (file.length() < MIN_FILE_SIZE) {
				logger.info("Deleting too small file");
				FileUtils.forceDelete(file);
			} else {
				processedFiles.add(file);
			}
		}
		seleniumRobot.getSelenium().click("//div[@id='save']/div[last()]/a");
		return processedFiles;
	}

}
