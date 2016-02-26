package org.cryptocoinpartners.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.persistence.EntityGraph;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.OptimisticLockException;
import javax.persistence.Query;
import javax.persistence.TypedQuery;

import org.cryptocoinpartners.schema.EntityBase;
import org.hibernate.TransientObjectException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.persist.Transactional;
import com.google.inject.persist.UnitOfWork;

/**
 * <p>Because DI is not always the right idea.</p>
 *
 * <p>Conventional wisdom is to inject the EntityManager into classes where you might need it. This
 * breaks down when you need to perform database access in places where injection is not available.
 * For example, you may have a polymorphic hierarchy of entity objects that exhibit different data-access
 * behavior.</p>
 *
 * <p>Aspects like persistence (aka transaction) and authorization really fit a thread-local model better.
 * This static accessor for the EntityManager gives you "always-available" access to the context.</p>
 */
public class EM {
    @Inject
    // @PersistenceContext
    static Provider<EntityManager> entityManagerProvider;
    private static final int defaultBatchSize = 20;
    protected static Logger log = LoggerFactory.getLogger("org.cryptocoinpartners.staticEntityManager");
    @Inject
    protected static UnitOfWork unitOfWork;

    private static EntityManager em() {
        return entityManagerProvider.get();
    }

    public static EntityManager getEnityManager() {
        try {
            beginUnitOfWork();
            return entityManagerProvider.get();
        } finally {
            unitOfWork.end();
        }
    }

    public static void queryEach(Visitor<Object[]> handler, String queryStr, Object... params) {
        queryEach(handler, defaultBatchSize, queryStr, params);
    }

    @SuppressWarnings("ConstantConditions")
    public static void queryEach(Visitor<Object[]> handler, int batchSize, String queryStr, Object... params) {
        EntityManager em = null;
        try {
            beginUnitOfWork();
            log.trace("namedQueryZeroOne unit of work ended for thread: " + Thread.currentThread());

            final Query query = em().createQuery(queryStr);
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
        } finally {
            unitOfWork.end();
            log.trace("namedQueryZeroOne unit of work ended for thread: " + Thread.currentThread());

        }
    }

    // @Transactional
    public static <T> T namedQueryZeroOne(Class<T> resultType, String namedQuery, Object... params) {
        try {
            beginUnitOfWork();
            log.trace("namedQueryZeroOne unit of work ended for thread: " + Thread.currentThread());

            TypedQuery<T> query = em().createNamedQuery(namedQuery, resultType);
            if (params != null) {
                for (int i = 0; i < params.length; i++) {
                    Object param = params[i];
                    query.setParameter(i + 1, param); // JPA uses 1-based indexes
                }

            }
            return query.setHint("org.hibernate.cacheable", true).getSingleResult();
        } catch (NoResultException x) {
            return null;
        } finally {
            unitOfWork.end();
            log.trace("namedQueryZeroOne unit of work ended for thread: " + Thread.currentThread());

            //  if (em() != null)
            //    em().close();
        }
    }

    public static <T> T namedQueryZeroOne(Class<T> resultType, String namedQuery, Map<String, String> properties, Object... params) {
        try {
            beginUnitOfWork();
            log.trace("namedQueryZeroOne unit of work ended for thread: " + Thread.currentThread());

            TypedQuery<T> query = em().createNamedQuery(namedQuery, resultType);
            for (Map.Entry<String, String> entry : properties.entrySet()) {
                EntityGraph graph = em().getEntityGraph(entry.getValue());

                query.setHint(entry.getKey(), graph);
            }
            if (params != null) {
                for (int i = 0; i < params.length; i++) {
                    Object param = params[i];
                    query.setParameter(i + 1, param); // JPA uses 1-based indexes
                }

            }
            return query.setHint("org.hibernate.cacheable", false).getSingleResult();
        } catch (NoResultException x) {
            return null;
        } finally {
            unitOfWork.end();
            log.trace("namedQueryZeroOne unit of work ended for thread: " + Thread.currentThread());

            //  if (em() != null)
            //    em().close();
        }
    }

