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
import org.cryptocoinpartners.util.PersistUtil;

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

    @ElementCollection
    public Map<String, String> getConfig() {
        return config;
    }

    @Override
    public void afterAttach(final Context context) {
        // attach the actual Strategy instead of this StrategyInstance
        // Set ourselves as the StrategyInstance
        //      context.loadStatements("BasicPortfolioService");
        strategy = context.attach(moduleName, new MapConfiguration(config), new Module()

        {
            @Override
            public void configure(Binder binder) {
                binder.bind(StrategyInstance.class).toInstance(StrategyInstance.this);
                Portfolio portfolio = Portfolio.findOrCreate(getModuleName());
                if (portfolio == null) {
                    portfolio = context.getInjector().getInstance(Portfolio.class);
                    portfolio.setName(getModuleName());
                    PersistUtil.insert(portfolio);
                }

                else
                    binder.bind(Portfolio.class).toInstance(portfolio);

                //  portfolio = context.getInjector().getInstance(Portfolio.class);
                //portfolio.setName(getModuleName());
                portfolio.setManager(StrategyInstance.this);
                StrategyInstance.this.setPortfolio(portfolio);
                // binder.bind(Portfolio.class).toInstance(portfolio);

                PortfolioService portfolioService = context.getInjector().getInstance(PortfolioService.class);
                portfolioService.setPortfolio(portfolio);
                //  portfolio.setName(getModuleName());
                //  portfolio.setManager(this);
                // binder.bind(Portfolio.class).toInstance(context.getInjector().getInstance(Portfolio.class));
                // binder.bind(PortfolioService.class).toInstance(context.getInjector().getInstance(PortfolioService.class));

                //              context.loadStatements("Portfolio");

                // Need to rebuild state.

            }
        });

    }

    // JPA
    protected StrategyInstance() {
    }

    protected void setModuleName(String moduleName) {
        this.moduleName = moduleName;
    }

    protected void setConfig(Map<String, String> config) {
        this.config = config;
    }

    private String moduleName;
    private Map<String, String> config;
    private Object strategy;
}
