/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.main;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.NoSuchLanguageException;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.PropertyBindingException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.HystrixConfigurationDefinition;
import org.apache.camel.model.Model;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.spi.CamelBeanPostProcessor;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.EventNotifier;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.spi.PropertyConfigurer;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.support.LifecycleStrategySupport;
import org.apache.camel.support.OrderedComparator;
import org.apache.camel.support.PropertyBindingSupport;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.OrderedProperties;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.concurrent.ThreadHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.support.ObjectHelper.invokeMethod;
import static org.apache.camel.util.ReflectionHelper.findMethod;
import static org.apache.camel.util.StringHelper.matches;

/**
 * Base class for main implementations to allow starting up a JVM with Camel embedded.
 */
public abstract class MainSupport extends ServiceSupport {
    protected static final Logger LOG = LoggerFactory.getLogger(MainSupport.class);
    protected static final int UNINITIALIZED_EXIT_CODE = Integer.MIN_VALUE;
    protected static final int DEFAULT_EXIT_CODE = 0;
    protected final AtomicInteger exitCode = new AtomicInteger(UNINITIALIZED_EXIT_CODE);
    protected final List<MainListener> listeners = new ArrayList<>();
    protected final AtomicBoolean completed = new AtomicBoolean(false);
    protected final CountDownLatch latch = new CountDownLatch(1);

    protected volatile CamelContext camelContext;
    protected volatile ProducerTemplate camelTemplate;
    protected final MainConfigurationProperties mainConfigurationProperties = new MainConfigurationProperties();
    protected List<RouteBuilder> routeBuilders = new ArrayList<>();
    protected String routeBuilderClasses;
    protected List<Object> configurations = new ArrayList<>();
    protected String configurationClasses;
    protected String propertyPlaceholderLocations;
    protected Properties initialProperties;
    protected Properties overrideProperties;

    /**
     * A class for intercepting the hang up signal and do a graceful shutdown of the Camel.
     */
    private static final class HangupInterceptor extends Thread {
        Logger log = LoggerFactory.getLogger(this.getClass());
        final MainSupport mainInstance;

        HangupInterceptor(MainSupport main) {
            mainInstance = main;
        }

        @Override
        public void run() {
            log.info("Received hang up - stopping the main instance.");
            try {
                mainInstance.stop();
            } catch (Exception ex) {
                log.warn("Error during stopping the main instance.", ex);
            }
        }
    }

    protected MainSupport(Class... configurationClasses) {
        this();
        addConfigurationClass(configurationClasses);
    }

    protected MainSupport() {
    }

    /**
     * Runs this process with the given arguments, and will wait until completed, or the JVM terminates.
     */
    public void run() throws Exception {
        if (!completed.get()) {
            internalBeforeStart();
            // if we have an issue starting then propagate the exception to caller
            beforeStart();
            start();
            try {
                afterStart();
                waitUntilCompleted();
                internalBeforeStop();
                beforeStop();
                stop();
                afterStop();
            } catch (Exception e) {
                // however while running then just log errors
                LOG.error("Failed: {}", e, e);
            }
        }
    }

    /**
     * Disable the hangup support. No graceful stop by calling stop() on a
     * Hangup signal.
     */
    public void disableHangupSupport() {
        mainConfigurationProperties.setHangupInterceptorEnabled(false);
    }

    /**
     * Hangup support is enabled by default.
     */
    public void enableHangupSupport() {
        mainConfigurationProperties.setHangupInterceptorEnabled(true);
    }

    /**
     * Adds a {@link org.apache.camel.main.MainListener} to receive callbacks when the main is started or stopping
     *
     * @param listener the listener
     */
    public void addMainListener(MainListener listener) {
        listeners.add(listener);
    }

    /**
     * Removes the {@link org.apache.camel.main.MainListener}
     *
     * @param listener the listener
     */
    public void removeMainListener(MainListener listener) {
        listeners.remove(listener);
    }

    /**
     * Callback to run custom logic before CamelContext is being started.
     * <p/>
     * It is recommended to use {@link org.apache.camel.main.MainListener} instead.
     */
    protected void beforeStart() throws Exception {
        for (MainListener listener : listeners) {
            listener.beforeStart(this);
        }
    }

    /**
     * Callback to run custom logic after CamelContext has been started.
     * <p/>
     * It is recommended to use {@link org.apache.camel.main.MainListener} instead.
     */
    protected void afterStart() throws Exception {
        for (MainListener listener : listeners) {
            listener.afterStart(this);
        }
    }

    private void internalBeforeStart() {
        if (mainConfigurationProperties.isHangupInterceptorEnabled()) {
            String threadName = ThreadHelper.resolveThreadName(null, "CamelHangupInterceptor");

            Thread task = new HangupInterceptor(this);
            task.setName(threadName);
            Runtime.getRuntime().addShutdownHook(task);
        }
    }

    /**
     * Callback to run custom logic before CamelContext is being stopped.
     * <p/>
     * It is recommended to use {@link org.apache.camel.main.MainListener} instead.
     */
    protected void beforeStop() throws Exception {
        for (MainListener listener : listeners) {
            listener.beforeStop(this);
        }
    }

    /**
     * Callback to run custom logic after CamelContext has been stopped.
     * <p/>
     * It is recommended to use {@link org.apache.camel.main.MainListener} instead.
     */
    protected void afterStop() throws Exception {
        for (MainListener listener : listeners) {
            listener.afterStop(this);
        }
    }

    private void internalBeforeStop() {
        try {
            if (camelTemplate != null) {
                ServiceHelper.stopService(camelTemplate);
                camelTemplate = null;
            }
        } catch (Exception e) {
            LOG.debug("Error stopping camelTemplate due " + e.getMessage() + ". This exception is ignored.", e);
        }
    }

