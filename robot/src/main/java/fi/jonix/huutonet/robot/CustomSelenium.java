package fi.jonix.huutonet.robot;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.apache.log4j.Logger;
import org.aspectj.weaver.patterns.ThisOrTargetAnnotationPointcut;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverBackedSelenium;

import com.thoughtworks.selenium.DefaultSelenium;
import com.thoughtworks.selenium.Selenium;
import com.thoughtworks.selenium.SeleniumException;

public class CustomSelenium extends WebDriverBackedSelenium implements Selenium {
	
	public static final Logger logger = Logger.getLogger(CustomSelenium.class);
	protected Long timeout = 60000L;
	protected Long sleep = 500L;
	private String maxWaitTime = "60000";
	
	/*public CustomSelenium(String serverHost, int serverPort, String browserStartCommand, String browserURL) {
		super(serverHost, serverPort, browserStartCommand, browserURL);
	}*/
	
	public CustomSelenium(WebDriver baseDriver, String baseUrl) {
		super(baseDriver, baseUrl);
	}

	@Override
	public void setTimeout(String timeout) {
		this.maxWaitTime = timeout;
		this.timeout = Long.parseLong(timeout);
		super.setTimeout(timeout);
	}
	
	public void setTimeout(long timeout) {
		setTimeout("" + timeout);
	}
	
	@Override
	public String getAttribute(String attributeLocator) {
		try {
			this.waitForAttribute(attributeLocator);
		} catch (TimeoutException e) {
			logger.error("TimeoutException");
		}
		return super.getAttribute(attributeLocator);
	}
	
	@Override
	public void select(String selectLocator, String optionLocator) {
		try {
			int eqIndex = optionLocator.indexOf("=");
			if(eqIndex > 0){
				int indIndex = optionLocator.indexOf("index");
				int valIndex = optionLocator.indexOf("value");
				if (indIndex >= 0 && indIndex < eqIndex) {
					this.waitForElement(selectLocator + "//option[" + optionLocator.substring(eqIndex+1) + "]");
				} else if (valIndex >= 0 && valIndex < eqIndex) {
					this.waitForElement(selectLocator + "//option[@value='" + optionLocator.substring(eqIndex+1) + "']");
				} else {
					this.waitForElement(selectLocator + "//option[@" + optionLocator + "]");
				}
			} else {
				this.waitForElement(selectLocator + "//option[contains(text(), '" + optionLocator + "')]");
			}
		} catch (TimeoutException e) {
			logger.error("TimeoutException");
		}
		super.select(selectLocator, optionLocator);
	}

	@Override
	public void type(String locator, String value) {
		try {
			this.waitForElement(locator);
		} catch (TimeoutException e) {
			logger.error("TimeoutException");
		}
		super.type(locator, value);
	}
	
	@Override
	public void click(String locator) {
		try {
			this.waitForElement(locator);
		} catch (TimeoutException e) {
			logger.error("TimeoutException");
		}
		super.click(locator);
	}
	
	@Override
	public String getValue(String locator) {
		try {
			this.waitForElement(locator);
		} catch (TimeoutException e) {
			logger.error("TimeoutException");
		}
		return super.getValue(locator);
	}
	
	@Override
	public String getText(String locator) {
		try {
			this.waitForElement(locator);
		} catch (TimeoutException e) {
			logger.error("TimeoutException");
		}
		return super.getText(locator);
	}
	
	@Override
	public void open(String url) {
		super.open(url);
		waitForPageToLoad();
	}
	
	public void open(String url, int tryCount) {
		this.open(url, tryCount, maxWaitTime);
	}
	
	public void open(String url, int tryCount, String timeout) {
		for (int i = 1; i < tryCount + 1; i++) {
			try {
				super.open(url);
				super.waitForPageToLoad(timeout);
				if (i > 1) {
					logger.info("Reloading of url " + url + " succeeded at attempt number " + i + ".");
				}
				return;
			} catch (SeleniumException e) {
				logger.info("Reloading failed: " + url + " at attempt number " + i + ".");
				if (i >= tryCount) {
					throw e;
				}
			}
		}
	}
	
	@Override
	public void waitForPageToLoad(String timeout) {
		long start = System.currentTimeMillis();
		try {
			super.waitForPageToLoad(timeout);
		} catch (Exception e) {
			try {
				Thread.sleep(10000L);
			} catch (InterruptedException ex) {}
		}
		
		/* If page loading takes less than minimum time, some sleep will take place.
		 * With my computer normal page loading takes 30 mill seconds.
		 */
		if (System.currentTimeMillis() < start + 10) {
			logger.debug("Page loading took too little time - lets take a nap");
			try {
				Thread.sleep(5000L);
			} catch (InterruptedException ex) {}
		}
	}
	
	protected void waitForAttribute(String attributeLocator) throws TimeoutException {
		Long start = System.currentTimeMillis();
		while (System.currentTimeMillis() < start + timeout) {
			if (isAttributePresent(attributeLocator)) {
				return;
			}
			try {
				Thread.sleep(sleep);
			} catch (InterruptedException e) {
				throw new TimeoutException();
			}
		}
		
		try {
			FileWriter fw = new FileWriter(new File("dump.html"));
			fw.write(getHtmlSource());
			logger.debug("waitForElement: locator not found, dumped html to dump.html");
		} catch (IOException ioe) {
			logger.debug("waitForElement: locator not found, couldn't dump html: ",ioe);
		}
		
		logger.info("Could not find " + attributeLocator);
	}
	
	public void waitForPageToLoad() {
		waitForPageToLoad(maxWaitTime);
	}
	
	
	protected void waitForElement(String locator) throws TimeoutException {
		Long start = System.currentTimeMillis();
		while (System.currentTimeMillis() < start + timeout) {
			if (isElementPresent(locator)) {
				return;
			}
			try {
				Thread.sleep(sleep);
			} catch (InterruptedException e) {
				throw new TimeoutException();
			}
		}
		
		try {
			FileWriter fw = new FileWriter(new File("dump.html"));
			fw.write(getHtmlSource());
			logger.debug("waitForElement: locator not found, dumped html to dump.html");
		} catch (IOException ioe) {
			logger.debug("waitForElement: locator not found, couldn't dump html: ",ioe);
		}
		
		logger.debug("Could not find " + locator);
	}
	
	public boolean isAttributePresent(String attributeLocator) throws TimeoutException {
		try {
			super.getAttribute(attributeLocator);
			return true;
		} catch (Exception e) {}
		return false;
	}
	
	public class TimeoutException extends Exception {
		private static final long serialVersionUID = -3417451675873960066L;
	}
	
}
