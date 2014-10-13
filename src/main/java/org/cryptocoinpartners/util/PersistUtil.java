package org.cryptocoinpartners.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.NoResultException;
import javax.persistence.Persistence;
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
public class PersistUtil implements Callable<String> {

	private static Logger log = LoggerFactory.getLogger("org.cryptocoinpartners.persist");

	private PersistUtil(EntityBase... entities) {
		this.entities = entities;
	}

	public static void insert(EntityBase... entities) {
		PersistUtil persistanceThread = new PersistUtil(entities);
		FutureTask<String> futureTask1 = new FutureTask<String>(persistanceThread);
		// FutureTask<String> futureTask2 = new FutureTask<String>(callable2);
		try {
			service.execute(futureTask1);
			while (true) {

				if (futureTask1.isDone()) {
					for (EntityBase entity : entities) {
						log.debug(entity.getClass().getSimpleName() + " saved to database");
					}
					return;
				}
			}
		} catch (Exception e) {
			if (e instanceof InterruptedException) {
				log.error("Cointrader Database Peristnace had an error, the details are:  {}.", e);

			} else if (e instanceof ExecutionException) {
				log.info("Cointrader Database Peristnace had an error, the details are: {}.", e);

			} else {
				log.info("Cointrader Database Peristnace had an error, the details are: {}.", e);

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
		return entityManagerFactory.createEntityManager();
	}

	public static void resetDatabase() {
		init(true);
	}

	public static void init() {
		init(false);
	}

	public static void shutdown() {
		if (entityManagerFactory != null)
			//	service.shutdown();
			entityManagerFactory.close();
	}

	private static void init(boolean resetDatabase) {
		if (entityManagerFactory != null) {
			if (!entityManagerFactory.isOpen()) {
				log.warn("entityManagerFactory was closed.  Re-initializing");
				entityManagerFactory = null;
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

		try {
			entityManagerFactory = Persistence.createEntityManagerFactory("org.cryptocoinpartners.schema", properties);
			ensureSingletonsExist();
		} catch (Throwable t) {
			if (entityManagerFactory != null) {
				entityManagerFactory.close();
				entityManagerFactory = null;
			}
			throw new Error("Could not initialize db", t);
		}
	}

	private static void ensureSingletonsExist() {
		// Touch the singleton holders
		Currencies.BTC.getSymbol(); // this should load all the singletons in Currencies
		Exchanges.BITFINEX.getSymbol(); // this should load all the singletons in Exchanges
	}

	@Override
	public String call() throws Exception {
		EntityManager em = null;
		try {
			em = createEntityManager();
			EntityTransaction transaction = em.getTransaction();
			transaction.begin();
			try {
				for (EntityBase entity : entities)
					em.persist(entity);
				transaction.commit();
			} catch (RuntimeException e) {
				if (transaction != null)
					transaction.rollback();
				throw e;
			}

		} finally {

			if (em != null)
				em.close();

		}
		return "Complete";
	}

	private static EntityManagerFactory entityManagerFactory;
	private static final int defaultBatchSize = 20;
	static ExecutorService service = Executors.newSingleThreadScheduledExecutor();
	private final EntityBase[] entities;

}
