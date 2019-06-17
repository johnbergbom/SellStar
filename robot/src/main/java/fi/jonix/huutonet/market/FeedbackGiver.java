package fi.jonix.huutonet.market;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import fi.jonix.huutonet.domain.model.Order;
import fi.jonix.huutonet.domain.model.SellerMarket;
import fi.jonix.huutonet.domain.model.dao.OrderDAO;
import fi.jonix.huutonet.exception.LoggedOutFromMarketException;
import fi.jonix.huutonet.exception.MarketLoggedOnAsWrongUserException;
import fi.jonix.huutonet.exception.MarketTemporarilyDownException;

@Component(value = "feedbackGiver")
public class FeedbackGiver {

	public static final Logger logger = Logger.getLogger(FeedbackGiver.class);

	@Autowired
	private OrderDAO orderDAO;

	public boolean givePositiveFeedback(List<Order> ordersWithoutFeedback, MarketRobot lister, SellerMarket sellerMarket) throws LoggedOutFromMarketException, MarketLoggedOnAsWrongUserException {
		/* There might be several orders pointing to the same ad, so order the
		 * orders according to what ads they belong to. */
		List<Order> sameAdOrders = new ArrayList<Order>();
		Long adId = null;
		for (Order order : ordersWithoutFeedback) {
			orderDAO.refresh(order);
			if (adId == null) {
				adId = order.getAd().getId();
				sameAdOrders.add(order);
			} else if (order.getAd().getId().equals(adId)) {
				sameAdOrders.add(order);
			}
		}
		ordersWithoutFeedback.removeAll(sameAdOrders);
		
		if (sameAdOrders.size() > 0) {
			logger.debug("Giving feedback for order " + sameAdOrders.get(0).getId()
					+ " (ad " + sameAdOrders.get(0).getAd().getId() + ").");
			lister.givePositiveFeedback(sameAdOrders,sellerMarket);
			return true;
		}
		return false;
	}
	
}
