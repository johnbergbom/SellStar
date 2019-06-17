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
@Table(name = "provider_question")
@SequenceGenerator(allocationSize = 1, name = "ProviderQuestionSeq", sequenceName = "provider_question_id_seq")
public class ProviderQuestion {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ProviderQuestionSeq")
	private Long id;
	
	//TODO: this field cannot be unique for resendings because for those we might
	//need a different provider question for the same order bundle.
	@ManyToOne(fetch=FetchType.LAZY)
	@JoinColumn(name="order_bundle_id", nullable=false)
	private	OrderBundle orderBundle;
	
	@Column(nullable = false, name = "nbr_messages")
	private Integer nbrMessages;

	@Column(nullable=false, name="last_own_comment")
	private Date lastOwnComment;
	
	@Column(nullable=true, name="last_provider_comment")
	private Date lastProviderComment;
	
	@Column(nullable=true, name="operator_last_informed")
	private Date operatorLastInformed;
	
	/**
	 * A ticket normally stays open as long as we expect an answer from FocalPrice.
	 * If the ticket is still open a week after the last order in the bundle was
	 * shipped, then the ticket is closed automatically in SellStar.
	 */
	@Column(nullable=false, name="open")
	private Boolean open;
	
	@Column(nullable=false, name="provider_ticket_id", unique = true)
	private String providerTicketId;
	
	@Column(nullable=false, name="subject")
	private String subject;
	
	/**
	 * To this field we add the text that we want to send to the
	 * provider (set by any method in SellStar). Then the provider
	 * communication module adds this comment at its next run.
	 * =>Hmm, is this one needed? What if we need to add another
	 * comment before the first one has been taken care of by the
	 * provider communication module? Maybe we should avoid using
	 * this field for the time being.
	 */
	@Column(nullable=true, name="comment_to_add")
	private String commentToAdd;

	/**
	 * This field contains the text that is fetched from the web page.
	 */
	@Column(nullable=false, name="contents")
	private String contents;

	public Long getId() {
		return id;
	}

	public void setOrderBundle(OrderBundle orderBundle) {
		this.orderBundle = orderBundle;
	}

	public OrderBundle getOrderBundle() {
		return orderBundle;
	}

	public void setNbrMessages(Integer nbrMessages) {
		this.nbrMessages = nbrMessages;
	}

	public Integer getNbrMessages() {
		return nbrMessages;
	}

	public void setLastOwnComment(Date lastOwnComment) {
		this.lastOwnComment = lastOwnComment;
	}

	public Date getLastOwnComment() {
		return lastOwnComment;
	}

	public void setLastProviderComment(Date lastProviderComment) {
		this.lastProviderComment = lastProviderComment;
	}

	public Date getLastProviderComment() {
		return lastProviderComment;
	}

	public void setCommentToAdd(String commentToAdd) {
		this.commentToAdd = commentToAdd;
	}

	public String getCommentToAdd() {
		return commentToAdd;
	}

	public void setContents(String contents) {
		this.contents = contents;
	}

	public String getContents() {
		return contents;
	}

	public void setOperatorLastInformed(Date operatorLastInformed) {
		this.operatorLastInformed = operatorLastInformed;
	}

	public Date getOperatorLastInformed() {
		return operatorLastInformed;
	}

	public void setSubject(String subject) {
		this.subject = subject;
	}

	public String getSubject() {
		return subject;
	}

	public void setProviderTicketId(String providerTicketId) {
		this.providerTicketId = providerTicketId;
	}

	public String getProviderTicketId() {
		return providerTicketId;
	}

	public void setOpen(Boolean open) {
		this.open = open;
	}

	public Boolean getOpen() {
		return open;
	}

}
