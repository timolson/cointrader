package org.cryptocoinpartners.util;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.FlushModeType;
import javax.persistence.Persistence;
import javax.persistence.Query;
import javax.persistence.TypedQuery;

import org.cryptocoinpartners.schema.EntityBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PersistUtilHelper {

	private static EntityManagerFactory emf;
	private static final ThreadLocal<EntityManager> threadLocal = new ThreadLocal<EntityManager>();
	private static Map<String, EntityManager> entityManagers = new ConcurrentHashMap<String, EntityManager>();
	protected static Logger log = LoggerFactory.getLogger("org.cryptocoinpartners.persistUtilHelper");

	PersistUtilHelper(Map<String, String> properties) {
		emf = Persistence.createEntityManagerFactory("org.cryptocoinpartners.schema", properties);
	}

	public static EntityManagerFactory getEntityManagerFactory() {
		return emf;
	}

	public static EntityManager getEntityManager() {
		EntityManager em = threadLocal.get();
		if (em == null || !em.isOpen()) {
			em = emf.createEntityManager();
			entityManagers.put(em.toString(), em);
			threadLocal.set(em);
		}
		return em;
	}

	public static <T> TypedQuery<T> createQuery(String qlString, Class<T> resultClass) {
		//getEntityManager().clear();
		// props.put("javax.persistence.cache.retrieveMode", "BYPASS");

		//getEntityManager().setProperty("javax.persistence.cache.storeMode", "BYPASS");
		// getEntityManager().setProperty("javax.persistence.cache.retrieveMode", CacheRetrieveMode.BYPASS);

		return getEntityManager().createQuery(qlString, resultClass);
	}

	public static Query createQuery(String qlString) {
		return getEntityManager().createQuery(qlString);
	}

	public static void closeEntityManager() {
		EntityManager em = threadLocal.get();
		if (em != null) {
			entityManagers.remove(em.toString());
			try {
				em.close();
			} catch (Exception | Error e) {
				log.error("Threw a Execpton or Error, full stack trace follows:", e);

				e.printStackTrace();

			}

			threadLocal.set(null);
		}
	}

	public static void clearEntityManager() {
		EntityManager em = threadLocal.get();
		if (em != null) {
			em.clear();
		}
	}

	public static void closeEntityManagerFactory() {
		emf.close();
		//  emf = null;
	}

	public static void beginTransaction() {
		getEntityManager().getTransaction().begin();
	}

	public static void rollback() {
		getEntityManager().getTransaction().rollback();
	}

	public static void commit() {
		getEntityManager().getTransaction().commit();
	}

	public static void detach(Object entity) {
		Iterator it = entityManagers.values().iterator();

		while (it.hasNext()) {
			EntityManager em = (EntityManager) it.next();
			if (em == null)
				return;
			else if (em.isOpen())
				em.detach(entity);
		}
	}

	public static void evict(Object entity) {
		Iterator it = entityManagers.values().iterator();

		while (it.hasNext()) {
			EntityManager em = (EntityManager) it.next();
			if (em == null)
				return;
			else if (em.isOpen())
				em.getEntityManagerFactory().getCache().evict(entity.getClass(), ((EntityBase) entity).getId());
			//em.getEntityManagerFactory().createEntityManager(SynchronizationType.)

		}
	}

	public static void merge(Object entity) {
		Iterator it = entityManagers.values().iterator();

		while (it.hasNext()) {
			EntityManager em = (EntityManager) it.next();
			if (em == null)
				return;
			else if (em.isOpen())
				if (em.find(entity.getClass(), ((EntityBase) entity).getId()) != null)
					em.merge(entity);
		}
	}

	public static void refresh(Object entity) {
		Iterator it = entityManagers.values().iterator();
		Object parent = null;
		while (it.hasNext()) {
			EntityManager em = (EntityManager) it.next();
			Object mergedEntity = null;
			Object rootEntity = null;
			Map<String, Object> props = new HashMap<String, Object>();

			if (em == null)
				return;
			else if (em.isOpen())
				em.refresh(entity);
			parent = em.find(entity.getClass(), ((EntityBase) entity).getId(), props);

			rootEntity = em.getReference(entity.getClass(), ((EntityBase) entity).getId());
			//rootEntity = em.find(entity.getClass(), ((EntityBase) entity).getId());

			//if (em.find(entity.getClass(), ((EntityBase) entity).getId()) != null)
			///em.re
			em.refresh(rootEntity);
			em.persist(rootEntity);
			//em.merge(rootEntity);
			//em.flush();

			//em.refresh(mergedEntity);

		}
	}

	public static void find(Object entity) {
		Iterator it = entityManagers.values().iterator();
		while (it.hasNext()) {
			EntityManager em = (EntityManager) it.next();
			FlushModeType flushMode;
			Object rootEntity = null;
			Map<String, Object> props = new HashMap<String, Object>();
			Object parent;
			//Object Object;
			if (em == null)
				return;
			else if (em.isOpen())
				parent = em.find(entity.getClass(), ((EntityBase) entity).getId(), props);

			rootEntity = em.getReference(entity.getClass(), ((EntityBase) entity).getId());
			{
				em.refresh(rootEntity);
				em.refresh(entity);

				em.detach(rootEntity);
			}

			//em.
			//em.refresh(rootEntity);
			//em.getEntityManagerFactory().unwrap(cls).
			//  flushMode = em.getFlushMode();
			//em.setFlushMode(FlushModeType.)
			//if (em.find(entity.getClass(), ((EntityBase) entity).getId()) != null)
			//  em.merge(entity);

		}
	}

	public static boolean isActive() {
		return getEntityManager().getTransaction().isActive();

	}

	public static boolean isOpen() {
		return emf.isOpen();
	}

	public static void evictAll() {
		emf.getCache().evictAll();

	}

	public static void reset() {
		emf = null;
		// TODO Auto-generated method stub

	}

}
