package fi.jonix.huutonet.robot.image;

import org.apache.log4j.Logger;

import com.thoughtworks.selenium.SeleniumException;

import fi.jonix.huutonet.domain.model.Image;
import fi.jonix.huutonet.domain.model.dao.ImageDAO;
import fi.jonix.huutonet.robot.CustomSelenium;
import fi.jonix.huutonet.robot.SeleniumRobot;

@Deprecated
public class CheapImageHostingUploader implements ImageUploader{

	public static final Logger logger = Logger.getLogger(CheapImageHostingUploader.class);

	private static String BASE_URL = "http://www.cheapimagehosting.com/";
	private ImageDAO imageDAO;
	private SeleniumRobot seleniumRobot;

	public boolean isImageAvailable(Image image) throws Exception {
		if (image == null || image.getImageHostUrl() == null)
			return false;
		logger.debug("Check if this picture exists: " + image.getLocalFileUrl());
		
		CustomSelenium selenium = seleniumRobot.getSelenium();
		selenium.open(image.getImageHostUrl());
		selenium.waitForPageToLoad();
		
		String title = selenium.getTitle();
		if(title.indexOf("JPG") > -1 || title.indexOf("JPEG") > -1)
			return true;
		
		return false;
	}
	
	private void logIn() throws Exception {
		CustomSelenium selenium = seleniumRobot.getSelenium();
		selenium.type("//input[@name='username']", "someusername");
		selenium.type("//input[@name='password']", "somepassword");
		selenium.click("//input[@name='login']");
		selenium.waitForPageToLoad();
	}
	
	public boolean uploadImage(Image image) throws Exception {
		if (image == null) {
			return false;
		}
		logger.debug("Uploading image " + image.getId());
		
		CustomSelenium selenium = seleniumRobot.getSelenium();
		selenium.open(BASE_URL);
		selenium.waitForPageToLoad();
		String h3 = null;
		try {
			h3 = selenium.getText("//h3");
		} catch (SeleniumException se) {
			logger.error("Couldn't determine if user is logged on.");
			return false;
		}
		boolean loggedIn = (h3 != null && h3.indexOf("teamlauber") > -1);

		if(!loggedIn){
			this.logIn();
			selenium.open(BASE_URL);
			selenium.waitForPageToLoad();
		}
		
		boolean success = false;
		for (int i = 0; i < 5; i++) {
			try {
				selenium.click("//span/a[@href='upload.php']");
				selenium.waitForPageToLoad();
				
				String pathInComputer = image.getLocalFileUrl();
				selenium.type("//form[@enctype='multipart/form-data']/div/div[3]//input[1]", pathInComputer);
				selenium.click("//input[@name='upload']");
				selenium.waitForPageToLoad();
				
				//logger.debug("before getting attribute");
				String hostUrl = selenium.getAttribute("//a[contains(@href,'images/teamlauber')]@href");
				//logger.debug("after getting attribute");
				image.setImageHostUrl(hostUrl);
				this.imageDAO.save(image);
				success = true;
				break;
			} catch (SeleniumException e) {
				logger.debug("The following selenium error came when trying to upload image: ",e);
				if (e.getMessage() != null && e.getMessage().startsWith("Timed out after")) {
					logger.error("Selenium timed out");
					continue;
				} else {
					//throw e;
					break;
				}
			}
		}

		if (!success) {
			//logger.info("Seems like the image uploading failed.");
			logger.debug("body text = " + selenium.getBodyText());
			throw new Exception("Image uploading failed");
		}
		return success;
	}

	public void setImageDAO(ImageDAO imageDAO) {
		this.imageDAO = imageDAO;
	}

	public void setSeleniumRobot(SeleniumRobot seleniumRobot) {
		this.seleniumRobot = seleniumRobot;
	}

}
