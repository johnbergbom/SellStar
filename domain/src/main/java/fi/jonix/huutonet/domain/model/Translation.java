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

@Entity
@Table(name = "translation")
@SequenceGenerator(allocationSize = 1, name = "TranslationSeq", sequenceName = "translation_id_seq")
public class Translation {

	@Id
	@Column(nullable = false, name = "id")
	@GeneratedValue(strategy=GenerationType.SEQUENCE, generator="TranslationSeq")
	private Long id;

	@Column(nullable = false, name = "original_text")
	private String originalText;

	@Column(nullable = false, name = "translated_text")
	private String translatedText;

	@Column(nullable = false, name = "language")
	private String language;
	
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "seller_id", nullable = false)
	private Seller seller;

	public Translation(){
	}
	
	public Translation(String originalText, String translatedText,
			String language, Seller seller) {
		this.originalText = originalText;
		this.translatedText = translatedText;
		this.language = language;
		this.seller = seller;
	}

	public String getOriginalText() {
		return originalText;
	}

	public void setOriginalText(String originalText) {
		this.originalText = originalText;
	}

	public String getTranslatedText() {
		return translatedText;
	}

	public void setTranslatedText(String translatedText) {
		this.translatedText = translatedText;
	}

	public String getLanguage() {
		return language;
	}

	public void setLanguage(String language) {
		this.language = language;
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public void setSeller(Seller seller) {
		this.seller = seller;
	}

	public Seller getSeller() {
		return seller;
	}
}
