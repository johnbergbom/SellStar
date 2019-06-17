package fi.jonix.huutonet.market.mikko;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import fi.jonix.huutonet.domain.model.Category;
import fi.jonix.huutonet.domain.model.SellerMarket;
import fi.jonix.huutonet.exception.LoggedOutFromMarketException;
import fi.jonix.huutonet.exception.MarketLoggedOnAsWrongUserException;
import fi.jonix.huutonet.robot.CustomSelenium;
import fi.jonix.huutonet.robot.SeleniumRobot;

@Component(value = "mikkoCategoryHelper")
public class MikkoCategoryHelper {

	public static final Logger logger = Logger.getLogger(MikkoCategoryHelper.class);
	
	@Autowired
	private SeleniumRobot seleniumRobot;

	public List<Category> getCategories(SellerMarket sellerMarket) throws LoggedOutFromMarketException, MarketLoggedOnAsWrongUserException {
		//StringBuffer strBuf = new StringBuffer();
		List<Category> categories = new ArrayList<Category>();
		CustomSelenium selenium = seleniumRobot.getSelenium();
		selenium.open("http://www.mikko.fi/announcements/management/add-unclassified.action");
		selenium.waitForPageToLoad();

		/* Make sure that we aren't logged out. */
		String bodyText = selenium.getBodyText();
		if (selenium.isElementPresent("//a[@id='loginLink']") || bodyText.indexOf("vanhenemaan ja sinun on kirjauduttava uudestaan") > 0) {
			throw new LoggedOutFromMarketException();
		}
		
		/* Make sure that we are logged on as the correct user. */
		if (bodyText.indexOf(sellerMarket.getSeller().getSystemEmail().getEmailAddress()) < 0) {
			logger.debug("bodyText = \"" + bodyText + "\"");
			logger.debug("htmlSource = \"" + selenium.getHtmlSource() + "\"");
			throw new MarketLoggedOnAsWrongUserException("Logged on to Mikko as the wrong user (we SHOULD have been logged on as " + sellerMarket.getUserName() + ")");
		}
		
		selenium.select("//select[@id='tradeType']", "value=" + MikkofiLister.SALES_TYPE_AUCTION);
		try { Thread.sleep(4000); } catch (Exception e) {} //wait for javascript
		
		String[] mainOptions = selenium.getSelectOptions("//select[@id='mainCategoryId']");
		//strBuf.append("cd /Mikko\n");
		try {
			int nbrMainCategories = 0;
			for (String mainOption : mainOptions) {
				nbrMainCategories++;
				try { Thread.sleep(1000); } catch (Exception e) {}
				selenium.select("//select[@id='mainCategoryId']", mainOption);
				this.skipConfirmation(selenium);
				String value = selenium.getSelectedValue("//select[@id='mainCategoryId']");
				if (value != null && value.trim().length() > 0) {
					logger.debug("Main category number " + nbrMainCategories);
					Category mainCategory = new Category();
					mainCategory.setMarketSpecId(Integer.parseInt(value.trim()));
					mainCategory.setName(mainOption);
					if (!mainOption.equals("Suomalainen käsityö")) {
						//strBuf.append("mkdir -" + value + " " + mainOption + "\n");
						//strBuf.append("cd " + mainOption + "\n");
						String[] sub1CategoryOptions = selenium.getSelectOptions("//select[@id='category1Id']");
						for (String sub1CategoryOption : sub1CategoryOptions) {
							try { Thread.sleep(1000); } catch (Exception e) {}
							selenium.select("//select[@id='category1Id']", sub1CategoryOption);
							this.skipConfirmation(selenium);
							String sub1Value = selenium.getSelectedValue("//select[@id='category1Id']");
							if (sub1Value != null && sub1Value.trim().length() > 0) {
								Category sub1Category = new Category();
								sub1Category.setParentCategory(mainCategory);
								sub1Category.setMarketSpecId(Integer.parseInt(sub1Value.trim()));
								sub1Category.setName(sub1CategoryOption);
								//strBuf.append("mkdir -" + sub1Value + " " + sub1CategoryOption + "\n");
								//strBuf.append("cd " + sub1CategoryOption + "\n");
								if (selenium.isElementPresent("//select[@id='category2Id']")) {
									String[] sub2CategoryOptions = selenium.getSelectOptions("//select[@id='category2Id']");
									for (String sub2CategoryOption : sub2CategoryOptions) {
										try { Thread.sleep(1000); } catch (Exception e) {}
										selenium.select("//select[@id='category2Id']", sub2CategoryOption);
										this.skipConfirmation(selenium);
										String sub2Value = selenium.getSelectedValue("//select[@id='category2Id']");
										if(sub2Value != null && sub2Value.trim().length() > 0){
											Category sub2Category = new Category();
											sub2Category.setParentCategory(sub1Category);
											sub2Category.setMarketSpecId(Integer.parseInt(sub2Value.trim()));
											sub2Category.setName(sub2CategoryOption);
											//strBuf.append("mkdir -" + sub2Value + " " + sub2CategoryOption + "\n");
											categories.add(sub2Category);
										}
									}
								} else {
									categories.add(sub1Category);
								}
								//strBuf.append("cd ..\n");
							}
						}
						//strBuf.append("cd ..\n");
					}
				}
			}
			System.out.println("Done fetching categories for Mikko.");
			//System.out.println(strBuf.toString());
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
		return categories;
	}
	
	private void skipConfirmation(CustomSelenium selenium) {
		try {
			selenium.chooseOkOnNextConfirmation();
			Thread.sleep(7000);
			if (selenium.isConfirmationPresent()) {
				selenium.getConfirmation();
			}
		} catch (Exception e){}
	}
	
}
