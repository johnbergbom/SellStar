package fi.jonix.huutonet.domain.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

@Entity
@Table(name = "orders")
@SequenceGenerator(allocationSize = 1, name = "OrderSeq", sequenceName = "orders_id_seq")
public class Order implements Serializable {

	private static final long serialVersionUID = 4440989398139661964L;

	/* Normally we get an email which moves the order from state "ordered from provider"
	 * to state "confirmed by provider", but in case that email never showed up,
	 * let's also update orders in state "ordered from provider".
	 * 
	 * Do NOT update orders having state = customer cancel pending, because if that
	 * was done, then ProviderHandler.updateOrderStatus might set the state to
	 * for example stock shortage, and then we risk that the customer cancellation
	 * request is "forgotten". For the same reason don't fetch provider cancel pending
	 * either.
	 * => UPDATE: we DO fetch "provider cancel pending", because it can happen that
	 * the provider sometimes sends a packet although we've told them to cancel the
	 * order.
	 * 
	 * It's also necessary to fetch orders being in the state fulfilment center
	 * (although those won't be set to state shipped in updateStatusOfOrders),
	 * because it can happen that an order first gets into the state fulfillment center
	 * and _after_ that moves to the state stock shortage. */
	public static final int UPDATABLE_STATES[] = { Order.STATE_ORDERED_FROM_PROVIDER, Order.STATE_ORDER_CONFIRMED_BY_PROVIDER,
		Order.STATE_AWAITING_STOCK, Order.STATE_WAITING_FOR_SUPPLIER, Order.STATE_STOCK_SHORTAGE, Order.STATE_FULFILMENT_CENTER,
		Order.STATE_PROVIDER_CANCEL_PENDING };
	
	/**
	 * An order will always be in one of the following states (corresponds to
	 * the state field).
	 */

	/* The states < 100 concerns states before the customer has paid. */
	public static final int STATE_FAULTY_CONTACT_INFO = 5; //this one needs fixing of contact information
	public static final int STATE_NEW_OUT_OF_STOCK = 8;
	public static final int STATE_NEW = 10; //covers actions as well as "osta heti"
	public static final int STATE_PAID_TOO_LITTLE_ERROR = 20;

	/* The states 100-199 concerns states where the customer has paid but before
	 * the product has been ordered. */
	public static final int STATE_CUSTOMER_PAID = 130;
	public static final int STATE_CUSTOMER_PAID_INFORMED = 140;
	public static final int STATE_SHOULD_RESEND = 160;
	public static final int STATE_STOCK_SHORTAGE_BEFORE_ORDER = 180;
	public static final int STATE_NO_LONGER_FOR_SALE_AT_PROVIDER_EARLY = 190;

	/* The states 200-299 concerns states where the product is ordered but
	 * the order isn't yet closed. */
	public static final int STATE_ORDERED_FROM_PROVIDER_NO_ID = 210;
	public static final int STATE_ORDERED_FROM_PROVIDER = 220;
	public static final int STATE_ORDER_CONFIRMED_BY_PROVIDER = 230;
	public static final int STATE_AWAITING_STOCK = 233;
	public static final int STATE_WAITING_FOR_SUPPLIER = 236;
	public static final int STATE_STOCK_SHORTAGE = 240;
	//public static final int STATE_CUSTOMER_CANCEL_PENDING = 245;
	public static final int STATE_CUSTOMER_CANCEL_CONFIRMED = 247; 	//SellStar has approved the cancellation attempt by the customer
	public static final int STATE_PROVIDER_CANCEL_PENDING = 250; 	//this is when SellStar forwards the cancellation request to the provider
	public static final int STATE_PROVIDER_CANCEL_CONFIRMED = 260;
	public static final int STATE_NO_LONGER_FOR_SALE_AT_PROVIDER_LATE = 275;
	public static final int STATE_FULFILMENT_CENTER = 280;
	public static final int STATE_SHIPPED_BY_PROVIDER = 290;
	public static final int STATE_SHIPPED_BY_POSTOFFICE = 292;
	public static final int STATE_REACHED_DESTINATION_COUNTRY = 294;
	public static final int STATE_ARRIVED_TO_CUSTOMERS_POSTOFFICE = 296;
	public static final int STATE_FETCHED_BY_CUSTOMER = 298;