    /**
     * Marks this process as being completed.
     */
    public void completed() {
        completed.set(true);
        exitCode.compareAndSet(UNINITIALIZED_EXIT_CODE, DEFAULT_EXIT_CODE);
        latch.countDown();
    }

    /**
     * To configure options on Camel Main.
     */
    public MainConfigurationProperties configure() {
        return mainConfigurationProperties;
    }

    @Deprecated
    public int getDuration() {
        return mainConfigurationProperties.getDurationMaxSeconds();
    }

    /**
     * Sets the duration (in seconds) to run the application until it
     * should be terminated. Defaults to -1. Any value <= 0 will run forever.
     * @deprecated use {@link #configure()}
     */
    @Deprecated
    public void setDuration(int duration) {
        mainConfigurationProperties.setDurationMaxSeconds(duration);
    }

    @Deprecated
    public int getDurationIdle() {
        return mainConfigurationProperties.getDurationMaxIdleSeconds();
    }

    /**
     * Sets the maximum idle duration (in seconds) when running the application, and
     * if there has been no message processed after being idle for more than this duration
     * then the application should be terminated.
     * Defaults to -1. Any value <= 0 will run forever.
     * @deprecated use {@link #configure()}
     */
    @Deprecated
    public void setDurationIdle(int durationIdle) {
        mainConfigurationProperties.setDurationMaxIdleSeconds(durationIdle);
    }

    @Deprecated
    public int getDurationMaxMessages() {
        return mainConfigurationProperties.getDurationMaxMessages();
    }

    /**
     * Sets the duration to run the application to process at most max messages until it
     * should be terminated. Defaults to -1. Any value <= 0 will run forever.
     * @deprecated use {@link #configure()}
     */
    @Deprecated
    public void setDurationMaxMessages(int durationMaxMessages) {
        mainConfigurationProperties.setDurationMaxMessages(durationMaxMessages);
    }

    /**
     * Sets the exit code for the application if duration was hit
     * @deprecated use {@link #configure()}
     */
    @Deprecated
    public void setDurationHitExitCode(int durationHitExitCode) {
        mainConfigurationProperties.setDurationHitExitCode(durationHitExitCode);
    }

    @Deprecated
    public int getDurationHitExitCode() {
        return mainConfigurationProperties.getDurationHitExitCode();
    }

    public int getExitCode() {
        return exitCode.get();
    }

    public String getConfigurationClasses() {
        return configurationClasses;
    }

    public void setConfigurationClasses(String configurations) {
        this.configurationClasses = configurations;
    }

    public void addConfigurationClass(Class... configuration) {
        String existing = configurationClasses;
        if (existing == null) {
            existing = "";
        }
        if (configuration != null) {
            for (Class clazz : configuration) {
                if (!existing.isEmpty()) {
                    existing = existing + ",";
                }
                existing = existing + clazz.getName();
            }
        }
        setConfigurationClasses(existing);
    }

    public void addConfiguration(Object configuration) {
        configurations.add(configuration);
    }

    public String getRouteBuilderClasses() {
        return routeBuilderClasses;
    }

    public void setRouteBuilderClasses(String builders) {
        this.routeBuilderClasses = builders;
    }

    public String getPropertyPlaceholderLocations() {
        return propertyPlaceholderLocations;
    }

    /**
     * A list of locations to add for loading properties.
     * You can use comma to separate multiple locations.
     */
    public void setPropertyPlaceholderLocations(String location) {
        this.propertyPlaceholderLocations = location;
    }

    @Deprecated
    public boolean isAutoConfigurationEnabled() {
        return mainConfigurationProperties.isAutoConfigurationEnabled();
    }

    /**
     * Whether auto-configuration of components/dataformats/languages is enabled or not.
     * When enabled the configuration parameters are loaded from the properties component
     * and configured as defaults (similar to spring-boot auto-configuration). You can prefix
     * the parameters in the properties file with:
     * - camel.component.name.option1=value1
     * - camel.component.name.option2=value2
     * - camel.dataformat.name.option1=value1
     * - camel.dataformat.name.option2=value2
     * - camel.language.name.option1=value1
     * - camel.language.name.option2=value2
     * Where name is the name of the component, dataformat or language such as seda,direct,jaxb.
     * <p/>
     * The auto-configuration also works for any options on components
     * that is a complex type (not standard Java type) and there has been an explicit single
     * bean instance registered to the Camel registry via the {@link org.apache.camel.spi.Registry#bind(String, Object)} method
     * or by using the {@link org.apache.camel.BindToRegistry} annotation style.
     * <p/>
     * This option is default enabled.
     * @deprecated use {@link #configure()}
     */
    @Deprecated
    public void setAutoConfigurationEnabled(boolean autoConfigurationEnabled) {
        mainConfigurationProperties.setAutoConfigurationEnabled(autoConfigurationEnabled);
    }

    public Properties getInitialProperties() {
        return initialProperties;
    }

    /**
     * Sets initial properties for the properties component,
     * which will be used before any locations are resolved.
     */
    public void setInitialProperties(Properties initialProperties) {
        this.initialProperties = initialProperties;
    }

    /**
     * Adds a property (initial) for the properties component,
     * which will be used before any locations are resolved.
     *
     * @param key    the property key
     * @param value  the property value
     *
     * @see #addInitialProperty(String, String)
     * @see #addOverrideProperty(String, String)
     */
    public void addProperty(String key, String value) {
        addInitialProperty(key, value);
    }

