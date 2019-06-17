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
@Table(name = "tracking")
@SequenceGenerator(allocationSize = 1, name = "TrackingSeq", sequenceName = "tracking_id_seq")
public class Tracking {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "TrackingSeq")
	private Long id;

	@Column(nullable = false, name = "tracking_code", unique = true)
	private String trackingCode;

	@ManyToOne(fetch=FetchType.LAZY)
	@JoinColumn(name="order_bundle_id", nullable=false)
	private	OrderBundle orderBundle;

	/**
	 * This is normally true, but false if we determine that the packet cannot
	 * be followed because the post office has screwed up.
	 */
	@Column(nullable = false, name = "follow")
	private Boolean follow;

	public Long getId() {
		return id;
	}

	public void setTrackingCode(String trackingCode) {
		this.trackingCode = trackingCode;
	}

	public String getTrackingCode() {
		return trackingCode;
	}

	public void setOrderBundle(OrderBundle orderBundle) {
		this.orderBundle = orderBundle;
	}

	public OrderBundle getOrderBundle() {
		return orderBundle;
	}

	public void setFollow(Boolean follow) {
		this.follow = follow;
	}

	public Boolean getFollow() {
		return follow;
	}
	
}