	/* The states 300-399 concerns transitional states. */
	public static final int STATE_MONEY_REFUNDED_BY_PROVIDER_FOR_CANCELED_ORDER = 320;
	//public static final int STATE_MONEY_REFUNDABLE_TO_CUSTOMER_FOR_EARLY_CANCEL = 325;
	//public static final int STATE_MONEY_REFUNDED_TO_CUSTOMER_FOR_CANCELED_ORDER = 330;
	public static final int STATE_OTHER_ERROR = 340;
	//public static final int STATE_REFUND_PENDING_FOR_SENT_ORDER = 350;
	//public static final int STATE_MONEY_REFUNDED_FOR_SENT_ORDER = 360;
	
	/* The states 400-499 concerns closed orders. */
	public static final int STATE_CLOSED = 450;
	public static final int STATE_CLOSED_CUSTOMER_NEVER_PAID = 460;
	
	// Important: see note about this state at the Order.finalSalesPrice field (and at the
	// OrderBundle.customerShippingCostInMarketCurrency field).
	public static final int STATE_CLOSED_ORDER_CANCELED = 470;
	public static final int STATE_CLOSED_RETURNED_TO_PROVIDER = 480;
	
	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "OrderSeq")
	private Long id;

	public Order() {
	}

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "ad_id", nullable = false)
	private Ad ad;

	@Column(nullable = false, name = "state")
	private Integer state;

	/**
	 * Reference number used for payment by customer.
	 */
	@Column(nullable = false, name = "reference", unique = true)
	private String customerReferenceNumber;

	/**
	 * IMPORTANT: always zero this field when an order is canceled/refunded. This is done for three reasons:
	 * 1.) We don't want to remind the customer about paying for already canceled orders (for the
	 *     case where nothing was yet paid for the order).
	 * 2.) All refunds are handled as "paid-too-much", so if the customer has paid (partly or
	 *     completely), then the targetedPaid will automatically be moved to another order or
	 *     refunded as money to the customer (customer's own choice).
	 * 3.) When an order was canceled/refunded we should decrease our sales numbers + vat to pay to the
	 *     government (total sales comes from sum(finalSalesPriceInMarketCurrencyInclVat) and total vat
	 *     comes from sum(finalSalesPriceInMarketCurrencyInclVat) - sum(finalSalesPriceInMarketCurrencyExclVat)).
	 */
	@Column(nullable = false, name = "fin_sp_mc_inc_vat", precision=12, scale=6)
	private BigDecimal finalSalesPriceInMarketCurrencyInclVat;

	@Column(nullable = false, name = "fin_sp_mc_exc_vat", precision=12, scale=6)
	private BigDecimal finalSalesPriceInMarketCurrencyExclVat;

	@Column(nullable = false, name = "fin_sp_euro_inc_vat", precision=12, scale=6)
	private BigDecimal finalSalesPriceInEuroInclVat;

	@Column(nullable = false, name = "fin_sp_euro_exc_vat", precision=12, scale=6)
	private BigDecimal finalSalesPriceInEuroExclVat;

	/**
	 * The targetedPaid field tells how much money SellStar determines that the customer
	 * has paid for the item. This is used because often the customer wants to pay several
	 * orders with one bank transfer. Let's say that the customer placed order A of 10 euros
	 * and order B of 10 euros and pays for these orders with one bank transfer using the
	 * reference number of A. Now it has been paid too much for order A while still B isn't
	 * paid for at all. The targeted payment however is 10 euros for A and 10 for B.
	 * 
	 * All refunds are created as "paid-too-much". SellStar creates cancelations/refunds by
	 * setting finalSalesPrice = 0. This makes targetedPaid > expectedPaid, which will be
	 * used by faultyPaymentFixer (in case the customer orders another product) for moving
	 * the excessive targeted payment to another order. If the customer rather wants us to
	 * pay back the money, then the refunder knows to pay back the excessive amount. At that
	 * point the refunder also needs to decrease the targetedPaid with the refunded amount.
	 * 
	 * If the customer is given a discount of X euros, then this field should be increased
	 * with X AND otherLoss should be increased with the same amount.
	 * 
	 * For this field it's enough to use the market currency because all the payments from
	 * the customer will come in market currency.
	 */
	@Column(nullable = false, name = "targeted_mc", precision=12, scale=6)
	private BigDecimal targetedPaidInMarketCurrency;
	
	/**
	 * The field "otherLoss" is normally zero but in case the revenue for this order is
	 * negative for some unexpected reason, then this field can be used. The following cases
	 * are identified:
	 * 1.) We need to pay back money for the customer although the product was already sent
	 *     (so we can no longer get a refund from the provider).
	 *     => set otherLoss = providerPrice;
	 * 2.) The customer cancels an order that has been out of stock for very long and
	 *     expects us to send him a similar product instead for the same price, although
	 *     the replacement product costs slightly more.
	 *     => set otherLoss = difference in price;
	 * 3.) The product got out of stock and the order is canceled and the customer wants to
	 *     get reimbursement in the form of interest (viivästyskorko).
	 * 4.) We want to give the customer a discount, for example if the customer forgot to pay
	 *     for the shipping and we decide to be nice to the customer (then the targetedPaid
	 *     should be increased with the same amount as with what otherLoss is increased).
	 *     See also info for the targetedPaid field.
	 * 5.) The customer returned the product and wants us to pay for the cost of sending the
	 *     packet to us.
	 *     => set "otherLoss = cost of returning product" as well as increase the targetPaid with
	 *     the same amount (this handles the shipping cost the same way as "paid too
	 *     much").
	 *     
	 * Note: resendings do NOT go here!
	 *     
	 * Note that this field tells the amount in euros. This is done because these losses can
	 * possibly come both in market currency and provider currency so a fixed currency (euro)
	 * is needed here.
	 * 
	 * Note: this field is purely for statistical purposes (so that we can get exact sales numbers
	 * in the sales report). It has no significance for how sales/vat is bookkeeped.
	 */
	@Column(nullable = false, name = "other_loss_euro", precision=12, scale=6)
	private BigDecimal otherLossInEuro;
	
	/**
	 * This value will usually be the same as the
	 * Ad.providerPriceAtListingTimeProviderCurrency, but can be different if
	 * the provider's price has changed between the listing to the market and
	 * the time that the product is ordered.
	 * Note: this is the price EXCLUDING the bundle specific costs (=drop shipping
	 * + shipping cost + tracking code). The bundle specific costs are stored in
	 * OrderBundle.providerShippingCost.
	 */
	@Column(nullable = true, name = "fin_pp_pc_inc_vat", precision=12, scale=6)
	private BigDecimal finalProviderPriceInProviderCurrencyInclVat;

	@Column(nullable = true, name = "fin_pp_pc_exc_vat", precision=12, scale=6)
	private BigDecimal finalProviderPriceInProviderCurrencyExclVat;

	@Column(nullable = true, name = "fin_pp_euro_inc_vat", precision=12, scale=6)
	private BigDecimal finalProviderPriceInEuroInclVat;

	@Column(nullable = true, name = "fin_pp_euro_exc_vat", precision=12, scale=6)
	private BigDecimal finalProviderPriceInEuroExclVat;

	/**
	 * If this one is false, then that means feedback hasn't been given to the
	 * customer (this should always be done at huutonet) after a deal -
	 * successful or not).
	 * 
	 * TODO: remove this field and handle the feedback_given as a state instead
	 * (because it's a huutonet specific thing, for example google-order doesn't
	 * have this at all). => Hmm, turning this into a state isn't necessarily
	 * such a good idea after all...
	 */
	@Column(nullable = false, name = "feedback_given")
	private Boolean feedbackGiven;

	@OneToMany(mappedBy = "order", cascade = { CascadeType.ALL }, fetch = FetchType.LAZY)
	private Set<StateChange> stateChanges;
	
	//TODO: remove this field since the same information exists in order.ad.market_id
	@ManyToOne(fetch=FetchType.LAZY)
	@JoinColumn(name = "market_id", nullable=false)
	private Market market;

	/**
	 * This field tells how many times the product has been order from the provider.
	 * This is normally zero for new orders and one for orders that have been ordered
	 * from the provider, but in case the customer complained that the product was
	 * broken and we need to order a new one, then sendCount will be two.
	 */
	@Column(nullable = false, name = "send_count")
	private Integer sendCount;

	/**
	 * The purpose of this field is to make it possible to handle the following case:
	 * the customer has ordered 10 products that are in the same bundle. However none
	 * of them can be ordered since _one_ of the products are out of stock. In that
	 * case we can move the missing product to a separate bundle and then mark the
	 * order with tryToBundle = false.
	 * 
	 * TODO: implement code that actually does this.
	 */
	@Column(name = "try_to_bundle", nullable = false)
	private Boolean tryToBundle;

	@ManyToOne(fetch=FetchType.LAZY)
	@JoinColumn(name="order_bundle_id", nullable=false)
	private	OrderBundle orderBundle;
	
	@ManyToOne(fetch=FetchType.LAZY)
	@JoinColumn(name="tracking_id", nullable=true)
	private	Tracking tracking;
	
	/**
	 * This column is used for describing what refundings we are expecting to show up
	 * in the transaction table. It's set whenever we make a refund and unset whenever
	 * the payment is registered in BankHelper. If refunding ever goes below zero, then
	 * we know that something very fishy is going on.
	 */
	@Column(nullable=false, name="refunding_mc", precision=12, scale=6)
	private BigDecimal refundingMarketCurrency;

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
	
	@Column(nullable = false, name = "customer_vat_perc", precision=12, scale=6)
	private BigDecimal customerVatPercentage;

	@Column(nullable = true, name = "provider_vat_perc", precision=12, scale=6)
	private BigDecimal providerVatPercentage;

	@Column(nullable = true, name = "provider_resend_vat_perc", precision=12, scale=6)
	private BigDecimal providerResendingVatPercentage;

	/**
	 * This is set to false at order creation time and when the order was canceled (zeroSalesPrice). That then
	 * tells SellStar that this order should be included in the next verification (sending of money to the company
	 * account).
	 */
	@Column(nullable = false, name = "transfered_to_verific")
	private Boolean transferedToVerification;

	/**
	 * This is true for orders that used the old verification scheme. It doesn't fill any function since
	 * new and old orders are handled identically in the code, so we can remove this field at some point.
	 * EXCEPTION: when we implement the "fixa automaattinen täsmäytys för liian isot maksut" in Test.java,
	 * then we need to make sure that no correction is sent to the verification for orders having
	 * oldVerification = true.
	 */
	@Column(nullable = false, name = "old_verification")
	private Boolean oldVerification;

	@ManyToOne(fetch=FetchType.LAZY, cascade = { CascadeType.ALL })
	@JoinColumn(name="product_id", nullable=false)
	private Product product;

	/** This one is set to true if this order came through google-order. */
	@Column(nullable = false, name = "google_order")
	private Boolean googleOrder;

	public Integer getState() {
		return state;
	}

	public void setState(Integer state) {
		this.state = state;
		throw new RuntimeException("Not allowed to call order.setState - use order.setStateWithHistory instead.");
	}

	public void setStateWithHistory(Integer state) {
		if (state != null && !state.equals(this.state)) {
			this.state = state;
			StateChange stateChange = new StateChange();
			stateChange.setState(state);
			stateChange.setOrder(this);
			stateChange.setUpdateDate(new Date());
			if (stateChanges == null) {
				stateChanges = new HashSet<StateChange>();
			}
			stateChanges.add(stateChange);
			/* Add a small delay here, because in case we make several status updates
			 * in rapid succession, then we want to make sure that later states have a
			 * later timestamp. */
			try { Thread.sleep(500); } catch (Exception e) { }
		}
	}

	public Date getCreationDate() {
		Date earliestDate = null;
		if (stateChanges != null) {
			/* The start state can be STATE_NEW or STATE_FAULTY_CONTACT_INFO so iterate
			 * through all dates and return the earliest one. */
			for (StateChange sc : stateChanges) {
				if (earliestDate == null || sc.getUpdateDate().getTime() < earliestDate.getTime()) {
					earliestDate = sc.getUpdateDate();
				}
			}
		}
		if (earliestDate == null) {
			throw new RuntimeException("Internal error: creationDate of order " + id + " not found");
		} else {
			return earliestDate;
		}
	}

	public StateChange getLatestStateChange() {
		StateChange latestDate = null;
		if (stateChanges != null) {
			for (StateChange sc : stateChanges) {
				/* Note: Because of a bug in java (or in the hibernate wrapper classes?) the following
				 * gives the wrong result in some cases:
				 * sc.getUpdateDate().after(latestDate.getUpdateDate())
				 * 
				 * Instead we need to use the following comparison:
				 * sc.getUpdateDate().getTime() > latestDate.getUpdateDate().getTime() */
				if (latestDate == null || sc.getUpdateDate().getTime() > latestDate.getUpdateDate().getTime()) {
					latestDate = sc;
				}
			}
		}
		if (latestDate == null) {
			throw new RuntimeException("Internal error: latestStateChange of order " + id + " not found");
		} else {
			return latestDate;
		}
	}

	public StateChange getPreviousStateChange() {
		StateChange latestStateChange = getLatestStateChange();
		StateChange secondLastStateChange = null;
		if (stateChanges != null) {
			for (StateChange sc : stateChanges) {
				/* Note: Because of a bug in java (or in the hibernate wrapper classes?) the following
				 * gives the wrong result in some cases:
				 * sc.getUpdateDate().after(latestDate.getUpdateDate())
				 * 
				 * Instead we need to use the following comparison:
				 * sc.getUpdateDate().getTime() > latestDate.getUpdateDate().getTime() */
				if (sc.getUpdateDate().getTime() < latestStateChange.getUpdateDate().getTime()
						&& (secondLastStateChange == null || sc.getUpdateDate().getTime() > secondLastStateChange.getUpdateDate().getTime())) {
					secondLastStateChange = sc;
				}
			}
		}
		return secondLastStateChange;
	}

	/* An order could have been ordered several times if it has been resent to the customer, 
	 * so this method returns the date of the latest ordering from provider (or null if the
	 * product has never been placed at the provider or hasn't been placed at provider since
	 * the last resending). */
	public Date getLatestOrderedDate() {
		return getLatestDateForStateSinceResending(STATE_ORDERED_FROM_PROVIDER);
	}

	/* An order could have been ordered several times if it has been resent to the customer, 
	 * so this method returns the date of the latest shipping (or null if the product hasn't
	 * been shipped yet (or hasn't been shipped since the last resending)). */
	public Date getLatestShippingDate() {
		return getLatestDateForStateSinceResending(STATE_SHIPPED_BY_PROVIDER);
	}

	/* An order could have been in state X several times if it has been resent to the customer, 
	 * so this method returns the date of the latest state X (or null if the product has never
	 * been in state X (or hasn't been in state X since the last resending)). */
	private Date getLatestDateForStateSinceResending(int state) {
		Date latestDate = null;
		Date latestResendDate = null;
		if (stateChanges != null) {
			for (StateChange sc : stateChanges) {
				if (sc.getState().equals(STATE_SHOULD_RESEND)
						&& (latestResendDate == null || sc.getUpdateDate().getTime() > latestResendDate.getTime())) {
					latestResendDate = sc.getUpdateDate();
				}
			}
			for (StateChange sc : stateChanges) {
				if (sc.getState().equals(state)
						&& (latestDate == null || sc.getUpdateDate().getTime() > latestDate.getTime())) {
					latestDate = sc.getUpdateDate();
				}
			}
		}
		if (latestResendDate == null || (latestDate != null && latestDate.getTime() > latestResendDate.getTime())) {
			return latestDate;
		} else {
			return null;
		}
	}

	/* Note: it shouldn't be possible to set several states at once,
	 * so therefore this method is commented out. */
	/*public void setStateChanges(Set<StateChange> stateChanges) {
		this.stateChanges = stateChanges;
	}*/

	public Set<StateChange> getStateChanges() {
		return stateChanges;
	}

	public Ad getAd() {
		return ad;
	}

	public void setAd(Ad ad) {
		this.ad = ad;
	}

	public Long getId() {
		return id;
	}

	public void setCustomerReferenceNumber(String customerReferenceNumber) {
		this.customerReferenceNumber = customerReferenceNumber;
	}

	public String getCustomerReferenceNumber() {
		return customerReferenceNumber;
	}

	public void setFeedbackGiven(Boolean feedbackGiven) {
		this.feedbackGiven = feedbackGiven;
	}

	public Boolean getFeedbackGiven() {
		return feedbackGiven;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public void setMarket(Market market) {
		this.market = market;
	}

	public Market getMarket() {
		return market;
	}

	public void setSendCount(Integer sendCount) {
		this.sendCount = sendCount;
	}

	public Integer getSendCount() {
		return sendCount;
	}

	public void setTargetedPaidInMarketCurrency(BigDecimal targetedPaidInMarketCurrency) {
		this.targetedPaidInMarketCurrency = targetedPaidInMarketCurrency;
	}

	public BigDecimal getTargetedPaidInMarketCurrency() {
		return targetedPaidInMarketCurrency;
	}

	public void setOtherLossInEuro(BigDecimal otherLossInEuro) {
		this.otherLossInEuro = otherLossInEuro;
	}

	public BigDecimal getOtherLossInEuro() {
		return otherLossInEuro;
	}

	public void setTryToBundle(Boolean tryToBundle) {
		this.tryToBundle = tryToBundle;
	}

	public Boolean getTryToBundle() {
		return tryToBundle;
	}

	public void setOrderBundle(OrderBundle orderBundle) {
		this.orderBundle = orderBundle;
	}

	public OrderBundle getOrderBundle() {
		return orderBundle;
	}

	public void setRefundingMarketCurrency(BigDecimal refundingMarketCurrency) {
		this.refundingMarketCurrency = refundingMarketCurrency;
	}

	public BigDecimal getRefundingMarketCurrency() {
		return refundingMarketCurrency;
	}

	public void setTracking(Tracking tracking) {
		this.tracking = tracking;
	}

	public Tracking getTracking() {
		return tracking;
	}

	public void setFinalSalesPriceInMarketCurrencyExclVat(
			BigDecimal finalSalesPriceInMarketCurrencyExclVat) {
		this.finalSalesPriceInMarketCurrencyExclVat = finalSalesPriceInMarketCurrencyExclVat;
	}

	public BigDecimal getFinalSalesPriceInMarketCurrencyExclVat() {
		return finalSalesPriceInMarketCurrencyExclVat;
	}

	public void setFinalSalesPriceInEuroExclVat(
			BigDecimal finalSalesPriceInEuroExclVat) {
		this.finalSalesPriceInEuroExclVat = finalSalesPriceInEuroExclVat;
	}

	public BigDecimal getFinalSalesPriceInEuroExclVat() {
		return finalSalesPriceInEuroExclVat;
	}

	public void setFinalProviderPriceInProviderCurrencyExclVat(
			BigDecimal finalProviderPriceInProviderCurrencyExclVat) {
		this.finalProviderPriceInProviderCurrencyExclVat = finalProviderPriceInProviderCurrencyExclVat;
	}

	public BigDecimal getFinalProviderPriceInProviderCurrencyExclVat() {
		return finalProviderPriceInProviderCurrencyExclVat;
	}

	public void setFinalProviderPriceInEuroExclVat(
			BigDecimal finalProviderPriceInEuroExclVat) {
		this.finalProviderPriceInEuroExclVat = finalProviderPriceInEuroExclVat;
	}

	public BigDecimal getFinalProviderPriceInEuroExclVat() {
		return finalProviderPriceInEuroExclVat;
	}

	public void setResendLossInEuroExclVat(BigDecimal resendLossInEuroExclVat) {
		this.resendLossInEuroExclVat = resendLossInEuroExclVat;
	}

	public BigDecimal getResendLossInEuroExclVat() {
		return resendLossInEuroExclVat;
	}

	public void setFinalSalesPriceInMarketCurrencyInclVat(
			BigDecimal finalSalesPriceInMarketCurrencyInclVat) {
		this.finalSalesPriceInMarketCurrencyInclVat = finalSalesPriceInMarketCurrencyInclVat;
	}

	public BigDecimal getFinalSalesPriceInMarketCurrencyInclVat() {
		return finalSalesPriceInMarketCurrencyInclVat;
	}

	public void setFinalSalesPriceInEuroInclVat(
			BigDecimal finalSalesPriceInEuroInclVat) {
		this.finalSalesPriceInEuroInclVat = finalSalesPriceInEuroInclVat;
	}

	public BigDecimal getFinalSalesPriceInEuroInclVat() {
		return finalSalesPriceInEuroInclVat;
	}

	public void setFinalProviderPriceInProviderCurrencyInclVat(
			BigDecimal finalProviderPriceInProviderCurrencyInclVat) {
		this.finalProviderPriceInProviderCurrencyInclVat = finalProviderPriceInProviderCurrencyInclVat;
	}

	public BigDecimal getFinalProviderPriceInProviderCurrencyInclVat() {
		return finalProviderPriceInProviderCurrencyInclVat;
	}

	public void setFinalProviderPriceInEuroInclVat(
			BigDecimal finalProviderPriceInEuroInclVat) {
		this.finalProviderPriceInEuroInclVat = finalProviderPriceInEuroInclVat;
	}

	public BigDecimal getFinalProviderPriceInEuroInclVat() {
		return finalProviderPriceInEuroInclVat;
	}

	public void setResendLossInEuroInclVat(BigDecimal resendLossInEuroInclVat) {
		this.resendLossInEuroInclVat = resendLossInEuroInclVat;
	}

	public BigDecimal getResendLossInEuroInclVat() {
		return resendLossInEuroInclVat;
	}

	public void setCustomerVatPercentage(BigDecimal customerVatPercentage) {
		this.customerVatPercentage = customerVatPercentage;
	}

	public BigDecimal getCustomerVatPercentage() {
		return customerVatPercentage;
	}

	public void setProviderVatPercentage(BigDecimal providerVatPercentage) {
		this.providerVatPercentage = providerVatPercentage;
	}

	public BigDecimal getProviderVatPercentage() {
		return providerVatPercentage;
	}

	public void setProviderResendingVatPercentage(
			BigDecimal providerResendingVatPercentage) {
		this.providerResendingVatPercentage = providerResendingVatPercentage;
	}

	public BigDecimal getProviderResendingVatPercentage() {
		return providerResendingVatPercentage;
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

	public void setProduct(Product product) {
		this.product = product;
	}

	public Product getProduct() {
		return product;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof Order) {
			Order another = (Order) obj;
			return this.id.equals(another.getId());
		}
		return false;
	}
	
	@Override
	public int hashCode() {
		return new Long(id).intValue();
	}

	public void setGoogleOrder(Boolean googleOrder) {
		this.googleOrder = googleOrder;
	}

	public Boolean getGoogleOrder() {
		return googleOrder;
	}
	
}
