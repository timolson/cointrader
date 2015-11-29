package org.cryptocoinpartners.schema.dao;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import javax.persistence.EntityManager;
import javax.persistence.EntityNotFoundException;
import javax.persistence.NoResultException;
import javax.persistence.OptimisticLockException;
import javax.persistence.Query;
import javax.persistence.TypedQuery;

import org.cryptocoinpartners.schema.EntityBase;
import org.cryptocoinpartners.util.ConfigUtil;
import org.cryptocoinpartners.util.Visitor;
import org.hibernate.PersistentObjectException;
import org.hibernate.StaleObjectStateException;
import org.hibernate.TransientObjectException;
import org.hibernate.TransientPropertyValueException;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import com.google.inject.persist.UnitOfWork;

public abstract class DaoJpa implements Dao {
    protected Class entityClass;
    protected static Logger log = LoggerFactory.getLogger("org.cryptocoinpartners.persist");
    private static final int defaultBatchSize = 20;
    private static int retry;
    private static final BlockingQueue<EntityBase[]> insertQueue = new LinkedBlockingQueue<EntityBase[]>();
    private static final BlockingQueue<EntityBase[]> mergeQueue = new LinkedBlockingQueue<EntityBase[]>();
    static {
        retry = ConfigUtil.combined().getInt("db.persist.retry");
    }
    @Inject
    protected Provider<EntityManager> entityManager;

    @Inject
    private UnitOfWork unitOfWork;

    //protected EntityManager entityManager;

    public DaoJpa() {
        //  ParameterizedType genericSuperclass = (ParameterizedType) getClass().getGenericSuperclass();
        //this.entityClass = (Class) genericSuperclass.getActualTypeArguments()[1];
    }

    @Override
    public void queryEach(Visitor<Object[]> handler, String queryStr, Object... params) {
        try {
            queryEach(handler, defaultBatchSize, queryStr, params);
        } catch (Exception | Error ex) {

            log.error("Unable to perform request in " + this.getClass().getSimpleName() + ":queryEach, full stack trace follows:", ex);
            throw ex;
        }
    }

    @Override
    @Transactional
    public void queryEach(Visitor<Object[]> handler, int batchSize, String queryStr, Object... params) {
        try {
            Query query = entityManager.get().createQuery(queryStr);
            if (params != null) {
                for (int i = 0; i < params.length; i++) {
                    Object param = params[i];
                    query.setParameter(i + 1, param); // JPA uses 1-based indexes
                }
            }
            query.setMaxResults(batchSize);
            for (int start = 0;; start += batchSize) {
                query.setFirstResult(start);
                List list = query.getResultList();
                if (list.isEmpty())
                    return;
                for (Object row : list) {
                    if (row.getClass().isArray() && !handler.handleItem((Object[]) row) || !row.getClass().isArray()
                            && !handler.handleItem(new Object[] { row }))
                        return;
                }
            }
        } catch (Exception | Error ex) {
            log.error("Unable to perform request in " + this.getClass().getSimpleName() + ":queryEach, full stack trace follows:", ex);

            throw ex;

            // log.error("Unable to perform request in " + this.getClass().getSimpleName() + ":queryEach, full stack trace follows:", ex);
            // ex.printStackTrace();

        }
    }

    @Override
    public <T> void queryEach(Class<T> resultType, Visitor<T> handler, String queryStr, Object... params) {
        queryEach(resultType, handler, defaultBatchSize, queryStr, params);
    }

