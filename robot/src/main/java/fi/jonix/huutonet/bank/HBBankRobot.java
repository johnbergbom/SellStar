package fi.jonix.huutonet.bank;

import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import fi.jonix.huutonet.domain.VirtualCoin;
import fi.jonix.huutonet.domain.model.Currency;
import fi.jonix.huutonet.domain.model.Transaction;
import fi.jonix.huutonet.domain.model.dao.CurrencyDAO;
import fi.jonix.huutonet.domain.model.dao.SettingsDAO;
import fi.jonix.huutonet.exception.BankException;
import fi.jonix.huutonet.robot.CustomSelenium;
import fi.jonix.huutonet.robot.SeleniumRobot;
import fi.jonix.huutonet.tools.Encryption;
import fi.jonix.huutonet.tools.MathUtilities;

/**
 * 
 * TODO: korjaa seuraava bugi: Meddelande-kohdasta haetaan vaan ensimmäinen rivi, vaikka niitä olisikin useita.
 *       Toinen virhe on se, että näyttää siltä, että sekä transaction.message että transaction.transaction.ref_number
 *       haetaan samasta webliittymän kentästä. Itse asiassa tämä saattaa johtua siitä, että haetaan (luulen) kaikki
 *       tiedot päänäkymästä, ja siinä näkyy vain yhtenä rivinä viitenumero/meddelande. Joutuu todennäköisesti avaamaan
 *       jokainen transactio klikkaamalla rivin linkkiä ja sieltä hakea oikeat tiedot. => Tämä ei olekaan niin kriittinen
 *       juttu, koska viitenumero/meddelande-kenttään tulee aina viitenumero jos semmoinen löytyy, vaikka olisi myös
 *       meddelande olemassa. Viitenumero/meddelande-kenttään tulee puhdas meddelande ainoastaan jos viitenumero puuttuu.
 *
 */
@Component(value = "bankRobot")
public class HBBankRobot extends BankRobot {
	
	public static final Logger logger = Logger.getLogger(HBBankRobot.class);

	private static final String BASE_URL = "https://www2.handelsbanken.fi";
	private DateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy");
	public static String OUT_OF_BANKCODES = "Ran out of bank codes";
	public static String CONFIRMATION_FAILED = "Confirmation failed";
	public static String FAULTY_ACCOUNT_NUMBER = "Faulty account number";
	public static String NO_CONFIRMABLE_TRANSACTIONS = "No confirmable transactions";
	public static String FAULTY_CURRENCY = "Faulty currency";
	private static String FIRST_ACCOUNT = "//form/table[2]/tbody/tr[2]/td[1]/a"; //use this for first account
	private static String SECOND_ACCOUNT = "//form/table[2]/tbody/tr[3]/td[1]/a"; //use this for second account

	@Autowired
	private SeleniumRobot seleniumRobot;
	
	@Autowired
	private SettingsDAO settingsDAO;
	
	@Autowired
	private CurrencyDAO currencyDAO;
	
	private boolean loggedIn = false;

	@Override
	public VirtualCoin getAccountBalance() {
		logger.info("Getting account balance");
		CustomSelenium selenium = seleniumRobot.getSelenium();
		selenium.click("//ul[@id='PalvelutOtsikkoLista']/li[3]/a");
		selenium.waitForPageToLoad();
		selenium.click(FIRST_ACCOUNT);
		selenium.waitForPageToLoad();
		String accountBalanceStr = selenium.getText("//div[@id='ValilehdetSisalto']/../table[2]//tr[3]//td[2]");
		logger.debug("accountBalanceStr = " + accountBalanceStr);
		Long currency = null;
		if (accountBalanceStr.indexOf("EUR") >= 0) {
			currency = Currency.EURO;
		} else {
			throw new RuntimeException("Unexpected currency in the bank: " + accountBalanceStr);
		}
		accountBalanceStr = accountBalanceStr.replace("EUR","").replaceAll(" ","").replaceAll("\\.","").replace(",",".");
		logger.debug("accountBalanceStr2 = " + accountBalanceStr);
		BigDecimal balance = new BigDecimal(accountBalanceStr);
		logger.info("Account balance is " + balance);
		VirtualCoin vcBalance = new VirtualCoin(currency,balance);
		return vcBalance;
	}

