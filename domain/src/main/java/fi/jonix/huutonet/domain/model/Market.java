package fi.jonix.huutonet.domain.model;

import java.io.Serializable;
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

/**
 * This class defines different markets, e.g. Huutonet.
 */
@Entity
@Table(name = "market")
@SequenceGenerator(allocationSize = 1, name = "MarketSeq", sequenceName = "market_id_seq")
public class Market implements Serializable {

	private static final long serialVersionUID = -4437158656090171821L;
	public static String HUUTONET_MARKET_NAME = "Huutonet"; 
	public static String GOOGLE_ORDER_MARKET_NAME = "Google-Order"; 
	public static String SELLSTAR_MARKET_NAME = "SellStar"; 
	public static String MIKKO_MARKET_NAME = "Mikko"; 

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "MarketSeq")
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

	@Column(nullable = false, name = "language", length=16)
	private String language;

	public Market() {
	}
	
	/*public Market(String name, String url, int currency) {
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

	public void setLanguage(String language) {
		this.language = language;
	}

	public String getLanguage() {
		return language;
	}

}
