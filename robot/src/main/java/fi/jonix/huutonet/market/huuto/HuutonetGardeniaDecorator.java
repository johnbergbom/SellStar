package fi.jonix.huutonet.market.huuto;

import java.awt.Dimension;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import fi.jonix.huutonet.domain.model.Ad;
import fi.jonix.huutonet.domain.model.AdTemplate;
import fi.jonix.huutonet.domain.model.Image;
import fi.jonix.huutonet.domain.model.SellerMarket;
import fi.jonix.huutonet.market.MarketAdDecorator;
import fi.jonix.huutonet.tools.StringUtilities;

//TODO: Fix thumbnails for the images. In some cases the images are very large but still
//they are shown as very small in the ad. The images would load a lot faster if thumbnails
//were used instead.

//TODO: Hautalan tuotteiden pääkuva on aika pieni (muut kuvat ovat paljon isompia). Ev. käy käsin läpi ne Hautalan tuotteet
//joilla on enemmän kuin yksi kuva (niitä ei ole hirveän monta) ja muuta image.image_order niiden kohdalla.
//=>isommat kuvat olivat haettu wolf-gartenin omilta sivuilta, mutta kaikki tuotteet eivät löytyneet sieltä. Ev. voisi
//käydä läpi ne tuotteet joiden product.manufacturer_url = null ja kuvien määrä > 1.

public class HuutonetGardeniaDecorator extends MarketAdDecorator {

	@Override
	public String getFormatedBody(Ad ad, SellerMarket sellerMarket, boolean useImageHost,
			TreeMap<Long, List<Long>> preFilledScoreMap) {
		logger.info("Generating body for Gardenia.");
		StringBuffer body = new StringBuffer();
		body.append("<table id=\"Taulukko_01\" width=\"935\" height=\"1329\" border=\"0\""
				+ " cellpadding=\"0\" cellspacing=\"0\" bgcolor=\"#eeeeee\"><tbody>\n");
		body.append(addBanner());
		body.append(addClosingAdsLink(sellerMarket));
		List<Image> images = ad.getAdTemplate().getProduct().getImagesInOrder();
		
		body.append(addMainImage(images.get(0)));
		int dimensionRows = addDimensions(ad.getAdTemplate(),body);
		body.append(addSameCategoryAdsLink(ad,sellerMarket));
		body.append(addSimilarProductsAdsLink(ad.getAdTemplate(),sellerMarket));
		body.append(addSpaceForDimensions(1,dimensionRows));

		/* For links to other products make sure that each adTemplate is only
		 * added once + make sure that no sibling products are added that refer to oneself. */
		List<AdTemplate> addedAdTemplates = new ArrayList<AdTemplate>();
		addedAdTemplates.add(ad.getAdTemplate());

		int addedOtherProds = addOtherProducts(ad,body,images,addedAdTemplates);
		body.append(addSpaceForDimensions(2,dimensionRows));
		body.append(addSummaryText(ad.getAdTemplate()));
		body.append(addImagesForProductHeadline());
		addImagesForProduct(body,images,addedOtherProds);
		body.append(addInformationAboutUs(ad.getAdTemplate(),sellerMarket));
		body.append(addBottomImage());
		body.append("</tbody></table>\n");
		//logger.debug("body for Gardenia: " + body.toString());
		return StringUtilities.escapeString2HTML(body.toString());
	}

	private String addBanner() {
		return "<tr>\n"
			+ "  <td colspan=\"6\">\n"
			+ "    <img src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/banner1.png\" width=\"218\" height=\"231\" alt=\"\">\n"
			+ "  </td>\n"
			+ "  <td colspan=\"6\">\n"
			+ "    <img src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/banner2.png\" width=\"230\" height=\"231\" alt=\"\">\n"
			+ "  </td>\n"
			+ "  <td colspan=\"4\">\n"
			+ "    <img src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/banner3.png\" width=\"181\" height=\"231\" alt=\"\">\n"
			+ "  </td>\n"
			+ "  <td>\n"
			+ "    <img src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/banner4.png\" width=\"83\" height=\"231\" alt=\"\">\n"
			+ "  </td>\n"
			+ "  <td colspan=\"3\">\n"
			+ "    <img src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/banner5.png\" width=\"222\" height=\"231\" alt=\"\">\n"
			+ "  </td>\n"
			+ "  <td>\n"
			+ "    <img src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/spacer.gif\" width=\"1\" height=\"231\" alt=\"\">\n"
			+ "  </td>\n"
			+ "</tr>\n";
	}

	private String addClosingAdsLink(SellerMarket sellerMarket) {
		return "<tr>\n"
			+ "  <td colspan=\"10\">\n"
			+ "    <img src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/fill_upper_left.png\" width=\"348\" height=\"6\" alt=\"\">\n"
			+ "  </td>\n"
			+ "  <td colspan=\"6\" rowspan=\"2\">\n"
			+ "    <img src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/fill_upper_middle.png\" width=\"281\" height=\"8\" alt=\"\">\n"
			+ "  </td>\n"
			+ "  <td rowspan=\"10\">\n"
			+ "    <img src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/fill_between_cols1.png\" width=\"83\" height=\"226\" alt=\"\">\n"
			+ "  </td>\n"
			+ "  <td colspan=\"2\" rowspan=\"3\">\n"
			+ "    <a href=\"http://www.huuto.net/fi/showlist.php3?odir=a&sellstyle=K&status=N&classification=0&stype=A&tits=&sprice=&eprice="
			+ "&s_ltime=0&e_ctime=0&seller=" + sellerMarket.getUserName() + "&location=&bidder=&flimit=\" target=\"_blank\" rel=\"nofollow\">\n"
			+ "      <img src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/text_closing_ads.png\" width=\"165\" height=\"28\" alt=\"\">\n"
			+ "    </a>\n"
			+ "  </td>\n"
			+ "  <td rowspan=\"10\">\n"
			+ "    <img src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/fill_right1.png\" width=\"57\" height=\"226\" alt=\"\">\n"
			+ "  </td>\n"
			+ "  <td>\n"
			+ "    <img src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/spacer.gif\" width=\"1\" height=\"6\" alt=\"\">\n"
			+ "  </td>\n"
			+ "</tr>\n";
	}

