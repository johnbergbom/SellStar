package fi.jonix.huutonet.domain.model;

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
@Table(name = "message", uniqueConstraints=@UniqueConstraint(columnNames={"key", "seller_id", "language"}))
@SequenceGenerator(allocationSize = 1, name = "MessageSeq", sequenceName = "message_id_seq")
public class Message {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "MessageSeq")
	private Long id;

	@Column(nullable = false, name = "key", length=255)
	private String key;

	/**
	 * The default message for a certain key is a message having seller_id = null.
	 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "seller_id", nullable = true)
	private Seller seller;

	@Column(nullable = false, name = "language", length=16)
	private String language;

	@Column(nullable = false, name = "text", length=4096)
	private String text;

	public Long getId() {
		return id;
	}

	public void setKey(String key) {
		this.key = key;
	}

	public String getKey() {
		return key;
	}

	public void setSeller(Seller seller) {
		this.seller = seller;
	}

	public Seller getSeller() {
		return seller;
	}

	public void setText(String text) {
		this.text = text;
	}

	public String getText() {
		return text;
	}

	public void setLanguage(String language) {
		this.language = language;
	}

	public String getLanguage() {
		return language;
	}

}
