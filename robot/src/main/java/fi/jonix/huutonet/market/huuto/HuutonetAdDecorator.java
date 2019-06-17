package fi.jonix.huutonet.market.huuto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import fi.jonix.huutonet.domain.model.Ad;
import fi.jonix.huutonet.domain.model.AdTemplate;
import fi.jonix.huutonet.domain.model.Image;
import fi.jonix.huutonet.domain.model.Seller;
import fi.jonix.huutonet.domain.model.SellerMarket;
import fi.jonix.huutonet.market.MarketAdDecorator;
import fi.jonix.huutonet.tools.StringUtilities;

public class HuutonetAdDecorator extends MarketAdDecorator {

	//private static String HEADLINE_COLOR = "ff9900";
	private static String HEADLINE_COLOR = "88bbff";

	/**
	 * @param useImageHost
	 *            If true, then use the url's of the image host for generating
	 *            the html image tags, otherwise use the paths of the locally
	 *            stored images.
	 */
	@Override
	public String getFormatedBody(Ad ad, SellerMarket sellerMarket, boolean useImageHost,
			TreeMap<Long, List<Long>> preFilledScoreMap) {
		if (ad.getStartPriceInMarketCurrencyInclVat().compareTo(BigDecimal.ZERO) <= 0) {
			throw new RuntimeException("Start price too low for ad " + ad.getId());
		}
		String body = "";
		body += "<tr><td>" + this.getHeadline(ad.getAdTemplate().getHeadline()) + "</td></tr>";
		body += "<tr><td>" + this.getSummary(ad.getAdTemplate().getSummary()) + "</td></tr>";
		body += "<tr><td>" + this.getTechicalSpecs2(ad.getAdTemplate()) + "</td></tr>";
		body += "<tr><td>" + "<br/>" + this.getDetails(ad.getAdTemplate().getDetails(),
				ad.getAdTemplate().getProduct().getDimensions()) + "</td></tr>";
		body += "<tr><td>" + this.getWebStoreLink(ad) + "</td></tr>";
		body += "<tr><td>" + this.formatImages(ad.getAdTemplate().getProduct().getImagesInOrder(), useImageHost) + "</td></tr>";
		body += "<tr><td>" + this.getSlogan(ad.getAdTemplate().getSlogan()) + "</td></tr>";
		
		/* For links to other products make sure that each adTemplate is only
		 * added once + make sure that no sibling products are added that refer to oneself. */
		List<AdTemplate> addedAdTemplates = new ArrayList<AdTemplate>();
		addedAdTemplates.add(ad.getAdTemplate());

		body += "<tr><td>" + formatLinksToOtherAds(getLinksToOtherAds(ad,addedAdTemplates), useImageHost) + "</td></tr>";
		body += "<tr><td>" + formatBestSellerAds(ad, useImageHost, preFilledScoreMap, addedAdTemplates) + "</td></tr>";
		body += this.getStartTable("Muut tiedot");
		return StringUtilities.escapeString2HTML(body);
	}

	private String formatLinksToOtherAds(List<Ad> adList, boolean useImageHost) {
		StringBuffer strBuf = new StringBuffer();
		strBuf.append("<br/><table text-align=\"center\" width='100%' cellspacing='1' cellpadding='5' border='outset' bgcolor='WHITE'>");
		strBuf.append("<tbody><tr bgcolor='#" + HEADLINE_COLOR + "'><th colspan='5'>MUITA VASTAAVIA TUOTTEITA</th>");
		strBuf.append("<tr><th colspan='5'>Kohteessa on linkki sivustolle, josta tuotteen voi tilata kohteen sulkeuduttua</th></tr>");
		strBuf.append("<tr>");
		for (Ad linkToAd : adList) {
			//logger.debug("creating link1");
			strBuf.append(this.formatLinkToAd(linkToAd, useImageHost));
		}
		strBuf.append("</tr></tbody></table>");
		return strBuf.toString();
	}