    @Override
    @Transactional
    public <T> void queryEach(Class<T> resultType, Visitor<T> handler, int batchSize, String queryStr, Object... params) {
        try {
            TypedQuery<T> query = entityManager.get().createQuery(queryStr, resultType);
            if (params != null) {
                for (int i = 0; i < params.length; i++) {
                    Object param = params[i];
                    query.setParameter(i + 1, param); // JPA uses 1-based indexes
                }
            }
            query.setMaxResults(batchSize);
            for (int start = 0;; start += batchSize) {
                query.setFirstResult(start);
                List<T> list = query.getResultList();
                if (list.isEmpty())
                    return;
                for (T row : list) {
                    if (!handler.handleItem(row))
                        return;
                }
            }
        } catch (Exception | Error ex) {

            log.error("Unable to perform request in " + this.getClass().getSimpleName() + ":queryEach, full stack trace follows:", ex);
            throw ex;

        }
    }

    @Override
    @Transactional
    public <T> T namedQueryOne(Class<T> resultType, String namedQuery, Object... params) throws NoResultException {
        try {
            TypedQuery<T> query = entityManager.get().createNamedQuery(namedQuery, resultType);
            if (params != null) {
                for (int i = 0; i < params.length; i++) {
                    Object param = params[i];
                    query.setParameter(i + 1, param); // JPA uses 1-based indexes
                }
            }
            return query.getSingleResult();
        } catch (Exception | Error ex) {

            log.error("Unable to perform request in " + this.getClass().getSimpleName() + ":namedQueryOne, full stack trace follows:", ex);
            throw ex;

        }

    }

    @Override
    @Transactional
    public <T> T queryZeroOne(Class<T> resultType, String queryStr, Object... params) {
        try {
            TypedQuery<T> query = entityManager.get().createQuery(queryStr, resultType);
            if (params != null) {
                for (int i = 0; i < params.length; i++) {
                    Object param = params[i];
                    query.setParameter(i + 1, param); // JPA uses 1-based indexes
                }
            }

            return query.getSingleResult();
        } catch (NoResultException x) {
            return null;
        } catch (Error | Exception ex) {
            log.error("Unable to perform request in " + this.getClass().getSimpleName() + ":queryZeroOne, full stack trace follows:", ex);

            throw ex;
        }

    }

    @Transactional
    public <T> T queryOne(Class<T> resultType, String queryStr, Object... params) {
        try {

            TypedQuery<T> query = entityManager.get().createQuery(queryStr, resultType);
            if (params != null) {
                for (int i = 0; i < params.length; i++) {
                    Object param = params[i];
                    query.setParameter(i + 1, param); // JPA uses 1-based indexes
                }
            }
            return query.getSingleResult();
        } catch (NoResultException x) {
            return null;
        } catch (Error | Exception ex) {
            log.error("Unable to perform request in " + this.getClass().getSimpleName() + ":queryOne, full stack trace follows:", ex);

            throw ex;
        }

    }

    @Override
    @Transactional
    public <T> List<T> queryList(Class<T> resultType, String queryStr, Object... params) {
        EntityManager em = null;
        try {

            TypedQuery<T> query = entityManager.get().createQuery(queryStr, resultType);
            if (params != null) {
                for (int i = 0; i < params.length; i++) {
                    Object param = params[i];
                    query.setParameter(i + 1, param); // JPA uses 1-based indexes
                }
            }
            return query.getResultList();
        } catch (TransientObjectException toe) {
            return null;
        } catch (Error | Exception ex) {
            log.error("Unable to perform request in " + this.getClass().getSimpleName() + ":queryList, full stack trace follows:", ex);
            throw ex;
        }
    }

    @Override
    //  @Transactional
    // @com.google.inject.persist.Transactional
    public void detach(EntityBase... entities) {
        for (EntityBase entity : entities)
            try {

                // EntityBase existingEntity = entityManager.get().find(entity.getClass(), entity.getId());
                //if (existingEntity != null) {
                //entityManager.get().merge(entity);
                //entityManager.get().flush();
                ///    } else
                // update(entity);

                evict(entity);
                // entityManager.get().detach(entity);
                //    entityManager.get().detach(entity);

                //entityManager.get().flush();

                // log.debug("persisting entity " + entity.getClass().getSimpleName() + " " + entity);
                //   entityManager.get().getTransaction().commit();
            }

            catch (Exception | Error ex) {

                log.error("Unable to perform request in " + this.getClass().getSimpleName() + ":detach, full stack trace follows:", ex);
                //
                throw ex;
                //ex.printStackTrace();

            }
    }

