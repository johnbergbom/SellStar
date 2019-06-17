package fi.jonix.huutonet.domain.model.dao;

import java.io.Serializable;
import java.util.List;

import org.hibernate.Criteria;
import org.hibernate.criterion.DetachedCriteria;
import org.springframework.dao.DataAccessException;
import org.springframework.orm.hibernate3.support.HibernateDaoSupport;

public abstract class GenericDAOImpl <T, PK extends Serializable> extends HibernateDaoSupport implements GenericDAO<T, PK> {

	protected Class<T> entityClass = getEntityClass();
	
	public void delete(T entity) throws DataAccessException {
		getHibernateTemplate().delete(entity);
	}
	
	public T save(T entity) throws DataAccessException {
		getHibernateTemplate().saveOrUpdate(entity);
		return entity;
	}
	
	public T update(T entity) throws DataAccessException {
		getHibernateTemplate().update(entity);
		return entity;
	}
	
	/**
	 * Return the persistent instance of the given entity class
	 * with the given identifier, throwing an exception if not found.
	 * 
	 * You should not use this method to determine if an instance exists
	 * (use get() instead). Use this only to retrieve an instance that you
	 * assume exists, where non-existence would be an actual error.
	 */
	@SuppressWarnings("unchecked")
	public T load(PK id) throws DataAccessException {
		return (T)getHibernateTemplate().load(entityClass, id);
	}

	/**
	 * Return the persistent instance of the given entity class with the
	 * given identifier, or null if not found.
	 */
	@SuppressWarnings("unchecked")
	public T get(PK id) throws DataAccessException {
		return (T)getHibernateTemplate().get(entityClass, id);
	}

	public List<T> loadAll() throws DataAccessException {
		//return getHibernateTemplate().loadAll(entityClass);
		
		DetachedCriteria dc = createCriteria()
			.setResultTransformer(Criteria.DISTINCT_ROOT_ENTITY);
		return (List<T>)getHibernateTemplate().findByCriteria(dc);
	}

	public DetachedCriteria createCriteria() {
		DetachedCriteria dc = DetachedCriteria.forClass(entityClass);
		dc = dc.setResultTransformer(Criteria.DISTINCT_ROOT_ENTITY);
		return dc;
	}
	
	/**
	 * Flush all pending saves, updates and deletes to the database.
	 * 
	 * @throws DataAccessException
	 */
	public void flush() throws DataAccessException {
		getHibernateTemplate().flush();
	}

	/**
	 * Re-read the state of the given persistent instance.
	 * 
	 * @throws DataAccessException
	 */
	public void refresh(T object) throws DataAccessException {
		getHibernateTemplate().refresh(object);
	}
	
}
