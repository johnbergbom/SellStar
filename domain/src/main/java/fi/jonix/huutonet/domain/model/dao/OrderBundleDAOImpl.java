package fi.jonix.huutonet.domain.model.dao;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.persistence.Table;

import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Restrictions;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.jdbc.core.simple.SimpleJdbcTemplate;
import org.springframework.orm.hibernate3.SessionFactoryUtils;

import fi.jonix.huutonet.domain.model.Currency;
import fi.jonix.huutonet.domain.model.Customer;
import fi.jonix.huutonet.domain.model.Order;
import fi.jonix.huutonet.domain.model.OrderBundle;
import fi.jonix.huutonet.domain.model.Provider;
import fi.jonix.huutonet.domain.model.Seller;
import fi.jonix.huutonet.domain.model.StateChange;


public class OrderBundleDAOImpl extends GenericDAOImpl <OrderBundle, Long> implements OrderBundleDAO {

	@Override
	public Class<OrderBundle> getEntityClass() {
		return OrderBundle.class;
	}

	public OrderBundle getOrderBundleByProviderOrderId(String providerOrderId, Provider provider) {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.add(Restrictions.eq("provider",provider));
		criteria.add(Restrictions.eq("providerOrderId",providerOrderId));
		List<OrderBundle> list = this.getHibernateTemplate().findByCriteria(criteria);
		if (list != null && list.size() > 0) {
			/* There will never be more than one order bundle here, because provider
			 * and providerOrderId are together unique in the database. */
			return list.get(0);
		} 
		return null;
	}

	public OrderBundle getOrderBundleByPayPalId(String payPalId, Provider provider) {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.add(Restrictions.eq("provider",provider));
		criteria.add(Restrictions.eq("payPalId",payPalId));
		List<OrderBundle> list = this.getHibernateTemplate().findByCriteria(criteria);
		if (list != null && list.size() > 0) {
			/* There will never be more than one order bundle here, because provider
			 * and payPalId are together unique in the database. */
			return list.get(0);
		} 
		return null;
	}

	public List<OrderBundle> getOrderBundles(Customer customer, Provider provider, Seller seller) {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.add(Restrictions.eq("customer",customer));
		criteria.add(Restrictions.eq("provider",provider));
		criteria.add(Restrictions.eq("seller",seller));
		return this.getHibernateTemplate().findByCriteria(criteria);
	}

	/**
	 * This method returns all bundles for which it has been paid too much or too little no matter what state they are in.
	 */
	public List<OrderBundle> getBundlesWithUnevenTargetPayments() {
		return this.getHibernateTemplate().find("SELECT ob FROM OrderBundle ob WHERE targetedPaidShippingCostInMarketCurrency <>"
				+ " customerShippingCostInMarketCurrencyInclVat");
	}

