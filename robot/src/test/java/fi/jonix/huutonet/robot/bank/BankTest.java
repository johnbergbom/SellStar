package fi.jonix.huutonet.robot.bank;

import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.sql.DataSource;

import org.dbunit.DataSourceDatabaseTester;
import org.dbunit.IDatabaseTester;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.xml.FlatXmlDataSet;
import org.springframework.test.AbstractTransactionalDataSourceSpringContextTests;

import fi.jonix.huutonet.domain.model.Order;
import fi.jonix.huutonet.domain.model.Transaction;
import fi.jonix.huutonet.domain.model.dao.MarketDAO;
import fi.jonix.huutonet.domain.model.dao.OrderDAO;
import fi.jonix.huutonet.bank.BankHelper;
import fi.jonix.huutonet.bank.FaultyPaymentFixer;

public class BankTest extends AbstractTransactionalDataSourceSpringContextTests {
	private DataSource dataSource;
	private IDatabaseTester databaseTester;
	private MarketDAO marketDAO;
	private FaultyPaymentFixer faultyPaymentFixer;
	//private SettingsDAO settingsDAO;
	private OrderDAO orderDAO;
	private BankHelper bankHelper;
	
	public void setDataSource(DataSource dataSource) {
		this.dataSource = dataSource;
	}
	
	public void setMarketDAO(MarketDAO marketDAO) {
		this.marketDAO = marketDAO;
	}
	
	public void setFaultyPaymentFixer(FaultyPaymentFixer faultyPaymentFixer) {
		this.faultyPaymentFixer = faultyPaymentFixer;
	}
	
	/*public void setSettingsDAO(SettingsDAO settingsDAO) {
		this.settingsDAO = settingsDAO;
	}*/
	
	public void setOrderDAO(OrderDAO orderDAO) {
		this.orderDAO = orderDAO;
	}
	
	public void setBankHelper(BankHelper bankHelper) {
		this.bankHelper = bankHelper;
	}
	
	protected void onSetUp() throws Exception {
		super.onSetUp();
		this.databaseTester = new DataSourceDatabaseTester(this.dataSource);
		IDataSet dataSet = new FlatXmlDataSet(getClass().getResource("bankTest.xml"));
		this.databaseTester.setDataSet(dataSet);
		this.databaseTester.onSetup();
	}
	
	protected void onTearDown() throws Exception {
		super.onTearDown();
		this.databaseTester.onTearDown();
	}
	
	protected String[] getConfigLocations() {
		return new String[] {"classpath:/dao/applicationContext-test.xml"};
	}
	
	public void testKickCustomers() throws Exception {
		/*List<EmailWrapper> emailList = faultyPaymentFixer.getEmailsToSend(false);
		//assertEquals(1,emailList.size());
		if (emailList != null && emailList.size() > 0) {
			/*for (EmailWrapper email : emailList) {
				System.out.println("To     : " + email.getAddress());
				System.out.println("Subject: " + email.getSubject());
				System.out.println("Contents:\n" + email.getText());
				System.out.println("-------------------------------------");
			}
		} else {
			System.out.println("No customer needs to be kicked.");
		}*/
	}
		
	public void testFixFaultyTransactions() throws Exception {
		//TODO: update this testcase to actually test fixFaultyTransactions in a sensible way
		int nbrFaulty = faultyPaymentFixer.fixFaultyTransactions(false);
		//assertEquals(1,nbrFaulty);
	}
	
