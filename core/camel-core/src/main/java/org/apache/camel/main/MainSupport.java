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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.FileWatcherReloadStrategy;
import org.apache.camel.model.Model;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.spi.CamelBeanPostProcessor;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.EventNotifier;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.spi.ReloadStrategy;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.EndpointHelper;
import org.apache.camel.support.IntrospectionSupport;
import org.apache.camel.support.LifecycleStrategySupport;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.concurrent.ThreadHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.support.ObjectHelper.invokeMethod;
import static org.apache.camel.util.ReflectionHelper.findMethod;

/**
 * Base class for main implementations to allow starting up a JVM with Camel embedded.
 */
public abstract class MainSupport extends ServiceSupport {
    protected static final Logger LOG = LoggerFactory.getLogger(MainSupport.class);
    protected static final int UNINITIALIZED_EXIT_CODE = Integer.MIN_VALUE;
    protected static final int DEFAULT_EXIT_CODE = 0;
    protected final List<MainListener> listeners = new ArrayList<>();
    protected final List<Option> options = new ArrayList<>();
    protected final CountDownLatch latch = new CountDownLatch(1);
    protected final AtomicBoolean completed = new AtomicBoolean(false);
    protected final AtomicInteger exitCode = new AtomicInteger(UNINITIALIZED_EXIT_CODE);

    // TODO: Move these to mainConfigurationProperties (delegate)
    protected long duration = -1;
    protected long durationIdle = -1;
    protected int durationMaxMessages;
    protected TimeUnit timeUnit = TimeUnit.SECONDS;
    protected boolean trace;
    protected String fileWatchDirectory;
    protected boolean fileWatchDirectoryRecursively;

