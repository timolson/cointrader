package org.cryptocoinpartners.util;

import java.util.Properties;

import org.apache.commons.configuration.Configuration;
import org.cryptocoinpartners.module.PersistanceModule;

import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.persist.jpa.JpaPersistModule;

/**
 * Guice doesn't allow binding overrides so we have to store the Configuration separately and add it in just before instance creation My first time
 * using Guice... not a fan. Should have tried Pico.
 * 
 * @author Tim Olson
 */
public class Injector {

	public static Injector root() {
		return root;
	}

	public Injector createChildInjector(java.lang.Iterable<? extends com.google.inject.Module> iterable) {
		return new Injector(injector.createChildInjector(iterable), config);
	}

	public Injector createChildInjector(com.google.inject.Module... modules) {
		return new Injector(injector.createChildInjector(modules), config);
	}

	public <T> T getInstance(Class<T> cls) {
		T instance = ic().getInstance(cls);
		return instance;
	}

	public void injectMembers(Object o) {
		ic().injectMembers(o);
	}

	public Injector withConfig(Configuration config) {
		setConfig(config);
		return this;
	}

	private com.google.inject.Injector ic() {
		if (injectorWithConfig == null) {
			injectorWithConfig = injector.createChildInjector(new Module() {

				@Override
				public void configure(Binder binder) {
					binder.bind(Injector.class).toProvider(new Provider<Injector>() {
						@Override
						public Injector get() {
							return Injector.this;
						}

					});
					binder.bind(Configuration.class).toProvider(new Provider<Configuration>() {
						@Override
						public Configuration get() {
							return config;
						}
					});

				}

			});
		}
		return injectorWithConfig;
	}

	public Configuration getConfig() {
		return config;
	}

	public com.google.inject.Injector getInjector() {
		return injector;
	}

	public com.google.inject.Injector getInjectorWithConfig() {
		return injectorWithConfig;
	}

	public void setConfig(Configuration config) {
		this.config = config;
		injectorWithConfig = null;
	}

	private Injector(com.google.inject.Injector injector, Configuration config) {
		this.injector = injector;
		this.config = config;
	}

	private Configuration config;
	private final com.google.inject.Injector injector;
	private com.google.inject.Injector injectorWithConfig;

	private static Injector root;

