package fi.jonix.huutonet.domain.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

@Entity
@Table(name = "users")
@SequenceGenerator(allocationSize = 1, name = "UserSeq", sequenceName = "users_id_seq")
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "UserSeq")
	private Long id;

	@Column(name = "name", nullable = false, length=64)
	private String name;

	@Column(name = "login_name", nullable = false, length=16, unique=true)
	private String loginName;

	@Column(name = "password_md5", nullable = false, length=64)
	private String passwordMd5;

	@Column(nullable = false, name = "operator")
	private Boolean operator;

	/**
	 * This is the personal email address of the user, for example
	 * some@email.fi, and this address will not be exposed
	 * to the customers.
	 */
	@Column(name = "email_address", nullable = false, length=64, unique=true)
	private String emailAddress;

	@Column(nullable = false, name = "fwd_bugs")
	private Boolean forwardBugs;

	@Column(nullable = false, name = "fwd_glob_err")
	private Boolean forwardGlobalErrors;

	@Column(nullable = false, name = "fwd_glob_oper_pr")
	private Boolean forwardGlobalOperProblems;

	@Column(nullable = false, name = "fwd_oper_unkn_em")
	private Boolean forwardOperatorUnknownEmails;
	
	public Long getId() {
		return id;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}

	public void setLoginName(String loginName) {
		this.loginName = loginName;
	}

	public String getLoginName() {
		return loginName;
	}

	public void setPasswordMd5(String passwordMd5) {
		this.passwordMd5 = passwordMd5;
	}

	public String getPasswordMd5() {
		return passwordMd5;
	}

	public void setOperator(Boolean operator) {
		this.operator = operator;
	}

	public Boolean getOperator() {
		return operator;
	}

	public void setEmailAddress(String emailAddress) {
		this.emailAddress = emailAddress;
	}

	public String getEmailAddress() {
		return emailAddress;
	}

	public void setForwardBugs(Boolean forwardBugs) {
		this.forwardBugs = forwardBugs;
	}

	public Boolean getForwardBugs() {
		return forwardBugs;
	}

	public void setForwardGlobalErrors(Boolean forwardGlobalErrors) {
		this.forwardGlobalErrors = forwardGlobalErrors;
	}

	public Boolean getForwardGlobalErrors() {
		return forwardGlobalErrors;
	}

	public void setForwardGlobalOperProblems(Boolean forwardGlobalOperProblems) {
		this.forwardGlobalOperProblems = forwardGlobalOperProblems;
	}

	public Boolean getForwardGlobalOperProblems() {
		return forwardGlobalOperProblems;
	}

	public void setForwardOperatorUnknownEmails(
			Boolean forwardOperatorUnknownEmails) {
		this.forwardOperatorUnknownEmails = forwardOperatorUnknownEmails;
	}

	public Boolean getForwardOperatorUnknownEmails() {
		return forwardOperatorUnknownEmails;
	}

}
