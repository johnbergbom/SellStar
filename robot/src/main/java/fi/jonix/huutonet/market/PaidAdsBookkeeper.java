package fi.jonix.huutonet.market;

import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import fi.jonix.huutonet.domain.VirtualCoin;
import fi.jonix.huutonet.domain.model.Ad;
import fi.jonix.huutonet.domain.model.Currency;
import fi.jonix.huutonet.domain.model.dao.AdDAO;
import fi.jonix.huutonet.email.EmailSender;
import fi.jonix.huutonet.tools.CurrencyFormatter;
import fi.jonix.huutonet.tools.Statics;
import fi.jonix.huutonet.tools.StringUtilities;

@Component(value = "paidAdsBookkeeper")
public class PaidAdsBookkeeper {

	public static final Logger logger = Logger.getLogger(PaidAdsBookkeeper.class);

	@Autowired
	private AdDAO adDAO;

	public void createVerification() {
		/* Create verification. */
		List<Ad> unverifiedAds = adDAO.findUnverifiedAds();
		StringBuffer verification = createVerificationForDisplayWindowAds(unverifiedAds);
		logger.debug("Verification: " + verification.toString());
		
		/* Update the ads. */
		for (Ad ad : unverifiedAds) {
			logger.debug("Verifying ad " + ad.getId());
			ad.setTransferedToVerification(true);
		}
		
		/* Inform the operator. */
		EmailSender.sendOperationProblemReport("Verification for highlighting of products at Huutonet",
				"Hightlighting of products using wallet done at Huutonet. Please print out and add this"
				+ " verification to the book keeping.\n\n---------------------------------------------------\n\n"
				+ verification.toString());
	}

