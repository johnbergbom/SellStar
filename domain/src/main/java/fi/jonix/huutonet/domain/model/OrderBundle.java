package fi.jonix.huutonet.domain.model;

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
import javax.persistence.UniqueConstraint;

@Entity
@Table(name = "order_bundle", uniqueConstraints={ @UniqueConstraint(columnNames={"prov_order_id", "provider_id"}),
		@UniqueConstraint(columnNames={"transaction_id", "provider_id"}),
		@UniqueConstraint(columnNames={"paypal_id", "provider_id"}) } )
@SequenceGenerator(allocationSize = 1, name = "OrderBundleSeq", sequenceName = "order_bundle_id_seq")
public class OrderBundle {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "OrderBundleSeq")
	private Long id;

	/**
	 * For example the order_id from FocalPrice.
	 */
	@Column(nullable = true, name = "prov_order_id")
	private String providerOrderId;

	/**
	 * This one is often the same as the providerOrderId, but in some cases it's different,
	 * and from DealExtreme we don't get any payPalId at all (therefore we cannot associate
	 * emails in DxProcessor.processPaymentSent with any bundle).
	 */
	@Column(nullable = true, name = "paypal_id")
	private String payPalId;

	/**
	 * Provider transaction id.
	 */
	@Column(nullable = true, name = "transaction_id")
	private String transactionId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "customer_id", nullable = false)
	private Customer customer;

	@ManyToOne(fetch=FetchType.LAZY)
	@JoinColumn(name="provider_id", nullable=false)
	private Provider provider;

	@ManyToOne(fetch=FetchType.LAZY)
	@JoinColumn(name="seller_id", nullable=false)
	private Seller seller;
	
	/**
	 * This is what the customer pays for shipping.
	 * 
	 * IMPORTANT: always zero this field when all orders in the bundle are canceled/refunded (point
	 * in time: when the last order is canceled/refunded). This is done for two reasons:
	 * 1.) We don't want to remind the customer about paying for already canceled orders (for the
	 *     case where nothing was yet paid for the order).
	 * 2.) All refunds are handled as "paid-too-much", so if the customer has paid (partly or
	 *     completely), then the targetedPaid will automatically be moved to another order or
	 *     refunded as money to the customer (customer's own choice).
	 */
	@Column(nullable=false, name="cust_shipping_cost_mc_inc_vat", precision=12, scale=6)
	private BigDecimal customerShippingCostInMarketCurrencyInclVat;

	@Column(nullable=false, name="cust_shipping_cost_mc_exc_vat", precision=12, scale=6)
	private BigDecimal customerShippingCostInMarketCurrencyExclVat;

	@Column(nullable=false, name="cust_shipping_cost_euro_inc_vat", precision=12, scale=6)
	private BigDecimal customerShippingCostInEuroInclVat;

	@Column(nullable=false, name="cust_shipping_cost_euro_exc_vat", precision=12, scale=6)
	private BigDecimal customerShippingCostInEuroExclVat;

	/**
	 * About this field see notes for Order.targetedPaid.
	 */
	@Column(nullable = false, name = "targeted_ship_mc", precision=12, scale=6)
	private BigDecimal targetedPaidShippingCostInMarketCurrency;
	
	/**
	 * This is what the provider charges for the bundle specific costs. They
	 * consist of: drop shipping, shipping cost, tracking code.
	 */
	@Column(nullable = true, name = "prov_ship_pc_inc_vat", precision=12, scale=6)
	private BigDecimal providerShippingCostInProviderCurrencyInclVat;
	
	@Column(nullable = true, name = "prov_ship_pc_exc_vat", precision=12, scale=6)
	private BigDecimal providerShippingCostInProviderCurrencyExclVat;
	
	@Column(nullable = true, name = "prov_ship_euro_inc_vat", precision=12, scale=6)
	private BigDecimal providerShippingCostInEuroInclVat;

	@Column(nullable = true, name = "prov_ship_euro_exc_vat", precision=12, scale=6)
	private BigDecimal providerShippingCostInEuroExclVat;

	/**
	 * The lastCustomerUpdateSent field is used for telling when the customer last got
	 * an information email.
	 * 
	 * Why don't we use the field dateKicked for this?
	 * Answer: these fields tell different things. dateKicked is a customer specific
	 * field telling when the customer was last reminded. On the other hand
	 * lastCustomerUpdateSent is an order specific field telling when SellStar last
	 * pushed automatic status information to the customer. dateKicked is a nullable
	 * field which isn't set until a reminder is sent out. On the other hand
	 * lastCustomerUpdateSent is not nullable.
	 */
	@Column(nullable = false, name = "last_cust_update_sent")
	private Date lastCustomerUpdateSent;

	@Column(nullable = false, name = "first_name")
	private String firstName;

	@Column(nullable = false, name = "last_name")
	private String lastName;

	@Column(nullable = false, name = "address")
	private String address;

	@Column(nullable = false, name = "postcode")
	private String postcode;

	@Column(nullable = false, name = "city")
	private String city;

	/**
	 * Cost of resending including the vat that we pay to the provider.
	 */
	@Column(nullable = false, name = "resend_loss_euro_inc_vat", precision=12, scale=6)
	private BigDecimal resendLossInEuroInclVat;
	
	/**
	 * Cost of resending excluding the vat that we pay to the provider.
	 */
	@Column(nullable = false, name = "resend_loss_euro_exc_vat", precision=12, scale=6)
	private BigDecimal resendLossInEuroExclVat;
	
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "pcurrency_id", nullable = false)
	private Currency usedProviderCurrency;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "ccurrency_id", nullable = false)
	private Currency usedCustomerCurrency;

	/**
	 * This one tells the maximum price of a bundle. When a new order is added and the bundle
	 * grows bigger than this value, then a new bundle is created instead of adding more
	 * orders to the old bundle. The value is gotten from sellerMarket.maxBundleSizeInMarketCurrencyExclVat
	 * at bundle creation time.
	 */
	@Column(nullable = false, name = "max_bundle_size_mc_exc_vat", precision=12, scale=6)
	private BigDecimal maxBundleSizeInMarketCurrencyExclVat;
	
	@Column(nullable = false, name = "customer_ship_vat_perc", precision=12, scale=6)
	private BigDecimal customerShippingVatPercentage;

	@Column(nullable = true, name = "provider_ship_vat_perc", precision=12, scale=6)
	private BigDecimal providerShippingVatPercentage;

	@Column(nullable = true, name = "provider_ship_resend_vat_perc", precision=12, scale=6)
	private BigDecimal providerShippingResendingVatPercentage;

	/**
	 * This is set to false at order creation time and when the order was canceled (zeroSalesPrice). That then
	 * tells SellStar that this order should be included in the next verification (sending of money to the company
	 * account).
	 */
	@Column(nullable = false, name = "transfered_to_verific")
	private Boolean transferedToVerification;

	/**
	 * This is true for orders that used the old verification scheme. It doesn't fill any function since
	 * new and old bundles are handled identically in the code, so we can remove this field at some point.
	 * EXCEPTION: when we implement the "fixa automaattinen täsmäytys för liian isot maksut" in Test.java,
	 * then we need to make sure that no correction is sent to the verification for bundles having
	 * oldVerification = true.
	 */
	@Column(nullable = false, name = "old_verification")
	private Boolean oldVerification;

	/**
	 * This field can be used by the operator for making free text comments regarding
	 * orders or bundles for which something extraordinary happened.
	 */
	@Column(nullable = true, name = "comment")
	private String comment;

	public Long getId() {
		return id;
	}

	public void setProviderOrderId(String providerOrderId) {
		this.providerOrderId = providerOrderId;
	}

	public String getProviderOrderId() {
		return this.providerOrderId;
	}

	public void setTransactionId(String transactionId) {
		this.transactionId = transactionId;
	}

	public String getTransactionId() {
		return this.transactionId;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public void setCustomer(Customer customer) {
		this.customer = customer;
	}

	public Customer getCustomer() {
		return customer;
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

	public void setLastCustomerUpdateSent(Date lastCustomerUpdateSent) {
		this.lastCustomerUpdateSent = lastCustomerUpdateSent;
	}

	public Date getLastCustomerUpdateSent() {
		return lastCustomerUpdateSent;
	}

	public void setFirstName(String firstName) {
		this.firstName = firstName;
	}

	public String getFirstName() {
		return firstName;
	}

	public void setLastName(String lastName) {
		this.lastName = lastName;
	}

	public String getLastName() {
		return lastName;
	}

	public void setAddress(String address) {
		this.address = address;
	}

	public String getAddress() {
		return address;
	}

	public void setPostcode(String postcode) {
		this.postcode = postcode;
	}

	public String getPostcode() {
		return postcode;
	}

	public void setCity(String city) {
		this.city = city;
	}

	public String getCity() {
		return city;
	}

	public void setTargetedPaidShippingCostInMarketCurrency(
			BigDecimal targetedPaidShippingCostInMarketCurrency) {
		this.targetedPaidShippingCostInMarketCurrency = targetedPaidShippingCostInMarketCurrency;
	}

	public BigDecimal getTargetedPaidShippingCostInMarketCurrency() {
		return targetedPaidShippingCostInMarketCurrency;
	}

	public void setPayPalId(String payPalId) {
		this.payPalId = payPalId;
	}

	public String getPayPalId() {
		return payPalId;
	}

	public void setUsedProviderCurrency(Currency usedProviderCurrency) {
		this.usedProviderCurrency = usedProviderCurrency;
	}

	public Currency getUsedProviderCurrency() {
		return usedProviderCurrency;
	}

	public void setUsedCustomerCurrency(Currency usedCustomerCurrency) {
		this.usedCustomerCurrency = usedCustomerCurrency;
	}

	public Currency getUsedCustomerCurrency() {
		return usedCustomerCurrency;
	}

	public void setCustomerShippingCostInMarketCurrencyExclVat(
			BigDecimal customerShippingCostInMarketCurrencyExclVat) {
		this.customerShippingCostInMarketCurrencyExclVat = customerShippingCostInMarketCurrencyExclVat;
	}

	public BigDecimal getCustomerShippingCostInMarketCurrencyExclVat() {
		return customerShippingCostInMarketCurrencyExclVat;
	}

	public void setProviderShippingCostInProviderCurrencyExclVat(
			BigDecimal providerShippingCostInProviderCurrencyExclVat) {
		this.providerShippingCostInProviderCurrencyExclVat = providerShippingCostInProviderCurrencyExclVat;
	}

	public BigDecimal getProviderShippingCostInProviderCurrencyExclVat() {
		return providerShippingCostInProviderCurrencyExclVat;
	}

	public void setProviderShippingCostInEuroExclVat(
			BigDecimal providerShippingCostInEuroExclVat) {
		this.providerShippingCostInEuroExclVat = providerShippingCostInEuroExclVat;
	}

	public BigDecimal getProviderShippingCostInEuroExclVat() {
		return providerShippingCostInEuroExclVat;
	}

	public void setResendLossInEuroExclVat(BigDecimal resendLossInEuroExclVat) {
		this.resendLossInEuroExclVat = resendLossInEuroExclVat;
	}

	public BigDecimal getResendLossInEuroExclVat() {
		return resendLossInEuroExclVat;
	}

	public void setCustomerShippingCostInEuroExclVat(
			BigDecimal customerShippingCostInEuroExclVat) {
		this.customerShippingCostInEuroExclVat = customerShippingCostInEuroExclVat;
	}

	public BigDecimal getCustomerShippingCostInEuroExclVat() {
		return customerShippingCostInEuroExclVat;
	}

	public void setCustomerShippingCostInMarketCurrencyInclVat(
			BigDecimal customerShippingCostInMarketCurrencyInclVat) {
		this.customerShippingCostInMarketCurrencyInclVat = customerShippingCostInMarketCurrencyInclVat;
	}

	public BigDecimal getCustomerShippingCostInMarketCurrencyInclVat() {
		return customerShippingCostInMarketCurrencyInclVat;
	}

	public void setCustomerShippingCostInEuroInclVat(
			BigDecimal customerShippingCostInEuroInclVat) {
		this.customerShippingCostInEuroInclVat = customerShippingCostInEuroInclVat;
	}

	public BigDecimal getCustomerShippingCostInEuroInclVat() {
		return customerShippingCostInEuroInclVat;
	}

	public void setProviderShippingCostInProviderCurrencyInclVat(
			BigDecimal providerShippingCostInProviderCurrencyInclVat) {
		this.providerShippingCostInProviderCurrencyInclVat = providerShippingCostInProviderCurrencyInclVat;
	}

	public BigDecimal getProviderShippingCostInProviderCurrencyInclVat() {
		return providerShippingCostInProviderCurrencyInclVat;
	}

	public void setProviderShippingCostInEuroInclVat(
			BigDecimal providerShippingCostInEuroInclVat) {
		this.providerShippingCostInEuroInclVat = providerShippingCostInEuroInclVat;
	}

	public BigDecimal getProviderShippingCostInEuroInclVat() {
		return providerShippingCostInEuroInclVat;
	}

	public void setResendLossInEuroInclVat(BigDecimal resendLossInEuroInclVat) {
		this.resendLossInEuroInclVat = resendLossInEuroInclVat;
	}

	public BigDecimal getResendLossInEuroInclVat() {
		return resendLossInEuroInclVat;
	}

	public void setMaxBundleSizeInMarketCurrencyExclVat(
			BigDecimal maxBundleSizeInMarketCurrencyExclVat) {
		this.maxBundleSizeInMarketCurrencyExclVat = maxBundleSizeInMarketCurrencyExclVat;
	}

	public BigDecimal getMaxBundleSizeInMarketCurrencyExclVat() {
		return maxBundleSizeInMarketCurrencyExclVat;
	}

	public void setCustomerShippingVatPercentage(
			BigDecimal customerShippingVatPercentage) {
		this.customerShippingVatPercentage = customerShippingVatPercentage;
	}

	public BigDecimal getCustomerShippingVatPercentage() {
		return customerShippingVatPercentage;
	}

	public void setProviderShippingVatPercentage(
			BigDecimal providerShippingVatPercentage) {
		this.providerShippingVatPercentage = providerShippingVatPercentage;
	}

	public BigDecimal getProviderShippingVatPercentage() {
		return providerShippingVatPercentage;
	}

	public void setProviderShippingResendingVatPercentage(
			BigDecimal providerShippingResendingVatPercentage) {
		this.providerShippingResendingVatPercentage = providerShippingResendingVatPercentage;
	}

	public BigDecimal getProviderShippingResendingVatPercentage() {
		return providerShippingResendingVatPercentage;
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

	public void setComment(String comment) {
		this.comment = comment;
	}

	public String getComment() {
		return comment;
	}

}