	private String addMainImage(Image mainImage) {
		int width = 277;
		int height = 207;
		Dimension scaledDimensions = getScaledImageDimensions(mainImage.getLocalFileUrl(),width,height,false);
		return "<tr>\n"
			+ "  <td colspan=\"2\" rowspan=\"9\">\n"
			+ "    <img src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/fill_left1.png\" width=\"57\" height=\"220\" alt=\"\">\n"
			+ "  </td>\n"
			+ "  <td colspan=\"7\" rowspan=\"7\">\n"
			+ "    <table id=\"kuvia_tuotteesta\" width=\"285\" height=\"215\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\" bgcolor=\"#eeeeee\">\n"
			+ "      <tbody>\n"
			+ "        <tr>\n"
			+ "          <td colspan=\"3\">\n"
			+ "            <img src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/image_border.png\" width=\"285\" height=\"4\" alt=\"\">\n"
			+ "          </td>\n"
			+ "        </tr>\n"
			+ "        <tr>\n"
			+ "          <td>\n"
			+ "            <img src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/image_border.png\" width=\"4\" height=\"207\" alt=\"\">\n"
			+ "          </td>\n"
			+ "          <td>\n"
			//+ "    <a href=\"" + mainImage.getImageHostUrl() + "\" target=\"_blank\" rel=\"nofollow\">\n" //if the link is put here, then the link area is still as small as the image
			+ "            <table id=\"kuva_1\" align=\"center\" width=\"" + width + "\" height=\"" + height + "\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\" bgcolor=\"white\">\n"
			//+ "    <a href=\"" + mainImage.getImageHostUrl() + "\" target=\"_blank\" rel=\"nofollow\">\n" //if the link is put here, then the link is removed altogether (i.e. the image isn't clickable at all)
			+ "              <tbody>\n"
			//+ "    <a href=\"" + mainImage.getImageHostUrl() + "\" target=\"_blank\" rel=\"nofollow\">\n" //if the link is put here, then the link is removed altogether (i.e. the image isn't clickable at all)
			+ "                <tr align=\"center\">\n"
			+ "                  <td>\n"
			+ "    <a href=\"" + mainImage.getImageHostUrl() + "\" target=\"_blank\" rel=\"nofollow\">\n"
			+ "      <img src=\"" + mainImage.getImageHostUrl() + "\" width=\"" + (int) scaledDimensions.getWidth()
			+ "\" height=\"" + (int) scaledDimensions.getHeight() + "\" alt=\"\">\n"
			+ "    </a>\n"
			+ "                  </td>\n"
			+ "                </tr>\n"
			//+ "    </a>\n" //if the link is put here, then the link is removed altogether (i.e. the image isn't clickable at all)
			+ "              </tbody>\n"
			//+ "    </a>\n" //if the link is put here, then the link is removed altogether (i.e. the image isn't clickable at all)
			+ "            </table>\n"
			//+ "    </a>\n" //if the link is put here, then the link area is still as small as the image
			+ "          </td>\n"
			+ "          <td>\n"
			+ "            <img src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/image_border.png\" width=\"4\" height=\"207\" alt=\"\">\n"
			+ "          </td>\n"
			+ "        </tr>\n"
			+ "        <tr>\n"
			+ "          <td colspan=\"3\">\n"
			+ "            <img src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/image_border.png\" width=\"285\" height=\"4\" alt=\"\">\n"
			+ "          </td>\n"
			+ "        </tr>\n"
			+ "      </tbody>\n"
			+ "    </table>\n"
			+ "  </td>\n"
			+ "  <td rowspan=\"10\">\n"
			+ "    <img src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/right_of_main_pic.png\" width=\"6\" height=\"221\" alt=\"\">\n"
			+ "  </td>\n"
			+ "  <td>\n"
			+ "    <img src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/spacer.gif\" width=\"1\" height=\"2\" alt=\"\">\n"
			+ "  </td>\n"
			+ "</tr>";
	}

	/**
	 * 
	 * @param adTemplate
	 * @param strBuf
	 * @return Number of dimension rows including headline.
	 */
	private int addDimensions(AdTemplate adTemplate, StringBuffer strBuf) {
		int dimensionRows = 0;
		strBuf.append("<tr valign=\"top\">\n"
			+ "  <td colspan=\"6\" rowspan=\"9\">\n"
			+ "    <table id=\"speksit\" width=\"281\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\" bgcolor=\"#eeeeee\">\n"
			+ "      <tbody>\n"
			+ "        <tr valign=\"top\" height=\"25px\">\n"
			+ "          <td>\n"
			+ "            <font face=\"Arial\" size=\"2\" color=\"#656565\"><b>" + StringUtilities.normalizer(adTemplate.getHeadline().toUpperCase()) + "</b></font>\n"
			+ "          </td>\n"
			+ "        </tr>\n");
		dimensionRows++;
		if (adTemplate.getProduct().getDimensions() != null) {
			String[] lines = adTemplate.getProduct().getDimensions().split("\n");
			for (int i = 0; i < lines.length; i++) {
				String line = lines[i].trim();
				String[] lineParts = line.split(":");
				strBuf.append("        <tr valign=\"top\" height=\"20px\">\n"
						+ "          <td>\n"
						+ "            <font face=\"Arial\" size=\"2\" color=\"#656565\"><b>"
						+ StringUtilities.normalizer(lineParts[0]) + ": </b>" + StringUtilities.normalizer(lineParts[1])
						+ "</font><br>\n"
						+ "          </td>\n"
						+ "        </tr>\n");
				dimensionRows++;
			}
		}
		strBuf.append("      </tbody>\n"
			+ "    </table>\n"
			+ "  </td>\n"
			+ "  <td>\n"
			+ "    <img src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/spacer.gif\" width=\"1\" height=\"20\" alt=\"\">\n"
			+ "  </td>\n"
			+ "</tr>");
		return dimensionRows;
	}