	private String formatLinkToAd(Ad ad, boolean useImageHost) {
		String linkPart = "<td><table><tr><td align='center'><A HREF='" + getBounceToMarketURL(ad) + "' TARGET='#'>";
		List<Image> images = ad.getAdTemplate().getProduct().getImagesInOrder();
		if (images == null || images.isEmpty()) {
			return "";
		}
		Image bestImage = images.get(0);
		if (images.get(0).getThumbnailImageId() != null) {
			bestImage = imageDAO.get(images.get(0).getThumbnailImageId());
		}
		linkPart += "<IMG WIDTH='130' SRC='" + (useImageHost ? bestImage.getImageHostUrl() : bestImage.getLocalFileUrl())
				+ "' alt='ImageHost.org' style='width: 140px;' />";
		linkPart += "<br/>";
		linkPart += StringUtilities.normalizer(ad.getAdTemplate().getHeadline());
		/*linkPart += "<br/>";
		linkPart += SHORT_DATE_FORMAT.format(ad.getDateClosing());*/
		linkPart += "<br/>";
		linkPart += "<br/>";
		linkPart += "<font face='Arial' color='CC0066' style='font-size: 11pt;'>" + currencyFormatter.format(ad.getStartPriceInMarketCurrencyInclVat())
				+ "</font></b>";
		linkPart += "</A></td></tr></table></td>";
		return linkPart;
	}

	private String formatBestSellerAds(Ad ad, boolean useImageHost, TreeMap<Long, List<Long>> preFilledScoreMap, List<AdTemplate> addedAdTemplates) {
		//logger.debug("getBestSellerAds");
		String linksPart = "<br/><table text-align=\"center\" width='100%' cellspacing='1' cellpadding='5' border='outset'"
			+ " bgcolor='WHITE'><tbody><tr bgcolor='#" + HEADLINE_COLOR + "'><th colspan='5'>MUITA VARTEENOTETTAVIA TUOTTEITA</th>";
		linksPart += "<tr><th colspan='5'>Kohteessa on linkki sivustolle, josta tuotteen voi tilata kohteen sulkeuduttua</th></tr>";

		//List<Ad> bestSellingAds = getBestSellingsAds(8,ad.getMarket(),ad.getAdTemplate().getSeller());
		List<Ad> bestSellingAds = getBestSellingsAds(12, preFilledScoreMap, true, addedAdTemplates);

		linksPart += "<tr>";
		int adsInRow = 0;
		for (Ad linkToAd : bestSellingAds) {
			//logger.debug("creating best seller link");
			linksPart += this.formatLinkToAd(linkToAd, useImageHost);
			adsInRow++;
			if (adsInRow > 3) {
				adsInRow = 0;
				linksPart += "</tr><tr>";
			}
		}
		linksPart += "</tr></tbody></table>";
		return linksPart;
	}

	private String getStartTable(String header) {
		return "</div></td></tr></tbody></table>";
	}
	
	private String getHeadline(String headline) {
		if (headline == null || headline.length() == 0) {
			return "";
		}
		return "<div><span style='font-size: 1.5em;'>" + StringUtilities.normalizer(headline) + "</span></div>";
	}

	private String getSummary(String summary) {
		if (summary == null || summary.length() == 0) {
			return "";
		}
		String summaryPart = new String();
		summaryPart += "<div>";
		if (summary != null) {
			summaryPart += StringUtilities.normalizer(summary.replaceAll("\n", "<br/>"));
		}
		summaryPart += "</div>";
		return summaryPart;
	}

	private String getTechicalSpecs2(AdTemplate adTemplate) {
		String specs = adTemplate.getTechnicalSpecs();
		if (specs == null) {
			specs = adTemplate.getProduct().getTechnicalSpecs();
		}
		if (specs == null || specs.length() == 0)
			return "";
		String specsPart = new String();
		String[] lines = specs.split("\n");

		int length = 0;

		for (String line : lines) {
			if (line.length() > length)
				length = line.length();
		}

		specsPart += "<br/>";
		specsPart += "<table cellspacing=\"0\" cellpadding=\"0\" border=\"3\">";
		specsPart += "<tbody><tr><td>";
		specsPart += "<table cellspacing=\"0\" cellpadding=\"3\" border=\"0\">";
		specsPart += "<tbody>";
		specsPart += "";
		specsPart += "<tr><th bgcolor=\"#ffffcc\"><font face=\"Courier New Bold\">SPEKSIT</font></th></tr>";

		// Draw the specification lines
		for (String line : lines) {
			specsPart += "<tr><td bgcolor=\"#ffffcc\"><font face='Courier New'>" + " - " + StringUtilities.normalizer(line) + "</font></td></tr>";
		}

		specsPart += "</tbody></table>";
		specsPart += "</td></tr></tbody></table>";
		specsPart += "<br/>";

		return specsPart;
	}