    @Transactional
    @Override
    public <T> T find(Class<T> resultType, UUID id) {
        try {
            return entityManager.get().find(resultType, id);
        } catch (Error | Exception ex) {
            log.error("Unable to perform request in " + this.getClass().getSimpleName() + ":find, full stack trace follows:", ex);

            throw ex;
        }

    }

    @Transactional
    public <T> boolean contains(EntityBase entity) {
        try {
            return entityManager.get().contains(entity);
        } catch (Error | Exception ex) {
            log.error("Unable to perform request in " + this.getClass().getSimpleName() + ":contains, full stack trace follows:", ex);

            throw ex;
        }

    }

    @Transactional
    public <T> T getReference(Class<T> resultType, UUID id) {
        try {
            return entityManager.get().getReference(resultType, id);
        } catch (Error | Exception ex) {
            log.error("Unable to perform request in " + this.getClass().getSimpleName() + ":getReference, full stack trace follows:", ex);

            throw ex;
        }

    }

    // @Override
    // @Transactional
    // @com.google.inject.persist.Transactional
    //  @Transactional
    //  @Inject
    @Override
    public void persist(EntityBase... entities) {
        int attempt = 0;
        // EntityManager em = entityManager.get();
        // thisEntity=EntityBase
        //        try {
        //            unitOfWork.begin();
        //        } catch (Exception | Error ex) {
        //            log.error("Unable to perform request in " + this.getClass().getSimpleName() + ":persist, full stack trace follows:", ex);
        //            //throw ex;
        //
        //            // catch (IllegalStateException ex) {
        //
        //        } finally {
        //            unitOfWork.end();
        //        }
        //  unitOfWork.end();

        while (attempt <= retry) {

            //  EntityBase thisentity;

            try {
                for (EntityBase entity : entities) {
                    // em.lock(entity, LockModeType.PESSIMISTIC_WRITE);
                    // em.refresh(entity);
                    //  if (em.contains(entity))
                    //em.merge(entity);
                    // else

                    insert(entity);
                    //  em.persist(entity);
                    //  PersistUtilHelper.commit();

                }

                // } /*catch (ConstraintViolationException cve) {
                //  for (EntityBase entity : entities) 
                //    log.info("Entity " + entity.getClass().getSimpleName() + " already pesisted with id: " + entity.getId() + ".", cve);
                //  unitOfWork.end();

                // merge(entity);
                //  }
                // break;
                //Entity you are trying to insert already exist, then call merge method
                /// catch (javax.persistence.PersistenceException pe) {
                //    entity.detach();
                // unitOfWork.end();
                // for (EntityBase entity : entities)
                //   merge(entity);
                // entity.merge();
                // break;
                // }*/
            } catch (OptimisticLockException | StaleObjectStateException ole) {

                for (EntityBase entity : entities) {
                    //  entity.setVersion(entity.getVersion() + 1);
                    log.error("Later version of " + entity.getClass().getSimpleName() + " id: " + entity.getId() + " already persisted. Persist attempt "
                            + attempt + " of " + retry);
                }
                if (attempt == retry) {
                    log.error(" " + this.getClass().getSimpleName() + ":persist, full stack trace follows:", ole);
                    throw ole;
                } else {
                    attempt++;
                    continue;
                }

            }

            catch (Exception | Error ex) {
                if (ex.getCause() != null && ex.getCause().getCause() instanceof TransientPropertyValueException) {
                    for (EntityBase entity : entities) {
                        //  entity.setVersion(entity.getVersion() + 1);

                        log.error("Parent object not persisted for  " + entity.getClass().getSimpleName() + " id: " + entity.getId() + ". Persist attempt "
                                + attempt + " of " + retry);
                    }
                    if (attempt == retry) {
                        log.error(" " + this.getClass().getSimpleName() + ":persist, full stack trace follows:", ex);
                        throw ex;
                    } else {
                        attempt++;
                        continue;
                    }
                } else if (ex.getCause() != null && ex.getCause().getCause() instanceof ConstraintViolationException) {
                    for (EntityBase entity : entities)
                        log.debug(" " + this.getClass().getSimpleName() + " " + entity.getClass().getSimpleName() + ":" + entity.getId()
                                + " :persist, already in db");
                } else if (ex.getCause() != null && ex.getCause() instanceof PersistentObjectException) {
                    for (EntityBase entity : entities) {
                        log.debug(" " + this.getClass().getSimpleName() + " " + entity.getClass().getSimpleName() + ":" + entity.getId()
                                + " :persist, already in db, merging update");
                        //   update(entity);
                    }

                } else {
                    log.error(" " + this.getClass().getSimpleName() + ":persist, full stack trace follows:", ex);
                    throw ex;
                }
                //break;

            }
            break;
        }
    }

