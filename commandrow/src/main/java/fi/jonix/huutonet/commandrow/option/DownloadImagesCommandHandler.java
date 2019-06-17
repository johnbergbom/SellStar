package fi.jonix.huutonet.commandrow.option;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import fi.jonix.huutonet.domain.model.Image;
import fi.jonix.huutonet.domain.model.Product;
import fi.jonix.huutonet.domain.model.Provider;
import fi.jonix.huutonet.domain.model.dao.ImageDAO;
import fi.jonix.huutonet.domain.model.dao.ProductDAO;
import fi.jonix.huutonet.domain.model.dao.ProviderDAO;
import fi.jonix.huutonet.domain.model.dao.SettingsDAO;
import fi.jonix.huutonet.order.ProviderHandler;
import fi.jonix.huutonet.order.ProviderOrder;

@Component(value = "downloadImagesCommandHandler")
public class DownloadImagesCommandHandler extends CommandHandler{
	
	public static final Logger logger = Logger.getLogger(DownloadImagesCommandHandler.class);
	
	@Autowired
	ProductDAO productDAO;
	
	@Autowired
	ProviderDAO providerDAO;
	
	@Autowired
	SettingsDAO settingsDAO;
	
	@Autowired
	ImageDAO imageDAO;
	
	@Autowired
	ProviderHandler providerHandler;
	
	@Override
	protected void initializeOptionList() {
		this.options.add(new Option(true, "provider", "Provider of the products", "Enter provider name", /*Provider.DEAL_EXTREME_PROVIDER_NAME, */Provider.FOCAL_PRICE_PROVIDER_NAME/*, Provider.HAUTALA_PROVIDER_NAME*/));
		this.options.add(new Option(false, "products", "Product ids separated with comma like sku1,sku2,sku3... ",
				"Please enter product ids"));
	}
	
	@Override
	protected void handleCommand() {
		logger.info("Downloading images of products");
		Provider provider = this.providerDAO.getProviderByName(this.getValue("provider"));
		ProviderOrder providerOrder = providerHandler.getProviderOrder(provider);
		boolean updateOnlyProductsWithoutImages = true;

		List<Product> products = new ArrayList<Product>();

		String productIdsString = this.getValue("products");
		if (productIdsString != null && productIdsString.length() > 0) {
			logger.info("Fetching fixed products with their ids");
			String[] productIdArray = this.getValue("products").split(",");
			for (String productId : productIdArray) {
				products.add(this.productDAO.getByProviderProdId(productId, provider));
			}
		} else if (updateOnlyProductsWithoutImages) {
			products = this.productDAO.getProductsWithoutImages(provider);
		} else {
			products = this.productDAO.getProducts(provider);
		}
		
		logger.info("Updating images of " + products.size() + " products");
		
		String basePathStr = settingsDAO.getValue("base.path");
		File basePath = new File(basePathStr);
		int i = 0;
		for (Product product : products) {
			i++;
			if (imageDAO.getByProduct(product).size() > 0) {
				// Before this is supported we need implement code for removing the old images.
				throw new UnsupportedOperationException("Images already exist for product - update not yet implemented.");
			}
			try {
				logger.info("Downloading images of product " + product.getProviderProdId() + " (" + i + "/" + products.size() + ").");
				providerOrder.downloadImages(product, basePath);
				logger.error("downloadimages saved images for product " + product.getId() + " (" + product.getProviderProdId() + ")");
			} catch (Exception e) {
				logger.error("Downloading of images failed for product " + product.getId() + " (" + product.getProviderProdId() + "): ",e);
			}
		}
		
		logger.info("Images updated");
		
	}

}