    // @Transactional

    public static EntityBase find(Map<String, String> properties, EntityBase... entities) {
        boolean found = true;
        EntityBase foundEntity = null;

        try {
            beginUnitOfWork();

            for (EntityBase entity : entities)
                if (!properties.isEmpty()) {
                    Map hints = new HashMap();
                    for (Map.Entry<String, String> entry : properties.entrySet()) {

                        EntityGraph graph = em().getEntityGraph(entry.getValue());
                        hints.put(entry.getKey(), graph);

                        //query.setHint(entry.getKey(), graph);
                    }
                    foundEntity = em().find(entity.getClass(), entity.getId(), hints);
                } else
                    foundEntity = em().find(entity.getClass(), entity.getId());
            if (foundEntity != null)
                for (EntityBase entity : entities)
                    log.debug(entity.getClass().getSimpleName() + ": " + entity.getId().toString() + " found in database");

            else
                for (EntityBase entity : entities)
                    log.error(entity.getClass().getSimpleName() + ": " + entity.getId().toString() + " not found in database");
            return foundEntity;

        } catch (Exception | Error e) {
            found = false;
            log.error("Threw a Execpton or Error in  org.cryptocoinpartners.util.persistUtil::insert, full stack trace follows:", e);

            throw e;
            //if (PersistUtilHelper.isActive())
            //  PersistUtilHelper.rollback();
        } finally {
            unitOfWork.end();
        }
        //  if (em() != null)
        //   em().close();

    }

    public static <T> T find(Class<T> resultType, UUID id, Map<String, String> properties) {
        boolean found = true;
        T foundEntity = null;

        try {
            beginUnitOfWork();

            if (!properties.isEmpty()) {
                Map hints = new HashMap();
                for (Map.Entry<String, String> entry : properties.entrySet()) {

                    EntityGraph graph = em().getEntityGraph(entry.getValue());
                    hints.put(entry.getKey(), graph);

                    //query.setHint(entry.getKey(), graph);
                }
                foundEntity = (T) em().find(resultType, id, hints);
            } else
                foundEntity = em().find(resultType, id);
            if (foundEntity != null)
                log.debug(id + " " + resultType.getClass().getSimpleName() + ":  found in database");

            else

                log.error(id + " " + resultType.getClass().getSimpleName() + ": not found in database");
            return foundEntity;

        } catch (Exception | Error e) {
            found = false;
            log.error("Threw a Execpton or Error in  org.cryptocoinpartners.util.persistUtil::insert, full stack trace follows:", e);

            throw e;
            //if (PersistUtilHelper.isActive())
            //  PersistUtilHelper.rollback();
        } finally {
            unitOfWork.end();
        }
        //  if (em() != null)
        //   em().close();

    }

    public static EntityBase find(EntityBase... entities) {
        boolean found = true;
        EntityBase foundEntity = null;

        try {
            beginUnitOfWork();

            for (EntityBase entity : entities)

                foundEntity = em().find(entity.getClass(), entity.getId());
            if (foundEntity != null)
                for (EntityBase entity : entities)
                    log.debug(entity.getClass().getSimpleName() + ": " + entity.getId().toString() + " found in database");

            else
                for (EntityBase entity : entities)
                    log.error(entity.getClass().getSimpleName() + ": " + entity.getId().toString() + " not found in database");
            return foundEntity;

        } catch (Exception | Error e) {
            found = false;
            log.error("Threw a Execpton or Error in  org.cryptocoinpartners.util.persistUtil::insert, full stack trace follows:", e);

            throw e;
            //if (PersistUtilHelper.isActive())
            //  PersistUtilHelper.rollback();
        } finally {
            unitOfWork.end();
        }
        //  if (em() != null)
        //   em().close();

    }

