package fi.jonix.huutonet.domain.model.dao;

import java.util.List;

import fi.jonix.huutonet.domain.model.Image;
import fi.jonix.huutonet.domain.model.Product;

public interface ImageDAO extends GenericDAO<Image, Long> {

	public List<Image> getByProduct(Product product);

}
