package fi.jonix.huutonet.domain.model;

import java.io.Serializable;
import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

@Entity
@Table(name = "customer")
@SequenceGenerator(allocationSize = 1, name = "CustomerSeq", sequenceName = "customer_id_seq")
public class Customer implements Serializable {

	private static final long serialVersionUID = 252008607720638116L;

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "CustomerSeq")
	private Long id;

	@Column(nullable = true, name = "phone_number")
	private String phoneNumber;

	//TODO: Make another table for the email address (the same way as order/state_changes
	//works. Or else just make another field "email2" in this (customer) table. Add the
	//second email address whenever EmailFromCustomerProcessor.java realizes that an
	//email from an unknown email address can be associated with an existing customer.
	//=>Ev. ännnu bättre ide (_eventuellt_ bättre...): gör en sökning i email-tabellen,
	//ifall man tidigare har skickat email till eller tagit emot email från samma adress
	//så slår man upp kunden från email.customer_id (detta kräver att vi lägger till fältet
	//email.customer_id (nullable). Ett sådant fält kan ev. komma till användning även
	//på andra ställen (tex. då man fixar webbaserad emailhantering (det jag ska göra för Ida).
	@Column(nullable = false, name = "email", unique = true)
	private String email;

	//@OneToMany(mappedBy = "customer", cascade = { CascadeType.ALL }, fetch = FetchType.LAZY)
	//private Set<Order> orders;

	/**
	 * The dateKicked column is used for determining when the customer was
	 * reminded of something, for example to make sure that "please pay soon" or
	 * "you have paid too little" reminders will only be sent once per week to
	 * the customer.
	 * 
	 * TODO: the dateKicked stuff should probably be seller/customer specific
	 * and not just customer specific.
	 */
	@Column(nullable = false, name = "date_kicked")
	private Date dateKicked;

	/**
	 * TODO: the nbrTimesKicked stuff should probably be seller/customer specific
	 * and not just customer specific.
	 */
	@Column(nullable = false, name = "nbr_times_kicked")
	private Integer nbrTimesKicked;

	/**
	 * This column tells whether the customer has been reminded about too big payments.
	 * It should be set to false whenever the customer places a new order or money comes in.
	 * 
	 * TODO: the informedAboutTooBigPayment stuff should probably be seller/customer specific
	 * and not just customer specific. There was actually one such case for someuser@suomi24.fi
	 * who had paid too much for both Team Lauber and for HK-myynti and SellStar only sent
	 * a "paid too much" message regarding the excess money for Team Lauber.
	 */
	@Column(nullable = false, name = "informed_too_big_pay")
	private Boolean informedAboutTooBigPayment;
	
	/**
	 * TODO: this stuff should probably be seller/customer specific
	 * and not just customer specific.
	 */
	@Column(nullable = false, name = "conf_code", unique = true)
	private String confirmationCode;

	/**
	 * This is the account number to which refunds are paid.
	 * 
	 * TODO: this stuff should probably be seller/customer specific
	 * and not just customer specific.
	 */
	@Column(nullable = true, name = "account_no")
	private String accountNumber;

	public String getPhoneNumber() {
		return phoneNumber;
	}

	public void setPhoneNumber(String phoneNumber) {
		this.phoneNumber = phoneNumber;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public Long getId() {
		return id;
	}

	public void setInformedAboutTooBigPayment(Boolean informedAboutTooBigPayment) {
		this.informedAboutTooBigPayment = informedAboutTooBigPayment;
	}

	public Boolean getInformedAboutTooBigPayment() {
		return informedAboutTooBigPayment;
	}

	public void setDateKicked(Date dateKicked) {
		this.dateKicked = dateKicked;
	}

	public Date getDateKicked() {
		return dateKicked;
	}

	public void setNbrTimesKicked(Integer nbrTimesKicked) {
		this.nbrTimesKicked = nbrTimesKicked;
	}

	public Integer getNbrTimesKicked() {
		return nbrTimesKicked;
	}

	public void setConfirmationCode(String confirmationCode) {
		this.confirmationCode = confirmationCode;
	}

	public String getConfirmationCode() {
		return confirmationCode;
	}

	public void setAccountNumber(String accountNumber) {
		this.accountNumber = accountNumber;
	}

	public String getAccountNumber() {
		return accountNumber;
	}

	/*public Set<Order> getOrders() {
		return orders;
	}*/

	/*public void setOrders(Set<Order> orders) {
		this.orders = orders;
	}*/

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof Customer) {
			Customer another = (Customer) obj;
			return this.id.equals(another.getId());
		}
		return false;
	}
	
	@Override
	public int hashCode() {
		return new Long(id).intValue();
	}
	
}
