package org.cryptocoinpartners.util;

import java.util.Map;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

public class PersistUtilHelper {

	private static EntityManagerFactory emf = null;
	private static final ThreadLocal<EntityManager> threadLocal;

	static {

		threadLocal = new ThreadLocal<EntityManager>();
	}

	PersistUtilHelper(Map<String, String> properties) {
		emf = Persistence.createEntityManagerFactory("org.cryptocoinpartners.schema", properties);
	}

	public static EntityManagerFactory getEntityManagerFactory() {

		return emf;
	}

	public static EntityManager getEntityManager() {
		EntityManager em = threadLocal.get();

		if (em == null) {
			em = emf.createEntityManager();
			// set your flush mode here 
			threadLocal.set(em);
		}
		return em;
	}

	public static void closeEntityManager() {
		EntityManager em = threadLocal.get();
		if (em != null) {
			if (em.isOpen())
				em.close();
			threadLocal.set(null);
		}
	}

	public static void closeEntityManagerFactory() {
		emf.close();
		emf = null;
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

	public static boolean isActive() {
		return getEntityManager().getTransaction().isActive();

	}

	public static boolean isOpen() {
		return emf.isOpen();
	}

	public static void evictAll() {
		emf.getCache().evictAll();

	}

}
