package fi.jonix.huutonet.robot;

import org.apache.log4j.Logger;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
//import org.openqa.selenium.WebDriverBackedSelenium;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxProfile;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.Wait;
import org.openqa.selenium.support.ui.WebDriverWait;
//import org.openqa.selenium.JavascriptExecutor;

public class CustomWebDriver extends FirefoxDriver {

	private static int DEFAULT_TIMEOUT_IN_SECONDS = 60;
	private static int DEFAULT_PAGE_LOAD_TRY_COUNT = 1;
	
	public static final Logger logger = Logger.getLogger(CustomWebDriver.class);
	
	//private WebDriverBackedSelenium selenium;
	
	public CustomWebDriver(FirefoxProfile profile) {
		super(profile);
		//this.selenium = new WebDriverBackedSelenium(this, "http://www.google.fi");
	}
	
	public CustomWebDriver() {
		super();
	}
	
	public void get(String url, By waitForVisibilityOfElement) {
		get(url,waitForVisibilityOfElement,DEFAULT_PAGE_LOAD_TRY_COUNT,DEFAULT_TIMEOUT_IN_SECONDS);
	}
	
	public void setValueWithJavascript(WebElement element, String value) {
        //((JavascriptExecutor)this).executeScript("arguments[0].value = arguments[1]", element, value);
        executeScript("arguments[0].value = arguments[1]", element, value);
    }
	
	public void get(String url, By waitForVisibilityOfElement, int tryCount) {
		get(url,waitForVisibilityOfElement,tryCount,DEFAULT_TIMEOUT_IN_SECONDS);
	}
	
	public void get(String url, int tryCount) {
		get(url,null,tryCount,DEFAULT_TIMEOUT_IN_SECONDS);
	}
	
	public void get(String url, By waitForVisibilityOfElement, int tryCount, int timeoutInSeconds) {
		for (int i = 1; i < (tryCount + 1); i++) {
			try {
				get(url);
				if (waitForVisibilityOfElement != null) {
					waitForElement(waitForVisibilityOfElement,timeoutInSeconds);
				}
				if (i > 1) {
					logger.info("Reloading of url " + url + " succeeded at attempt number " + i + ".");
				}
				return;
			} catch (TimeoutException e) {
				if (tryCount > 1) {
					logger.info("Reloading failed: " + url + " at attempt number " + i + ".");
				}
				if (i >= tryCount) {
					throw e;
				}
			}
		}
	}
	
	/* Sometimes we get problems with selenium hanging forever on get(url). Next time this is a problem
	 * we can do some testing using the old selenium api (where it's possible to set a timeout for the
	 * open call). Then we can try the following:
	 * logger.info("first");
	 * driver.getTest("http://www.focalprice.com/CA070S/something.html", null, 1, 30);
	 * logger.info("second");
	 * driver.get("http://www.focalprice.com/CA070S/something.html", null, 1, 30);
	 * logger.info("third");
	 * And then see if the first one works but the second one hangs forever.
	 * 
	 * There are also two other ways that we could try to solve the problem:
	 * 1.) Start a separate thread that does a reload if super.get(...) hasn't returned within a minute
	 *     (by pressing esc). See also http://stackoverflow.com/questions/7348387/selenium-htmlunit-freeze-randomly-loading-a-web-page
	 *     (that example uses HtmlDriver but FirefoxDriver also has a similar method, or else an awt-robot can probably be used).
	 * 2.) Check if some firefox plugin can accomplish the same this (i.e. solution has nothing to do with selenium). */
	/*public void getTest(String url, By waitForVisibilityOfElement, int tryCount, int timeoutInSeconds) {
		for (int i = 1; i < (tryCount + 1); i++) {
			try {
				//long atStart = System.currentTimeMillis();
				selenium.setTimeout(""+timeoutInSeconds*1000);
				selenium.open("http://www.focalprice.com/CA070S/something.html");
				//get(url);
				if (waitForVisibilityOfElement != null) {
					//long elapsed = System.currentTimeMillis() - atStart;
					waitForElement(waitForVisibilityOfElement,timeoutInSeconds);
				}
				if (i > 1) {
					logger.info("Reloading of url " + url + " succeeded at attempt number " + i + ".");
				}
				return;
			} catch (TimeoutException e) {
				if (tryCount > 1) {
					logger.info("Reloading failed: " + url + " at attempt number " + i + ".");
				}
				if (i >= tryCount) {
					throw e;
				}
			}
		}
	}*/
	
	public void waitForElement(By waitForVisibilityOfElement) {
		waitForElement(waitForVisibilityOfElement, DEFAULT_TIMEOUT_IN_SECONDS);
	}
	
	private void waitForElement(By waitForVisibilityOfElement, int timeoutInSeconds) {
		Wait<WebDriver> wait = new WebDriverWait(this, timeoutInSeconds);
		wait.until(visibilityOfElementLocated(waitForVisibilityOfElement));
	}
	
	private ExpectedCondition<WebElement> visibilityOfElementLocated(final By locator) {
		return new ExpectedCondition<WebElement>() {
			public WebElement apply(WebDriver driver) {
				WebElement toReturn = driver.findElement(locator);
				if (toReturn.isDisplayed()) {
					return toReturn;
				}
				return null;
			}
		};
	}

	/* This method behaves the same way as the superclass's findElement method
	 * with the exception that null is returned when the element wasn't found rather
	 * than throwing an exception. */
	public WebElement findElementOrNull(By by) {
		WebElement element = null;
		try {
			element = super.findElement(by);
		} catch (NoSuchElementException e) {
			return null;
		}
		return element;
	}
	
	public boolean isElementPresent(By by) {
		return (findElementOrNull(by) != null);
	}

}
