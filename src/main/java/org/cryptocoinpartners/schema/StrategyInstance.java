package org.cryptocoinpartners.schema;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.Transient;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.MapConfiguration;
import org.cryptocoinpartners.module.Context;
import org.cryptocoinpartners.service.PortfolioService;
import org.cryptocoinpartners.service.Strategy;
import org.cryptocoinpartners.util.ConfigUtil;

import com.google.inject.Binder;
import com.google.inject.Module;

/**
 * StrategyInstance represents an instance of a Strategy with a specific configuration and its own
 * deposit Portfolio.  When you attach a StrategyInstance to a Context, the StrategyInstance will create an instance
 * of the class given by the moduleName and configure it using this StrategyInstance's configuration settings.  The
 * instantiated Strategy may also bind using @Inject to fields for its trading Portfolio
 *
 * @author Tim Olson
 */
@Entity
//@Cacheable
public class StrategyInstance extends PortfolioManager implements Context.AttachListener {

	public StrategyInstance(String moduleName) {
		// super(moduleName + " portfolio");
		this.moduleName = moduleName;
	}

	public StrategyInstance(String moduleName, Map<String, String> config) {
		super(moduleName + " Portfolio");
		this.moduleName = moduleName;
		this.config = config;
	}

	public StrategyInstance(String moduleName, Configuration configuration) {
		super(moduleName + " Portfolio");
		this.moduleName = moduleName;
		this.config = new HashMap<>();
		Iterator keys = configuration.getKeys();
		while (keys.hasNext()) {
			String key = (String) keys.next();
			String value = configuration.getString(key);
			this.config.put(key, value);
		}
	}

	public String getModuleName() {
		return moduleName;
	}

	@Transient
	public Strategy getStrategy() {
		return (Strategy) strategy;
	}

	@Transient
	public void setStrategy(Object strategy) {
		this.strategy = strategy;
	}

	@ElementCollection
	public Map<String, String> getConfig() {
		return config;
	}

	@Override
	public void afterAttach(final Context context) {
		// attach the actual Strategy instead of this StrategyInstance
		// Set ourselves as the StrategyInstance
		//      context.loadStatements("BasicPortfolioService");

		portfolio = Portfolio.findOrCreate(getModuleName(), context);
		if (portfolio == null) {
			portfolio = context.getInjector().getInstance(Portfolio.class);
			portfolio.setName(getModuleName());
			portfolio.persit();
		} else {
			portfolio.setPersisted(true);
			log.info(this.getClass().getSimpleName() + ":afterAttach - " + context.getInjector().getInstance(Portfolio.class)
					+ " attched to injector. Binding portfolio:" + portfolio);
			context.getInjector().injectMembers(portfolio);
			log.info(this.getClass().getSimpleName() + ":afterAttach - " + context.getInjector().getInstance(Portfolio.class)
					+ " attched to injector. Bound portfolio:" + portfolio);
		}
		// set base currency
		if (portfolio.getBaseAsset() == null) {
			Asset baseAsset = Asset.forSymbol(portfolio.getContext().getConfig().getString("base.symbol", "USD"));
			portfolio.setBaseAsset(baseAsset);
		}
		if (portfolio.getBaseNotionalBalanceCount() == 0) {
			DiscreteAmount baseNotionalBalance = new DiscreteAmount(
					(long) (ConfigUtil.combined().getLong("base.notional.balance", 100000) / (portfolio.getBaseAsset().getBasis())),
					portfolio.getBaseAsset().getBasis());
			portfolio.setBaseNotionalBalanceCount(baseNotionalBalance.getCount());
			portfolio.getBaseNotionalBalance();
		}
		if (portfolio.getStartingBaseNotionalBalanceCount() == 0) {
			DiscreteAmount baseNotionalBalance = new DiscreteAmount(
					(long) (ConfigUtil.combined().getLong("base.notional.balance", 100000) / (portfolio.getBaseAsset().getBasis())),
					portfolio.getBaseAsset().getBasis());
			portfolio.setStartingBaseNotionalBalanceCount(baseNotionalBalance.getCount());
			portfolio.getStartingBaseNotionalBalance();
		}
		portfolio.merge();
		StrategyInstance.this.setPortfolio(portfolio);
		if (getStrategy() != null)
			getStrategy().setPortfolio(portfolio);
		context.getInjector().injectMembers(StrategyInstance.this);
		portfolio.setManager(StrategyInstance.this);
		portfolioService = context.getInjector().getInstance(PortfolioService.class);
		portfolioService.addPortfolio(portfolio);
		strategy = context.attach(moduleName, new MapConfiguration(config), new Module() {
			@Override
			public void configure(Binder binder) {
				binder.bind(Portfolio.class).toInstance(portfolio);
				binder.bind(StrategyInstance.class).toInstance(StrategyInstance.this);
			}
		});

		//((Strategy) strategy).setPortfolio(portfolio);

	}

	// JPA
	protected StrategyInstance() {
	}

	protected void setModuleName(String moduleName) {
		this.moduleName = moduleName;
	}

	@Override
	@Transient
	public Portfolio getPortfolio() {
		return portfolio;
	}

	protected void setConfig(Map<String, String> config) {
		this.config = config;
	}

	private String moduleName;
	// @Inject
	//private Portfolio portfolio;
	private Map<String, String> config;
	private Object strategy;
}
