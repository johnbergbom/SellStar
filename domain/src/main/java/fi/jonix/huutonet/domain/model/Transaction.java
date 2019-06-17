package fi.jonix.huutonet.domain.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

@Entity
@Table(name="transaction")
@SequenceGenerator(allocationSize=1, name="TransactionSeq", sequenceName="transaction_id_seq")
public class Transaction implements Serializable {

	private static final long serialVersionUID = 225364338916867278L;

	@Id
	@GeneratedValue(strategy=GenerationType.SEQUENCE, generator="TransactionSeq")
	private Long id;
	
	@Column(nullable=false, name="date")
	private Date date;
	
	@Column(nullable=false, name="payer")
	private String payer;
	
	@Column(nullable=false, name="description")
	private String description;
	
	@Column(nullable=false, name="message")
	private String message;
	
	@Column(nullable=false, name="amount", precision=12, scale=6)
	private BigDecimal amount;

	@Column(nullable=false, name="ref_number")
	private String referenceNumber;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "currency_id", nullable = false)
	private Currency currency;

	/**
	 * In the normal case this one shouldn't be null, but in case
	 * the customer entered the wrong reference id the transaction
	 * cannot be associated with any order. This case is actually
	 * an error which needs to be resolved manually.
	 * 
	 * A valid case when a payment isn't associated with any order
	 * is when provision is paid to Huutonet + when paying salary to
	 * ourselves.
	 */
	@ManyToOne(fetch=FetchType.LAZY)
	@JoinColumn(name="order_id", nullable=true)
	private Order order;
	
	/**
	 * This column isn't normally used, but for transactions that
	 * aren't mapped to any order (=special transactions) this
	 * field can be used to explain what kind of transaction this
	 * is. Normally it's an error if a transaction isn't associated
	 * with any order, but if operatorDescription isn't null, then
	 * it's not considered to be an error.
	 */
	@Column(nullable=true, name="oper_descr")
	private String operatorDescription;

	/**
	 * This is set to false when a new transaction was fetched from the bank. That then tells SellStar
	 * that this order should be included in the next verification (sending of money to the company
	 * account).
	 */
	@Column(nullable = false, name = "transfered_to_verific")
	private Boolean transferedToVerification;
	
	/**
	 * This is true for orders that used the old verification scheme. It doesn't fill any function since
	 * new and old transactions are handled identically in the code, so we can remove this field at some point.
	 */
	@Column(nullable = false, name = "old_verification")
	private Boolean oldVerification;

	public Transaction() {
	}

	public Transaction(Date date, String payer, String description, String message, BigDecimal amount, String referenceNumber) {
		this.date = date;
		this.payer = payer;
		this.description = description;
		this.message = message;
		this.amount = amount;
		this.referenceNumber = referenceNumber;
	}

	public Long getId() {
		return id;
	}

	public void setDate(Date date) {
		this.date = date;
	}

	public Date getDate() {
		return date;
	}

	public void setPayer(String payer) {
		this.payer = payer;
	}

	public String getPayer() {
		return payer;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getDescription() {
		return description;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public String getMessage() {
		return message;
	}

	public void setAmount(BigDecimal amount) {
		this.amount = amount;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public void setReferenceNumber(String referenceNumber) {
		this.referenceNumber = referenceNumber;
	}

	public String getReferenceNumber() {
		return referenceNumber;
	}

	public void setOrder(Order order) {
		this.order = order;
	}

	public Order getOrder() {
		return order;
	}
	
	/**
	 * This method compares two Transactions checking the _contents_ of the fields, _not_ using the id
	 * nor the order_id column (because id and order_id doesn't exist in the bank).
	 * 
	 * @param tr
	 * @return
	 */
	public boolean equalsDontCompareWithIdNorWithOrder(Transaction tr) {
		if (tr == null) {
			return false;
		}
		if (!this.getDate().equals(tr.getDate())) {
			return false;
		}
		if (!this.getPayer().equals(tr.getPayer())) {
			return false;
		}
		if (!this.getDescription().equals(tr.getDescription())) {
			return false;
		}
		if (!this.getMessage().equals(tr.getMessage())) {
			return false;
		}
		if (this.getAmount().compareTo(tr.getAmount()) != 0) {
			return false;
		}
		if (!this.getReferenceNumber().equals(tr.getReferenceNumber())) {
			return false;
		}
		if (!this.getCurrency().getId().equals(tr.getCurrency().getId())) {
			return false;
		}
		return true;
	}

	public void setOperatorDescription(String operatorDescription) {
		this.operatorDescription = operatorDescription;
	}

	public String getOperatorDescription() {
		return operatorDescription;
	}

	public void setCurrency(Currency currency) {
		this.currency = currency;
	}

	public Currency getCurrency() {
		return currency;
	}

	public void setTransferedToVerification(Boolean transferedToVerification) {
		this.transferedToVerification = transferedToVerification;
	}

	public Boolean getTransferedToVerification() {
		return transferedToVerification;
	}

	public void setOldVerification(Boolean oldVerification) {
		this.oldVerification = oldVerification;
	}

	public Boolean getOldVerification() {
		return oldVerification;
	}

}
