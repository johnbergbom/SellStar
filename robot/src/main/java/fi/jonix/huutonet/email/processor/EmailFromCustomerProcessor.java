package fi.jonix.huutonet.email.processor;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.mail.Message;
import javax.mail.internet.InternetAddress;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import fi.jonix.huutonet.domain.VirtualCoin;
import fi.jonix.huutonet.domain.model.Customer;
import fi.jonix.huutonet.domain.model.Order;
import fi.jonix.huutonet.domain.model.OrderBundle;
import fi.jonix.huutonet.domain.model.ProviderQuestion;
import fi.jonix.huutonet.domain.model.Seller;
import fi.jonix.huutonet.domain.model.SellerMarket;
import fi.jonix.huutonet.domain.model.dao.OrderDAO;
import fi.jonix.huutonet.domain.model.dao.ProviderQuestionDAO;
import fi.jonix.huutonet.domain.model.dao.SellerMarketDAO;
import fi.jonix.huutonet.email.EmailSender;
import fi.jonix.huutonet.exception.CriticalSectionInfo;
import fi.jonix.huutonet.market.SellerEmailFilter;
import fi.jonix.huutonet.order.ContactInfoChecker;
import fi.jonix.huutonet.order.OrderInformation;
import fi.jonix.huutonet.post.GeneralPostService;
import fi.jonix.huutonet.tools.CurrencyFormatter;
import fi.jonix.huutonet.tools.Statics;
import fi.jonix.huutonet.tools.StringUtilities;

/**
 * This class checks if the email is from a customer that's in the customer
 * database.
 * 
 * @author john
 * 
 */
@Component(value = "emailFromCustomerProcessor")
public class EmailFromCustomerProcessor extends SellerEmailFilter {

	public static final Logger logger = Logger.getLogger(EmailFromCustomerProcessor.class);
	public static String STATUS_REQUEST = "status";

	@Autowired
	private OrderDAO orderDAO;
	
	@Autowired
	private ProviderQuestionDAO providerQuestionDAO;
	
	@Autowired
	private GeneralPostService generalPostService;
	
	@Autowired
	private SellerMarketDAO sellerMarketDAO;

	private Order resolveOrderByMarketSalesId(String text, Seller seller) {
		List<String> numeralStrings = this.getNumeralString(9, 9, text.trim().replaceAll(" ", "").replaceAll("\n", ""));
		for (String numeralString : numeralStrings) {
			List<Order> orderList = orderDAO.getByMarketSalesId(numeralString,seller,null);
			if (orderList.size() > 0) {
				/* If there are two markets having the same marketSalesId, then we don't know
				 * to which one of these the marketSalesId belongs to. */
				Long marketId = null;
				for (Order order : orderList) {
					if (marketId == null) {
						marketId = order.getAd().getMarket().getId();
					} else if (!marketId.equals(order.getAd().getMarket().getId())) {
						throw new RuntimeException("Not sure of which ad this marketSalesId refers to"
								+ " since it exists on several markets (" + numeralString + "). Fix this one.");
					}
				}
				//If all orders belong to the same market, then we can simply pick the first one
				return orderList.get(0);
			}
		}
		return null;
	}
	
	private Order resolveOrderByReferenceNumber(String text, Seller seller) {
		List<String> numeralStrings = this.getNumeralString(10, 21, text.trim().replaceAll(" ", "").replaceAll("\n", ""));
		for (String numeralString : numeralStrings){
			Order order = orderDAO.getByReferenceNumber(numeralString,seller);
			if (order != null) {
				return order;
			}
		}
		return null;
	}

	private List<String> getNumeralString(Integer minLength, Integer maxLength, String text) {
		List<String> numeralStrings = new ArrayList<String>();
		int start = -1;
		for (int i = 0; i < text.length(); i++) {
			if (Character.isDigit(text.charAt(i))) {
				if (start < 0) {
					start = i;
				}
			} else if (start >= 0) {
				if ((minLength == null || i - start >= minLength) && (maxLength == null || i - start <= maxLength)) {
					numeralStrings.add(text.substring(start, i));
				}
				start = -1;
			}
		}
		if(start >= 0){
			numeralStrings.add(text.substring(start));
		}
		return numeralStrings;
	}
	
