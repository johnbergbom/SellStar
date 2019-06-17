package fi.jonix.huutonet.domain.model;

import java.util.Date;

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
@Table(name = "state_changes")
@SequenceGenerator(allocationSize = 1, name = "StateChangesSeq", sequenceName = "state_change_id_seq")
public class StateChange implements Comparable<StateChange> {
	
	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "StateChangesSeq")
	Long id;
	
	@ManyToOne(fetch=FetchType.LAZY)
	@JoinColumn(name="order_id", nullable=false)
	private	Order order;
	
	@Column(nullable=false, name="state")
	Integer state;
	
	@Column(nullable=false, name="update_date")
	Date updateDate;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Integer getState() {
		return state;
	}

	public void setState(Integer state) {
		this.state = state;
	}

	public Date getUpdateDate() {
		return updateDate;
	}

	public void setUpdateDate(Date updateDate) {
		this.updateDate = updateDate;
	}

	public void setOrder(Order order) {
		this.order = order;
	}

	public Order getOrder() {
		return order;
	}

	public int compareTo(StateChange o) {
		return this.getUpdateDate().compareTo(o.getUpdateDate());
	}
	
}