	private String addSameCategoryAdsLink(Ad ad, SellerMarket sellerMarket) {
		return "<tr>\n"
			+ "  <td colspan=\"2\">\n"
			+ "    <img src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/line_btw_qlinks.png\" width=\"165\" height=\"12\" alt=\"\">\n"
			+ "  </td>\n"
			+ "  <td>\n"
			+ "    <img src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/spacer.gif\" width=\"1\" height=\"12\" alt=\"\">\n"
			+ "  </td>\n"
			+ "</tr>\n"
			+ "<tr>\n"
			+ "  <td colspan=\"2\">\n"
			+ "    <a href=\"http://www.huuto.net/fi/showlist.php3?ncat[]=" + ad.getMarketCategory().getMarketSpecId()
			+ "&seller=" + sellerMarket.getUserName() + "&status=DN&odir=A\" target=\"_blank\" rel=\"nofollow\">\n"
			+ "      <img src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/text_same_category_prods.png\" width=\"165\" height=\"28\" alt=\"\">\n"
			+ "    </a>\n"
			+ "  </td>\n"
			+ "  <td>\n"
			+ "    <img src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/spacer.gif\" width=\"1\" height=\"28\" alt=\"\">\n"
			+ "  </td>\n"
			+ "</tr>\n"
			;
	}

	private String addSimilarProductsAdsLink(AdTemplate adTemplate, SellerMarket sellerMarket) {
		//Note: this one can probably be redone without resorting to the hack with adding invisible
		//texts to the ads by using the seller parameter. For example the seller Laite-ja varustemyynti
		//uses the following: http://www.huuto.net/fi/showlist.php3?tits=videokamer*+or+muistikortt*&ccat=-1&status=N&stype=A&classification=0&seller=Laite-%26varustemyynti&sellstyle=K&order=B&start=0&num=50&odir=D
		return "<tr>\n"
			+ "  <td colspan=\"2\">\n"
			+ "    <img src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/line_btw_qlinks.png\" width=\"165\" height=\"12\" alt=\"\">\n"
			+ "  </td>\n"
			+ "  <td>\n"
			+ "    <img src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/spacer.gif\" width=\"1\" height=\"12\" alt=\"\">\n"
			+ "  </td>\n"
			+ "</tr>"
			+ "<tr>\n"
			+ "  <td colspan=\"2\">\n"
			+ "    <a href=\"http://www.huuto.net/fi/showlist.php3?odir=a&sellstyle=K&status=N&classification=0&stype=A&tits="
			+ StringUtilities.toUSASCIICharacters(adTemplate.getProduct().getCategory().getName()).replaceAll(" ","") + "&sprice=&eprice=&s_ltime=0"
			+ "&e_ctime=0&seller=&location=&bidder=\" target=\"_blank\" rel=\"nofollow\">\n"
			+ "      <img src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/text_similar_prods.png\" width=\"165\" height=\"27\" alt=\"\">\n"
			+ "    </a>\n"
			+ "  </td>\n"
			+ "  <td>\n"
			+ "    <img src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/spacer.gif\" width=\"1\" height=\"27\" alt=\"\">\n"
			+ "  </td>\n"
			+ "</tr>\n"
			+ "<tr>\n"
			+ "  <td colspan=\"2\" rowspan=\"2\">\n"
			+ "    <img src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/right_col_box_separator.png\" width=\"165\" height=\"116\" alt=\"\">\n"
			+ "  </td>\n"
			+ "  <td>\n"
			+ "    <img src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/spacer.gif\" width=\"1\" height=\"114\" alt=\"\">\n"
			+ "  </td>\n"
			+ "</tr>";
	}

	private String addSpaceForDimensions(int phase, int dimensionRows) {
		int flexibleHeight = 15;
		if (dimensionRows > 8) {
			/* Here we need to calculate how much space we should add based on the
			 * number of rows of the dimensions and that's not yet implemented. */
			throw new RuntimeException("Not yet implemented.");
		}
		if (phase == 1) {
			return "<tr>\n"
				+ "  <td colspan=\"7\" rowspan=\"3\">\n"
				//+ "    <span>HERE WE CAN PUT SOME TEXT, IMAGE OR AD IF THE TECHNICAL SPECS GROW TOO LARGE.</span>\n"
				+ "  </td>\n"
				+ "  <td>\n"
				+ "    <img src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/spacer.gif\" width=\"1\" height=\"2\" alt=\"\">\n"
				+ "  </td>\n"
				+ "</tr>";
		} else {
			return "<tr>\n"
				+ "  <td colspan=\"2\">\n"
				+ "    <img id=\"juoksuta_speksit\" src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/juoksuta_speksit1.png\""
				+ " width=\"57\" height=\"" + flexibleHeight + "\" alt=\"\">\n"
				+ "  </td>\n"
				+ "  <td>\n"
				+ "    <img id=\"juoksuta_speksit\" src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/juoksuta_speksit2.png\""
				+ " width=\"83\" height=\"" + flexibleHeight + "\" alt=\"\">\n"
				+ "  </td>\n"
				+ "  <td>\n"
				+ "    <img id=\"juoksuta_speksit\" src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/juoksuta_speksit3.png\""
				+ " width=\"57\" height=\"" + flexibleHeight + "\" alt=\"\">\n"
				+ "  </td>\n"
				+ "  <td>\n"
				+ "    <img src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/spacer.gif\" width=\"1\" height=\"1\" alt=\"\">\n"
				+ "  </td>\n"
				+ "</tr>";
		}
	}

