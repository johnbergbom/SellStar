package fi.jonix.huutonet.domain.model;

import java.io.Serializable;

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

@Entity
@Table(name = "product_keywords")
@SequenceGenerator(allocationSize = 1, name = "ProductKeywordSeq", sequenceName = "product_keyword_id_seq")
public class ProductKeyword implements Serializable {

	private static final long serialVersionUID = 985292948047454344L;
	
	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ProductKeywordSeq")
	private Long id;
	
	@Column(nullable = false, name = "keyword")
	private String keyword;
	
	@ManyToOne(fetch=FetchType.LAZY, cascade = { CascadeType.ALL })
	@JoinColumn(name="product_id", nullable=false)
	private Product product;

	public void setId(Long id) {
		this.id = id;
	}

	public Long getId() {
		return id;
	}

	public void setKeyword(String keyword) {
		this.keyword = keyword;
	}

	public String getKeyword() {
		return keyword;
	}

	public void setProduct(Product product) {
		this.product = product;
	}

	public Product getProduct() {
		return product;
	}
	
	
}