    /**
     * Adds a initial property for the properties component,
     * which will be used before any locations are resolved.
     *
     * @param key    the property key
     * @param value  the property value
     */
    public void addInitialProperty(String key, String value) {
        if (initialProperties == null) {
            initialProperties = new OrderedProperties();
        }
        initialProperties.setProperty(key, value);
    }

    public Properties getOverrideProperties() {
        return overrideProperties;
    }

    /**
     * Sets a special list of override properties that take precedence
     * and will use first, if a property exist.
     */
    public void setOverrideProperties(Properties overrideProperties) {
        this.overrideProperties = overrideProperties;
    }

    /**
     * Adds an override property that take precedence
     * and will use first, if a property exist.
     *
     * @param key    the property key
     * @param value  the property value
     */
    public void addOverrideProperty(String key, String value) {
        if (overrideProperties == null) {
            overrideProperties = new OrderedProperties();
        }
        overrideProperties.setProperty(key, value);
    }

    public boolean isTrace() {
        return mainConfigurationProperties.isTracing();
    }

    public void enableTrace() {
        mainConfigurationProperties.setTracing(true);
    }

    @Override
    protected void doStop() throws Exception {
        // call completed to properly stop as we count down the waiting latch
        completed();
    }

    @Override
    protected void doStart() throws Exception {
    }

