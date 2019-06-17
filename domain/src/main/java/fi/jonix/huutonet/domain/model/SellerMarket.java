package fi.jonix.huutonet.domain.model;

import java.math.BigDecimal;

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
@Table(name = "seller_market", uniqueConstraints={@UniqueConstraint(columnNames={"seller_id", "market_id"}),
		@UniqueConstraint(columnNames={"market_id", "user_name"}) } )
@SequenceGenerator(allocationSize = 1, name = "SellerMarketSeq", sequenceName = "seller_market_id_seq")
public class SellerMarket {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SellerMarketSeq")
	private Long id;

	// this one is fetched eagerly so that it can be used outside of transactions (in Test.java)
	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "seller_id", nullable = false)
	private Seller seller;

	// this one is fetched eagerly so that it can be used outside of transactions (in Test.java)
	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "market_id", nullable = false)
	private Market market;

	/**
	 * This is the username for the user to log on as at the market place.
	 */
	@Column(name = "user_name", nullable = false, length=64)
	private String userName;

	/**
	 * This is the password for the user to log on as at the market place.
	 */
	@Column(name = "password", nullable = false, length=64)
	private String password;

	/**
	 * Ad decorator makes sure that we get a neat presentation of the product. It
	 * does not need to be unique, because we might in some cases want to allow
	 * different sellers to use the same lister bean for the same market. 
	 */
	@Column(name = "ad_decorator", nullable = false, length=64)
	private String adDecorator;

	/* Shipping cost is in the seller_market table and not in the seller table
	 * since we cannot have a single value in the seller table in case the
	 * seller does business with several different currencies. */
	@Column(nullable = false, name = "min_ship_cost_mark_curr_inc_vat", precision=12, scale=6)
	private BigDecimal minShippingCostMarketCurrInclVat;

	/**
	 * This is a market dependent value which might not be in use
	 * on every market. For huutonet this one corresponds to the
	 * field "Minimikorotus".
	 */
	@Column(nullable = false, name = "min_bid_increase_mc_inc_vat", precision=12, scale=6)
	private BigDecimal minBidIncreaseInMarketCurrencyInclVat;

	/**
	 * This one tells the maximum price of a bundle. If the price of a single product is
	 * greater than this value, then the product isn't listed at all. See also
	 * orderBundle.maxBundleSizeInMarketCurrencyExclVat.
	 * 
	 * TODO: this doesn't work for Google-order when we start using other market currencies
	 * than euro. => UPDATE: OrderCreator doesn't use this field for google order (rather
	 * it takes the value from the market that was refered to).
	 */
	@Column(nullable = false, name = "max_bundle_size_mc_exc_vat", precision=12, scale=6)
	private BigDecimal maxBundleSizeInMarketCurrencyExclVat;
	
	/**
	 * If this one is true, then listing will be done for this one.
	 */
	@Column(name = "do_listing", nullable = false)
	private Boolean doListing;

	/**
	 * This column tells how much more expensive the buyout price should
	 * be than the minimum selling price.
	 */
	@Column(nullable = false, name = "buyout_multiplier", precision=12, scale=6)
	private BigDecimal buyoutPriceMultiplier;
	
	public Long getId() {
		return id;
	}

	public void setSeller(Seller seller) {
		this.seller = seller;
	}

	public Seller getSeller() {
		return seller;
	}

	public void setMarket(Market market) {
		this.market = market;
	}

	public Market getMarket() {
		return market;
	}

	public void setUserName(String userName) {
		this.userName = userName;
	}

	public String getUserName() {
		return userName;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getPassword() {
		return password;
	}

	public void setMinBidIncreaseInMarketCurrencyInclVat(
			BigDecimal minBidIncreaseInMarketCurrencyInclVat) {
		this.minBidIncreaseInMarketCurrencyInclVat = minBidIncreaseInMarketCurrencyInclVat;
	}

	public BigDecimal getMinBidIncreaseInMarketCurrencyInclVat() {
		return minBidIncreaseInMarketCurrencyInclVat;
	}

	public void setMaxBundleSizeInMarketCurrencyExclVat(
			BigDecimal maxBundleSizeInMarketCurrencyExclVat) {
		this.maxBundleSizeInMarketCurrencyExclVat = maxBundleSizeInMarketCurrencyExclVat;
	}

	public BigDecimal getMaxBundleSizeInMarketCurrencyExclVat() {
		return maxBundleSizeInMarketCurrencyExclVat;
	}

	public void setDoListing(Boolean doListing) {
		this.doListing = doListing;
	}

	public Boolean getDoListing() {
		return doListing;
	}

	public void setAdDecorator(String adDecorator) {
		this.adDecorator = adDecorator;
	}

	public String getAdDecorator() {
		return adDecorator;
	}

	public void setMinShippingCostMarketCurrInclVat(
			BigDecimal minShippingCostMarketCurrInclVat) {
		this.minShippingCostMarketCurrInclVat = minShippingCostMarketCurrInclVat;
	}

	public BigDecimal getMinShippingCostMarketCurrInclVat() {
		return minShippingCostMarketCurrInclVat;
	}

	public void setBuyoutPriceMultiplier(BigDecimal buyoutPriceMultiplier) {
		this.buyoutPriceMultiplier = buyoutPriceMultiplier;
	}

	public BigDecimal getBuyoutPriceMultiplier() {
		return buyoutPriceMultiplier;
	}

}
