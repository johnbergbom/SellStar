package fi.jonix.huutonet.commandrow.option;

import java.util.ArrayList;
import java.util.List;

public class Option {
	
	private boolean required;
	private String question;
	private String name;
	private String description;
	private String value;
	private List<String> alternatives;
	
	public Option(boolean required, String name, String description, String question) {
		this.required = required;
		this.name = name;
		this.description = description;
		this.setQuestion(question);
	}
	
	public Option(boolean required, String name, String description, String question, String... alternatives) {
		this.required = required;
		this.name = name;
		this.description = description;
		this.setQuestion(question);
		this.alternatives = new ArrayList<String>();
		for(String alternative : alternatives)
			this.alternatives.add(alternative);
	}

	public boolean isRequired() {
		return required;
	}

	public void setRequired(boolean required) {
		this.required = required;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public void setValue(String value) {
		this.value = value;
	}

	public String getValue() {
		return value;
	}

	public void setQuestion(String question) {
		this.question = question;
	}

	public String getQuestion() {
		return question;
	}

	public void setAlternatives(List<String> alternatives) {
		this.alternatives = alternatives;
	}

	public List<String> getAlternatives() {
		return alternatives;
	}
	
	
	
}