    protected void waitUntilCompleted() {
        while (!completed.get()) {
            try {
                int idle = mainConfigurationProperties.getDurationMaxIdleSeconds();
                int max = mainConfigurationProperties.getDurationMaxMessages();
                long sec = mainConfigurationProperties.getDurationMaxSeconds();
                if (sec > 0) {
                    LOG.info("Waiting for: {} seconds", sec);
                    latch.await(sec, TimeUnit.SECONDS);
                    exitCode.compareAndSet(UNINITIALIZED_EXIT_CODE, mainConfigurationProperties.getDurationHitExitCode());
                    completed.set(true);
                } else if (idle > 0 || max > 0) {
                    if (idle > 0 && max > 0) {
                        LOG.info("Waiting to be idle for: {} seconds or until: {} messages has been processed", idle, max);
                    } else if (idle > 0) {
                        LOG.info("Waiting to be idle for: {} seconds", idle);
                    } else {
                        LOG.info("Waiting until: {} messages has been processed", max);
                    }
                    exitCode.compareAndSet(UNINITIALIZED_EXIT_CODE, mainConfigurationProperties.getDurationHitExitCode());
                    latch.await();
                    completed.set(true);
                } else {
                    latch.await();
                }
            } catch (InterruptedException e) {
                // okay something interrupted us so terminate
                completed.set(true);
                latch.countDown();
                Thread.currentThread().interrupt();
            }
        }
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    public List<RouteBuilder> getRouteBuilders() {
        return routeBuilders;
    }

    public void setRouteBuilders(List<RouteBuilder> routeBuilders) {
        this.routeBuilders = routeBuilders;
    }

    public List<Object> getConfigurations() {
        return configurations;
    }

    public void setConfigurations(List<Object> configurations) {
        this.configurations = configurations;
    }

    public List<RouteDefinition> getRouteDefinitions() {
        List<RouteDefinition> answer = new ArrayList<>();
        if (camelContext != null) {
            answer.addAll(camelContext.getExtension(Model.class).getRouteDefinitions());
        }
        return answer;
    }

    public ProducerTemplate getCamelTemplate() throws Exception {
        if (camelTemplate == null) {
            camelTemplate = findOrCreateCamelTemplate();
        }
        return camelTemplate;
    }

    protected abstract ProducerTemplate findOrCreateCamelTemplate();

    protected abstract CamelContext createCamelContext();

    protected void initCamelContext() throws Exception {
        camelContext = createCamelContext();
        if (camelContext == null) {
            throw new IllegalStateException("Created CamelContext is null");
        }
        postProcessCamelContext(camelContext);
    }

    protected void loadRouteBuilders(CamelContext camelContext) throws Exception {
        // lets use Camel's bean post processor on any existing route builder classes
        // so the instance has some support for dependency injection
        CamelBeanPostProcessor postProcessor = camelContext.adapt(ExtendedCamelContext.class).getBeanPostProcessor();
        for (RouteBuilder routeBuilder : getRouteBuilders()) {
            postProcessor.postProcessBeforeInitialization(routeBuilder, routeBuilder.getClass().getName());
            postProcessor.postProcessAfterInitialization(routeBuilder, routeBuilder.getClass().getName());
        }

        if (routeBuilderClasses != null) {
            String[] routeClasses = routeBuilderClasses.split(",");
            for (String routeClass : routeClasses) {
                Class<?> routeClazz = camelContext.getClassResolver().resolveClass(routeClass);
                // lets use Camel's injector so the class has some support for dependency injection
                Object builder = camelContext.getInjector().newInstance(routeClazz);
                if (builder instanceof RouteBuilder) {
                    getRouteBuilders().add((RouteBuilder) builder);
                } else {
                    LOG.warn("Class {} is not a RouteBuilder class", routeClazz);
                }
            }
        }
    }

    protected void loadConfigurations(CamelContext camelContext) throws Exception {
        // lets use Camel's bean post processor on any existing configuration classes
        // so the instance has some support for dependency injection
        CamelBeanPostProcessor postProcessor = camelContext.adapt(ExtendedCamelContext.class).getBeanPostProcessor();
        for (Object configuration : getConfigurations()) {
            postProcessor.postProcessBeforeInitialization(configuration, configuration.getClass().getName());
            postProcessor.postProcessAfterInitialization(configuration, configuration.getClass().getName());
        }

        if (configurationClasses != null) {
            String[] configClasses = configurationClasses.split(",");
            for (String configClass : configClasses) {
                Class<?> configClazz = camelContext.getClassResolver().resolveClass(configClass);
                // lets use Camel's injector so the class has some support for dependency injection
                Object config = camelContext.getInjector().newInstance(configClazz);
                getConfigurations().add(config);
            }
        }

        for (Object config : getConfigurations()) {
            // invoke configure method if exists
            Method method = findMethod(config.getClass(), "configure");
            if (method != null) {
                log.info("Calling configure method on configuration class: {}", config.getClass().getName());
                invokeMethod(method, config);
            }
        }
    }

    protected void postProcessCamelContext(CamelContext camelContext) throws Exception {
        if (propertyPlaceholderLocations != null) {
            PropertiesComponent pc = camelContext.getPropertiesComponent();
            pc.addLocation(propertyPlaceholderLocations);
            if (initialProperties != null) {
                pc.setInitialProperties(initialProperties);
            }
            if (overrideProperties != null) {
                pc.setOverrideProperties(overrideProperties);
            }
            LOG.info("Using properties from: {}", propertyPlaceholderLocations);
        } else {
            // lets default to application.properties and ignore if its missing
            // if there are no existing locations configured
            PropertiesComponent pc = camelContext.getPropertiesComponent();
            if (pc.getLocations().isEmpty()) {
                pc.addLocation("classpath:application.properties;optional=true");
            }
            if (initialProperties != null) {
                pc.setInitialProperties(initialProperties);
            }
            if (overrideProperties != null) {
                pc.setOverrideProperties(overrideProperties);
            }
            LOG.info("Using properties from classpath:application.properties");
        }

        if (mainConfigurationProperties.getDurationMaxMessages() > 0 || mainConfigurationProperties.getDurationMaxIdleSeconds() > 0) {
            // register lifecycle so we can trigger to shutdown the JVM when maximum number of messages has been processed
            EventNotifier notifier = new MainDurationEventNotifier(camelContext, mainConfigurationProperties.getDurationMaxMessages(),
                    mainConfigurationProperties.getDurationMaxIdleSeconds(), completed, latch, true);
            // register our event notifier
            ServiceHelper.startService(notifier);
            camelContext.getManagementStrategy().addEventNotifier(notifier);
        }

        // gathers the properties (key=value) that was auto-configured
        final Map<String, String> autoConfiguredProperties = new LinkedHashMap<>();

        // need to eager allow to auto-configure properties component
        if (mainConfigurationProperties.isAutoConfigurationEnabled()) {
            autoConfigurationFailFast(camelContext, autoConfiguredProperties);
            autoConfigurationPropertiesComponent(camelContext, autoConfiguredProperties);
            autoConfigurationMainConfiguration(camelContext, mainConfigurationProperties, autoConfiguredProperties);
        }

        // configure from main configuration properties
        doConfigureCamelContextFromMainConfiguration(camelContext, mainConfigurationProperties, autoConfiguredProperties);

        // try to load configuration classes
        loadConfigurations(camelContext);

        // conventional configuration via properties to allow configuring options on
        // component, dataformat, and languages (like spring-boot auto-configuration)
        if (mainConfigurationProperties.isAutowireComponentProperties() || mainConfigurationProperties.isAutowireComponentPropertiesDeep()) {
            autowireConfigurationFromRegistry(camelContext, mainConfigurationProperties.isAutowireComponentPropertiesDeep());
        }
        if (mainConfigurationProperties.isAutoConfigurationEnabled()) {
            autoConfigurationFromProperties(camelContext, autoConfiguredProperties);
        }

        // log summary of configurations
        if (mainConfigurationProperties.isAutoConfigurationLogSummary() && !autoConfiguredProperties.isEmpty()) {
            LOG.info("Auto-configuration summary:");
            autoConfiguredProperties.forEach((k, v) -> {
                boolean sensitive = k.toLowerCase(Locale.US).contains("password") || k.contains("secret") || k.contains("passphrase") || k.contains("token");
                if (sensitive) {
                    LOG.info("\t{}=xxxxxx", k);
                } else {
                    LOG.info("\t{}={}", k, v);
                }
            });
        }

        // try to load the route builders
        loadRouteBuilders(camelContext);
        // sort routes according to ordered
        routeBuilders.sort(OrderedComparator.get());
        for (RouteBuilder routeBuilder : routeBuilders) {
            camelContext.addRoutes(routeBuilder);
        }
        // register lifecycle so we are notified in Camel is stopped from JMX or somewhere else
        camelContext.addLifecycleStrategy(new MainLifecycleStrategy(completed, latch));
        // allow to do configuration before its started
        for (MainListener listener : listeners) {
            listener.configure(camelContext);
        }
    }

    protected void autoConfigurationFailFast(CamelContext camelContext, Map<String, String> autoConfiguredProperties) throws Exception {
        // load properties
        Properties prop = camelContext.getPropertiesComponent().loadProperties(name -> name.startsWith("camel."));
        LOG.debug("Properties from Camel properties component:");
        for (String key : prop.stringPropertyNames()) {
            LOG.debug("    {}={}", key, prop.getProperty(key));
        }

        // special for environment-variable-enbaled as we need to know this early before we set all the other options
        Object envEnabled = prop.remove("camel.main.autoConfigurationEnvironmentVariablesEnabled");
        if (envEnabled == null) {
            envEnabled = prop.remove("camel.main.auto-configuration-environment-variables-enabled");
            if (envEnabled != null) {
                PropertyBindingSupport.build().withMandatory(true).withIgnoreCase(true).bind(camelContext, mainConfigurationProperties, "autoConfigurationEnvironmentVariablesEnabled", envEnabled);
                autoConfiguredProperties.put("camel.main.auto-configuration-environment-variables-enabled", envEnabled.toString());
            }
        }

        // load properties from ENV (override existing)
        Properties propENV = null;
        if (mainConfigurationProperties.isAutoConfigurationEnvironmentVariablesEnabled()) {
            propENV = loadEnvironmentVariablesAsProperties(new String[]{"camel.main."});
            if (!propENV.isEmpty()) {
                prop.putAll(propENV);
                LOG.debug("Properties from OS environment variables:");
                for (String key : propENV.stringPropertyNames()) {
                    LOG.debug("    {}={}", key, propENV.getProperty(key));
                }
            }
        }

        // special for fail-fast as we need to know this early before we set all the other options
        Object failFast = propENV != null ? propENV.remove("camel.main.autoconfigurationfailfast") : null;
        if (failFast != null) {
            PropertyBindingSupport.build().withMandatory(true).withIgnoreCase(true).bind(camelContext, mainConfigurationProperties, "autoConfigurationFailFast", failFast);
        } else {
            failFast = prop.remove("camel.main.autoConfigurationFailFast");
            if (failFast == null) {
                failFast = prop.remove("camel.main.auto-configuration-fail-fast");
            }
            if (failFast != null) {
                PropertyBindingSupport.build().withMandatory(true).withIgnoreCase(true).bind(camelContext, mainConfigurationProperties, "autoConfigurationFailFast", failFast);
                autoConfiguredProperties.put("camel.main.auto-configuration-fail-fast", failFast.toString());
            }
        }
    }

    /**
     * Configures CamelContext from the {@link MainConfigurationProperties} properties.
     */
    protected void doConfigureCamelContextFromMainConfiguration(CamelContext camelContext, MainConfigurationProperties config,
                                                                Map<String, String> autoConfiguredProperties) throws Exception {
        if (config.getFileConfigurations() != null) {
            String[] locs = config.getFileConfigurations().split(",");
            for (String loc : locs) {
                String path = FileUtil.onlyPath(loc);
                if (path != null) {
                    String pattern = loc.length() > path.length() ? loc.substring(path.length() + 1) : null;
                    File[] files = new File(path).listFiles(f -> matches(pattern, f.getName()));
                    if (files != null) {
                        for (File file : files) {
                            Properties props = new Properties();
                            try (FileInputStream is = new FileInputStream(file)) {
                                props.load(is);
                            }
                            if (!props.isEmpty()) {
                                if (overrideProperties == null) {
                                    // setup override properties on properties component
                                    overrideProperties = new Properties();
                                    PropertiesComponent pc = camelContext.getPropertiesComponent();
                                    pc.setOverrideProperties(overrideProperties);
                                }
                                LOG.info("Loaded additional {} properties from file: {}", props.size(), file);
                                overrideProperties.putAll(props);
                            }
                        }
                    }
                }
            }
        }

        // configure the common/default options
        DefaultConfigurationConfigurer.configure(camelContext, config);
        // lookup and configure SPI beans
        DefaultConfigurationConfigurer.afterPropertiesSet(camelContext);

        // now configure context/hystrix/rest with additional properties
        Properties prop = camelContext.getPropertiesComponent().loadProperties(name -> name.startsWith("camel."));

        // load properties from ENV (override existing)
        if (mainConfigurationProperties.isAutoConfigurationEnvironmentVariablesEnabled()) {
            Properties propENV = loadEnvironmentVariablesAsProperties(new String[]{"camel.component.properties."});
            if (!propENV.isEmpty()) {
                prop.putAll(propENV);
                LOG.debug("Properties from OS environment variables:");
                for (String key : propENV.stringPropertyNames()) {
                    LOG.debug("    {}={}", key, propENV.getProperty(key));
                }
            }
        }

        Map<String, Object> contextProperties = new LinkedHashMap<>();
        Map<String, Object> hystrixProperties = new LinkedHashMap<>();
        Map<String, Object> restProperties = new LinkedHashMap<>();
        for (String key : prop.stringPropertyNames()) {
            if (key.startsWith("camel.context.")) {
                // grab the value
                String value = prop.getProperty(key);
                String option = key.substring(14);
                validateOptionAndValue(key, option, value);
                contextProperties.put(optionKey(option), value);
            } else if (key.startsWith("camel.hystrix.")) {
                // grab the value
                String value = prop.getProperty(key);
                String option = key.substring(14);
                validateOptionAndValue(key, option, value);
                hystrixProperties.put(optionKey(option), value);
            } else if (key.startsWith("camel.rest.")) {
                // grab the value
                String value = prop.getProperty(key);
                String option = key.substring(11);
                validateOptionAndValue(key, option, value);
                restProperties.put(optionKey(option), value);
            }
        }
        if (!contextProperties.isEmpty()) {
            LOG.debug("Auto-configuring CamelContext from loaded properties: {}", contextProperties.size());
            setPropertiesOnTarget(camelContext, camelContext, contextProperties, null, "camel.context.",
                    mainConfigurationProperties.isAutoConfigurationFailFast(), true, autoConfiguredProperties);
        }
        if (!hystrixProperties.isEmpty()) {
            LOG.debug("Auto-configuring Hystrix EIP from loaded properties: {}", hystrixProperties.size());
            ModelCamelContext model = camelContext.adapt(ModelCamelContext.class);
            HystrixConfigurationDefinition hystrix = model.getHystrixConfiguration(null);
            if (hystrix == null) {
                hystrix = new HystrixConfigurationDefinition();
                model.setHystrixConfiguration(hystrix);
            }
            setPropertiesOnTarget(camelContext, hystrix, hystrixProperties, null, "camel.hsytrix.",
                    mainConfigurationProperties.isAutoConfigurationFailFast(), true, autoConfiguredProperties);
        }
        if (!restProperties.isEmpty()) {
            LOG.debug("Auto-configuring Rest DSL from loaded properties: {}", restProperties.size());
            ModelCamelContext model = camelContext.adapt(ModelCamelContext.class);
            RestConfiguration rest = model.getRestConfiguration();
            if (rest == null) {
                rest = new RestConfiguration();
                model.setRestConfiguration(rest);
            }
            setPropertiesOnTarget(camelContext, rest, restProperties, null, "camel.rest.",
                    mainConfigurationProperties.isAutoConfigurationFailFast(), true, autoConfiguredProperties);
        }

        // log which options was not set
        if (!contextProperties.isEmpty()) {
            contextProperties.forEach((k, v) -> {
                LOG.warn("Property not auto-configured: camel.context.{}={} on bean: {}", k, v, camelContext);
            });
        }
        if (!hystrixProperties.isEmpty()) {
            ModelCamelContext model = camelContext.adapt(ModelCamelContext.class);
            HystrixConfigurationDefinition hystrix = model.getHystrixConfiguration(null);
            hystrixProperties.forEach((k, v) -> {
                LOG.warn("Property not auto-configured: camel.hystrix.{}={} on bean: {}", k, v, hystrix);
            });
        }
        if (!restProperties.isEmpty()) {
            ModelCamelContext model = camelContext.adapt(ModelCamelContext.class);
            RestConfiguration rest = model.getRestConfiguration();
            restProperties.forEach((k, v) -> {
                LOG.warn("Property not auto-configured: camel.rest.{}={} on bean: {}", k, v, rest);
            });
        }
    }

    protected void autoConfigurationPropertiesComponent(CamelContext camelContext, Map<String, String> autoConfiguredProperties) throws Exception {
        // load properties
        Properties prop = camelContext.getPropertiesComponent().loadProperties(name -> name.startsWith("camel."));

        // load properties from ENV (override existing)
        if (mainConfigurationProperties.isAutoConfigurationEnvironmentVariablesEnabled()) {
            Properties propENV = loadEnvironmentVariablesAsProperties(new String[]{"camel.component.properties."});
            if (!propENV.isEmpty()) {
                prop.putAll(propENV);
            }
        }

        Map<String, Object> properties = new LinkedHashMap<>();

        for (String key : prop.stringPropertyNames()) {
            if (key.startsWith("camel.component.properties.")) {
                int dot = key.indexOf(".", 26);
                String option = dot == -1 ? "" : key.substring(dot + 1);
                String value = prop.getProperty(key, "");
                validateOptionAndValue(key, option, value);
                properties.put(optionKey(option), value);
            }
        }

        if (!properties.isEmpty()) {
            LOG.debug("Auto-configuring properties component from loaded properties: {}", properties.size());
            setPropertiesOnTarget(camelContext, camelContext.getPropertiesComponent(), properties, null,
                    "camel.component.properties.", mainConfigurationProperties.isAutoConfigurationFailFast(),
                    true, autoConfiguredProperties);
        }

        // log which options was not set
        if (!properties.isEmpty()) {
            properties.forEach((k, v) -> {
                LOG.warn("Property not auto-configured: camel.component.properties.{}={} on object: {}", k, v, camelContext.getPropertiesComponent());
            });
        }
    }

    protected void autoConfigurationMainConfiguration(CamelContext camelContext, MainConfigurationProperties config, Map<String, String> autoConfiguredProperties) throws Exception {
        // load properties
        Properties prop = camelContext.getPropertiesComponent().loadProperties(name -> name.startsWith("camel."));

        // load properties from ENV (override existing)
        if (mainConfigurationProperties.isAutoConfigurationEnvironmentVariablesEnabled()) {
            Properties propENV = loadEnvironmentVariablesAsProperties(new String[]{"camel.main."});
            if (!propENV.isEmpty()) {
                prop.putAll(propENV);
            }
        }

        Map<String, Object> properties = new LinkedHashMap<>();

        for (String key : prop.stringPropertyNames()) {
            if (key.startsWith("camel.main.")) {
                // grab the value
                String value = prop.getProperty(key);
                String option = key.substring(11);
                validateOptionAndValue(key, option, value);
                properties.put(optionKey(option), value);
            }
        }

        if (!properties.isEmpty()) {
            LOG.debug("Auto-configuring main from loaded properties: {}", properties.size());
            setPropertiesOnTarget(camelContext, config, properties, null, "camel.main.",
                    mainConfigurationProperties.isAutoConfigurationFailFast(), true, autoConfiguredProperties);
        }

        // log which options was not set
        if (!properties.isEmpty()) {
            properties.forEach((k, v) -> {
                LOG.warn("Property not auto-configured: camel.main.{}={} on bean: {}", k, v, config);
            });
        }
    }

    protected void autoConfigurationFromProperties(CamelContext camelContext, Map<String, String> autoConfiguredProperties) throws Exception {
        // load optional META-INF/services/org/apache/camel/autowire.properties
        Properties prop = new OrderedProperties();
        try {
            InputStream is = camelContext.getClassResolver().loadResourceAsStream("/META-INF/services/org/apache/camel/autowire.properties");
            if (is != null) {
                prop.load(is);
                if (!prop.isEmpty()) {
                    LOG.info("Autowired enabled from classpath: META-INF/services/org/apache/camel/autowire.properties with {} properties", prop.size());
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Properties from classpath: META-INF/services/org/apache/camel/autowire.properties:");
                        for (String key : prop.stringPropertyNames()) {
                            LOG.debug("    {}={}", key, prop.getProperty(key));
                        }
                    }
                }
                IOHelper.close(is);
            }
        } catch (Throwable e) {
            // ignore as this file is optional
        }

        // load properties from properties component (override existing)
        Properties propPC = camelContext.getPropertiesComponent().loadProperties(name -> name.startsWith("camel."));
        prop.putAll(propPC);

        // load properties from ENV (override existing)
        if (mainConfigurationProperties.isAutoConfigurationEnvironmentVariablesEnabled()) {
            Properties propENV = loadEnvironmentVariablesAsProperties(new String[]{"camel.component.", "camel.dataformat.", "camel.language."});
            if (!propENV.isEmpty()) {
                prop.putAll(propENV);
            }
        }

        Map<PropertyOptionKey, Map<String, Object>> properties = new LinkedHashMap<>();

        for (String key : prop.stringPropertyNames()) {
            if (key.startsWith("camel.component.")) {
                // grab name
                int dot = key.indexOf(".", 16);
                String name = dot == -1 ? key.substring(16) : key.substring(16, dot);
                // skip properties as its already configured earlier
                if ("properties".equals(name)) {
                    continue;
                }
                Component component = camelContext.getComponent(name);
                if (component == null) {
                    throw new IllegalArgumentException("Error configuring property: " + key + " because cannot find component with name " + name
                            + ". Make sure you have the component on the classpath");
                }
                String option = dot == -1 ? "" : key.substring(dot + 1);
                String value = prop.getProperty(key, "");
                String prefix = dot == -1 ? "" : key.substring(0, dot + 1);
                validateOptionAndValue(key, option, value);
                PropertyOptionKey pok = new PropertyOptionKey(key, component, prefix);
                Map<String, Object> values = properties.getOrDefault(pok, new LinkedHashMap<>());
                // we ignore case for property keys (so we should store them in canonical style
                values.put(optionKey(option), value);
                properties.put(pok, values);
            }
            if (key.startsWith("camel.dataformat.")) {
                // grab name
                int dot = key.indexOf(".", 17);
                String name = dot == -1 ? key.substring(17) : key.substring(17, dot);
                DataFormat dataformat = camelContext.resolveDataFormat(name);
                if (dataformat == null) {
                    throw new IllegalArgumentException("Error configuring property: " + key + " because cannot find dataformat with name " + name
                            + ". Make sure you have the dataformat on the classpath");
                }
                String option = dot == -1 ? "" : key.substring(dot + 1);
                String value = prop.getProperty(key, "");
                String prefix = dot == -1 ? "" : key.substring(0, dot + 1);
                validateOptionAndValue(key, option, value);
                PropertyOptionKey pok = new PropertyOptionKey(key, dataformat, prefix);
                Map<String, Object> values = properties.getOrDefault(pok, new LinkedHashMap<>());
                values.put(optionKey(option), value);
                properties.put(pok, values);
            }
            if (key.startsWith("camel.language.")) {
                // grab name
                int dot = key.indexOf(".", 15);
                String name = dot == -1 ? key.substring(15) : key.substring(15, dot);
                Language language;
                try {
                    language = camelContext.resolveLanguage(name);
                } catch (NoSuchLanguageException e) {
                    throw new IllegalArgumentException("Error configuring property: " + key + " because cannot find language with name " + name
                            + ". Make sure you have the language on the classpath");
                }
                String option = dot == -1 ? "" : key.substring(dot + 1);
                String value = prop.getProperty(key, "");
                String prefix = dot == -1 ? "" : key.substring(0, dot + 1);
                validateOptionAndValue(key, option, value);
                PropertyOptionKey pok = new PropertyOptionKey(key, language, prefix);
                Map<String, Object> values = properties.getOrDefault(pok, new LinkedHashMap<>());
                values.put(optionKey(option), value);
                properties.put(pok, values);
            }
        }

        if (!properties.isEmpty()) {
            long total = properties.values().stream().mapToLong(Map::size).sum();
            LOG.debug("Auto-configuring {} components/dataformat/languages from loaded properties: {}", properties.size(), total);
        }

        for (PropertyOptionKey pok : properties.keySet()) {
            Map<String, Object> values = properties.get(pok);
            String optionKey = pok.getKey().substring(pok.getOptionPrefix().length());
            setPropertiesOnTarget(camelContext, pok.getInstance(), values, optionKey, pok.getOptionPrefix(),
                    mainConfigurationProperties.isAutoConfigurationFailFast(), true, autoConfiguredProperties);
        }

        // log which options was not set
        if (!properties.isEmpty()) {
            for (PropertyOptionKey pok : properties.keySet()) {
                Map<String, Object> values = properties.get(pok);
                values.forEach((k, v) -> {
                    String stringValue = v != null ? v.toString() : null;
                    LOG.warn("Property ({}={}) not auto-configured with name: {} on bean: {} with value: {}", pok.getKey(), stringValue, k, pok.getInstance(), stringValue);
                });
            }
        }
    }

