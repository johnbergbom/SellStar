package fi.jonix.huutonet.domain.model.dao;

import fi.jonix.huutonet.domain.model.ProductKeyword;

public class ProductKeywordDAOImpl extends GenericDAOImpl <ProductKeyword, Long> implements ProductKeywordDAO {

	public Class<ProductKeyword> getEntityClass() {
		return ProductKeyword.class;
	}
	
}