	@Override
	public List<Transaction> getTransactions(Calendar beginCalendar) throws Exception {
		CustomSelenium selenium = seleniumRobot.getSelenium();
		//selenium.open(BASE_URL);
		selenium.click("//ul[@id='PalvelutOtsikkoLista']/li[3]/a");
		selenium.waitForPageToLoad();
		selenium.click(FIRST_ACCOUNT);
		selenium.waitForPageToLoad();
		List<Transaction> transactions = parseTransactionsFromList(beginCalendar);
		if (transactions == null || transactions.isEmpty()) {
			throw new Exception("Transaction list is empty - parseTransactionsFromList must have failure!");
		}
		
		/* Reorder the transactions so that the oldest one comes first. */
		List<Transaction> reorderedTransactions = new ArrayList<Transaction>();
		for (int i = transactions.size() - 1; i >= 0; i--) {
			reorderedTransactions.add(transactions.get(i));
		}
		return reorderedTransactions;
	}

	private List<Transaction> parseTransactionsFromList(Calendar beginCalendar) {
		logger.info("Fetching transactions from date " + beginCalendar.getTime().toLocaleString());
		CustomSelenium selenium = seleniumRobot.getSelenium();
		Calendar mirrorCalendar = (Calendar)beginCalendar.clone();
		mirrorCalendar.set(Calendar.HOUR_OF_DAY, 12);
		mirrorCalendar.add(Calendar.DATE, -1);
		List<Transaction> transactions = new ArrayList<Transaction>();
		int page = 1;
		try {
			while (true) {
				logger.debug("Fetching transactions from page " + page);
				int i = 0;
				try {
					while (true) {
						i++;
						Transaction transaction = this.getTransaction(i);
						if (transaction.getDate().getTime() < mirrorCalendar.getTime().getTime()) {
							return transactions;
						}
						transactions.add(transaction);
					}
				} catch (Exception e) {
					if (i < 5)
						break;
				}
				selenium.click("//div[@id='ValilehdetSisalto']/../table[4]//tr[1]/td[2]//a[last()]");
				selenium.waitForPageToLoad();
				page++;
			}
		} catch (Exception e) {
		}
		return transactions;
	}

	private String getOneTimePasswordIndex(String div) throws Exception {
		String bankLanguage = settingsDAO.getValue("fi.jonix.huutonet.robot.bank.HBBankRobot.bankLanguage");
		if (bankLanguage.equals("FI")) { //Finnish
			String begin = "kirjaus antamalla avaintunnusta";
			String password = div.substring(div.indexOf(begin) + begin.length());
			StringTokenizer st = new StringTokenizer(password);
			return st.nextToken().trim();
		} else if (bankLanguage.equals("SE")) { //Swedish
			String begin = "som motsvarar nyckelkod";
			String password = div.substring(div.indexOf(begin) + begin.length());
			StringTokenizer st = new StringTokenizer(password);
			return st.nextToken().trim();
		} else {
			throw new Exception("Unsupported bank language.");
		}
	}

