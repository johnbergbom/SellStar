package fi.jonix.huutonet.domain.model;

import java.io.Serializable;
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

import fi.jonix.huutonet.domain.ModuleNames;

/**
 * This class keeps track of errors.
 * 
 * Note: At the point where we get several bank accounts we might want to add information
 * about the used bank to this class.
 * 
 * TODO: take the market, provider and seller fields into use. Currently the full capabilities
 * of error handling isn't in use since these fields aren't set. We can start using these for
 * example for the listing. I just need to figure out a good way to figure out what
 * market/provider/seller was used when the crash came.
 * 
 * @author john
 *
 */
@Entity
@Table(name = "error_tracking")
@SequenceGenerator(allocationSize = 1, name = "ErrorTrackingSeq", sequenceName = "error_tracking_id_seq")
public class ErrorTracking implements Serializable {

	private static final long serialVersionUID = -6585486738115192231L;

	public static String GLOBAL_ERROR = "ALL";
	//public static String PAYMENT_ERROR = "PAYMENT_ERROR";
	public static String MODULE_STOPPED = "STOPPED";
	
	/** An error in one of the following modules makes SellStar go into the PAYMENT_ERROR state. */
	public static final String PAYMENT_ERROR_MODULES[] = { ModuleNames.FETCH_TRANSACTIONS, ModuleNames.MAKE_REFUNDS, ModuleNames.TRANSFER_TO_QD };
	
	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ErrorTrackingSeq")
	private Long id;

	/**
	 * Tells what command caused the error, can be for example "listitems",
	 * or "ALL" for global errors.
	 */
	@Column(nullable = false, name = "command")
	private String command;

	/**
	 * Arguments to the command (if any).
	 * 
	 * NOTE: make sure that bank codes are not stored into this column because
	 * of some coding mistake.
	 */
	@Column(nullable = true, name = "arguments")
	private String arguments;

	/**
	 * This one is set at least for listing.
	 */
	@ManyToOne(fetch=FetchType.LAZY)
	@JoinColumn(name = "market_id", nullable=true)
	private Market market;

	/**
	 * This one is set for placeOrders and possibly also for listing.
	 */
	@ManyToOne(fetch=FetchType.LAZY)
	@JoinColumn(name="provider_id", nullable=true)
	private Provider provider;

	/**
	 * This one is only set for checkEmail and for listing.
	 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "seller_id", nullable = true)
	private Seller seller;

	/**
	 * When the error occurred.
	 */
	@Column(nullable = false, name = "date")
	private Date date;

	/**
	 * Null if this error is open or timestamp of acknowledgement if it has been
	 * taken care of (by the operator or automatically).
	 */
	@Column(nullable = true, name = "acknowledged")
	private Date acknowledged;
	
	/**
	 * Short message describing the error.
	 */
	@Column(nullable = false, name = "message")
	private String message;

	/**
	 * Complete stack trace for the error.
	 */
	@Column(nullable = false, name = "stacktrace")
	private String stacktrace;

	public Long getId() {
		return id;
	}

	public void setCommand(String command) {
		this.command = command;
	}

	public String getCommand() {
		return command;
	}

	public void setDate(Date date) {
		this.date = date;
	}

	public Date getDate() {
		return date;
	}

	public void setAcknowledged(Date acknowledged) {
		this.acknowledged = acknowledged;
	}

	public Date getAcknowledged() {
		return acknowledged;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public String getMessage() {
		return message;
	}

	public void setStacktrace(String stacktrace) {
		this.stacktrace = stacktrace;
	}

	public String getStacktrace() {
		return stacktrace;
	}

	public void setMarket(Market market) {
		this.market = market;
	}

	public Market getMarket() {
		return market;
	}

	public void setProvider(Provider provider) {
		this.provider = provider;
	}

	public Provider getProvider() {
		return provider;
	}

	public void setSeller(Seller seller) {
		this.seller = seller;
	}

	public Seller getSeller() {
		return seller;
	}

	public void setArguments(String arguments) {
		this.arguments = arguments;
	}

	public String getArguments() {
		return arguments;
	}

}
