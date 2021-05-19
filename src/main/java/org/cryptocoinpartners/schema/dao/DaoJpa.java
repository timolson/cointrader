package org.cryptocoinpartners.schema.dao;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.persistence.EntityExistsException;
import javax.persistence.EntityManager;
import javax.persistence.EntityNotFoundException;
import javax.persistence.NoResultException;
import javax.persistence.OptimisticLockException;
import javax.persistence.Query;
import javax.persistence.TypedQuery;

import org.cryptocoinpartners.enumeration.PersistanceAction;
import org.cryptocoinpartners.module.ApplicationInitializer;
import org.cryptocoinpartners.schema.Bar;
import org.cryptocoinpartners.schema.Book;
import org.cryptocoinpartners.schema.EntityBase;
import org.cryptocoinpartners.schema.Trade;
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

public abstract class DaoJpa implements Dao, java.io.Serializable {
  /** */
  private static final long serialVersionUID = -3999121207747846784L;

  protected static Logger log = LoggerFactory.getLogger("org.cryptocoinpartners.persist");
  private static final int batchSize = ConfigUtil.combined().getInt("db.batch_size", 1000);
  private static final boolean persistance = ConfigUtil.combined().getBoolean("db.persist", true);
  private static int retry;

  static {
    retry = ConfigUtil.combined().getInt("db.persist.retry");
  }

  @Inject protected transient Provider<EntityManager> entityManager;

  @Inject protected transient ApplicationInitializer application;

  @Inject private transient UnitOfWork unitOfWork;

  private static ArrayList<EntityBase> bulkInsertEntities = new ArrayList<EntityBase>();
  private static ArrayList<EntityBase> bulkMergeEntities = new ArrayList<EntityBase>();
  // protected EntityManager entityManager;

  public DaoJpa() {
    //  ParameterizedType genericSuperclass = (ParameterizedType) getClass().getGenericSuperclass();
    // this.entityClass = (Class) genericSuperclass.getActualTypeArguments()[1];
  }

  @Override
  public void queryEach(Visitor<Object[]> handler, String queryStr, Object... params) {
    try {
      queryEach(handler, batchSize, queryStr, params);
    } catch (Exception | Error ex) {

      log.error(
          "Unable to perform request in "
              + this.getClass().getSimpleName()
              + ":queryEach, full stack trace follows:",
          ex);
      throw ex;
    }
  }

  @Override
  @Transactional
  public void queryEach(
      Visitor<Object[]> handler, int batchSize, String queryStr, Object... params) {
    try {
      Query query = entityManager.get().createQuery(queryStr);
      if (params != null) {
        for (int i = 0; i < params.length; i++) {
          Object param = params[i];
          query.setParameter(i + 1, param); // JPA uses 1-based indexes
        }
      }
      query.setMaxResults(batchSize);
      for (int start = 0; ; start += batchSize) {
        query.setFirstResult(start);
        List list = query.getResultList();
        if (list.isEmpty()) return;
        for (Object row : list) {
          if (row.getClass().isArray() && !handler.handleItem((Object[]) row)
              || !row.getClass().isArray() && !handler.handleItem(new Object[] {row})) return;
        }
      }
    } catch (Exception | Error ex) {
      log.error(
          "Unable to perform request in "
              + this.getClass().getSimpleName()
              + ":queryEach, full stack trace follows:",
          ex);

      throw ex;

      // log.error("Unable to perform request in " + this.getClass().getSimpleName() + ":queryEach,
      // full stack trace follows:", ex);
      // ex.printStackTrace();

    }
  }

  @Override
  public <T> void queryEach(
      Class<T> resultType, Visitor<T> handler, String queryStr, Object... params) {
    queryEach(resultType, handler, batchSize, queryStr, params);
  }

  @Override
  @Transactional
  public <T> void queryEach(
      Class<T> resultType, Visitor<T> handler, int batchSize, String queryStr, Object... params) {
    try {
      TypedQuery<T> query = entityManager.get().createQuery(queryStr, resultType);
      if (params != null) {
        for (int i = 0; i < params.length; i++) {
          Object param = params[i];
          query.setParameter(i + 1, param); // JPA uses 1-based indexes
        }
      }
      query.setMaxResults(batchSize);
      for (int start = 0; ; start += batchSize) {
        query.setFirstResult(start);
        List<T> list = query.getResultList();
        if (list.isEmpty()) return;
        for (T row : list) {
          if (!handler.handleItem(row)) return;
        }
      }
    } catch (Exception | Error ex) {

      log.error(
          "Unable to perform request in "
              + this.getClass().getSimpleName()
              + ":queryEach, full stack trace follows:",
          ex);
      throw ex;
    }
  }