	private Transaction getTransaction(int i) throws Exception {
		Transaction transaction = new Transaction();
		CustomSelenium selenium = seleniumRobot.getSelenium();
		if (!selenium.isElementPresent("//div[@id='ValilehdetSisalto']/../table[3]//tr[contains(@class,'TaulukonTausta')][" + i + "]/td[1]")) {
			throw new Exception("List ended here.");
		}
		String dateString = selenium
				.getText("//div[@id='ValilehdetSisalto']/../table[3]//tr[contains(@class,'TaulukonTausta')][" + i + "]/td[1]");
		try {
			transaction.setDate(this.dateFormat.parse(dateString.trim()));
		} catch (Exception e) {
		}
		transaction.setPayer(selenium
						.getText("//div[@id='ValilehdetSisalto']/../table[3]//tr[contains(@class,'TaulukonTausta')][" + i + "]/td[2]/a"));
		logger.debug("payer = " + transaction.getPayer());
		transaction.setDescription(selenium
						.getText("//div[@id='ValilehdetSisalto']/../table[3]//tr[contains(@class,'TaulukonTausta')][" + i + "]/td[3]/a"));
		transaction.setMessage(selenium
						.getText("//div[@id='ValilehdetSisalto']/../table[3]//tr[contains(@class,'TaulukonTausta')][" + i + "]/td[4]"));
		String amountString = selenium
				.getText("//div[@id='ValilehdetSisalto']/../table[3]//tr[contains(@class,'TaulukonTausta')][" + i + "]/td[5]");
		transaction.setAmount(new BigDecimal(amountString.replaceAll("\\.","").replaceAll(",", ".")));
		
		/* _Always_ remove spaces from the reference number. */
		String refNumber = selenium
			.getText("//div[@id='ValilehdetSisalto']/../table[3]//tr[contains(@class,'TaulukonTausta')][" + i + "]/td[4]");
		if (refNumber != null) {
			refNumber = refNumber.replaceAll(" ","");
		}
		transaction.setReferenceNumber(refNumber);
		//TODO: maybe we should here do a check to make sure that this transaction
		//is actually done using euros - what would the transaction look like if
		//the payment was made in for example dollars? I could maybe test this
		//by paying one dollar from my personal bank account to tomis account (NOT
		//to SellStar's account), or else I could call the bank and ask about this
		//- what happens if you pay one dollar to a normal account? Is it automatically
		//converted to euro (i.e. is the currency an account specific property or a
		//transaction specific property) or does something else happen)?
		transaction.setCurrency(currencyDAO.get(Currency.EURO));
		transaction.setOldVerification(false);
		transaction.setTransferedToVerification(false);

		return transaction;
	}

	@Override
	public void confirmTransactions(String user, String password, Map<String,String> bankCodes) throws Exception {
		CustomSelenium selenium = seleniumRobot.getSelenium();
		//selenium.open(BASE_URL);
		selenium.click("//ul[@id='PalvelutOtsikkoLista']/li[2]/a"); //Click on Payment sheet
		Thread.sleep(2*1000);
		selenium.click("//*[@id='list-menu-ip']/ul/li[8]/a"); //Click on Confirmations
		Thread.sleep(2*1000);
		if (selenium.getBodyText().indexOf("Sinulla on vahvistamattomia maksuja 0 kpl.") >= 0) {
			throw new BankException(NO_CONFIRMABLE_TRANSACTIONS);
		}
		String codeText = selenium.getText("//form[@name='vahvistaminenForm']/table[2]/tbody/tr[1]/td[1]");
		logger.debug("codeText = " + codeText);
		String start = "antamalla avaintunnusta ";
		String end = " vastaava turvaluku";
		
		String codeKey = codeText.substring(codeText.indexOf(start)+start.length(), codeText.indexOf(end));
		logger.debug("codeKey = " + codeKey);
		/*Integer index = Integer.parseInt(codeKey);
		if (index.intValue() == 0) {
			throw new BankException(OUT_OF_BANKCODES);
		}*/
		
		String code = bankCodes.get(codeKey);
		selenium.type("//input[@name='turvaLuku']", Encryption.decrypt(code));
		selenium.click("//input[@type='submit' and contains(@name,'vahvistaMaksut')]");
		selenium.waitForPageToLoad();
		
		if (selenium.isElementPresent("//form[@name='vahvistaminenForm']/table[2]/tbody/tr[1]/td[1]")) {
			if (selenium.getText("//form[@name='vahvistaminenForm']/table[2]/tbody/tr[1]/td[1]") != null) {
				throw new BankException(CONFIRMATION_FAILED);
			}
		}
	}
	