	private Order figureOutOrderFromEmail(Message message, Seller seller) throws Exception {
		String subject = message.getSubject();
		if (subject == null) {
			subject = "";
		}
		subject = StringUtilities.replaceNewLinesWithSpace(StringUtilities.removeNonUSASCIICharacters(subject));
		String contents = EmailTools.getPlainTextContents(message);
		contents = StringUtilities.replaceNewLinesWithSpace(StringUtilities.removeNonUSASCIICharacters(contents));
		Order order = resolveOrderByMarketSalesId(subject,seller);
		if (order == null) {
			order = resolveOrderByMarketSalesId(contents,seller);
		}
		if (order == null) {
			order = resolveOrderByReferenceNumber(subject,seller);
		}
		if (order == null) {
			order = resolveOrderByReferenceNumber(contents,seller);
		}
		return order;
	}

	@Override
	public boolean processSellerEmail(Message message, Seller seller,
			CriticalSectionInfo processingInfo) throws Exception {
		InternetAddress sender = (InternetAddress) message.getFrom()[0];
		String email = sender.getAddress();
		List<Order> orderList = orderDAO.getOrdersByCustomerEmailAddress(email,seller);

		/* In many cases the customers send emails from a different email address than what we
		 * have in our database. If no orders were found when searching for orders by email address,
		 * then try to figure out what order this email might be about by checking if the email
		 * contains a valid market sales id or a valid reference number. */
		String orderNumberStr = "unknown order";
		if (orderList == null || orderList.isEmpty()) {
			Order order = figureOutOrderFromEmail(message,seller);
			if (order != null) {
				email = order.getOrderBundle().getCustomer().getEmail();
				orderNumberStr = "order " + order.getId();
				orderList = orderDAO.getOrdersByCustomer(order.getOrderBundle().getCustomer(),seller);
			}
		} else if (orderList.size() == 1) {
			orderNumberStr = "order " + orderList.get(0).getId();
		} else {
			/* If the customer has more than one order, then we don't know which one this email
			 * concerns, so try to figure that out from the email contents if possible. */
			Order order = figureOutOrderFromEmail(message,seller);
			if (order != null) {
				orderNumberStr = "order " + order.getId();
			}
		}
		
		if (orderList == null || orderList.isEmpty() || !orderList.get(0).getOrderBundle().getSeller().equals(seller)) {
			return false;
		}

		email = email.toLowerCase();
		StringBuffer strBuf = new StringBuffer();
		if (message.getSubject() != null && message.getSubject().trim().equalsIgnoreCase(STATUS_REQUEST)) {
			/* If status request, then send an email to the customer. */
			strBuf.append("Hei,\n\nTässä statustietoa avoimista tilauksistasi:\n\n");
			//strBuf.append(OrderInformation.getCustomerAddressInfo(orderList.get(0).getCustomer()) + "\n");
			strBuf.append(OrderInformation.getOrderStockInfo(orderList,false));
			strBuf.append("\n\n\n\nYstävällisin terveisin,\n" + seller.getSignature() + "\n");
			EmailSender.sendEmailBatch(orderList.get(0).getOrderBundle().getSeller().getSystemEmail(),email, "Tilaustesi status", strBuf.toString());
			return true;
		} else {
			/* Otherwise send an email to the operator. */
			strBuf.append("<ANSWER>\nMoi,\n\n\n\nYstävällisin terveisin,\n" + seller.getSignature() + "\n</ANSWER>\n\n");
			strBuf.append("<ACTION></ACTION>\n\n");
			strBuf.append("<OLD_EMAIL>" + EmailTools.getPlainTextContents(message) + "</OLD_EMAIL>\n\n");
			StringBuffer answerSuggestions = getAnswerSuggestions(EmailTools.getPlainTextContents(message),orderList, seller);
			if (answerSuggestions.length() > 0) {
				strBuf.append("<LIKELY_QUESTIONS>\n");
				strBuf.append(answerSuggestions.toString() + "\n");
				strBuf.append("</LIKELY_QUESTIONS>\n\n");
			}
			answerSuggestions = getOtherPossibleQuestions(orderList);
			if (answerSuggestions.length() > 0) {
				strBuf.append("<POSSIBLE_QUESTIONS>\n");
				strBuf.append(answerSuggestions.toString() + "\n");
				strBuf.append("</POSSIBLE_QUESTIONS>\n\n");
			}
			strBuf.append("<INFORMATION>\n");
			strBuf.append("Email from customer received.\n\n");
			//strBuf.append(OrderInformation.getCustomerAddressInfo(orderList.get(0).getCustomer()) + "\n");
			strBuf.append(OrderInformation.getOrderStockInfo(orderList,true));
			
			/* If the customer has some order that's ordered from provider but not yet shipped from provider,
			 * then also include the provider conversations for those bundles.
			 * 
			 * Note: possibly we could also add a possibility to write to the provider at the same time
			 * as we're answering customer emails. However this isn't necessarily so useful since the
			 * provider is already kicked about the order if the shipping is delayed, and order cancelation
			 * takes place through www.hkmyynti.fi anyway. */
			Map<Long,String> bundleIds = new HashMap<Long,String>();
			for (Order order : orderList) {
				OrderBundle bundle = order.getOrderBundle();
				if (bundleIds.get(bundle.getId()) == null) {
					if (order.getState().intValue() >= Order.STATE_ORDERED_FROM_PROVIDER
							&& order.getState().intValue() < Order.STATE_SHIPPED_BY_PROVIDER) {
						bundleIds.put(bundle.getId(),"");
						List<ProviderQuestion> providerQuestionList = providerQuestionDAO.getProviderQuestionForBundle(bundle);
						if (providerQuestionList != null && providerQuestionList.size() > 0) {
							if (providerQuestionList.size() > 1) {
								strBuf.append("\n\nNOTE: A TOTAL OF " + providerQuestionList.size()
										+ " PROVIDER CONVERSATIONS EXIST. ONLY SHOWING THE LATEST ONE.\n\n");
							}
							ProviderQuestion providerQuestion = providerQuestionList.get(0);
							strBuf.append("\nMessage from provider for bundle " + bundle.getId() + ":\n");
							strBuf.append("--------------------------------------\n");
							strBuf.append("Nbr messages: " + providerQuestion.getNbrMessages() + "\n");
							strBuf.append("Last own comment: " + providerQuestion.getLastOwnComment() + "\n");
							strBuf.append("Last provider comment: " + providerQuestion.getLastProviderComment() + "\n");
							strBuf.append("Provider order id: " + bundle.getId() + "\n");
							strBuf.append("Provider ticket id: " + providerQuestion.getProviderTicketId() + "\n");
							strBuf.append("Subject: " + providerQuestion.getSubject() + "\n");
							strBuf.append("Contents:\n" + providerQuestion.getContents() + "\n\n");
						}
					}
				}
			}
			strBuf.append("</INFORMATION>\n\n");
			strBuf.append("<CUSTOMER_EMAIL_ADDRESS>" + email + "</CUSTOMER_EMAIL_ADDRESS>\n\n");
			strBuf.append("<ORIGINAL_SUBJECT>" + message.getSubject() + "</ORIGINAL_SUBJECT>\n\n");
			String forwardCustomerEmailsTo = seller.getForwardCustomerEmails();
			if (forwardCustomerEmailsTo == null) {
				forwardCustomerEmailsTo = seller.getUser().getEmailAddress();
			}

			/* Check if this email should be associated with some bundle. */
			OrderBundle bundleToAssociateWith = null;
			for (Order order : orderList) {
				if (order.getState().intValue() >= Order.STATE_CUSTOMER_PAID_INFORMED
						&& order.getState().intValue() < Order.STATE_SHIPPED_BY_PROVIDER) {
					bundleToAssociateWith = order.getOrderBundle();
					break; //pick the first one found (hard to do any better)
				}
			}
			
			/* Customer id and order id are added to the subject in order to make sure that gmail
			 * doesn't group these emails together.
			 * =>UPDATE: in fact it's good to group the emails together customer wise, so this is
			 * now changed. */
			EmailSender.sendEmailBatch(seller.getSystemEmail(),forwardCustomerEmailsTo, "Email from customer "
					+ orderList.get(0).getOrderBundle().getCustomer().getId()/* + " (" + orderNumberStr + ")"*/ + " - "
					+ seller.getSignature(), strBuf.toString(),bundleToAssociateWith);
		}

		logger.info("Email from customer received.");
		return true;
	}

