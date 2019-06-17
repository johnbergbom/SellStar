package fi.jonix.huutonet.domain.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
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
import javax.persistence.UniqueConstraint;

@Entity
@Table(name = "product", uniqueConstraints=@UniqueConstraint(columnNames={"provider_prod_id", "provider_id"}))
@SequenceGenerator(allocationSize = 1, name = "ProductSeq", sequenceName = "product_id_seq")
public class Product implements Serializable {

	private static final long serialVersionUID = -1698202362721710288L;

	public static final int STATE_UNKNOWN = 0;
	public static final int STATE_IN_STOCK = 1;
	public static final int STATE_OUT_OF_STOCK = 2;
	public static final int STATE_NO_LONGER_FOR_SALE = 10;
	//public static final int STATE_ONLY_OFFLINE_SALE = 20;

	public Product() {
		this.createdTimestamp = new Date();
	}

	public Product(Provider provider, String providerProdId, String name) {
		this();
		this.provider = provider;
		this.providerProdId = providerProdId;
		this.name = name;
	}

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ProductSeq")
	private Long id;

	@Column(nullable = false, name = "created_timestamp")
	private Date createdTimestamp;

	@ManyToOne(fetch=FetchType.LAZY)
	@JoinColumn(name="provider_id", nullable=false)
	private Provider provider;

	@Column(nullable = false, name = "provider_prod_id")
	private String providerProdId;
	
	@Column(nullable = false, name = "provider_category")
	private String providerCategory;

	@Column(nullable = false, name = "name")
	private String name;

	@OneToMany(mappedBy="product", cascade = { CascadeType.ALL }, fetch = FetchType.LAZY)
	private Set<Image> images;

	@ManyToOne(fetch=FetchType.LAZY)
	@JoinColumn(name="product_link_id", nullable=true)
	private Product productLinkId;
	
	@Column(nullable=false, name="technical_specs")
	private String technicalSpecs;
	
	@Column(nullable=true, name="dimensions")
	private String dimensions;
	
	@Column(nullable=true, name="contents")
	private String contents;
	
	/**
	 * This one needs to be nullable because the category isn't set until fillitems
	 * is ran for the product for the first time, so we don't known the internal
	 * SellStar category at the time we fetch a batch of products with getitems.
	 */
	@ManyToOne(fetch=FetchType.LAZY)
	@JoinColumn(name="category_id", nullable=true)
	private Category category;

	@Column(nullable = true, name = "cat_suggestion")
	private String categorySuggestion;
	
	/**
	 * This field tells if the product is cubject to compensation (hyvitysmaksu).
	 */
	@Column(nullable = false, name = "compensation")
	private Boolean compensation;

	@Column(nullable=false, name="provider_url", unique=true)
	private String providerUrl;
	
	/**
	 * This column tells the minimum shipping cost that the provider charges
	 * for shipping of this product.
	 */
	@Column(nullable = false, name = "min_prov_ship_cost_pc_inc_vat", precision=12, scale=6)
	private BigDecimal minProviderShippingCostProviderCurrencyIncVat;
	
	/**
	 * The statusFetchDate tells when current provider price
	 * and product status was last fetched.
	 */
	@Column(nullable = true, name = "status_fetch_date")
	private Date statusFetchDate;

	@Column(nullable = true, name = "curr_prov_price_pc_inc_vat", precision=12, scale=6)
	private BigDecimal currentProviderPriceProviderCurrencyInclVat;

	@Column(nullable = false, name = "product_state")
	private Integer productState;

	@Column(nullable=true, name="manufacturer_url", unique=true)
	private String manufacturerUrl;
	
	/**
	 * Because the FocalPrice web page sometimes don't show products that are indeed
	 * for sale, we need to several times check whether the product is still for sale
	 * or not. If after checking several times the product still doesn't show up, we
	 * determine that the product is no longer for sale.
	 */
	@Column(nullable=false, name="not_found_counter")
	private Integer notFoundCounter;
	
	@Column(nullable = false, name = "offline_sale")
	private Boolean offlineSale;

	/**
	 * @return Provider, for example deal extreme.
	 */
	public Provider getProvider() {
		return provider;
	}

	/**
	 * @param Provider, for example deal extreme.
	 */
	public void setProvider(Provider provider) {
		this.provider = provider;
	}

	public String getProviderProdId() {
		return providerProdId;
	}

