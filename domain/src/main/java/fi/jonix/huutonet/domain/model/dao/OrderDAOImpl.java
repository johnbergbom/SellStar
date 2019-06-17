package fi.jonix.huutonet.domain.model.dao;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.persistence.Table;

import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Restrictions;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.jdbc.core.simple.SimpleJdbcTemplate;
import org.springframework.orm.hibernate3.SessionFactoryUtils;

import fi.jonix.huutonet.domain.ApplicationContextPlaceholder;
import fi.jonix.huutonet.domain.model.AdTemplate;
import fi.jonix.huutonet.domain.model.Currency;
import fi.jonix.huutonet.domain.model.Customer;
import fi.jonix.huutonet.domain.model.Market;
import fi.jonix.huutonet.domain.model.Order;
import fi.jonix.huutonet.domain.model.OrderBundle;
import fi.jonix.huutonet.domain.model.Product;
import fi.jonix.huutonet.domain.model.Provider;
import fi.jonix.huutonet.domain.model.Seller;
import fi.jonix.huutonet.domain.model.SellerMarket;
import fi.jonix.huutonet.domain.model.StateChange;
import fi.jonix.huutonet.domain.model.Tracking;

public class OrderDAOImpl extends GenericDAOImpl <Order, Long> implements OrderDAO  {

	public Class<Order> getEntityClass() {
		return Order.class;
	}
	
	public List<Order> getByProviderProductId(String providerProdId, Provider provider) {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.createAlias("product", "p");
		criteria.add(Restrictions.eq("p.providerProdId",providerProdId));
		criteria.createAlias("orderBundle", "ob");
		criteria.add(Restrictions.eq("ob.provider",provider));
		//criteria.addOrder(org.hibernate.criterion.Order.asc("id"));
		return this.getHibernateTemplate().findByCriteria(criteria);
	}

	public int getNbrOrdersInBundle(OrderBundle orderBundle) {
		SimpleJdbcTemplate jdbcTemplate =
			new SimpleJdbcTemplate(SessionFactoryUtils.getDataSource(getSessionFactory()));
		Table table = AnnotationUtils.findAnnotation(Order.class, Table.class);
		return jdbcTemplate.queryForInt("SELECT count(*) FROM " + table.name() + " WHERE order_bundle_id = ?",orderBundle.getId());
	}

	public List<Order> getOrdersInBundle(OrderBundle orderBundle) {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.add(Restrictions.eq("orderBundle",orderBundle));
		return this.getHibernateTemplate().findByCriteria(criteria);
	}

	public List<Order> getOrdersInTrackingObject(Tracking tracking) {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.add(Restrictions.eq("tracking",tracking));
		return this.getHibernateTemplate().findByCriteria(criteria);
	}

	public Order getByReferenceNumberOnly(String customerReferenceNumber, Currency usedCustomerCurrency) {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.add(Restrictions.eq("customerReferenceNumber",customerReferenceNumber));
		criteria.createAlias("orderBundle", "ob");
		criteria.add(Restrictions.eq("ob.usedCustomerCurrency",usedCustomerCurrency));
		List<Order> list = this.getHibernateTemplate().findByCriteria(criteria);
		if (list != null && list.size() > 0) {
			/* There will never be more than one order here, because customerReferenceNumber
			 * has unique pattern. */
			return list.get(0);
		} 
		return null;
	}

	/**
	 * @param headline - Example: "aaa" fetches all strings equal to "aaa",
	 * "%aaa" fetches all strings ending with "aaa", "aaa%" fetches all strings
	 * starting with "aaa" and "%aaa%" fetches all strings having "aaa" somewhere
	 * in it.
	 */
	public List<Order> getBySimilarReferenceNumberOnly(String partialReferenceNumber, Currency usedCustomerCurrency) {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.add(Restrictions.like("customerReferenceNumber",partialReferenceNumber).ignoreCase());
		criteria.createAlias("orderBundle", "ob");
		criteria.add(Restrictions.eq("ob.usedCustomerCurrency",usedCustomerCurrency));
		return this.getHibernateTemplate().findByCriteria(criteria);
	}
	