	private int addOtherProducts(Ad ad, StringBuffer strBuf, List<Image> images, List<AdTemplate> addedAdTemplates) {
		strBuf.append("<tr valign=\"top\">\n"
			+ "  <td rowspan=\"10\">\n"
			+ "    <img src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/other_prods_left.png\" width=\"8\" height=\"423\" alt=\"\">\n"
			+ "  </td>\n"
			+ "  <td rowspan=\"14\">\n"
			+ "    <table id=\"kuvia_tuotteesta\" width=\"157\" height=\"208\" border=\"0\""
			+ " cellpadding=\"0\" cellspacing=\"0\" bgcolor=\"#eeeeee\">\n"
			+ "      <tbody>");

		List<Ad> adList = getLinksToOtherAds(ad,addedAdTemplates);
		int maxOtherProducts = 2; 
		if (images.size() < 4) { //at most 2 extra images
			maxOtherProducts = 3;
		} else if (images.size() < 6) { //at most 4 extra images
			maxOtherProducts = 4;
		} else if (images.size() < 8) { //at most 6 extra images
			maxOtherProducts = 5;
		} else {
			maxOtherProducts = 6;
		}
		int addedOtherProds = 0;
		for (Ad otherAd : adList) {
			Image mainImage = otherAd.getAdTemplate().getProduct().getImagesInOrder().get(0);
			int width = 149;
			int height = 149;
			Dimension scaledDimensions = getScaledImageDimensions(mainImage.getLocalFileUrl(),width,height,false);
			strBuf.append("        <tr>\n"
				+ "          <td>\n"
				+ "            <table id=\"muu_tuote_kuva_1\" width=\"157\" height=\"157\" border=\"0\""
				+ " cellpadding=\"0\" cellspacing=\"0\" bgcolor=\"white\">\n"
				+ "              <tbody>\n"
				+ "                <tr>\n"
				+ "                  <td colspan=\"3\">\n"
				+ "                    <img src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/image_border.png\" width=\"157\" height=\"4\" alt=\"\">\n"
				+ "                  </td>\n"
				+ "                </tr>\n"
				+ "                <tr>\n"
				+ "                  <td>\n"
				+ "                    <img src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/image_border.png\" width=\"4\" height=\"149\" alt=\"\">\n"
				+ "                  </td>\n"
				+ "                  <td>\n"
				+ "                    <table id=\"kuva_1\" width=\"" + width + "\" height=\"" + height + "\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\" bgcolor=\"white\">\n"
				+ "                      <tbody>\n"
				+ "                        <tr align=\"center\">\n"
				+ "                          <td>\n"
				+ "                            <a href=\"" + getBounceToMarketURL(otherAd) + "\" target=\"_blank\"><img"
				+ " src=\"" + mainImage.getImageHostUrl() + "\" width=\"" + (int) scaledDimensions.getWidth()
				+ "\" height=\"" + (int) scaledDimensions.getHeight() + "\" alt=\"\"></a>\n"
				+ "                          </td>\n"
				+ "                        </tr>\n"
				+ "                      </tbody>\n"
				+ "                    </table>\n"
				+ "                  </td>\n"
				+ "                  <td>\n"
				+ "                    <img src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/image_border.png\" width=\"4\" height=\"149\" alt=\"\">\n"
				+ "                  </td>\n"
				+ "                </tr>\n"
				+ "                <tr>\n"
				+ "                  <td colspan=\"3\">\n"
				+ "                    <img src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/image_border.png\" width=\"157\" height=\"4\" alt=\"\">\n"
				+ "                  </td>\n"
				+ "                </tr>\n"
				+ "              </tbody>\n"
				+ "            </table>\n"
				+ "          </td>\n"
				+ "        </tr>\n"
				+ "        <tr valign=\"TOP\">\n"
				+ "          <td>\n"
				+ "            <table id=\"kuvia_tuotteesta\" width=\"157\" height=\"52\" border=\"0\""
				+ " cellpadding=\"0\" cellspacing=\"0\" bgcolor=\"#eeeeee\">\n"
				+ "              <tbody>\n"
				+ "                <tr valign=\"TOP\">\n"
				+ "                  <td>\n"
				+ "                    <a href=\"" + getBounceToMarketURL(otherAd) + "\" target=\"_blank\">\n"
				+ "                      <font face=\"Arial\" size=\"2\" color=\"#181818\">" + StringUtilities.normalizer(otherAd.getAdTemplate().getHeadline()) + "</font>\n"
				+ "                    </a>\n"
				+ "                  </td>\n"
				+ "                </tr>\n"
				+ "                <tr valign=\"TOP\">\n"
				+ "                  <td>\n"
				+ "                    <a href=\"" + getBounceToMarketURL(otherAd) + "\" target=\"_blank\">\n"
				+ "                      <font face=\"Arial\" size=\"2\" color=\"#181818\">"/* + SHORT_DATE_FORMAT.format(otherAd.getDateClosing())*/ + " [</font><font face=\"Arial\""
				+ " size=\"2\" color=\"#406900\"><b>" + currencyFormatter.format(otherAd.getStartPriceInMarketCurrencyInclVat())
				+ "</b></font><font face=\"Arial\" size=\"2\" color=\"#181818\">]</font>\n"
				+ "                    </a>\n"
				+ "                  </td>\n"
				+ "                </tr>\n"
				+ "                <tr height=\"15\">\n"
				+ "                  <td>\n"
				+ "                  </td>\n"
				+ "                </tr>\n"
				+ "              </tbody>\n"
				+ "            </table>\n"
				+ "          </td>\n"
				+ "        </tr>\n");
			addedOtherProds++;
			if (addedOtherProds == maxOtherProducts) {
				break;
			}
		}
		
		strBuf.append("      </tbody>\n"
				+ "    </table>\n"
				+ "  </td>\n"
				+ "  <td>\n"
				+ "    <img src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/spacer.gif\" width=\"1\" height=\"3\" alt=\"\">\n"
				+ "  </td>\n"
				+ "</tr>");
		return addedOtherProds;
	}

