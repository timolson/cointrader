package org.cryptocoinpartners.command;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.lang.StringUtils;
import org.cryptocoinpartners.util.ConfigUtil;

import javax.inject.Inject;
import java.io.IOException;
import java.io.StringReader;
import java.util.Map;
import java.util.Properties;


/**
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
public class UnsetCommand extends CommandBase {

    @Override
    public String getUsageHelp() {
        return "unset {key}";
    }

    @Override
    public String getExtraHelp() {
        return "Removes the specified key from the Configuration.  This is different than setting the key to a blank value, since it allows the key to be set by parent Configurations, while setting a key to a blank value overrides the parent Configuration value with an empty setting.";
    }

    @Override
    public void parse(String commandArguments) {
        arg = commandArguments;
    }

    @Override
    public void run() {
        if( StringUtils.isBlank(arg) )
            dumpConfig();
        else
            setProperty();
    }

    private void setProperty() {
        Properties props = new Properties();
        try {
            props.load(new StringReader(arg));
        }
        catch( IOException e ) {
            out.println("Could not parse property setting.");
        }
        for( Map.Entry<Object, Object> entry : props.entrySet() ) {
            String key = entry.getKey().toString();
            String value = entry.getValue().toString();
            config.setProperty(key, value);
            out.println("Set "+key);
        }
    }


    private void dumpConfig() {
        out.println(ConfigUtil.asString(config));
    }


    @Inject
    private Configuration config;
    private String arg;
}
