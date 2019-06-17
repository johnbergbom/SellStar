package fi.jonix.huutonet.tools;

import java.util.List;

import org.apache.log4j.Logger;

import com.thoughtworks.selenium.SeleniumException;

import fi.jonix.huutonet.domain.model.Image;
import fi.jonix.huutonet.domain.model.Product;
import fi.jonix.huutonet.domain.model.dao.ProductDAO;
import fi.jonix.huutonet.robot.image.ImageUploader;

public class ImageHelper {

	public static final Logger logger = Logger.getLogger(ImageHelper.class);

	private ProductDAO productDAO;
	private List<ImageUploader> usedImageUploaders;
	private ImageUploader imageUploader;

	public boolean checkProductImages(Product product) throws Exception {
		productDAO.refresh(product);
		boolean success = true;
		logger.debug("Checking product images for product " + product.getProviderProdId());
		for (Image image : product.getImages()) {
			try {
				if (image != null) {
					boolean found = false;
					for (ImageUploader uploader : usedImageUploaders) {
						if (uploader.isImageAvailable(image)) {
							found = true;
							break;
						}
					}
					if (!found) {
						success = imageUploader.uploadImage(image);
					}
				}
			} catch (SeleniumException e) {
				if (e.getMessage() != null && e.getMessage().startsWith("Timed out after")) {
					success = false;
				} else {
					throw e;
				}
			} catch (Exception e) {
				if (e.getMessage() != null && e.getMessage().startsWith("Image uploading failed")) {
					logger.error(e.getMessage());
				} else {
					throw e;
				}
			}
		}
		logger.debug("Done checking product images for product " + product.getProviderProdId());
		return success;
	}

	public void setProductDAO(ProductDAO productDAO) {
		this.productDAO = productDAO;
	}

	public void setUsedImageUploaders(List<ImageUploader> usedImageUploaders) {
		this.usedImageUploaders = usedImageUploaders;
	}

	public void setImageUploader(ImageUploader imageUploader) {
		this.imageUploader = imageUploader;
	}

}