	public void testFixFaultyPayments() throws Exception {
		/*List<Transaction> trs = new ArrayList<Transaction>();
		DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		
		/* This one already exists in the database, so this one shouldn't get stored again. /
		trs.add(new Transaction(df.parse("2010-04-06 00:00:00"), "SOME NAME", "VIITESIIRTO", "11 22222 33333", new BigDecimal("5.7"), "blabla"));
		
		/* Pay 5.00 euros for some@email.fi who has ordered the orders 1-4 whose total price
		 * is 4.0 + 0.04 = 4.04 euro including shipping. The result should then be:
		 * order.id = 1: state = customer paid, paidTooMuch = true
		 * order.id = 2: state = customer paid, paidTooMuch = false
		 * order.id = 3: state = customer paid, paidTooMuch = false
		 * order.id = 4: state = customer paid, paidTooMuch = false /
		trs.add(new Transaction(new Date(), "john", "VIITESIIRTO", "111111111111", new BigDecimal("5.00"), "111111111111")); //order.id = 1

		/* Now fix the faulty payments. /
		int nbrFixed = faultyPaymentFixer.fixFaultyPayments();
		System.out.println("nbrFixed = " + nbrFixed); //4 + 

		Order order1 = orderDAO.get(1L);
		Order order2 = orderDAO.get(2L);
		Order order3 = orderDAO.get(3L);
		Order order4 = orderDAO.get(4L);
		assertEquals(new Integer(Order.STATE_CUSTOMER_PAID),order1.getState());
		assertEquals(new Integer(Order.STATE_CUSTOMER_PAID),order2.getState());
		assertEquals(new Integer(Order.STATE_CUSTOMER_PAID),order3.getState());
		assertEquals(new Integer(Order.STATE_CUSTOMER_PAID),order4.getState());
		assertEquals(new Boolean(true),order1.getPaidTooMuch());
		assertEquals(new Boolean(false),order2.getPaidTooMuch());
		assertEquals(new Boolean(false),order3.getPaidTooMuch());
		assertEquals(new Boolean(false),order4.getPaidTooMuch());

		Order order6 = orderDAO.get(6L);
		Order order7 = orderDAO.get(7L);
		Order order8 = orderDAO.get(8L);
		assertEquals(new Integer(Order.STATE_CUSTOMER_PAID),order6.getState());
		assertEquals(new Integer(Order.STATE_CUSTOMER_PAID),order7.getState());
		assertEquals(new Integer(Order.STATE_CUSTOMER_PAID),order8.getState());
		assertEquals(new Boolean(false),order6.getPaidTooMuch());
		assertEquals(new Boolean(false),order7.getPaidTooMuch());
		assertEquals(new Boolean(false),order8.getPaidTooMuch());

		Order order9 = orderDAO.get(9L);
		Order order10 = orderDAO.get(10L);
		assertEquals(new Integer(Order.STATE_CUSTOMER_PAID),order9.getState());
		assertEquals(new Integer(Order.STATE_CUSTOMER_PAID),order10.getState());
		assertEquals(new Boolean(false),order9.getPaidTooMuch());
		assertEquals(new Boolean(false),order10.getPaidTooMuch());

		Order order11 = orderDAO.get(11L);
		Order order12 = orderDAO.get(12L);
		Order order13 = orderDAO.get(13L);
		Order order14 = orderDAO.get(14L);
		Order order15 = orderDAO.get(15L);
		/*System.out.println("order11.state = " + order11.getState() + ", order11.paidTooMuch = " + order11.getPaidTooMuch());
		System.out.println("order12.state = " + order12.getState() + ", order12.paidTooMuch = " + order12.getPaidTooMuch());
		System.out.println("order13.state = " + order13.getState() + ", order13.paidTooMuch = " + order13.getPaidTooMuch());
		System.out.println("order14.state = " + order14.getState() + ", order14.paidTooMuch = " + order14.getPaidTooMuch());
		System.out.println("order15.state = " + order15.getState() + ", order15.paidTooMuch = " + order15.getPaidTooMuch());/
		assertEquals(new Integer(Order.STATE_CUSTOMER_PAID),order11.getState());
		assertEquals(new Integer(Order.STATE_CUSTOMER_PAID),order12.getState());
		assertEquals(new Integer(Order.STATE_PAID_TOO_LITTLE_ERROR),order13.getState());
		assertEquals(new Integer(Order.STATE_PAID_TOO_LITTLE_ERROR),order14.getState());
		assertEquals(new Integer(Order.STATE_PAID_TOO_LITTLE_ERROR),order15.getState());
		assertEquals(new Boolean(true),order11.getPaidTooMuch());
		assertEquals(new Boolean(false),order12.getPaidTooMuch());
		assertEquals(new Boolean(false),order13.getPaidTooMuch());
		assertEquals(new Boolean(false),order14.getPaidTooMuch());
		assertEquals(new Boolean(false),order15.getPaidTooMuch());
	
		Order order16 = orderDAO.get(16L);
		Order order17 = orderDAO.get(17L);
		assertEquals(new Integer(Order.STATE_CUSTOMER_PAID),order16.getState());
		assertEquals(new Integer(Order.STATE_CUSTOMER_PAID),order17.getState());
		assertEquals(new Boolean(false),order16.getPaidTooMuch());
		assertEquals(new Boolean(false),order17.getPaidTooMuch());

		Order order18 = orderDAO.get(18L);
		Order order19 = orderDAO.get(19L);
		assertEquals(new Integer(Order.STATE_CUSTOMER_PAID),order18.getState());
		assertEquals(new Integer(Order.STATE_PAID_TOO_LITTLE_ERROR),order19.getState());
		assertEquals(new Boolean(true),order18.getPaidTooMuch());
		assertEquals(new Boolean(false),order19.getPaidTooMuch());

		Order order20 = orderDAO.get(20L);
		Order order21 = orderDAO.get(21L);
		assertEquals(new Integer(Order.STATE_CUSTOMER_PAID),order20.getState());
		assertEquals(new Integer(Order.STATE_CUSTOMER_PAID),order21.getState());
		assertEquals(new Boolean(true),order20.getPaidTooMuch());
		assertEquals(new Boolean(false),order21.getPaidTooMuch());

		Order order22 = orderDAO.get(22L);
		Order order23 = orderDAO.get(23L);
		assertEquals(new Integer(Order.STATE_CUSTOMER_PAID),order22.getState());
		assertEquals(new Integer(Order.STATE_CUSTOMER_PAID),order23.getState());
		assertEquals(new Boolean(false),order22.getPaidTooMuch());
		assertEquals(new Boolean(false),order23.getPaidTooMuch());*/
	}

}