	private StringBuffer createVerificationForDisplayWindowAds(List<Ad> highlightedAds) {
		BigDecimal totalCostEuroInclVat = BigDecimal.ZERO;
		BigDecimal totalCostEuroExclVat = BigDecimal.ZERO;
		for (Ad ad : highlightedAds) {
			totalCostEuroInclVat = totalCostEuroInclVat.add(ad.getAdvertisementCostInEuroInclVat());
			totalCostEuroExclVat = totalCostEuroExclVat.add(ad.getAdvertisementCostInEuroExclVat());
			if (ad.getAdvertisementCostInEuroExclVat().compareTo(BigDecimal.ZERO) == 0
					|| ad.getAdvertisementCostInEuroInclVat().compareTo(BigDecimal.ZERO) == 0) {
				throw new RuntimeException("Ad " + ad.getId() + " still has a zero advertisement cost.");
			}
		}
		
		//TODO: don't hardcode "23%" here. Rather create a market specific vatPerc field that is used when buying things
		//from the market itself.
		String expectedVatPercentage = "23%";

		DateFormat dateFormatDateOnly = new SimpleDateFormat("dd.MM.yyyy");
		Long usedCurrency = null;
		StringBuffer verification = new StringBuffer();
		DateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
		verification.append("------------------------------------------\n");
		verification.append("PAID ADVERTISEMENTS (" + dateFormat.format(new Date()) + "):\n");
		verification.append("------------------------------------------\n");
		verification.append("    Date    |    Seller    | Advertisement id |    Alv    | Alviton hinta | Alvillinen hinta\n");
		verification.append("------------+--------------+------------------+-----------+---------------+-----------------\n");
		BigDecimal totalVatCostEuro = BigDecimal.ZERO;
		for (Ad ad : highlightedAds) {
			usedCurrency = ad.getMarket().getCurrency().getId();
			if (!usedCurrency.equals(Currency.EURO)) {
				/* We get here if we start to buy these using some other currency than euros. In that case
				 * we probably need to change the output of this method (handle different currencies separately)
				 * and possibly also add the new fields ad.advert_cost_mc_inc_vat and ad.advert_cost_mc_exc_vat.
				 * (For statistical purposes it's enough to have prices just in euros but for the bookkeeping
				 * we probably need to have exact market specific currencies.) */
				logger.warn("Paid ads are bought in currency other than euro. Doesn't know how to create verification.");
				throw new RuntimeException("Paid ads are bought in currency other than euro. Doesn't know how to create verification.");
			}
			BigDecimal vatCost = ad.getAdvertisementCostInEuroInclVat().subtract(ad.getAdvertisementCostInEuroExclVat());
			totalVatCostEuro = totalVatCostEuro.add(vatCost);
			VirtualCoin vatCostVC = new VirtualCoin(usedCurrency,vatCost);
			VirtualCoin exclVatVC = new VirtualCoin(usedCurrency,ad.getAdvertisementCostInEuroExclVat());
			VirtualCoin inclVatVC = new VirtualCoin(usedCurrency,ad.getAdvertisementCostInEuroInclVat());
			String vatVCStr = CurrencyFormatter.print(vatCostVC, Statics.FINNISH);
			String exclVatVCStr = CurrencyFormatter.print(exclVatVC, Statics.FINNISH);
			String inclVatVCStr = CurrencyFormatter.print(inclVatVC, Statics.FINNISH);
			verification.append(" ");
			StringUtilities.printField("   Date   ".length(),dateFormatDateOnly.format(ad.getDateToMarket()),verification,false);
			verification.append(" | ");
			StringUtilities.printField("   Seller   ".length(),ad.getAdTemplate().getSeller().getName(),verification,false);
			verification.append(" | ");
			StringUtilities.printField("Advertisement id".length(),"" + ad.getId(),verification,true);
			verification.append(" | ");
			StringUtilities.printField("   Alv   ".length(),vatVCStr,verification,true);
			verification.append(" | ");
			StringUtilities.printField("Alviton hinta".length(),exclVatVCStr,verification,true);
			verification.append(" | ");
			StringUtilities.printField("Alvillinen hinta".length(),inclVatVCStr,verification,true);
			verification.append("\n");
		}
		
		if (totalVatCostEuro.compareTo(new BigDecimal("40.00")) < 0) {
			throw new RuntimeException("Skipping createVerification.");
		}
		
		verification.append("------------+--------------+------------------+-----------+---------------+-----------------\n");
		verification.append(" ");
		StringUtilities.printField("   Date    |    Seller    | Advertisement id".length(),"Yhteensä:",verification,true);
		verification.append(" | ");
		VirtualCoin totalVatCostVC = new VirtualCoin(usedCurrency,totalVatCostEuro);
		String totalVatVCStr = CurrencyFormatter.print(totalVatCostVC, Statics.FINNISH);
		StringUtilities.printField("   Alv   ".length(),totalVatVCStr,verification,true);
		verification.append(" | ");
		VirtualCoin totalCostEuroExclVatVC = new VirtualCoin(usedCurrency,totalCostEuroExclVat);
		String totalCostEuroExclVatVCStr = CurrencyFormatter.print(totalCostEuroExclVatVC, Statics.FINNISH);
		StringUtilities.printField("Alviton hinta".length(),totalCostEuroExclVatVCStr,verification,true);
		verification.append(" | ");
		VirtualCoin totalCostEuroInclVatVC = new VirtualCoin(usedCurrency,totalCostEuroInclVat);
		String totalCostEuroInclVatVCStr = CurrencyFormatter.print(totalCostEuroInclVatVC, Statics.FINNISH);
		StringUtilities.printField("Alvillinen hinta".length(),totalCostEuroInclVatVCStr,verification,true);
		verification.append("\n");
		StringBuffer finalVerific = new StringBuffer();
		finalVerific.append("OHJEET KIRJANPITÄJÄLLE: Tämä tosite koskee internet-mainnonnan maksua. Maksu");
		finalVerific.append(" tehtiin huutonettiin käyttäen\nesitäytettyä kukkaroa. Eli rahoja EI otettu");
		finalVerific.append(" yritystililtä tästä vaan se tehtiin jo aiemmin silloin kun\nkukkarolle");
		finalVerific.append(" siirrettiin rahaa. Sen takia maksun loppusummaa ei tarvitse (välttämättä?) kirjata");
		finalVerific.append("  mihinkään.\nSen sijaan tulee vähentää alvit (" + expectedVatPercentage);
		finalVerific.append(" - " + totalVatVCStr + ") meidän yhteisestä myynnistä koska aiemmin tehty");
		finalVerific.append(" rahojen\nsiirto yritystililtä kukkaroon oli alviton.\n\n\n");
		finalVerific.append(verification);
		return finalVerific;
	}
	
}