    //   unitOfWork.end();

    //     break;
    //   }

    @Override
    public void persistEntities(EntityBase... entities) {

        try {
            insertQueue.put(entities);
        } catch (InterruptedException e) {
            log.error("Unable to resubmit insert request in " + this.getClass().getSimpleName() + "insert, full stack trace follows:", e);
            e.printStackTrace();

        } finally {

        }

    }

    public class persistRunnable implements Runnable {

        @Override
        public void run() {
            while (true) {
                try {
                    EntityBase[] entities = insertQueue.take();
                    persistEntities(entities);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return; // supposing there is no cleanup or other stuff to be done

                }
            }

        }

        public persistRunnable() {

        }

    }

    public class mergeRunnable implements Runnable {

        @Override
        public void run() {
            while (true) {
                try {
                    EntityBase[] entities = mergeQueue.take();
                    mergeEntities(entities);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return; // supposing there is no cleanup or other stuff to be done

                }
            }

        }

        public mergeRunnable() {

        }

    }

    //  @Nullable
    // @ManyToOne(optional = true)
    //, cascade = { CascadeType.PERSIST, CascadeType.MERGE })
    //cascade = { CascadeType.ALL })
    // @JoinColumn(name = "position")
    @Override
    public BlockingQueue<EntityBase[]> getInsertQueue() {

        return insertQueue;
    }

    @Override
    public BlockingQueue<EntityBase[]> getMergeQueue() {

        return mergeQueue;
    }

    @Override
    public void mergeEntities(EntityBase... entities) {

        try {
            mergeQueue.put(entities);
        } catch (InterruptedException e) {
            log.error("Unable to resubmit merge request in " + this.getClass().getSimpleName() + " merge, full stack trace follows:", e);
            e.printStackTrace();

        } finally {

        }

    }

    @Transactional
    public void insert(EntityBase entity) {
        try {
            entityManager.get().persist(entity);
        } catch (Error | Exception ex) {
            //   log.error("Unable to perform request in " + this.getClass().getSimpleName() + ":getReference, full stack trace follows:", ex);

            throw ex;
        }

        // entityManager.get().getTransaction().commit();
        // entityManager.get().flush();
        // TODO Auto-generated method stub

    }

    @Transactional
    public void update(EntityBase entity) {
        //  try {
        entityManager.get().merge(entity);
        //} catch (Error | Exception ex) {
        //  throw ex;
        // }

        // TODO Auto-generated method stub

    }

    @Transactional
    public void evict(EntityBase entity) {
        try {
            // entityManager.get().(entity);
            entityManager.get().detach(entity);
        } catch (Error | Exception ex) {
            throw ex;
        }

        // TODO Auto-generated method stub

    }

