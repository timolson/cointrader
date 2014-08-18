package org.cryptocoinpartners.module;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.apache.commons.configuration.AbstractConfiguration;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.ArrayUtils;
import org.cryptocoinpartners.esper.annotation.Listeners;
import org.cryptocoinpartners.esper.annotation.Subscriber;
import org.cryptocoinpartners.esper.annotation.When;
import org.cryptocoinpartners.schema.Event;
import org.cryptocoinpartners.service.Service;
import org.cryptocoinpartners.util.ConfigUtil;
import org.cryptocoinpartners.util.Injector;
import org.cryptocoinpartners.util.ReflectionUtil;
import org.joda.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.espertech.esper.client.EPAdministrator;
import com.espertech.esper.client.EPRuntime;
import com.espertech.esper.client.EPServiceProvider;
import com.espertech.esper.client.EPServiceProviderManager;
import com.espertech.esper.client.EPStatement;
import com.espertech.esper.client.EventBean;
import com.espertech.esper.client.SafeIterator;
import com.espertech.esper.client.StatementAwareUpdateListener;
import com.espertech.esper.client.UpdateListener;
import com.espertech.esper.client.deploy.DeploymentException;
import com.espertech.esper.client.deploy.DeploymentOptions;
import com.espertech.esper.client.deploy.DeploymentResult;
import com.espertech.esper.client.deploy.EPDeploymentAdmin;
import com.espertech.esper.client.deploy.ParseException;
import com.espertech.esper.client.time.CurrentTimeEvent;
import com.espertech.esper.client.time.CurrentTimeSpanEvent;
import com.espertech.esper.core.service.EPServiceProviderImpl;
import com.google.inject.Binder;
import com.google.inject.Module;

