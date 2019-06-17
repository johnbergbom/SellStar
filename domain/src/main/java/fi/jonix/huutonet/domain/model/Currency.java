package fi.jonix.huutonet.domain.model;

import java.math.BigDecimal;
import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

@Entity
@Table(name = "currency")
@SequenceGenerator(allocationSize = 1, name = "CurrencySeq", sequenceName = "currency_id_seq")
public class Currency {

	public static final long EURO = 1;
	public static final long DOLLAR = 2;

	//public static final String EURO_STR = "EURO";
	//public static final String DOLLAR_STR = "DOLLAR";

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "CurrencySeq")
	private Long id;
	
	@Column(nullable = false, name = "name", unique = true, length=16)
	private String name;
	
	@Column(nullable = false, name = "symbol", unique = true, length=16)
	private String symbol;
	
	@Column(nullable = false, name = "exrate_euro", precision=12, scale=6)
	private BigDecimal exchangeRateToEuro;

	@Column(nullable=false, name="last_updated")
	private Date updateDate;

	public Long getId() {
		return id;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}

	public void setSymbol(String symbol) {
		this.symbol = symbol;
	}

	public String getSymbol() {
		return symbol;
	}

	public void setExchangeRateToEuro(BigDecimal exchangeRateToEuro) {
		this.exchangeRateToEuro = exchangeRateToEuro;
	}

	public BigDecimal getExchangeRateToEuro() {
		return exchangeRateToEuro;
	}

	public void setUpdateDate(Date updateDate) {
		this.updateDate = updateDate;
	}

	public Date getUpdateDate() {
		return updateDate;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof Currency) {
			Currency another = (Currency) obj;
			return this.id.equals(another.getId());
		}
		return false;
	}
	
	@Override
	public int hashCode() {
		return new Long(id).intValue();
	}
	
}
