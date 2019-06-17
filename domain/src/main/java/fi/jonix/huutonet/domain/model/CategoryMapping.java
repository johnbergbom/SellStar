package fi.jonix.huutonet.domain.model;

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
@Table(name = "category_mapping", uniqueConstraints={ @UniqueConstraint(columnNames={"category_id1", "category_id2"}) })
@SequenceGenerator(allocationSize = 1, name = "CategoryMapSeq", sequenceName = "category_map_id_seq")
public class CategoryMapping {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "CategoryMapSeq")
	private Long id;
	
	@ManyToOne(fetch=FetchType.LAZY)
	@JoinColumn(name="category_id1", nullable=false)
	private Category category1;
	
	@ManyToOne(fetch=FetchType.LAZY)
	@JoinColumn(name="category_id2", nullable=false)
	private Category category2;

	public Long getId() {
		return id;
	}

	public void setCategory1(Category category1) {
		this.category1 = category1;
	}

	public Category getCategory1() {
		return category1;
	}

	public void setCategory2(Category category2) {
		this.category2 = category2;
	}

	public Category getCategory2() {
		return category2;
	}
	
}