/**
 * This is a wrapper around Esper with dependency injection functionality as well.
 *
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
public class Context {

	/**
	 * Contexts are not created through injection, because they are injection contexts themselves.  Use this static
	 * method for construction.
	 */
	public static Context create() {
		return new Context(null);
	}

	/**
	 * Use a TimeProvider when you do not want real wall-clock time to drive the Context; for example, during replay
	 * of historical events.
	 */
	public static Context create(TimeProvider timeProvider) {
		return new Context(timeProvider);
	}

	public interface TimeProvider {
		/**
		 * @return the Instant the Context should be initialized to as the starting time
		 */
		Instant getInitialTime();

		/**
		 * @param event The event to be published after the time is advanced.  If null is returned, the time is
		 *              not advanced and the current time in the Esper engine is used.
		 */
		Instant nextTime(Event event);
	}

	public static interface AttachListener {
		public void afterAttach(Context context);
	}

	/**
	 * This is the main way to register modules with the Context.  Attaching a class to a Context has
	 * many effects:
	 * <ol>
	 * <li>The class will be instantiated and the instance will be <pre>@Inject</pre>ed by Guice, binding any other
	 * objects which have been attached to this Context previously</li>
	 * <li>The instance will have any fields marked with @Config set using this Context's current Configuration.</li>
	 * <li>If the class c has any superclasses or interfaces tagged with @Service, this instance is registered as
	 * an implementation of that service interface for future injections.  Other instances in this Context will have
	 * their @Injected fields of service types set to an instance of this class c when the types match.</li>
	 * <li>The created instance is <pre>subscribe()</pre>'d to the Context's esper, binding any @When annotations
	 * on the instances's methods to esper statements</li>
	 * <li>If the attached class implements AttachListener, the instance's afterAttach() method is called.</li>
	 * <li>The new instance is returned after configuration</li>
	 * </ol>
	 */
	public <T> T attach(Class<T> c) {
		return attach(c, null);
	}

	/**
	 * Looks in the module.path packages for a class with the given simple name.  The classname may have "Module"
	 * appended at the end e.g. class MyNameModule can be referenced as String "MyName"
	 * @param name The name of the Class to load from the module.path (See Configuration)
	 * @return the instance that was created and attached
	 */
	public Object attach(String name) {
		Class<?> c = findModuleClass(name);
		if (c == null)
			throw new Error("Could not find module named " + name + " in module.path");
		//noinspection RedundantCast
		return attach(c, (Configuration) null);
	}

	/**
	 * @param config Override configuration for this particular attachment
	 * @see #attach(Class)
	 */
	public Object attach(String name, Configuration config, Module... specificInjections) {
		Class<?> c = findModuleClass(name);
		if (c == null)
			throw new Error("Could not find module named " + name + " in module.path");
		return attach(c, config, specificInjections);
	}

	public <T> T attach(Class<T> c, final Configuration moduleConfig) {
		return attach(c, moduleConfig, (Module[]) null);
	}

	public <T> T attach(Class<T> c, final Configuration moduleConfig, Module... specificInjections) {
		Injector i = ArrayUtils.isEmpty(specificInjections) ? injector : injector.createChildInjector(specificInjections);
		if (moduleConfig != null)
			i = i.withConfig(moduleConfig);
		T instance = i.getInstance(c);
		attach(c, instance);
		return instance;
	}

	public void attach(Class c, Object instance) {
		ConfigUtil.applyConfiguration(instance, config);
		loadStatements(instance.getClass().getSimpleName());
		subscribe(instance);
		registerBindings(c, instance);
		if (AttachListener.class.isAssignableFrom(c)) {
			AttachListener listener = (AttachListener) instance;
			listener.afterAttach(this);
		}
	}

	public List<Object> loadStatementByName(String name) throws ParseException, DeploymentException, IOException {
		EPStatement statement = epAdministrator.getStatement(name);
		List<Object> list = new ArrayList<Object>();
		if (statement != null && statement.isStarted()) {
			SafeIterator<EventBean> it = statement.safeIterator();
			try {
				while (it.hasNext()) {
					EventBean bean = it.next();
					Object underlaying = bean.getUnderlying();
					list.add(underlaying);
				}
			} finally {
				it.close();
			}
		}
		return list;
	}

	/**
	 * Attaches a specific instance to this Context.
	 */
	public void attachInstance(Object instance) {
		attach(instance.getClass(), instance);
	}

	public void publish(Event e) {
		Instant now;
		if (timeProvider != null) {
			now = timeProvider.nextTime(e);
			if (now != null)
				advanceTime(now);
			else
				now = new Instant(epRuntime.getCurrentTime());
		} else
			now = new Instant(epRuntime.getCurrentTime());
		e.publishedAt(now);
		epRuntime.sendEvent(e);
	}

	public void destroy() {
		privateDestroy();
	}

	public void advanceTime(Instant now) {
		if (timeProvider == null)
			throw new IllegalArgumentException("Can only advanceTime() when the Context was constructed with a TimeProvider");
		if (lastTime == null) {
			// jump to the start time instead of stepping to it
			epRuntime.sendEvent(new CurrentTimeEvent(now.getMillis()));
		} else if (now.isBefore(lastTime))
			throw new IllegalArgumentException("advanceTime must always move time forward. " + now + " < " + lastTime);
		else if (now.isAfter(lastTime)) {
			// step time up to now
			epRuntime.sendEvent(new CurrentTimeSpanEvent(now.getMillis()));
		}
		lastTime = now;
	}

	public void subscribe(Object listener) {
		if (listener == this)
			return;
		for (Class<?> cls = listener.getClass(); cls != Object.class; cls = cls.getSuperclass())
			subscribe(listener, cls);
	}

	private void processAnnotations(EPStatement statement) throws Exception {

		Annotation[] annotations = statement.getAnnotations();
		for (Annotation annotation : annotations) {
			if (annotation instanceof Subscriber) {

				Subscriber subscriber = (Subscriber) annotation;
				Object obj = getSubscriber(subscriber.className());
				statement.setSubscriber(obj);

			} else if (annotation instanceof Listeners) {

				Listeners listeners = (Listeners) annotation;
				for (String className : listeners.classNames()) {
					Class<?> cl = Class.forName(className);
					Object obj = cl.newInstance();
					if (obj instanceof StatementAwareUpdateListener) {
						statement.addListener((StatementAwareUpdateListener) obj);
					} else {
						statement.addListener((UpdateListener) obj);
					}
				}

			}
		}
	}

	private Object getSubscriber(String fqdn) throws Exception {

		Class<?> cl = Class.forName(fqdn);
		return cl.newInstance();
	}

	private void subscribe(Object listener, Class<?> cls) {
		for (Method method : cls.getDeclaredMethods()) {
			When when = method.getAnnotation(When.class);
			if (when != null) {
				String statement = when.value();
				log.debug("subscribing " + method + " with statement \"" + statement + "\"");
				subscribe(listener, method, statement);
			}
		}
	}

	public void subscribe(Object listener, Method method, String statement) {
		EPStatement epStatement = epAdministrator.createEPL(statement);
		subscribe(listener, method, epStatement);
	}

	public void loadStatements(String source) {
		loadStatements(source, null);
	}

	/**
	 * @param source        a string containing EPL statements
	 * @param intoFieldBean if not null, any @IntoMethod annotations on Esper statements will bind the columns from
	 *                      the select statement into the fields of the intoFieldBean instance.
	 */
	public void loadStatements(String source, Object intoFieldBean) {
		EPDeploymentAdmin deploymentAdmin = epAdministrator.getDeploymentAdmin();
		com.espertech.esper.client.deploy.Module module;
		try {
			module = deploymentAdmin.read(source + ".epl");
			DeploymentResult deployResult;
			try {
				deployResult = deploymentAdmin.deploy(module, new DeploymentOptions());
				List<EPStatement> statements = deployResult.getStatements();

				for (EPStatement statement : statements) {

					try {
						processAnnotations(statement);
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
				log.debug("deployed module " + source + ".epl");
			} catch (DeploymentException e) {
				System.out.println(e);
				e.printStackTrace();
			}
		} catch (IOException e) {
			log.debug("no module file found for " + source + ".epl on classpath. Please ensure " + source + ".epl is in the resources directory.");
		} catch (ParseException e) {
			System.out.println(e);
			e.printStackTrace();
		}

	}

	public Injector getInjector() {
		return injector;
	}

	//
	// End of Public Interface
	//

	private void subscribe(Object listener, Method method, EPStatement statement) {
		statement.setSubscriber(new Listener(listener, method, statement.getText()));
	}

	private Class<?> findModuleClass(String name) {
		Class<?> found;
		for (String path : getModulePathList()) {
			String pdot = path + ".";
			if ((found = findClass(pdot + name)) != null)
				return found;
			if ((found = findClass(pdot + name + "Module")) != null)
				return found;
		}
		return null;
	}

	private Class<?> findClass(String className) {
		try {
			return Class.forName(className);
		} catch (ClassNotFoundException e) {
			return null;
		}
	}

	private static void load(Context context, File file) throws IOException, ParseException, DeploymentException {
		context.loadStatements(FileUtils.readFileToString(file));
	}

	private static void loadEsperFiles(Context context, String modulePackageName) throws Exception {
		String path = modulePackageName.replaceAll("\\.", "/");
		File[] files = new File(path).listFiles();
		if (files != null) {
			for (File file : files) {
				if (file.getName().toLowerCase().endsWith(".epl")) {
					log.debug("loading epl file " + file.getName());
					load(context, file);
				}
			}
		}
	}

	private static List<String> getModulePathList() {
		String pathProperty = "module.path";
		return ConfigUtil.getPathProperty(pathProperty);
	}

	// todo how to bring in module-specific config now?
	private static AbstractConfiguration buildConfig(String name, String modulePackageName, @Nullable AbstractConfiguration c) throws ConfigurationException {
		final ClassLoader classLoader = Context.class.getClassLoader();
		final ArrayList<AbstractConfiguration> moduleConfigs = new ArrayList<>();

		// first priority is the caller's configuration
		if (c != null)
			moduleConfigs.add(c);

		// then add the package-specific props file
		String slashPackage = modulePackageName.replaceAll("\\.", "/");
		String propsFilePath = slashPackage + "/" + name + ".properties";
		URL resource = classLoader.getResource(propsFilePath);
		if (resource != null) {
			PropertiesConfiguration packageConfig = new PropertiesConfiguration(resource);
			moduleConfigs.add(packageConfig);
		}

		// then the more generic config.properties
		propsFilePath = slashPackage + "/config.properties";
		resource = classLoader.getResource(propsFilePath);
		if (resource != null) {
			PropertiesConfiguration packageConfig = new PropertiesConfiguration(resource);
			moduleConfigs.add(packageConfig);
		}

		return ConfigUtil.forModule(moduleConfigs);
	}

	private boolean registerBindings(Class<?> c) {
		return registerBindings(c, c);
	}

	// recursion method walks up the superclass and interface parent tree looking for parent classes to register
	private boolean registerBindings(Class service, Object implementationClassOrObject) {
		boolean injectorWasUpdated = conditionalRegister(service, implementationClassOrObject);
		Class<?> superclass = service.getSuperclass();
		if (superclass != null && registerBindings(superclass, implementationClassOrObject))
			injectorWasUpdated = true;
		for (Class<?> interfaceClass : service.getInterfaces())
			if (registerBindings(interfaceClass, implementationClassOrObject))
				injectorWasUpdated = true;
		return injectorWasUpdated;
	}

	private boolean conditionalRegister(final Class interfaceClass, final Object implementationClassOrObject) {
		if (interfaceClass.getAnnotation(Service.class) != null) {
			doRegister(interfaceClass, implementationClassOrObject);
			return true;
		}
		return false;
	}

	private void doRegister(final Class interfaceClass, final Object implementationClassOrObject) {
		injector = childInjector(null, new Module() {
			@Override
			@SuppressWarnings("unchecked")
			public void configure(Binder binder) {
				if (Class.class.isAssignableFrom(implementationClassOrObject.getClass()))
					binder.bind(interfaceClass).to((Class) implementationClassOrObject);
				else
					binder.bind(interfaceClass).toInstance(implementationClassOrObject);
			}
		});
	}

	private <T> void register(final Class<? super T> interfaceClass, final T instance) {
		injector = childInjector(null, new Module() {
			@Override
			public void configure(Binder binder) {
				binder.bind(interfaceClass).toInstance(instance);
			}
		});
	}


	@SuppressWarnings("unchecked")
	private Injector childInjector(final @Nullable Configuration configParams, Module... modules) {
		final int moduleLength = ArrayUtils.getLength(modules);
		if (moduleLength == 0 && configParams == null)
			return injector;
		Injector childInjector = injector.createChildInjector(modules);
		if (configParams != null)
			childInjector.setConfig(configParams);
		return childInjector;
	}

	private Context(TimeProvider timeProvider) {
		this.timeProvider = timeProvider;

		final com.espertech.esper.client.Configuration esperConfig = new com.espertech.esper.client.Configuration();
		esperConfig.configure("cointrader-esper.cfg.xml");

		esperConfig.addEventType(Event.class);
		Set<Class<? extends Event>> eventTypes = ReflectionUtil.getSubtypesOf(Event.class);
		for (Class<? extends Event> eventType : eventTypes)
			esperConfig.addEventType(eventType);
		esperConfig.addImport(IntoMethod.class);
		if (timeProvider != null) {
			esperConfig.getEngineDefaults().getThreading().setInternalTimerEnabled(false);
		}
		epService = EPServiceProviderManager.getDefaultProvider(esperConfig);
		if (timeProvider != null) {
			lastTime = timeProvider.getInitialTime();
			final EPServiceProviderImpl epService1 = (EPServiceProviderImpl) epService;
			epService1.initialize(lastTime.getMillis());
		}
		epRuntime = epService.getEPRuntime();
		epAdministrator = epService.getEPAdministrator();
		config = ConfigUtil.combined();
		//injector = Injector.root().createChildInjector(subscribingModule,new Module()
		injector = Injector.root().createChildInjector(new Module() {
			@Override
			public void configure(Binder binder) {
				// bind this Context
				binder.bind(Context.class).toInstance(Context.this);
			}
		});
		injector.setConfig(config);
	}

	/**
	 * this class conforms to the callback specs for an Esper subscriber
	 * http://esper.codehaus.org/esper-4.11.0/doc/reference/en-US/html_single/index.html#api-admin-subscriber
	 * then forwards that invocation to the original listener
	 */
	private class Listener {
		public void update(Object[] row) {
			boolean wasAccessible = method.isAccessible();
			method.setAccessible(true);
			try {
				method.invoke(delegate, row);
			} catch (IllegalAccessException | InvocationTargetException e) {
				throw new EsperError("Could not invoke method " + method + " on statement trigger " + statement, e);
			} catch (Throwable t) {
				throw new Error("Error invoking " + delegate.getClass().getName() + "." + method.getName(), t);
			} finally {
				method.setAccessible(wasAccessible);
			}
		}

		private Listener(Object delegate, Method method, String statement) {
			this.delegate = delegate;
			this.method = method;
			this.statement = statement;
		}

		private final Object delegate;
		private final Method method;
		private final String statement;
	}

	private static Logger log = LoggerFactory.getLogger(Context.class);

	private Configuration config;
	private Injector injector;
	private TimeProvider timeProvider;
	private Instant lastTime = null;
	private EPServiceProvider epService;
	private EPRuntime epRuntime;
	private EPAdministrator epAdministrator;

	private void privateDestroy() {
		epService.destroy();

		// null all the variables here to eliminate any crazy cycles
		config = null;
		injector = null;
		timeProvider = null;
		lastTime = null;
		epService = null;
		epRuntime = null;
		epAdministrator = null;

		ScheduledExecutorService svc = Executors.newSingleThreadScheduledExecutor();
		Runnable garbageCollection = new Runnable() {
			@Override
			public void run() {
				Runtime.getRuntime().gc();
			}
		};
		svc.schedule(garbageCollection, 1, TimeUnit.MILLISECONDS);
	}

}
