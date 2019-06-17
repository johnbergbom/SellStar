package fi.jonix.huutonet.domain.model;

import java.math.BigDecimal;

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

@Entity
@Table(name = "seller")
@SequenceGenerator(allocationSize = 1, name = "SellerSeq", sequenceName = "seller_id_seq")
public class Seller {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SellerSeq")
	private Long id;

	@Column(name = "name", nullable = false, length=64)
	private String name;

	@Column(name = "signature", nullable = false, length=64, unique=true)
	private String signature;

	/**
	 * This is the public email address for the seller, which is exposed
	 * to the market as well as to the customers (for example team.lauber@gmail.com).
	 */
	@ManyToOne(fetch=FetchType.EAGER, cascade = { CascadeType.ALL })
	@JoinColumn(name="system_email_id", nullable=false)
	private SystemEmail systemEmail;
	
	@ManyToOne(fetch=FetchType.LAZY, cascade = { CascadeType.ALL })
	@JoinColumn(name="user_id", nullable=false)
	private User user;

	/**
	 * These "forward" fields describe to where different types of emails
	 * are to be forwarded. They are nullable and the default forwarding
	 * address is the address of the user (user.emailAddress and NOT
	 * seller.emailAddress).
	 */
	@Column(name = "fwd_public_quest", nullable = true, length=128)
	private String forwardPublicQuestions;

	@Column(name = "fwd_private_quest", nullable = true, length=128)
	private String forwardPrivateQuestions;
	
	@Column(name = "fwd_unknown_emails", nullable = true, length=128)
	private String forwardUnknownEmails;

	@Column(name = "fwd_customer_emails", nullable = true, length=128)
	private String forwardCustomerEmails;

	@Column(name = "fwd_error_reports", nullable = true, length=128)
	private String forwardErrorReports;

	@Column(name = "fwd_oper_problems", nullable = true, length=128)
	private String forwardOperationProblems;

	@Column(name = "store_url", nullable = false, length=128)
	private String storeUrl;

	@Column(name = "delivery_text", nullable = false, length=4096)
	private String deliveryText;

	@Column(nullable = false, name = "margin", precision=12, scale=6)
	private BigDecimal margin;

	/**
	 * Value added tax for the seller (i.e. the vat that the consumer pays to us). This percentage
	 * is always use for the shipping cost. Most products will also have this vat percentage, but
	 * some individual products could have a lower vat percentage, but if so, then that's taken
	 * care of in some other way.
	 */
	@Column(nullable = false, name = "customer_vat_perc", precision=12, scale=6)
	private BigDecimal customerVatPercentage;

	/**
	 * This field tells how much we should add for shipping:
	 * priceWithShipping = finalSalesPrice * shippingCostMultiplier
	 */
	@Column(nullable = false, name = "ship_cost_multiplier", precision=12, scale=6)
	private BigDecimal shippingCostMultiplier;

	@Column(name = "tracking_always", nullable = false)
	private Boolean trackingAlways;

	public Long getId() {
		return id;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}

	public void setSignature(String signature) {
		this.signature = signature;
	}

	public String getSignature() {
		return signature;
	}

	public void setUser(User user) {
		this.user = user;
	}

	public User getUser() {
		return user;
	}

	public void setForwardPublicQuestions(String forwardPublicQuestions) {
		this.forwardPublicQuestions = forwardPublicQuestions;
	}

	public String getForwardPublicQuestions() {
		return forwardPublicQuestions;
	}

	public void setForwardPrivateQuestions(String forwardPrivateQuestions) {
		this.forwardPrivateQuestions = forwardPrivateQuestions;
	}

	public String getForwardPrivateQuestions() {
		return forwardPrivateQuestions;
	}

	public void setForwardUnknownEmails(String forwardUnknownEmails) {
		this.forwardUnknownEmails = forwardUnknownEmails;
	}

	public String getForwardUnknownEmails() {
		return forwardUnknownEmails;
	}

	public void setForwardCustomerEmails(String forwardCustomerEmails) {
		this.forwardCustomerEmails = forwardCustomerEmails;
	}

	public String getForwardCustomerEmails() {
		return forwardCustomerEmails;
	}

	public void setForwardErrorReports(String forwardErrorReports) {
		this.forwardErrorReports = forwardErrorReports;
	}

	public String getForwardErrorReports() {
		return forwardErrorReports;
	}

	public void setForwardOperationProblems(String forwardOperationProblems) {
		this.forwardOperationProblems = forwardOperationProblems;
	}

	public String getForwardOperationProblems() {
		return forwardOperationProblems;
	}

	public void setMargin(BigDecimal margin) {
		this.margin = margin;
	}

	public BigDecimal getMargin() {
		return margin;
	}

	public void setSystemEmail(SystemEmail systemEmail) {
		this.systemEmail = systemEmail;
	}

	public SystemEmail getSystemEmail() {
		return systemEmail;
	}

	public void setStoreUrl(String storeUrl) {
		this.storeUrl = storeUrl;
	}

	public String getStoreUrl() {
		return storeUrl;
	}

	public void setTrackingAlways(Boolean trackingAlways) {
		this.trackingAlways = trackingAlways;
	}

	public Boolean getTrackingAlways() {
		return trackingAlways;
	}

	public void setDeliveryText(String deliveryText) {
		this.deliveryText = deliveryText;
	}

	public String getDeliveryText() {
		return deliveryText;
	}

	public void setShippingCostMultiplier(BigDecimal shippingCostMultiplier) {
		this.shippingCostMultiplier = shippingCostMultiplier;
	}

	public BigDecimal getShippingCostMultiplier() {
		return shippingCostMultiplier;
	}

	public void setCustomerVatPercentage(BigDecimal customerVatPercentage) {
		this.customerVatPercentage = customerVatPercentage;
	}

	public BigDecimal getCustomerVatPercentage() {
		return customerVatPercentage;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof Seller) {
			Seller another = (Seller) obj;
			return this.id.equals(another.getId());
		}
		return false;
	}
	
	@Override
	public int hashCode() {
		return new Long(id).intValue();
	}
	
}
