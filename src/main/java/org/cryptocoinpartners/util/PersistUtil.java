package org.cryptocoinpartners.util;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

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
	//	private static EntityManagerFactory entityManagerFactory;
	private static final ThreadLocal<EntityManager> threadLocal;

	private static final int defaultBatchSize = 20;
	private static boolean running = false;
	private static boolean shutdown = false;
	private static ExecutorService service;
	private static FutureTask persitanceTask = null;

	static {
		threadLocal = new ThreadLocal<EntityManager>();
	}
	//private final EntityBase[] entities;

	private static final BlockingQueue<EntityBase[]> blockingQueue = new ArrayBlockingQueue<EntityBase[]>(10000);

	private PersistUtil(EntityBase... entities) {
	}

	public static void persit(EntityBase... entities) {
		EntityManager em = null;
		boolean persited = true;
		try {
			em = createEntityManager();
			PersistUtilHelper.beginTransaction();

			try {
				for (EntityBase entity : entities)
					em.persist(entity);
				PersistUtilHelper.commit();
			} catch (RuntimeException e) {
				persited = false;
				e.printStackTrace();
				for (EntityBase entity : entities)
					log.error(entity.getClass().getSimpleName() + ": " + entity.getId().toString() + " not saved to database");
				if (PersistUtilHelper.isActive())
					PersistUtilHelper.rollback();

			}

			catch (Error t) {
				PersistUtilHelper.rollback();
				throw t;
			}
		} finally {
			if (persited)
				for (EntityBase entity : entities) {

					log.debug(entity.getClass().getSimpleName() + ": " + entity.getId().toString() + " saved to database");
				}
			if (em != null)
				PersistUtilHelper.closeEntityManager();
		}
	}

	private static void persit(EntityManager em, EntityBase... entities) {
		boolean persited = true;
		try {
			PersistUtilHelper.beginTransaction();
			try {
				for (EntityBase entity : entities)
					if (em.find(entity.getClass(), entity.getId()) != null) {
						em.merge(entity);
					} else {
						em.persist(entity);
					}

				PersistUtilHelper.commit();
			} catch (RuntimeException e) {
				persited = false;
				e.printStackTrace();
				for (EntityBase entity : entities)
					log.error(entity.getClass().getSimpleName() + ": " + entity.getId().toString() + " not saved to database");
				if (PersistUtilHelper.isActive())
					PersistUtilHelper.rollback();

			}

			if (persited)
				for (EntityBase entity : entities) {

					log.debug(entity.getClass().getSimpleName() + ": " + entity.getId().toString() + " saved to database");
				}

		} finally {

		}
	}

	public static void insert(EntityBase... entities) {

		if (!shutdown && running && persitanceTask == null) {
			//EntityManager em = createEntityManager();

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
			//			for (EntityBase entity : entities)
			//				log.debug(entity.getClass().getSimpleName() + ": " + entity.getId().toString() + " Placed on persitance queue");

		}
		//persit(entities);
		//	PersistUtil persistanceThread = new PersistUtil(entities);
		//FutureTask<String> futureTask1 = new FutureTask<String>(persistanceThread);
		// FutureTask<String> futureTask2 = new FutureTask<String>(callable2);
		else if (!running) {
			EntityManager em = null;
			try {
				em = createEntityManager();
				for (EntityBase entity : entities)
					//em.merge(entity);
					persit(em, entities);
			} finally {
				if (em != null)
					PersistUtilHelper.closeEntityManager();
			}

		} else {

			try {

				blockingQueue.put(entities);
				//	for (EntityBase entity : entities)
				//		log.debug(entity.getClass().getSimpleName() + ": " + entity.getId().toString() + " Placed on persitance queue");
				//service.execute(futureTask1);

				//if (futureTask1.isDone()) {

				//	}

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
				PersistUtilHelper.closeEntityManager();
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
				PersistUtilHelper.closeEntityManager();
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
				PersistUtilHelper.closeEntityManager();
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
				PersistUtilHelper.closeEntityManager();
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
				PersistUtilHelper.closeEntityManager();
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
			service.shutdown();
			try {
				service.awaitTermination(10, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			running = false;

		}
		if (PersistUtilHelper.getEntityManagerFactory() != null)
			PersistUtilHelper.closeEntityManagerFactory();

	}

	private static void init(boolean resetDatabase) {
		if (PersistUtilHelper.getEntityManagerFactory() != null) {
			if (!PersistUtilHelper.isOpen()) {
				log.warn("entityManagerFactory was closed.  Re-initializing");
				//entityManagerFactory = null;
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
			//entityManagerFactory = Persistence.createEntityManagerFactory("org.cryptocoinpartners.schema", properties);

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

	public static void purgeTransactions() {

		EntityManager em = null;
		try {
			//	em = createEntityManager();
			//EntityTransaction transaction = em.getTransaction();

			//	transaction.begin();

			//Session session = em.unwrap(Session.class);
			//session.createSQLQuery("delete from transaction").executeUpdate();
			//session.createSQLQuery("delete from order_fills").executeUpdate();
			//session.createSQLQuery("delete from fill").executeUpdate();
			//session.createSQLQuery("delete from specific_order").executeUpdate();
			//session.createSQLQuery("delete from order_children").executeUpdate();
			//session.createSQLQuery("delete from general_order").executeUpdate();
			//transaction.commit();
		} catch (RuntimeException e) {
			e.printStackTrace();

		} finally {

			if (em != null)
				PersistUtilHelper.closeEntityManager();

		}

		//EntityTransaction transaction = em.getTransaction();

		//			transaction.begin();
		//			try {
		//				em.createQuery("delete from Fill f");
		//				em.createQuery("delete from SpecificOrder s");
		//				em.createQuery("delete from GeneralOrder g");
		//				em.createQuery("delete from Transaction t");
		//
		//				transaction.commit();
		//			} catch (RuntimeException e) {
		//				e.printStackTrace();
		//				if (transaction.isActive())
		//					transaction.rollback();
		//
		//			}
		//
		//		} finally {
		//
		//			if (em != null)
		//				closeEntityManager();();
		//
		//		}
	}

	public static byte[] convert(String uuidAsString) {
		UUID u = UUID.fromString(uuidAsString);
		ByteBuffer bb = ByteBuffer.allocate(16);
		bb.putLong(u.getMostSignificantBits()).putLong(u.getLeastSignificantBits());
		return bb.array();
	}

	@Override
	public void run() {
		EntityBase[] entities = null;
		PersistUtilHelper.evictAll();
		//EntityManager em = null;
		//		boolean persited;
		//		//let's reset teh cache
		//		entityManagerFactory.getCache().evictAll();
		//
		//		//EntityManagerFactory emf = entityManager.getEntityManagerFactory();
		EntityManager em = PersistUtilHelper.getEntityManager();

		while (!shutdown) {

			try {
				entities = blockingQueue.take();
				boolean peristed = true;

				try {
					PersistUtilHelper.beginTransaction();
					for (EntityBase entity : entities)
						if (em.find(entity.getClass(), entity.getId()) != null) {
							em.merge(entity);
						} else {

							em.persist(entity);
						}
					PersistUtilHelper.commit();
				} catch (Exception e) {
					peristed = false;
					e.printStackTrace();
					if (peristed)
						for (EntityBase entity : entities)
							log.error(entity.getClass().getSimpleName() + ": " + entity.getId().toString() + " not saved to database");
					if (PersistUtilHelper.isActive())
						PersistUtilHelper.rollback();
					//rollback();
				} finally {

					if (peristed)
						for (EntityBase entity : entities)
							log.debug(entity.getClass().getSimpleName() + ": " + entity.getId().toString() + " saved to database");
				}

			} catch (InterruptedException e) {
				//if (blockingQueue.isEmpty())
				//return;
				// TODO Auto-generated catch block

			}

		}
		PersistUtilHelper.closeEntityManager();

	}

	//	public static EntityManager getEntityManager() {
	//		EntityManager em = threadLocal.get();
	//
	//		if (em == null) {
	//			em = entityManagerFactory.createEntityManager();
	//			// set your flush mode here 
	//			threadLocal.set(em);
	//		}
	//		return em;
	//	}
	//
	//	public static void closeEntityManager() {
	//		EntityManager em = threadLocal.get();
	//		if (em != null) {
	//			em.close();
	//			threadLocal.set(null);
	//		}
	//	}
	//
	//	public static void closeEntityManagerFactory() {
	//		entityManagerFactory.close();
	//	}
	//
	//	public static void beginTransaction() {
	//		getEntityManager().getTransaction().begin();
	//	}
	//
	//	public static void rollback() {
	//		getEntityManager().getTransaction().rollback();
	//	}
	//
	//	public static boolean isActive() {
	//		return getEntityManager().getTransaction().isActive();
	//	}
	//
	//	public static void commit() {
	//		getEntityManager().getTransaction().commit();
	//	}
}

//	@Override
//	public void run() {
//		EntityBase[] entities = null;
//		//EntityManager em = null;
//		boolean persited;
//		//let's reset teh cache
//		entityManagerFactory.getCache().evictAll();
//
//		//EntityManagerFactory emf = entityManager.getEntityManagerFactory();
//		while (!shutdown) {
//
//			
//				entities = blockingQueue.take();
//		        
//		}}

//				persited = true;
//				for (EntityBase entity : entities)
//					log.debug(entity.getClass().getSimpleName() + ": " + entity.getId().toString() + " Read from persitance queue");
//				EntityManager em = null;
//				em = createEntityManager();
//				EntityTransaction transaction = em.getTransaction();
//				transaction.begin();
//				for (EntityBase entity : entities) {
//					//			if (em.find(entity.getClass(), entity.getId()) != null) {
//					//			em.merge(entity);
//					//	} else {
//					//	if (!em.find(entity));
//					em.persist(entity);com
//				}
//				//}
//
//				transaction.commit();
//			} catch (RuntimeException e) {
//				persited = false;
//				e.printStackTrace();
//				for (EntityBase entity : entities)
//					log.error(entity.getClass().getSimpleName() + ": " + entity.getId().toString() + " not saved to database");
//				if (transaction.isActive())
//					transaction.rollback();
//
//			} finally {
//				if (em != null)
//					closeEntityManager();();
//				if (persited)
//					for (EntityBase entity : entities)
//						log.error(entity.getClass().getSimpleName() + ": " + entity.getId().toString() + "  saved to database");
//
//			}

