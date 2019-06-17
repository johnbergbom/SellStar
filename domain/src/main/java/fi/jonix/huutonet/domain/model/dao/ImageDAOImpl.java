package fi.jonix.huutonet.domain.model.dao;

import java.util.List;

import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Restrictions;

import fi.jonix.huutonet.domain.model.Image;
import fi.jonix.huutonet.domain.model.Product;

public class ImageDAOImpl extends GenericDAOImpl <Image, Long> implements ImageDAO  {

	public Class<Image> getEntityClass() {
		return Image.class;
	}

	public List<Image> getByProduct(Product product) {
		DetachedCriteria criteria = DetachedCriteria.forClass(this.getEntityClass());
		criteria.add(Restrictions.eq("product", product));
		return this.getHibernateTemplate().findByCriteria(criteria);
	}
	
}