	public Order getByReferenceNumber(String customerReferenceNumber, Seller seller) {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.add(Restrictions.eq("customerReferenceNumber",customerReferenceNumber));
		criteria.createAlias("orderBundle", "ob");
		criteria.add(Restrictions.eq("ob.seller",seller));
		List<Order> list = this.getHibernateTemplate().findByCriteria(criteria);
		if (list != null && list.size() > 0) {
			/* There will never be more than one order here, because customerReferenceNumber
			 * has unique pattern. */
			return list.get(0);
		}
		return null;
	}

	public List<Order> getAllOrdersRefundedFromProvider() {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		DetachedCriteria stateChangesCriteria = criteria.createCriteria("stateChanges");
		stateChangesCriteria.add(Restrictions.eq("state",Order.STATE_MONEY_REFUNDED_BY_PROVIDER_FOR_CANCELED_ORDER));
		//stateChangesCriteria.add(Restrictions.ge("updateDate",startDate));
		//stateChangesCriteria.add(Restrictions.lt("updateDate",endDate));
		return this.getHibernateTemplate().findByCriteria(criteria);
	}

	public List<Order> getAllOrdersInOrderConfirmedOrLaterStates(Date startDate, Date endDate) {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		DetachedCriteria stateChangesCriteria = criteria.createCriteria("stateChanges");
		stateChangesCriteria.add(Restrictions.eq("state",Order.STATE_ORDER_CONFIRMED_BY_PROVIDER));
		stateChangesCriteria.add(Restrictions.ge("updateDate",startDate));
		stateChangesCriteria.add(Restrictions.lt("updateDate",endDate));
		return this.getHibernateTemplate().findByCriteria(criteria);
	}

	public List<Order> getAllOrdersInOrderConfirmedOrLaterStatesByTranslator(Date startDate, Date endDate, String translator) {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		DetachedCriteria stateChangesCriteria = criteria.createCriteria("stateChanges");
		stateChangesCriteria.add(Restrictions.eq("state",Order.STATE_ORDER_CONFIRMED_BY_PROVIDER));
		stateChangesCriteria.add(Restrictions.ge("updateDate",startDate));
		stateChangesCriteria.add(Restrictions.lt("updateDate",endDate));
		criteria.createAlias("ad", "a");
		criteria.createAlias("a.adTemplate", "at");
		criteria.add(Restrictions.eq("at.addedBy",translator).ignoreCase());
		return this.getHibernateTemplate().findByCriteria(criteria);
	}
	
	public int getNbrOrdersInState(int state) {
		SimpleJdbcTemplate jdbcTemplate =
			new SimpleJdbcTemplate(SessionFactoryUtils.getDataSource(getSessionFactory()));
		Table table = AnnotationUtils.findAnnotation(Order.class, Table.class);
		return jdbcTemplate.queryForInt("SELECT count(*) FROM " + table.name() + " WHERE state = ?",state);
	}

	public int getNbrOrdersHavingAtLeastState(int state) {
		SimpleJdbcTemplate jdbcTemplate =
			new SimpleJdbcTemplate(SessionFactoryUtils.getDataSource(getSessionFactory()));
		Table table = AnnotationUtils.findAnnotation(Order.class, Table.class);
		return jdbcTemplate.queryForInt("SELECT count(*) FROM " + table.name() + " WHERE state >= ?",state);
	}

	public int getNbrOrders() {
		SimpleJdbcTemplate jdbcTemplate =
			new SimpleJdbcTemplate(SessionFactoryUtils.getDataSource(getSessionFactory()));
		Table table = AnnotationUtils.findAnnotation(Order.class, Table.class);
		return jdbcTemplate.queryForInt("SELECT count(*) FROM " + table.name());
	}
	
	public List<Order> getOrdersWithoutFeedbackInPaidOrLaterStates(SellerMarket sellerMarket) {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.add(Restrictions.eq("feedbackGiven",Boolean.FALSE));
		//criteria.add(Restrictions.isNotNull("ad"));
		criteria.add(Restrictions.eq("googleOrder",Boolean.FALSE)); //don't fetch orders coming from google-order because there is no feedback to give for those
		criteria.createAlias("orderBundle", "ob");
		criteria.add(Restrictions.eq("ob.seller",sellerMarket.getSeller()));
		criteria.add(Restrictions.eq("market",sellerMarket.getMarket()));
		DetachedCriteria stateChangesCriteria = criteria.createCriteria("stateChanges");
		stateChangesCriteria.add(Restrictions.eq("state",Order.STATE_CUSTOMER_PAID));
		return this.getHibernateTemplate().findByCriteria(criteria);
	}

