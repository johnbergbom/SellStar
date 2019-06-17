package fi.jonix.huutonet.domain.model.dao;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import javax.persistence.Table;

import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Restrictions;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.SimpleJdbcTemplate;
import org.springframework.orm.hibernate3.SessionFactoryUtils;

import fi.jonix.huutonet.domain.model.Order;
import fi.jonix.huutonet.domain.model.Transaction;

public class TransactionDAOImpl extends GenericDAOImpl <Transaction, Long> implements TransactionDAO  {

	public static final String QD_TRANSFER_IDENTIFICATION = "Transfer from SellStar to QD";
	
	public Class<Transaction> getEntityClass() {
		return Transaction.class;
	}

	public Date getLatestTransactionDate() throws DataAccessException {
		SimpleJdbcTemplate jdbcTemplate =
			new SimpleJdbcTemplate(SessionFactoryUtils.getDataSource(getSessionFactory()));
		Table table = AnnotationUtils.findAnnotation(Transaction.class, Table.class);
		return jdbcTemplate.queryForObject("SELECT MAX(date) FROM " + table.name(),Date.class);
	}

	/**
	 * 
	 * @param marketSalesId
	 * @param market
	 * @return Number of transactions identical to the one that's passed as a parameter found in the database.
	 */
	public int identicalTransactionsFoundInDb(Transaction transaction) {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.add(Restrictions.eq("date", transaction.getDate()));
		criteria.add(Restrictions.eq("payer", transaction.getPayer()));
		criteria.add(Restrictions.eq("description", transaction.getDescription()));
		criteria.add(Restrictions.eq("message", transaction.getMessage()));
		criteria.add(Restrictions.eq("amount", transaction.getAmount()));
		criteria.add(Restrictions.eq("referenceNumber", transaction.getReferenceNumber()));
		criteria.add(Restrictions.eq("currency", transaction.getCurrency()));
		/*if (transaction.getReferenceNumber() != null) {
			criteria.add(Restrictions.eq("referenceNumber", transaction.getReferenceNumber()));
		} else {
			criteria.add(Restrictions.isNull("referenceNumber"));
		}*/
		//DON'T compare transaction.id nor transaction.order_id, because that information doesn't come from the bank!!
		List<Transaction> list = this.getHibernateTemplate().findByCriteria(criteria);
		return list.size();
	}

	/**
	 * 
	 * @param marketSalesId
	 * @param market
	 * @return Number of transactions identical to the one that's passed as a parameter found in the transaction list.
	 */
	public int identicalTransactionsFoundInList(Transaction transaction, List<Transaction> transactions) {
		int nbr = 0;
		for (Transaction tr : transactions) {
			if (tr.equalsDontCompareWithIdNorWithOrder(transaction)) {
				nbr++;
			}
		}
		return nbr;
	}

	public List<Transaction> getTransactionsForOrder(Order order) {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.add(Restrictions.eq("order",order));
		List<Transaction> list = this.getHibernateTemplate().findByCriteria(criteria);
		return list;
	}

	/*public double getTotalAmountPaidForOrder(Order order) {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.add(Restrictions.eq("order",order));
		List<Transaction> list = this.getHibernateTemplate().findByCriteria(criteria);
		if (list == null || list.isEmpty()) {
			return 0;
		} else {
			double actualPayment = 0;
			for (Transaction tr : list) {
				actualPayment += tr.getAmount();
			}
			return actualPayment;
		}
	}*/

	public BigDecimal getSumAmount(Date startDate, Date endDate) {
		SimpleJdbcTemplate jdbcTemplate =
			new SimpleJdbcTemplate(SessionFactoryUtils.getDataSource(getSessionFactory()));
		Table table = AnnotationUtils.findAnnotation(Transaction.class, Table.class);
		return jdbcTemplate.queryForObject("SELECT SUM(amount) FROM " + table.name() + " WHERE date >= ? and date < ?",BigDecimal.class,startDate,endDate);
	}

	public BigDecimal getBalance() {
		SimpleJdbcTemplate jdbcTemplate =
			new SimpleJdbcTemplate(SessionFactoryUtils.getDataSource(getSessionFactory()));
		Table table = AnnotationUtils.findAnnotation(Transaction.class, Table.class);
		return jdbcTemplate.queryForObject("SELECT SUM(amount) FROM " + table.name(),BigDecimal.class);
	}

	public BigDecimal getBalanceAfterTransaction(Transaction transaction) {
		SimpleJdbcTemplate jdbcTemplate =
			new SimpleJdbcTemplate(SessionFactoryUtils.getDataSource(getSessionFactory()));
		Table table = AnnotationUtils.findAnnotation(Transaction.class, Table.class);
		return jdbcTemplate.queryForObject("SELECT SUM(amount) FROM " + table.name() + " WHERE id > ?",BigDecimal.class,transaction.getId());
	}

	public List<Transaction> getTransactionsAfterGivenTransaction(Transaction transaction) {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.add(Restrictions.gt("id", transaction.getId()));
		criteria.addOrder(org.hibernate.criterion.Order.asc("id"));
		return this.getHibernateTemplate().findByCriteria(criteria);
	}

	public int getNbrTransactionsForCurrency(Long currency) {
		SimpleJdbcTemplate jdbcTemplate =
			new SimpleJdbcTemplate(SessionFactoryUtils.getDataSource(getSessionFactory()));
		Table table = AnnotationUtils.findAnnotation(Transaction.class, Table.class);
		return jdbcTemplate.queryForInt("SELECT count(id) FROM " + table.name() + " WHERE currency_id = ?",currency);
	}

	public int getTotalNbrTransactions() {
		SimpleJdbcTemplate jdbcTemplate =
			new SimpleJdbcTemplate(SessionFactoryUtils.getDataSource(getSessionFactory()));
		Table table = AnnotationUtils.findAnnotation(Transaction.class, Table.class);
		return jdbcTemplate.queryForInt("SELECT count(id) FROM " + table.name());
	}

	/**
	 * @return A list of transactions that aren't mapped to any order
	 * and where operatorDescription is null.
	 */
	public List<Transaction> getUnmappedNormalTransactions() {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.add(Restrictions.isNull("order"));
		criteria.add(Restrictions.isNull("operatorDescription"));
		List<Transaction> list = this.getHibernateTemplate().findByCriteria(criteria);
		return list;
	}

	public List<Transaction> getOrderRefunds() {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.add(Restrictions.isNotNull("order"));
		criteria.add(Restrictions.lt("amount", BigDecimal.ZERO));
		List<Transaction> list = this.getHibernateTemplate().findByCriteria(criteria);
		return list;
	}

	public List<Transaction> getTransactionsByPayer(String payer) {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.add(Restrictions.eq("payer",payer));
		List<Transaction> list = this.getHibernateTemplate().findByCriteria(criteria);
		return list;
	}

	/*public Transaction getLastQDTransferTransaction() {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.add(Restrictions.eq("operatorDescription",QD_TRANSFER_IDENTIFICATION));
		criteria.add(Restrictions.eq("payer","QD-TECH OY"));
		criteria.addOrder(org.hibernate.criterion.Order.desc("id"));
		List<Transaction> list = this.getHibernateTemplate().findByCriteria(criteria);
		if (list != null && list.size() > 0) {
			/* Return the first one. /
			return list.get(0);
		}
		return null;
	}*/

	public List<Transaction> getTransactionsNotTransferedToVerification() {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.add(Restrictions.eq("transferedToVerification",Boolean.FALSE));
		criteria.addOrder(org.hibernate.criterion.Order.asc("id"));
		return this.getHibernateTemplate().findByCriteria(criteria);
	}

}
