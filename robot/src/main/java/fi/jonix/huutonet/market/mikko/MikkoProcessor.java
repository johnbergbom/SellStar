package fi.jonix.huutonet.market.mikko;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.StringTokenizer;

import javax.mail.Message;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import fi.jonix.huutonet.domain.VirtualCoin;
import fi.jonix.huutonet.domain.model.Ad;
import fi.jonix.huutonet.domain.model.Currency;
import fi.jonix.huutonet.domain.model.Customer;
import fi.jonix.huutonet.domain.model.LoginName;
import fi.jonix.huutonet.domain.model.Market;
import fi.jonix.huutonet.domain.model.Provider;
import fi.jonix.huutonet.domain.model.Seller;
import fi.jonix.huutonet.domain.model.SellerMarket;
import fi.jonix.huutonet.domain.model.User;
import fi.jonix.huutonet.domain.model.dao.AdDAO;
import fi.jonix.huutonet.domain.model.dao.CustomerDAO;
import fi.jonix.huutonet.domain.model.dao.LoginNameDAO;
import fi.jonix.huutonet.domain.model.dao.MarketDAO;
import fi.jonix.huutonet.domain.model.dao.MessageDAO;
import fi.jonix.huutonet.domain.model.dao.SellerMarketDAO;
import fi.jonix.huutonet.domain.model.dao.UserDAO;
import fi.jonix.huutonet.email.EmailSender;
import fi.jonix.huutonet.exception.CriticalSectionInfo;
import fi.jonix.huutonet.exception.MarketLoggedOnAsWrongUserException;
import fi.jonix.huutonet.exception.MarketTemporarilyDownException;
import fi.jonix.huutonet.exception.ProcessEmailLaterException;
import fi.jonix.huutonet.exception.ProductNotFoundAtProviderException;
import fi.jonix.huutonet.exception.ProviderTemporarilyDownException;
import fi.jonix.huutonet.exception.SoldOutAtProviderException;
import fi.jonix.huutonet.exception.UnprocessableEmailException;
import fi.jonix.huutonet.market.SellerEmailFilter;
import fi.jonix.huutonet.order.OrderCreator;
import fi.jonix.huutonet.tools.StringUtilities;

/**
 * This class takes care of emails coming from mikko.
 * 
 * @author john
 *
 */
@Component(value = "mikkoProcessor")
public class MikkoProcessor extends SellerEmailFilter {

	public static final Logger logger = Logger.getLogger(MikkoProcessor.class);

	private static final String ORDER_RECEIVED_ID_STRING = "on lisätty Mikko.fi -palveluun";
	private static final String QUESTION_ID_STRING = "Voit vastata kysymykseen alla olevan osoitteen kautta";
	private static final String USER_COMMENT_ID_STRING = "on käyty kommentoimassa seuraavasti";
	private static final String ORDER_CLOSED_UNSUCCESSFULLY_ID_STRING = "Valitettavasti huutokauppaan ei tehty yhtään huutoa.";
	private static final String WON_BID_ID_STRING = "Huuto ylitti kohteelle määrittelemäsi hintavarauksen";

	//private static final String MIKKO_EMAIL_ADDRESS = "mikko-asiakaspalvelu@mikko.fi";
	private static final String MIKKO_EMAIL_ADDRESS = "asiakaspalvelu@myyjaosta.com";
	
	@Autowired
	private MikkofiLister mikkoLister;
	
	@Autowired
	private MarketDAO marketDAO;
	
	@Autowired
	private AdDAO adDAO;
	
	@Autowired
	private UserDAO userDAO;
	
	@Autowired
	private SellerMarketDAO sellerMarketDAO;
	
	@Autowired
	private MessageDAO messageDAO;
	
	@Autowired
	private CustomerDAO customerDAO;
	
	@Autowired
	private LoginNameDAO loginNameDAO;
	