	private void getAnswerForWhereIsMyProduct(StringBuffer strBuf, List<Order> orderList) {
		DateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy");
		StringBuffer noTracking = new StringBuffer();
		Map<Long,StringBuffer> gaveUpTracking = new HashMap<Long,StringBuffer>();
		Map<Long,StringBuffer> stillTracking = new HashMap<Long,StringBuffer>();
		List<String> gaveUpTrackingUrls = new ArrayList<String>();
		List<String> trackingUrls = new ArrayList<String>();
		for (Order order : orderList) {
			if (order.getState().equals(Order.STATE_SHIPPED_BY_PROVIDER)
					|| order.getState().equals(Order.STATE_SHIPPED_BY_POSTOFFICE)) {
				if (order.getTracking() == null) {
					noTracking.append("Tilaus " + order.getId() + " (" + order.getAd().getAdTemplate().getHeadline()
							+ ") oli viety postiin " + dateFormat.format(order.getLatestShippingDate()));
				} else if (!order.getTracking().getFollow()) {
					String url1 = generalPostService.getManualTrackingURL(order.getTracking(), Order.STATE_SHIPPED_BY_POSTOFFICE);
					String url2 = generalPostService.getManualTrackingURL(order.getTracking(), Order.STATE_SHIPPED_BY_PROVIDER);
					if (!gaveUpTrackingUrls.contains(url1)) {
						gaveUpTrackingUrls.add(url1);
					}
					if (!gaveUpTrackingUrls.contains(url2)) {
						gaveUpTrackingUrls.add(url2);
					}
					if (gaveUpTracking.get(order.getTracking().getId()) == null) {
						StringBuffer appender = new StringBuffer();
						appender.append("Seurantakoodilla " + order.getTracking().getTrackingCode() + " (viety postiin"
								+ " " + dateFormat.format(order.getLatestShippingDate()) + "):\n");
						gaveUpTracking.put(order.getTracking().getId(), appender);
					}
					StringBuffer appender = gaveUpTracking.get(order.getTracking().getId());
					appender.append("Tilaus " + order.getId() + " (" + order.getAd().getAdTemplate().getHeadline() + ")\n");
				} else {
					String url1 = generalPostService.getManualTrackingURL(order.getTracking(), Order.STATE_SHIPPED_BY_POSTOFFICE);
					String url2 = generalPostService.getManualTrackingURL(order.getTracking(), Order.STATE_SHIPPED_BY_PROVIDER);
					if (!trackingUrls.contains(url1)) {
						trackingUrls.add(url1);
					}
					if (!trackingUrls.contains(url2)) {
						trackingUrls.add(url2);
					}
					if (stillTracking.get(order.getTracking().getId()) == null) {
						StringBuffer appender = new StringBuffer();
						appender.append("Seurantakoodilla " + order.getTracking().getTrackingCode() + " (viety postiin"
								+ " " + dateFormat.format(order.getLatestShippingDate()) + "):\n");
						stillTracking.put(order.getTracking().getId(), appender);
					}
					StringBuffer appender = stillTracking.get(order.getTracking().getId());
					appender.append("Tilaus " + order.getId() + " (" + order.getAd().getAdTemplate().getHeadline() + ")\n");
				}
			}
		}
		StringBuffer tmpBuf = new StringBuffer();
		if (noTracking.length() > 0) {
			tmpBuf.append("Lähetetty ilman seurantaa:\n");
			tmpBuf.append("--------------------------\n");
			tmpBuf.append("Seuraavat tilaukset ovat lähetetty ilman seurantaa. Normaalitapauksessa"
					+ " tuotteet tulevat perille noin kolme viikkoa postituksen jälkeen, mutta välillä"
					+ " saattaa kestää pari viikkoa kauemmin (esim. jos Kiinan postissa on ruuhka)."
					+ " Välillä Kiinan posti on erittäin hidas ja paketit saattavat joskus jäädä roikkumaan"
					+ " kuukauden ennen kuin ne lähetetään eteenpäin.\n");
			tmpBuf.append(noTracking);
		}
		if (gaveUpTracking.size() > 0) {
			tmpBuf.append("Ongelmia seurannassa:\n");
			tmpBuf.append("---------------------\n");
			tmpBuf.append("Seurannassa näyttää olevan joku ongelma (ei välttämättä näy). Pystyt itse tarkistamaan");
			if (gaveUpTrackingUrls.size() > 1) {
				tmpBuf.append(" jostain näistä osoitteista:\n");
			} else {
				tmpBuf.append(" tästä osoitteesta:\n");
			}
			for (String url : gaveUpTrackingUrls) {
				tmpBuf.append(url + "\n");
			}
			tmpBuf.append("\nMikäli paketti ei näy lainkaan postin seurannassa, niin posti on sekoillut ja seuranta"
					+ " ei ole sitten saatavilla. Tässä tapauksessa tuotteen pitäisi kuitenkin saapua viimestään viisi viikkoa"
					+ " postituksen jälkeen. Jos taas tuote näkyy seurannassa, niin tarkistathan milloin oli viimeinen merkintä"
					+ " tästä paketista. Välillä Kiinan posti on erittäin hidas ja paketit saattavat joskus jäädä roikkumaan"
					+ " kuukauden ennen kuin ne lähetetään eteenpäin.\n");
			Iterator<StringBuffer> iter = gaveUpTracking.values().iterator();
			while (iter.hasNext()) {
				tmpBuf.append(iter.next());
			}
		}
		if (stillTracking.size() > 0) {
			tmpBuf.append("Seurattavat paketit:\n");
			tmpBuf.append("--------------------\n");
			tmpBuf.append("Pystyt tarkistamaan pakettien etenemistä");
			if (trackingUrls.size() > 1) {
				tmpBuf.append(" jostain näistä osoitteista:\n");
			} else {
				tmpBuf.append(" tästä osoitteesta:\n");
			}
			for (String url : trackingUrls) {
				tmpBuf.append(url + "\n");
			}
			tmpBuf.append("\nMikäli paketti ei näy lainkaan postin seurannassa, niin se saattaa johtua siitä, että alkaa näkyä"
					+ " vasta käsittelyjakson jälkeen. Mikäli ei näy kahden viikon sisällä postituksesta, niin"
					+ " posti on mahdollisesti kämmännyt ja toimittaa paketin hyödyntämättä seurantakoodia. Normaalisti tuote"
					+ " saapuu kolmen viikon sisällä postituksesta. Välillä Kiinan posti on erittäin hidas ja paketit saattavat"
					+ " joskus jäädä roikkumaan kuukauden ennen kuin ne lähetetään eteenpäin. Tarkistathan seurantasivulla"
					+ " milloin oli paketin viimeisin merkintä. Jos viimeisin merkintä oli muutama päivä sitten, niin se tarkoittaa"
					+ " sitä, että paketin toimitus edistyy.\n");
			Iterator<StringBuffer> iter = stillTracking.values().iterator();
			while (iter.hasNext()) {
				tmpBuf.append(iter.next());
			}
		}
		if (tmpBuf.length() > 0) {
			strBuf.append("QUESTION: WHERE IS MY PRODUCT?\n");
			strBuf.append("ANSWER:\n");
			strBuf.append(tmpBuf);
			strBuf.append("\n\n\n");
		}
	}