	private String addSummaryText(AdTemplate adTemplate) {
		/* Usually all technical specs rows of Hautala consist of just one long row, so
		 * let's approximate how many rows that corresponds to. Assume that around 80 characters
		 * fit onto one line. */
		//String techSpecs = StringUtilities.normalizer(adTemplate.getProduct().getTechnicalSpecs());
		String techSpecs = StringUtilities.normalizer(adTemplate.getTechnicalSpecs());
		/*if (adTemplate.getProduct().getManufacturerUrl() != null) {
			techSpecs += " Valmistajan tiedot: " + adTemplate.getProduct().getManufacturerUrl();
		}*/
		//int nbrWords = techSpecs.split(" ").length;
		//int nbrRows = techSpecs.length() / 80;
		int nbrRows = (int) Math.ceil((double) techSpecs.length() / 80);
		
		/* Three rows can be added without adding extra space. If extra space is needed, then assume
		 * that 17 pixels are needed for each extra row. */
		int flexibleHeight = 35;
		if (nbrRows > 3) {
			flexibleHeight += (nbrRows - 3)*17;
		}

		return "<tr valign=\"top\">\n"
			+ "  <td colspan=\"2\">\n"
			+ "    <img src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/summary_left_border.png\" width=\"57\" height=\"66\" alt=\"\">\n"
			+ "  </td>\n"
			+ "  <td colspan=\"14\" rowspan=\"2\">\n"
			+ "    <table id=\"summary\" width=\"572\" height=\"67\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\" bgcolor=\"#eeeeee\">\n"
			+ "      <tbody>\n"
			+ "        <tr>\n"
			+ "          <td>\n"
			+ "            <font face=\"Arial\" size=\"2\" color=\"#656565\">" + techSpecs + "</font>"
			+ (adTemplate.getProduct().getManufacturerUrl() != null ?
					"            <font face=\"Arial\" size=\"2\" color=\"#656565\"> - Valmistajan tiedot löytyvät "
					+ "<a href=\"" + adTemplate.getProduct().getManufacturerUrl() + "\" target=\"_blank\" rel=\"nofollow\"> tästä.</a></font>"
					: "")
			+ "          </td>\n"
			+ "        </tr>\n"
			+ "      </tbody>\n"
			+ "    </table>\n"
			+ "  </td>\n"
			+ "  <td>\n"
			+ "    <img src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/summary_middle_border.png\" width=\"83\" height=\"66\" alt=\"\">\n"
			+ "  </td>\n"
			+ "  <td>\n"
			+ "    <img src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/summary_right_border.png\" width=\"57\" height=\"66\" alt=\"\">\n"
			+ "  </td>\n"
			+ "  <td>\n"
			+ "    <img src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/spacer.gif\" width=\"1\" height=\"66\" alt=\"\">\n"
			+ "  </td>\n"
			+ "</tr>\n"
			+ "<tr>\n"
			+ "  <td colspan=\"2\">\n"
			+ "    <img id=\"juoksuta_summary\" src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/juoksuta_summary1.png\" width=\"57\" height=\"" + flexibleHeight + "\" alt=\"\">\n"
			+ "  </td>\n"
			+ "  <td>\n"
			+ "    <img id=\"juoksuta_summary\" src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/juoksuta_summary2.png\" width=\"83\" height=\"" + flexibleHeight + "\" alt=\"\">\n"
			+ "  </td>\n"
			+ "  <td>\n"
			+ "    <img id=\"juoksuta_summary\" src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/juoksuta_summary3.png\" width=\"57\" height=\"" + flexibleHeight + "\" alt=\"\">\n"
			+ "  </td>\n"
			+ "  <td>\n"
			+ "    <img src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/spacer.gif\" width=\"1\" height=\"1\" alt=\"\">\n"
			+ "  </td>\n"
			+ "</tr>";
	}

	private String addImagesForProductHeadline() {
		return "<tr>\n"
			+ "  <td colspan=\"5\">\n"
			+ "    <img src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/prod_img_headline1.png\" width=\"178\" height=\"55\" alt=\"\">\n"
			+ "  </td>\n"
			+ "  <td colspan=\"6\">\n"
			+ "    <img src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/prod_img_headline2.png\" width=\"174\" height=\"55\" alt=\"\">\n"
			+ "  </td>\n"
			+ "  <td colspan=\"4\">\n"
			+ "    <img src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/prod_img_headline3.png\" width=\"181\" height=\"55\" alt=\"\">\n"
			+ "  </td>\n"
			+ "  <td colspan=\"2\">\n"
			+ "    <img src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/prod_img_headline4.png\" width=\"179\" height=\"55\" alt=\"\">\n"
			+ "  </td>\n"
			+ "  <td rowspan=\"2\">\n"
			+ "    <img src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/prod_img_headline5.png\" width=\"57\" height=\"84\" alt=\"\">\n"
			+ "  </td>\n"
			+ "  <td>\n"
			+ "    <img src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/spacer.gif\" width=\"1\" height=\"55\" alt=\"\">\n"
			+ "  </td>\n"
			+ "</tr>";
	}