	/* Processes ad received messages from mikko. This method sets ad.confirmed to true. */
	private boolean processAdReceived(String contents, String subject, Seller seller) throws Exception {
		if (subject.indexOf("julkaistu Mikko.fi-palvelussa") < 0) {
			return false;
		}
		String marketSalesId = getFieldValue(contents,"Ilmoituksesi");
		marketSalesId = marketSalesId.replace("(","").replace(")","");
		logger.debug("marketSalesId = " + marketSalesId);
		logger.info("Ad received notification received from mikko.fi for market sales id \"" + marketSalesId + "\"");
		Market market = marketDAO.getByName(Market.MIKKO_MARKET_NAME);
		if (market == null) {
			throw new Exception("market " + Market.MIKKO_MARKET_NAME + " not found in database.");
		}
		Ad ad = adDAO.getByMarketSalesId(marketSalesId,market,seller);
		if (ad == null) {
			/* Now and then it happens that mikko sends out an email so quickly after listing that
			 * the listing thread hasn't yet stored the new ad to the database. In those cases it works
			 * if we try again a little later to process the same email. */
			throw new ProcessEmailLaterException("ad.marketSalesId " + marketSalesId + " not found in database (for mikko).");
		}
		ad.setConfirmed(true);
		adDAO.update(ad);
		adDAO.flush(); //do a flush in order to catch database errors right away
		return true;
	}

	/* Processes messages from mikko telling that the target is closed and that somebody
	 * bought the product (bid is won). The ad.dateFromMarket field is set to the current
	 * date and a new row is added to the order table. */
	private boolean processWonBidOrBuyOut(String contents, Seller seller) throws Exception {
		//String marketSalesId = getFieldValue(contents,"Mikossa myymäsi kohde");
		String marketSalesId = getFieldValue(contents,"MyyJaOsta.comissa myymäsi kohde");
		if (marketSalesId.endsWith(",")) {
			marketSalesId = marketSalesId.replace(",","");
		}
		Long currency = null;
		if (!matchEmailContents(contents,"EUR teki nimimerkki")) {
			throw new UnprocessableEmailException("Unknown currency for mikko processWonBidOrBuyOut bid.");
		} else {
			currency = Currency.EURO;
		}
		logger.debug("marketSalesId = " + marketSalesId);
		BigDecimal finalSalesPriceBD = new BigDecimal(getFieldValue(contents,"Voittohuudon -"));
		logger.debug("finalSalesPrice = " + finalSalesPriceBD);
		VirtualCoin finalSalesPriceMC = new VirtualCoin(currency,finalSalesPriceBD);
		logger.info("Won bid notification received from mikko for item number \"" + marketSalesId + "\"");

		String loginName = getFieldValue(contents,"Nimimerkki:");
		logger.debug("loginName = " + loginName);

		/* Some people have several first names, and there can be several spaces between the names. */
		String names = getFieldValueToEndOfRow(contents,"Nimi: ");
		logger.debug("names = " + names);
		String firstName = "";
		String lastName = "";
		if (names == null) {
			return false;
		} else if (!names.trim().equals("")) {
			String names2 = StringUtilities.trimMultipleSpaces(names).trim();
			int spInd = names2.lastIndexOf(" ");
			firstName = names2.substring(0,spInd);
			lastName = names2.substring(spInd+1);
		}
		logger.debug("firstName = " + firstName + ", lastName = " + lastName);

		String address = getFieldValueToEndOfRow(contents,"Katuosoite: ");
		logger.debug("address = " + address);
		String postCode = getFieldValueToEndOfRow(contents,"Postinumero: ");
		logger.debug("postCode = " + postCode);
		String city = getFieldValueToEndOfRow(contents,"Postitoimipaikka: ");
		logger.debug("city = " + city);
		String phone = getFieldValueToEndOfRow(contents,"Matkapuhelinnro: ");
		logger.debug("phone1 = " + phone);
		if (phone == null || phone.trim().equals("")) {
			phone = getFieldValueToEndOfRow(contents,"Muu puhelinnro: ");
			logger.debug("phone2 = " + phone);
		}
		String email = getFieldValueToEndOfRow(contents,"Sähköpostiosoite: ");
		logger.debug("email = " + email);
		
		Ad ad = OrderCreator.createNewOrders(firstName,lastName,address,postCode,city,
				email,phone,loginName,Market.MIKKO_MARKET_NAME,marketSalesId,seller,1,finalSalesPriceMC);
		if (ad.getConfirmed().booleanValue() == false) {
			//sometimes the confirmation message is lost on the way - here we know that the ad was actually listed
			ad.setConfirmed(true);
		}
		ad.setDateFromMarket(new Date());
		return true;
	}
	
