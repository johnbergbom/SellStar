package fi.jonix.huutonet.tools;

import java.math.BigDecimal;
import java.util.Date;

import org.apache.log4j.Logger;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import fi.jonix.huutonet.domain.model.Currency;
import fi.jonix.huutonet.domain.model.dao.CurrencyDAO;
import fi.jonix.huutonet.robot.CustomWebDriver;
import fi.jonix.huutonet.robot.SeleniumRobot;

@Component(value = "exchangeRateFetcher")
public class ExchangeRateFetcher {
	
	public static final Logger logger = Logger.getLogger(ExchangeRateFetcher.class);

	@Autowired
	private SeleniumRobot seleniumRobot;
	
	@Autowired
	private CurrencyDAO currencyDAO;
	
	/**
	 * This method updates the currency list by fetching the current
	 * values from the web using selenium. If it's for some reason not
	 * possible to fetch exchange rates from the web, the database will
	 * not be updated (then the old values can still be used, which is
	 * probably okay, because if google happens to be down, then the
	 * values from the day before should be pretty safe to use, since
	 * currencies usually don't fluctuate considerably from one day to
	 * another).
	 */
	public void fetchExchangeRates() {
		logger.info("Fetching current exchange rates from the web.");
		Currency dollar = currencyDAO.get(Currency.DOLLAR);
		long daysSinceLastUpdate = (System.currentTimeMillis() - dollar.getUpdateDate().getTime()) / (1000*3600*24);
		Currency euro = currencyDAO.get(Currency.EURO);
		long tempSinceLastUpdate = (System.currentTimeMillis() - euro.getUpdateDate().getTime()) / (1000*3600*24);
		daysSinceLastUpdate = Math.max(daysSinceLastUpdate, tempSinceLastUpdate);
		try {
			CustomWebDriver driver = seleniumRobot.getWebDriver();
			driver.get("http://www.google.fi/");
			driver.waitForElement(By.xpath("//input[@id='gbqfq']")); //wait for page to load
			WebElement searchBox = driver.findElement(By.xpath("//input[@id='gbqfq']"));
			searchBox.clear();
			searchBox.sendKeys("EURO USD");
			try {
				WebElement submitButton = driver.findElement(By.xpath("//button[@id='gbqfba']"));
				submitButton.click();
			} catch (Exception e) {
				logger.debug("Couldn't press \"Google-haku\"-button. Perhaps the javascript has already loaded the next page: " + e.getMessage());
			}
			driver.waitForElement(By.xpath("//h2[@class='r']/b")); //wait for page to load
			String result = driver.findElement(By.xpath("//h2[@class='r']/b")).getText();
			String oneEuroInUsdStr = result.substring(result.indexOf("=")+1, result.indexOf("U")-1).trim();
			logger.info("oneEuroInUsdStr = " + oneEuroInUsdStr);
			BigDecimal oneEuroInUsd = new BigDecimal(oneEuroInUsdStr);
			BigDecimal diff = dollar.getExchangeRateToEuro().subtract(oneEuroInUsd);
			if (diff.abs().compareTo(new BigDecimal("0.1")) > 0) {
				throw new RuntimeException("Exchange rate changed too much: old dollar exchange rate = "
						+ dollar.getExchangeRateToEuro() + ", new exchange rate = " + oneEuroInUsd);
			}
			if (oneEuroInUsd.doubleValue() > 1.55 || oneEuroInUsd.doubleValue() < 1.0) {
				throw new RuntimeException("Fishy dollar exchange rate: " + oneEuroInUsd);
			}
			dollar.setExchangeRateToEuro(oneEuroInUsd);
			dollar.setUpdateDate(new Date());
			//BigDecimal euro2Euro = new BigDecimal("1.0");
			//euro.setExchangeRateToEuro(euro2Euro);
			euro.setUpdateDate(new Date());
			logger.info("exchange rates updated.");
		} catch (Exception e) {
			logger.error("Error fetching current currency exchange rates. Not updating database: ",e);
			if (daysSinceLastUpdate > 3) {
				throw new RuntimeException("Last exchange rate update is " + daysSinceLastUpdate + " days ago. Bailing out.");
			}
		}
	}
	
}