	private String formatImages(List<Image> images, boolean useImageHost) {
		// Check first if any images are available
		if (images == null || images.isEmpty()) {
			return "";
		}
		boolean imagesFound = false;

		for (Image image : images) {
			if (image.getImageHostUrl() != null && image.getImageHostUrl().length() > 2) {
				imagesFound = true;
				break;
			}
		}

		if (!imagesFound) {
			return "";
		}

		String imagePart = "<br/><table text-align='center' width='100%' cellspacing='1' cellpadding='5' border='outset' bgcolor='WHITE'><tbody><tr bgcolor='#" + HEADLINE_COLOR + "'><th colspan='5'>KUVIA TUOTTEESTA</th>";
		imagePart += "<tr><th colspan='5'>Klikkaamalla kuvia saat ne suurempina</th></tr>";
		imagePart += "<tr>";
		if (images != null) {
			int imagesAtRow = 0;
			for (int i = 1; i < images.size(); i++) {
				Image image = images.get(i);

				boolean foundDuplicate = false;
				for (Image testImage : images) {
					if (testImage.getImageOrder().equals(image.getImageOrder())) {
						if (testImage.getThumbnailImageId() != null && !testImage.getId().equals(image.getId()))
							foundDuplicate = true;
					}
				}

				if (foundDuplicate)
					continue;

				if (imagesAtRow > 3) {
					imagePart += "</tr><tr>";
					imagesAtRow = 0;
				}

				imagePart += "<td align='center'><div style='text-align:center;'>";

				Image thumbnail = null;

				if (image.getThumbnailImageId() != null)
					thumbnail = this.imageDAO.get(image.getThumbnailImageId());
				if (thumbnail == null) {
					imagePart += "<A HREF='" + (useImageHost ? image.getImageHostUrl() : image.getLocalFileUrl())
							+ "' TARGET='_blank'><IMG WIDTH='130' SRC='" + (useImageHost ? image.getImageHostUrl() : image.getLocalFileUrl())
							+ "'></A>";
				} else {
					imagePart += "<A HREF='" + (useImageHost ? image.getImageHostUrl() : image.getLocalFileUrl())
							+ "' TARGET='_blank'><IMG WIDTH='130' SRC='" + (useImageHost ? thumbnail.getImageHostUrl() : thumbnail.getLocalFileUrl())
							+ "'></A>";
				}

				imagesAtRow++;
				imagePart += "<br/>";
				if (image.getImageText() != null && image.getImageText().length() > 0) {
					imagePart += StringUtilities.normalizer(image.getImageText());
				}
				imagePart += "</td></div></td>";
			}
		}
		imagePart += "</tr></tbody></table>";
		return imagePart;
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
		String detailsPart = new String();
		detailsPart += "<div>";
		detailsPart += StringUtilities.normalizer(details.replaceAll("\n", "<br/>"));
		detailsPart += "</div>";
		return detailsPart;
	}

	private String getSlogan(String slogan) {
		if (slogan == null || slogan.length() == 0) {
			return "";
		}
		String sloganPart = new String();
		sloganPart += "<br/>";
		sloganPart += "<div style='text-align:center;'>";
		sloganPart += "<font face='Garamond' color='CC0066' style='font-size: 13pt;'>";
		sloganPart += "<b>";
		sloganPart += StringUtilities.normalizer(slogan.replaceAll("\n", "<br/>"));
		sloganPart += "</b>";
		sloganPart += "</font>";
		sloganPart += "</div>";
		return sloganPart;
	}

	private String getWebStoreLink(Ad ad) {
		String webStorePart = new String();
		String linkText = "Tilaa tuote suoraan verkkokaupastamme";
		webStorePart += "<A HREF='" + getWebStorePlainURL(ad) + "' TARGET='_blank'>";
		webStorePart += "<font face='Garamond' color='BLUE' style='font-size: 13pt;'>";
		webStorePart += linkText.replaceAll("\n", "<br/>");
		webStorePart += "</font>";
		webStorePart += "</A> (Voi tilata myös kohteen sulkeuduttua)";
		return webStorePart;
	}

	@Override
	public String getBeanVersion() {
		return "1.0";
	}

}