    // @Override
    // @Transactional
    // @Override
    // @Transactional
    // @Inject
    @Override
    public void merge(EntityBase... entities) {
        //  unitOfWork.end();
        //        try {
        //            unitOfWork.begin();
        //        } catch (Exception | Error ex) {
        //            log.error("Unable to perform request in " + this.getClass().getSimpleName() + ":persist, full stack trace follows:", ex);
        //
        //            // catch (IllegalStateException ex) {
        //
        //        } finally {
        //            unitOfWork.end();
        //        }
        int attempt = 0;
        while (attempt <= retry) {
            // unitOfWork.begin();
            try {
                for (EntityBase entity : entities)

                    //     entityManager.get().getTransaction().begin();
                    // entityManager.get().flush();
                    update(entity);
                // entityManager.get().merge(entity);
                // log.debug("merged entity " + entity.getClass().getSimpleName() + " " + entity);
                //   entityManager.get().getTransaction().commit();
            } catch (OptimisticLockException | EntityNotFoundException | StaleObjectStateException ole) {
                //     unitOfWork.end();

                for (EntityBase entity : entities) {
                    entity.setVersion(entity.getVersion() + 1);

                    log.error("Later version of " + entity.getClass().getSimpleName() + " id: " + entity.getId() + " already merged. Merge attempt " + attempt
                            + " of " + retry);
                }
                if (attempt == retry) {
                    log.error(" " + this.getClass().getSimpleName() + ":merge, full stack trace follows:", ole);
                    throw ole;
                } else {
                    attempt++;
                    continue;
                }

            } catch (Exception | Error ex) {
                if (ex.getCause() != null && ex.getCause().getCause() instanceof TransientPropertyValueException) {
                    for (EntityBase entity : entities) {
                        //  entity.setVersion(entity.getVersion() + 1);

                        log.error("Parent object not persisted for  " + entity.getClass().getSimpleName() + " id: " + entity.getId() + ". Merge attempt "
                                + attempt + " of " + retry);
                    }
                    if (attempt == retry) {
                        log.error(" " + this.getClass().getSimpleName() + ":merge, full stack trace follows:", ex);
                        throw ex;
                    } else {
                        attempt++;
                        continue;
                    }
                } else if (ex.getCause() != null && ex.getCause().getCause() instanceof ConstraintViolationException) {
                    for (EntityBase entity : entities)
                        log.debug(" " + this.getClass().getSimpleName() + " " + entity.getClass().getSimpleName() + ":" + entity.getId()
                                + " :merge, already in db");
                } else {
                    log.error(" " + this.getClass().getSimpleName() + ":merge, full stack trace follows:", ex);
                    throw ex;
                }

                //     unitOfWork.end();

            }
            break;
        }
        //  break;

        // ex.printStackTrace();

    }

    /**
    returns a single result entity.  if none found, a javax.persistence.NoResultException is thrown.
    */
    // @Override
    //    public <T> T queryOne(Class<T> resultType, String queryStr, Object... params) {
    //
    //        final TypedQuery<T> query = entityManager.get().createQuery(queryStr, resultType);
    //        if (params != null) {
    //            for (int i = 0; i < params.length; i++) {
    //                Object param = params[i];
    //                query.setParameter(i + 1, param); // JPA uses 1-based indexes
    //            }
    //        }
    //        return query.getSingleResult();
    //
    //    }

    @Override
    @Transactional
    public void remove(EntityBase... entities) {
        try {
            for (EntityBase entity : entities)
                entityManager.get().remove(entity);
        } catch (Error | Exception ex) {
            throw ex;
        }

    }

    @Override
    public <T extends EntityBase> T findById(Class<T> resultType, UUID id) throws NoResultException {
        try {
            return queryOne(resultType, "select x from " + resultType.getSimpleName() + " x where x.id = ?1", id);
        } catch (Exception | Error ex) {
            log.error("Unable to perform request in " + this.getClass().getSimpleName() + ":findById, full stack trace follows:", ex);
            throw ex;
            //break;

        }
    }

}
