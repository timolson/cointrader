package org.cryptocoinpartners.module;

import org.cryptocoinpartners.schema.Balance;
import org.cryptocoinpartners.schema.Currency;
import org.cryptocoinpartners.schema.Exchange;
import org.cryptocoinpartners.schema.Holding;
import org.cryptocoinpartners.schema.Listing;
import org.cryptocoinpartners.schema.Market;
import org.cryptocoinpartners.schema.Prompt;
import org.cryptocoinpartners.schema.SyntheticMarket;
import org.cryptocoinpartners.util.EM;
import org.cryptocoinpartners.util.IoUtil;

import com.google.inject.AbstractModule;

public class StaticInjectionModule extends AbstractModule {

	//private static final ThreadLocal<EntityManager> ENTITY_MANAGER_CACHE = new ThreadLocal<EntityManager>();

	@Override
	public void configure() {
		//  @Override
		//public void configure(Binder binder) {
		//        int retryCount = ConfigUtil.combined().getInt("db.persist.retry");
		//        Properties properties = new Properties();
		//        //Map<String, String> properties = new HashMap<String, String>();
		//
		//        retryCount = ConfigUtil.combined().getInt("db.persist.retry");
		//
		//        properties.put("hibernate.hbm2ddl.auto", "update");
		//        properties.put("hibernate.connection.driver_class", ConfigUtil.combined().getString("db.driver"));
		//        properties.put("hibernate.dialect", ConfigUtil.combined().getString("db.dialect"));
		//        properties.put("hibernate.connection.url", ConfigUtil.combined().getString("db.url"));
		//        properties.put("hibernate.connection.username", ConfigUtil.combined().getString("db.username"));
		//        properties.put("hibernate.connection.password", ConfigUtil.combined().getString("db.password"));
		//        properties.put("hibernate.ejb.naming_strategy", "org.hibernate.cfg.ImprovedNamingStrategy");
		//        properties.put("hibernate.connection.autocommit", "true");
		//        properties.put("hibernate.connection.autocommit", "true");
		//        properties.put("hibernate.connection.release_mode", "auto");
		//        properties.put("hibernate.connection.provider_class", "org.hibernate.connection.C3P0ConnectionProvider");
		//        properties.put("hibernate.cache.region.factory_class", "org.hibernate.cache.ehcache.SingletonEhCacheRegionFactory");
		//        properties.put("hibernate.cache.use_second_level_cache", "true");
		//        properties.put("hibernate.cache.use_query_cache", "true");
		//        properties.put("hibernate.c3p0.min_size", "10");
		//        properties.put("hibernate.c3p0.max_size", ConfigUtil.combined().getString("db.pool.size"));
		//        properties.put("hibernate.c3p0.acquire_increment", ConfigUtil.combined().getString("db.pool.growth"));
		//        properties.put("show_sql", "true");
		//        properties.put("format_sql", "true");
		//        properties.put("use_sql_comments", "true");
		//        //   properties.put("hibernate.c3p0.debugUnreturnedConnectionStackTraces", "true");
		//        // properties.put("hibernate.c3p0.unreturnedConnectionTimeout", "120");
		//        //    properties.put("hibernate.c3p0.idle_test_period", ConfigUtil.combined().getString("db.idle.test.period"));
		//        //  properties.put("hibernate.c3p0.max_statements", "0");
		//        // properties.put("hibernate.c3p0.maxIdleTimeExcessConnections", "2");
		//        //  properties.put("hibernate.c3p0.timeout", "300");
		//        //properties.put("hibernate.c3p0.checkoutTimeout", "500");
		//        properties.put("hibernate.c3p0.preferredTestQuery", "SELECT 1 from exchange");
		//        //  properties.put("hibernate.c3p0.maxConnectionAge", ConfigUtil.combined().getString("db.max.connection.age"));
		//        final String testConnection = ConfigUtil.combined().getString("db.test.connection");
		//        properties.put("hibernate.c3p0.testConnectionOnCheckin", testConnection);
		//        properties.put("hibernate.c3p0.acquireRetryDelay", "1000");
		//        properties.put("hibernate.c3p0.acquireRetryAttempts", "0");
		//        properties.put("hibernate.c3p0.breakAfterAcquireFailure", "false");
		//        properties.put("hibernate.c3p0.checkoutTimeout", "10000");
		//        properties.put("hibernate.c3p0.idleConnectionTestPeriod", "100");
		//
		//        properties.put("javax.persistence.sharedCache.mode", "ENABLE_SELECTIVE");
		//
		//        JpaPersistModule jpa = new JpaPersistModule("org.cryptocoinpartners.schema");
		//        jpa.properties(properties);
		//        jpa.configure(binder());

		requestStaticInjection(Market.class);
		requestStaticInjection(SyntheticMarket.class);
		requestStaticInjection(Exchange.class);
		requestStaticInjection(Listing.class);
		requestStaticInjection(Balance.class);
		requestStaticInjection(Prompt.class);
		requestStaticInjection(Currency.class);
		requestStaticInjection(Holding.class);
		// requestStaticInjection(FeesUtil.class);
		// requestStaticInjection(Portfolio.class);
		//requestStaticInjection(Transaction.class);
		//requestStaticInjection(Fill.class);
		//requestStaticInjection(OrderUpdate.class);
		requestStaticInjection(EM.class);
		// requestStaticInjection(Market.class);
		//  requestStaticInjection(Exchange.class);
		//requestStaticInjection(Listing.class);
		// requestStaticInjection(Prompt.class);
		//  requestStaticInjection(Currency.class);
		// requestStaticInjection(Replay.class);
		// requestStaticInjection(Book.class);
		//requestStaticInjection(Trade.class);
		// requestStaticInjection(Bar.class);
		// requestStaticInjection(SaveMarketData.class);
		requestStaticInjection(IoUtil.class);
		// requestStaticInjection(BaseStrategy.class);
		// requestStaticInjection(Portfolio.class);
		//requestStaticInjection(Transaction.class);
		// requestStaticInjection(Fill.class);
		//requestStaticInjection(OrderUpdate.class);

		//  install(new FactoryModuleBuilder().build(GeneralOrderFactory.class));
		//install(new FactoryModuleBuilder().build(SpecificOrderFactory.class));
		// bind(SpecificOrderFactory.class).to(SpecificOrderFactory.class);

		// .build(SpecificOrderFactory.class));
		// install(new FactoryModuleBuilder().implement(Serializable.class, GeneralOrder.class).build(GeneralOrderFactory.class));

	}

	//    @Provides
	//    @Singleton
	//    public EntityManagerFactory provideEntityManagerFactory() {
	//
	//        //   createMode = "update";
	//              return Persistence.createEntityManagerFactory("org.cryptocoinpartners.schema", properties);
	//    }

	//    @Provides
	//    public EntityManager provideEntityManager(EntityManagerFactory entityManagerFactory) {
	//        //  EntityManager entityManager = ENTITY_MANAGER_CACHE.get();
	//        //if (entityManager == null) {
	//        //   ENTITY_MANAGER_CACHE.set(entityManager = entityManagerFactory.createEntityManager());
	//        // }
	//        // return entityManager;
	//    }

}
