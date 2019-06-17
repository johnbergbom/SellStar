package fi.jonix.huutonet.tools;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

import fi.jonix.huutonet.domain.model.Ad;
import fi.jonix.huutonet.domain.model.Order;
import fi.jonix.huutonet.domain.model.StateChange;
import fi.jonix.huutonet.domain.model.dao.AdDAO;
import fi.jonix.huutonet.domain.model.dao.OrderDAO;
import fi.jonix.huutonet.domain.model.dao.SettingsDAO;
import fi.jonix.huutonet.email.EmailSender;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * This class checks for inconsistency problems, fixes things and if necessary informs
 * the operator about problems. It's supposed to be ran automatically for example
 * once per day.
 * 
 * TODO: Add the following checks:
 * - is the last fetched bank transaction more than 3 days old?
 * - does some order have status = error?
 * - do such transactions exist that have both order_id and oper_descr != null?
 * - are there orders in state faulty contact info that are over two weeks old?
 * - kolla efter ads med headline kortare än 5 tecken (tex. adtemplate.id = 1493)
 * - kolla efter översättningar i translation-tabellen som innehåller erikoismerkkejä
 * - kolla efter ads och products med "SKU" i rubriken
 * - kolla ifall någon icke-operator i users-tabellen har forwarding=true
 *   för någon typ av globala email
 * - varmista att det finns minst en operator i users-tabellen
 * - varmista että löytyy mottagare i users-tabellen för alla typer av email
 *   som kan behöva forwardas
 * - make sure that no order has been in the state STATE_ORDERED_FROM_PROVIDER_NO_ID
 *   several times (because the send count is zeroed each time it goes out of that
 *   state, so basically it would thus be possible to order the same product 1000
 *   times, and what if dx bills us for each one...)
 * - check if some order has order.lastCustomerUpdateSent >= 7 dagar
 * - kolla ifall fler än en order i samma bundle har shippingCost > 0
 * - kolla ifall alla ordrar i en bundle har shippingCost == 0
 * - kolla ifall mer än en order viittaa samaan adiin => tämä taitaa nykyään olla sallittua
 * - kolla ifall någon bundle har ett värde på mer än 40 euro
 * - kolla ifall någon bundle saknar orders
 * - kolla ifall någon order har order.targetedPaid > order.expectedPaid (detta ska aldrig förekomma eftersom
 *   excessive payment alltid ska flyttas till bundle.targetedPaidShippingCost) => detta kollas redan i faultyPaymentFixer.
 * - kolla ifall någon bundle eller order har targetedPaid < 0
 * - kolla ifall en och samma kund för någon bundle har targetedPaid > expectedPaid och
 *   samtidigt för någon order targetedPaid < expectedPaid eller tvärtom.
 * - kolla ifall någon provider conversation has been open for very long
 * - kolla ifall någon order har order.status != det som state_changes-tabellen säger
 * - kolla ifall det finns någon produkt för vilken det är väldigt länge sedan status/prisinformation har hämtats
 * - kolla ifall det finns någon order vars produkt hör till en annan provider än bundle.provider_id
 * - kolla ifall det finns produkter som inte har listats på mer än två veckor fastän de finns i stock
 * - kolla ifall det finns någon produkt för vilken man inte har kunnat hämta priset för
 *   på två veckor (tex. ifall man koko ajan får "provider down" för produkten i fråga)
 * - kolla ifall det finns något icke-automatiskt acknowledged error som är äldre än ett dygn
 *  
 * @author john
 *
 */
@Component(value = "consistencyChecker")
public class ConsistencyChecker {
	
	public static final Logger logger = Logger.getLogger(ConsistencyChecker.class);
	
	@Autowired
	private AdDAO adDAO;
	
	@Autowired
	private SettingsDAO settingsDAO;

	@Autowired
	private OrderDAO orderDAO;

	public void run() throws Exception {
		List<Ad> unconfirmedAds = adDAO.getUnconfirmedAds();
		if (unconfirmedAds.size() > 50) {
			String emailStr = "There were " + unconfirmedAds.size() + " unconfirmed ads. Is there some problem with listing of new ads?\n\n"
				+ "This can also happen if the lister is running and SellStar has been in the error state in the mean time.";
			EmailSender.sendOperationSummaryReport("Suspiciously many unconfirmed ads",emailStr);
		}
		/* Go through all unconfirmed ads and check for inconsistency problems
		 * + remove the ones older than one month. */
		Calendar monthAgo = Calendar.getInstance();
		monthAgo.add(Calendar.DATE,-31);
		Calendar dayAgo = Calendar.getInstance();
		dayAgo.add(Calendar.DATE,-1);
		StringBuffer hours24 = new StringBuffer();
		int countHours24 = 0;
		for (Ad ad : unconfirmedAds) {
			if (ad.getDateFromMarket() != null) {
				String emailStr = "Database inconsistency problem: ad " + ad.getId() + " was not confirmed but still has dateFromMarket != null.";
				EmailSender.sendOperationProblemReport("Database inconsistency problem detected",emailStr);
			} else if (ad.getDateToMarket().getTime() < monthAgo.getTime().getTime()) {
				logger.info("Removing ad " + ad.getId() + " because it was unconfirmed and over a month old.");
				adDAO.delete(ad);
			} else if (ad.getDateToMarket().getTime() < dayAgo.getTime().getTime()) {
				if (hours24.length() > 0) {
					hours24.append(",");
				}
				countHours24++;
				if (countHours24 % 15 == 0) {
					hours24.append("\n");
				}
				hours24.append(ad.getId());
			}
		}

		if (hours24.length() > 0) {
			String emailStr = "Unconfirmed ads older than 24 hours detected. These shouldn't normally exist.\n\n" + hours24.toString();
			EmailSender.sendOperationSummaryReport("Unconfirmed ads detected",emailStr);
		}

		/* Old open ads can be automatically closed. There are two cases that can leave an ad open:
		 * 1.) Delivery of the market's closed-email failed (has happened in practice).
		 * 2.) Mikko.fi doesn't send out emails when an ad is manually closed. */
		StringBuffer oldAds = new StringBuffer();
		List<Ad> openOldAds = adDAO.getOpenOldAds();
		for (Ad ad : openOldAds) {
			Calendar limit = Calendar.getInstance();
			limit.setTime(ad.getDateClosing());
			limit.add(Calendar.DATE,1);
			if (limit.getTimeInMillis() < System.currentTimeMillis()) {
				ad.setDateFromMarket(new Date());
				if (oldAds.length() > 0) {
					oldAds.append(",");
				}
				oldAds.append(ad.getId());
			}
		}
		if (oldAds.length() > 0) {
			String emailStr = "There are " + openOldAds.size()
				+ " open ads in the database whose dateClosing has gone by. The following ads were automatically closed\n\n" + oldAds.toString()
				+ "\n\nMost likely this happened because the closing email from the market wasn't delivered to the seller's mailbox.\n"
				+ "Another possible reason is that some markets don't send any acknowledgement when an ad is manually closed.";
			logger.debug(emailStr);
			if (openOldAds.size() > 10) {
				EmailSender.sendOperationSummaryReport("Many old open ads detected",emailStr);
			}
			logger.warn(emailStr);
		}
	}

}
