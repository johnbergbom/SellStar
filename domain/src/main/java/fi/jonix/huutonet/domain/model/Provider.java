package fi.jonix.huutonet.domain.model;

import java.io.Serializable;
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

/**
 * This class defines different providers, e.g. DealExtreme.
 */
@Entity
@Table(name = "provider")
@SequenceGenerator(allocationSize = 1, name = "ProviderSeq", sequenceName = "provider_id_seq")
public class Provider implements Serializable {

	private static final long serialVersionUID = -7353955255001665125L;
	public static String DEAL_EXTREME_PROVIDER_NAME = "DealExtreme";
	public static String FOCAL_PRICE_PROVIDER_NAME = "FocalPrice"; 
	public static String HAUTALA_PROVIDER_NAME = "Hautala"; 
	
	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ProviderSeq")
	private Long id;
	
	@Column(nullable = false, name = "name", unique=true)
	private String name;
	
	@Column(nullable = false, name = "url")
	private String url;

	//@Column(nullable = false, name = "currency")
	//private Integer currency;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "currency_id", nullable = false)
	private Currency currency;

	@Column(nullable = false, name = "username", length=32)
	private String userName;
	
	@Column(nullable = false, name = "password", length=32)
	private String password;
	
	/**
	 * This is the email address which is used for communicating with the provider,
	 * and it will not be exposed to the customer (unless the seller uses the same
	 * email address of course).
	 */
	@ManyToOne(fetch=FetchType.EAGER, cascade = { CascadeType.ALL })
	@JoinColumn(name="system_email_id", nullable=false)
	private SystemEmail systemEmail;
	
	/**
	 * TODO: this field can be removed once bundle support is fixed also for deal extreme.
	 */
	@Column(name = "bundle_support", nullable = false)
	private Boolean bundleSupport;

	@Column(nullable = false, name = "location", length=32)
	private String location;
	
	/**
	 * Value added tax for the provider (i.e. the vat that we pay to the provider).
	 */
	@Column(nullable = false, name = "vat_perc", precision=12, scale=6)
	private BigDecimal vatPercentage;

	public Provider() {
	}
	
	/*public Provider(String name, String url, int currency) {
		this.name = name;
		this.url = url;
		this.currency = currency;
	}*/
	
	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public void setCurrency(Currency currency) {
		this.currency = currency;
	}

	public Currency getCurrency() {
		return currency;
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

	public void setSystemEmail(SystemEmail systemEmail) {
		this.systemEmail = systemEmail;
	}

	public SystemEmail getSystemEmail() {
		return systemEmail;
	}

	public void setBundleSupport(Boolean bundleSupport) {
		this.bundleSupport = bundleSupport;
	}

	public Boolean getBundleSupport() {
		return bundleSupport;
	}

	public void setLocation(String location) {
		this.location = location;
	}

	public String getLocation() {
		return location;
	}

	public void setVatPercentage(BigDecimal vatPercentage) {
		this.vatPercentage = vatPercentage;
	}

	public BigDecimal getVatPercentage() {
		return vatPercentage;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof Provider) {
			Provider another = (Provider) obj;
			return this.id.equals(another.getId());
		}
		return false;
	}
	
	@Override
	public int hashCode() {
		return new Long(id).intValue();
	}
	
}
