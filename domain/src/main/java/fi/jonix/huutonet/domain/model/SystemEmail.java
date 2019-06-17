package fi.jonix.huutonet.domain.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

/**
 * This table contains information for system emails, i.e. mailboxes that SellStar uses (as opposed to
 * for example customer email addresses - to which SellStar _sends_ emails but cannot logon to the
 * customer's mailboxes).
 * 
 * @author john
 *
 */
@Entity
@Table(name = "system_email")
@SequenceGenerator(allocationSize = 1, name = "SystemEmailSeq", sequenceName = "system_email_id_seq")
public class SystemEmail {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SystemEmailSeq")
	private Long id;

	@Column(name = "email_address", nullable = false, length=64, unique=true)
	private String emailAddress;

	@Column(name = "name", nullable = false, length=64)
	private String name;

	@Column(name = "email_password", nullable = false, length=64)
	private String emailPassword;

	@Column(name = "email_inbound_protocol", nullable = false, length=64)
	private String emailInboundProtocol;

	@Column(name = "email_outbound_protocol", nullable = false, length=64)
	private String emailOutboundProtocol;

	@Column(name = "email_inbound_port", nullable = false)
	private Integer emailInboundPort;

	@Column(name = "email_outbound_port", nullable = false)
	private Integer emailOutboundPort;

	@Column(name = "email_inbound_ssl", nullable = false)
	private Boolean emailInboundSsl;

	@Column(name = "email_inbound_host", nullable = false, length=64)
	private String emailInboundHost;

	@Column(name = "email_outbound_host", nullable = false, length=64)
	private String emailOutboundHost;

	/**
	 * If this is true, then SellStar will regularily check this mail box for new emails.
	 */
	@Column(name = "check_email", nullable = false)
	private Boolean checkEmail;

	public Long getId() {
		return id;
	}

	public void setEmailAddress(String emailAddress) {
		this.emailAddress = emailAddress;
	}

	public String getEmailAddress() {
		return emailAddress;
	}

	public void setEmailPassword(String emailPassword) {
		this.emailPassword = emailPassword;
	}

	public String getEmailPassword() {
		return emailPassword;
	}

	public void setEmailInboundProtocol(String emailInboundProtocol) {
		this.emailInboundProtocol = emailInboundProtocol;
	}

	public String getEmailInboundProtocol() {
		return emailInboundProtocol;
	}

	public void setEmailOutboundProtocol(String emailOutboundProtocol) {
		this.emailOutboundProtocol = emailOutboundProtocol;
	}

	public String getEmailOutboundProtocol() {
		return emailOutboundProtocol;
	}

	public void setEmailInboundPort(Integer emailInboundPort) {
		this.emailInboundPort = emailInboundPort;
	}

	public Integer getEmailInboundPort() {
		return emailInboundPort;
	}

	public void setEmailOutboundPort(Integer emailOutboundPort) {
		this.emailOutboundPort = emailOutboundPort;
	}

	public Integer getEmailOutboundPort() {
		return emailOutboundPort;
	}

	public void setEmailInboundSsl(Boolean emailInboundSsl) {
		this.emailInboundSsl = emailInboundSsl;
	}

	public Boolean getEmailInboundSsl() {
		return emailInboundSsl;
	}

	public void setEmailInboundHost(String emailInboundHost) {
		this.emailInboundHost = emailInboundHost;
	}

	public String getEmailInboundHost() {
		return emailInboundHost;
	}

	public void setEmailOutboundHost(String emailOutboundHost) {
		this.emailOutboundHost = emailOutboundHost;
	}

	public String getEmailOutboundHost() {
		return emailOutboundHost;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}

	public void setCheckEmail(Boolean checkEmail) {
		this.checkEmail = checkEmail;
	}

	public Boolean getCheckEmail() {
		return checkEmail;
	}

}