	private void getAnswerForBrokenProduct(StringBuffer strBuf, List<Order> orderList) {
		boolean likelyBrokenProduct = false;
		/* Start by determine if it's likely that this customer complains about a broken product.
		 * If the order was closed less than two weeks ago, then it might be a complaint. SellStar
		 * isn't always 100% up to date with the status of a packet, so it's possible that the
		 * packet has already reached the customer even if it's not yet in state "closed". */
		for (Order order : orderList) {
			if (order.getState().equals(Order.STATE_SHIPPED_BY_PROVIDER)
					|| order.getState().equals(Order.STATE_SHIPPED_BY_POSTOFFICE)) {
				long daysSinceShipping = (System.currentTimeMillis() - order.getLatestShippingDate().getTime()) / (1000*3600*24);
				if (daysSinceShipping >= 14) {
					likelyBrokenProduct = true;
				}
			} else if (order.getState() >= Order.STATE_REACHED_DESTINATION_COUNTRY
					&& order.getState() <= Order.STATE_FETCHED_BY_CUSTOMER) {
				long daysSinceShipping = (System.currentTimeMillis() - order.getLatestShippingDate().getTime()) / (1000*3600*24);
				if (daysSinceShipping >= 1) {
					likelyBrokenProduct = true;
				}
			} else if (order.getState().equals(Order.STATE_CLOSED)) {
				long daysInThisState = (System.currentTimeMillis() - order.getLatestStateChange().getUpdateDate().getTime()) / (1000*3600*24);
				if (daysInThisState <= 14) {
					likelyBrokenProduct = true;
				}
			}
		}
		if (likelyBrokenProduct) {
			strBuf.append("QUESTION: MY PRODUCT IS BROKEN\n");
			strBuf.append("ANSWER:\n");
			strBuf.append("Olet sitten saattanut saada viallisen kappaleen. Pahoittelemme tapahtunutta."
					+ " Saat hyvityksen jos lähetät tuotteen seuraavaan osoitteeseen:\n\nJohn Bergbom\n"
					+ "Palautus Osoitteen tie 500\n12345 Helsinki\n\nMuista kertoa paketissa mikä tilausnumero on"
					+ " kyseessä (XXXX) + kerrothan myös paketissa palautuksen postikulut + laita mukaan"
					+ " tietoja siitä, että haluatko saada uuden tuotteen vai rahat takaisin.");
			strBuf.append("\n\n\n");
		}
	}

