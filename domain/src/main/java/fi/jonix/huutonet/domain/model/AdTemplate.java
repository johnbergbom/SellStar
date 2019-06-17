package fi.jonix.huutonet.domain.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

import javax.persistence.Column;
//import javax.persistence.Embedded;
//import javax.persistence.EmbeddedId;
import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

/**
 * This class defines templates for products. A template is a localized description of a product
 * and they are thus language specific (and not market specific).
 */
@Entity
@Table(name = "adtemplate")
@SequenceGenerator(allocationSize = 1, name = "AdTemplateSeq", sequenceName = "adtemplate_id_seq")
public class AdTemplate implements Serializable {

	private static final long serialVersionUID = -3164728160144534352L;

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "AdTemplateSeq")
	private Long id;
	
	@ManyToOne(fetch=FetchType.LAZY, cascade = { CascadeType.ALL })
	@JoinColumn(name="product_id", nullable=false)
	private Product product;

	@Column(nullable = false, name = "active")
	private Boolean active;

	/**
	 * Tells what template bean should be used for generating new ads.
	 */
	@Column(nullable = false, name = "bean_name")
	private String beanName;

	@Column(nullable=false, name="quantity")
	private Integer quantity;

	/**
	 * For huutonet this is "Kohteen nimi".
	 */
	@Column(nullable=false, name="headline")
	private String headline;

	/**
	 * This is a market dependent value which might not be in use
	 * on every market. For huutonet this one corresponds to the
	 * field "Aukioloaika".
	 */
	@Column(nullable=false, name="open_days")
	private Integer openDays;

	/**
	 * This is a market dependent value which might not be in use
	 * on every market. For huutonet this one corresponds to the
	 * field "Sijaintipaikkakunta".
	 * 
	 * TODO: This column should be removed for the same reason as why
	 * shippingDescription should be removed.
	 */
	@Column(nullable=false, name="location")
	private String location;
	
	/**
	 * This is a market dependent value which might not be in use
	 * on every market. For huutonet this one corresponds to the
	 * field "Kuntoluokitus".
	 */
	@Column(nullable=true, name="classification")
	private Integer classification;

	/**
	 * This is a market dependent value which might not be in use
	 * on every market. For huutonet this one corresponds to the
	 * field "Maksutapa".
	 */
	@Column(nullable=false, name="payment_way")
	private Integer paymentWay;

	/**
	 * This is a market dependent value which might not be in use
	 * on every market. For huutonet this one corresponds to the
	 * field "Toimitustapa".
	 */
	@Column(nullable=false, name="shipping_way")
	private Integer shippingWay;

	/**
	 * This is a market dependent value which might not be in use
	 * on every market. For huutonet this one corresponds to the
	 * field "Muut toimitusehdot".
	 * 
	 * TODO: this column should probably be removed. It's not in use because the contents
	 * is taken from the settings and it probably more sensible to to it that way. Ask
	 * Tomi if this field can be removed.
	 */
	@Column(nullable=false, name="shipping_desc")
	private String shippingDescription;

	@Column(nullable=false, name="summary")
	private String summary;
	
	@Column(nullable=true, name="technical_specs")
	private String technicalSpecs;
	
	@Column(nullable=false, name="details")
	private String details;

	@Column(nullable=false, name="slogan")
	private String slogan;
	
	/**
	 * This column tells who added this ad to the database.
	 * TODO: replace this with a reference to the user table.
	 */
	@Column(nullable=false, name="added_by")
	private String addedBy;
	
	@Column(nullable = false, name = "created_timestamp")
	private Date createdTimestamp;
	
	@ManyToOne(fetch=FetchType.LAZY, cascade = { CascadeType.ALL })
	@JoinColumn(name="seller_id", nullable=false)
	private Seller seller;

	@Column(nullable = false, name = "language", length=16)
	private String language;

	/**
	 * This field is used for determining the visibility of the ad. A value of zero
	 * means that this listing of free from charge.
	 * 
	 * 0 = Nothing extra (free)
	 * 
	 * 1 = Highlighted headline
	 * 2 = Image (these we get automatically at huutonet if we have Huuto Plus or store)
	 * 3 = Background color
	 * 4 = Display window (on Huutonet: "näyteikkuna")
	 * 
	 * 10 = Display window + highlighted headline
	 * 11 = Display window + image
	 * 12 = Display window + background color
	 * 
	 * 20 = Display window + highlighted headline + image
	 * 21 = Display window + highlighted headline + background color
	 * 22 = Display window + image + background color
	 * 
	 * 30 = Main section display window
	 * 40 = Front page display window
	 */
	@Column(nullable=false, name="visibility_type")
	private Integer visibilityType;

	@Column(nullable = true, name = "inactive_reason")
	private String inactiveReason;

	public AdTemplate() {
	}
	
	public AdTemplate(Product product, Boolean active, String beanName) {
		this.product = product;
		this.active = active;
		this.beanName = beanName;
	}
	
	public void setActive(Boolean active) {
		this.active = active;
	}

	public Boolean getActive() {
		return active;
	}

	public Product getProduct() {
		return product;
	}

	public void setProduct(Product product) {
		this.product = product;
	}

	public void setBeanName(String beanName) {
		this.beanName = beanName;
	}

	public String getBeanName() {
		return beanName;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getId() {
		return id;
	}

	public void setQuantity(Integer quantity) {
		this.quantity = quantity;
	}

	public Integer getQuantity() {
		return quantity;
	}

	public void setHeadline(String headline) {
		this.headline = headline;
	}

	public String getHeadline() {
		return headline;
	}

	public void setOpenDays(Integer openDays) {
		this.openDays = openDays;
	}

	public Integer getOpenDays() {
		return openDays;
	}

	public void setLocation(String location) {
		this.location = location;
	}

	public String getLocation() {
		return location;
	}

	public void setClassification(Integer classification) {
		this.classification = classification;
	}

	public Integer getClassification() {
		return classification;
	}

	public void setPaymentWay(Integer paymentWay) {
		this.paymentWay = paymentWay;
	}

	public Integer getPaymentWay() {
		return paymentWay;
	}

	public void setShippingWay(Integer shippingWay) {
		this.shippingWay = shippingWay;
	}

	public Integer getShippingWay() {
		return shippingWay;
	}

	public void setShippingDescription(String shippingDescription) {
		this.shippingDescription = shippingDescription;
	}

	public String getShippingDescription() {
		return shippingDescription;
	}

	public void setSummary(String summary) {
		this.summary = summary;
	}

	public String getSummary() {
		return summary;
	}

	public void setTechnicalSpecs(String technicalSpecs) {
		this.technicalSpecs = technicalSpecs;
	}

	public String getTechnicalSpecs() {
		return technicalSpecs;
	}

	public void setDetails(String details) {
		this.details = details;
	}

	public String getDetails() {
		return details;
	}

	public void setSlogan(String slogan) {
		this.slogan = slogan;
	}

	public String getSlogan() {
		return slogan;
	}

	public void setAddedBy(String addedBy) {
		this.addedBy = addedBy;
	}

	public String getAddedBy() {
		return addedBy;
	}

	public void setCreatedTimestamp(Date createdTimestamp) {
		this.createdTimestamp = createdTimestamp;
	}

	public Date getCreatedTimestamp() {
		return createdTimestamp;
	}

	public void setSeller(Seller seller) {
		this.seller = seller;
	}

	public Seller getSeller() {
		return seller;
	}

	public void setLanguage(String language) {
		this.language = language;
	}

	public String getLanguage() {
		return language;
	}

	public void setVisibilityType(Integer visibilityType) {
		this.visibilityType = visibilityType;
	}

	public Integer getVisibilityType() {
		return visibilityType;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof AdTemplate) {
			AdTemplate another = (AdTemplate) obj;
			return this.id.equals(another.getId());
		}
		return false;
	}
	
	@Override
	public int hashCode() {
		return new Long(id).intValue();
	}

	public void setInactiveReason(String inactiveReason) {
		this.inactiveReason = inactiveReason;
	}

	public String getInactiveReason() {
		return inactiveReason;
	}
	
}
