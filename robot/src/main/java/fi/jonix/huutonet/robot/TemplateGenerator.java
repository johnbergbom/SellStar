package fi.jonix.huutonet.robot;

import java.util.List;
import java.util.TreeMap;

import fi.jonix.huutonet.domain.model.Ad;
import fi.jonix.huutonet.domain.model.SellerMarket;

public interface TemplateGenerator {
	
	public String getBeanVersion();
	public String getFormatedBody(Ad ad, SellerMarket sellerMarket, boolean useImageHost,
			TreeMap<Long, List<Long>> preFilledScoreMap) throws Exception;
	
}