	/* Processes public questions (user comments) from mikko. */
	private boolean processPublicQuestion(String contents, Seller seller) throws Exception {
		String itemNumber = getFieldValue(contents,"Mikko.fi-palvelussa olevaa ilmoitustasi");
		logger.debug("itemNumber = " + itemNumber);
		String itemUrl = getFieldValue(contents,"katsomassa kommenttia kohteesi ilmoitussivulla osoitteessa:");
		if (itemUrl.endsWith(".")) {
			itemUrl = itemUrl.substring(0,itemUrl.length()-1);
		}
		logger.debug("itemUrl = " + itemUrl);
		int startIndex = contents.indexOf("Aihe: ");
		if (startIndex < 0) {
			return false;
		}
		String str = contents.substring(startIndex + "Aihe: ".length());
		int endIndex = str.indexOf("Voit käydä katsomassa kommenttia kohteesi");
		if (endIndex < 0) {
			return false;
		}
		String comment = str.substring(0,endIndex);
		logger.debug("comment = " + comment);
		int newRowIndex = comment.indexOf("\n");
		if (newRowIndex < 0) {
			return false;
		}

		String subject = StringUtilities.replaceNewLinesWithSpace(comment.substring(0,newRowIndex)).trim();
		logger.debug("subject = " + subject);
		String question = StringUtilities.replaceNewLinesWithSpace(comment.substring(subject.length())).trim();
		logger.debug("question = " + question);
		
		String forwardPublicQuestionsTo = seller.getForwardPublicQuestions();
		if (forwardPublicQuestionsTo == null) {
			forwardPublicQuestionsTo = seller.getUser().getEmailAddress();
		}
		String newEmail = "<ANSWER></ANSWER>\n\n"
			+ "<ACTION></ACTION>\n"
			+ "<SUBJECT>" + subject + "</SUBJECT>\n\n"
			+ "<QUESTION>" + question + "</QUESTION>\n\n"
			+ "<INFORMATION>The above public question has arrived\n"
			+ "By answering to this email the question will be automatically answered.\n\n"
			+ "</INFORMATION>\n\n\n"
			+ "<ANSWER_AT_ADDRESS>" + itemUrl + "</ANSWER_AT_ADDRESS>\n\n\n"
			+ "<MARKET_SALES_ID>" + itemNumber + "</MARKET_SALES_ID>\n\n\n"
			+ "<OLD_EMAIL>" + contents + "</OLD_EMAIL>";
		EmailSender.sendEmailBatch(seller.getSystemEmail(),forwardPublicQuestionsTo,
				"Public question from Mikko (ad " + itemNumber + ") - " + seller.getSignature(),newEmail);
		logger.info("Public question from Mikko received.");
		return true;
	}
	