	private void addImagesForProduct(StringBuffer strBuf, List<Image> images, int addedOtherProds) {
		strBuf.append("<tr>\n"
				+ "  <td colspan=\"3\" rowspan=\"5\">\n"
				+ "    <img src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/other_img_left_border.png\" width=\"59\" height=\"297\" alt=\"\">\n"
				+ "  </td>\n"
				+ "  <td colspan=\"13\" rowspan=\"4\">\n");
		
		/* Add the images. Note: there is no support for removing the "Kuvia tuotteesta" part (adding
		 * support for that would require quite big changes of the html). Therefore we always display
		 * the main image also under "Kuvia tuotteesta" to make sure that we don't just get a big empty
		 * area. (If, however, there are more than one images, then don't display the main image a
		 * second time.) */
		int addedExtraImages = 0;
		boolean extraTDAdded = false;
		boolean addedFrameImages = false;
		if (images.size() > 0) {
			for (int i = 0; i < images.size(); i++) {
				if (images.size() > 1 && i == 0) {
					//Skip the first image if there are more images than one (because the first image is already displayed elsewhere).
					continue;
				}
				Image image = images.get(i);
				if (addedExtraImages == 2) {
					/* If we are at the beginning of row two. */
					strBuf.append("<tr>\n"
							+ "  <td colspan=\"13\" rowspan=\"2\">\n");
					extraTDAdded = true;
				}
				if (addedExtraImages % 2 == 0) {
					/* Add the start table if we are at the beginning of a row. */
					strBuf.append("    <table id=\"kuvia_tuotteesta\" width=\"570\" height=\"285\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\" bgcolor=\"#eeeeee\">\n"
							+ "      <tbody>\n"
							+ "        <tr>\n");
				} else {
					/* Add some extra space between horizontally lined up images. */
					strBuf.append("          <td>\n"
							+ "            <table id=\"empty_space\" width=\"28\" height=\"270\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\" bgcolor=\"#eeeeee\">\n"
							+ "              <tbody>\n"
							+ "                <tr>\n"
							+ "                  <td>\n"
							+ "                  </td>\n"
							+ "                </tr>\n"
							+ "              </tbody>\n"
							+ "            </table>\n"
							+ "          </td>\n");
				}
				
				/* Display image. */
				int width = 262;
				int height = 262;
				Dimension scaledDimensions = getScaledImageDimensions(image.getLocalFileUrl(),width,height,false);
				strBuf.append("          <td>\n"
						+ "            <table id=\"kuva_1\" width=\"270\" height=\"270\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\" bgcolor=\"white\">\n"
						+ "              <tbody>\n"
						+ "                <tr>\n"
						+ "                  <td colspan=\"3\">\n"
						+ "                    <img src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/image_border.png\" width=\"270\" height=\"4\" alt=\"\">\n"
						+ "                  </td>\n"
						+ "                </tr>\n"
						+ "                <tr>\n"
						+ "                  <td>\n"
						+ "                    <img src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/image_border.png\" width=\"4\" height=\"262\" alt=\"\">\n"
						+ "                  </td>\n"
						+ "                  <td>\n"
						+ "                    <table id=\"kuva_1\" width=\"" + width + "\" height=\"" + height + "\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\" bgcolor=\"white\">\n"
						+ "                      <tbody>\n"
						+ "                        <tr align=\"center\">\n"
						+ "                          <td>\n"
						+ "                            <a href=\"" + image.getImageHostUrl() + "\" target=\"_blank\" rel=\"nofollow\">\n"
						+ "                              <img src=\"" + image.getImageHostUrl() + "\" width=\"" + (int) scaledDimensions.getWidth()
						+ "\" height=\"" + (int) scaledDimensions.getHeight() + "\" alt=\"\">\n"
						+ "                            </a>\n"
						+ "                          </td>\n"
						+ "                        </tr>\n"
						+ "                      </tbody>\n"
						+ "                    </table>\n"
						+ "                  </td>\n"
						+ "                  <td>\n"
						+ "                    <img src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/image_border.png\" width=\"4\" height=\"262\" alt=\"\">\n"
						+ "                  </td>\n"
						+ "                </tr>\n"
						+ "                <tr>\n"
						+ "                  <td colspan=\"3\">\n"
						+ "                    <img src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/image_border.png\" width=\"270\" height=\"4\" alt=\"\">\n"
						+ "                  </td>\n"
						+ "                </tr>\n"
						+ "              </tbody>\n"
						+ "            </table>\n"
						+ "          </td>\n");

				addedExtraImages++;
				if (addedExtraImages % 2 == 0 || i == (images.size()-1)) {
					/* Add the end table tag if we are at the end of a row. */
					strBuf.append("        </tr>\n"
							+ "      </tbody>\n"
							+ "    </table>\n");
				}
				
				/* Add the frame images if we are at the end of the first row. */
				if (addedExtraImages == 2 || (addedExtraImages == 1 && i == (images.size()-1))) {
					addFrameImagesForExtraPictures(strBuf);
					addedFrameImages = true;
				}
			}
		}
		
		/* This one always need to be added, so if it wasn't added in the loop above, then
		 * it needs to be added here. */
		if (!addedFrameImages) {
			addFrameImagesForExtraPictures(strBuf);
		}
		
		if (!extraTDAdded) {
			/* This one always need to be added, so if it wasn't added in the loop above, then
			 * it needs to be added here. */
			strBuf.append("<tr>\n"
					+ "  <td colspan=\"13\" rowspan=\"2\">\n");
			extraTDAdded = true;
		}
		strBuf.append("  </td>\n");

		int nbrAddedRows = (int) Math.ceil((double) addedExtraImages / 2);
		int flexibleHeight = 20 + addedOtherProds*20;
		if (nbrAddedRows > 1) {
			flexibleHeight += (nbrAddedRows-1)*275;
		}
		strBuf.append("  <td>\n"
				+ "    <img src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/spacer.gif\" width=\"1\" height=\"12\" alt=\"\">\n"
				+ "  </td>\n"
				+ "</tr>\n"
				
				+ "<tr>\n"
				+ "  <td colspan=\"3\">\n"
				+ "    <img id=\"juoksuta_kuvat\" src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/juoksuta_other_images1.png\" width=\"59\" height=\"" + flexibleHeight + "\" alt=\"\">\n"
				+ "  </td>\n"
				+ "  <td colspan=\"2\">\n"
				+ "    <img id=\"juoksuta_kuvat\" src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/juoksuta_other_images2.png\" width=\"91\" height=\"" + flexibleHeight + "\" alt=\"\">\n"
				+ "  </td>\n"
				+ "  <td>\n"
				+ "    <img id=\"juoksuta_kuvat\" src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/juoksuta_other_images3.png\" width=\"57\" height=\"" + flexibleHeight + "\" alt=\"\">\n"
				+ "  </td>\n"
				+ "  <td>\n"
				+ "    <img src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/spacer.gif\" width=\"1\" height=\"1\" alt=\"\">\n"
				+ "  </td>\n"
				+ "</tr>\n");
	}