	public List<Order> getOrdersByState(int state) {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.add(Restrictions.eq("state",state));
		return this.getHibernateTemplate().findByCriteria(criteria);
	}

	public List<Order> getOrdersByCustomerEmailAddress(String emailAddress) {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.createAlias("orderBundle", "ob");
		criteria.createAlias("ob.customer", "c");
		criteria.add(Restrictions.eq("c.email",emailAddress.toLowerCase()));
		//criteria.addOrder(org.hibernate.criterion.Order.asc("id"));
		return this.getHibernateTemplate().findByCriteria(criteria);
	}

	public List<Order> getOrdersByCustomerEmailAddress(String emailAddress, Seller seller) {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.createAlias("orderBundle", "ob");
		criteria.add(Restrictions.eq("ob.seller",seller));
		criteria.createAlias("ob.customer", "c");
		criteria.add(Restrictions.eq("c.email",emailAddress.toLowerCase()));
		//criteria.addOrder(org.hibernate.criterion.Order.asc("id"));
		return this.getHibernateTemplate().findByCriteria(criteria);
	}

	public List<Order> getOrdersByCustomer(Customer customer, Seller seller) {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.createAlias("orderBundle", "ob");
		criteria.add(Restrictions.eq("ob.customer",customer));
		criteria.add(Restrictions.eq("ob.seller",seller));
		return this.getHibernateTemplate().findByCriteria(criteria);
	}

	public List<Order> getByMarketSalesId(String marketSalesId, Seller seller, Market market) {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		if (seller != null) {
			criteria.createAlias("orderBundle", "ob");
			criteria.add(Restrictions.eq("ob.seller",seller));
		}
		criteria.createAlias("ad", "a");
		criteria.add(Restrictions.eq("a.marketSalesId",marketSalesId));
		if (market != null) {
			criteria.add(Restrictions.eq("a.market",market));
		}
		return this.getHibernateTemplate().findByCriteria(criteria);
	}

	/*public String getProductName(Order order) {
		String prodName = null;
		if (order.getAd() != null) {
			prodName = order.getAd().getAdTemplate().getHeadline();
		} else {
			/* If this order was ordered from google-order, then we need to get the name through the product table instead. /
			//ProductDAO productDAO = (ProductDAO) ApplicationContextPlaceholder.applicationContext.getBean("productDAO");
			AdTemplateDAO adTemplateDAO = (AdTemplateDAO) ApplicationContextPlaceholder.applicationContext.getBean("adTemplateDAO");
			//Product product = productDAO.getByProviderProdId(order.getProviderProdId(), order.getOrderBundle().getProvider());
			List<AdTemplate> adTemplates = adTemplateDAO.findByProduct(order.getProduct());
			/* Simply pick the headline of the first one (in practice there should only be one adtemplate,
			 * but even if there would be several ones it's not a big deal). /
			prodName = adTemplates.get(0).getHeadline();
		}
		return prodName;
	}*/

	public List<Order> getOrdersByStateAndCustomerEmailAddress(int state, String emailAddress, Seller seller) {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.add(Restrictions.eq("state",state));
		criteria.createAlias("orderBundle", "ob");
		criteria.add(Restrictions.eq("ob.seller",seller));
		criteria.createAlias("ob.customer", "c");
		criteria.add(Restrictions.eq("c.email",emailAddress.toLowerCase()));
		return this.getHibernateTemplate().findByCriteria(criteria);
	}

	public List<Order> getOrdersByCustomerStringFieldIgnoreCase(String fieldName, String value) {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.createAlias("orderBundle", "ob");
		criteria.add(Restrictions.eq("ob." + fieldName,value).ignoreCase());
		return this.getHibernateTemplate().findByCriteria(criteria);
	}

	/**
	 * @param headline - Example: "aaa" fetches all strings equal to "aaa",
	 * "%aaa" fetches all strings ending with "aaa", "aaa%" fetches all strings
	 * starting with "aaa" and "%aaa%" fetches all strings having "aaa" somewhere
	 * in it.
	 */
	public List<Order> getBySimilarHeadline(String headline) {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.createAlias("ad", "a");
		criteria.createAlias("a.adTemplate", "at");
		criteria.add(Restrictions.like("at.headline",headline).ignoreCase());
		return this.getHibernateTemplate().findByCriteria(criteria);
	}
	