	private boolean processAnswerToPublicQuestion(String contents, String operatorEmailAddress, Seller seller) throws Exception {
		String answer = getTagValue(contents, "ANSWER");
		if (answer == null) {
			return false;
		}
		logger.debug("answer = " + answer);
		String answerAddress = getTagValue(contents, "ANSWER_AT_ADDRESS");
		if (answerAddress == null) {
			return false;
		}
		logger.debug("answerAddress = " + answerAddress);
		String marketSalesId = getTagValue(contents, "MARKET_SALES_ID");
		if (marketSalesId == null) {
			return false;
		}
		logger.debug("marketSalesId = " + marketSalesId);
		Market market = marketDAO.getByName(Market.MIKKO_MARKET_NAME);
		if (market == null) {
			throw new Exception("market " + Market.MIKKO_MARKET_NAME + " not found in database.");
		}
		Ad ad = adDAO.getByMarketSalesId(marketSalesId,market,seller);
		if (ad == null) {
			throw new UnprocessableEmailException("ad.marketSalesId " + marketSalesId + " not found in database (for mikko).");
		}
		String question = getTagValue(contents, "QUESTION");
		if (question == null) {
			return false;
		}
		logger.debug("question = " + question);
		logger.info("Answer to public question from Mikko received.");
		/*String subject = getTagValue(contents, "SUBJECT");
		if (subject == null) {
			return false;
		}
		logger.debug("subject = " + subject);*/

		/* Make a check in order to avoid sending empty answers to the customers. */
		boolean answered = true;
		if (answer.trim().equals("")) {
			String newEmail = "You tried to send an empty answer to a public question for ad " + ad.getId() + ".\n"
				+ "Remember that the answer MUST be within the <ANSWER></ANSWER> tag!\n\n"
				+ "Please answer again!!";
			EmailSender.sendEmailBatch(seller.getSystemEmail(),operatorEmailAddress,"Cannot send empty answer to public question from Mikko",newEmail);
		} else {
			answered = false;
			try {
				SellerMarket sellerMarket = sellerMarketDAO.getBySellerAndMarket(seller,market);
				mikkoLister.login(sellerMarket);
				answered = mikkoLister.answerPublicQuestion(ad, question, answer, answerAddress, sellerMarket);
			} catch (MarketTemporarilyDownException e) {
				throw new ProcessEmailLaterException("Cannot answer public question because market is down.");
			} catch (MarketLoggedOnAsWrongUserException e) {
				throw new ProcessEmailLaterException("Cannot answer public question because market is in use by a different seller.");
			}
			mikkoLister.logout();
		}
		if (!answered) {
			throw new UnprocessableEmailException("Couldn't answer public question from mikko");
		}
		return answered;
	}
	
	/* Processes private questions from mikko. */
	private boolean processPrivateQuestion(String contents, boolean loggedOn, Seller seller) throws Exception {
		StringTokenizer st = new StringTokenizer(contents);
		String userId = st.nextToken();
		logger.debug("userId = " + userId);
		int startIndex = contents.indexOf("=============");
		if (startIndex < 0) {
			return false;
		}
		String str = contents.substring(startIndex + "=============".length());
		int endIndex = str.indexOf("=============");
		if (endIndex < 0) {
			return false;
		}
		String question = str.substring(0,endIndex);
		logger.debug("question = " + question);
		String answerAddress = getRowValue(contents,"Voit vastata kysymykseen alla olevan osoitteen kautta:",1);
		logger.debug("answerAddress = " + answerAddress);
		
		/* Try to get information about the orders for this user (if any). */
		Customer customer = null;
		if (!loggedOn) {
			//When non-logged on users send a question the userId is the email address.
			customer = customerDAO.getCustomerByEmailAddress(userId);
		} else {
			//For logged on users the userId is the user name
			LoginName ln = loginNameDAO.getByLoginNameAndMarket(userId, marketDAO.getByName(Market.MIKKO_MARKET_NAME));
			if (ln != null) {
				customer = ln.getCustomer();
			}
		}
		StringBuffer strBuf = new StringBuffer();
		if (customer != null) {
			strBuf = getOrderListForCustomer(customer, seller, customer.getEmail(), true);
		}
		
		String forwardPrivateQuestionsTo = seller.getForwardPrivateQuestions();
		if (forwardPrivateQuestionsTo == null) {
			forwardPrivateQuestionsTo = seller.getUser().getEmailAddress();
		}
		String newEmail = "<ANSWER>\nMoi,\n\n\n\nYstävällisin terveisin,\n" + seller.getSignature() + "\n</ANSWER>\n\n"
			+ "<ACTION></ACTION>"
			+ "<QUESTION>" + question + "</QUESTION>\n\n"
			+ "<INFORMATION>The above private question has arrived\n"
			+ "By answering to this email the question will be automatically answered.\n\n"
			+ strBuf.toString() + "</INFORMATION>\n\n\n"
			+ (loggedOn ? "<ANSWER_AT_ADDRESS>" + answerAddress + "</ANSWER_AT_ADDRESS>\n\n\n" : "")
			+ "<USER_ID>" + userId + "</USER_ID>\n\n\n"
			+ "<OLD_EMAIL>" + contents + "</OLD_EMAIL>";
		EmailSender.sendEmailBatch(seller.getSystemEmail(),forwardPrivateQuestionsTo,
				"Private question from Mikko (" + userId + ") - " + seller.getSignature(),newEmail);
		logger.info("Private question from Mikko received.");
		return true;
	}
	