	private void addFrameImagesForExtraPictures(StringBuffer strBuf) {
		strBuf.append("  </td>\n"
				+ "  <td rowspan=\"5\">\n"
				+ "    <img src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/other_img_middle_border.png\" width=\"83\" height=\"297\" alt=\"\">\n" //7_40.jpg
				+ "  </td>\n"
				+ "  <td>\n"
				+ "    <img src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/spacer.gif\" width=\"1\" height=\"29\" alt=\"\">\n"
				+ "  </td>\n"
				+ "</tr>\n"
				+ "<tr>\n"
				+ "  <td>\n"
				+ "    <img src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/other_img_right_border1.png\" width=\"57\" height=\"53\" alt=\"\">\n" //7_41.jpg
				+ "  </td>\n"
				+ "  <td>\n"
				+ "    <img src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/spacer.gif\" width=\"1\" height=\"53\" alt=\"\">\n"
				+ "  </td>\n"
				+ "</tr>\n"
				+ "<tr>\n"
				+ "  <td>\n"
				+ "    <img src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/other_img_right_border2.png\" width=\"57\" height=\"156\" alt=\"\">\n" //7_42.jpg
				+ "  </td>\n"
				+ "  <td>\n"
				+ "    <img src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/spacer.gif\" width=\"1\" height=\"156\" alt=\"\">\n"
				+ "  </td>\n"
				+ "</tr>\n"
				+ "<tr>\n"
				+ "  <td rowspan=\"2\">\n"
				+ "    <img src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/other_img_right_border3.png\" width=\"57\" height=\"59\" alt=\"\">\n" //7_43.jpg
				+ "  </td>\n"
				+ "  <td>\n"
				+ "    <img src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/spacer.gif\" width=\"1\" height=\"47\" alt=\"\">\n"
				+ "  </td>\n"
				+ "</tr>\n");
	}

	private String addInformationAboutUs(AdTemplate adTemplate, SellerMarket sellerMarket) {
		String aboutUs = "Olemme suomalainen puutarhakoneisiin ja -työkaluihin erikoistunut"
			+ " verkkokauppa. Edustamamme Wolf-Garten tunnetaan luotettavuudesta ja laadusta."
			+ " Kaikki myytävät koneet ja laitteet toimitetaan kotiovellesi ammattimaisin ottein.";
		
		/* Assume that around 80 characters fit onto one line and then calculate how many
		 * rows will be needed for the "about us" information. */
		int nbrRows = (int) Math.ceil((double) aboutUs.length() / 80);
		
		/* Five rows can be added without adding extra space. If extra space is needed, then assume
		 * that 17 pixels are needed for each extra row. */
		int flexibleHeight = 15;
		if (nbrRows > 5) {
			flexibleHeight += (nbrRows - 5)*17;
		}
		
		return "<tr>\n"
			+ "  <td colspan=\"4\">\n"
			+ "    <img src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/info_about_us_headline1.png\" width=\"143\" height=\"121\" alt=\"\">\n"
			+ "  </td>\n"
			+ "  <td colspan=\"4\">\n"
			+ "    <img src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/info_about_us_headline2.png\" width=\"172\" height=\"121\" alt=\"\">\n"
			+ "  </td>\n"
			+ "  <td colspan=\"5\">\n"
			+ "    <img src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/info_about_us_headline3.png\" width=\"193\" height=\"121\" alt=\"\">\n"
			+ "  </td>\n"
			+ "  <td colspan=\"5\">\n"
			+ "    <img src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/info_about_us_headline4.png\" width=\"212\" height=\"121\" alt=\"\">\n"
			+ "  </td>\n"
			+ "  <td>\n"
			+ "    <img src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/info_about_us_headline5.png\" width=\"57\" height=\"121\" alt=\"\">\n"
			+ "  </td>\n"
			+ "  <td>\n"
			+ "    <img src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/spacer.gif\" width=\"1\" height=\"121\" alt=\"\">\n"
			+ "  </td>\n"
			+ "</tr>\n"
			+ "<tr>\n"
			+ "  <td>\n"
			+ "    <img id=\"juoksuta_tietoa_meista\" src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/juoksuta_about_us1.png\" width=\"49\" height=\"" + flexibleHeight + "\" alt=\"\">\n"
			+ "  </td>\n"
			+ "  <td>\n"
			+ "    <img src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/left_of_about_us.png\" width=\"8\" height=\"1\" alt=\"\">\n"
			+ "  </td>\n"
			+ "  <td colspan=\"14\" rowspan=\"2\">\n"
			+ "    <table id=\"company_info\" width=\"572\" height=\"87\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\" bgcolor=\"#eeeeee\">\n"
			+ "      <tbody>\n"
			+ "        <tr valign=\"top\">\n"
			+ "          <td>\n"
			+ "            <font face=\"Arial\" size=\"2\" color=\"#656565\">" + aboutUs + "</font>\n"
			/* Add an invisible sellstar category which can be used for making searches. */
			+ "            <font face=\"Arial\" size=\"1\" color=\"#eeeeee\">" + sellerMarket.getUserName() + " " + sellerMarket.getSeller().getName()
			+ " " + StringUtilities.toUSASCIICharacters(adTemplate.getProduct().getCategory().getName()).replaceAll(" ","") + "</font>\n"
			+ "          </td>\n"
			+ "        </tr>\n"
			+ "      </tbody>\n"
			+ "    </table>\n"
			+ "  </td>\n"
			+ "  <td colspan=\"2\">\n"
			+ "    <img id=\"juoksuta_tietoa_meista\" src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/juoksuta_about_us2.png\" width=\"91\" height=\"" + flexibleHeight + "\" alt=\"\">\n"
			+ "  </td>\n"
			+ "  <td>\n"
			+ "    <img id=\"juoksuta_tietoa_meista\" src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/juoksuta_about_us3.png\" width=\"57\" height=\"" + flexibleHeight + "\" alt=\"\">\n"
			+ "  </td>\n"
			+ "  <td>\n"
			+ "    <img src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/spacer.gif\" width=\"1\" height=\"1\" alt=\"\">\n"
			+ "  </td>\n"
			+ "</tr>\n"
			+ "<tr>\n"
			+ "  <td colspan=\"2\">\n"
			+ "    <img src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/about_us_bottom_left.png\" width=\"57\" height=\"86\" alt=\"\">\n"
			+ "  </td>\n"
			+ "  <td colspan=\"2\">\n"
			+ "    <img src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/about_us_bottom_middle.png\" width=\"91\" height=\"86\" alt=\"\">\n"
			+ "  </td>\n"
			+ "  <td>\n"
			+ "    <img src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/about_us_bottom_right.png\" width=\"57\" height=\"86\" alt=\"\">\n"
			+ "  </td>\n"
			+ "  <td>\n"
			+ "    <img src=\"http://www.sellstar.fi/images/seller/Gardenia/ad/spacer.gif\" width=\"1\" height=\"86\" alt=\"\">\n"
			+ "  </td>\n"
			+ "</tr>";
	}