	private void getCommonAnswersForNewOrder(String emailText, StringBuffer strBuf, List<Order> orderList, Seller seller) {
		/* Handle "I have paid" */
		String lowerCaseEmail = emailText.toLowerCase();
		boolean paidReference = (lowerCaseEmail.indexOf("maksettu") >= 0 || lowerCaseEmail.indexOf("maksanut") >= 0);
		for (Order order : orderList) {
			if ((order.getState().equals(Order.STATE_NEW) || order.getState().equals(Order.STATE_PAID_TOO_LITTLE_ERROR)) && paidReference) {
				//TODO: here we could possibly set "customer.date_kicked += 2 bank days" (so that the customer
				//won't get a paymend reminder right away).
				strBuf.append("QUESTION: I HAVE PAID\n");
				strBuf.append("ANSWER:\n");
				strBuf.append("Ei tarvitse itse asiassa erikseen ilmoittaa maksun suorittamisesta. Huomataan"
						+ " silloin kun rahat tulevat tilille (kunhan viitenumero on oikein laitettu). Saat"
						+ " ilmoituksen kun rahat ovat kirjautuneet tilillemme. Mikäli olet maksanut etkä"
						+ " ole siitä saanut kuittausta kolmen päivän sisällä, niin saattaa olla maksussasi"
						+ " joku ongelma.");
				strBuf.append("\n\n\n");
				break;
			}
		}
		
		/* Handle "My address is ..." */
		boolean addressReference = lowerCaseEmail.indexOf("osoite") >= 0;
		for (Order order : orderList) {
			if (order.getState().equals(Order.STATE_NEW) && addressReference) {
				strBuf.append("QUESTION: I HAVE A NEW ADDRESS\n");
				strBuf.append("ANSWER:\n");
				OrderBundle b = orderList.get(0).getOrderBundle();
				Customer c = b.getCustomer();
				strBuf.append("Meidän tämän hetkiset osoitetiedot ovat:\n"
						+ ContactInfoChecker.formatContactInfo(b.getFirstName(), b.getLastName(),
								b.getAddress(), b.getPostcode(), b.getCity(), c.getEmail())
						+ "\nMikäli haluat, että tuote lähetetään toiseen osoitteeseen, niin pyydämme korjaamaan"
						+ " osoitetietojasi maksuohjeissa olevan linkin kautta (ei sähköpostitse).");
				strBuf.append("\n\n\n");
				break;
			}
		}
		
		/* Handle "I lost my bill, please send me a new one" and "Could you send all my orders in one packet?" */
		int nbrNew = 0;
		for (Order order : orderList) {
			if (order.getState().equals(Order.STATE_NEW)) {
				nbrNew++;
			}
		}
		if (nbrNew > 0) {
			strBuf.append("QUESTION: CAN I HAVE A NEW BILL?\n");
			strBuf.append("ANSWER:\n");
			strBuf.append("Pystyt itse tilaamaan koontilaskun osoitteessa " + seller.getStoreUrl() + "\n\n\n");
			if (nbrNew > 0) {
				strBuf.append("QUESTION: CAN YOU SEND MY PRODUCTS IN ONE PACKAGE?\n");
				strBuf.append("ANSWER:\n");
				strBuf.append("Pystyt itse tilaamaan koontilaskun osoitteessa " + seller.getStoreUrl() + "\n\n\n");
				strBuf.append("\nMikäli olet ostamassa useita tuotteita, niin ne yhdistetään automaattisesti"
						+ " yhteen nippuun (yhteiset postikulut) mikäli tuotteet lähetetään samaan osoitteeseen"
						+ " ja hinta on niin matala, etteivät ALV- ja tullirajat ylity (noin 41 euroa). Jos"
						+ " tilaukset olisi sinun mielestä pitänyt niputtua, niin tarkistathan osoitetietoja ja"
						+ " hintoja.\n\n\n");
			}
		}
	}

