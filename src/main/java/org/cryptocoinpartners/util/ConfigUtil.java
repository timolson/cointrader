package org.cryptocoinpartners.util;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.apache.commons.configuration.AbstractConfiguration;
import org.apache.commons.configuration.CombinedConfiguration;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.ConfigurationUtils;
import org.apache.commons.configuration.MapConfiguration;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.configuration.SystemConfiguration;
import org.apache.commons.configuration.tree.OverrideCombiner;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
public class ConfigUtil {

	public static CombinedConfiguration combined() {
		return combined;
	}

	public static PropertiesConfiguration defaults() {
		return defaultConfig;
	}

	public static PropertiesConfiguration user() {
		return userConfig;
	}

	public static PropertiesConfiguration buildtime() {
		return buildtimeConfig;
	}

	public static SystemConfiguration system() {
		return sysConfig;
	}

	public static MapConfiguration commandLine() {
		return clConfig;
	}

	/**
	 * Finds all non-static members tagged with @Config and populates them with the current combined() configuration
	 */
	public static void applyConfiguration(Object instance) {
		Class<?> cls = instance.getClass();
		for (Field field : cls.getFields()) {
			Config annotation = field.getAnnotation(Config.class);
			if (annotation != null)
				inject(combined(), instance, field, annotation);
		}
	}

	/**
	 * Examines injectee for any setters or fields marked with @Config, then sets those fields to values from the
	 * Configuration object.
	 */
	public static void applyConfiguration(Object injectee, Configuration config) {
		Class<?> cls = injectee.getClass();
		for (Field field : cls.getFields()) {
			Config annotation = field.getAnnotation(Config.class);
			if (annotation != null)
				inject((AbstractConfiguration) config, injectee, field, annotation);
		}
	}

	public static void init(String filename, Map<String, String> commandLine) throws ConfigurationException {
		boolean loadUserPropertiesFile = new File(filename).exists();
		if (!loadUserPropertiesFile)
			log.warn("Could not find configuration file \"" + filename + "\"");
		clConfig = new MapConfiguration(commandLine);
		sysConfig = new SystemConfiguration();
		if (loadUserPropertiesFile)
			userConfig = new PropertiesConfiguration(filename);
		else
			userConfig = new PropertiesConfiguration();
    URL defaultProps = ConfigUtil.class.getResource("/cointrader-default.properties");
		if (defaultProps == null)
			throw new ConfigurationException("Could not load cointrader-default.properties");
		defaultConfig = new PropertiesConfiguration(defaultProps);
		URL buildtimeProps = ConfigUtil.class.getResource("/org/cryptocoinpartners/buildtime.properties");
		if (buildtimeProps == null)
			throw new ConfigurationException("Could not load buildtime.properties");
		buildtimeConfig = new PropertiesConfiguration(buildtimeProps);
		combined = buildConfig(Collections.<AbstractConfiguration> emptyList());
		if (log.isDebugEnabled())
			log.debug("Combined Configuration:\n" + asString(combined));
	}

	public static CombinedConfiguration forModule(Object... keyValuePairs) {
		if (keyValuePairs.length % 2 != 0)
			throw new Error("Configuration parameters must be key-value pairs.  Found an odd number.");
		HashMap<String, Object> map = new HashMap<>();
		for (int i = 0; i < keyValuePairs.length; i++)
			map.put(keyValuePairs[i++].toString(), keyValuePairs[i]);
		return forModule(Collections.singletonList(new MapConfiguration(map)));
	}

	public static CombinedConfiguration forModule(Collection<? extends AbstractConfiguration> moduleConfigs) {
		CombinedConfiguration result = buildConfig(moduleConfigs);
		if (log.isDebugEnabled())
			log.debug("Module Configuration:\n" + asString(result));
		return result;
	}

	public static List<String> getPathProperty(String pathProperty) {
		CombinedConfiguration config = combined();
		return getPathProperty(config, pathProperty);
	}

	public static List<String> getPathProperty(CombinedConfiguration config, String pathProperty) {
		String modulePath = config.getString(pathProperty, "");
		List<String> paths = new ArrayList<>(Arrays.asList(modulePath.split(":")));
		paths.add("org.cryptocoinpartners.module");
		paths.remove("");
		return paths;
	}

	private static CombinedConfiguration buildConfig(Collection<? extends AbstractConfiguration> intermediateConfigs) {
		final CombinedConfiguration result = new CombinedConfiguration(new OverrideCombiner());
		result.addConfiguration(buildtimeConfig); // buildtime config cannot be overridden
		result.addConfiguration(clConfig);
		result.addConfiguration(sysConfig);
		for (AbstractConfiguration moduleConfig : intermediateConfigs)
			result.addConfiguration(moduleConfig);
		if (!userConfig.isEmpty())
			result.addConfiguration(userConfig);
		result.addConfiguration(defaultConfig);
		return result;
	}