    // @Transactional
    public static <T> T queryZeroOne(Class<T> resultType, String queryStr, Object... params) {
        try {
            beginUnitOfWork();

            final TypedQuery<T> query = em().createQuery(queryStr, resultType);
            if (params != null) {
                for (int i = 0; i < params.length; i++) {
                    Object param = params[i];
                    query.setParameter(i + 1, param); // JPA uses 1-based indexes
                }
            }

            return query.getSingleResult();

        } catch (NoResultException x) {
            return null;
        } finally {
            unitOfWork.end();
            //         if (em != null)
            //           em.close();
        }
    }

    // @Transactional
    public static <T> T queryOne(Class<T> resultType, String queryStr, Map<String, String> properties, Object... params) {
        //   EntityManager em = em();
        //  try {

        //  try {
        return sqlQueryOne(resultType, queryStr, params);

        //     PersistUtil.queryOne(Portfolio.class, queryStr, portfolioName);

    }

    public static <T> T queryOne(Class<T> resultType, String queryStr, Object... params) {
        //   EntityManager em = em();
        //  try {

        //  try {
        return sqlQueryOne(resultType, queryStr, params);

        //     PersistUtil.queryOne(Portfolio.class, queryStr, portfolioName);

    }

    // @Transactional
    public static <T> T sqlQueryOne(Class<T> resultType, String queryStr, Object... params) throws NoResultException {
        //   EntityManager em = em();
        T result = null;
        try {
            beginUnitOfWork();
            log.debug("sqlQueryOne unit of work started for thread: " + Thread.currentThread());
            TypedQuery<T> query = em().createQuery(queryStr, resultType);
            if (params != null) {
                for (int i = 0; i < params.length; i++) {
                    Object param = params[i];
                    query.setParameter(i + 1, param); // JPA uses 1-based indexes
                }
            }
            result = query.getSingleResult();
            return result;
        } catch (NoResultException e) {
            //  context.getInjector().getInstance(Portfolio.class);
            // PersistUtil.insert(portfolio);
            throw e;
            //return null;

        } finally {
            unitOfWork.end();
            log.trace("sqlQueryOne unit of work ended for thread: " + Thread.currentThread());

        }
        // em.flush();
        //   if (em != null)
        //em.close();
        // }
    }

    public static <T> T sqlQueryOne(Class<T> resultType, String queryStr, Map<String, String> properties, Object... params) throws NoResultException {
        //   EntityManager em = em();
        T result = null;
        try {
            beginUnitOfWork();
            log.debug("sqlQueryOne unit of work started for thread: " + Thread.currentThread());
            TypedQuery<T> query = em().createQuery(queryStr, resultType);
            for (Map.Entry<String, String> entry : properties.entrySet()) {
                EntityGraph graph = em().getEntityGraph(entry.getValue());

                query.setHint(entry.getKey(), graph);
            }
            if (params != null) {
                for (int i = 0; i < params.length; i++) {
                    Object param = params[i];
                    query.setParameter(i + 1, param); // JPA uses 1-based indexes
                }
            }
            result = query.getSingleResult();
            return result;
        } catch (NoResultException e) {
            //  context.getInjector().getInstance(Portfolio.class);
            // PersistUtil.insert(portfolio);
            throw e;
            //return null;

        } finally {
            unitOfWork.end();
            log.trace("sqlQueryOne unit of work ended for thread: " + Thread.currentThread());

        }
        // em.flush();
        //   if (em != null)
        //em.close();
        // }
    }

    //@Transactional
    public static <T> List<T> namedQueryList(Class<T> resultType, String namedQuery, Object... params) {
        try {
            beginUnitOfWork();
            log.trace("namedQueryList unit of work ended for thread: " + Thread.currentThread());

            TypedQuery<T> query = em().createNamedQuery(namedQuery, resultType);

            if (params != null) {
                for (int i = 0; i < params.length; i++) {
                    Object param = params[i];
                    query.setParameter(i + 1, param); // JPA uses 1-based indexes
                }

            }
            return query.setHint("org.hibernate.cacheable", true).getResultList();
        } catch (NoResultException x) {
            return null;
        } finally {
            unitOfWork.end();
            log.trace("namedQueryList unit of work ended for thread: " + Thread.currentThread());

            //  if (em() != null)
            //    em().close();
        }
    }

