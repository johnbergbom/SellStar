package fi.jonix.huutonet.market;

import java.util.List;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import fi.jonix.huutonet.domain.model.Order;
import fi.jonix.huutonet.domain.model.SellerMarket;
import fi.jonix.huutonet.domain.model.dao.OrderDAO;

@Component(value = "feedbackReader")
public class FeedbackReader {

	public static final Logger logger = Logger.getLogger(FeedbackReader.class);

	@Autowired
	private OrderDAO orderDAO;

	public void readFeedback(MarketRobot lister, SellerMarket sellerMarket) throws Exception {
		/*if (!lister.isLoggedIn()) {
			lister.login(sellerMarket);
		}*/
		/* Read received feedbacks and close orders if possible. */
		List<OrderFeedback> list = lister.readFeedback(sellerMarket);
		for (OrderFeedback of : list) {
			for (Order order : of.getOrderList()) {
				if (!order.getOrderBundle().getSeller().equals(sellerMarket.getSeller())) {
					throw new Exception("Order " + order.getId() + " belongs to the wrong seller (readFeedback)");
				}
				if (!order.getMarket().getId().equals(sellerMarket.getMarket().getId())) {
					throw new Exception("Order " + order.getId() + " belongs to the wrong market (readFeedback)");
				}
				if (order.getState().intValue() >= Order.STATE_SHIPPED_BY_PROVIDER
						&& order.getState().intValue() <= Order.STATE_FETCHED_BY_CUSTOMER) {
					if (of.getFeedbackType() == OrderFeedback.POSITIVE) {
						/* If tracking is in use we shouldn't normally close the order until it's
						 * fetched by the customer. However sometimes the post office screws up updating
						 * the status, so if the order has been in this state for at least a month,
						 * then close the order anyway. */
						long daysInThisState = (System.currentTimeMillis()
								- order.getLatestStateChange().getUpdateDate().getTime()) / (1000*3600*24);
						if (order.getTracking() != null && order.getTracking().getFollow() && daysInThisState < 31) {
							logger.debug("Getting positive feedback for order " + order.getId()
									+ " but not closing order since tracking is in use.");
						} else {
							logger.info("Closing order " + order.getId() + " due to positive feedback");
							order.setStateWithHistory(Order.STATE_CLOSED);
							orderDAO.update(order);
						}
					} else {
						//Note: Here we could possibly send an email to the operator and then make it possible for
						//him to reply to the negative feedback by simply answering to the email.
						logger.info("Left order status unchanged due to negative feedback for order " + order.getId());
					}
				}
			}
		}
	}
	
}
