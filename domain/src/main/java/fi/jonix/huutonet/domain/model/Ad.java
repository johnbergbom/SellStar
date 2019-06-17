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
import javax.persistence.UniqueConstraint;

/**
 * This class defines ads. One row corresponds to one ad on a market.
 */
@Entity
@Table(name = "ad", uniqueConstraints=@UniqueConstraint(columnNames={"market_sales_id", "market_id"}))
@SequenceGenerator(allocationSize = 1, name = "AdSeq", sequenceName = "ad_id_seq")
public class Ad implements Serializable {

	private static final long serialVersionUID = -6230534835910183427L;

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "AdSeq")
	private Long id;

	public Long getId() {
		return id;
	}
	
	/**
	 * For example huutonet sales id.
	 */
	@Column(nullable=false, name="market_sales_id")
	private String marketSalesId;

	@ManyToOne(fetch=FetchType.LAZY)
	@JoinColumn(name = "market_id", nullable=false)
	private Market market;

	/*@ManyToOne(fetch=FetchType.LAZY)
	@JoinColumn(name="product_id", nullable=false)
	private Product product;*/

	/*@Column(nullable=false, name="description")
	private String description;*/
	
	/**
	 * This is a market dependent value which might not be in use
	 * on every market. For huutonet this one means:
	 * 1 = huutokauppa, 2 = tarjoa / osta heti, 3 = osta heti
	 */
	@Column(nullable=false, name="sales_type")
	private Integer salesType;
	
	/**
	 * This one is dependent on the current provider price at the point in time when the
	 * ad is put to the market. For Huutonet this corresponds to "Lähtöhinta".
	 */
	@Column(nullable=false, name="start_price_mc_inc_vat", precision=12, scale=6)
	private BigDecimal startPriceInMarketCurrencyInclVat;
	
	/**
	 * This one is dependent on the current provider price at the point in time when the
	 * ad is put to the market. For Huutonet this corresponds to "Lähtöhinta".
	 */
	@Column(nullable=false, name="start_price_euro_inc_vat", precision=12, scale=6)
	private BigDecimal startPriceInEuroInclVat;

	/**
	 * This field tells the buy out price, i.e. the price when the item isn't
	 * bought through an auction ("Osta heti").
	 */
	@Column(nullable=false, name="buyout_price_mc_inc_vat", precision=12, scale=6)
	private BigDecimal buyOutPriceInMarketCurrencyInclVat;

	@Column(nullable=false, name="buyout_price_euro_inc_vat", precision=12, scale=6)
	private BigDecimal buyOutPriceInEuroInclVat;

	/**
	 * This is a market dependent value which might not be in use
	 * on every market. For huutonet this one corresponds to the
	 * field "Hintavaraus".
	 */
	@Column(nullable=false, name="min_selling_price_mc_inc_vat", precision=12, scale=6)
	private BigDecimal minSellingPriceInMarketCurrencyInclVat;

	@Column(nullable=false, name="min_selling_price_euro_inc_vat", precision=12, scale=6)
	private BigDecimal minSellingPriceInEuroInclVat;

	/**
	 * Provider price in provider's currency, e.g. price in dollars
	 * for deal extreme.
	 */
	@Column(nullable=false, name="prov_price_at_listing_pc_inc_vat", precision=12, scale=6)
	private BigDecimal providerPriceAtListingTimeProviderCurrencyInclVat;

	/**
	 * Provider price in euros. For example of provider is
	 * deal extreme, then this field contains the price in euros
	 * using the conversion rate at the time of listing the ad.
	 */
	@Column(nullable=false, name="prov_price_at_listing_euro_inc_vat", precision=12, scale=6)
	private BigDecimal providerPriceAtListingTimeEuroInclVat;

	@Column(nullable=false, name="shipping_cost_mc_inc_vat", precision=12, scale=6)
	private BigDecimal shippingCostInMarketCurrencyInclVat;

	@Column(nullable=false, name="shipping_cost_euro_inc_vat", precision=12, scale=6)
	private BigDecimal shippingCostInEuroInclVat;

	/**
	 * This field tells what market category was used for the ad.
	 */
	@ManyToOne(fetch=FetchType.LAZY)
	@JoinColumn(name="market_category_id", nullable=false)
	private Category marketCategory;

	/**
	 * Tells what template bean was actually used for generating this ad.
	 * It should normally be the same as ad_template.bean_name, but
	 * can be different if ad_template.bean_name has been changed.
	 */
	@Column(nullable=false, name="used_bean_name")
	private String usedBeanName;

	/**
	 * Tells what version of the template bean was actually used for
	 * generating this ad.
	 */
	@Column(nullable=false, name="used_bean_version")
	private String usedBeanVersion;

	/**
	 * This field tells when the ad was put to the market (for example to huutonet).
	 */
	@Column(nullable=false, name="date_to_market")
	private Date dateToMarket;

	/**
	 * This is set at listing time and is read from the market place ad.
	 */
	@Column(nullable=false, name="date_closing")
	private Date dateClosing;
	
	/**
	 * This field tells when the ad was removed from the market (for example from huutonet).
	 * It's set when the market place informs that the ad was closed (should in general
	 * have around the same value as date_closing but can be different if buyout ("osta heti")
	 * was used).
	 */
	@Column(nullable=true, name="date_from_market")
	private Date dateFromMarket;
	
	/**
	 * This one is used for determining which template ad was used
	 * for creating this ad. 
	 */
	@ManyToOne(fetch=FetchType.LAZY)
	@JoinColumn(name="ad_template_id", nullable=false)
	private AdTemplate adTemplate;

	/**
	 * Set to false when the ad is created and set to true when a confirmation
	 * email is received from the market that the ad was actually accepted.
	 */
	@Column(nullable = false, name = "confirmed")
	private Boolean confirmed;
	
	/**
	 * This field is used to keep track of which public question should be fetched (in case
	 * several public questions have come for the same ad). It replaces the old "vastataan pian"
	 * scheme.
	 */
	@Column(nullable=false, name="nbr_public_questions")
	private Integer numberOfPublicQuestions;
	
	/** This field tells how many sellable items we set for this ad at listing time. */
	@Column(nullable=false, name="original_amount")
	private Integer originalAmount;
	
	/** This field tells how many items were actually sold for this ad. */
	@Column(nullable=false, name="bought_amount")
	private Integer boughtAmount;
	
	/**
	 * This is normally null, but for cloned ads this column points to the original
	 * ad from which the cloned ad was created. For more information regarding cloned
	 * ads see HuutonetProcessor.cloneAd. */
	@ManyToOne(fetch=FetchType.LAZY)
	@JoinColumn(name="original_ad_id", nullable=true)
	private Ad originalAd;
	
	/**
	 * This field tells what visibility type was used for this particular ad. It's taken
	 * from adTemplate.visibilityType at listing time.
	 */
	@Column(nullable=false, name="visibility_type")
	private Integer visibilityType;

	@Column(nullable=false, name="open_days")
	private Integer openDays;

	/**
	 * This field is normally zero but for paid ads it tells how many euros the
	 * listing cost.
	 */
	@Column(nullable=false, name="advert_cost_euro_inc_vat", precision=12, scale=6)
	private BigDecimal advertisementCostInEuroInclVat;

	@Column(nullable=false, name="advert_cost_euro_exc_vat", precision=12, scale=6)
	private BigDecimal advertisementCostInEuroExclVat;

	/**
	 * This field is used for telling whether information regarding paid ads have been
	 * moved to the bookkeeping. It's set to false at ad creation time and set to true
	 * in bookkeepPaidAds.
	 */
	@Column(nullable = false, name = "transfered_to_verific")
	private Boolean transferedToVerification;

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof Ad) {
			Ad another = (Ad) obj;
			return this.id.equals(another.getId());
		}
		return false;
	}
	
	@Override
	public int hashCode() {
		return new Long(id).intValue();
	}
	
	public String getMarketSalesId() {
		return marketSalesId;
	}

	public void setMarketSalesId(String marketSalesId) {
		this.marketSalesId = marketSalesId;
	}

	public Market getMarket() {
		return market;
	}

	public void setMarket(Market market) {
		this.market = market;
	}

	public void setUsedBeanName(String usedBeanName) {
		this.usedBeanName = usedBeanName;
	}

	public String getUsedBeanName() {
		return usedBeanName;
	}

	public void setUsedBeanVersion(String usedBeanVersion) {
		this.usedBeanVersion = usedBeanVersion;
	}

	public String getUsedBeanVersion() {
		return usedBeanVersion;
	}

	public void setDateToMarket(Date dateToMarket) {
		this.dateToMarket = dateToMarket;
	}

	public Date getDateToMarket() {
		return dateToMarket;
	}

	public void setDateFromMarket(Date dateFromMarket) {
		this.dateFromMarket = dateFromMarket;
	}

	public Date getDateFromMarket() {
		return dateFromMarket;
	}

	public void setAdTemplate(AdTemplate adTemplate) {
		this.adTemplate = adTemplate;
	}

	public AdTemplate getAdTemplate() {
		return adTemplate;
	}

	public void setConfirmed(Boolean confirmed) {
		this.confirmed = confirmed;
	}

	public Boolean getConfirmed() {
		return confirmed;
	}

	public void setSalesType(Integer salesType) {
		this.salesType = salesType;
	}

	public Integer getSalesType() {
		return salesType;
	}

	public void setDateClosing(Date dateClosing) {
		this.dateClosing = dateClosing;
	}

	public Date getDateClosing() {
		return dateClosing;
	}

	public void setMarketCategory(Category marketCategory) {
		this.marketCategory = marketCategory;
	}

	public Category getMarketCategory() {
		return marketCategory;
	}

	public void setNumberOfPublicQuestions(Integer numberOfPublicQuestions) {
		this.numberOfPublicQuestions = numberOfPublicQuestions;
	}

	public Integer getNumberOfPublicQuestions() {
		return numberOfPublicQuestions;
	}

	public void setShippingCostInMarketCurrencyInclVat(
			BigDecimal shippingCostInMarketCurrencyInclVat) {
		this.shippingCostInMarketCurrencyInclVat = shippingCostInMarketCurrencyInclVat;
	}

	public BigDecimal getShippingCostInMarketCurrencyInclVat() {
		return shippingCostInMarketCurrencyInclVat;
	}

	public void setShippingCostInEuroInclVat(BigDecimal shippingCostInEuroInclVat) {
		this.shippingCostInEuroInclVat = shippingCostInEuroInclVat;
	}

	public BigDecimal getShippingCostInEuroInclVat() {
		return shippingCostInEuroInclVat;
	}

	public void setStartPriceInMarketCurrencyInclVat(
			BigDecimal startPriceInMarketCurrencyInclVat) {
		this.startPriceInMarketCurrencyInclVat = startPriceInMarketCurrencyInclVat;
	}

	public BigDecimal getStartPriceInMarketCurrencyInclVat() {
		return startPriceInMarketCurrencyInclVat;
	}

	public void setBuyOutPriceInMarketCurrencyInclVat(
			BigDecimal buyOutPriceInMarketCurrencyInclVat) {
		this.buyOutPriceInMarketCurrencyInclVat = buyOutPriceInMarketCurrencyInclVat;
	}

	public BigDecimal getBuyOutPriceInMarketCurrencyInclVat() {
		return buyOutPriceInMarketCurrencyInclVat;
	}

	public void setStartPriceInEuroInclVat(BigDecimal startPriceInEuroInclVat) {
		this.startPriceInEuroInclVat = startPriceInEuroInclVat;
	}

	public BigDecimal getStartPriceInEuroInclVat() {
		return startPriceInEuroInclVat;
	}

	public void setMinSellingPriceInMarketCurrencyInclVat(
			BigDecimal minSellingPriceInMarketCurrencyInclVat) {
		this.minSellingPriceInMarketCurrencyInclVat = minSellingPriceInMarketCurrencyInclVat;
	}

	public BigDecimal getMinSellingPriceInMarketCurrencyInclVat() {
		return minSellingPriceInMarketCurrencyInclVat;
	}

	public void setMinSellingPriceInEuroInclVat(
			BigDecimal minSellingPriceInEuroInclVat) {
		this.minSellingPriceInEuroInclVat = minSellingPriceInEuroInclVat;
	}

	public BigDecimal getMinSellingPriceInEuroInclVat() {
		return minSellingPriceInEuroInclVat;
	}

	public void setProviderPriceAtListingTimeProviderCurrencyInclVat(
			BigDecimal providerPriceAtListingTimeProviderCurrencyInclVat) {
		this.providerPriceAtListingTimeProviderCurrencyInclVat = providerPriceAtListingTimeProviderCurrencyInclVat;
	}

	public BigDecimal getProviderPriceAtListingTimeProviderCurrencyInclVat() {
		return providerPriceAtListingTimeProviderCurrencyInclVat;
	}

	public void setProviderPriceAtListingTimeEuroInclVat(
			BigDecimal providerPriceAtListingTimeEuroInclVat) {
		this.providerPriceAtListingTimeEuroInclVat = providerPriceAtListingTimeEuroInclVat;
	}

	public BigDecimal getProviderPriceAtListingTimeEuroInclVat() {
		return providerPriceAtListingTimeEuroInclVat;
	}

	public void setBuyOutPriceInEuroInclVat(BigDecimal buyOutPriceInEuroInclVat) {
		this.buyOutPriceInEuroInclVat = buyOutPriceInEuroInclVat;
	}

	public BigDecimal getBuyOutPriceInEuroInclVat() {
		return buyOutPriceInEuroInclVat;
	}

	public void setOriginalAmount(Integer originalAmount) {
		this.originalAmount = originalAmount;
	}

	public Integer getOriginalAmount() {
		return originalAmount;
	}

	public void setBoughtAmount(Integer boughtAmount) {
		this.boughtAmount = boughtAmount;
	}

	public Integer getBoughtAmount() {
		return boughtAmount;
	}

	public void setOriginalAd(Ad originalAd) {
		this.originalAd = originalAd;
	}

	public Ad getOriginalAd() {
		return originalAd;
	}

	public void setVisibilityType(Integer visibilityType) {
		this.visibilityType = visibilityType;
	}

	public Integer getVisibilityType() {
		return visibilityType;
	}

	public void setOpenDays(Integer openDays) {
		this.openDays = openDays;
	}

	public Integer getOpenDays() {
		return openDays;
	}

	public void setAdvertisementCostInEuroInclVat(
			BigDecimal advertisementCostInEuroInclVat) {
		this.advertisementCostInEuroInclVat = advertisementCostInEuroInclVat;
	}

	public BigDecimal getAdvertisementCostInEuroInclVat() {
		return advertisementCostInEuroInclVat;
	}

	public void setAdvertisementCostInEuroExclVat(
			BigDecimal advertisementCostInEuroExclVat) {
		this.advertisementCostInEuroExclVat = advertisementCostInEuroExclVat;
	}

	public BigDecimal getAdvertisementCostInEuroExclVat() {
		return advertisementCostInEuroExclVat;
	}

	public void setTransferedToVerification(Boolean transferedToVerification) {
		this.transferedToVerification = transferedToVerification;
	}

	public Boolean getTransferedToVerification() {
		return transferedToVerification;
	}

}