	private String addBottomImage() {
		return "<tr>\n"
			+ "  <td colspan=\"7\">\n"
			+ "    <img src=\"http://www.sellstar.fi/images/test/7_61.png\" width=\"262\" height=\"242\" alt=\"\">\n"
			+ "  </td>\n"
			+ "  <td colspan=\"7\">\n"
			+ "    <img src=\"http://www.sellstar.fi/images/test/7_62.png\" width=\"259\" height=\"242\" alt=\"\">\n"
			+ "  </td>\n"
			+ "  <td colspan=\"4\">\n"
			+ "    <img src=\"http://www.sellstar.fi/images/test/7_63.png\" width=\"199\" height=\"242\" alt=\"\">\n"
			+ "  </td>\n"
			+ "  <td colspan=\"2\">\n"
			+ "    <img src=\"http://www.sellstar.fi/images/test/7_64.png\" width=\"214\" height=\"242\" alt=\"\">\n"
			+ "  </td>\n"
			+ "  <td>\n"
			+ "    <img src=\"http://www.sellstar.fi/images/test/spacer.gif\" width=\"1\" height=\"242\" alt=\"\">\n"
			+ "  </td>\n"
			+ "</tr>\n"
			+ "<tr>\n"
			+ "  <td>\n"
			+ "    <img src=\"http://www.sellstar.fi/images/test/spacer.gif\" width=\"49\" height=\"1\" alt=\"\">\n"
			+ "  </td>\n"
			+ "  <td>\n"
			+ "    <img src=\"http://www.sellstar.fi/images/test/spacer.gif\" width=\"8\" height=\"1\" alt=\"\">\n"
			+ "  </td>\n"
			+ "  <td>\n"
			+ "    <img src=\"http://www.sellstar.fi/images/test/spacer.gif\" width=\"2\" height=\"1\" alt=\"\">\n"
			+ "  </td>\n"
			+ "  <td>\n"
			+ "    <img src=\"http://www.sellstar.fi/images/test/spacer.gif\" width=\"84\" height=\"1\" alt=\"\">\n"
			+ "  </td>\n"
			+ "  <td>\n"
			+ "    <img src=\"http://www.sellstar.fi/images/test/spacer.gif\" width=\"35\" height=\"1\" alt=\"\">\n"
			+ "  </td>\n"
			+ "  <td>\n"
			+ "    <img src=\"http://www.sellstar.fi/images/test/spacer.gif\" width=\"40\" height=\"1\" alt=\"\">\n"
			+ "  </td>\n"
			+ "  <td>\n"
			+ "    <img src=\"http://www.sellstar.fi/images/test/spacer.gif\" width=\"44\" height=\"1\" alt=\"\">\n"
			+ "  </td>\n"
			+ "  <td>\n"
			+ "    <img src=\"http://www.sellstar.fi/images/test/spacer.gif\" width=\"53\" height=\"1\" alt=\"\">\n"
			+ "  </td>\n"
			+ "  <td>\n"
			+ "    <img src=\"http://www.sellstar.fi/images/test/spacer.gif\" width=\"27\" height=\"1\" alt=\"\">\n"
			+ "  </td>\n"
			+ "  <td>\n"
			+ "    <img src=\"http://www.sellstar.fi/images/test/spacer.gif\" width=\"6\" height=\"1\" alt=\"\">\n"
			+ "  </td>\n"
			+ "  <td>\n"
			+ "    <img src=\"http://www.sellstar.fi/images/test/spacer.gif\" width=\"4\" height=\"1\" alt=\"\">\n"
			+ "  </td>\n"
			+ "  <td>\n"
			+ "    <img src=\"http://www.sellstar.fi/images/test/spacer.gif\" width=\"96\" height=\"1\" alt=\"\">\n"
			+ "  </td>\n"
			+ "  <td>\n"
			+ "    <img src=\"http://www.sellstar.fi/images/test/spacer.gif\" width=\"60\" height=\"1\" alt=\"\">\n"
			+ "  </td>\n"
			+ "  <td>\n"
			+ "    <img src=\"http://www.sellstar.fi/images/test/spacer.gif\" width=\"13\" height=\"1\" alt=\"\">\n"
			+ "  </td>\n"
			+ "  <td>\n"
			+ "    <img src=\"http://www.sellstar.fi/images/test/spacer.gif\" width=\"12\" height=\"1\" alt=\"\">\n"
			+ "  </td>\n"
			+ "  <td>\n"
			+ "    <img src=\"http://www.sellstar.fi/images/test/spacer.gif\" width=\"96\" height=\"1\" alt=\"\">\n"
			+ "  </td>\n"
			+ "  <td>\n"
			+ "    <img src=\"http://www.sellstar.fi/images/test/spacer.gif\" width=\"83\" height=\"1\" alt=\"\">\n"
			+ "  </td>\n"
			+ "  <td>\n"
			+ "    <img src=\"http://www.sellstar.fi/images/test/spacer.gif\" width=\"8\" height=\"1\" alt=\"\">\n"
			+ "  </td>\n"
			+ "  <td>\n"
			+ "    <img src=\"http://www.sellstar.fi/images/test/spacer.gif\" width=\"157\" height=\"1\" alt=\"\">\n"
			+ "  </td>\n"
			+ "  <td>\n"
			+ "    <img src=\"http://www.sellstar.fi/images/test/spacer.gif\" width=\"57\" height=\"1\" alt=\"\">\n"
			+ "  </td>\n"
			+ "  <td></td>\n"
			+ "  </tr>";
	}

	@Override
	public String getBeanVersion() {
		return "1.0";
	}

}
