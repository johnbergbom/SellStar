package fi.jonix.huutonet.domain.model;

import java.io.Serializable;

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
@Table(name = "category", uniqueConstraints={ @UniqueConstraint(columnNames={"name", "parent_category_id"}) })
@SequenceGenerator(allocationSize = 1, name = "CategorySeq", sequenceName = "category_id_seq")
public class Category implements Serializable {
	
	private static final long serialVersionUID = -7420650120794960049L;

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "CategorySeq")
	private Long id;
	
	@Column(nullable = false, name = "name")
	private String name;
	
	@ManyToOne(fetch=FetchType.LAZY)
	@JoinColumn(name="parent_category_id", nullable=true)
	private Category parentCategory;
	
	@ManyToOne(fetch=FetchType.LAZY)
	@JoinColumn(name = "market_id", nullable=false)
	private Market market;

	/**
	 * This isn't the same as market id, but rather an id that is used for the category
	 * in question at the market place.
	 */
	@Column(nullable = false, name = "market_spec_id")
	private Integer marketSpecId;

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Category getParentCategory() {
		return parentCategory;
	}

	public void setParentCategory(Category parentCategory) {
		this.parentCategory = parentCategory;
	}

	public void setMarketSpecId(Integer marketSpecId) {
		this.marketSpecId = marketSpecId;
	}

	public Integer getMarketSpecId() {
		return marketSpecId;
	}

	public void setMarket(Market market) {
		this.market = market;
	}

	public Market getMarket() {
		return market;
	}

}
