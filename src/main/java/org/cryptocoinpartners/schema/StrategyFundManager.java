package org.cryptocoinpartners.schema;

import org.apache.commons.configuration.Configuration;

import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;


/**
 * StrategyFundManager represents an instance of a Strategy with a specific configuration and its own deposit Fund.
 *
 * @author Tim Olson
 */
@Entity
public class StrategyFundManager extends FundManager {


    public StrategyFundManager(String moduleName) {
        super( moduleName+" fund" );
        this.moduleName = moduleName;
    }


    public StrategyFundManager(String moduleName, Map<String, String> config) {
        super( moduleName+" Fund" );
        this.moduleName = moduleName;
        this.config = config;
    }


    public StrategyFundManager(String moduleName, Configuration configuration) {
        super( moduleName+" Fund" );
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
    protected StrategyFundManager() { }
    protected void setModuleName(String moduleName) { this.moduleName = moduleName; }
    protected void setConfig(Map<String, String> config) { this.config = config; }


    private String moduleName;
    private Map<String,String> config;
}
