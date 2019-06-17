package fi.jonix.huutonet.domain.model.dao;

import java.util.Calendar;
import java.util.List;

import javax.sql.DataSource;

import org.dbunit.DataSourceDatabaseTester;
import org.dbunit.IDatabaseTester;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.xml.FlatXmlDataSet;
import org.hibernate.SessionFactory;
import org.junit.After;
import org.junit.Before;
import org.springframework.test.AbstractDependencyInjectionSpringContextTests;
import org.springframework.test.AbstractTransactionalDataSourceSpringContextTests;

import fi.jonix.huutonet.domain.model.Order;

public class OrderDAOImplTest extends AbstractTransactionalDataSourceSpringContextTests {
	
	private DataSource dataSource;
	private IDatabaseTester databaseTester;
	private OrderDAO orderDAO;
	
	protected String[] getConfigLocations() {
		return new String[] {"classpath:/context/applicationContext.xml"};
	}
	
	public void testLoadAll() throws Exception{
		List<Order> orders = this.orderDAO.loadAll();
		assertEquals(10, orders.size());
	}
	
	/*public void testGetPaidOrdersWhereCustomerIsInformed() throws Exception{
		List<Order> orders = this.orderDAO.getPaidOrdersWhereCustomerIsInformed();
		assertEquals(1, orders.size());
	}
	
	public void testGetPaidOrdersWhereCustomerIsNotInformed() throws Exception{
		List<Order> orders = this.orderDAO.getPaidOrdersWhereCustomerIsNotInformed();
		assertEquals(1, orders.size());
	}*/
	
	/*public void testGetAllOrdersPaidOrLaterStates() throws Exception{
		Calendar cal1 = Calendar.getInstance();
		cal1.set(2009,3,0,10,0);
		Calendar cal2 = Calendar.getInstance();
		cal2.set(2011,3,0,10,0);
		List<Order> orders = this.orderDAO.getAllOrdersPaidOrLaterStates(cal1.getTime(), cal2.getTime());
		assertEquals(7, orders.size());
	}*/
	
	/*public void testGetOrdersWithoutFeedbackInPaidOrLaterStates() throws Exception{
		List<Order> orders = this.orderDAO.getOrdersWithoutFeedbackInPaidOrLaterStates();
		assertEquals(5, orders.size());
	}*/
	
	/*
	public void testStateUpdate() throws Exception{
		Order order = this.orderDAO.get(1L);
		int statesSize1 = order.getStateChanges().size();
		order.updateState(Order.ORDER_STATE_CLOSED);
		this.orderDAO.save(order);
		
		Order order2 = this.orderDAO.get(1L);
		int statesSize2 = order2.getStateChanges().size();
		
		assertEquals(1, statesSize2 - statesSize1);
		
		int closedState = Order.ORDER_STATE_CLOSED;
		
		assertEquals(closedState, order2.getState().intValue());
	}
	*/
	
	@Before
	public void onSetUp() throws Exception{
		this.databaseTester = new DataSourceDatabaseTester(this.getDataSource());
		IDataSet dataSet = new FlatXmlDataSet(getClass().getResource("orderDAOTest.xml"));
		this.databaseTester.setDataSet(dataSet);
		this.databaseTester.onSetup();
	}

	@After
	public void onTearDown() throws Exception{
		this.databaseTester.onTearDown();
	}
	
	public void setDataSource(DataSource dataSource) {
		this.dataSource = dataSource;
	}

	public DataSource getDataSource() {
		return dataSource;
	}

	public void setOrderDAO(OrderDAO orderDAO) {
		this.orderDAO = orderDAO;
	}

	public OrderDAO getOrderDAO() {
		return orderDAO;
	}

}