	private boolean processAnswerToPrivateQuestion(String contents, String operatorEmailAddress, Seller seller) throws Exception {
		String answer = getTagValue(contents, "ANSWER");
		if (answer == null) {
			return false;
		}
		boolean loggedOn = true;
		logger.debug("answer = " + answer);
		String answerAddress = getTagValue(contents, "ANSWER_AT_ADDRESS");
		if (answerAddress == null) {
			loggedOn = false;
		} else {
			answerAddress = StringUtilities.replaceNewLinesWithSpace(answerAddress);
		}
		logger.debug("answerAddress = " + answerAddress);
		String userId = getTagValue(contents, "USER_ID");
		if (userId == null) {
			return false;
		}
		userId = StringUtilities.replaceNewLinesWithSpace(userId).replaceAll(" ","");
		logger.debug("userId = " + userId);
		String question = getTagValue(contents, "QUESTION");
		if (question == null) {
			return false;
		}
		logger.debug("question = " + question);

		/* Make a check in order to avoid sending empty answers to the customers. */
		boolean answered = true;
		if (StringUtilities.sloppyMatch("",answer.replaceAll("Moi","").replaceAll("Ystävällisin terveisin","").replaceAll(seller.getSignature(),""))) {
			String newEmail = "You tried to send an empty answer to a private question from " + userId + ".\n"
				+ "Remember that the answer MUST be within the <ANSWER></ANSWER> tag!\n\n"
				+ "Please answer again!!";
			EmailSender.sendEmailBatch(seller.getSystemEmail(),operatorEmailAddress,"Cannot send empty answer to private question from Mikko",newEmail);
		} else if (loggedOn) {
			try {
				Market market = marketDAO.getByName(Market.MIKKO_MARKET_NAME);
				SellerMarket sellerMarket = sellerMarketDAO.getBySellerAndMarket(seller,market);
				mikkoLister.login(sellerMarket);
				answered = mikkoLister.answerPrivateQuestion(null, question, answer, answerAddress, sellerMarket);
			} catch (MarketTemporarilyDownException e) {
				throw new ProcessEmailLaterException("Cannot answer private question because market is down.");
			} catch (MarketLoggedOnAsWrongUserException e) {
				logger.debug("Logged on as wrong user: ", e);
				throw new ProcessEmailLaterException("Cannot answer private question because market is in use by a different seller.");
			}
			mikkoLister.logout();
		} else {
			/* For non-logged in users we only get the email address, so then answer by sending an email. */
			String answerEmail = answer + "\n\n\n----------------\n" + question;
			EmailSender.sendEmailBatch(seller.getSystemEmail(),userId,"Vastaus kyselyyn",answerEmail);
		}
		if (!answered) {
			throw new UnprocessableEmailException("Couldn't answer private question from huutonet");
		}
		logger.info("Answer to private question from Mikko received.");
		return answered;
	}
	
	/* Processes messages from mikko telling that the target is closed and noone bought
	 * the product. The only thing it does is to set the ad.dateFromMarket field to the
	 * current date. */
	private boolean processAdClosedFail(String contents, Seller seller) throws Exception {
		String rePublishUrl = getRowValue(contents,"tai alla olevan osoitteen kautta.",2);
		logger.debug("rePublishUrl = " + rePublishUrl);
		//String itemNumber = rePublishUrl.replaceAll("http://www.mikko.fi/uudelleenjulkaise/", "");
		String itemNumber = rePublishUrl.replaceAll("http://www.myyjaosta.com/uudelleenjulkaise/", "");
		logger.debug("itemNumber = " + itemNumber);

		logger.info("Ad unsuccessfully closed notification received from mikko for item number \"" + itemNumber + "\"");
		Market market = marketDAO.getByName(Market.MIKKO_MARKET_NAME);
		if (market == null) {
			throw new Exception("market " + Market.MIKKO_MARKET_NAME + " not found in database.");
		}
		Ad ad = adDAO.getByMarketSalesId(itemNumber,market,seller);
		if (ad == null) {
			/* Check if this is an ad that has been moved to a different seller after the listing
			 * but before the closing of the ad. */
			ad = adDAO.getByMarketSalesIdWithoutSeller(itemNumber, market);
			if (ad != null) {
				String warnMsg = "Ad " + ad.getId() + " has been moved from seller " + seller.getName()
					+ " to seller " + ad.getAdTemplate().getSeller().getName() + " after the listing.";
				logger.warn(warnMsg);
				//EmailSender.sendOperationProblemReport("MikkoProcessor.processAdClosedFail",warnMsg);
			} else {
				throw new UnprocessableEmailException("ad.marketSalesId " + itemNumber + " not found in database (for mikko).");
			}
		}
		if (ad.getConfirmed().booleanValue() == false) {
			//sometimes the confirmation message is lost on the way - here we know that the ad was actually listed
			ad.setConfirmed(true);
		}
		ad.setDateFromMarket(new Date());
		adDAO.update(ad);
		adDAO.flush(); //do a flush in order to catch database errors right away
		return true;
	}