	@Override
	public void makeTransaction(String message, VirtualCoin sum, String receiverName, String accountNumber) throws Exception {
		if (accountNumber == null || accountNumber.trim().equals("")) {
			logger.error("Account number cannot be empty.");
			throw new BankException(HBBankRobot.FAULTY_ACCOUNT_NUMBER);
		}
		if (!sum.getCurrency().equals(Currency.EURO)) {
			throw new Exception(FAULTY_CURRENCY);
		}
		CustomSelenium selenium = seleniumRobot.getSelenium();
		//selenium.open(BASE_URL);
		selenium.click("//ul[@id='PalvelutOtsikkoLista']/li[2]/a");
		selenium.waitForPageToLoad();
		selenium.type("//table[contains(@class,'TaustaEka')]/tbody/tr[1]/td[2]/input", accountNumber);
		selenium.click("//input[@type='submit' and contains(@value,'Jatka')]");
		selenium.waitForPageToLoad();
		if ((selenium.getBodyText().indexOf("Tilinumero on virheellinen. Ole h") >= 0
				&& selenium.getBodyText().indexOf("ja tarkista tilinumero.") >= 0)
				|| (selenium.getBodyText().indexOf("Tilinumeron on oltava v") >= 0
						&& selenium.getBodyText().indexOf("n 8 merkk") >= 0)
						|| selenium.getBodyText().indexOf("Tilinumero on pakollinen") >= 0) {
			logger.debug("bodyText = \"" + selenium.getBodyText() + "\"");
			logger.debug("htmlSource = \"" + selenium.getHtmlSource() + "\"");
			throw new BankException(HBBankRobot.FAULTY_ACCOUNT_NUMBER);
		}
		selenium.type("//input[@name='saajanNimi']", receiverName);
		String sumStr = MathUtilities.roundTo2Decimals(sum.getValue()).replace(".", ",");
		selenium.type("//input[@name='rahaMaara']", sumStr);
		selenium.type("//textarea[@name='viesti']", message);
		selenium.click("//input[@type='submit' and contains(@value,'Hyväksy maksu')]");
		selenium.waitForPageToLoad();
		//selenium.click("//input[@type='submit' and contains(@name,'vahvistaminen')]");
		//selenium.waitForPageToLoad();
	}

	@Override
	public void login(String user, String password, Map<String,String> bankCodes) throws Exception {
		logger.debug("Logging in to the bank.");
		CustomSelenium selenium = seleniumRobot.getSelenium();
		selenium.open(BASE_URL);
		selenium.type("//input[@id='text2' and @name='j_username']", user);
		selenium.type("//input[@id='text1' and @name='j_password']", Encryption.decrypt(password));
		selenium.click("//input[@type='submit']");
		selenium.waitForPageToLoad();
		String logInDiv = selenium.getText("//div[@id='Sisaankirjaus']/div");
		logger.debug("logInDiv = " + logInDiv);
		String oneTimePasswordKey = this.getOneTimePasswordIndex(logInDiv);
		logger.debug("oneTimePasswordKey = \"" + oneTimePasswordKey + "\"");
		//String oneTimePassword = bankCodes.get(Integer.parseInt(oneTimePasswordKey)-1);
		String oneTimePassword = bankCodes.get(oneTimePasswordKey);
		selenium.type("//input[@name='turvaluku']", Encryption.decrypt(oneTimePassword));
		selenium.click("//input[@type='submit' and @name='vahvista']");
		selenium.waitForPageToLoad();
		/* If the Payment sheet isn't present, then the login failed. */
		if (!selenium.isElementPresent("//ul[@id='PalvelutOtsikkoLista']/li[2]/a")) {
			throw new BankException(OUT_OF_BANKCODES);
		}
		loggedIn = true;
	}

	@Override
	public void logout() {
		logger.debug("Logging out from the bank.");
		CustomSelenium selenium = seleniumRobot.getSelenium();
		//selenium.open(BASE_URL);
		selenium.click("//td[@class='KirjauduUlos']/a");
		loggedIn = false;
		
	}

	@Override
	public boolean isLoggedIn() {
		return loggedIn;
	}

}
