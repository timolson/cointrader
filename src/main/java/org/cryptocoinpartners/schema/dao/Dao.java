package org.cryptocoinpartners.schema.dao;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;

import javax.persistence.NoResultException;

import org.cryptocoinpartners.schema.EntityBase;
import org.cryptocoinpartners.util.Visitor;

public interface Dao {
    void persist(EntityBase... entities);

    void detach(EntityBase... entities);

    void remove(EntityBase... entities);

    void merge(EntityBase... entities);

    BlockingQueue<EntityBase[]> getInsertQueue();

    BlockingQueue<EntityBase[]> getMergeQueue();

    void persistEntities(EntityBase... entities);

    void mergeEntities(EntityBase... entities);

    <T> T find(Class<T> resultType, UUID id);

    <T> T queryZeroOne(Class<T> resultType, String queryStr, Object... params);

    <T> List<T> queryList(Class<T> resultType, String queryStr, Object... params);

    <T> T namedQueryOne(Class<T> resultType, String namedQuery, Object... params) throws NoResultException;

    <T extends EntityBase> T findById(Class<T> resultType, UUID id) throws NoResultException;

    void queryEach(Visitor<Object[]> handler, String queryStr, Object... params);

    <T> void queryEach(Class<T> resultType, Visitor<T> handler, int batchSize, String queryStr, Object... params);

    <T> void queryEach(Class<T> resultType, Visitor<T> handler, String queryStr, Object... params);

    void queryEach(Visitor<Object[]> handler, int batchSize, String queryStr, Object... params);

    // T queryZeroOne(Class<T> resultType, String queryStr, Object[] params);

    //  T queryZeroOne(Class<T> resultType, String queryStr, Object[] params);

    //public <T> T queryOne(Class<T> resultType, String queryStr, Object... params);

}