  @Override
  @Transactional
  public <T> T namedQueryOne(Class<T> resultType, String namedQuery, Object... params)
      throws NoResultException {
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

      log.error(
          "Unable to perform request in "
              + this.getClass().getSimpleName()
              + ":namedQueryOne, full stack trace follows:",
          ex);
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
      log.error(
          "Unable to perform request in "
              + this.getClass().getSimpleName()
              + ":queryZeroOne, full stack trace follows:",
          ex);

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
      log.error(
          "Unable to perform request in "
              + this.getClass().getSimpleName()
              + ":queryOne, full stack trace follows:",
          ex);

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
      log.error(
          "Unable to perform request in "
              + this.getClass().getSimpleName()
              + ":queryList, full stack trace follows:",
          ex);
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
        // if (existingEntity != null) {
        // entityManager.get().merge(entity);
        // entityManager.get().flush();
        ///    } else
        // update(entity);

        evict(entity);
        // entityManager.get().detach(entity);
        //    entityManager.get().detach(entity);

        // entityManager.get().flush();

        // log.debug("persisting entity " + entity.getClass().getSimpleName() + " " + entity);
        //   entityManager.get().getTransaction().commit();
      } catch (Exception | Error ex) {

        log.error(
            "Unable to perform request in "
                + this.getClass().getSimpleName()
                + ":detach, full stack trace follows:",
            ex);
        //
        throw ex;
        // ex.printStackTrace();

      }
  }

  @Override
  public EntityBase refresh(EntityBase... entities) {
    EntityBase localEntity = null;
    for (EntityBase entity : entities)
      try {

        // EntityBase existingEntity = entityManager.get().find(entity.getClass(), entity.getId());
        // if (existingEntity != null) {
        // entityManager.get().merge(entity);
        // entityManager.get().flush();
        ///    } else
        // update(entity);

        localEntity = restore(entity);
        // entityManager.get().detach(entity);
        //    entityManager.get().detach(entity);

        // entityManager.get().flush();

        // log.debug("persisting entity " + entity.getClass().getSimpleName() + " " + entity);
        //   entityManager.get().getTransaction().commit();
      } catch (Exception | Error ex) {

        log.error(
            "Unable to perform request in "
                + this.getClass().getSimpleName()
                + ":detach, full stack trace follows:",
            ex);
        //
        throw ex;
        // ex.printStackTrace();

      }

    return localEntity;
  }

  @Transactional
  @Override
  public <T> T find(Class<T> resultType, Long id) {
    try {
      return entityManager.get().find(resultType, id);
    } catch (Error | Exception ex) {
      log.error(
          "Unable to perform request in "
              + this.getClass().getSimpleName()
              + ":find, full stack trace follows:",
          ex);

      throw ex;
    }
  }

  @Override
  @Transactional
  public boolean contains(EntityBase entity) {
    try {
      return entityManager.get().contains(entity);
    } catch (Error | Exception ex) {
      log.error(
          "Unable to perform request in "
              + this.getClass().getSimpleName()
              + ":contains, full stack trace follows:",
          ex);

      throw ex;
    }
  }

  @Transactional
  public <T> T getReference(Class<T> resultType, UUID id) {
    try {
      return entityManager.get().getReference(resultType, id);
    } catch (Error | Exception ex) {
      log.error(
          "Unable to perform request in "
              + this.getClass().getSimpleName()
              + ":getReference, full stack trace follows:",
          ex);

      throw ex;
    }
  }

  // @Override
  // @Transactional
  // @com.google.inject.persist.Transactional
  //  @Transactional
  //  @Inject
  @Override
  public void persistEntities(boolean bulkInsert, EntityBase... entities) throws Throwable {
    int attempt = 0;
    boolean persisted = false;
    for (EntityBase entity : entities) {
      try {
        //   long revision = entity.findRevisionById();
        // if (entity.getRevision() > revision) {
        if (!entity.getPersisted()) {
          if (bulkInsert) bulkInsert(entity);
          else insert(entity);

          persisted = true;
        } else mergeEntities(bulkInsert, entity);
        // } else {
        //  log.trace("DapJpa - persistEntities: " + entity.getClass().getSimpleName() + " not
        // peristed as entity revision " + entity.getRevision()
        //        + " is not greater than peristed revision " + revision + ". Entity " +
        // entity.getId());
        // }

      } catch (OptimisticLockException | StaleObjectStateException ole) {
        if (attempt >= retry) {
          log.error(
              " "
                  + this.getClass().getSimpleName()
                  + ":persist attempt:"
                  + entity.getAttempt()
                  + " for "
                  + entity
                  + " "
                  + retry
                  + ", full stack trace follows:",
              ole);

          entity.setAttempt(0);

          throw ole;
        }
        attempt = entity.getAttempt() + 1;
        entity.setAttempt(attempt);
        try {
          if (entity.getVersion() < entity.getRevision()) {
            entity.setVersion(entity.getRevision());
            if (entity.getOriginalEntity() != null)
              entity.getOriginalEntity().setVersion(entity.getVersion());
            entity.setPeristanceAction(PersistanceAction.MERGE);
            if (entity instanceof Book) {
              Book book = (Book) entity;
              application.getMergeBookQueue(book.getMarket()).put(book);
            } else if (entity instanceof Trade) {
              Trade trade = (Trade) entity;
              application.getMergeTradeQueue(trade.getMarket()).put(trade);
            } else if (entity instanceof Bar) application.getMergeBarQueue().put((Bar) entity);
            else application.getMergeQueue().put(entity);

            //   merge(false, entity);
          }
        } catch (Exception | Error ex) {

          //
          //	entity.setPeristanceAction(PersistanceAction.MERGE);
          //	application.getMergeQueue().put(entity);
          //    merge(false, entity);
          //	}

        }
        // else {
        //		log.debug(this.getClass().getSimpleName() + ":persist Later version of " +
        // entity.getClass().getSimpleName() + " id: " + entity.getId()
        //				+ " already persisted. Persist attempt " + entity.getAttempt() + " of " + retry);
        //
        //					entity.setAttempt(0);
        //					entity.setPeristanceAction(PersistanceAction.NEW);
        //					application.getInsertQueue().put(entity);
        //      persist(false, entity);
        //				}

      } catch (LockTimeoutException lte) {

        attempt = entity.getAttempt() + 1;
        entity.setAttempt(attempt);

        if (attempt >= retry) {
          log.error(
              " "
                  + this.getClass().getSimpleName()
                  + ":persist attempt:"
                  + entity.getAttempt()
                  + " for "
                  + entity
                  + " "
                  + retry
                  + ", full stack trace follows:",
              lte);

          //    log.error(" " + this.getClass().getSimpleName() + ":persist, full stack trace
          // follows:", lte);
          entity.setAttempt(0);
          throw lte;
        } else {
          log.info(
              "Record locked version of "
                  + entity.getClass().getSimpleName()
                  + " id: "
                  + entity.getUuid()
                  + " already persisted. Persist attempt "
                  + entity.getAttempt()
                  + " of "
                  + retry);
          //               entity.setRevision(0);
          entity.setPeristanceAction(PersistanceAction.NEW);
          if (entity instanceof Book) {
            Book book = (Book) entity;
            application.getInsertBookQueue(book.getMarket()).put(book);
          } else if (entity instanceof Trade) {
            Trade trade = (Trade) entity;
            application.getInsertTradeQueue(trade.getMarket()).put(trade);
          } else if (entity instanceof Bar) application.getInsertBarQueue().put((Bar) entity);
          else application.getInsertQueue().put(entity);

          //  persist(false, entity);

        }

      } catch (Throwable ex) {
        //   log.debug("casuse:" + ex.getCause() + " ex " + ex);
        if (ex.getCause() != null
            && (ex.getCause() instanceof TransientPropertyValueException
                || ex.getCause() instanceof IllegalStateException)) {
          // .setVersion(entity.getVersion() + 1);
          attempt = entity.getAttempt() + 1;
          entity.setAttempt(attempt);
          //  entity.setStartTime(entity.getDelay() * 2);

          if (attempt >= retry) {
            // log.error(" " + this.getClass().getSimpleName() + ":persist, Parent object not
            // persisted for, attempting last ditch merge.");
            log.error(
                " "
                    + this.getClass().getSimpleName()
                    + ":persist attempt:"
                    + entity.getAttempt()
                    + " for "
                    + entity
                    + " "
                    + retry
                    + ", full stack trace follows:",
                ex);
            entity.setAttempt(0);
            // merge(entities);

            throw ex;
          } else {
            log.debug(
                "Parent object not persisted for  "
                    + entity.getClass().getSimpleName()
                    + " id: "
                    + entity.getUuid()
                    + ". Persist attempt "
                    + entity.getAttempt()
                    + " of "
                    + retry);
            //                   entity.setRevision(0);

            entity.persitParents();
            entity.prePersist();
            entity.setPeristanceAction(PersistanceAction.NEW);
            if (entity instanceof Book) {
              Book book = (Book) entity;
              application.getInsertBookQueue(book.getMarket()).put(book);
            } else if (entity instanceof Trade) {
              Trade trade = (Trade) entity;
              application.getInsertTradeQueue(trade.getMarket()).put(trade);
            } else if (entity instanceof Bar) application.getInsertBarQueue().put((Bar) entity);
            else application.getInsertQueue().put(entity);

            //    persist(false, entity);
            //  persist(false, entity);
            // attempt++;
            // continue;
          }
        } else if (ex instanceof EntityExistsException
            || (ex.getCause() != null
                && ex.getCause().getCause() instanceof ConstraintViolationException)) {
          attempt = entity.getAttempt() + 1;
          entity.setAttempt(attempt);
          //    entity.setStartTime(entity.getDelay() * 2);
          //   EntityBase dbEntity = entity.refresh();

          // dbEntity = entity;

          if (attempt >= retry) {
            log.error(
                " "
                    + this.getClass().getSimpleName()
                    + ":persist attempt:"
                    + entity.getAttempt()
                    + " for "
                    + entity
                    + " "
                    + retry
                    + ", full stack trace follows:",
                ex);
            //   log.error(" " + this.getClass().getSimpleName() + ":persist, full stack trace
            // follows:", ex);
            entity.setAttempt(0);
            throw ex;
          } else {
            log.debug(
                " "
                    + this.getClass().getSimpleName()
                    + " "
                    + entity.getClass().getSimpleName()
                    + ":"
                    + entity.getUuid()
                    + " :persist, primary key for version "
                    + entity.getVersion()
                    + "  already present in db. Persist attempt "
                    + entity.getAttempt()
                    + " of "
                    + retry);

            // entity.setAttempt(0);
            //                      entity.setRevision(0);
            entity.setPeristanceAction(PersistanceAction.MERGE);
            entity.setPersisted(true);
            if (entity.getOriginalEntity() != null) entity.getOriginalEntity().setPersisted(true);
            if (entity instanceof Book) {
              Book book = (Book) entity;
              application.getMergeBookQueue(book.getMarket()).put(book);
            } else if (entity instanceof Trade) {
              Trade trade = (Trade) entity;
              application.getMergeTradeQueue(trade.getMarket()).put(trade);
            } else if (entity instanceof Bar) application.getMergeBarQueue().put((Bar) entity);
            else application.getMergeQueue().put(entity);
            // merge(false, entity);
          }

        } else if (ex.getCause() != null && ex.getCause() instanceof PersistentObjectException) {
          entity.persitParents();
          attempt = entity.getAttempt() + 1;
          entity.setAttempt(attempt);
          // entity.setStartTime(entity.getDelay() * 2);
          // entity.setStartTime(entity.getDelay() * 2);

          //   update(entity);

          if (attempt >= retry) {
            log.error(
                " "
                    + this.getClass().getSimpleName()
                    + ":persist attempt:"
                    + entity.getAttempt()
                    + " for "
                    + entity
                    + " "
                    + retry
                    + ", full stack trace follows:",
                ex);
            entity.setAttempt(0);
            throw ex;
          } else {
            log.trace(
                " "
                    + this.getClass().getSimpleName()
                    + " "
                    + entity.getClass().getSimpleName()
                    + ":"
                    + entity.getUuid()
                    + " :persist, presistent object expection, will attmpt to merge. Persist attempt "
                    + entity.getAttempt()
                    + " of "
                    + retry);

            //              for (EntityBase entity : entities)
            // entity.setAttempt(0);
            //                        entity.setAttempt(0);
            //                     entity.setRevision(0);
            entity.setPeristanceAction(PersistanceAction.MERGE);

            if (entity instanceof Book) {
              Book book = (Book) entity;
              application.getMergeBookQueue(book.getMarket()).put(book);
            } else if (entity instanceof Trade) {
              Trade trade = (Trade) entity;
              application.getMergeTradeQueue(trade.getMarket()).put(trade);
            } else if (entity instanceof Bar) application.getMergeBarQueue().put((Bar) entity);
            else application.getMergeQueue().put(entity);

            //   merge(false, entity);
          }

        } else if (ex.getCause() != null && ex.getCause() instanceof OptimisticLockException) {
          attempt = entity.getAttempt() + 1;
          entity.setAttempt(attempt);
          //    entity.setStartTime(entity.getDelay() * 2);
          EntityBase dbEntity = entity.refresh();

          // dbEntity = entity;

          if (attempt >= retry) {
            log.error(
                " "
                    + this.getClass().getSimpleName()
                    + ":persist attempt:"
                    + entity.getAttempt()
                    + " for "
                    + entity
                    + " "
                    + retry
                    + ", full stack trace follows:",
                ex);
            entity.setAttempt(0);
            throw ex;
          } else {
            // entity.setAttempt(0);
            log.debug(
                " "
                    + this.getClass().getSimpleName()
                    + " "
                    + entity.getClass().getSimpleName()
                    + ":"
                    + entity.getUuid()
                    + " :persist, primary key for version "
                    + entity.getVersion()
                    + "  already present in db with version "
                    + dbEntity.getVersion()
                    + ". Persist attempt "
                    + entity.getAttempt()
                    + " of "
                    + retry);
            entity.setPeristanceAction(PersistanceAction.MERGE);
            if (entity instanceof Book) {
              Book book = (Book) entity;
              application.getMergeBookQueue(book.getMarket()).put(book);
            } else if (entity instanceof Trade) {
              Trade trade = (Trade) entity;
              application.getMergeTradeQueue(trade.getMarket()).put(trade);
            } else if (entity instanceof Bar) application.getMergeBarQueue().put((Bar) entity);
            else application.getMergeQueue().put(entity);

            // merge(false, entity);

          }

          // for (EntityBase entity : entities)
          //    merge(entities);
        } else {
          log.error(
              " "
                  + this.getClass().getSimpleName()
                  + ":persist attempt:"
                  + entity.getAttempt()
                  + " for "
                  + entity
                  + " "
                  + retry
                  + ", full stack trace follows:",
              ex);
          //  log.error(" " + this.getClass().getSimpleName() + ":persist, full stack trace
          // follows:", ex);

          entity.setAttempt(0);
          throw ex;
        }
        // break;

      } finally {

        if (persisted) {
          log.trace(
              " "
                  + this.getClass().getSimpleName()
                  + ":persist. Succefully persisted "
                  + entity.getClass().getSimpleName()
                  + " "
                  + entity.getUuid());

          entity.setAttempt(0);
          entity.setPersisted(true);
          if (entity.getOriginalEntity() != null) {
            entity.getOriginalEntity().setPersisted(true);
            entity.setOriginalEntity(null);
            entity = null;
          }
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
    if (!persistance) return;
    for (EntityBase entity : entities) {

      log.trace(
          "DaoJpa - Persist : Persit of {} {} called from class {}",
          entity.getClass().getSimpleName(),
          entity.getUuid(),
          Thread.currentThread().getStackTrace()[2]);

      // let's clone the object as it could update and cause issues
      //  SerializationUtils.clone(Object);
      if (entity.getPersisted()) persist(true, entity);
      else merge(true, entity);
    }
  }

  @Override
  public void persist(Book... books) {
    try {
      for (Book book : books) {
        if (book != null & !book.isPersisted()) {
          book.setPeristanceAction(PersistanceAction.NEW);
          if (application.getInsertBookQueue(book.getMarket()) != null)
            application.getInsertBookQueue(book.getMarket()).put(book);
          else application.getInsertQueue().put(book);
          log.trace("persisting " + book.getClass().getSimpleName() + " id:" + book.getUuid());
        }
      }
    } catch (Throwable e) {
      log.error(
          "Unable to resubmit insert request in "
              + this.getClass().getSimpleName()
              + "insert, full stack trace follows:",
          e);
    }
  }

  @Override
  public void persist(Bar... bars) {
    try {
      for (Bar bar : bars) {
        if (bar != null && !bar.isPersisted()) {
          bar.setPeristanceAction(PersistanceAction.NEW);
          application.getInsertBarQueue().put(bar);
          log.trace("persisting " + bar.getClass().getSimpleName() + " id:" + bar.getUuid());
        }
      }
    } catch (Throwable e) {
      log.error(
          "Unable to resubmit insert request in "
              + this.getClass().getSimpleName()
              + "insert, full stack trace follows:",
          e);
    }
  }

  @Override
  public void persist(Trade... trades) {
    try {
      for (Trade trade : trades) {
        if (trade != null && !trade.isPersisted()) {
          trade.setPeristanceAction(PersistanceAction.NEW);
          if (application.getInsertTradeQueue(trade.getMarket()) != null)
            application.getInsertTradeQueue(trade.getMarket()).put(trade);
          else application.getInsertQueue().put(trade);
          log.trace("persisting " + trade.getClass().getSimpleName() + " id:" + trade.getUuid());
        }
      }
    } catch (Throwable e) {
      log.error(
          "Unable to resubmit insert request in "
              + this.getClass().getSimpleName()
              + "insert, full stack trace follows:",
          e);
    }
  }

  private void persist(boolean increment, EntityBase... entities) {

    try {
      for (EntityBase entity : entities) {
        //    entity.getDao().persistEntities(entity);
        // synchronized (entity) {
        if (entity != null && !entity.isPersisted()) {
          entity.setPeristanceAction(PersistanceAction.NEW);
          //      if (increment)
          // entity.getDao().persistEntities(entity);
          //  SerializationUtils.clone(entity);
          synchronized (entity) {
            //	EntityBase entityClone = entity.clone();
            //	entityClone.setDao(entity.getDao());
            //	entityClone.setOriginalEntity(entity);
            log.trace(
                "persisting {} uuid: {} with queue length {}",
                entity.getClass().getSimpleName(),
                entity.getUuid(),
                application.getInsertQueue().size());
            application.getInsertQueue().put(entity);
          }

          //  } else {
          //    application.getInsertQueue().putFirst(entity);
          // }
          log.trace("persisting {}  uuid: {}", entity.getClass().getSimpleName(), entity.getUuid());
        }
        // }
      }

    } catch (Throwable e) {
      log.error(
          "Unable to resubmit insert request in "
              + this.getClass().getSimpleName()
              + "insert, full stack trace follows:",
          e);
      //  e.printStackTrace();

    }
  }

  @Override
  public void delete(EntityBase... entities) {

    for (EntityBase entity : entities)
      log.trace(
          "DaoJpa - Delete : delete of {} {} called from class {}",
          entity.getClass().getSimpleName(),
          entity.getUuid(),
          Thread.currentThread().getStackTrace()[2]);

    delete(true, entities);
  }

  private void delete(boolean increment, EntityBase... entities) {

    try {
      for (EntityBase entity : entities) {
        //   synchronized (entity) {
        //    entity.getDao().persistEntities(entity);
        if (entity != null) {
          entity.setPeristanceAction(PersistanceAction.DELETE);
          //  if (increment)
          // entity.setRevision(entity.getRevision() + 1);
          entity.setStartTime(entity.getDelay());
          //   entity.getDao().deleteEntities(entity);
          // EntityBase entityClone = entity.clone();

          // EntityBase entityClone = SerializationUtils.clone(entity);
          //  entityClone.setDao(entity.getDao());
          application.getMergeQueue().put(entity);
          // application.getMergeQueue().put(entity);

        }
        //  }
      }

    } catch (Error | Exception e) {
      log.error(
          "Unable to resubmit delete request in "
              + this.getClass().getSimpleName()
              + "delete, full stack trace follows:",
          e);
      //  e.printStackTrace();

    } finally {

    }
  }

  /*
   * public class persistRunnable implements Runnable {
   * @Override public void run() { while (true) { try { EntityBase[] entities = insertQueue.take(); persistEntities(entities); } catch
   * (InterruptedException e) { Thread.currentThread().interrupt(); return; // supposing there is no cleanup or other stuff to be done } } } public
   * persistRunnable() { } }
   */

  /*
   * public class mergeRunnable implements Runnable {
   * @Override public void run() { while (true) { try { EntityBase[] entities = mergeQueue.take(); mergeEntities(entities); } catch
   * (InterruptedException e) { Thread.currentThread().interrupt(); return; // supposing there is no cleanup or other stuff to be done } } } public
   * mergeRunnable() { } }
   */
  //  @Nullable
  // @ManyToOne(optional = true)
  // , cascade = { CascadeType.PERSIST, CascadeType.MERGE })
  // cascade = { CascadeType.ALL })
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
    if (!persistance) return;
    for (EntityBase entity : entities)
      log.trace(
          "DaoJpa - Merge : Merge of {} {} called from class {}",
          entity.getClass().getSimpleName(),
          entity.getUuid(),
          Thread.currentThread().getStackTrace()[2]);

    merge(false, entities);
  }

  @Override
  public void merge(Book... books) {
    try {
      for (Book book : books) {
        if (book != null && book.isPersisted()) {
          book.setPeristanceAction(PersistanceAction.MERGE);
          if (application.getMergeBookQueue(book.getMarket()) != null)
            application.getMergeBookQueue(book.getMarket()).put(book);
          else application.getMergeQueue().put(book);

          log.trace("merging " + book.getClass().getSimpleName() + " id:" + book.getUuid());
        } else {
          book.setPeristanceAction(PersistanceAction.NEW);
          if (application.getInsertBookQueue(book.getMarket()) != null)
            application.getInsertBookQueue(book.getMarket()).put(book);
          else application.getInsertQueue().put(book);
          log.trace("persisting " + book.getClass().getSimpleName() + " id:" + book.getUuid());
        }
      }
    } catch (Throwable e) {
      log.error(
          "Unable to resubmit merge request in "
              + this.getClass().getSimpleName()
              + " merge, full stack trace follows:",
          e);
    }
  }

  @Override
  public void merge(Trade... trades) {
    try {
      for (Trade trade : trades) {
        if (trade != null) {
          trade.setPeristanceAction(PersistanceAction.MERGE);
          if (application.getMergeTradeQueue(trade.getMarket()) != null)
            application.getMergeTradeQueue(trade.getMarket()).put(trade);
          else application.getMergeQueue().put(trade);
          log.trace("merging " + trade.getClass().getSimpleName() + " id:" + trade.getUuid());
        }
      }
    } catch (Throwable e) {
      log.error(
          "Unable to resubmit merge request in "
              + this.getClass().getSimpleName()
              + " merge, full stack trace follows:",
          e);
    }
  }

  @Override
  public void merge(Bar... bars) {
    try {
      for (Bar bar : bars) {
        if (bar != null && bar.isPersisted()) {
          bar.setPeristanceAction(PersistanceAction.MERGE);
          application.getMergeBarQueue().put(bar);

          log.trace("merging " + bar.getClass().getSimpleName() + " id:" + bar.getUuid());
        } else {
          bar.setPeristanceAction(PersistanceAction.NEW);
          application.getInsertBarQueue().put(bar);

          log.trace("persisting " + bar.getClass().getSimpleName() + " id:" + bar.getUuid());
        }
      }
    } catch (Throwable e) {
      log.error(
          "Unable to resubmit merge request in "
              + this.getClass().getSimpleName()
              + " merge, full stack trace follows:",
          e);
    }
  }

  // @Override
  private void merge(boolean increment, EntityBase... entities) {

    try {
      for (EntityBase entity : entities) {
        // synchronized (entity) {
        if (entity != null && entity.isPersisted()) {
          entity.setPeristanceAction(PersistanceAction.MERGE);
          synchronized (entity) {
            EntityBase entityClone = entity.clone();

            entityClone.setDao(entity.getDao());
            entityClone.setOriginalEntity(entity);
            log.trace(
                "merging {} uuid: {} with queue length {}",
                entity.getClass().getSimpleName(),
                entity.getUuid(),
                application.getMergeQueue().size());
            application.getMergeQueue().put(entityClone);
          }

          log.trace("merging {} uuid: {} ", entity.getClass().getSimpleName(), entity.getUuid());
        } else {
          persist(increment, entities);
        }
        // }
      }
    } catch (Throwable e) {

      log.error(
          "Unable to resubmit merge request in {}  merge, full stack trace follows:{}  ",
          this.getClass().getSimpleName(),
          e);
      // e.printStackTrace();

    }
  }

  @Transactional
  public void insert(EntityBase entity) throws Throwable {

    synchronized (entity) {
      entityManager.get().persist(entity);
    }
  }

  @Transactional
  public void bulkInsert(EntityBase entity) throws Throwable {

    synchronized (bulkInsertEntities) {
      if (bulkInsertEntities.size() > 0 && bulkInsertEntities.size() % batchSize == 0) {
        //	EntityTransaction entityTransaction = entityManager.get().getTransaction();

        //	try {
        //		entityTransaction.begin();

        for (EntityBase entityToPersit : bulkInsertEntities)
          entityManager.get().persist(entityToPersit);
        //		entityTransaction.commit();
        bulkInsertEntities.clear();
        //	} catch (RuntimeException e) {
        //		if (entityTransaction.isActive()) {
        //			entityTransaction.rollback();
        //		}
        //		throw e;
        //	} finally {
        //		entityManager.get().close();
        //	}
      }
      bulkInsertEntities.add(entity);
    }
  }

  @Transactional
  public EntityBase restore(EntityBase entity) {
    // EntityBase localEntity = entity;
    // localEntity = entity;
    ///  Object dBEntity;
    // if (!entityManager.get().contains(entity)) {
    //  try {
    EntityBase localEntity = entityManager.get().find(entity.getClass(), entity.getUuid());
    if (localEntity != null) {
      // entityManager.get().refresh(localEntity);
      //   entityManager.get().persist(localEntity);

      // localEntity
      // EntityBase newEntity = entityManager.get().merge(localEntity);
      // newEntity.setVersion(entity.getVersion());
      // EntityBase newEntityDecrement = entityManager.get().merge(localEntity);
      entity.setVersion(localEntity.getVersion());
      synchronized (entity) {
        entityManager.get().merge(entity);
      }
    } else {
      synchronized (entity) {
        entityManager.get().persist(entity);
      }
    }

    //   find(entity.getClass(), entity.getId());
    //  entityManager.get().merge(entity);

    // }

    // entityManager.get().refresh(entity);

    // localEntity = entityManager.get().merge(entity);

    // return null;

    // entityManager.get().refresh(localEntity);
    //   EntityBase returnEntity =
    return entityManager.get().find(entity.getClass(), entity.getId());

    // } catch (Error | Exception ex) {
    //      return null;
    // throw ex;
    //     }
    // return entity;

    // TODO Auto-generated method stub

  }

  @Transactional
  public EntityBase update(EntityBase entity) throws Throwable {

    synchronized (entity) {
      EntityBase dbEntity = entityManager.get().merge(entity);
      if (entity.getId() == null) {
        entity.setId(dbEntity.getId());
        if (entity.getOriginalEntity() != null) entity.getOriginalEntity().setId(dbEntity.getId());
      }
      return dbEntity;
    }
  }

  @Transactional
  public EntityBase bulkUpdate(EntityBase entity) throws Throwable {

    // ArrayList<EntityBase> dbEntities = new ArrayList<EntityBase>();
    synchronized (entity) {
      EntityBase dbEntity = entityManager.get().merge(entity);
      if (entity.getId() == null) {
        entity.setId(dbEntity.getId());
        if (entity.getOriginalEntity() != null) entity.getOriginalEntity().setId(dbEntity.getId());
      }
      return dbEntity;
      // dbEntities.add(dbEntity);

    }
  }

  @Transactional
  public void evict(EntityBase entity) {
    try {
      // entityManager.get().(entity);
      synchronized (entity) {
        entityManager.get().detach(entity);
      }
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
    if (!persistance) return;

    try {
      for (EntityBase entity : entities) {
        long revision = entity.findRevisionById();
        // long version = entity.findVersionById();

        if (entity.getRevision() >= revision) {
          //    attempt = entity.getAttempt();
          //
          //  EntityBase dbEntity = restore(entity);
          // .getClass(), entity.getId());

          // long dbVersion = target.findVersionById();
          //   if (dbEntity != null)
          //     entity.setVersion(dbEntity.getVersion());
          // refresh(entity);
          remove(entity);
          deleted = true;
        } else {
          log.debug(
              "DapJpa - deleteEntities: "
                  + entity.getClass().getSimpleName()
                  + " not peristed as entity revision "
                  + entity.getRevision()
                  + " is not greater than peristed revision "
                  + revision
                  + ". Entity "
                  + entity.getUuid());
        }
      }
    } catch (EntityNotFoundException | LockTimeoutException enf) {
      for (EntityBase entity : entities) {
        //   attempt = entity.getAttempt() + 1;
        // entity.setAttempt(attempt);

        //   if (attempt >= retry) {
        //   log.error(this.getClass().getSimpleName() + ":delete attempt:" + entity.getAttempt() +
        // " for " + entity + " " + retry
        //       + ", full stack trace follows:", enf);
        //      entity.setAttempt(0);
        //       throw enf;
        //     } else {

        //       EntityBase dbEntity = null;
        //       dbEntity = restore(entity);
        //
        //       if (dbEntity != null) {
        //         delete(false, dbEntity);
        //         deleted = true;
        //       }
        //      .setPeristanceAction(PersistanceAction.MERGE);
        // merge(false, entity);
        //    EntityBase dbEntity = restore(entity);

        // find(entity.getClass(), entity.getId());

        // long dbVersion = target.findVersionById();
        //  if (dbEntity != null)
        ///    entity.setVersion(dbEntity.getVersion());
        // entity.merge();
        log.debug(
            "Entity  "
                + entity.getClass().getSimpleName()
                + " id: "
                + entity.getUuid()
                + " not found in database to delete. Delete attempt "
                + entity.getAttempt()
                + " of "
                + retry);
        //    }
      }

    } catch (IllegalArgumentException ie) {
      for (EntityBase entity : entities) {
        attempt = entity.getAttempt() + 1;
        entity.setAttempt(attempt);
        if (attempt >= retry) {
          log.error(
              " "
                  + this.getClass().getSimpleName()
                  + ":delete attempt:"
                  + entity.getAttempt()
                  + " of "
                  + retry
                  + ", full stack trace follows:",
              ie);

          entity.setAttempt(0);
          throw ie;
        }
      }
      for (EntityBase entity : entities) {
        // entity.setAttempt(0);
        //      entity.setVersion(entity.getVersion() + 1);

        // entity.setStartTime(entity.getDelay());
        merge(false, entity);
        delete(false, entity);
        deleted = true;
        log.info(
            this.getClass().getSimpleName()
                + ":delete - Detached instance of "
                + entity.getClass().getSimpleName()
                + " id: "
                + entity.getUuid()
                + " already in database. Delete attempt "
                + entity.getAttempt()
                + " of "
                + retry);
      }

      // for (EntityBase entity : entities)
      //  delete(false, entities);

    } catch (OptimisticLockException | StaleObjectStateException ole) {

      //     unitOfWork.end();
      for (EntityBase entity : entities) {
        attempt = entity.getAttempt() + 1;
        entity.setAttempt(attempt);

        if (attempt >= retry) {
          log.error(
              " "
                  + this.getClass().getSimpleName()
                  + ":delete attempt:"
                  + entity.getAttempt()
                  + " of "
                  + retry
                  + ", full stack trace follows:",
              ole);

          entity.setAttempt(0);
          throw ole;
        }

        EntityBase dbEntity = null;
        try {
          dbEntity = restore(entity);
          if (dbEntity != null) {
            dbEntity.setPeristanceAction(PersistanceAction.DELETE);
            try {
              remove(dbEntity);
            } catch (Exception | Error ex) {
              delete(false, dbEntity);
            }

          } else {
            //   entity.setVersion(dbEntity.getVersion());
            entity.setPeristanceAction(PersistanceAction.DELETE);
            delete(false, entity);
            // deleted = true;
          }

        } catch (Exception | Error ex) {

          //
          entity.setPeristanceAction(PersistanceAction.DELETE);
          delete(false, entity);
          // deleted = true;
        }

        log.debug(
            this.getClass().getSimpleName()
                + ":delete - Later version of "
                + entity.getClass().getSimpleName()
                + " id: "
                + entity
                + " already merged. Delete attempt "
                + entity.getAttempt()
                + " of "
                + retry);
      }

      //  delete(false, entities);

    } catch (Exception | Error ex) {

      /*
       * if (ex.getMessage().equals("Entity not managed")) { for (EntityBase entity : entities) try { //entityManager.get().persist(entity); //
       * restore(entity); update(entity); remove(entity); // entityManager.get().refresh(entity); //entityManager.get().remove(entity); } catch
       * (Exception | Error ex1) { throw ex1; } // entity.refresh(); delete(entities); }
       */
      if (ex.getCause() != null
          && ex.getCause().getCause() instanceof TransientPropertyValueException) {
        for (EntityBase entity : entities) {
          // entity.setVersion(entity.getVersion() + 1);
          attempt = entity.getAttempt() + 1;
          entity.setAttempt(attempt);

          log.info(
              "Parent object not persisted for  "
                  + entity.getClass().getSimpleName()
                  + " id: "
                  + entity.getUuid()
                  + ". Delete attempt "
                  + entity.getAttempt()
                  + " of "
                  + retry);
        }
        if (attempt >= retry) {
          log.error(
              " " + this.getClass().getSimpleName() + ":delete, full stack trace follows:", ex);
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
          // continue;
        }
      } else if (ex.getCause() != null
          && ex.getCause().getCause() instanceof ConstraintViolationException) {
        for (EntityBase entity : entities) {
          attempt = entity.getAttempt() + 1;
          entity.setAttempt(attempt);

          log.debug(
              " "
                  + this.getClass().getSimpleName()
                  + " "
                  + entity.getClass().getSimpleName()
                  + ":"
                  + entity.getUuid()
                  + " :delete, duplicate primary key already in db. Delete attempt "
                  + entity.getAttempt()
                  + " of "
                  + retry);
        }
        if (attempt >= retry) {

          for (EntityBase entity : entities) {
            log.error(
                " "
                    + this.getClass().getSimpleName()
                    + ":merge, unable to merge on attempt "
                    + entity.getAttempt()
                    + " of "
                    + retry
                    + " full stack trace follows:",
                ex);
            entity.setAttempt(0);
          }
          // throw ex;
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

          log.debug(
              " "
                  + this.getClass().getSimpleName()
                  + " "
                  + entity.getClass().getSimpleName()
                  + ":"
                  + entity.getUuid()
                  + " :delete issue with accessing proerpties, retrying delete. Delete attempt "
                  + entity.getAttempt()
                  + " of "
                  + retry);

          // entity.setStartTime(entity.getDelay() * 2);
        }
        if (attempt >= retry) {

          for (EntityBase entity : entities) {
            log.error(
                " "
                    + this.getClass().getSimpleName()
                    + ":merge, unable to merge on attempt "
                    + entity.getAttempt()
                    + " of "
                    + retry
                    + " full stack trace follows:",
                ex);
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
            log.error(
                " "
                    + this.getClass().getSimpleName()
                    + ":delete attempt:"
                    + entity.getAttempt()
                    + " of "
                    + retry
                    + ", full stack trace follows:",
                ex);

            entity.setAttempt(0);
            throw ex;
          }
        }
        for (EntityBase entity : entities) {
          entity.setAttempt(0);
          //      entity.setVersion(entity.getVersion() + 1);

          entity.setStartTime(entity.getDelay());

          log.debug(
              this.getClass().getSimpleName()
                  + ":delete - Later version of "
                  + entity.getClass().getSimpleName()
                  + " id: "
                  + entity.getUuid()
                  + " already in database. Delete attempt "
                  + entity.getAttempt()
                  + " of "
                  + retry);
        }

        // for (EntityBase entity : entities)
        //  delete(false, entities);
      } else if (ex.getCause() != null && ex.getCause() instanceof IllegalArgumentException) {
        for (EntityBase entity : entities) {
          attempt = entity.getAttempt() + 1;
          entity.setAttempt(attempt);
          if (attempt >= retry) {
            log.error(
                " "
                    + this.getClass().getSimpleName()
                    + ":delete attempt:"
                    + entity.getAttempt()
                    + " of "
                    + retry
                    + ", full stack trace follows:",
                ex);

            entity.setAttempt(0);
            throw ex;
          }
        }
        for (EntityBase entity : entities) {
          // entity.setAttempt(0);
          //      entity.setVersion(entity.getVersion() + 1);

          // entity.setStartTime(entity.getDelay());
          merge(false, entity);
          delete(false, entity);
          log.info(
              this.getClass().getSimpleName()
                  + ":delete - Detached instance of "
                  + entity.getClass().getSimpleName()
                  + " id: "
                  + entity.getUuid()
                  + " already in database. Delete attempt "
                  + entity.getAttempt()
                  + " of "
                  + retry);
        }

        // for (EntityBase entity : entities)
        //  delete(false, entities);
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
          log.trace(
              this.getClass().getSimpleName()
                  + ":delete. Succefully deleted "
                  + entity.getClass().getSimpleName()
                  + " "
                  + entity.getUuid());
        }
    }
    // break;
    // }
    //  break;

    // ex.printStackTrace();

  }

  @Override
  public EntityBase mergeEntities(boolean bulkInsert, EntityBase... entities) throws Throwable {

    int attempt = 0;
    boolean merged = false;
    EntityBase mergedEntity = null;
    try {
      for (EntityBase entity : entities) {
        long revision = entity.findRevisionById();
        if (entity.getRevision() >= revision) {
          //	entity.setVersion(entity.getRevision());
          mergedEntity = (bulkInsert ? bulkUpdate(entity) : update(entity));

          merged = true;

        } else {
          log.debug(
              "DapJpa - mergeEntities: "
                  + entity.getClass().getSimpleName()
                  + " not peristed as entity revision "
                  + entity.getRevision()
                  + " is not greater than peristed revision "
                  + revision
                  + ". Entity "
                  + entity.getUuid());
        }
      }
    } catch (EntityNotFoundException | IllegalArgumentException | LockTimeoutException enf) {
      for (EntityBase entity : entities) {
        attempt = entity.getAttempt() + 1;
        entity.setAttempt(attempt);

        //  attempt = entity.getAttempt() + 1;
        //   entity.setAttempt(0);
        //  entity.setStartTime(entity.getDelay() * 2);f

        log.info(
            this.getClass().getSimpleName()
                + "Entity  "
                + entity.getClass().getSimpleName()
                + " id: "
                + entity.getUuid()
                + " not found in database, persisting. Merge attempt "
                + entity.getAttempt()
                + " of "
                + retry);
        if (attempt >= retry) {

          log.error(
              "{}:merge attempt:{} {} for {} {} , full stack trace follows:{}",
              this.getClass().getSimpleName(),
              entity.getAttempt(),
              entity.getClass().getSimpleName(),
              entity,
              retry,
              enf);

          entity.setAttempt(0);
          throw enf;
        } else {
          // try {
          //    restore(entity);
          // } catch (Exception | Error ex1) {
          //     log.error(" " + this.getClass().getSimpleName() + ":merge attempt:" +
          // entity.getAttempt() + " of " + retry
          //           + ", unable to restore entity " + entity.getClass().getSimpleName() + " id "
          // + entity.getId());
          // }
          entity.prePersist();

          entity.setPeristanceAction(PersistanceAction.MERGE);
          if (entity instanceof Book) {
            Book book = (Book) entity;
            application.getMergeBookQueue(book.getMarket()).put(book);
          } else if (entity instanceof Trade) {
            Trade trade = (Trade) entity;
            application.getMergeTradeQueue(trade.getMarket()).put(trade);
          } else if (entity instanceof Bar) application.getMergeBarQueue().put((Bar) entity);
          else application.getMergeQueue().put(entity);
          //   persist(false, entity);
          //  EntityBase dbEntity = null;
          /*
           * try { dbEntity = restore(entity); } catch (Exception | Error ex) { // entity.setPeristanceAction(PersistanceAction.MERGE); merge(false,
           * entity); return; } if (dbEntity != null) merge(false, entity); else persist(false, entity);
           */
          //  entity.setPeristanceAction(PersistanceAction.NEW);
          // persist(false, entity);
          // merge(false, entity);
          //      return;

          //  log.error("error", ex);
          //   }
          // find(entity.getClass(), entity.getId());

          // long dbVersion = target.findVersionById();
          //   if (dbEntity != null)
          //        entity.setVersion(dbEntity.getVersion());
          // entity.refresh();
          // if (dbEntity != null) {
          //  dbEntity.setPeristanceAction(PersistanceAction.MERGE);
          //  merge(false, dbEntity);
          // }

          //  entity.setAttempt(0);
          // well it may be in db put not in persistance context
          // entity.setRevision(0);
          // entity.setPeristanceAction(PersistanceAction.NEW);
          //// persist(false, entity);
        }
      }
    } catch (OptimisticLockException | StaleObjectStateException ole) {

      //     unitOfWork.end();
      for (EntityBase entity : entities) {

        if (entity.getAttempt() >= retry) {

          log.debug(
              " "
                  + this.getClass().getSimpleName()
                  + " "
                  + entity.getClass().getSimpleName()
                  + ":"
                  + entity.getUuid()
                  + " :merge, primary key for version "
                  + entity.getVersion()
                  + "  already present in db with higher version. Merge attempt "
                  + entity.getAttempt()
                  + " of "
                  + retry);
          entity.setAttempt(0);

          // throw ole;
        } else {
          try {
            //	if (entity.getVersion() < entity.getRevision()) {
            entity.setVersion(entity.getVersion() + 1);
            entity.setPeristanceAction(PersistanceAction.MERGE);
            mergedEntity = update(entity);

            merged = true;
            /*if (entity instanceof Book) {
            	Book book = (Book) entity;
            	application.getMergeBookQueue(book.getMarket()).put(book);
            } else if (entity instanceof Trade) {
            	Trade trade = (Trade) entity;
            	application.getMergeTradeQueue(trade.getMarket()).put(trade);
            }

            else if (entity instanceof Bar)
            	application.getMergeBarQueue().put((Bar) entity);
            else
            	application.getMergeQueue().put(entity);*/

            //   merge(false, entity);

          } catch (Exception | Error ex) {

            //
            entity.setPeristanceAction(PersistanceAction.MERGE);
            if (entity instanceof Book) {
              Book book = (Book) entity;
              application.getMergeBookQueue(book.getMarket()).put(book);
            } else if (entity instanceof Trade) {
              Trade trade = (Trade) entity;
              application.getMergeTradeQueue(trade.getMarket()).put(trade);
            } else if (entity instanceof Bar) application.getMergeBarQueue().put((Bar) entity);
            else application.getMergeQueue().put(entity);

            //    merge(false, entity);
          }
          attempt = entity.getAttempt() + 1;
          entity.setAttempt(attempt);

          //     return;

          //  log.error("error", ex);
          //   }
          //     EntityBase dbEntity = restore(entity);
          // find(entity.getClass(), entity.getId());

          // long dbVersion = target.findVersionById();
          //  if (dbEntity != null)
          //  entity.setVersion(dbEntity.getVersion());
          //
          // }
          // for (EntityBase entity : entities) {
          //  EntityBase dbEntity = find(entity.getClass(), entity.getId());

          //   dbEntity.setVersion(0);
          //  if (dbEntity instanceof Fill) {
          //        Fill dbfill = (Fill) dbEntity;
          ///        Fill fill = (Fill) entity;
          // .setOpenVolumeCount(fill.getOpenVolumeCount());

          //       merge(false, dbEntity);
          //   }

          // update(dbEntity);
          // EntityBase dbEntity = entity.refresh();
          // if (dbEntity != null) {
          //      entity.setVersion(dbEntity.getVersion());
          //      merge(dbEntity);
          //  } //else
          // entity.setVersion(entity.getVersion() + 1);

          //	* if (attempt >= retry) { log.error(this.getClass().getSimpleName() + ":merge attempt:"
          // + entity.getAttempt() + " of " + retry +
          //	* ", unable to merge " + entity.getClass().getSimpleName() + " with id " +
          // entity.getId()); entity.setAttempt(0); // throw ole; } else {
          //	* //} //for (EntityBase entity : entities) {
          // entity.setVersion(Math.max(entity.findVersionById() + 1, entity.getVersion() + 1));
          //  entity.setStartTime(entity.getDelay() * 2);
          //   entity.setAttempt(0);
          //			log.debug(this.getClass().getSimpleName() + ":merge - Later version of " +
          // entity.getClass().getSimpleName() + " id: " + entity
          //					+ " already merged. Merge attempt " + entity.getAttempt() + " of " + retry);
          //  entity.setRevision(0);
          //   merge(false, entity);
          // mergeEntities(entities);
          // update(entity);
          //       entity.setPeristanceAction(PersistanceAction.MERGE);
          // merge(entity);
          // merge(false, entity);

        }
      }

    } catch (Throwable ex) {
      //    log.debug(" cause: " + ex.getCause() + "cause casuse" + ex.getCause().getCause() + "ex"
      // + ex);
      if (ex.getCause() != null && ex.getCause() instanceof TransientPropertyValueException
          || (ex.getCause() != null
              && ex.getCause().getCause() != null
              && ex.getCause().getCause() instanceof TransientPropertyValueException)) {
        for (EntityBase entity : entities) {
          // entity.setVersion(entity.getVersion() + 1);
          attempt = entity.getAttempt() + 1;
          entity.setAttempt(attempt);

          entity.persitParents();
          entity.prePersist();

          log.info(
              "Parent object not persisted for  "
                  + entity.getClass().getSimpleName()
                  + " id: "
                  + entity.getUuid()
                  + ". Merge attempt "
                  + entity.getAttempt()
                  + " of "
                  + retry);

          if (attempt >= retry) {
            log.error(
                " "
                    + this.getClass().getSimpleName()
                    + ":merge attempt:"
                    + entity.getAttempt()
                    + " for "
                    + entity
                    + " "
                    + retry
                    + ", full stack trace follows:",
                ex);

            // log.error(" " + this.getClass().getSimpleName() + ":merge, full stack trace
            // follows:", ex);
            entity.setAttempt(0);
            throw ex;
          } else {

            // entity.setRevision(0);
            //                   entity.setRevision(0);
            entity.prePersist();
            entity.setPeristanceAction(PersistanceAction.MERGE);
            if (entity instanceof Book) {
              Book book = (Book) entity;
              application.getMergeBookQueue(book.getMarket()).put(book);
            } else if (entity instanceof Trade) {
              Trade trade = (Trade) entity;
              application.getMergeTradeQueue(trade.getMarket()).put(trade);
            } else if (entity instanceof Bar) application.getMergeBarQueue().put((Bar) entity);
            else application.getMergeQueue().put(entity);

            //     merge(false, entities);

          }
        }
      } else if (ex.getCause() != null
          && ex.getCause().getCause() instanceof ConstraintViolationException) {
        for (EntityBase entity : entities) {
          attempt = entity.getAttempt() + 1;
          entity.setAttempt(attempt);

          log.debug(
              " "
                  + this.getClass().getSimpleName()
                  + " "
                  + entity.getClass().getSimpleName()
                  + ":"
                  + entity.getUuid()
                  + " :merge, duplicate primary key already in db. Persist attempt "
                  + entity.getAttempt()
                  + " of "
                  + retry);

          if (attempt >= retry) {
            log.error(
                " "
                    + this.getClass().getSimpleName()
                    + ":merge attempt:"
                    + entity.getAttempt()
                    + " for "
                    + entity
                    + " "
                    + retry
                    + ", full stack trace follows:",
                ex);

            //  log.error(" " + this.getClass().getSimpleName() + ":merge, full stack trace
            // follows:", ex);
            entity.setAttempt(0);

            throw ex;
          } else {
            //      entity.setRevision(0);
            entity.setPeristanceAction(PersistanceAction.MERGE);
            entity.setPersisted(true);
            if (entity.getOriginalEntity() != null) entity.getOriginalEntity().setPersisted(true);
            if (entity instanceof Book) {
              Book book = (Book) entity;
              application.getMergeBookQueue(book.getMarket()).put(book);
            } else if (entity instanceof Trade) {
              Trade trade = (Trade) entity;
              application.getMergeTradeQueue(trade.getMarket()).put(trade);
            } else if (entity instanceof Bar) application.getMergeBarQueue().put((Bar) entity);
            else application.getMergeQueue().put(entity);

            //    merge(false, entities);
          }
        }

      } else if (ex.getCause() != null && ex.getCause() instanceof PropertyAccessException) {
        for (EntityBase entity : entities) {
          attempt = entity.getAttempt() + 1;
          entity.setAttempt(attempt);

          log.debug(
              " "
                  + this.getClass().getSimpleName()
                  + " "
                  + entity.getClass().getSimpleName()
                  + ":"
                  + entity.getUuid()
                  + " :merge, issue with accessing proerpties, retrying merge. Persist attempt "
                  + entity.getAttempt()
                  + " of "
                  + retry,
              ex);

          if (attempt >= retry) {
            log.error(
                " "
                    + this.getClass().getSimpleName()
                    + ":merge attempt:"
                    + entity.getAttempt()
                    + " for "
                    + entity
                    + " "
                    + retry
                    + ", full stack trace follows:",
                ex);

            // log.error(" " + this.getClass().getSimpleName() + ":merge, full stack trace
            // follows:", ex);
            entity.setAttempt(0);
            throw ex;
          } else {
            //     entity.setRevision(0);
            entity.setPeristanceAction(PersistanceAction.MERGE);
            if (entity instanceof Book) {
              Book book = (Book) entity;
              application.getMergeBookQueue(book.getMarket()).put(book);
            } else if (entity instanceof Trade) {
              Trade trade = (Trade) entity;
              application.getMergeTradeQueue(trade.getMarket()).put(trade);
            } else if (entity instanceof Bar) application.getMergeBarQueue().put((Bar) entity);
            else application.getMergeQueue().put(entity);

            //  merge(false, entities);
          }
        }
      } else if (ex.getCause() != null && ex.getCause() instanceof OptimisticLockException) {
        for (EntityBase entity : entities) {
          if (attempt >= retry) {
            log.error(
                " "
                    + this.getClass().getSimpleName()
                    + ":merge attempt:"
                    + entity.getAttempt()
                    + " for "
                    + entity
                    + " "
                    + retry
                    + ", full stack trace follows:",
                ex);

            entity.setAttempt(0);

            throw ex;
          }
          attempt = entity.getAttempt() + 1;
          entity.setAttempt(attempt);
          try {
            if (entity.getVersion() < entity.getRevision()) {
              entity.setVersion(entity.getRevision());
              entity.setPeristanceAction(PersistanceAction.MERGE);
              if (entity instanceof Book) {
                Book book = (Book) entity;
                application.getMergeBookQueue(book.getMarket()).put(book);
              } else if (entity instanceof Trade) {
                Trade trade = (Trade) entity;
                application.getMergeTradeQueue(trade.getMarket()).put(trade);
              } else if (entity instanceof Bar) application.getMergeBarQueue().put((Bar) entity);
              else application.getMergeQueue().put(entity);

              //   merge(false, entity);
            }
          } catch (Exception | Error e) {

            //
            //	entity.setPeristanceAction(PersistanceAction.MERGE);
            //	application.getMergeQueue().put(entity);
            //    merge(false, entity);
            //	}

          }
          // else {
          //		log.debug(this.getClass().getSimpleName() + ":persist Later version of " +
          // entity.getClass().getSimpleName() + " id: " + entity.getId()
          //				+ " already persisted. Persist attempt " + entity.getAttempt() + " of " + retry);
          //
          //					entity.setAttempt(0);
          //					entity.setPeristanceAction(PersistanceAction.NEW);
          //					application.getInsertQueue().put(entity);
          //      persist(false, entity);
          //
          /*
          for (EntityBase entity : entities) {
          	attempt = entity.getAttempt() + 1;
          	entity.setAttempt(attempt);
          	if (attempt >= retry) {
          		log.error(" " + this.getClass().getSimpleName() + ":merge attempt:" + entity.getAttempt() + " for " + entity + " " + retry
          				+ ", full stack trace follows:", ex);

          		entity.setAttempt(0);
          		throw ex;
          	} else {
          		EntityBase dbEntity = null;
          		try {
          			dbEntity = restore(entity);
          			if (dbEntity != null)
          				entity.setVersion(dbEntity.getVersion());
          		} catch (Exception | Error ex1) {
          			entity.setPeristanceAction(PersistanceAction.MERGE);
          			if (entity instanceof Book) {
          				Book book = (Book) entity;
          				application.getMergeBookQueue(book.getMarket()).put(book);
          			} else if (entity instanceof Trade) {
          				Trade trade = (Trade) entity;
          				application.getMergeTradeQueue(trade.getMarket()).put(trade);
          			}

          			else if (entity instanceof Bar)
          				application.getMergeBarQueue().put((Bar) entity);
          			else
          				application.getMergeQueue().put(entity);

          			//   merge(false, entity);
          			return;

          			//  log.error("error", ex);
          		}
          		// }
          		// for (EntityBase entity : entities) {
          		//
          		// EntityBase dbEntity = restore(entity);
          		//find(entity.getClass(), entity.getId());

          		// long dbVersion = target.findVersionById();
          		//   if (dbEntity != null)
          		//   entity.setVersion(entity.getVersion() + 1);

          		//      EntityBase dbEntity = find(entity.getClass(), entity.getId());
          		//     if (dbEntity != null)
          		//   dbEntity.setVersion(0);
          		// merge(false, dbEntity);
          		//entity.setVersion(dbEntity.getVersion());
          		//  update(dbEntity);

          		// entity.setStartTime(entity.getDelay() * 2);
          		//  entity.setAttempt(0);
          		//	log.debug(this.getClass().getSimpleName() + ":merge - Later version of " + entity.getClass().getSimpleName() + " id: " + entity
          		//			+ " caused by already merged. Merge attempt " + entity.getAttempt() + " of " + retry);
          		//           entity.setRevision(0);
          		//  entity.setPeristanceAction(PersistanceAction.MERGE);

          		//    merge(false, entity);
          		// mergeEntities(entities);
          		//
          		// update(entity);
          		//persist(false, entities);
          */
        }
      } else {

        for (EntityBase entity : entities) {
          log.error(
              " "
                  + this.getClass().getSimpleName()
                  + ":merge, unable to merge "
                  + entity.getId()
                  + " full stack trace follows:",
              ex);
          entity.setAttempt(0);
        }
        throw ex;
      }

    } finally {
      if (merged) {
        for (EntityBase entity : entities) {
          log.trace(
              " "
                  + this.getClass().getSimpleName()
                  + ":merge. Succefully merged "
                  + entity.getClass().getSimpleName()
                  + " "
                  + entity.getUuid());

          entity.setAttempt(0);
          entity.setPersisted(true);

          if (entity.getOriginalEntity() != null) {
            entity.getOriginalEntity().setPersisted(true);
            entity.setOriginalEntity(null);
            entity = null;
          }
        }
      }
    }
    return mergedEntity;
  }

  /**
   * returns a single result entity. if none found, a javax.persistence.NoResultException is thrown.
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

    //   EntityBase target = entity;
    //  em.remove(target);
    //    utx.commit();

    //  EntityBase localEntity;
    // synchronized (entity) {
    //    if (!entityManager.get().contains(entity))
    //      try {

    //        target = entityManager.get().merge(entity);

    //  } catch (OptimisticLockException | StaleObjectStateException ole) {
    //    EntityBase dbEntity = null;
    //   try {
    //     dbEntity = restore(entity);
    //  } catch (Exception | Error ex) {
    //     entity.setPeristanceAction(PersistanceAction.MERGE);

    //    merge(false, entity);
    //     return;

    //  log.error("error", ex);

    //  EntityBase dbEntity = restore(entity);

    // long dbVersion = target.findVersionById();
    //   if (dbEntity != null)
    //      entity.setVersion(dbEntity.getVersion());
    //  target = entityManager.get().merge(entity);
    //  }

    ///  entityManager.get().refresh(entity);

    //   if (localEntity != null)
    // entityManager.get().refresh(target);
    synchronized (entity) {
      entityManager
          .get()
          .remove(
              entityManager.get().contains(entity) ? entity : entityManager.get().merge(entity));
    }
    // entityManager.get().remove(entity);
    // }
  }

  @Override
  public <T extends EntityBase> T findById(Class<T> resultType, Long id) throws NoResultException {
    try {
      return queryOne(
          resultType, "select x from " + resultType.getSimpleName() + " x where x.id = ?1", id);
    } catch (Exception | Error ex) {
      log.error(
          "Unable to perform request in "
              + this.getClass().getSimpleName()
              + ":findById, full stack trace follows:",
          ex);
      throw ex;
      // break;

    }
  }

  @Override
  public <T> Long findRevisionById(Class<T> resultType, Long id) throws NoResultException {
    try {
      Integer revision =
          queryOne(
              Integer.class,
              "select revision from " + resultType.getSimpleName() + " x where x.id = ?1",
              id);
      return revision.longValue();
    } catch (Exception | Error ex) {
      // log.error("Unable to perform request in " + this.getClass().getSimpleName() + ":findById,
      // full stack trace follows:", ex);
      throw ex;
      // break;

    }
  }

  @Override
  public <T> Long findVersionById(Class<T> resultType, Long id) throws NoResultException {
    try {
      Long version =
          queryOne(
              Long.class,
              "select version from " + resultType.getSimpleName() + " x where x.id = ?1",
              id);
      return version.longValue();
    } catch (Exception | Error ex) {
      // log.error("Unable to perform request in " + this.getClass().getSimpleName() + ":findById,
      // full stack trace follows:", ex);
      throw ex;
      // break;

    }
  }
}