	/**
	 * This method returns bundles whose orders have a sum(order.fin_sp_mc_inc_vat) + bundle.cust_shipping_cost_mc_inc_vat = totalExpectedPayment.
	 * NOTE: this method doesn't differentiate between paid, partly paid nor unpaid orders.
	 */
	public List<OrderBundle> getBundlesByFinalSalesPricePlusShippingCost(BigDecimal totalExpectedPayment, Currency usedCustomerCurrency) {
		SimpleJdbcTemplate jdbcTemplate =
			new SimpleJdbcTemplate(SessionFactoryUtils.getDataSource(getSessionFactory()));
		Table orderTable = AnnotationUtils.findAnnotation(Order.class, Table.class);
		Table orderBundleTable = AnnotationUtils.findAnnotation(OrderBundle.class, Table.class);
		/* Use two queries for this, because straight sql is simpler than hsql: first fetch
		 * all bundle id's that match the right criterias and then fetch the OrderBundle
		 * objects with a separate query. */
		List<Map<String,Object>> bundleIdMapList = jdbcTemplate.queryForList("SELECT bundle_id FROM (SELECT ob.id as bundle_id, sum(o.targeted_mc)"
				+ " as targeted_mc, sum(o.fin_sp_mc_inc_vat) as fin_sp_mc_inc_vat, ob.targeted_ship_mc,"
				+ " ob.cust_shipping_cost_mc_inc_vat FROM " + orderTable.name() + " o, " + orderBundleTable.name() + " ob WHERE"
				+ " o.order_bundle_id = ob.id GROUP BY ob.id, ob.targeted_ship_mc, ob.cust_shipping_cost_mc_inc_vat) as foo"
				+ " WHERE fin_sp_mc_inc_vat + cust_shipping_cost_mc_inc_vat = ?",totalExpectedPayment);
		List<OrderBundle> returnList = new ArrayList<OrderBundle>();
		if (bundleIdMapList.size() == 0) {
			return returnList;
		}
		List<Long> bundleIdList = new ArrayList<Long>();
		for (Map<String,Object> map : bundleIdMapList) {
			bundleIdList.add((Long) map.get("bundle_id"));
		}
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.add(Restrictions.in("id",bundleIdList));
		criteria.add(Restrictions.eq("usedCustomerCurrency",usedCustomerCurrency));
		return this.getHibernateTemplate().findByCriteria(criteria);
	}
	
	/**
	 * This method returns bundles whose orders have:
	 * sum(fin_sp_mc_inc_vat) + bundle.cust_shipping_cost_mc_inc_vat - sum(o.targeted_mc) - bundle.targeted_ship_mc = totalExpectedPayment.
	 * In other words the method returns all bundles that have remainingExpectedPayment unpaid.
	 * NOTE: this method only includes such bundles that are _partly_ paid. I.e. if nothing is paid, then
	 * the bundle is not included even if expectedPayment - targetedPayment = remainingExpectedPayment.
	 */
	public List<OrderBundle> getPartlyPaidBundlesByRemainingFinalSalesPricePlusShippingCost(BigDecimal remainingExpectedPayment, Currency usedCustomerCurrency) {
		List<OrderBundle> returnList = new ArrayList<OrderBundle>();
		if (remainingExpectedPayment.compareTo(BigDecimal.ZERO) == 0) {
			/* No use to compare to zero, because that will give us almost all bundles in the whole database (all
			 * bundles for orders that are fully paid). */
			return returnList;
		}
		SimpleJdbcTemplate jdbcTemplate =
			new SimpleJdbcTemplate(SessionFactoryUtils.getDataSource(getSessionFactory()));
		Table orderTable = AnnotationUtils.findAnnotation(Order.class, Table.class);
		Table orderBundleTable = AnnotationUtils.findAnnotation(OrderBundle.class, Table.class);
		/* Use two queries for this, because straight sql is simpler than hsql: first fetch
		 * all bundle id's that match the right criterias and then fetch the OrderBundle
		 * objects with a separate query. */
		List<Map<String,Object>> bundleIdMapList = jdbcTemplate.queryForList("SELECT bundle_id FROM (SELECT ob.id as bundle_id, sum(o.targeted_mc)"
				+ " as targeted_mc, sum(o.fin_sp_mc_inc_vat) as fin_sp_mc_inc_vat, ob.targeted_ship_mc,"
				+ " ob.cust_shipping_cost_mc_inc_vat FROM " + orderTable.name() + " o, " + orderBundleTable.name() + " ob WHERE"
				+ " o.order_bundle_id = ob.id GROUP BY ob.id, ob.targeted_ship_mc, ob.cust_shipping_cost_mc_inc_vat) as foo"
				+ " WHERE (targeted_mc <> 0 or targeted_ship_mc <> 0) and"
				+ " (fin_sp_mc_inc_vat + cust_shipping_cost_mc_inc_vat - targeted_mc - targeted_ship_mc = ?)",remainingExpectedPayment);
		if (bundleIdMapList.size() == 0) {
			return returnList;
		}
		List<Long> bundleIdList = new ArrayList<Long>();
		for (Map<String,Object> map : bundleIdMapList) {
			bundleIdList.add((Long) map.get("bundle_id"));
		}
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.add(Restrictions.in("id",bundleIdList));
		criteria.add(Restrictions.eq("usedCustomerCurrency",usedCustomerCurrency));
		return this.getHibernateTemplate().findByCriteria(criteria);
	}
	
