package fi.jonix.huutonet.domain.model;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name="settings")
public class Settings implements Serializable {

	private static final long serialVersionUID = 5885040298694770690L;

	@Id
	@Column(nullable=false, name="key")
	private String key;
	
	@Column(nullable=false, name="value")
	private String value;

	public Settings() {
	}
	
	public Settings(String key, String value) {
		this.key = key;
		this.value = value;
	}

	public String getKey() {
		return key;
	}

	public void setValue(String value) {
		this.value = value;
	}

	public String getValue() {
		return value;
	}

}
