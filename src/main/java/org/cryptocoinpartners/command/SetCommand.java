package org.cryptocoinpartners.command;

import java.io.IOException;
import java.io.StringReader;
import java.util.Map;
import java.util.Properties;

import javax.inject.Inject;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.lang.StringUtils;
import org.cryptocoinpartners.util.ConfigUtil;

/**
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
public class SetCommand extends CommandBase {

    @Override
    public String getUsageHelp() {
        return "set [{key}={value}]";
    }

    @Override
    public String getExtraHelp() {
        return "When invoked without an argument, 'set' lists all the current settings.  When an argument is passed, 'set' parses the argument as a line in a Properties file and attempts to set the Configuration property as specified.  Setting a key to a blank value overrides the value to be empty.  Use 'unset' to remove an overridden setting and revert to the default value for a key.";
    }

    @Override
    public void parse(String commandArguments) {
        arg = commandArguments;
    }

    @Override
    public Object call() {
        if (StringUtils.isBlank(arg))
            dumpConfig();
        else
            setProperty();
        return true;
    }

    private void setProperty() {
        Properties props = new Properties();
        try {
            props.load(new StringReader(arg));
        } catch (IOException e) {
            out.println("Could not parse property setting.");
        }
        for (Map.Entry<Object, Object> entry : props.entrySet()) {
            String key = entry.getKey().toString();
            String value = entry.getValue().toString();
            config.setProperty(key, value);
            out.println("Set " + key);
        }
    }

    private void dumpConfig() {
        out.println(ConfigUtil.asString(config));
    }

    @Inject
    private Configuration config;
    private String arg;
}
