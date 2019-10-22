package org.cryptocoinpartners.module;

import java.lang.management.ManagementFactory;

import javax.management.MBeanServer;

import org.cryptocoinpartners.schema.BalanceFactory;
import org.cryptocoinpartners.schema.BarFactory;
import org.cryptocoinpartners.schema.BookFactory;
import org.cryptocoinpartners.schema.CurrencyFactory;
import org.cryptocoinpartners.schema.ExchangeFactory;
import org.cryptocoinpartners.schema.FillFactory;
import org.cryptocoinpartners.schema.GeneralOrderFactory;
import org.cryptocoinpartners.schema.ListingFactory;
import org.cryptocoinpartners.schema.MarketFactory;
import org.cryptocoinpartners.schema.OrderUpdateFactory;
import org.cryptocoinpartners.schema.PositionFactory;
import org.cryptocoinpartners.schema.ReplayFactory;
import org.cryptocoinpartners.schema.SpecificOrderFactory;
import org.cryptocoinpartners.schema.SyntheticMarketFactory;
import org.cryptocoinpartners.schema.TradeFactory;
import org.cryptocoinpartners.schema.TransactionFactory;
import org.cryptocoinpartners.schema.dao.BalanceDao;
import org.cryptocoinpartners.schema.dao.BalanceJpaDao;
import org.cryptocoinpartners.schema.dao.BarDao;
import org.cryptocoinpartners.schema.dao.BarJpaDao;
import org.cryptocoinpartners.schema.dao.BookDao;
import org.cryptocoinpartners.schema.dao.BookJpaDao;
import org.cryptocoinpartners.schema.dao.CurrencyDao;
import org.cryptocoinpartners.schema.dao.CurrencyJpaDao;
import org.cryptocoinpartners.schema.dao.ExchangeDao;
import org.cryptocoinpartners.schema.dao.ExchangeJpaDao;
import org.cryptocoinpartners.schema.dao.FillDao;
import org.cryptocoinpartners.schema.dao.FillJpaDao;
import org.cryptocoinpartners.schema.dao.HoldingDao;
import org.cryptocoinpartners.schema.dao.HoldingJpaDao;
import org.cryptocoinpartners.schema.dao.ListingDao;
import org.cryptocoinpartners.schema.dao.ListingJpaDao;
import org.cryptocoinpartners.schema.dao.OrderDao;
import org.cryptocoinpartners.schema.dao.OrderJpaDao;
import org.cryptocoinpartners.schema.dao.OrderUpdateDao;
import org.cryptocoinpartners.schema.dao.OrderUpdateJpaDao;
import org.cryptocoinpartners.schema.dao.PortfolioDao;
import org.cryptocoinpartners.schema.dao.PortfolioJpaDao;
import org.cryptocoinpartners.schema.dao.PositionDao;
import org.cryptocoinpartners.schema.dao.PositionJpaDao;
import org.cryptocoinpartners.schema.dao.PromptDao;
import org.cryptocoinpartners.schema.dao.PromptJpaDao;
import org.cryptocoinpartners.schema.dao.ReportDao;
import org.cryptocoinpartners.schema.dao.ReportJpaDao;
import org.cryptocoinpartners.schema.dao.TradeDao;
import org.cryptocoinpartners.schema.dao.TradeJpaDao;
import org.cryptocoinpartners.schema.dao.TradeableDao;
import org.cryptocoinpartners.schema.dao.TradeableJpaDao;
import org.cryptocoinpartners.schema.dao.TransactionDao;
import org.cryptocoinpartners.schema.dao.TransactionJpaDao;

import com.google.inject.AbstractModule;
import com.google.inject.assistedinject.FactoryModuleBuilder;

public class PersistanceModule extends AbstractModule {

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

		/* requestStaticInjection(Market.class);
		 requestStaticInjection(Exchange.class);
		 requestStaticInjection(Listing.class);
		 requestStaticInjection(Prompt.class);
		 requestStaticInjection(Currency.class);
		 requestStaticInjection(Portfolio.class);
		 requestStaticInjection(Transaction.class);
		 requestStaticInjection(Fill.class);
		 requestStaticInjection(OrderUpdate.class);
		*/

		//bind(Dao.class).to(DaoJpa.class);
		bind(MBeanServer.class).toInstance(ManagementFactory.getPlatformMBeanServer());
		//   bind(JMXManagerMBean.class).to(JMXManager.class).asEagerSingleton();

		bind(PortfolioDao.class).to(PortfolioJpaDao.class);
		bind(BookDao.class).to(BookJpaDao.class);
		bind(BarDao.class).to(BarJpaDao.class);
		bind(CurrencyDao.class).to(CurrencyJpaDao.class);
		bind(ExchangeDao.class).to(ExchangeJpaDao.class);
		bind(FillDao.class).to(FillJpaDao.class);
		bind(ListingDao.class).to(ListingJpaDao.class);
		//  bind(MarketDao.class).to(MarketJpaDao.class);
		bind(TradeableDao.class).to(TradeableJpaDao.class);
		bind(HoldingDao.class).to(HoldingJpaDao.class);
		bind(BalanceDao.class).to(BalanceJpaDao.class);
		//bind(MarketDataDao.class).to(MarketDataJpaDao.class);
		bind(OrderDao.class).to(OrderJpaDao.class);
		bind(PositionDao.class).to(PositionJpaDao.class);
		bind(PromptDao.class).to(PromptJpaDao.class);
		bind(TradeDao.class).to(TradeJpaDao.class);
		bind(TransactionDao.class).to(TransactionJpaDao.class);
		bind(OrderUpdateDao.class).to(OrderUpdateJpaDao.class);
		bind(ReportDao.class).to(ReportJpaDao.class);

		//  bind(OrderUpdateDao.class).to(OrderUpdateJpaDao.class);

		//  bind(FillDao.class).to(FillJpaDao.class);

		//bind(SpecificOrderFactory.class).to(SpecificOrderFactory.class);
		// install(new FactoryModuleBuilder().build(PortfolioFactory.class));
		install(new FactoryModuleBuilder().build(CurrencyFactory.class));
		install(new FactoryModuleBuilder().build(MarketFactory.class));
		install(new FactoryModuleBuilder().build(SyntheticMarketFactory.class));
		install(new FactoryModuleBuilder().build(GeneralOrderFactory.class));
		install(new FactoryModuleBuilder().build(SpecificOrderFactory.class));
		install(new FactoryModuleBuilder().build(FillFactory.class));
		install(new FactoryModuleBuilder().build(ExchangeFactory.class));
		install(new FactoryModuleBuilder().build(ListingFactory.class));
		install(new FactoryModuleBuilder().build(PositionFactory.class));
		install(new FactoryModuleBuilder().build(OrderUpdateFactory.class));
		install(new FactoryModuleBuilder().build(TransactionFactory.class));
		install(new FactoryModuleBuilder().build(ReplayFactory.class));
		install(new FactoryModuleBuilder().build(BookFactory.class));
		install(new FactoryModuleBuilder().build(TradeFactory.class));
		install(new FactoryModuleBuilder().build(BarFactory.class));
		install(new FactoryModuleBuilder().build(BalanceFactory.class));
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