	@Override
	public boolean processSellerEmail(Message message, Seller seller,
			CriticalSectionInfo processingInfo) throws Exception {
		/* Check if it's an ad received message from Mikko. */
		String contents = matchEmail(message,MIKKO_EMAIL_ADDRESS,"Ilmoitus ",true,ORDER_RECEIVED_ID_STRING);
		if (contents != null) {
			return processAdReceived(contents,message.getSubject(),seller);
		}

		/* Check if it's a "buy" from Mikko. */
		/*contents = matchEmail(message,emailAddress,"Myyntikohteesta",true,WON_BUY_ID_STRING);
		if (contents != null) {
			return processBuy(contents);
		}*/
		
		/* Check if it's a won bid / buy out (they look the same) from Mikko. */
		contents = matchEmail(message,MIKKO_EMAIL_ADDRESS,"Huutokauppa ",true,WON_BID_ID_STRING);
		if (contents != null) {
			return processWonBidOrBuyOut(contents,seller);
		}

		/* Check if it's a private question ("question from user") from Mikko. */
		contents = matchEmail(message,MIKKO_EMAIL_ADDRESS,"Kohteesta",true,QUESTION_ID_STRING);
		if (contents != null) {
			return processPrivateQuestion(contents,true,seller);
		}
		String subject = message.getSubject();
		//For non-logged in users it won't match the QUESTION_ID_STRING, so check separately
		//for questions from non-logged in users.
		if (subject != null && subject.startsWith("Kohteesta") && subject.endsWith("on esitetty kysymys")) {
			contents = matchEmail(message,MIKKO_EMAIL_ADDRESS,"Kohteesta",true,null);
			if (contents != null) {
				return processPrivateQuestion(contents,false,seller);
			}
		}

		/* Check if it's a public question ("user comment") from Mikko. */
		contents = matchEmail(message,MIKKO_EMAIL_ADDRESS,"Mikko.fi-palvelussa olevaa ilmoitustasi",true,USER_COMMENT_ID_STRING);
		if (contents != null) {
			return processPublicQuestion(contents,seller);
		}

		/* Check if it's an ad closed unsuccessfully message from Mikko that didn't get any bids. */
		contents = matchEmail(message,MIKKO_EMAIL_ADDRESS,"Huutokauppa ",true,ORDER_CLOSED_UNSUCCESSFULLY_ID_STRING);
		if (contents != null) {
			return processAdClosedFail(contents,seller);
		}

		/* Check if this is an email from operator that has to do with Mikko. */
		List<User> operators = userDAO.getOperators();
		for (User oper : operators) {
			String email = oper.getEmailAddress();
			
			/* Check if an answer to a private question from an operator. */
			contents = matchEmail(message,email,"Private question from Mikko",false,null);
			if (contents != null) {
				return processAnswerToPrivateQuestion(contents,email,seller);
			}

			/* Check if an answer to a public question from an operator. */
			contents = matchEmail(message,email,"Public question from Mikko",false,null);
			if (contents != null) {
				return processAnswerToPublicQuestion(contents,email,seller);
			}
		}

		return false;
	}

}