	public List<OrderBundle> getEmptyBundles() {
		return this.getHibernateTemplate().find("SELECT ob FROM OrderBundle ob WHERE ob.id NOT IN ("
				+ "SELECT o.orderBundle from Order o)");
	}
	
	public List<OrderBundle> getPaidOrPartlyPaidBundlesWhereCustomerIsNotInformed() {
		//return this.getHibernateTemplate().find("SELECT distinct ob FROM OrderBundle ob, Order o WHERE o.orderBundle = ob.id AND"
			//	+ " o.state = ?",Order.STATE_CUSTOMER_PAID);
		return this.getHibernateTemplate().find("SELECT ob FROM OrderBundle ob WHERE ob IN (SELECT o.orderBundle FROM Order o"
				+ " WHERE o.state = ?)",Order.STATE_CUSTOMER_PAID);
	}

	public List<OrderBundle> getBundlesForPlaceOrder(Provider provider) {
		/* First orders in state = 140 are returned, then orders in state = 160 and last orders
		//having state = 180. Then reason for this is that it seems like orders that fail (typically orders
		//in state = 180) sometimes messes up the order system of FocalPrice so that subsequent attempts to order
		//something else also fails. */
		//return this.getHibernateTemplate().find("SELECT ob FROM OrderBundle ob WHERE ob IN (SELECT o.orderBundle FROM Order o"
			//	+ " WHERE o.state = ? OR o.state = ? OR o.state = ?)",new Object[] { Order.STATE_CUSTOMER_PAID_INFORMED,
				//Order.STATE_STOCK_SHORTAGE_BEFORE_ORDER,Order.STATE_SHOULD_RESEND });
		List<OrderBundle> returnList = new ArrayList<OrderBundle>();
		Object[] cond = new Object[2];
		cond[0] = Order.STATE_CUSTOMER_PAID_INFORMED;
		cond[1] = provider;
		returnList.addAll(getHibernateTemplate().find("SELECT ob FROM OrderBundle ob WHERE ob IN (SELECT o.orderBundle FROM Order o"
				+ " WHERE o.state = ?) AND ob.provider = ?",cond));
		cond[0] = Order.STATE_SHOULD_RESEND;
		returnList.addAll(getHibernateTemplate().find("SELECT ob FROM OrderBundle ob WHERE ob IN (SELECT o.orderBundle FROM Order o"
				+ " WHERE o.state = ?) AND ob.provider = ?",cond));
		cond[0] = Order.STATE_STOCK_SHORTAGE_BEFORE_ORDER;
		returnList.addAll(getHibernateTemplate().find("SELECT ob FROM OrderBundle ob WHERE ob IN (SELECT o.orderBundle FROM Order o"
				+ " WHERE o.state = ?) AND ob.provider = ?",cond));
		return returnList;
	}

	public List<OrderBundle> getBundlesForStatusUpdate(Provider provider) {
		Object[] cond = new Object[Order.UPDATABLE_STATES.length + 1];
		cond[Order.UPDATABLE_STATES.length] = provider;
		String whereClause = "WHERE ";
		for (int i = 0; i < Order.UPDATABLE_STATES.length; i++) {
			cond[i] = Order.UPDATABLE_STATES[i];
			whereClause += "o.state = ? OR ";
		}
		whereClause = whereClause.substring(0,whereClause.length()-4);
		return this.getHibernateTemplate().find("SELECT ob FROM OrderBundle ob WHERE ob IN (SELECT o.orderBundle FROM Order o"
				+ " " + whereClause + ") AND ob.provider = ?",cond);
	}