	public void setProviderProdId(String providerProdId) {
		this.providerProdId = providerProdId;
	}

	public Category getCategory() {
		return category;
	}

	public void setCategory(Category category) {
		this.category = category;
	}

	public Long getId() {
		return this.id;
	}

	public Date getCreatedTimestamp() {
		return this.createdTimestamp;
	}

	public Set<Image> getImages() {
		return images;
	}

	public List<Image> getImagesInOrder() {
		List<Image> imagesInOrder = new ArrayList<Image>();
		Iterator<Image> imagesIter = images.iterator();
		while (imagesIter.hasNext()) {
			imagesInOrder.add(imagesIter.next());
		}
		Collections.sort(imagesInOrder);
		return imagesInOrder;
	}

	/*
	 * public void setCreatedTimestamp(Date createdTimestamp) {
	 * this.createdTimestamp = createdTimestamp; }
	 */

	/*public void setImages(Set<Image> images) {
		this.images = images;
	}*/
	
	public void addImage(Image image) {
		if (images == null) {
			images = new HashSet<Image>();
		}
		images.add(image);
	}
	
	public void removeImage(Image image) {
		this.images.remove(image);
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}

	public void setProductLinkId(Product productLinkId) {
		this.productLinkId = productLinkId;
	}

	public Product getProductLinkId() {
		return productLinkId;
	}

	public void setProviderCategory(String providerCategory) {
		this.providerCategory = providerCategory;
	}

	public String getProviderCategory() {
		return providerCategory;
	}

	public void setTechnicalSpecs(String technicalSpecs) {
		this.technicalSpecs = technicalSpecs;
	}

	public String getTechnicalSpecs() {
		return technicalSpecs;
	}

	public void setDimensions(String dimensions) {
		this.dimensions = dimensions;
	}

	public String getDimensions() {
		return dimensions;
	}

	public void setContents(String contents) {
		this.contents = contents;
	}

	public String getContents() {
		return contents;
	}

	public String getCategorySuggestion() {
		return categorySuggestion;
	}

	public void setCategorySuggestion(String categorySuggestion) {
		this.categorySuggestion = categorySuggestion;
	}

	public void setCompensation(Boolean compensation) {
		this.compensation = compensation;
	}

	public Boolean getCompensation() {
		return compensation;
	}

	public void setProviderUrl(String providerUrl) {
		this.providerUrl = providerUrl;
	}

	public String getProviderUrl() {
		return providerUrl;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof Product) {
			Product another = (Product) obj;
			return this.id.equals(another.getId());
		}
		return false;
	}
	
	@Override
	public int hashCode() {
		return new Long(id).intValue();
	}

	public void setMinProviderShippingCostProviderCurrencyIncVat(
			BigDecimal minProviderShippingCostProviderCurrencyIncVat) {
		this.minProviderShippingCostProviderCurrencyIncVat = minProviderShippingCostProviderCurrencyIncVat;
	}

	public BigDecimal getMinProviderShippingCostProviderCurrencyIncVat() {
		return minProviderShippingCostProviderCurrencyIncVat;
	}

	public void setStatusFetchDate(Date statusFetchDate) {
		this.statusFetchDate = statusFetchDate;
	}

	public Date getStatusFetchDate() {
		return statusFetchDate;
	}

	public void setCurrentProviderPriceProviderCurrencyInclVat(
			BigDecimal currentProviderPriceProviderCurrencyInclVat) {
		this.currentProviderPriceProviderCurrencyInclVat = currentProviderPriceProviderCurrencyInclVat;
	}

	public BigDecimal getCurrentProviderPriceProviderCurrencyInclVat() {
		return currentProviderPriceProviderCurrencyInclVat;
	}

	public void setProductState(Integer productState) {
		this.productState = productState;
	}

	public Integer getProductState() {
		return productState;
	}
	
	public void setNotFoundCounter(Integer notFoundCounter) {
		this.notFoundCounter = notFoundCounter;
	}

	public Integer getNotFoundCounter() {
		return notFoundCounter;
	}

	public void setManufacturerUrl(String manufacturerUrl) {
		this.manufacturerUrl = manufacturerUrl;
	}

	public String getManufacturerUrl() {
		return manufacturerUrl;
	}

	public void setOfflineSale(Boolean offlineSale) {
		this.offlineSale = offlineSale;
	}

	public Boolean getOfflineSale() {
		return offlineSale;
	}

}