	private void getAnswerForIsProductStillForSale(String emailText, StringBuffer strBuf) {
		/* Handle "Is the product still for sale". */
		String lowerCaseEmail = emailText.toLowerCase();
		boolean stillForSaleReference = (lowerCaseEmail.indexOf("vielä") >= 0 && (lowerCaseEmail.indexOf("myynnissä") >= 0
				|| lowerCaseEmail.indexOf("myytävänä") >= 0));
		if (stillForSaleReference) {
			strBuf.append("QUESTION: IS PRODUCT XXX STILL FOR SALE\n");
			strBuf.append("ANSWER:\n");
			strBuf.append("Voit ostaa tuotteita myös kohteiden sulkeuduttua klikkaamalla ilmoitusten"
					+ " linkkiä \"Tilaa tuote suoraan verkkokaupastamme\".");
			strBuf.append("\n\n\n");
		}
	}
	
	private void getAnswerForFaultyContactInfo(StringBuffer strBuf, List<Order> orderList) {
		for (Order order : orderList) {
			if (order.getState().equals(Order.STATE_FAULTY_CONTACT_INFO)) {
				strBuf.append("QUESTION (SOME ORDER IS IN STATE \"FAULTY CONTACT INFO\"): WHERE IS MY BILL?\n");
				strBuf.append("ANSWER:\n");
				strBuf.append("Sinulla on väärät osoitetiedot. Aiemmassa mailissa lähetettiin linkki webbilomakkeeseen"
						+ " jonka kautta pystyt muuttamaan osoitetietojasi (ei oteta vastaan osoitteen muutoksia"
						+ " sähköpostitse). Kun osoitetiedot ovat kunnossa, niin lähetämme sen jälkeen laskun.\n\n\n");
				break;
			}
		}
	}