	public static String asString(Configuration configuration) {
		StringWriter out = new StringWriter();
		PrintWriter pout = new PrintWriter(out);
		ConfigurationUtils.dump(configuration, pout);
		try {
			pout.close();
			out.close();
		} catch (IOException e) {
			throw new Error(e);
		}
		ArrayList<String> outLines = new ArrayList<>();
		for (String line : out.toString().split("\n")) {
			if (!isSecret(line))
				outLines.add(line);
		}
		Collections.sort(outLines);
		return StringUtils.join(outLines, "\n");
	}

	protected static boolean isSecret(String line) {
		return line.contains("password") || line.contains("secret");
	}

	private static void inject(@Nullable Object instance, Field field) {
		inject(combined(), instance, field, null);
	}

	private static void inject(AbstractConfiguration configuration, @Nullable Object instance, Field field, @Nullable Config configAnnotation) {
		if (instance == null && !Modifier.isStatic(field.getModifiers()))
			return;
		//if( !Modifier.isPublic(field.getModifiers()) ) {
		//    log.warn("Field " + field.getName() + " is tagged with @Config but is not declared public.  Config for this field failed.");
		//    return;
		//}
		if (Modifier.isFinal(field.getModifiers())) {
			log.warn("Field " + field.getDeclaringClass().getName() + "." + field.getName()
					+ " is tagged with @Config but is declared final.  Config for this field failed.");
			return;
		}
		if (configAnnotation == null)
			configAnnotation = field.getAnnotation(Config.class);
		String key = null;
		if (configAnnotation != null)
			key = configAnnotation.value();
		if (key == null)
			key = field.getName();
		Config classConfigAnnotation = field.getDeclaringClass().getAnnotation(Config.class);
		if (classConfigAnnotation != null)
			key = classConfigAnnotation.value() + "." + key;
		Object value = getDynamic(field.getType(), configuration, key);
		if (value != null) {
			try {
				field.set(instance, value);
				if (log.isDebugEnabled()) {
					// hide values marked as passwords
					String printValue = isSecret(key) ? "**-hidden-**" : field.get(instance).toString();
					log.debug("Set field " + field.getDeclaringClass().getName() + "." + field.getName() + " to " + printValue);
				}
			} catch (IllegalAccessException e) {
				log.error("Could not set config on field " + field.getDeclaringClass().getName() + "." + field.getName(), e);
			}
		}
	}

	public static <T> T getDynamic(Class<T> resultType, AbstractConfiguration configuration, String key) {
		return getDynamic(resultType, configuration, key, null);
	}

	@SuppressWarnings("unchecked")
	public static <T> T getDynamic(Class<T> resultType, AbstractConfiguration configuration, String key, T defaultValue) {
		if (resultType.isAssignableFrom(String.class))
			return (T) configuration.getString(key, (String) defaultValue);
		else if (resultType.isAssignableFrom(Boolean.class) || resultType.isAssignableFrom(Boolean.TYPE))
			return (T) configuration.getBoolean(key, (Boolean) defaultValue);
		else if (resultType.isAssignableFrom(Long.class) || resultType.isAssignableFrom(Long.TYPE))
			return (T) configuration.getLong(key, (Long) defaultValue);
		else if (resultType.isAssignableFrom(Integer.class) || resultType.isAssignableFrom(Integer.TYPE))
			return (T) configuration.getInteger(key, (Integer) defaultValue);
		else if (resultType.isAssignableFrom(Short.class) || resultType.isAssignableFrom(Short.TYPE))
			return (T) configuration.getShort(key, (Short) defaultValue);
		else if (resultType.isAssignableFrom(Byte.class) || resultType.isAssignableFrom(Byte.TYPE))
			return (T) configuration.getByte(key, (Byte) defaultValue);
		else if (resultType.isAssignableFrom(Double.class) || resultType.isAssignableFrom(Double.TYPE))
			return (T) configuration.getDouble(key, (Double) defaultValue);
		else if (resultType.isAssignableFrom(Float.class) || resultType.isAssignableFrom(Float.TYPE))
			return (T) configuration.getFloat(key, (Float) defaultValue);
		else if (resultType.isAssignableFrom(List.class))
			return (T) configuration.getList(key, (List) defaultValue);
		else if (resultType.isAssignableFrom(BigDecimal.class))
			return (T) configuration.getBigDecimal(key, (BigDecimal) defaultValue);
		else if (resultType.isAssignableFrom(BigInteger.class))
			return (T) configuration.getBigInteger(key, (BigInteger) defaultValue);

		throw new IllegalArgumentException("Cannot cast configuration values to " + resultType.getName());
	}

	private static PropertiesConfiguration defaultConfig;
	private static PropertiesConfiguration buildtimeConfig;
	private static PropertiesConfiguration userConfig;
	private static MapConfiguration clConfig;
	private static SystemConfiguration sysConfig;
	private static CombinedConfiguration combined;
	private static Logger log = LoggerFactory.getLogger(ConfigUtil.class);
}