	/**
	 * This method returns a list of provider product id's for the products that have sold the most.
	 */
	/*public List<Map<String,Object>> getBestSellingProviderProdIds() {
		SimpleJdbcTemplate jdbcTemplate =
			new SimpleJdbcTemplate(SessionFactoryUtils.getDataSource(getSessionFactory()));
		Table orderTable = AnnotationUtils.findAnnotation(Order.class, Table.class);
		Table orderBundleTable = AnnotationUtils.findAnnotation(OrderBundle.class, Table.class);
		Table productTable = AnnotationUtils.findAnnotation(Product.class, Table.class);
		
		/* The following query fetches best selling orders with respect to seller. The problem with
		 * this is that this way the statistics is "zeroed" when an adtemplate is moved from one seller
		 * to another. */
		//return jdbcTemplate.queryForList("SELECT count(id) as count, provider_id, provider_prod_id FROM " + table.name()
			//	+ " WHERE seller_id = ? GROUP BY provider_id, provider_prod_id ORDER BY count DESC",seller.getId());

		/* The following query fetches best selling orders with no respect to seller. This makes
		 * sure that the statistics stays the same even if an adtemplate is moved from one seller
		 * to another. /
		return jdbcTemplate.queryForList("SELECT count(o.id) as count, ob.provider_id, p.provider_prod_id FROM " + orderTable.name()
				+ " o, " + orderBundleTable.name() + " ob, " + productTable.name() + " p WHERE o.order_bundle_id = ob.id"
				+ " AND o.product_id = p.id GROUP BY ob.provider_id, p.provider_prod_id ORDER BY count DESC");
	}*/
	
	public List<Map<String,Object>> getBestSellingProviderProdIds(int weeksBack) {
		SimpleJdbcTemplate jdbcTemplate =
			new SimpleJdbcTemplate(SessionFactoryUtils.getDataSource(getSessionFactory()));
		Table orderTable = AnnotationUtils.findAnnotation(Order.class, Table.class);
		Table productTable = AnnotationUtils.findAnnotation(Product.class, Table.class);
		//Table adTemplateTable = AnnotationUtils.findAnnotation(AdTemplate.class, Table.class);
		Table stateChangesTable = AnnotationUtils.findAnnotation(StateChange.class, Table.class);
		
		/* Calculate a startDate. */
		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.DATE, -weeksBack*7);
		Date startDate = cal.getTime();
		
