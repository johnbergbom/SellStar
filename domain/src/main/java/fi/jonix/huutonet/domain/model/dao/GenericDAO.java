package fi.jonix.huutonet.domain.model.dao;

import java.io.Serializable;
import java.util.List;

import org.springframework.dao.DataAccessException;

public interface GenericDAO <T, PK extends Serializable> {

    List<T> loadAll() throws DataAccessException;
    T save(T object) throws DataAccessException;
    public void delete(T object) throws DataAccessException;
	T update(T entity) throws DataAccessException;
    T load(PK id) throws DataAccessException;
	public T get(PK id) throws DataAccessException;
    Class<T> getEntityClass();
	public void flush() throws DataAccessException;
	public void refresh(T object) throws DataAccessException;

}
