package org.cryptocoinpartners.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;

import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.Query;
import javax.persistence.TypedQuery;

import org.cryptocoinpartners.schema.Currencies;
import org.cryptocoinpartners.schema.EntityBase;
import org.cryptocoinpartners.schema.Exchanges;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Tim Olson
 */
public class PersistUtil implements Runnable {

	private static Logger log = LoggerFactory.getLogger("org.cryptocoinpartners.persist");
	private static final BlockingQueue<EntityBase[]> blockingQueue = new ArrayBlockingQueue<EntityBase[]>(10000);
	private static boolean running = false;
	private static boolean shutdown = false;
	private static FutureTask persitanceTask = null;
	private static ExecutorService service;

	private static void persist(EntityBase... entities) {
		boolean persited = true;
		try {
			PersistUtilHelper.beginTransaction();

			//PersistUtilHelper.beginTransaction();
			try {
				for (EntityBase entity : entities)
					PersistUtilHelper.getEntityManager().persist(entity);
				PersistUtilHelper.commit();
			} catch (Exception e) {
				e.printStackTrace();
				if (persited)
					for (EntityBase entity : entities)
						log.error(entity.getClass().getSimpleName() + ": " + entity.getId().toString() + " not saved to database");
				if (PersistUtilHelper.isActive())
					PersistUtilHelper.rollback();

			}
		} finally {
			if (persited)
				for (EntityBase entity : entities)
					log.debug(entity.getClass().getSimpleName() + ": " + entity.getId().toString() + " saved to database");

		}
	}

	public static void insert(EntityBase... entities) {
		try {

			persist(entities);

		} catch (Exception e) {
			e.printStackTrace();

		} finally {
			PersistUtilHelper.closeEntityManager();
		}
	}

