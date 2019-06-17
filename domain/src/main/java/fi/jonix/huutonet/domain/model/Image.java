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

@Entity
@Table(name="image")
@SequenceGenerator(allocationSize=1, name="ImageSeq", sequenceName="image_id_seq")
public class Image implements Serializable, Comparable<Image> {

	private static final long serialVersionUID = -4051645869580654148L;

	@Id
	@GeneratedValue(strategy=GenerationType.SEQUENCE, generator="ImageSeq")
	private Long id;
	
	@Column(nullable=true, name="html")
	private String html;
	
	@Column(nullable=true, name="small_img")
	private String smallImage;
	
	@Column(nullable=true, name="link_img")
	private String linkImage;
	
	@Column(nullable=true, name="link_img_desc")
	private String linkImageWithDescription;
	
	@Column(nullable=true, name="big_img")
	private String bigImage;
	
	@Column(nullable=false, name="image_order")
	private Integer imageOrder;
	
	@Column(nullable=true, name="thumbnail_image_id")
	private Long thumbnailImageId;
	
	/**
	 * URL of image at image host.
	 */
	@Column(nullable=true, name="img_host_url")
	private String imageHostUrl;
	
	@Column(nullable=true, name="img_text")
	private String imageText;
	
	/**
	 * URL of image at localhost. 
	 */
	@Column(nullable=false, name="loc_file_url")
	private String localFileUrl;
	
	@ManyToOne(fetch=FetchType.LAZY)
	@JoinColumn(name="product_id", nullable=false)
	private Product product;

	public void setProduct(Product product) {
		this.product = product;
	}

	public Image() {
	}
	
	public Image(String localFileUrl, String imageText, Integer imageOrder, Product product) {
		this.localFileUrl = localFileUrl;
		this.imageText = imageText;
		this.imageOrder = imageOrder;
		this.product = product;
	}
	
	public Integer getImageOrder() {
		return imageOrder;
	}

	public void setImageOrder(Integer imageOrder) {
		this.imageOrder = imageOrder;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getId() {
		return id;
	}

	public void setHtml(String html) {
		this.html = html;
	}

	public String getHtml() {
		return html;
	}

	public void setSmallImage(String smallImage) {
		this.smallImage = smallImage;
	}

	public String getSmallImage() {
		return smallImage;
	}

	public void setLinkImage(String linkImage) {
		this.linkImage = linkImage;
	}

	public String getLinkImage() {
		return linkImage;
	}

	public void setLinkImageWithDescription(String linkImageWithDescription) {
		this.linkImageWithDescription = linkImageWithDescription;
	}

	public String getLinkImageWithDescription() {
		return linkImageWithDescription;
	}

	public void setBigImage(String bigImage) {
		this.bigImage = bigImage;
	}

	public String getBigImage() {
		return bigImage;
	}

	public void setImageHostUrl(String imageHostUrl) {
		this.imageHostUrl = imageHostUrl;
	}

	public String getImageHostUrl() {
		return imageHostUrl;
	}

	public void setImageText(String imageText) {
		this.imageText = imageText;
	}

	public String getImageText() {
		return imageText;
	}

	public void setLocalFileUrl(String localFileUrl) {
		this.localFileUrl = localFileUrl;
	}

	public String getLocalFileUrl() {
		return localFileUrl;
	}

	public int compareTo(Image image) {
		if(this.thumbnailImageId != null && image.getThumbnailImageId() == null)
			return -1;
		if(this.thumbnailImageId == null && image.getThumbnailImageId() != null)
			return 1;
		if(image.getImageOrder() == null && this.getImageOrder() == null)
			return 0;
		return this.getImageOrder().compareTo(image.getImageOrder());
	}

	public Product getProduct() {
		return product;
	}

	public void setThumbnailImageId(Long thumbnailImageId) {
		this.thumbnailImageId = thumbnailImageId;
	}

	public Long getThumbnailImageId() {
		return thumbnailImageId;
	}

}
