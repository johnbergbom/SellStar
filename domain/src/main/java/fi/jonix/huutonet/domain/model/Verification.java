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

/**
 * This class keeps track of sales and is used when generating verifications for book keeping.
 */
@Entity
@Table(name = "verification", uniqueConstraints={ @UniqueConstraint(columnNames={"vat_perc", "currency_id"}) })
@SequenceGenerator(allocationSize = 1, name = "VerificationSeq", sequenceName = "verification_id_seq")
public class Verification {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "VerificationSeq")
	private Long id;
	
	@Column(nullable = false, name = "vat_perc", precision=12, scale=6)
	private BigDecimal vatPercentage;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "currency_id", nullable = false)
	private Currency currency;

	@Column(nullable = false, name = "tot_sp_inc_vat", precision=12, scale=6)
	private BigDecimal totalSalesPriceInclVat;

	@Column(nullable = false, name = "tot_sp_exc_vat", precision=12, scale=6)
	private BigDecimal totalSalesPriceExclVat;

	/**
	 * This field tells how much was sold BEFORE the new order based verification
	 * scheme got into production. It's value was set when the new order based
	 * verification scheme was put into production and should never be changed.
	 */
	@Column(nullable = false, name = "tot_sp_inc_vat_corr", precision=12, scale=6)
	private BigDecimal totalSalesPriceInclVatCorrection;

	/**
	 * This field tells how much was sold BEFORE the new order based verification
	 * scheme got into production. It's value was set when the new order based
	 * verification scheme was put into production and should never be changed.
	 */
	@Column(nullable = false, name = "tot_sp_exc_vat_corr", precision=12, scale=6)
	private BigDecimal totalSalesPriceExclVatCorrection;

	public Long getId() {
		return id;
	}

	public void setVatPercentage(BigDecimal vatPercentage) {
		this.vatPercentage = vatPercentage;
	}

	public BigDecimal getVatPercentage() {
		return vatPercentage;
	}

	public void setCurrency(Currency currency) {
		this.currency = currency;
	}

	public Currency getCurrency() {
		return currency;
	}

	public void setTotalSalesPriceInclVat(BigDecimal totalSalesPriceInclVat) {
		this.totalSalesPriceInclVat = totalSalesPriceInclVat;
	}

	public BigDecimal getTotalSalesPriceInclVat() {
		return totalSalesPriceInclVat;
	}

	public void setTotalSalesPriceExclVat(BigDecimal totalSalesPriceExclVat) {
		this.totalSalesPriceExclVat = totalSalesPriceExclVat;
	}

	public BigDecimal getTotalSalesPriceExclVat() {
		return totalSalesPriceExclVat;
	}

	public void setTotalSalesPriceInclVatCorrection(
			BigDecimal totalSalesPriceInclVatCorrection) {
		this.totalSalesPriceInclVatCorrection = totalSalesPriceInclVatCorrection;
	}

	public BigDecimal getTotalSalesPriceInclVatCorrection() {
		return totalSalesPriceInclVatCorrection;
	}

	public void setTotalSalesPriceExclVatCorrection(
			BigDecimal totalSalesPriceExclVatCorrection) {
		this.totalSalesPriceExclVatCorrection = totalSalesPriceExclVatCorrection;
	}

	public BigDecimal getTotalSalesPriceExclVatCorrection() {
		return totalSalesPriceExclVatCorrection;
	}

}