	public static void insertThreaded(EntityBase... entities) {
		//org.hibernate.TransientPropertyValueException is thrown when peristing entities with many to one or many to many relationships
		// as this persitnace is done via a separete thread, even though the smae entitymanager factory is used, it is not aware of any of the already saved entities.
		// if you don't close the session manager after each perist, it works fine, hovever the query methods faild as they are not aware of the newly persitsted entities
		// until the entity manager has been closed.
		if (!shutdown && running && persitanceTask == null) {
			service = Executors.newSingleThreadExecutor();
			PersistUtil persistanceThread = new PersistUtil();
			persitanceTask = (FutureTask) service.submit(persistanceThread);
			running = true;
			shutdown = false;
			try {
				blockingQueue.put(entities);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} else if (!running) {
			EntityManager em = null;
			try {

				for (EntityBase entity : entities)
					persist(entities);
			} finally {

				PersistUtilHelper.closeEntityManager();
			}

		} else {

			try {

				blockingQueue.put(entities);

			} catch (Exception e) {

				log.error("Cointrader Database Peristnace had an error, the details are:  {}.", e);

			}
		}

	}

	/**
	 * Use this method if you do not know the number of columns or rows in the result set.  The visitor will be called
	 * once for each row with an Object[] of column values
	 */
	public static void queryEach(Visitor<Object[]> handler, String queryStr, Object... params) {
		queryEach(handler, defaultBatchSize, queryStr, params);
	}

	/**
	 * Use this method if you do not know the number of columns or rows in the result set.  The visitor will be called
	 * once for each row with an Object[] of column values
	 */
	@SuppressWarnings("ConstantConditions")
	public static void queryEach(Visitor<Object[]> handler, int batchSize, String queryStr, Object... params) {
		EntityManager em = null;
		try {
			em = createEntityManager();
			final Query query = em.createQuery(queryStr);
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
			if (em != null)
				em.close();
		}
	}

	public static <T> void queryEach(Class<T> resultType, Visitor<T> handler, String queryStr, Object... params) {
		queryEach(resultType, handler, defaultBatchSize, queryStr, params);
	}

	public static <T> void queryEach(Class<T> resultType, Visitor<T> handler, int batchSize, String queryStr, Object... params) {
		EntityManager em = null;
		try {
			em = createEntityManager();
			final TypedQuery<T> query = em.createQuery(queryStr, resultType);
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
			if (em != null)
				em.close();
		}
	}

	public static <T> List<T> queryList(Class<T> resultType, String queryStr, Object... params) {
		EntityManager em = null;
		try {
			em = createEntityManager();
			final TypedQuery<T> query = em.createQuery(queryStr, resultType);
			if (params != null) {
				for (int i = 0; i < params.length; i++) {
					Object param = params[i];
					query.setParameter(i + 1, param); // JPA uses 1-based indexes
				}
			}
			return query.getResultList();
		} finally {
			if (em != null)
				em.close();
		}
	}

	/**
	 returns a single result entity.  if none found, a javax.persistence.NoResultException is thrown.
	 */
	public static <T> T queryOne(Class<T> resultType, String queryStr, Object... params) throws NoResultException {
		EntityManager em = null;
		try {
			em = createEntityManager();
			final TypedQuery<T> query = em.createQuery(queryStr, resultType);
			if (params != null) {
				for (int i = 0; i < params.length; i++) {
					Object param = params[i];
					query.setParameter(i + 1, param); // JPA uses 1-based indexes
				}
			}
			return query.getSingleResult();
		} finally {
			if (em != null)
				em.close();
		}
	}

	/**
	 returns a single result entity or null if not found
	 */
	public static <T> T queryZeroOne(Class<T> resultType, String queryStr, Object... params) {
		EntityManager em = null;
		try {
			em = createEntityManager();
			final TypedQuery<T> query = em.createQuery(queryStr, resultType);
			if (params != null) {
				for (int i = 0; i < params.length; i++) {
					Object param = params[i];
					query.setParameter(i + 1, param); // JPA uses 1-based indexes
				}
			}
			try {
				return query.getSingleResult();
			} catch (NoResultException x) {
				return null;
			}
		} finally {
			if (em != null)
				em.close();
		}
	}

	public static <T extends EntityBase> T findById(Class<T> resultType, UUID id) throws NoResultException {
		return queryOne(resultType, "select x from " + resultType.getSimpleName() + " x where x.id = ?1", id);
	}

	public static EntityManager createEntityManager() {
		init(false);
		return PersistUtilHelper.getEntityManager();
	}

	public static void resetDatabase() {
		init(true);
	}

	public static void init() {
		init(false);
		running = true;
		shutdown = false;
	}

	public static void shutdown() {
		if (persitanceTask != null) {
			shutdown = true;
			while (!persitanceTask.isDone()) {
				//log.info("waiting for persitance thread to shutdown");
			}
			running = false;
			service.shutdown();

		}
		if (PersistUtilHelper.getEntityManagerFactory() != null)
			PersistUtilHelper.closeEntityManagerFactory();
	}

	private static void init(boolean resetDatabase) {
		if (PersistUtilHelper.getEntityManagerFactory() != null) {
			if (!PersistUtilHelper.isOpen()) {
				log.warn("entityManagerFactory was closed.  Re-initializing");
				PersistUtilHelper.reset();
			} else if (!resetDatabase) {
				// entityManagerFactory exists, is open, and a reset is not requested.  continue to use existing EMF
				return;
			}
		}

		if (resetDatabase) {
			log.info("resetting database");
		} else
			log.info("initializing persistence");
		Map<String, String> properties = new HashMap<>();
		String createMode;
		if (resetDatabase)
			createMode = "create";
		else
			createMode = "update";
		properties.put("hibernate.hbm2ddl.auto", createMode);
		properties.put("hibernate.connection.driver_class", ConfigUtil.combined().getString("db.driver"));
		properties.put("hibernate.dialect", ConfigUtil.combined().getString("db.dialect"));
		properties.put("hibernate.connection.url", ConfigUtil.combined().getString("db.url"));
		properties.put("hibernate.connection.username", ConfigUtil.combined().getString("db.username"));
		properties.put("hibernate.connection.password", ConfigUtil.combined().getString("db.password"));
		properties.put("hibernate.ejb.naming_strategy", "org.hibernate.cfg.ImprovedNamingStrategy");
		try {
			PersistUtilHelper emh = new PersistUtilHelper(properties);
			ensureSingletonsExist();

		} catch (Throwable t) {
			if (PersistUtilHelper.getEntityManagerFactory() != null) {
				PersistUtilHelper.closeEntityManagerFactory();

			}
			throw new Error("Could not initialize db", t);
		}

	}

	private static void ensureSingletonsExist() {
		// Touch the singleton holders
		Currencies.BTC.getSymbol(); // this should load all the singletons in Currencies
		Exchanges.BITFINEX.getSymbol(); // this should load all the singletons in Exchanges
	}

	private static final int defaultBatchSize = 20;

	@Override
	public void run() {
		EntityBase[] entities;
		while (!blockingQueue.isEmpty() || !shutdown) {

			try {
				entities = blockingQueue.take();
				persist(entities);
			} catch (Exception e) {
				e.printStackTrace();

			} finally {
				entities = null;
				PersistUtilHelper.closeEntityManager();
			}

		}
	}

}
