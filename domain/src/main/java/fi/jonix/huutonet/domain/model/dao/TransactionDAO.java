package fi.jonix.huutonet.domain.model.dao;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import org.springframework.dao.DataAccessException;

import fi.jonix.huutonet.domain.model.Order;
import fi.jonix.huutonet.domain.model.Transaction;

public interface TransactionDAO extends GenericDAO<Transaction, Long> {

	public Date getLatestTransactionDate() throws DataAccessException;
	public int identicalTransactionsFoundInDb(Transaction transaction);
	public int identicalTransactionsFoundInList(Transaction transaction, List<Transaction> transactions);
	public List<Transaction> getTransactionsForOrder(Order order);
	//public double getTotalAmountPaidForOrder(Order order);
	public BigDecimal getSumAmount(Date startDate, Date endDate);
	public BigDecimal getBalance();
	public BigDecimal getBalanceAfterTransaction(Transaction transaction);
	public List<Transaction> getTransactionsAfterGivenTransaction(Transaction transaction);
	public int getNbrTransactionsForCurrency(Long currency);
	public int getTotalNbrTransactions();
	public List<Transaction> getUnmappedNormalTransactions();
	public List<Transaction> getOrderRefunds();
	public List<Transaction> getTransactionsByPayer(String payer);
	//public Transaction getLastQDTransferTransaction();
	public List<Transaction> getTransactionsNotTransferedToVerification();

}
