package org.cryptocoinpartners.schema;

import org.apache.commons.configuration.Configuration;

import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;


/**
 * StrategyPortfolioManager represents an instance of a Strategy with a specific configuration and its own deposit Portfolio.
 *
 * @author Tim Olson
 */
@Entity
public class StrategyPortfolioManager extends PortfolioManager {


    public StrategyPortfolioManager(String moduleName) {
        super( moduleName+" portfolio" );
        this.moduleName = moduleName;
    }


    public StrategyPortfolioManager(String moduleName, Map<String, String> config) {
        super( moduleName+" Portfolio" );
        this.moduleName = moduleName;
        this.config = config;
    }


    public StrategyPortfolioManager(String moduleName, Configuration configuration) {
        super( moduleName+" Portfolio" );
        this.moduleName = moduleName;
        this.config = new HashMap<>();
        Iterator keys = configuration.getKeys();
        while( keys.hasNext() ) {
            String key = (String) keys.next();
            String value = configuration.getString(key);
            this.config.put(key,value);
        }
    }


    public String getModuleName() { return moduleName; }


    @ElementCollection
    public Map<String,String> getConfig() { return config; }


    // JPA
    protected StrategyPortfolioManager() { }
    protected void setModuleName(String moduleName) { this.moduleName = moduleName; }
    protected void setConfig(Map<String, String> config) { this.config = config; }


    private String moduleName;
    private Map<String,String> config;
}
