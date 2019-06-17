package fi.jonix.huutonet.robot;

import java.io.File;

import org.apache.log4j.Logger;
import org.openqa.selenium.firefox.FirefoxProfile;
import org.openqa.selenium.support.ui.Wait;

public class SeleniumRobot implements BaseRobot {

	public static final Logger logger = Logger.getLogger(SeleniumRobot.class);

	protected CustomSelenium selenium;
	private boolean initiated;
	protected CustomWebDriver webDriver;
	protected Wait wait;
	
	
	
	public SeleniumRobot() {
		initiated = false;
	}
	
	public CustomSelenium getSelenium() {
		return selenium;
	}

	public CustomWebDriver getWebDriver() {
		return webDriver;
	}

	public Wait getWait() {
		return wait;
	}

	/*public SeleniumServer getSeleniumServer() {
		return seleniumServer;
	}*/

	private CustomWebDriver getWebDriver(/*BrowserType browserType*/String seleniumProfile) {
		/*switch (browserType) {
		case FIREFOX:
			// Need to implement
			return null;
		case IE:
			// Need to implement
			return null;
		case CHROME:
			// Need to implement
			return null;
		case HTMLUNIT:
			// Need to implement
			return null;
		default:
			throw new RuntimeException("Browser type unsupported");
		}*/
		if (seleniumProfile != null) {
			logger.debug("Using profile " + seleniumProfile);
			File profileLocation = new File(seleniumProfile);
			FirefoxProfile profile = new FirefoxProfile(profileLocation);
			//profile.setPreference("webdriver.load.strategy", "fast");
			//profile.setPreference("webdriver.load.strategy", "unstable");
			return new CustomWebDriver(profile);
		} else {
			logger.debug("Using default profile.");
			//FirefoxProfile profile = new ProfilesIni().getProfile("default");
			//FirefoxProfile profile = new FirefoxProfile();
			//profile.setPreference("webdriver.load.strategy", "fast");
			//profile.setPreference("webdriver.load.strategy", "unstable");
			//return new CustomWebDriver(profile);
			return new CustomWebDriver();
		}
	}
	
	/*public enum BrowserType {
		FIREFOX, IE, CHROME, HTMLUNIT
	}*/
	
	public void init(String seleniumProfile) throws Exception {
		if (!initiated) {
			webDriver = getWebDriver(seleniumProfile);
			//webDriverDefaultSelenium = new DefaultSelenium(new WebDriverCommandProcessor("http://www.google.fi",webDriver));
			//wait = new WebDriverWait(webDriver, 30);
			//driver.get("http://www.google.fi");
			selenium = new CustomSelenium(webDriver, "http://www.google.fi");
			initiated = true;
		}
		if (!initiated) {
			logger.error("Couldn't initialize selenium.");
			throw new Exception("Couldn't initialize selenium, tried on many different ports and none of them worked.");
		}
	}
	
	public void tearDown() throws Exception {
		if (initiated) {
			logger.debug("Tearing down selenium 1.");
			logger.debug("Tearing down selenium 2.");
			webDriver.close();
			logger.debug("Selenium torn down.");
			initiated = false;
		}
	}

	public void setInitiated(boolean initiated) {
		this.initiated = initiated;
	}

	public boolean isInitiated() {
		return initiated;
	}
	
}