	/*public List<OrderBundle> getBundlesForLocationTracking() {
		Object[] states = new Object[2];
		states[0] = Order.STATE_SHIPPED_BY_PROVIDER;
		states[1] = Order.STATE_FETCHED_BY_CUSTOMER;
		return this.getHibernateTemplate().find("SELECT ob FROM OrderBundle ob WHERE ob IN (SELECT o.orderBundle FROM Order o"
				+ " WHERE o.state >= ? AND o.state < ?) AND ob.trackingCode IS NOT NULL",states);
	}*/

	public List<OrderBundle> getAllOpenBundles() {
		return this.getHibernateTemplate().find("SELECT ob FROM OrderBundle ob WHERE ob IN (SELECT o.orderBundle FROM Order o"
				+ " WHERE o.state < ?)",Order.STATE_CLOSED);
	}

	public int getBundleCountForUsedCustomerCurrency(Customer customer, Currency usedCustomerCurrency) {
		SimpleJdbcTemplate jdbcTemplate =
			new SimpleJdbcTemplate(SessionFactoryUtils.getDataSource(getSessionFactory()));
		Table table = AnnotationUtils.findAnnotation(OrderBundle.class, Table.class);
		return jdbcTemplate.queryForInt("SELECT count(*) FROM " + table.name() + " WHERE ccurrency_id = ?"
				+ " AND customer_id = ?",usedCustomerCurrency.getId(),customer.getId());
	}

	public List<OrderBundle> getBundlesNotTransferedToVerification() {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.add(Restrictions.eq("transferedToVerification",Boolean.FALSE));
		criteria.addOrder(org.hibernate.criterion.Order.asc("id"));
		return this.getHibernateTemplate().findByCriteria(criteria);
	}

	public BigDecimal getTotalSalesInclVat(Currency currency, BigDecimal customerVatPercentage) {
		SimpleJdbcTemplate jdbcTemplate =
			new SimpleJdbcTemplate(SessionFactoryUtils.getDataSource(getSessionFactory()));
		Table orderBundleTable = AnnotationUtils.findAnnotation(OrderBundle.class, Table.class);
		return jdbcTemplate.queryForObject("SELECT sum(cust_shipping_cost_mc_inc_vat) FROM "
				+ orderBundleTable.name() + " ob WHERE ob.ccurrency_id = ? AND ob.customer_ship_vat_perc = ?",
				BigDecimal.class,currency.getId(),customerVatPercentage);
	}

	public BigDecimal getTotalSalesExclVat(Currency currency, BigDecimal customerVatPercentage) {
		SimpleJdbcTemplate jdbcTemplate =
			new SimpleJdbcTemplate(SessionFactoryUtils.getDataSource(getSessionFactory()));
		Table orderBundleTable = AnnotationUtils.findAnnotation(OrderBundle.class, Table.class);
		return jdbcTemplate.queryForObject("SELECT sum(cust_shipping_cost_mc_exc_vat) FROM "
				+ orderBundleTable.name() + " ob WHERE ob.ccurrency_id = ? AND ob.customer_ship_vat_perc = ?",
				BigDecimal.class,currency.getId(),customerVatPercentage);
	}

	public List<BigDecimal> getDistinctCustomerVatPercentages() {
		List<BigDecimal> returnList = new ArrayList<BigDecimal>();
		SimpleJdbcTemplate jdbcTemplate =
			new SimpleJdbcTemplate(SessionFactoryUtils.getDataSource(getSessionFactory()));
		Table orderBundleTable = AnnotationUtils.findAnnotation(OrderBundle.class, Table.class);
		List<Map<String,Object>> custVatPercentageList = jdbcTemplate.queryForList("SELECT DISTINCT customer_ship_vat_perc FROM "
				+ orderBundleTable.name());
		for (Map<String,Object> row : custVatPercentageList) {
			BigDecimal custVatPercentage = (BigDecimal) row.get("customer_ship_vat_perc");
			returnList.add(custVatPercentage);
		}
		return returnList;
	}

}