    public static <T> List<T> namedQueryList(Class<T> resultType, String namedQuery, Map<String, String> properties, Object... params) {
        try {
            beginUnitOfWork();
            log.trace("namedQueryList unit of work ended for thread: " + Thread.currentThread());

            TypedQuery<T> query = em().createNamedQuery(namedQuery, resultType);
            for (Map.Entry<String, String> entry : properties.entrySet()) {
                EntityGraph graph = em().getEntityGraph(entry.getValue());

                query.setHint(entry.getKey(), graph);
            }
            if (params != null) {
                for (int i = 0; i < params.length; i++) {
                    Object param = params[i];
                    query.setParameter(i + 1, param); // JPA uses 1-based indexes
                }

            }
            return query.setHint("org.hibernate.cacheable", true).getResultList();
        } catch (NoResultException x) {
            return null;
        } finally {
            unitOfWork.end();
            log.trace("namedQueryList unit of work ended for thread: " + Thread.currentThread());

            //  if (em() != null)
            //    em().close();
        }
    }

    public static <T> List<T> queryList(Class<T> resultType, String queryStr, Object... params) {
        //  EntityManager em = em();
        try {
            beginUnitOfWork();
            final TypedQuery<T> query = em().createQuery(queryStr, resultType);

            if (params != null) {
                for (int i = 0; i < params.length; i++) {
                    Object param = params[i];
                    query.setParameter(i + 1, param); // JPA uses 1-based indexes
                }
            }
            return query.getResultList();
        } catch (TransientObjectException toe) {
            log.debug("what happened");
            return null;
        } finally {
            unitOfWork.end();
            // em.clear();
            //   if (em() != null)
            //  em().close();
        }
    }

    public static <T> List<T> queryList(Class<T> resultType, String queryStr, Map<String, String> properties, Object... params) {
        //  EntityManager em = em();
        try {
            beginUnitOfWork();
            final TypedQuery<T> query = em().createQuery(queryStr, resultType);
            for (Map.Entry<String, String> entry : properties.entrySet()) {
                EntityGraph graph = em().getEntityGraph(entry.getValue());

                query.setHint(entry.getKey(), graph);
            }

            // EntityGraph graph = EM.getEnityManager().getEntityGraph("graph.Position.fills");

            if (params != null) {
                for (int i = 0; i < params.length; i++) {
                    Object param = params[i];
                    query.setParameter(i + 1, param); // JPA uses 1-based indexes
                }
            }
            return query.getResultList();
        } catch (TransientObjectException toe) {
            log.debug("what happened");
            return null;
        } finally {
            unitOfWork.end();
            // em.clear();
            //   if (em() != null)
            //  em().close();
        }
    }

    // @Transactional
    public static void insert(EntityBase entity) {
        //  EntityManager em = entityManager.get();
        //    unitOfWork.begin();
        try {
            beginUnitOfWork();

            em().persist(entity);
        } catch (Exception | Error ex) {
            throw ex;
        } finally {

            unitOfWork.end();
        }
        //  em().persist(entity);
        //em().flush();
        //unitOfWork.end();
        // TODO Auto-generated method stub

    }

    @Transactional
    private static void update(EntityBase entity) {
        // EntityManager em = entityManager.get();

        em().merge(entity);
        // TODO Auto-generated method stub

    }

    //  @Transactional
    private static void evict(EntityBase entity) {
        // EntityManager em = entityManager.get();
        beginUnitOfWork();
        em().detach(entity);
        unitOfWork.end();
        // TODO Auto-generated method stub

    }

    //@Transactional
    // @Transactional
    public static void persist(EntityBase... entities) {
        // unitOfWork.begin();
        for (EntityBase entity : entities) {
            // unitOfWork.begin();
            try {

                insert(entity);
            }
            //entityManager.get().flush();

            //entityManager.get().getTransaction().commit();
            // log.debug("persisting entity " + entity.getClass().getSimpleName() + " " + entity);
            //   entityManager.get().getTransaction().commit();
            //  } catch (OptimisticLockException ole) {
            //      log.error("Unable to merge record" + ole);
            //   }

            catch (Exception | Error ex) {

                System.out.println("Unable to perform request in " + EM.class.getSimpleName() + ":persist, full stack trace follows:" + ex);
                //       ex.printStackTrace();

                //   } finally {
                //  unitOfWork.end();
                // }
            }
        }

    }

