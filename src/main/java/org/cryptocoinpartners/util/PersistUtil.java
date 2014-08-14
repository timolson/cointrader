package org.cryptocoinpartners.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.LockTimeoutException;
import javax.persistence.NoResultException;
import javax.persistence.NonUniqueResultException;
import javax.persistence.Persistence;
import javax.persistence.PersistenceException;
import javax.persistence.PessimisticLockException;
import javax.persistence.Query;
import javax.persistence.QueryTimeoutException;
import javax.persistence.TransactionRequiredException;
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

		Future<String> futureResult = service.submit(persistanceThread);
		try {
			//TODO have user configured timeout
			//futureResult.get(30, TimeUnit.SECONDS);
			futureResult.get();
			//		} catch (TimeoutException e) {
			//			log.error("Could not insert entity into Cointrader Db");
			//			futureResult.cancel(true);
		} catch (InterruptedException e1) {
			log.error("Cointrader Database Peristnace had an error, the details are:  {}.", e1);

		} catch (ExecutionException e2) {
			log.info("Cointrader Database Peristnace had an error, the details are: {}.", e2);

		}
		//service.shutdown();
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
			} catch (Error t) {
				transaction.rollback();
				throw t;
			} catch (NoResultException e) {
				//- if there is no result}
			} catch (NonUniqueResultException e) {
				//- if more than one result
			} catch (IllegalStateException e) {
				//- if called for a Java Persistence query language UPDATE or DELETE statement
			} catch (QueryTimeoutException e) {
				// - if the query execution exceeds the query timeout value set and only the statement is rolled back
			} catch (TransactionRequiredException e) {
				// - if a lock mode has been set and there is no transaction
			} catch (PessimisticLockException e) {
				//- if pessimistic locking fails and the transaction is rolled back
			} catch (LockTimeoutException e) {
				// - if pessimistic locking fails and only the statement is rolled back
			} catch (PersistenceException e) {
				// - if the query execution exceeds the query timeout value set and the transaction is rolled back
			}
			return "Complete";
		} finally {

			if (em != null)
				em.close();

		}

	}

	private static EntityManagerFactory entityManagerFactory;
	private static final int defaultBatchSize = 20;
	static ExecutorService service = Executors.newFixedThreadPool(100);
	private final EntityBase[] entities;

}
