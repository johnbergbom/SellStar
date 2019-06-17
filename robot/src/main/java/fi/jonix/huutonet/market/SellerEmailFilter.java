package fi.jonix.huutonet.market;

import java.util.List;

import javax.mail.Message;

import fi.jonix.huutonet.domain.ApplicationContextPlaceholder;
import fi.jonix.huutonet.domain.model.Customer;
import fi.jonix.huutonet.domain.model.Order;
import fi.jonix.huutonet.domain.model.Seller;
import fi.jonix.huutonet.domain.model.SystemEmail;
import fi.jonix.huutonet.domain.model.dao.OrderDAO;
import fi.jonix.huutonet.email.processor.EmailFilter;
import fi.jonix.huutonet.exception.CriticalSectionInfo;
import fi.jonix.huutonet.order.OrderInformation;

/**
 * This class implements processSpecial and processProviderEmail and throws an
 * UnsupportedOperationException if any of those are called. Subclasses are supposed
 * to implement the processSellerEmail method.
 * 
 * @author john
 *
 */
public abstract class SellerEmailFilter extends EmailFilter {

	@Override
	public boolean processSpecial(Message message, Seller seller, int mailboxType, SystemEmail systemEmail,
			CriticalSectionInfo processingInfo) throws Exception {
		throw new UnsupportedOperationException("Use processSellerEmail instead.");
	}
	
	@Override
	public boolean processProviderEmail(Message message, SystemEmail systemEmail,
			CriticalSectionInfo processingInfo) throws Exception {
		throw new UnsupportedOperationException("Use processSellerEmail instead.");
	}
	
	protected StringBuffer getOrderListForCustomer(Customer customer, Seller seller, String email, boolean showOperatorInfo) {
		StringBuffer strBuf = new StringBuffer();
		OrderDAO orderDAO = (OrderDAO) ApplicationContextPlaceholder.applicationContext.getBean("orderDAO");
		List<Order> orderList = orderDAO.getOrdersByCustomer(customer,seller);
		//strBuf.append(OrderInformation.getCustomerAddressInfo(customer) + "\n");
		strBuf.append(OrderInformation.getOrderStockInfo(orderList,showOperatorInfo));
		return strBuf;
	}
	
}
