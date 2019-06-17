package fi.jonix.huutonet.tools;

import java.io.InputStreamReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.util.List;

import org.apache.log4j.Logger;

import fi.jonix.huutonet.domain.ApplicationContextPlaceholder;
import fi.jonix.huutonet.domain.VirtualCoin;
import fi.jonix.huutonet.domain.model.Ad;
import fi.jonix.huutonet.domain.model.Category;
import fi.jonix.huutonet.domain.model.Currency;
import fi.jonix.huutonet.domain.model.Market;
import fi.jonix.huutonet.robot.TemplateGenerator;
import fi.jonix.huutonet.market.AdCreator;
import fi.jonix.huutonet.market.ListerStrategy;
import fi.jonix.huutonet.domain.model.AdTemplate;
import fi.jonix.huutonet.domain.model.dao.AdTemplateDAO;
import fi.jonix.huutonet.domain.model.dao.CategoryMappingDAO;
import fi.jonix.huutonet.domain.model.dao.SettingsDAO;

public class TemplateTester {

	public static final Logger logger = Logger.getLogger(TemplateTester.class);

	AdTemplateDAO adTemplateDAO;
	AdCreator adCreator;
	private SettingsDAO settingsDAO;
	private CategoryMappingDAO categoryMappingDAO;
	private ListerStrategy strategy;

	public void setAdTemplateDAO(AdTemplateDAO adTemplateDAO) {
		this.adTemplateDAO = adTemplateDAO;
	}

	public void setAdCreator(AdCreator adCreator) {
		this.adCreator = adCreator;
	}

	public void setSettingsDAO(SettingsDAO settingsDAO) {
		this.settingsDAO = settingsDAO;
	}

	public void setCategoryMappingDAO(CategoryMappingDAO categoryMappingDAO) {
		this.categoryMappingDAO = categoryMappingDAO;
	}

	public void setStrategy(ListerStrategy strategy) {
		this.strategy = strategy;
	}

	/**
	 * Creates a template for the first active ad template that happens to be found.
	 */
	public void createTemplate(Market market) {
		try {
			List<AdTemplate> adTemplates = adTemplateDAO.loadAll();
			for (AdTemplate adTemplate : adTemplates) {
				if (Boolean.FALSE.equals(adTemplate.getActive())) {
					continue;
				}
				createTemplate(adTemplate,market);
				if (Boolean.TRUE.equals(adTemplate.getActive())) {
					break;
				}
			}
		} catch (Exception e) {
			logger.info("template generation failed", e);
		}
	}

	/**
	 * Creates a template for the specified ad template.
	 */
	public void createTemplate(long adTemplateId, Market market) {
		try {
			createTemplate(adTemplateDAO.get(adTemplateId),market);
		} catch (Exception e) {
			logger.info("template generation failed", e);
		}
	}

	private void createTemplate(AdTemplate adTemplate, Market market) throws Exception {
		logger.info("Creating template ad");
		
		/* Figure out the right category to use. */
		Category sellStarCategory = adTemplate.getProduct().getCategory();
		if (sellStarCategory == null) {
			throw new Exception("adtemplate " + adTemplate.getId() + " is associated to product "
					+ adTemplate.getProduct().getId() + " but this product doesn't have any category defined."
					+ " This should never happen (because the product category should always be set when the adtemplate"
					+ " is created).");
		}
		List<Category> marketCategoryList = categoryMappingDAO.getCategoryMappingsForMarket(sellStarCategory, market);
		if (marketCategoryList.isEmpty()) {
			throw new Exception("adtemplate "
					+ adTemplate.getId() + " has product.category_id = "
					+ adTemplate.getProduct().getCategory().getId() + " and this category isn't mapped to ANY"
					+ " category at " + market.getName() + ".");
		}
		Category marketCategory = marketCategoryList.get(0);

		VirtualCoin providerPrice = new VirtualCoin(Currency.DOLLAR,new BigDecimal("12.2"));
		/*Ad ad = adCreator.createAd(adTemplate, marketCategory, providerPrice, strategy, null/*adTemplate.getSeller()/);
		//ad.setTemplateAd(adTemplate.getAd());
		TemplateGenerator templateGenerator = (TemplateGenerator) ApplicationContextPlaceholder.applicationContext.getBean("huutonetAdDecorator");
		ad.setUsedBeanName(ad.getAdTemplate().getBeanName());
		ad.setUsedBeanVersion(templateGenerator.getBeanVersion());
		String description = templateGenerator.getFormatedBody(ad,true);

		String basePath = settingsDAO.getValue("base.path");
		String templateDir = settingsDAO.getValue("fi.jonix.huutonet.tools.TemplateTester.templateDir");
		String textFromFile = this
			.getTextFromFile(new File(new File(basePath,templateDir),"template.txt"));
		logger.info("file size " + textFromFile.length());
		// logger.info("file included: " + textFromFile);

		String textToFile = textFromFile.replace(
				"DESCRIPTION_REPLACEMENT_PART", description);
		this.fileWriter(new File(new File(basePath,templateDir),"showitem.php3.htm"), textToFile);
		*/
		logger.info("Template example ready");
	}

	private String getTextFromFile(File file) throws Exception {
		FileInputStream fis = new FileInputStream(file);
		InputStreamReader in = new InputStreamReader(fis, "UTF-8");
		java.io.BufferedReader br = new java.io.BufferedReader(in);
		StringBuilder contents = new StringBuilder();
		String line = null;
		while ((line = br.readLine()) != null) {
			contents.append(line);
			contents.append(System.getProperty("line.separator"));
		}
		fis.close();
		return contents.toString();
	}

	private void fileWriter(File file, String textToFile) throws Exception {
		FileOutputStream fos = new FileOutputStream(file);
		OutputStreamWriter out = new OutputStreamWriter(fos);
		out.write(textToFile);
	}
}