    protected void autowireConfigurationFromRegistry(CamelContext camelContext, boolean deepNesting) throws Exception {
        camelContext.addLifecycleStrategy(new LifecycleStrategySupport() {
            @Override
            public void onComponentAdd(String name, Component component) {
                PropertyBindingSupport.autowireSingletonPropertiesFromRegistry(camelContext, component, false, deepNesting, (obj, propertyName, type, value) -> {
                    LOG.info("Autowired property: {} on component: {} as exactly one instance of type: {} found in the registry",
                            propertyName, component.getClass().getSimpleName(), type.getName());
                });
            }
        });
    }

    protected static Properties loadEnvironmentVariablesAsProperties(String[] prefixes) {
        Properties answer = new OrderedProperties();
        if (prefixes == null || prefixes.length == 0) {
            return answer;
        }

        for (String prefix : prefixes) {
            final String pk = prefix.toUpperCase(Locale.US).replaceAll("[^\\w]", "-");
            final String pk2 = pk.replace('-', '_');
            System.getenv().forEach((k, v) -> {
                k = k.toUpperCase(Locale.US);
                if (k.startsWith(pk) || k.startsWith(pk2)) {
                    String key = k.toLowerCase(Locale.US).replace('_', '.');
                    answer.put(key, v);
                }
            });
        }

        return answer;
    }