	/**
	 * This method uses heuristics to attempt to guess what kind of question the customer
	 * might have and creates answers for those questions. 
	 * @param emailText
	 * @param orderList
	 * @return
	 */
	private StringBuffer getAnswerSuggestions(String emailText, List<Order> orderList, Seller seller) {
		StringBuffer strBuf = new StringBuffer();
		/* If the customer has orders in states "shipped by provider" or "shipped by postoffice", then
		 * he might ask where is product is. */
		getAnswerForWhereIsMyProduct(strBuf, orderList);
		
		/* If the customer has just received his product, then he might have complaints about it. */
		getAnswerForBrokenProduct(strBuf, orderList);
		
		/* If the customer has just placed an order, he might tell us for example one of the
		 * following things:
		 * 1.) "I have now paid"
		 * 2.) "I lost my bill, please send me a new one"
		 * 3.) "Could you send all my orders on one packet?"
		 * 4.) "My address is ..." */
		getCommonAnswersForNewOrder(emailText, strBuf, orderList, seller);

		/* The customer might ask if a certain product is still for sale. */
		getAnswerForIsProductStillForSale(emailText, strBuf);
		
		/* If the customer has an order in state "faulty contact info", then often
		 * they ask "How do I pay". */
		getAnswerForFaultyContactInfo(strBuf, orderList);
		
		/* People using mikko.fi often ask us "How do I order?".
		 * =>NOTE: it's not possible to answer this question here, because this code
		 * is only executed if the customer has actually made an order and in that case
		 * the customer has already succeeded placing an order...
		 * TODO: this one should rather be put into "public/private question from Mikko". */
		//getAnswerForMikko(strBuf, orderList);
		
		return strBuf;
	}

