package fi.jonix.huutonet.robot.email.processor;

import java.sql.Date;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.test.AbstractDependencyInjectionSpringContextTests;

import fi.jonix.huutonet.domain.model.Ad;
import fi.jonix.huutonet.domain.model.AdTemplate;
import fi.jonix.huutonet.domain.model.Order;

public class EmailFromCustomerProcessorTest extends AbstractDependencyInjectionSpringContextTests{
	
	public void testGetOrderInformation() throws Exception {
		/*Collections.sort(orders);
		EmailFromCustomerProcessor processor = new EmailFromCustomerProcessor();
		System.out.println(processor.getOrderInformation(orders, "Voitetut huudot", Order.ORDER_STATE_WON_BID));
		*/
	}

}
