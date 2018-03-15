package org.cryptocoinpartners.schema.dao;

import java.util.List;
import java.util.UUID;

import javax.persistence.NoResultException;

import org.cryptocoinpartners.schema.Bar;
import org.cryptocoinpartners.schema.Book;
import org.cryptocoinpartners.schema.EntityBase;
import org.cryptocoinpartners.schema.Trade;
import org.cryptocoinpartners.util.Visitor;

public interface Dao {
	void persist(EntityBase... entities);

	void persist(Book... book);

	void persist(Trade... trade);

	void persist(Bar... bar);

	void detach(EntityBase... entities);

	EntityBase refresh(EntityBase... entities);

	void merge(EntityBase... entities);

	void merge(Book... book);

	void merge(Trade... trade);

	void merge(Bar... bar);

	void persistEntities(EntityBase... entities) throws Throwable;

	void mergeEntities(EntityBase... entities) throws Throwable;

	void deleteEntities(EntityBase... entities);

	void delete(EntityBase... entities);

	<T> T find(Class<T> resultType, UUID id);

	boolean contains(EntityBase entity);

	<T> T queryZeroOne(Class<T> resultType, String queryStr, Object... params);

	<T> List<T> queryList(Class<T> resultType, String queryStr, Object... params);

	<T> T namedQueryOne(Class<T> resultType, String namedQuery, Object... params) throws NoResultException;

	<T extends EntityBase> T findById(Class<T> resultType, UUID id) throws NoResultException;

	void queryEach(Visitor<Object[]> handler, String queryStr, Object... params);

	<T> void queryEach(Class<T> resultType, Visitor<T> handler, int batchSize, String queryStr, Object... params);

	<T> void queryEach(Class<T> resultType, Visitor<T> handler, String queryStr, Object... params);

	void queryEach(Visitor<Object[]> handler, int batchSize, String queryStr, Object... params);

	<T> Long findRevisionById(Class<T> resultType, UUID id) throws NoResultException;

	<T> Long findVersionById(Class<T> resultType, UUID id) throws NoResultException;

	// T queryZeroOne(Class<T> resultType, String queryStr, Object[] params);

	//  T queryZeroOne(Class<T> resultType, String queryStr, Object[] params);

	//public <T> T queryOne(Class<T> resultType, String queryStr, Object... params);

}
