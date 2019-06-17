package fi.jonix.huutonet.email;

import fi.jonix.huutonet.domain.model.SystemEmail;

/**
 * Envelope class for emails.
 * 
 * @author john
 *
 */
public class EmailWrapper {

	private SystemEmail systemEmail;
	private String address;
	private String subject;
	private String text;

	public EmailWrapper(SystemEmail systemEmail, String address, String subject, String text) {
		this.systemEmail = systemEmail;
		this.address = address;
		this.subject = subject;
		this.text = text;
	}

	public String getAddress() {
		return address;
	}

	public String getSubject() {
		return subject;
	}

	public String getText() {
		return text;
	}
	
	public SystemEmail getSystemEmail() {
		return systemEmail;
	}
	
}
