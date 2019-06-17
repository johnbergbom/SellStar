package fi.jonix.huutonet.market;

import java.util.List;

import fi.jonix.huutonet.domain.model.Order;

public class OrderFeedback {

	public static int POSITIVE = 1;
	public static int NEUTRAL = 2;
	public static int NEGATIVE = 3;
	
	private List<Order> orderList;
	private int feedbackType;
	
	public OrderFeedback(List<Order> orderList, int feedbackType) {
		this.orderList = orderList;
		this.feedbackType = feedbackType;
	}
	
	public List<Order> getOrderList() {
		return orderList;
	}
	
	public int getFeedbackType() {
		return feedbackType;
	}

}
