package fi.jonix.huutonet.domain.model;

import java.io.Serializable;

import javax.persistence.CascadeType;
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

import org.hibernate.annotations.Index;

import java.util.Date;

@Entity
@Table(name="email")
@SequenceGenerator(allocationSize=1, name="EmailSeq", sequenceName="email_id_seq")
public class Email implements Serializable {

	/** Tells how many times an email should be attempted to send before giving up. */
	public static int MAX_SENDING_ATTEMPTS = 10;
	
	/** Type */
	public static int REAL_EMAIL = 1;
	public static int FAKE_EMAIL = 2;

	/** Direction */
	public static int IN = 1;
	public static int OUT_PENDING = 2;
	public static int OUT_SENT = 3;

	/** Priority. Emails with priority LOW will not be sent out right away. Rather they are sent as a summary report
	 * once per day. They are intented for low priority operator emails. */
	public static int PRIORITY_LOW = 5;
	public static int PRIORITY_NORMAL = 10;
	
	private static final long serialVersionUID = 8083143548163607062L;

	@Id
	@GeneratedValue(strategy=GenerationType.SEQUENCE, generator="EmailSeq")
	private Long id;
	
	@Column(nullable=false, name="date")
	private Date date;
	
	@ManyToOne(fetch=FetchType.LAZY, cascade = { CascadeType.ALL })
	@JoinColumn(name="system_email_id", nullable=false)
	private SystemEmail systemEmail;

	@Column(nullable=false, name="sender")
	private String sender;
	
	@Column(nullable=false, name="receiver")
	private String receiver;
	
	@Column(nullable=false, name="subject")
	private String subject;
	
	@Column(nullable=false, name="contents")
	private String contents;

	/**
	 * 1 = Real email
	 * 2 = Fake sending of email
	 */
	@Column(nullable=false, name = "type")
	@Index(name = "email_typ_index")
	private Integer type;

	/**
	 * 1 = in
	 * 2 = out pending
	 * 3 = out sent
	 */
	@Column(nullable=false, name = "direction")
	@Index(name = "email_dir_index")
	private Integer direction;

	@Column(nullable=false, name = "sending_attempts")
	private Integer sendingAttempts;

	/**
	 * An email is associated with a bundle if the email was received after
	 * the orders were paid but before all orders in the bundle were shipped
	 * from the provider. The only purpose of this field is to make it possible
	 * to easily send emails to the operator where provider conversation as
	 * well as all customer communication is included.
	 */
	@ManyToOne(fetch=FetchType.LAZY)
	@JoinColumn(name="order_bundle_id", nullable=true)
	@Index(name = "email_ob_index")
	private	OrderBundle orderBundle;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "customer_id", nullable = true)
	private Customer customer;

	@Column(nullable=true, name = "priority") //TODO: set this one to nullable = false (requires deployment of new google-order and posttracker)
	private Integer priority;

	public Long getId() {
		return id;
	}

	public void setDate(Date date) {
		this.date = date;
	}

	public Date getDate() {
		return date;
	}

	public void setSystemEmail(SystemEmail systemEmail) {
		this.systemEmail = systemEmail;
	}

	public SystemEmail getSystemEmail() {
		return systemEmail;
	}

	public void setReceiver(String receiver) {
		this.receiver = receiver;
	}

	public String getReceiver() {
		return receiver;
	}

	public void setSubject(String subject) {
		this.subject = subject;
	}

	public String getSubject() {
		return subject;
	}

	public void setContents(String contents) {
		this.contents = contents;
	}

	public String getContents() {
		return contents;
	}

	public void setType(Integer type) {
		this.type = type;
	}

	public Integer getType() {
		return type;
	}

	public void setDirection(Integer direction) {
		this.direction = direction;
	}

	public Integer getDirection() {
		return direction;
	}

	public void setSender(String sender) {
		this.sender = sender;
	}

	public String getSender() {
		return sender;
	}

	public void setSendingAttempts(Integer sendingAttempts) {
		this.sendingAttempts = sendingAttempts;
	}

	public Integer getSendingAttempts() {
		return sendingAttempts;
	}

	public void setOrderBundle(OrderBundle orderBundle) {
		this.orderBundle = orderBundle;
	}

	public OrderBundle getOrderBundle() {
		return orderBundle;
	}

	public void setCustomer(Customer customer) {
		this.customer = customer;
	}

	public Customer getCustomer() {
		return customer;
	}

	public void setPriority(Integer priority) {
		this.priority = priority;
	}

	public Integer getPriority() {
		return priority;
	}

}
