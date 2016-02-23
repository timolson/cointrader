package org.cryptocoinpartners.schema.dao;

import java.util.List;
import java.util.UUID;

import javax.persistence.EntityManager;
import javax.persistence.EntityNotFoundException;
import javax.persistence.NoResultException;
import javax.persistence.OptimisticLockException;
import javax.persistence.Query;
import javax.persistence.TypedQuery;

import org.cryptocoinpartners.enumeration.PersistanceAction;
import org.cryptocoinpartners.module.ApplicationInitializer;
import org.cryptocoinpartners.schema.EntityBase;
import org.cryptocoinpartners.util.ConfigUtil;
import org.cryptocoinpartners.util.Visitor;
import org.hibernate.PersistentObjectException;
import org.hibernate.PropertyAccessException;
import org.hibernate.StaleObjectStateException;
import org.hibernate.TransientObjectException;
import org.hibernate.TransientPropertyValueException;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.LockTimeoutException;
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
    static {
        retry = ConfigUtil.combined().getInt("db.persist.retry");
    }
    @Inject
    protected Provider<EntityManager> entityManager;

    @Inject
    protected ApplicationInitializer application;

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

    @Override
    public EntityBase refresh(EntityBase... entities) {
        EntityBase localEntity = null;
        for (EntityBase entity : entities)
            try {

                // EntityBase existingEntity = entityManager.get().find(entity.getClass(), entity.getId());
                //if (existingEntity != null) {
                //entityManager.get().merge(entity);
                //entityManager.get().flush();
                ///    } else
                // update(entity);

                localEntity = restore(entity);
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

        return localEntity;
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

    @Override
    @Transactional
    public boolean contains(EntityBase entity) {
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
    public void persistEntities(EntityBase... entities) {
        int attempt = 0;
        boolean persisted = false;
        for (EntityBase entity : entities) {
            try {
                long revision = entity.findRevisionById();
                if (entity.getRevision() > revision) {
                    insert(entity);
                    persisted = true;
                }

            } catch (OptimisticLockException | StaleObjectStateException ole) {
                attempt = entity.getAttempt() + 1;
                entity.setAttempt(attempt);

                if (attempt >= retry) {
                    log.error(" " + this.getClass().getSimpleName() + ":persist, full stack trace follows:", ole);

                    entity.setAttempt(0);

                    throw ole;
                } else {
                    log.debug("Later version of " + entity.getClass().getSimpleName() + " id: " + entity.getId() + " already persisted. Persist attempt "
                            + entity.getAttempt() + " of " + retry);

                    entity.setAttempt(0);
                }

            } catch (LockTimeoutException lte) {

                attempt = entity.getAttempt() + 1;
                entity.setAttempt(attempt);

                if (attempt >= retry) {
                    log.error(" " + this.getClass().getSimpleName() + ":persist, full stack trace follows:", lte);
                    entity.setAttempt(0);
                    throw lte;
                } else {
                    log.error("Record locked version of " + entity.getClass().getSimpleName() + " id: " + entity.getId()
                            + " already persisted. Persist attempt " + entity.getAttempt() + " of " + retry);
                    //               entity.setRevision(0);
                    persist(false, entity);

                }

            } catch (Exception | Error ex) {
                if (ex.getCause() != null && (ex.getCause() instanceof TransientPropertyValueException || ex.getCause() instanceof IllegalStateException)) {
                    //.setVersion(entity.getVersion() + 1);
                    attempt = entity.getAttempt() + 1;
                    entity.setAttempt(attempt);
                    //  entity.setStartTime(entity.getDelay() * 2);

                    if (attempt >= retry) {
                        //log.error(" " + this.getClass().getSimpleName() + ":persist, Parent object not persisted for, attempting last ditch merge.");
                        log.error(" " + this.getClass().getSimpleName() + ":persist, full stack trace follows:", ex);

                        entity.setAttempt(0);
                        // merge(entities);

                        throw ex;
                    } else {
                        log.error("Parent object not persisted for  " + entity.getClass().getSimpleName() + " id: " + entity.getId() + ". Persist attempt "
                                + entity.getAttempt() + " of " + retry);
                        //                   entity.setRevision(0);
                        persist(false, entity);
                        // attempt++;
                        // continue;
                    }
                } else if (ex.getCause() != null && ex.getCause().getCause() instanceof ConstraintViolationException) {
                    attempt = entity.getAttempt() + 1;
                    entity.setAttempt(attempt);
                    //    entity.setStartTime(entity.getDelay() * 2);
                    //   EntityBase dbEntity = entity.refresh();

                    //dbEntity = entity;

                    if (attempt >= retry) {
                        log.error(" " + this.getClass().getSimpleName() + ":persist, full stack trace follows:", ex);
                        entity.setAttempt(0);
                        throw ex;
                    } else {
                        log.debug(" " + this.getClass().getSimpleName() + " " + entity.getClass().getSimpleName() + ":" + entity.getId()
                                + " :persist, primary key for version " + entity.getVersion() + "  already present in db. Persist attempt "
                                + entity.getAttempt() + " of " + retry);

                        // entity.setAttempt(0);
                        //                      entity.setRevision(0);
                        merge(false, entity);
                    }

                } else if (ex.getCause() != null && ex.getCause() instanceof PersistentObjectException) {
                    attempt = entity.getAttempt() + 1;
                    entity.setAttempt(attempt);
                    // entity.setStartTime(entity.getDelay() * 2);
                    // entity.setStartTime(entity.getDelay() * 2);

                    //   update(entity);

                    if (attempt >= retry) {
                        log.error(" " + this.getClass().getSimpleName() + ":persist, full stack trace follows:", ex);
                        entity.setAttempt(0);
                        throw ex;
                    } else {
                        log.debug(" " + this.getClass().getSimpleName() + " " + entity.getClass().getSimpleName() + ":" + entity.getId()
                                + " :persist, presistent object expection, will attmpt to merge. Persist attempt " + entity.getAttempt() + " of " + retry);

                        //              for (EntityBase entity : entities)
                        // entity.setAttempt(0);
                        //                        entity.setAttempt(0);
                        //                     entity.setRevision(0);
                        merge(false, entities);
                    }

                } else if (ex.getCause() != null && ex.getCause() instanceof OptimisticLockException) {
                    attempt = entity.getAttempt() + 1;
                    entity.setAttempt(attempt);
                    //    entity.setStartTime(entity.getDelay() * 2);
                    EntityBase dbEntity = entity.refresh();

                    //dbEntity = entity;

                    if (attempt >= retry) {
                        log.error(" " + this.getClass().getSimpleName() + ":persist attempt:" + entity.getAttempt() + " of " + retry
                                + ", full stack trace follows:", ex);
                        entity.setAttempt(0);
                        throw ex;
                    } else {
                        log.debug(" " + this.getClass().getSimpleName() + " " + entity.getClass().getSimpleName() + ":" + entity.getId()
                                + " :persist, primary key for version " + entity.getVersion() + "  already present in db with version " + dbEntity.getVersion()
                                + ". Persist attempt " + entity.getAttempt() + " of " + retry);

                    }

                    //for (EntityBase entity : entities)
                    //    merge(entities);
                }

                else {
                    log.error(" " + this.getClass().getSimpleName() + ":persist, full stack trace follows:", ex);

                    entity.setAttempt(0);
                    throw ex;
                }
                //break;

            } finally {
                if (persisted) {
                    entity.setAttempt(0);

                    log.debug(" " + this.getClass().getSimpleName() + ":persist. Succefully persisted " + entity.getClass().getSimpleName() + " " + entity);
                }

            }
            // break;
            // }
        }
    }

    //   unitOfWork.end();

    //     break;
    //   }

    //  @Override
    @Override
    public void persist(EntityBase... entities) {
        persist(true, entities);

    }

    private void persist(boolean increment, EntityBase... entities) {

        try {
            for (EntityBase entity : entities) {
                //    entity.getDao().persistEntities(entity);
                if (entity != null) {
                    entity.setPeristanceAction(PersistanceAction.NEW);
                    if (increment)
                        entity.setRevision(entity.getRevision() + 1);
                    application.getInsertQueue().put(entity);
                    //  } else {
                    //    application.getInsertQueue().addFirst(entity);
                    //}
                    log.debug("persisting " + entity.getClass().getSimpleName() + " id:" + entity.getId());
                }
            }

        } catch (Error | Exception e) {
            log.error("Unable to resubmit insert request in " + this.getClass().getSimpleName() + "insert, full stack trace follows:", e);
            //  e.printStackTrace();

        } finally {

        }

    }

    @Override
    public void delete(EntityBase... entities) {
        delete(true, entities);

    }

    private void delete(boolean increment, EntityBase... entities) {

        try {
            for (EntityBase entity : entities) {
                //    entity.getDao().persistEntities(entity);
                if (entity != null) {
                    entity.setPeristanceAction(PersistanceAction.DELETE);
                    if (increment)
                        entity.setRevision(entity.getRevision() + 1);
                    entity.setStartTime(entity.getDelay());

                    application.getInsertQueue().put(entity);
                    log.debug("deleting " + entity.getClass().getSimpleName() + " id:" + entity.getId());
                }
            }

        } catch (Error | Exception e) {
            log.error("Unable to resubmit delete request in " + this.getClass().getSimpleName() + "delete, full stack trace follows:", e);
            //  e.printStackTrace();

        } finally {

        }

    }

    /*    public class persistRunnable implements Runnable {

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

        }*/

    /*    public class mergeRunnable implements Runnable {

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
    */
    //  @Nullable
    // @ManyToOne(optional = true)
    //, cascade = { CascadeType.PERSIST, CascadeType.MERGE })
    //cascade = { CascadeType.ALL })
    // @JoinColumn(name = "position")
    //    @Override
    //    public BlockingQueue<EntityBase[]> getInsertQueue() {
    //
    //        return insertQueue;
    //    }
    //
    //    @Override
    //    public BlockingQueue<EntityBase[]> getMergeQueue() {
    //
    //        return mergeQueue;
    //    }

    @Override
    public void merge(EntityBase... entities) {
        merge(true, entities);
    }

    // @Override
    private void merge(boolean increment, EntityBase... entities) {

        try {
            for (EntityBase entity : entities) {
                if (entity != null) {
                    entity.setPeristanceAction(PersistanceAction.MERGE);
                    if (increment)
                        entity.setRevision(entity.getRevision() + 1);
                    //    entity.getDao().mergeEntities(entity);
                    application.getInsertQueue().put(entity);

                    // }

                    // else {
                    //   application.getInsertQueue().addFirst(entity);
                    //}

                    log.debug("merging " + entity.getClass().getSimpleName() + " id:" + entity.getId());
                }
            }
        } catch (Error | Exception e) {
            log.error("Unable to resubmit merge request in " + this.getClass().getSimpleName() + " merge, full stack trace follows:", e);
            // e.printStackTrace();

        } finally {

        }

    }

    @Transactional
    public void insert(EntityBase entity) {
        // try {
        //entityManager.get().lock(entity, LockModeType.PESSIMISTIC_WRITE);
        entityManager.get().persist(entity);
        //entityManager.get().flush();
        //} catch (Error | Exception ex) {
        //   log.error("Unable to perform request in " + this.getClass().getSimpleName() + ":getReference, full stack trace follows:", ex);

        //  throw ex;
        // }

        // entityManager.get().getTransaction().commit();
        // entityManager.get().flush();
        // TODO Auto-generated method stub

    }

    @Transactional
    public EntityBase restore(EntityBase entity) {
        EntityBase localEntity = null;
        //  try {
        // localEntity = entity;
        Object dBEntity;
        if (!entityManager.get().contains(entity))
            //  try {
            localEntity = entityManager.get().find(entity.getClass(), entity.getId());

        if (localEntity != null)
            entityManager.get().merge(entity);
        // localEntity = entityManager.get().merge(entity);

        //return null;
        // localEntity = entityManager.get().find(entity.getClass(), entity.getId());

        // entityManager.get().refresh(localEntity);
        if (localEntity != null)
            return localEntity;
        else
            return entity;

        //} catch (Error | Exception ex) {
        //  throw ex;
        // }

        // TODO Auto-generated method stub

    }

    @Transactional
    public void update(EntityBase entity) {
        // try {
        // entityManager.get().lock(entity, LockModeType.PESSIMISTIC_WRITE);
        entityManager.get().merge(entity);
        // } catch (Error | Exception ex) {
        //   throw ex;
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
    public void deleteEntities(EntityBase... entities) {
        int attempt = 0;
        boolean deleted = false;

        try {
            for (EntityBase entity : entities) {
                long revision = entity.findRevisionById();
                if (entity.getRevision() >= revision) {
                    attempt = entity.getAttempt();
                    remove(entity);
                    deleted = true;
                }

            }
        } catch (EntityNotFoundException | LockTimeoutException enf) {
            for (EntityBase entity : entities) {
                attempt = entity.getAttempt() + 1;
                entity.setAttempt(attempt);
                entity.merge();
                log.error("Entity  " + entity.getClass().getSimpleName() + " id: " + entity.getId() + " not found in database to delete. Delete attempt "
                        + entity.getAttempt() + " of " + retry);
                if (attempt >= retry) {
                    // log.error(
                    //       " " + this.getClass().getSimpleName() + ":delete attempt:" + entity.getAttempt() + " of " + retry + ", full stack trace follows:",
                    //      enf);
                    entity.setAttempt(0);
                    //throw enf;
                } else {
                    entity.setStartTime(entity.getDelay());

                    delete(false, entity);
                }
            }

        } catch (OptimisticLockException | StaleObjectStateException ole) {
            //     unitOfWork.end();
            for (EntityBase entity : entities) {
                attempt = entity.getAttempt() + 1;
                entity.setAttempt(attempt);

                if (attempt >= retry) {
                    log.error(
                            " " + this.getClass().getSimpleName() + ":delete attempt:" + entity.getAttempt() + " of " + retry + ", full stack trace follows:",
                            ole);

                    entity.setAttempt(0);
                    throw ole;
                }
            }
            for (EntityBase entity : entities) {
                entity.setVersion(entity.getVersion() + 1);
                entity.setStartTime(entity.getDelay());

                log.error("Later version of " + entity.getClass().getSimpleName() + " id: " + entity.getId() + " already in database. Delete attempt "
                        + entity.getAttempt() + " of " + retry);
            }

            delete(false, entities);

        } catch (Exception | Error ex) {

            /*            if (ex.getMessage().equals("Entity not managed")) {
                            for (EntityBase entity : entities)
                                try {
                                    //entityManager.get().persist(entity);
                                    // restore(entity);
                                    update(entity);
                                    remove(entity);
                                    // entityManager.get().refresh(entity);
                                    //entityManager.get().remove(entity);
                                } catch (Exception | Error ex1) {
                                    throw ex1;
                                }

                            //  entity.refresh();
                            delete(entities);
                        }*/
            if (ex.getCause() != null && ex.getCause().getCause() instanceof TransientPropertyValueException) {
                for (EntityBase entity : entities) {
                    // entity.setVersion(entity.getVersion() + 1);
                    attempt = entity.getAttempt() + 1;
                    entity.setAttempt(attempt);

                    log.error("Parent object not persisted for  " + entity.getClass().getSimpleName() + " id: " + entity.getId() + ". Delete attempt "
                            + entity.getAttempt() + " of " + retry);
                }
                if (attempt >= retry) {
                    log.error(" " + this.getClass().getSimpleName() + ":delete, full stack trace follows:", ex);
                    for (EntityBase entity : entities) {

                        entity.setAttempt(0);
                    }
                    throw ex;
                } else {
                    for (EntityBase entity : entities) {
                        // entity.setAttempt(entity.getAttempt() + 1);
                        entity.setStartTime(entity.getDelay());
                    }

                    delete(false, entities);
                    // attempt++;
                    //continue;
                }
            } else if (ex.getCause() != null && ex.getCause().getCause() instanceof ConstraintViolationException) {
                for (EntityBase entity : entities) {
                    attempt = entity.getAttempt() + 1;
                    entity.setAttempt(attempt);

                    log.debug(" " + this.getClass().getSimpleName() + " " + entity.getClass().getSimpleName() + ":" + entity.getId()
                            + " :delete, duplicate primary key already in db. Delete attempt " + entity.getAttempt() + " of " + retry);

                }
                if (attempt >= retry) {
                    log.error(" " + this.getClass().getSimpleName() + ":merge, full stack trace follows:", ex);
                    for (EntityBase entity : entities) {
                        entity.setAttempt(0);

                    }
                    throw ex;
                } else {
                    for (EntityBase entity : entities) {
                        // entity.setAttempt(entity.getAttempt() + 1);
                        entity.setStartTime(entity.getDelay());
                    }

                    delete(false, entities);
                }

            } else if (ex.getCause() != null && ex.getCause() instanceof PropertyAccessException) {
                for (EntityBase entity : entities) {
                    attempt = entity.getAttempt() + 1;
                    entity.setAttempt(attempt);

                    log.debug(" " + this.getClass().getSimpleName() + " " + entity.getClass().getSimpleName() + ":" + entity.getId()
                            + " :delete issue with accessing proerpties, retrying delete. Delete attempt " + entity.getAttempt() + " of " + retry);

                    // entity.setStartTime(entity.getDelay() * 2);
                }
                if (attempt >= retry) {
                    log.error(" " + this.getClass().getSimpleName() + ":merge, full stack trace follows:", ex);
                    for (EntityBase entity : entities) {

                        entity.setAttempt(0);
                    }
                    throw ex;
                } else {
                    for (EntityBase entity : entities) {
                        // entity.setAttempt(entity.getAttempt() + 1);
                        entity.setStartTime(entity.getDelay());
                    }
                    delete(false, entities);
                }
            } else if (ex.getCause() != null && ex.getCause() instanceof OptimisticLockException) {
                for (EntityBase entity : entities) {
                    attempt = entity.getAttempt() + 1;
                    entity.setAttempt(attempt);
                    if (attempt >= retry) {
                        log.error(" " + this.getClass().getSimpleName() + ":delete attempt:" + entity.getAttempt() + " of " + retry
                                + ", full stack trace follows:", ex);

                        entity.setAttempt(0);
                        throw ex;
                    }
                }
                for (EntityBase entity : entities) {

                    entity.setVersion(entity.getVersion() + 1);

                    entity.setStartTime(entity.getDelay());

                    log.error("Later version of " + entity.getClass().getSimpleName() + " id: " + entity.getId() + " already in database. Delete attempt "
                            + entity.getAttempt() + " of " + retry);
                }

                //for (EntityBase entity : entities)
                delete(false, entities);
            } else {
                log.error(" " + this.getClass().getSimpleName() + ":delete, full stack trace follows:", ex);
                for (EntityBase entity : entities) {

                    entity.setAttempt(0);
                }
                throw ex;
            }

            //     unitOfWork.end();

        } finally {
            if (deleted)
                for (EntityBase entity : entities) {
                    entity.setAttempt(0);
                    log.debug(this.getClass().getSimpleName() + ":delete. Succefully deleted " + entity.getClass().getSimpleName() + " " + entity);
                }

        }
        // break;
        // }
        //  break;

        // ex.printStackTrace();

    }

    @Override
    public void mergeEntities(EntityBase... entities) {

        int attempt = 0;
        boolean merged = false;
        try {
            for (EntityBase entity : entities) {
                long revision = entity.findRevisionById();
                if (entity.getRevision() >= revision) {
                    update(entity);
                    merged = true;
                }

            }
        } catch (EntityNotFoundException | IllegalArgumentException | LockTimeoutException enf) {
            for (EntityBase entity : entities) {
                attempt = entity.getAttempt() + 1;
                entity.setAttempt(attempt);
                //  entity.setStartTime(entity.getDelay() * 2);f

                log.error(this.getClass().getSimpleName() + "Entity  " + entity.getClass().getSimpleName() + " id: " + entity.getId()
                        + " not found in database, persisting. Merge attempt " + entity.getAttempt() + " of " + retry);
                if (attempt >= retry) {
                    log.error(this.getClass().getSimpleName() + ":merge attempt:" + entity.getAttempt() + " of " + retry + ", full stack trace follows:", enf);
                    entity.setAttempt(0);
                    throw enf;
                } else {
                    //try {
                    //    restore(entity);
                    // } catch (Exception | Error ex1) {
                    //     log.error(" " + this.getClass().getSimpleName() + ":merge attempt:" + entity.getAttempt() + " of " + retry
                    //           + ", unable to restore entity " + entity.getClass().getSimpleName() + " id " + entity.getId());
                    // }
                    // entity.refresh();

                    //  entity.setAttempt(0);
                    // well it may be in db put not in persistance context
                    //entity.setRevision(0);
                    persist(false, entity);
                }
            }
        } catch (OptimisticLockException | StaleObjectStateException ole) {
            //     unitOfWork.end();
            for (EntityBase entity : entities) {
                attempt = entity.getAttempt() + 1;
                entity.setAttempt(attempt);
                if (attempt >= retry) {
                    log.error(" " + this.getClass().getSimpleName() + ":merge attempt:" + entity.getAttempt() + " of " + retry + ", full stack trace follows:",
                            ole);
                    entity.setAttempt(0);
                    throw ole;
                } else {
                    //}
                    //for (EntityBase entity : entities) {
                    entity.setVersion(entity.getVersion() + 1);

                    //  entity.setStartTime(entity.getDelay() * 2);

                    log.error("Later version of " + entity.getClass().getSimpleName() + " id: " + entity.getId() + " already merged. Merge attempt "
                            + entity.getAttempt() + " of " + retry);
                    //  entity.setRevision(0);
                    merge(false, entities);
                }
            }

        } catch (Exception | Error ex) {
            if (ex.getCause() != null && ex.getCause().getCause() instanceof TransientPropertyValueException) {
                for (EntityBase entity : entities) {
                    // entity.setVersion(entity.getVersion() + 1);
                    attempt = entity.getAttempt() + 1;
                    entity.setAttempt(attempt);

                    log.error("Parent object not persisted for  " + entity.getClass().getSimpleName() + " id: " + entity.getId() + ". Merge attempt "
                            + entity.getAttempt() + " of " + retry);

                    if (attempt >= retry) {
                        log.error(" " + this.getClass().getSimpleName() + ":merge, full stack trace follows:", ex);
                        entity.setAttempt(0);
                        throw ex;
                    } else {

                        // entity.setRevision(0);
                        merge(false, entities);

                    }
                }
            } else if (ex.getCause() != null && ex.getCause().getCause() instanceof ConstraintViolationException) {
                for (EntityBase entity : entities) {
                    attempt = entity.getAttempt() + 1;
                    entity.setAttempt(attempt);

                    log.debug(" " + this.getClass().getSimpleName() + " " + entity.getClass().getSimpleName() + ":" + entity.getId()
                            + " :merge, duplicate primary key already in db. Persist attempt " + entity.getAttempt() + " of " + retry);

                    if (attempt >= retry) {
                        log.error(" " + this.getClass().getSimpleName() + ":merge, full stack trace follows:", ex);
                        entity.setAttempt(0);

                        throw ex;
                    } else {
                        //      entity.setRevision(0);
                        merge(false, entities);
                    }
                }

            } else if (ex.getCause() != null && ex.getCause() instanceof PropertyAccessException) {
                for (EntityBase entity : entities) {
                    attempt = entity.getAttempt() + 1;
                    entity.setAttempt(attempt);

                    log.debug(" " + this.getClass().getSimpleName() + " " + entity.getClass().getSimpleName() + ":" + entity.getId()
                            + " :merge, issue with accessing proerpties, retrying merge. Persist attempt " + entity.getAttempt() + " of " + retry);

                    if (attempt >= retry) {
                        log.error(" " + this.getClass().getSimpleName() + ":merge, full stack trace follows:", ex);
                        entity.setAttempt(0);
                        throw ex;
                    } else {
                        //     entity.setRevision(0);
                        merge(false, entities);
                    }
                }
            } else if (ex.getCause() != null && ex.getCause() instanceof OptimisticLockException) {
                for (EntityBase entity : entities) {
                    attempt = entity.getAttempt() + 1;
                    entity.setAttempt(attempt);
                    if (attempt >= retry) {
                        log.error(" " + this.getClass().getSimpleName() + ":merge attempt:" + entity.getAttempt() + " of " + retry
                                + ", full stack trace follows:", ex);
                        entity.setAttempt(0);
                        throw ex;
                    } else {
                        // }
                        // for (EntityBase entity : entities) {
                        entity.setVersion(entity.getVersion() + 1);

                        // entity.setStartTime(entity.getDelay() * 2);

                        log.error("Later version of " + entity.getClass().getSimpleName() + " id: " + entity.getId() + " already merged. Merge attempt "
                                + entity.getAttempt() + " of " + retry);
                        //           entity.setRevision(0);
                        merge(false, entities);
                    }
                }
            } else {
                log.error(" " + this.getClass().getSimpleName() + ":merge, full stack trace follows:", ex);
                for (EntityBase entity : entities)
                    entity.setAttempt(0);
                throw ex;
            }

            //     unitOfWork.end();

        } finally {
            if (merged)
                for (EntityBase entity : entities) {

                    entity.setAttempt(0);
                    log.debug(" " + this.getClass().getSimpleName() + ":merge. Succefully merged " + entity.getClass().getSimpleName() + " " + entity);
                }

        }

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

    @Transactional
    public void remove(EntityBase entity) {

        // if (!em.contains(entity)) {
        //   System.out.println("delete() entity not managed: " + entity);
        // utx.begin();

        EntityBase target = entity;
        //  em.remove(target);
        //    utx.commit();

        //  EntityBase localEntity;
        if (!entityManager.get().contains(entity))
            target = entityManager.get().merge(entity);

        ///  entityManager.get().refresh(entity);

        //   if (localEntity != null)
        entityManager.get().remove(target);
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

    @Override
    public <T> int findRevisionById(Class<T> resultType, UUID id) throws NoResultException {
        try {
            return queryOne(Integer.class, "select revision from " + resultType.getSimpleName() + " x where x.id = ?1", id);
        } catch (Exception | Error ex) {
            //log.error("Unable to perform request in " + this.getClass().getSimpleName() + ":findById, full stack trace follows:", ex);
            throw ex;
            //break;

        }
    }

}