		/* The following query fetches best selling orders with no respect to seller. This makes
		 * sure that the statistics stays the same even if an adtemplate is moved from one seller
		 * to another. */
		return jdbcTemplate.queryForList("SELECT count(o.id) as count, p.provider_id, p.provider_prod_id FROM " + orderTable.name()
				+ " o, " + productTable.name() + " p, " + stateChangesTable.name() + " sc WHERE o.product_id = p.id"
				+ " AND sc.order_id = o.id AND sc.state = 10 AND sc.update_date >= ? GROUP BY p.provider_id,"
				+ " p.provider_prod_id ORDER BY count DESC",startDate);
	}
	
	public List<Order> getRecentOutOfStockOrdersForProduct(int weeksBack, Product product) {
		/* Calculate a startDate. */
		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.DATE, -weeksBack*7);
		Date startDate = cal.getTime();
		
		Object[] cond = new Object[5];
		cond[0] = Order.STATE_STOCK_SHORTAGE;
		cond[1] = Order.STATE_NEW_OUT_OF_STOCK;
		cond[2] = Order.STATE_STOCK_SHORTAGE_BEFORE_ORDER;
		cond[3] = startDate;
		cond[4] = product;
		return this.getHibernateTemplate().find("SELECT o FROM Order o WHERE o.id IN (SELECT order FROM"
				+ " StateChange WHERE state IN (?,?,?) and updateDate >= ?) AND o.product = ?",cond);
	}

	public int getNbrOrdersForProduct(Product product) {
		SimpleJdbcTemplate jdbcTemplate =
			new SimpleJdbcTemplate(SessionFactoryUtils.getDataSource(getSessionFactory()));
		Table table = AnnotationUtils.findAnnotation(Order.class, Table.class);
		return jdbcTemplate.queryForInt("SELECT count(*) FROM " + table.name() + " WHERE product_id = ?",product.getId());
	}

	/**
	 * This method returns all orders for which it has been paid too much or too little no matter what state they are in.
	 */
	public List<Order> getOrdersWithUnevenTargetPayments() {
		//return this.getHibernateTemplate().find("SELECT o FROM Order o WHERE targetedPaidInMarketCurrency <>"
			//	+ " shippingCostInMarketCurrency + finalSalesPriceInMarketCurrency");
		return this.getHibernateTemplate().find("SELECT o FROM Order o WHERE targetedPaidInMarketCurrency <>"
				+ " finalSalesPriceInMarketCurrencyInclVat");
	}

	/*public List<Order> getAllOrdersForBundlesWhereSomeOrderIsRefunded() {
		return this.getHibernateTemplate().find("SELECT distinct o FROM Order o WHERE o.orderBundle IN"
				+ " (SELECT o.orderBundle FROM Order o WHERE o.id IN ("
				+ "SELECT order from StateChange WHERE state IN ("
				+ Order.STATE_MONEY_REFUNDED_TO_CUSTOMER_FOR_CANCELED_ORDER
				+ "," + Order.STATE_MONEY_REFUNDED_FOR_SENT_ORDER + "))) ORDER BY o.orderBundle");
	}*/
	
	/*public List<Long> getIdsOfAllRefundedOrders() {
		return this.getHibernateTemplate().find("SELECT o.id FROM Order o WHERE o.id IN ("
				+ "SELECT order from StateChange WHERE state IN ("
				+ Order.STATE_MONEY_REFUNDED_TO_CUSTOMER_FOR_CANCELED_ORDER
				+ "," + Order.STATE_MONEY_REFUNDED_FOR_SENT_ORDER + "))");
	}*/

	public List<Order> getOrdersWithNonZeroRefundings() {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.add(Restrictions.ne("refundingMarketCurrency",BigDecimal.ZERO));
		return this.getHibernateTemplate().findByCriteria(criteria);
	}

	public Date getDateOfLastWonBid(Customer customer, Seller seller) {
		SimpleJdbcTemplate jdbcTemplate =
			new SimpleJdbcTemplate(SessionFactoryUtils.getDataSource(getSessionFactory()));
		Table orderTable = AnnotationUtils.findAnnotation(Order.class, Table.class);
		Table orderBundleTable = AnnotationUtils.findAnnotation(OrderBundle.class, Table.class);
		Table stateChangesTable = AnnotationUtils.findAnnotation(StateChange.class, Table.class);
		
		return jdbcTemplate.queryForObject("SELECT max(sc.update_date) FROM " + stateChangesTable.name() + " sc, "
				+ orderTable.name() + " o, " + orderBundleTable.name() + " ob WHERE sc.order_id = o.id AND"
				+ " o.order_bundle_id = ob.id and ob.customer_id = ? AND ob.seller_id = ? AND (sc.state = ?"
				+ " OR sc.state = ? OR sc.state = ?)",Date.class,customer.getId(),seller.getId(),
				Order.STATE_FAULTY_CONTACT_INFO,Order.STATE_NEW_OUT_OF_STOCK,Order.STATE_NEW);
	}

	/**
	 * This method tells in how many batches ("partial shipping") the bundle has been shipped.
	 * The number of batches is determined based on the timestamp when the orders of the bundle
	 * was set to state "shipped by provider". Two orders having a timestamp at most 5 minutes
	 * apart are considered to be shipped in the same batch. Note that it's not accurate to
	 * instead check the number of tracking objects for the order bundle. For example let's say
	 * a bundle had three orders that were shipped using three different batches where the
	 * first and the last one had a tracking code but the middle one doesn't. In this case
	 * counting the number of tracking objects gives the wrong result 2 shippings whereas
	 * checking the timestamps of when the individual orders were shipped gives the correct
	 * result: 3 batches.
	 * 
	 * Handling of resendings: if a resending has been done, then only check sendings that have
	 * been done AFTER the state was "should resend".
	 */
	public int getNbrShippingBatchesForBundle(OrderBundle bundle) {
		SimpleJdbcTemplate jdbcTemplate =
			new SimpleJdbcTemplate(SessionFactoryUtils.getDataSource(getSessionFactory()));
		Table orderTable = AnnotationUtils.findAnnotation(Order.class, Table.class);
		Table stateChangeTable = AnnotationUtils.findAnnotation(StateChange.class, Table.class);
		List<Map<String,Object>> shippingDateList = jdbcTemplate.queryForList("SELECT sc.update_date, sc.state FROM "
				+ orderTable.name() + " o, " + stateChangeTable.name() + " sc WHERE sc.order_id = o.id AND"
				+ " o.order_bundle_id = ? AND (sc.state = ? OR sc.state = ?) ORDER BY sc.update_date ASC",bundle.getId(),
				Order.STATE_SHOULD_RESEND,Order.STATE_SHIPPED_BY_PROVIDER);
		int nbrShippings = 0;
		Date previousShippingDate = null;
		for (Map<String,Object> row : shippingDateList) {
			Date date = (Date) row.get("update_date");
			int state = (Integer) row.get("state");
			if (state == Order.STATE_SHOULD_RESEND) {
				nbrShippings = 0;
				previousShippingDate = null;
			} else {
				if (previousShippingDate == null) {
					nbrShippings++;
				} else {
					/* Consider two successive dates to belong to the same batch if
					 * they are at most five minutes apart. */
					if (date.getTime() - previousShippingDate.getTime() > 5*60*1000) {
						nbrShippings++;
					}
				}
				previousShippingDate = date;
			}
		}
		return nbrShippings;
	}
	
	public List<Order> getOrdersNotTransferedToVerification() {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.add(Restrictions.eq("transferedToVerification",Boolean.FALSE));
		criteria.addOrder(org.hibernate.criterion.Order.asc("id"));
		return this.getHibernateTemplate().findByCriteria(criteria);
	}
	
	public BigDecimal getTotalSalesInclVat(Currency currency, BigDecimal customerVatPercentage) {
		SimpleJdbcTemplate jdbcTemplate =
			new SimpleJdbcTemplate(SessionFactoryUtils.getDataSource(getSessionFactory()));
		Table orderTable = AnnotationUtils.findAnnotation(Order.class, Table.class);
		Table orderBundleTable = AnnotationUtils.findAnnotation(OrderBundle.class, Table.class);
		return jdbcTemplate.queryForObject("SELECT sum(fin_sp_mc_inc_vat) FROM " + orderTable.name() + " o, "
				+ orderBundleTable.name() + " ob WHERE o.order_bundle_id = ob.id AND"
				+ " ob.ccurrency_id = ? AND o.customer_vat_perc = ?",BigDecimal.class,
				currency.getId(),customerVatPercentage);
	}

	public BigDecimal getTotalSalesExclVat(Currency currency, BigDecimal customerVatPercentage) {
		SimpleJdbcTemplate jdbcTemplate =
			new SimpleJdbcTemplate(SessionFactoryUtils.getDataSource(getSessionFactory()));
		Table orderTable = AnnotationUtils.findAnnotation(Order.class, Table.class);
		Table orderBundleTable = AnnotationUtils.findAnnotation(OrderBundle.class, Table.class);
		return jdbcTemplate.queryForObject("SELECT sum(fin_sp_mc_exc_vat) FROM " + orderTable.name() + " o, "
				+ orderBundleTable.name() + " ob WHERE o.order_bundle_id = ob.id AND"
				+ " ob.ccurrency_id = ? AND o.customer_vat_perc = ?",BigDecimal.class,
				currency.getId(),customerVatPercentage);
	}

	public List<BigDecimal> getDistinctCustomerVatPercentages() {
		List<BigDecimal> returnList = new ArrayList<BigDecimal>();
		SimpleJdbcTemplate jdbcTemplate =
			new SimpleJdbcTemplate(SessionFactoryUtils.getDataSource(getSessionFactory()));
		Table orderTable = AnnotationUtils.findAnnotation(Order.class, Table.class);
		List<Map<String,Object>> custVatPercentageList = jdbcTemplate.queryForList("SELECT DISTINCT customer_vat_perc FROM "
				+ orderTable.name());
		for (Map<String,Object> row : custVatPercentageList) {
			BigDecimal custVatPercentage = (BigDecimal) row.get("customer_vat_perc");
			returnList.add(custVatPercentage);
		}
		return returnList;
	}

}