    protected void validateOptionAndValue(String key, String option, String value) {
        if (ObjectHelper.isEmpty(option)) {
            throw new IllegalArgumentException("Error configuring property: " + key + " because option is empty");
        }
        if (ObjectHelper.isEmpty(value)) {
            throw new IllegalArgumentException("Error configuring property: " + key + " because value is empty");
        }
    }

    public void addRouteBuilder(RouteBuilder routeBuilder) {
        getRouteBuilders().add(routeBuilder);
    }

    public void addRouteBuilder(Class... routeBuilder) {
        String existing = routeBuilderClasses;
        if (existing == null) {
            existing = "";
        }
        if (routeBuilder != null) {
            for (Class clazz : routeBuilder) {
                if (!existing.isEmpty()) {
                    existing = existing + ",";
                }
                existing = existing + clazz.getName();
            }
        }
        setRouteBuilderClasses(existing);
    }

    protected static String optionKey(String key) {
        // as we ignore case for property names we should use keys in same case and without dashes
        key = StringHelper.replaceAll(key, "-", "");
        key = key.toLowerCase(Locale.US);
        return key;
    }

    protected static boolean setPropertiesOnTarget(CamelContext context, Object target, Map<String, Object> properties,
                                                 String optionKey, String optionPrefix, boolean failIfNotSet, boolean ignoreCase,
                                                 Map<String, String> autoConfiguredProperties) throws Exception {
        ObjectHelper.notNull(context, "context");
        ObjectHelper.notNull(target, "target");
        ObjectHelper.notNull(properties, "properties");

        boolean rc = false;
        Iterator it = properties.entrySet().iterator();

        PropertyConfigurer configurer = null;
        if (target instanceof Component) {
            // the component needs to be initialized to have the configurer ready
            ServiceHelper.initService(target);
            configurer = ((Component) target).getComponentPropertyConfigurer();
        }

        while (it.hasNext()) {
            Map.Entry<String, Object> entry = (Map.Entry) it.next();
            String name = entry.getKey();
            Object value = entry.getValue();
            String stringValue = value != null ? value.toString() : null;
            String key = name;
            if (optionPrefix != null && optionKey != null) {
                key = optionPrefix + optionKey;
            } else if (optionPrefix != null) {
                key = optionPrefix + name;
            }

            LOG.debug("Configuring property: {}={} on bean: {}", key, stringValue, target);
            try {
                boolean hit;
                if (failIfNotSet) {
                    PropertyBindingSupport.build().withMandatory(true).withConfigurer(configurer).withIgnoreCase(ignoreCase).bind(context, target, name, stringValue);
                    hit = true;
                } else {
                    hit = PropertyBindingSupport.build().withConfigurer(configurer).withIgnoreCase(true).bind(context, target, name, stringValue);
                }
                if (hit) {
                    it.remove();
                    rc = true;
                    LOG.debug("Configured property: {}={} on bean: {}", key, stringValue, target);
                    autoConfiguredProperties.put(key, stringValue);
                }
            } catch (PropertyBindingException e) {
                if (failIfNotSet) {
                    // enrich the error with more precise details with option prefix and key
                    throw new PropertyBindingException(e.getTarget(), e.getPropertyName(), e.getValue(), optionPrefix, optionKey, e.getCause());
                } else {
                    LOG.debug("Error configuring property (" + key + ") with name: " + name + ") on bean: " + target
                            + " with value: " + stringValue + ". This exception is ignored as failIfNotSet=false.", e);
                }
            }
        }

        return rc;
    }

