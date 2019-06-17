package fi.jonix.huutonet.commandrow.option;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;

import fi.jonix.huutonet.domain.VirtualCoin;
import fi.jonix.huutonet.domain.model.AdTemplate;
import fi.jonix.huutonet.domain.model.Currency;
import fi.jonix.huutonet.domain.model.Product;
import fi.jonix.huutonet.domain.model.Provider;
import fi.jonix.huutonet.domain.model.dao.AdTemplateDAO;
import fi.jonix.huutonet.domain.model.dao.CurrencyDAO;
import fi.jonix.huutonet.domain.model.dao.ProductDAO;
import fi.jonix.huutonet.domain.model.dao.ProviderDAO;
import fi.jonix.huutonet.order.ProviderHandler;
import fi.jonix.huutonet.order.ProviderOrder;
import fi.jonix.huutonet.order.focalprice.FocalPriceProvider;

public class GetItemCommandHandler extends CommandHandler {

	public static final Logger logger = Logger.getLogger(GetItemCommandHandler.class);

	@Autowired
	ProductDAO productDAO;

	@Autowired
	ProviderHandler providerHandler;
	
	@Autowired
	ProviderDAO providerDAO;
	
	@Autowired
	CurrencyDAO currencyDAO;
	
	@Autowired
	AdTemplateDAO adTemplateDAO;
	
	@Override
	protected void initializeOptionList() {
		this.options.add(new Option(true, "provider", "Provider of the products", "Please enter provider name", /*Provider.DEAL_EXTREME_PROVIDER_NAME, */Provider.FOCAL_PRICE_PROVIDER_NAME/*, Provider.HAUTALA_PROVIDER_NAME*/));
		this.options.add(new Option(false, "products", "Product ids separated with comma like sku1,sku2,sku3... ",
				"Please enter product ids"));
		this.options.add(new Option(false, "count", "Count of products for automatic getter ", "Please enter product count of products"));
		this.options.add(new Option(false, "percentOfProducts", "Max percent of products in the same category ", "Please enter max percent of product in the same category"));
	}

	@Override
	protected void handleCommand() {
		Provider provider = providerDAO.getProviderByName(this.getValue("provider"));

		List<String> productIds = new ArrayList<String>();

		String productIdsString = this.getValue("products");
		if (productIdsString != null && productIdsString.length() > 0) {
			logger.info("Fetching fixed products with their ids");
			String[] productIdArray = this.getValue("products").split(",");
			for (String productId : productIdArray) {
				productIds.add(productId);
			}
		} else {
			String countString = this.getValue("count");
			logger.info("Fetching randomly " + countString + " products");
			if (countString != null && countString.length() > 0) {
				try {
					BigDecimal percentOfProducts = null;
					if(this.getValue("percentOfProducts") != null){
						percentOfProducts = new BigDecimal(this.getValue("percentOfProducts"));
					}
					if (/*provider.getCurrency().getId().equals(Currency.EURO) && */provider.getVatPercentage().compareTo(BigDecimal.ZERO) > 0) {
						productIds = providerHandler.getGoodProductIds(provider,Integer.parseInt(countString),null,null,percentOfProducts);
					} else {
						//TODO: take the value of the maxPriceExclVat parameter to getGoodProductIds from market.maxBundleSize
						productIds = providerHandler.getGoodProductIds(provider,Integer.parseInt(countString),
								new VirtualCoin(Currency.EURO,new BigDecimal("0.50")),
								new VirtualCoin(Currency.EURO,new BigDecimal("41.0")), percentOfProducts);
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		
		logger.info("Start creating " + productIds.size() + " products");
		int count = 0;
		for (String productId : productIds) {
			count++;
			try {
				logger.info("Create product " + productId + " (" + count + " of " + productIds.size() + ")");
				Product product = providerHandler.createProduct(provider,productId);
				if (product != null) {
					product = productDAO.save(product);
					productDAO.flush(); // do a flush in order to catch database errors right away
					logger.error("Getitems saved product " + productId + " (id = " + product.getId() + ")");
					AdTemplate adTemplate = providerHandler.createAutoTemplate(product);
					if (adTemplate != null) {
						adTemplateDAO.save(adTemplate);
						adTemplateDAO.flush(); // do a flush in order to catch database errors right away
					}
				}
			} catch (Exception e) {
				logger.error("Getitems failed for product " + productId + ": ",e);
			}
		}
	}

}