	/**
	 * It's not possible to make educated guesses about all kinds of common questions. Here
	 * we list questions and answers for some common questions that are not possible to
	 * deduce automatically based on the email contents nor from the states of possible orders
	 * of the customer.
	 */
	private StringBuffer getOtherPossibleQuestions(List<Order> orderList) {
		StringBuffer strBuf = new StringBuffer();
		strBuf.append("QUESTION: DO YOU HAVE THIS KIND OF PRODUCT ...?\n");
		strBuf.append("ANSWER:\n");
		strBuf.append("Voit katsoa meidän tuotteitamme osoitteessa: http://www.huuto.net/kauppa/hk-myynti\n\n\n");
		strBuf.append("QUESTION: HOW MUCH DOES SHIPPING COST?\n");
		strBuf.append("ANSWER:\n");
		
		/* Try to find the current shipping price. Shipping price is sellerMarket specific and there is
		 * no sellerMarket for Google-Order, so if the customer has placed orders only through Google-Order,
		 * then we skip displaying shipping cost. */
		SellerMarket sellerMarket = null;
		for (Order order : orderList) {
			String marketName = order.getMarket().getName();
			if (!marketName.endsWith("SNAPSHOT") && !marketName.equals("Google-Order")) {
				sellerMarket = sellerMarketDAO.getBySellerAndMarket(order.getOrderBundle().getSeller(), order.getMarket());
				break;
			}
		}
		if (sellerMarket != null) {
			VirtualCoin commonShippingCostVC = new VirtualCoin(sellerMarket.getMarket().getCurrency(),
					sellerMarket.getMinShippingCostMarketCurrInclVat());
			strBuf.append("Postikulut lukee ilmoituksessa, yleisimmin "
					+ CurrencyFormatter.print(commonShippingCostVC,Statics.FINNISH) + "\n\n\n");
		} else {
			strBuf.append("Postikulut lukee ilmoituksessa.\n\n\n");
		}
		strBuf.append("QUESTION: I WANT THREE ITEMS OF PRODUCT XXX\n");
		strBuf.append("ANSWER:\n");
		strBuf.append("Voit ostaa tuotteita (myös kohteiden sulkeuduttua) klikkaamalla ilmoitusten"
				+ " linkkiä \"Tilaa tuote suoraan verkkokaupastamme\". Aukeavassa lomakkeessa"
				+ " voi määritellä montako kappaletta haluat tilata.\n\n\n");
		return strBuf;
	}
	
}
