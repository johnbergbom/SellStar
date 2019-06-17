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

/**
 * This class sits between the customer and the market table and
 * the reason for having this class is that the same login name
 * at market place A can belong to a different user than the same
 * login name at market place B.
 * 
 * @author john
 *
 */
@Entity
@Table(name = "login_name", uniqueConstraints={@UniqueConstraint(columnNames={"customer_id", "market_id"}),
		@UniqueConstraint(columnNames={"market_id", "login_name"})})
@SequenceGenerator(allocationSize = 1, name = "LoginNameSeq", sequenceName = "login_name_id_seq")
public class LoginName implements Serializable {

	private static final long serialVersionUID = -4236209840614853153L;

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "LoginNameSeq")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "customer_id", nullable = false)
	private Customer customer;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "market_id", nullable = false)
	private Market market;

	@Column(name = "login_name", nullable = false)
	private String loginName;

	public Long getId() {
		return id;
	}

	public void setCustomer(Customer customer) {
		this.customer = customer;
	}

	public Customer getCustomer() {
		return customer;
	}

	public void setMarket(Market market) {
		this.market = market;
	}

	public Market getMarket() {
		return market;
	}

	public void setLoginName(String loginName) {
		this.loginName = loginName;
	}

	public String getLoginName() {
		return loginName;
	}

}