	static {
		//  int retryCount = ConfigUtil.combined().getInt("db.persist.retry");
		Properties properties = new Properties();
		//Map<String, String> properties = new HashMap<String, String>();

		//daretryCount = ConfigUtil.combined().getInt("db.persist.retry");

		properties.put("hibernate.hbm2ddl.auto", "update");
		properties.put("show_sql", "true");
		properties.put("hibernate.connection.driver_class", ConfigUtil.combined().getString("db.driver"));
		properties.put("hibernate.dialect", ConfigUtil.combined().getString("db.dialect"));
		properties.put("hibernate.connection.url", ConfigUtil.combined().getString("db.url"));
		properties.put("hibernate.connection.username", ConfigUtil.combined().getString("db.username"));
		properties.put("hibernate.connection.password", ConfigUtil.combined().getString("db.password"));
		properties.put("hibernate.connection.zeroDateTimeBehavior", "CONVERT_TO_NULL");
		properties.put("hibernate.physical_naming_strategy", "org.cryptocoinpartners.util.PhysicalNamingStrategyImpl");
		properties.put("hibernate.connection.autocommit", "false");
		properties.put("hibernate.flushMode", "COMMIT");
		properties.put("hibernate.jdbc.fetch_size", ConfigUtil.combined().getString("db.fetch_size", "10000"));
		properties.put("hibernate.connection.release_mode", "auto");
		//properties.put("hibernate.jdbc.batch_size", ConfigUtil.combined().getString("db.batch_size", "1000000000"));
		//properties.put("hibernate.order_inserts", "true");
		//properties.put("hibernate.order_updates", "true");

		properties.put("hibernate.connection.provider_class", "org.hibernate.connection.C3P0ConnectionProvider");
		properties.put("hibernate.cache.region.factory_class", "org.hibernate.cache.ehcache.EhCacheRegionFactory");
		//	"org.hibernate.cache.ehcache.SingletonEhCacheRegionFactory");
		properties.put("hibernate.cache.use_second_level_cache", "false");

		properties.put("hibernate.cache.use_query_cache", "true");
		properties.put("hibernate.cache.use_structured_entries ", "false");
		properties.put("net.sf.ehcache.configurationResourceName", "META-INF/ehcache.xml");

		properties.put("hibernate.c3p0.min_size", "1");
		properties.put("hibernate.c3p0.max_size", ConfigUtil.combined().getString("db.pool.size"));
		properties.put("hibernate.c3p0.acquire_increment", ConfigUtil.combined().getString("db.pool.growth"));
		properties.put("hibernate.c3p0.max_statements", "50");

		properties.put("hibernate.showSql", "true");
		properties.put("hibernate.format_sql", "true");
		properties.put("hibernate.use_sql_comments", "true");
		properties.put("hibernate.c3p0.debugUnreturnedConnectionStackTraces", "true");
		//properties.put("hibernate.jdbc.fetch_size", "10000");
		// properties.put("hibernate.c3p0.unreturnedConnectionTimeout", "60");
		// properties.put("hibernate.c3p0.idle_test_period", "300");
		//  properties.put("hibernate.c3p0.max_statements", "0");
		// properties.put("hibernate.c3p0.maxIdleTimeExcessConnections", "2");
		// properties.put("hibernate.c3p0.timeout", "300");
		properties.put("hibernate.c3p0.maxIdleTime", ConfigUtil.combined().getString("db.max_idle_time", "0"));
		properties.put("hibernate.c3p0.idle_test_period", ConfigUtil.combined().getString("db.idle.test.period", "0"));

		//properties.put("hibernate.c3p0.checkoutTimeout", "500");
		properties.put("hibernate.c3p0.preferredTestQuery", "SELECT 1 from exchange");
		//  properties.put("hibernate.c3p0.maxConnectionAge", ConfigUtil.combined().getString("db.max.connection.age"));
		final String testConnection = ConfigUtil.combined().getString("db.test.connection", "false");
		properties.put("hibernate.c3p0.testConnectionOnCheckout", testConnection);
		// properties.put("hibernate.c3p0.testConnectionOnCheckin", testConnection);
		properties.put("hibernate.c3p0.acquireRetryDelay", ConfigUtil.combined().getString("db.acquire_retry_delay", "5000"));
		properties.put("hibernate.c3p0.acquireRetryAttempts", ConfigUtil.combined().getString("db.acquire_retry_attempts", "30"));
		properties.put("hibernate.c3p0.breakAfterAcquireFailure", ConfigUtil.combined().getString("db.break_after_acquire_failure", "false"));
		properties.put("hibernate.c3p0.checkoutTimeout", ConfigUtil.combined().getString("db.checkout_timeout", "0"));
		properties.put("hibernate.c3p0.idleConnectionTestPeriod", ConfigUtil.combined().getString("db.idle_connection_test_period", "0"));
		properties.put("hibernate.c3p0.numHelperThreads", ConfigUtil.combined().getString("db.num_helper_threads", "10"));
		properties.put("hibernate.c3p0.unreturnedConnectionTimeout", ConfigUtil.combined().getString("db.unreturned_connection_timeout", "0"));
		properties.put("hibernate.c3p0.statementCacheNumDeferredCloseThreads",
				ConfigUtil.combined().getString("db.statement_cache_num_deferred_close_threads", "1"));
		properties.put("hibernate.c3p0.maxAdministrativeTaskTime", ConfigUtil.combined().getString("db.max_administrative_task_time", "0"));

		properties.put("hibernate.c3p0.debugUnreturnedConnectionStackTraces",
				ConfigUtil.combined().getString("db.debug_unreturned_connection_stack_traces", "true"));
		//  properties.put("hibernate.c3p0.max_statements", "0");", "false"));. debug_unreturned_connection_stack_traces
		//  properties.put("hibernate.c3p0.max_statements", "0");
		properties.put("hibernate.c3p0.maxStatementsPerConnection", ConfigUtil.combined().getString("db.max_statements_per_connection", "0"));
		properties.put("hibernate.c3p0.statementCacheNumDeferredCloseThreads",
				ConfigUtil.combined().getString("db.statement_cache_num_deferred_close_threads", "1"));
		properties.put("hibernate.c3p0.maxStatements", ConfigUtil.combined().getString("db.max_statements", "0"));
		// properties.put("hibernate.c3p0.validate", "true");

		properties.put("javax.persistence.sharedCache.mode", "NONE");
		properties.put("javax.persistence.query.timeout", ConfigUtil.combined().getString("db.query_timeout", "9999999"));

		properties.put("javax.persistence.LockModeType", "OPTIMISTIC");

		// root = new Injector(Guice.createInjector(new LogInjector(), new PersistanceModule()), ConfigUtil.combined());
		//  new JpaPersistModule("org.cryptocoinpartners.schema")
		root = new Injector(
				Guice.createInjector(new LogInjector(), new JpaPersistModule("org.cryptocoinpartners.schema").properties(properties), new PersistanceModule()),
				ConfigUtil.combined());
	}
}
