package fi.jonix.huutonet.market.mikko;

import java.util.List;
import java.util.TreeMap;

import fi.jonix.huutonet.domain.model.Ad;
import fi.jonix.huutonet.domain.model.Seller;
import fi.jonix.huutonet.domain.model.SellerMarket;
import fi.jonix.huutonet.market.MarketAdDecorator;
import fi.jonix.huutonet.tools.StringUtilities;

public class MikkoAdDecorator extends MarketAdDecorator {

	@Override
	public String getBeanVersion() {
		return "1.0";
	}

	@Override
	public String getFormatedBody(Ad ad, SellerMarket sellerMarket, boolean useImageHost,
			TreeMap<Long, List<Long>> preFilledScoreMap) {
		String techSpecs = ad.getAdTemplate().getTechnicalSpecs();
		if (techSpecs == null) {
			techSpecs = ad.getAdTemplate().getProduct().getTechnicalSpecs();
		}
		if (techSpecs.equals("")) {
			techSpecs = ad.getAdTemplate().getHeadline(); //mikko doesn't allow the description to be empty so we need to set something here!
		}
		techSpecs = "SPEKSIT:\n" + techSpecs;
		techSpecs += getDetails(ad.getAdTemplate().getDetails(),
				ad.getAdTemplate().getProduct().getDimensions());
		techSpecs += "\n\n-------------------------\nVoit myös tilata tuotteen suoraan verkkokaupastamme: ";
		//techSpecs += ad.getAdTemplate().getSeller().getStoreUrl();
		techSpecs += getWebStorePlainURL(ad);
		techSpecs += "\n\nToimitusehdot löytyvät täältä: " + ad.getAdTemplate().getSeller().getStoreUrl()
			+ "/webstore/sellstar/seller/deliveryInfo";
		techSpecs += "\n\nKatso myös linkit alla!";
		return StringUtilities.normalizer(techSpecs);
	}

	private String getDetails(String details, String dimensions) {
		if (dimensions != null) {
			if (details == null || details.length() == 0) {
				details = dimensions;
			} else {
				details += "\n" + dimensions;
			}
		}
		if (details == null || details.length() == 0)
			return "";
		return "\n\n" + details;
	}

}