    protected CamelContext camelContext;
    // TODO: Make it possible to configure MainConfigurationProperties from application.properties via camel.main.xxx
    protected final MainConfigurationProperties mainConfigurationProperties = new MainConfigurationProperties();
    protected List<RouteBuilder> routeBuilders = new ArrayList<>();
    protected String routeBuilderClasses;
    protected List<Object> configurations = new ArrayList<>();
    protected String configurationClasses;
    protected ProducerTemplate camelTemplate;
    protected boolean hangupInterceptorEnabled = true;
    protected int durationHitExitCode = DEFAULT_EXIT_CODE;
    protected ReloadStrategy reloadStrategy;
    protected String propertyPlaceholderLocations;
    protected boolean autoConfigurationEnabled = true;
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
        addOption(new Option("h", "help", "Displays the help screen") {
            protected void doProcess(String arg, LinkedList<String> remainingArgs) {
                showOptions();
                completed();
            }
        });
        addOption(new ParameterOption("r", "routers",
            "Sets the router builder classes which will be loaded while starting the camel context",
            "routerBuilderClasses") {
            @Override
            protected void doProcess(String arg, String parameter, LinkedList<String> remainingArgs) {
                setRouteBuilderClasses(parameter);
            }
        });
        addOption(new ParameterOption("d", "duration",
            "Sets the time duration (seconds) that the application will run for before terminating.",
            "duration") {
            protected void doProcess(String arg, String parameter, LinkedList<String> remainingArgs) {
                // skip second marker to be backwards compatible
                if (parameter.endsWith("s") || parameter.endsWith("S")) {
                    parameter = parameter.substring(0, parameter.length() - 1);
                }
                setDuration(Integer.parseInt(parameter));
            }
        });
        addOption(new ParameterOption("dm", "durationMaxMessages",
            "Sets the duration of maximum number of messages that the application will process before terminating.",
            "durationMaxMessages") {
            protected void doProcess(String arg, String parameter, LinkedList<String> remainingArgs) {
                setDurationMaxMessages(Integer.parseInt(parameter));
            }
        });
        addOption(new ParameterOption("di", "durationIdle",
            "Sets the idle time duration (seconds) duration that the application can be idle before terminating.",
            "durationIdle") {
            protected void doProcess(String arg, String parameter, LinkedList<String> remainingArgs) {
                // skip second marker to be backwards compatible
                if (parameter.endsWith("s") || parameter.endsWith("S")) {
                    parameter = parameter.substring(0, parameter.length() - 1);
                }
                setDurationIdle(Integer.parseInt(parameter));
            }
        });
        addOption(new Option("t", "trace", "Enables tracing") {
            protected void doProcess(String arg, LinkedList<String> remainingArgs) {
                enableTrace();
            }
        });
        addOption(new ParameterOption("e", "exitcode",
            "Sets the exit code if duration was hit",
            "exitcode") {
            protected void doProcess(String arg, String parameter, LinkedList<String> remainingArgs) {
                setDurationHitExitCode(Integer.parseInt(parameter));
            }
        });
        addOption(new ParameterOption("watch", "fileWatch",
            "Sets a directory to watch for file changes to trigger reloading routes on-the-fly",
            "fileWatch") {
            @Override
            protected void doProcess(String arg, String parameter, LinkedList<String> remainingArgs) {
                setFileWatchDirectory(parameter);
            }
        });
        addOption(new ParameterOption("pl", "propertiesLocation",
            "Sets location(s) to load properties, such as from classpath or file system.",
            "propertiesLocation") {
            protected void doProcess(String arg, String parameter, LinkedList<String> remainingArgs) {
                setPropertyPlaceholderLocations(parameter);
            }
        });
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
        hangupInterceptorEnabled = false;
    }

    /**
     * Hangup support is enabled by default.
     */
    public void enableHangupSupport() {
        hangupInterceptorEnabled = true;
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
        if (hangupInterceptorEnabled) {
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
     * Displays the command line options.
     */
    public void showOptions() {
        showOptionsHeader();

        for (Option option : options) {
            System.out.println(option.getInformation());
        }
    }

    /**
     * Parses the command line arguments.
     */
    public void parseArguments(String[] arguments) {
        LinkedList<String> args = new LinkedList<>(Arrays.asList(arguments));

        boolean valid = true;
        while (!args.isEmpty()) {
            String arg = args.removeFirst();

            boolean handled = false;
            for (Option option : options) {
                if (option.processOption(arg, args)) {
                    handled = true;
                    break;
                }
            }
            if (!handled) {
                System.out.println("Unknown option: " + arg);
                System.out.println();
                valid = false;
                break;
            }
        }
        if (!valid) {
            showOptions();
            completed();
        }
    }

    public void addOption(Option option) {
        options.add(option);
    }

    public long getDuration() {
        return duration;
    }

    /**
     * Sets the duration (in seconds) to run the application until it
     * should be terminated. Defaults to -1. Any value <= 0 will run forever.
     */
    public void setDuration(long duration) {
        this.duration = duration;
    }

    public long getDurationIdle() {
        return durationIdle;
    }

    /**
     * Sets the maximum idle duration (in seconds) when running the application, and
     * if there has been no message processed after being idle for more than this duration
     * then the application should be terminated.
     * Defaults to -1. Any value <= 0 will run forever.
     */
    public void setDurationIdle(long durationIdle) {
        this.durationIdle = durationIdle;
    }

    public int getDurationMaxMessages() {
        return durationMaxMessages;
    }

    /**
     * Sets the duration to run the application to process at most max messages until it
     * should be terminated. Defaults to -1. Any value <= 0 will run forever.
     */
    public void setDurationMaxMessages(int durationMaxMessages) {
        this.durationMaxMessages = durationMaxMessages;
    }

    public TimeUnit getTimeUnit() {
        return timeUnit;
    }

    /**
     * Sets the time unit duration (seconds by default).
     */
    public void setTimeUnit(TimeUnit timeUnit) {
        this.timeUnit = timeUnit;
    }

    /**
     * Sets the exit code for the application if duration was hit
     */
    public void setDurationHitExitCode(int durationHitExitCode) {
        this.durationHitExitCode = durationHitExitCode;
    }

    public int getDurationHitExitCode() {
        return durationHitExitCode;
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

    public String getFileWatchDirectory() {
        return fileWatchDirectory;
    }

    /**
     * Sets the directory name to watch XML file changes to trigger live reload of Camel routes.
     * <p/>
     * Notice you cannot set this value and a custom {@link ReloadStrategy} as well.
     */
    public void setFileWatchDirectory(String fileWatchDirectory) {
        this.fileWatchDirectory = fileWatchDirectory;
    }

    public boolean isFileWatchDirectoryRecursively() {
        return fileWatchDirectoryRecursively;
    }

    /**
     * Sets the flag to watch directory of XML file changes recursively to trigger live reload of Camel routes.
     * <p/>
     * Notice you cannot set this value and a custom {@link ReloadStrategy} as well.
     */
    public void setFileWatchDirectoryRecursively(boolean fileWatchDirectoryRecursively) {
        this.fileWatchDirectoryRecursively = fileWatchDirectoryRecursively;
    }

    public ReloadStrategy getReloadStrategy() {
        return reloadStrategy;
    }

    /**
     * Sets a custom {@link ReloadStrategy} to be used.
     * <p/>
     * Notice you cannot set this value and the fileWatchDirectory as well.
     */
    public void setReloadStrategy(ReloadStrategy reloadStrategy) {
        this.reloadStrategy = reloadStrategy;
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

    public boolean isAutoConfigurationEnabled() {
        return autoConfigurationEnabled;
    }

    /**
     * Whether auto configuration of components/dataformats/languages is enabled or not.
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
     * The auto configuration also works for any options on components
     * that is a complex type (not standard Java type) and there has been an explicit single
     * bean instance registered to the Camel registry via the {@link org.apache.camel.spi.Registry#bind(String, Object)} method
     * or by using the {@link org.apache.camel.BindToRegistry} annotation style.
     * <p/>
     * This option is default enabled.
     */
    public void setAutoConfigurationEnabled(boolean autoConfigurationEnabled) {
        this.autoConfigurationEnabled = autoConfigurationEnabled;
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

    public boolean isTrace() {
        return trace;
    }

    public void enableTrace() {
        this.trace = true;
    }

    protected void doStop() throws Exception {
        // call completed to properly stop as we count down the waiting latch
        completed();
    }

    protected void doStart() throws Exception {
    }

    protected void waitUntilCompleted() {
        while (!completed.get()) {
            try {
                if (duration > 0) {
                    TimeUnit unit = getTimeUnit();
                    LOG.info("Waiting for: {} {}", duration, unit);
                    latch.await(duration, unit);
                    exitCode.compareAndSet(UNINITIALIZED_EXIT_CODE, durationHitExitCode);
                    completed.set(true);
                } else if (durationIdle > 0) {
                    TimeUnit unit = getTimeUnit();
                    LOG.info("Waiting to be idle for: {} {}", duration, unit);
                    exitCode.compareAndSet(UNINITIALIZED_EXIT_CODE, durationHitExitCode);
                    latch.await();
                    completed.set(true);
                } else if (durationMaxMessages > 0) {
                    LOG.info("Waiting until: {} messages has been processed", durationMaxMessages);
                    exitCode.compareAndSet(UNINITIALIZED_EXIT_CODE, durationHitExitCode);
                    latch.await();
                    completed.set(true);
                } else {
                    latch.await();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Parses the command line arguments then runs the program.
     */
    public void run(String[] args) throws Exception {
        parseArguments(args);
        run();
        LOG.info("MainSupport exiting code: {}", getExitCode());
    }

    /**
     * Displays the header message for the command line options.
     */
    public void showOptionsHeader() {
        System.out.println("Apache Camel Runner takes the following options");
        System.out.println();
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
        postProcessCamelContext(camelContext);
    }

    protected void loadRouteBuilders(CamelContext camelContext) throws Exception {
        // lets use Camel's bean post processor on any existing route builder classes
        // so the instance has some support for dependency injection
        CamelBeanPostProcessor postProcessor = camelContext.getBeanPostProcessor();
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
        CamelBeanPostProcessor postProcessor = camelContext.getBeanPostProcessor();
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
            PropertiesComponent pc = camelContext.getPropertiesComponent();
            pc.addLocation("classpath:application.properties");
            pc.setIgnoreMissingLocation(true);
            if (initialProperties != null) {
                pc.setInitialProperties(initialProperties);
            }
            if (overrideProperties != null) {
                pc.setOverrideProperties(overrideProperties);
            }
            LOG.info("Using optional properties from classpath:application.properties");
        }
        if (trace) {
            camelContext.setTracing(true);
        }
        if (fileWatchDirectory != null) {
            ReloadStrategy reload = new FileWatcherReloadStrategy(fileWatchDirectory, fileWatchDirectoryRecursively);
            camelContext.setReloadStrategy(reload);
            // ensure reload is added as service and started
            camelContext.addService(reload);
            // and ensure its register in JMX (which requires manually to be added because CamelContext is already started)
            Object managedObject = camelContext.getManagementStrategy().getManagementObjectStrategy().getManagedObjectForService(camelContext, reload);
            if (managedObject == null) {
                // service should not be managed
                return;
            }

            // skip already managed services, for example if a route has been restarted
            if (camelContext.getManagementStrategy().isManaged(managedObject)) {
                LOG.trace("The service is already managed: {}", reload);
                return;
            }

            try {
                camelContext.getManagementStrategy().manageObject(managedObject);
            } catch (Exception e) {
                LOG.warn("Could not register service: " + reload + " as Service MBean.", e);
            }
        }

        if (durationMaxMessages > 0 || durationIdle > 0) {
            // convert to seconds as that is what event notifier uses
            long seconds = timeUnit.toSeconds(durationIdle);
            // register lifecycle so we can trigger to shutdown the JVM when maximum number of messages has been processed
            EventNotifier notifier = new MainDurationEventNotifier(camelContext, durationMaxMessages, seconds, completed, latch, true);
            // register our event notifier
            ServiceHelper.startService(notifier);
            camelContext.getManagementStrategy().addEventNotifier(notifier);
        }

        // need to eager allow to auto configure properties component
        if (autoConfigurationEnabled) {
            autoConfigurationPropertiesComponent(camelContext);
        }

        // try to load configuration classes
        loadConfigurations(camelContext);

        // conventional configuration via properties to allow configuring options on
        // component, dataformat, and languages (like spring-boot auto-configuration)
        if (autoConfigurationEnabled) {
            autoConfigurationFromRegistry(camelContext);
            autoConfigurationFromProperties(camelContext);
        }

        // try to load the route builders
        loadRouteBuilders(camelContext);
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

    protected void autoConfigurationPropertiesComponent(CamelContext camelContext) throws Exception {
        // load properties
        Properties prop = camelContext.getPropertiesComponent().loadProperties();

        Map<Object, Map<String, Object>> properties = new LinkedHashMap<>();

        for (String key : prop.stringPropertyNames()) {
            int dot = key.indexOf(".", 26);
            if (key.startsWith("camel.component.properties.") && dot > 0) {
                Component component = camelContext.getPropertiesComponent();
                // grab the value
                String value = prop.getProperty(key);
                String option = key.substring(dot + 1);
                Map<String, Object> values = properties.getOrDefault(component, new LinkedHashMap<>());
                values.put(option, value);
                properties.put(component, values);
            }
        }

        if (!properties.isEmpty()) {
            long total = properties.values().stream().mapToLong(Map::size).sum();
            LOG.info("Auto configuring properties component from loaded properties: {}", total);
        }

        for (Object obj : properties.keySet()) {
            Map<String, Object> values = properties.get(obj);
            setCamelProperties(camelContext, obj, values, true);
        }
    }

    protected void autoConfigurationFromProperties(CamelContext camelContext) throws Exception {
        // load properties
        Properties prop = camelContext.getPropertiesComponent().loadProperties();

        Map<Object, Map<String, Object>> properties = new LinkedHashMap<>();

        for (String key : prop.stringPropertyNames()) {
            int dot = key.indexOf(".", 16);
            if (key.startsWith("camel.component.") && dot > 0) {
                // grab component name
                String name = key.substring(16, dot);
                // skip properties as its already configured earlier
                if ("properties".equals(name)) {
                    continue;
                }
                Component component = camelContext.getComponent(name);
                // grab the value
                String value = prop.getProperty(key);
                String option = key.substring(dot + 1);
                Map<String, Object> values = properties.getOrDefault(component, new LinkedHashMap<>());
                values.put(option, value);
                properties.put(component, values);
            }
            dot = key.indexOf(".", 17);
            if (key.startsWith("camel.dataformat.") && dot > 0) {
                // grab component name
                String name = key.substring(17, dot);
                DataFormat dataformat = camelContext.resolveDataFormat(name);
                // grab the value
                String value = prop.getProperty(key);
                String option = key.substring(dot + 1);
                Map<String, Object> values = properties.getOrDefault(dataformat, new LinkedHashMap<>());
                values.put(option, value);
                properties.put(dataformat, values);
            }
            dot = key.indexOf(".", 15);
            if (key.startsWith("camel.language.") && dot > 0) {
                // grab component name
                String name = key.substring(15, dot);
                Language language = camelContext.resolveLanguage(name);
                // grab the value
                String value = prop.getProperty(key);
                String option = key.substring(dot + 1);
                Map<String, Object> values = properties.getOrDefault(language, new LinkedHashMap<>());
                values.put(option, value);
                properties.put(language, values);
            }
        }

        if (!properties.isEmpty()) {
            long total = properties.values().stream().mapToLong(Map::size).sum();
            LOG.info("Auto configuring {} components/dataformat/languages from loaded properties: {}", properties.size(), total);
        }

        for (Object obj : properties.keySet()) {
            Map<String, Object> values = properties.get(obj);
            setCamelProperties(camelContext, obj, values, true);
        }
    }

    protected void autoConfigurationFromRegistry(CamelContext camelContext) throws Exception {
        camelContext.addLifecycleStrategy(new LifecycleStrategySupport() {
            @Override
            public void onComponentAdd(String name, Component component) {
                // when adding a component then support auto-configuring complex types
                // by looking up from registry, such as DataSource etc
                Map<String, Object> properties = new LinkedHashMap<>();
                IntrospectionSupport.getProperties(component, properties, null);

                // lookup complex types
                properties.forEach((k, v) -> {
                    // if the property has not been set and its a complex type (not simple or string etc)
                    Class type = getGetterType(component, k);
                    if (isComplexType(type)) {
                        Set lookup = findExplicitBindingByType(camelContext, type);
                        if (lookup.size() == 1) {
                            v = lookup.iterator().next();
                            try {
                                LOG.info("Auto configuring option: {} on component: {} as one instance of type: {} registered in the Camel Registry", k, name, type.getName());
                                IntrospectionSupport.setProperty(camelContext, component, k, v);
                            } catch (Exception e) {
                                LOG.warn("Cannot auto configure option: " + k + " on component: " + name + " due to " + e.getMessage());
                            }
                        }
                    }
                });
            }

            /**
             * Finds any explicit bean bindings that has been added to the registry.
             * This means that if there are any, then they have been added by the end user
             * and we should favour using the bean if there is a single instance bound for the type.
             */
            private Set findExplicitBindingByType(CamelContext camelContext, Class type) {
                if (camelContext.getRegistry() instanceof MainRegistry) {
                    return ((MainRegistry) camelContext.getRegistry()).findBindingsByType(type);
                }
                return Collections.EMPTY_SET;
            }

            private boolean isComplexType(Class type) {
                // lets consider all non java as complex types
                return type != null && !type.isPrimitive() && !type.getName().startsWith("java");
            }

            private Class getGetterType(Component component, String key) {
                try {
                    Method getter = IntrospectionSupport.getPropertyGetter(component.getClass(), key);
                    if (getter != null) {
                        return getter.getReturnType();
                    }
                } catch (NoSuchMethodException e) {
                    // ignore
                }
                return null;
            }
        });
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

    private static boolean setCamelProperties(CamelContext context, Object target, Map<String, Object> properties, boolean failIfNotSet) throws Exception {
        ObjectHelper.notNull(context, "context");
        ObjectHelper.notNull(target, "target");
        ObjectHelper.notNull(properties, "properties");
        boolean rc = false;
        Iterator it = properties.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<String, Object> entry = (Map.Entry) it.next();
            String name = entry.getKey();
            Object value = entry.getValue();

            // if the name has dot's then its an OGNL expressions (so lets use simple language to walk down this ognl path)
            boolean ognl = name.contains(".");
            if (ognl) {
                Language method = context.resolveLanguage("simple");
                String path = name.substring(0, name.lastIndexOf('.'));
                Expression exp = method.createExpression("${body." + path + "}");
                Exchange dummy = new DefaultExchange(context);
                dummy.getMessage().setBody(target);
                Object newTarget = exp.evaluate(dummy, Object.class);
                if (newTarget != null) {
                    target = newTarget;
                    name = name.substring(name.lastIndexOf('.') + 1);
                }
            }

            String stringValue = value != null ? value.toString() : null;
            boolean hit = false;

            LOG.debug("Setting property {} on {} with value {}", name, target, stringValue);
            if (EndpointHelper.isReferenceParameter(stringValue)) {
                hit = IntrospectionSupport.setProperty(context, context.getTypeConverter(), target, name, null, stringValue, true);
            } else if (value != null) {
                try {
                    hit = IntrospectionSupport.setProperty(context, context.getTypeConverter(), target, name, value);
                } catch (IllegalArgumentException var12) {
                    hit = IntrospectionSupport.setProperty(context, context.getTypeConverter(), target, name, null, stringValue, true);
                }
            }

            if (hit) {
                it.remove();
                rc = true;
            } else if (failIfNotSet) {
                throw new IllegalArgumentException("Cannot configure option [" + name + "] with value [" + stringValue + "] as the bean class ["
                    + ObjectHelper.classCanonicalName(target) + "] has no suitable setter method, or not possible to lookup a bean with the id [" + stringValue + "] in Camel registry");
            }
        }

        return rc;
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

    public abstract class ParameterOption extends Option {
        private String parameterName;

        protected ParameterOption(String abbreviation, String fullName, String description, String parameterName) {
            super(abbreviation, fullName, description);
            this.parameterName = parameterName;
        }

        protected void doProcess(String arg, LinkedList<String> remainingArgs) {
            if (remainingArgs.isEmpty()) {
                System.err.println("Expected fileName for ");
                showOptions();
                completed();
            } else {
                String parameter = remainingArgs.removeFirst();
                doProcess(arg, parameter, remainingArgs);
            }
        }

        public String getInformation() {
            return "  " + getAbbreviation() + " or " + getFullName() + " <" + parameterName + "> = " + getDescription();
        }

        protected abstract void doProcess(String arg, String parameter, LinkedList<String> remainingArgs);
    }
}