    //  @Transactional
    public static void merge(EntityBase... entities) {
        for (EntityBase entity : entities)
            try {

                update(entity);
                //  em().flush();
                //entityManager.get().flush();

                //entityManager.get().getTransaction().commit();
                // log.debug("persisting entity " + entity.getClass().getSimpleName() + " " + entity);
                //   entityManager.get().getTransaction().commit();
            } catch (OptimisticLockException ole) {
                log.error("Unable to merge record" + ole);
            }

            catch (Exception | Error ex) {

                System.out.println("Unable to perform request in " + EM.class.getSimpleName() + ":persist, full stack trace follows:" + ex);
                ex.printStackTrace();

            }

    }

    //  @Transactional
    // @com.google.inject.persist.Transactional
    public static void beginUnitOfWork() {
        //try {
        unitOfWork.begin();
        //} catch (IllegalStateException ex) {
        //  unitOfWork.end();
        //unitOfWork.begin();

        // }
    }

    public static void detach(EntityBase... entities) {
        for (EntityBase entity : entities)
            try {

                //evict(entity);
                //entityManager.get().flush();

                //entityManager.get().getTransaction().commit();
                // log.debug("persisting entity " + entity.getClass().getSimpleName() + " " + entity);
                //   entityManager.get().getTransaction().commit();
            } catch (OptimisticLockException ole) {
                log.error("Unable to detach record" + ole);
            }

            catch (Exception | Error ex) {

                System.out.println("Unable to perform request in " + EM.class.getSimpleName() + ":persist, full stack trace follows:" + ex);
                ex.printStackTrace();

            }

    }

    //   @Transactional
    public static <T> void queryEach(Class<T> resultType, Visitor<T> handler, String queryStr, Object... params) {
        queryEach(resultType, handler, defaultBatchSize, queryStr, params);
    }

    // @Transactional
    public static <T> T find(Class<T> resultType, UUID id) {
        try {
            beginUnitOfWork();
            return em().find(resultType, id);
        } finally {
            unitOfWork.end();
        }

    }

    // @Transactional
    public static <T> T namedQueryOne(Class<T> resultType, String namedQuery, Object... params) throws NoResultException {
        try {
            // unitOfWork.
            beginUnitOfWork();

            final TypedQuery<T> query = em().createNamedQuery(namedQuery, resultType);
            if (params != null) {
                for (int i = 0; i < params.length; i++) {
                    Object param = params[i];
                    query.setParameter(i + 1, param); // JPA uses 1-based indexes
                }
            }
            return query.getSingleResult();
        } catch (Exception | Error ex) {

            // System.out.println("Unable to perform request in " + EM.class.getSimpleName() + ":persist, full stack trace follows:" + ex);
            // ex.printStackTrace();
            throw ex;
            // return null;

        } finally {
            unitOfWork.end();
        }

    }

    //  @Transactional
    public static <T> void queryEach(Class<T> resultType, Visitor<T> handler, int batchSize, String queryStr, Object... params) {
        try {
            beginUnitOfWork();

            final TypedQuery<T> query = em().createQuery(queryStr, resultType);
            if (params != null) {
                for (int i = 0; i < params.length; i++) {
                    Object param = params[i];
                    query.setParameter(i + 1, param); // JPA uses 1-based indexes
                }
            }
            query.setMaxResults(batchSize);
            for (int start = 0;; start += batchSize) {
                query.setFirstResult(start);
                final List<T> list = query.getResultList();
                if (list.isEmpty())
                    return;
                for (T row : list) {
                    if (!handler.handleItem(row))
                        return;
                }
            }
        } finally {
            unitOfWork.end();
            // if (em() != null)
            //  em().close();

        }
    }
}
