package fi.jonix.huutonet.robot.image;

import org.apache.log4j.Logger;

import fi.jonix.huutonet.domain.model.Image;
import fi.jonix.huutonet.domain.model.dao.ImageDAO;
import fi.jonix.huutonet.robot.CustomSelenium;
import fi.jonix.huutonet.robot.SeleniumRobot;

public class ImageHostUploader implements ImageUploader{

	public static final Logger logger = Logger.getLogger(ImageHostUploader.class);

	private static String BASE_URL = "http://www.imagehost.org/";
	private ImageDAO imageDAO;
	private SeleniumRobot seleniumRobot;

	public void setSeleniumRobot(SeleniumRobot seleniumRobot) {
		this.seleniumRobot = seleniumRobot;
	}

	public void setImageDAO(ImageDAO imageDAO) {
		this.imageDAO = imageDAO;
	}

	public boolean isImageAvailable(Image image) throws Exception {
		if (image == null || image.getImageHostUrl() == null)
			return false;
		logger.debug("Check if this picture exists: " + image.getLocalFileUrl());
		CustomSelenium selenium = seleniumRobot.getSelenium();
		selenium.open(image.getImageHostUrl());
		selenium.waitForPageToLoad();
		String imageWidth = null;
		try {
			imageWidth = selenium.getAttribute("//img@width");
		} catch (Exception e) {
		}
		if (imageWidth != null && imageWidth.length() > 0)
			return true;
		String fileExtension = image.getImageHostUrl().substring(image.getImageHostUrl().length() - 3);
		String title = selenium.getTitle();
		logger.debug("fileExtension = " + fileExtension + ", title = " + title);
		if (title == null || title.trim().equals("")) {
			return false;
		}
		if (fileExtension.equalsIgnoreCase("JPG") || fileExtension.equalsIgnoreCase("JPEG")) {
			String lastPart = title.substring(title.indexOf(fileExtension) + 3).toUpperCase();
			if (lastPart.indexOf("JPG") < 0 && lastPart.indexOf("JPEG") < 0)
				return false;
		} else {
			if (title.substring(title.indexOf(fileExtension) + 3).toUpperCase().indexOf(fileExtension.toUpperCase()) < 0)
				return false;
		}

		if (title.replaceAll(" ", "").indexOf("125×125") > -1)
			return false;
		logger.debug("old image host: image found");
		return true;
	}

	public boolean uploadImage(Image image) throws Exception {
		if (image == null)
			return false;
		// Upload image

		CustomSelenium selenium = seleniumRobot.getSelenium();
		boolean success = false;
		for (int i = 0; i < 5; i++) {

			logger.debug("Uploading picture because it wasn't found: " + image.getLocalFileUrl());
			selenium.open(BASE_URL);
			selenium.waitForPageToLoad();

			String pathInComputer = image.getLocalFileUrl();
			logger.debug("Uploading picture file:: " + pathInComputer);
			selenium.type("//table[@class='upload']/tbody/tr[1]//input", pathInComputer);
			selenium.click("//input[@type='submit']");
			selenium.waitForPageToLoad();

			if (selenium.isTextPresent("Internal error")) {
				continue;
			}

			// Save image information
			String hostUrl = null;
			try {
				hostUrl = selenium.getValue("//table[@class='links']/tbody/tr[7]//input");
			} catch (Exception e) {
			}
			if (hostUrl == null) {
				try {
					hostUrl = selenium.getValue("//table[@class='links ']/tbody/tr[7]//input");
				} catch (Exception e) {
					try {
						hostUrl = selenium.getValue("//table[@class='links ']/tbody/tr[4]//input");
					} catch (Exception ex) {
						try {
							hostUrl = selenium.getValue("//table[@class='links']/tbody/tr[4]//input");
						} catch (Exception exc) {
							continue;
						}
					}
				}
			}

			image.setImageHostUrl(hostUrl);
			this.imageDAO.save(image);
			success = true;
			break;
		}

		if (!success) {
			//logger.info("Seems like the image uploading failed.");
			logger.debug(selenium.getBodyText());
			throw new Exception("Image uploading failed");
		}
		return success;
	}

}