    private static final class PropertyOptionKey {

        private final String key;
        private final Object instance;
        private final String optionPrefix;

        private PropertyOptionKey(String key, Object instance, String optionPrefix) {
            this.key = key;
            this.instance = instance;
            this.optionPrefix = optionPrefix;
        }

        public String getKey() {
            return key;
        }

        public Object getInstance() {
            return instance;
        }

        public String getOptionPrefix() {
            return optionPrefix;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            PropertyOptionKey that = (PropertyOptionKey) o;
            return key.equals(that.key) && instance.equals(that.instance);
        }

        @Override
        public int hashCode() {
            return Objects.hash(key, instance);
        }
    }

    public abstract class Option {
        private String abbreviation;
        private String fullName;
        private String description;

        protected Option(String abbreviation, String fullName, String description) {
            this.abbreviation = "-" + abbreviation;
            this.fullName = "-" + fullName;
            this.description = description;
        }

        public boolean processOption(String arg, LinkedList<String> remainingArgs) {
            if (arg.equalsIgnoreCase(abbreviation) || fullName.startsWith(arg)) {
                doProcess(arg, remainingArgs);
                return true;
            }
            return false;
        }

        public String getAbbreviation() {
            return abbreviation;
        }

        public String getDescription() {
            return description;
        }

        public String getFullName() {
            return fullName;
        }

        public String getInformation() {
            return "  " + getAbbreviation() + " or " + getFullName() + " = " + getDescription();
        }

        protected abstract void doProcess(String arg, LinkedList<String> remainingArgs);
    }

}
