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
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.camel.CamelConfiguration;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Component;
import org.apache.camel.Configuration;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.NoSuchLanguageException;
import org.apache.camel.NonManagedService;
import org.apache.camel.PropertiesLookupListener;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.Service;
import org.apache.camel.StartupStep;
import org.apache.camel.console.DevConsole;
import org.apache.camel.console.DevConsoleRegistry;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckRegistry;
import org.apache.camel.health.HealthCheckRepository;
import org.apache.camel.impl.debugger.DebuggerJmxConnectorService;
import org.apache.camel.impl.debugger.DefaultBacklogDebugger;
import org.apache.camel.impl.engine.DefaultCompileStrategy;
import org.apache.camel.impl.engine.DefaultRoutesLoader;
import org.apache.camel.saga.CamelSagaService;
import org.apache.camel.spi.AutowiredLifecycleStrategy;
import org.apache.camel.spi.BacklogDebugger;
import org.apache.camel.spi.BacklogTracer;
import org.apache.camel.spi.CamelBeanPostProcessor;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.CamelMetricsService;
import org.apache.camel.spi.CamelTracingService;
import org.apache.camel.spi.CompileStrategy;
import org.apache.camel.spi.ContextReloadStrategy;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.Debugger;
import org.apache.camel.spi.DebuggerFactory;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.LifecycleStrategy;
import org.apache.camel.spi.PackageScanClassResolver;
import org.apache.camel.spi.PeriodTaskScheduler;
import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.spi.Registry;
import org.apache.camel.spi.RouteTemplateParameterSource;
import org.apache.camel.spi.RoutesLoader;
import org.apache.camel.spi.StartupStepRecorder;
import org.apache.camel.spi.SupervisingRouteController;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.DefaultContextReloadStrategy;
import org.apache.camel.support.LifecycleStrategySupport;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.PropertyBindingSupport;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.support.SimpleEventNotifierSupport;
import org.apache.camel.support.jsse.KeyManagersParameters;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.jsse.SSLContextServerParameters;
import org.apache.camel.support.jsse.TrustManagersParameters;
import org.apache.camel.support.scan.PackageScanHelper;
import org.apache.camel.support.service.BaseService;
import org.apache.camel.support.startup.BacklogStartupStepRecorder;
import org.apache.camel.support.startup.LoggingStartupStepRecorder;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.OrderedLocationProperties;
import org.apache.camel.util.OrderedProperties;
import org.apache.camel.util.SensitiveUtils;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.vault.VaultConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.main.MainConstants.profilePropertyPlaceholderLocation;
import static org.apache.camel.main.MainHelper.computeProperties;
import static org.apache.camel.main.MainHelper.optionKey;
import static org.apache.camel.main.MainHelper.setPropertiesOnTarget;
import static org.apache.camel.main.MainHelper.validateOptionAndValue;
import static org.apache.camel.util.LocationHelper.locationSummary;
import static org.apache.camel.util.StringHelper.matches;
import static org.apache.camel.util.StringHelper.startsWithIgnoreCase;

/**
 * Base class for main implementations to allow bootstrapping Camel in standalone mode.
 */
public abstract class BaseMainSupport extends BaseService {

    private static final Logger LOG = LoggerFactory.getLogger(BaseMainSupport.class);

    protected final List<MainListener> listeners = new ArrayList<>();
    protected volatile CamelContext camelContext;
    protected final MainConfigurationProperties mainConfigurationProperties = new MainConfigurationProperties();
    protected final OrderedLocationProperties wildcardProperties = new OrderedLocationProperties();
    protected RoutesCollector routesCollector = new DefaultRoutesCollector();
    protected String propertyPlaceholderLocations;
    protected String defaultPropertyPlaceholderLocation = MainConstants.DEFAULT_PROPERTY_PLACEHOLDER_LOCATION;
    protected Properties initialProperties;
    protected Properties overrideProperties;
    protected boolean standalone = true;
    protected final MainHelper helper;

    protected BaseMainSupport() {
        this.helper = new MainHelper();
    }

    protected BaseMainSupport(CamelContext camelContext) {
        this.camelContext = camelContext;
        this.helper = new MainHelper();
    }

    /**
     * To configure options on Camel Main.
     */
    public MainConfigurationProperties configure() {
        return mainConfigurationProperties;
    }

    public RoutesCollector getRoutesCollector() {
        return routesCollector;
    }

    /**
     * To use a custom {@link RoutesCollector}.
     */
    public void setRoutesCollector(RoutesCollector routesCollector) {
        this.routesCollector = routesCollector;
    }

    public String getPropertyPlaceholderLocations() {
        return propertyPlaceholderLocations;
    }

    /**
     * A list of locations to add for loading properties. You can use comma to separate multiple locations.
     */
    public void setPropertyPlaceholderLocations(String location) {
        this.propertyPlaceholderLocations = location;
    }

    public String getDefaultPropertyPlaceholderLocation() {
        return defaultPropertyPlaceholderLocation;
    }

    /**
     * Set the default location for application properties if no locations have been set. If the value is set to "false"
     * or empty, the default location is not taken into account.
     * <p/>
     * Default value is "classpath:application.properties;optional=true".
     */
    public void setDefaultPropertyPlaceholderLocation(String defaultPropertyPlaceholderLocation) {
        this.defaultPropertyPlaceholderLocation = defaultPropertyPlaceholderLocation;
    }

    public Properties getInitialProperties() {
        if (initialProperties == null) {
            initialProperties = new OrderedProperties();
        }
        return initialProperties;
    }

    /**
     * Sets initial properties for the properties component, which will be used before any locations are resolved.
     */
    public void setInitialProperties(Properties initialProperties) {
        this.initialProperties = initialProperties;
    }

    /**
     * Sets initial properties for the properties component, which will be used before any locations are resolved.
     */
    public void setInitialProperties(Map<String, Object> initialProperties) {
        this.initialProperties = new OrderedProperties();
        this.initialProperties.putAll(initialProperties);
    }

    /**
     * Adds a property (initial) for the properties component, which will be used before any locations are resolved.
     *
     * @param key   the property key
     * @param value the property value
     * @see         #addInitialProperty(String, String)
     * @see         #addOverrideProperty(String, String)
     */
    public void addProperty(String key, String value) {
        addInitialProperty(key, value);
    }

    /**
     * Adds a property (initial) for the properties component, which will be used before any locations are resolved.
     *
     * @param key   the property key
     * @param value the property value
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
     * Sets a special list of override properties that take precedence and will use first, if a property exist.
     */
    public void setOverrideProperties(Properties overrideProperties) {
        this.overrideProperties = overrideProperties;
    }

    /**
     * Sets a special list of override properties that take precedence and will use first, if a property exist.
     */
    public void setOverrideProperties(Map<String, Object> initialProperties) {
        this.overrideProperties = new OrderedProperties();
        this.overrideProperties.putAll(initialProperties);
    }

    /**
     * Adds an override property that take precedence and will use first, if a property exist.
     *
     * @param key   the property key
     * @param value the property value
     */
    public void addOverrideProperty(String key, String value) {
        if (overrideProperties == null) {
            overrideProperties = new OrderedProperties();
        }
        overrideProperties.setProperty(key, value);
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    /**
     * Adds a {@link MainListener} to receive callbacks when the main is started or stopping
     *
     * @param listener the listener
     */
    public void addMainListener(MainListener listener) {
        listeners.add(listener);
    }

    /**
     * Removes the {@link MainListener}
     *
     * @param listener the listener
     */
    public void removeMainListener(MainListener listener) {
        listeners.remove(listener);
    }

    protected void loadCustomBeans(CamelContext camelContext) throws Exception {
        // auto-detect custom beans via base package scanning
        String basePackage = camelContext.getCamelContextExtension().getBasePackageScan();
        if (basePackage != null) {
            PackageScanHelper.registerBeans(camelContext, Set.of(basePackage));
        }
    }

    protected void loadConfigurations(CamelContext camelContext) throws Exception {
        // auto-detect camel configurations via base package scanning
        String basePackage = camelContext.getCamelContextExtension().getBasePackageScan();
        if (basePackage != null) {
            PackageScanClassResolver pscr = PluginHelper.getPackageScanClassResolver(camelContext);
            Set<Class<?>> found1 = pscr.findImplementations(CamelConfiguration.class, basePackage);
            Set<Class<?>> found2 = pscr.findAnnotated(Configuration.class, basePackage);
            Set<Class<?>> found = new LinkedHashSet<>();
            found.addAll(found1);
            found.addAll(found2);
            for (Class<?> clazz : found) {
                // lets use Camel's injector so the class has some support for dependency injection
                Object config = camelContext.getInjector().newInstance(clazz);
                if (config instanceof CamelConfiguration cc) {
                    LOG.debug("Discovered CamelConfiguration class: {}", cc);
                    mainConfigurationProperties.addConfiguration(cc);
                }
            }
        }

        if (mainConfigurationProperties.getConfigurationClasses() != null) {
            String[] configClasses = mainConfigurationProperties.getConfigurationClasses().split(",");
            for (String configClass : configClasses) {
                Class<CamelConfiguration> configClazz
                        = camelContext.getClassResolver().resolveClass(configClass, CamelConfiguration.class);
                // skip main classes
                boolean mainClass = false;
                try {
                    configClazz.getDeclaredMethod("main", String[].class);
                    mainClass = true;
                } catch (NoSuchMethodException e) {
                    // ignore
                }
                if (!mainClass) {
                    // let's use Camel's injector so the class has some support for dependency injection
                    CamelConfiguration config = camelContext.getInjector().newInstance(configClazz);
                    mainConfigurationProperties.addConfiguration(config);
                }
            }
        }

        // lets use Camel's bean post processor on any existing configuration classes
        // so the instance has some support for dependency injection
        CamelBeanPostProcessor postProcessor = PluginHelper.getBeanPostProcessor(camelContext);

        // discover configurations from the registry
        Set<CamelConfiguration> registryConfigurations = camelContext.getRegistry().findByType(CamelConfiguration.class);
        for (CamelConfiguration configuration : registryConfigurations) {
            postProcessor.postProcessBeforeInitialization(configuration, configuration.getClass().getName());
            postProcessor.postProcessAfterInitialization(configuration, configuration.getClass().getName());
        }

        // prepare the directly configured instances (from registry should have been post processed already)
        for (Object configuration : mainConfigurationProperties.getConfigurations()) {
            postProcessor.postProcessBeforeInitialization(configuration, configuration.getClass().getName());
            postProcessor.postProcessAfterInitialization(configuration, configuration.getClass().getName());
        }

        // invoke configure on configurations
        for (CamelConfiguration config : mainConfigurationProperties.getConfigurations()) {
            config.configure(camelContext);
        }
        // invoke configure on configurations that are from registry
        for (CamelConfiguration config : registryConfigurations) {
            config.configure(camelContext);
        }
    }

    protected void configurePropertiesService(CamelContext camelContext) throws Exception {
        PropertiesComponent pc = camelContext.getPropertiesComponent();
        if (pc.getLocations().isEmpty()) {
            String locations = propertyPlaceholderLocations;
            if (locations == null) {
                // ENV/SYS takes precedence, then java configured value
                String profile = MainHelper.lookupPropertyFromSysOrEnv(MainConstants.PROFILE)
                        .orElse(mainConfigurationProperties.getProfile());
                if (profile == null) {
                    // fallback to check if application.properties has a profile
                    Properties prop = new Properties();
                    try (InputStream is
                            = ResourceHelper.resolveResourceAsInputStream(camelContext, "application.properties")) {
                        if (is != null) {
                            prop.load(is);
                        }
                    }
                    profile = prop.getProperty("camel.main.profile");
                }
                if (profile != null) {
                    mainConfigurationProperties.setProfile(profile);
                    String loc = profilePropertyPlaceholderLocation(profile);
                    if (!defaultPropertyPlaceholderLocation.contains(loc)) {
                        defaultPropertyPlaceholderLocation = loc + "," + defaultPropertyPlaceholderLocation;
                    }
                }
                locations
                        = MainHelper.lookupPropertyFromSysOrEnv(MainConstants.PROPERTY_PLACEHOLDER_LOCATION)
                                .orElse(defaultPropertyPlaceholderLocation);
            }
            if (locations != null) {
                locations = locations.trim();
            }
            if (ObjectHelper.isNotEmpty(locations) && !locations.endsWith("false")) {
                pc.addLocation(locations);
                if (defaultPropertyPlaceholderLocation.equals(locations)) {
                    LOG.debug("Properties location: {}", locations);
                } else {
                    // if not default location then log at INFO
                    LOG.info("Properties location: {}", locations);
                }
            }
        }

        final Properties ip = tryLoadProperties(initialProperties, MainConstants.INITIAL_PROPERTIES_LOCATION, camelContext);
        if (ip != null) {
            pc.setInitialProperties(ip);
        }

        final Properties op = tryLoadProperties(overrideProperties, MainConstants.OVERRIDE_PROPERTIES_LOCATION, camelContext);
        if (op != null) {
            pc.setOverrideProperties(op);
        }

        Optional<String> cloudLocations = pc.resolveProperty(MainConstants.CLOUD_PROPERTIES_LOCATION);
        if (cloudLocations.isPresent()) {
            LOG.info("Cloud properties location: {}", cloudLocations);
            final Properties kp = tryLoadCloudProperties(op, cloudLocations.get());
            if (kp != null) {
                pc.setOverrideProperties(kp);
            }
        }
    }

    private Properties tryLoadProperties(
            Properties initialProperties, String initialPropertiesLocation, CamelContext camelContext)
            throws IOException {
        Properties ip = initialProperties;
        if (ip == null || ip.isEmpty()) {
            Optional<String> location = MainHelper.lookupPropertyFromSysOrEnv(initialPropertiesLocation);
            if (location.isPresent()) {
                try (InputStream is = ResourceHelper.resolveMandatoryResourceAsInputStream(camelContext, location.get())) {
                    ip = new Properties();
                    ip.load(is);
                }
            }
        }
        return ip;
    }

    private static Properties tryLoadCloudProperties(
            Properties overridProperties, String cloudPropertiesLocations)
            throws IOException {
        final OrderedLocationProperties cp = new OrderedLocationProperties();
        try {
            String[] locations = cloudPropertiesLocations.split(",");
            for (String loc : locations) {
                Path confPath = Paths.get(loc);
                if (Files.exists(confPath) && Files.isDirectory(confPath)) {
                    Files.walkFileTree(confPath, new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                            if (!Files.isDirectory(file)) {
                                try {
                                    String val = new String(Files.readAllBytes(file));
                                    cp.put(loc, file.getFileName().toString(), val);
                                } catch (IOException e) {
                                    LOG.warn("Some error happened while reading property from cloud configuration file {}",
                                            file, e);
                                }
                            }
                            return FileVisitResult.CONTINUE;
                        }
                    });
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (overridProperties == null) {
            return cp;
        }
        Properties mergedProperties = new Properties(overridProperties);
        mergedProperties.putAll(cp);
        return mergedProperties;
    }

    protected void configureLifecycle(CamelContext camelContext) throws Exception {
    }

    private void scheduleRefresh(CamelContext camelContext, String key, long period) throws Exception {
        final Optional<Runnable> task = PluginHelper.getPeriodTaskResolver(camelContext)
                .newInstance(key, Runnable.class);
        if (task.isPresent()) {
            Runnable r = task.get();
            if (LOG.isDebugEnabled()) {
                LOG.debug("Scheduling: {} (period: {})", r, TimeUtils.printDuration(period, false));
            }
            if (camelContext.hasService(ContextReloadStrategy.class) == null) {
                // refresh is enabled then we need to automatically enable context-reload as well
                ContextReloadStrategy reloader = new DefaultContextReloadStrategy();
                camelContext.addService(reloader);
            }
            PeriodTaskScheduler scheduler = PluginHelper.getPeriodTaskScheduler(camelContext);
            scheduler.schedulePeriodTask(r, period);
        }
    }

    protected void autoconfigure(CamelContext camelContext) throws Exception {
        // gathers the properties (key=value) that was auto-configured
        final OrderedLocationProperties autoConfiguredProperties = new OrderedLocationProperties();

        // configure the profile with pre-configured settings
        ProfileConfigurer.configureMain(camelContext, mainConfigurationProperties.getProfile(), mainConfigurationProperties);

        // need to eager allow to auto-configure properties component
        if (mainConfigurationProperties.isAutoConfigurationEnabled()) {
            autoConfigurationFailFast(camelContext, autoConfiguredProperties);
            autoConfigurationPropertiesComponent(camelContext, autoConfiguredProperties);

            autoConfigurationSingleOption(camelContext, autoConfiguredProperties, "camel.main.routesIncludePattern",
                    value -> {
                        mainConfigurationProperties.setRoutesIncludePattern(value);
                        return null;
                    });
            if (mainConfigurationProperties.isModeline()) {
                camelContext.setModeline(true);
            }
            // eager load properties from modeline by scanning DSL sources and gather properties for auto configuration
            // also load other non-route related configuration (e.g., beans)
            modelineRoutes(camelContext);

            autoConfigurationMainConfiguration(camelContext, mainConfigurationProperties, autoConfiguredProperties);
        }

        // configure from main configuration properties
        doConfigureCamelContextFromMainConfiguration(camelContext, mainConfigurationProperties, autoConfiguredProperties);

        // try to load custom beans/configuration classes via package scanning
        configurePackageScan(camelContext);
        loadCustomBeans(camelContext);
        loadConfigurations(camelContext);

        if (mainConfigurationProperties.isAutoConfigurationEnabled()) {
            autoConfigurationFromProperties(camelContext, autoConfiguredProperties);
            autowireWildcardProperties(camelContext);
            // register properties reloader so we can auto-update if updated
            camelContext.addService(new MainPropertiesReload(this));
        }

        // log summary of configurations
        if (mainConfigurationProperties.isAutoConfigurationLogSummary() && !autoConfiguredProperties.isEmpty()) {
            logConfigurationSummary(autoConfiguredProperties);
        }

        // we are now done with the main helper during bootstrap
        helper.bootstrapDone();
    }

    private static void logConfigurationSummary(OrderedLocationProperties autoConfiguredProperties) {
        // first log variables
        MainHelper.logConfigurationSummary(LOG, autoConfiguredProperties, "Variables summary",
                (k) -> k.startsWith("camel.variable."));
        // then log standard options
        MainHelper.logConfigurationSummary(LOG, autoConfiguredProperties, "Auto-configuration summary", null);
    }

    protected void configureStartupRecorder(CamelContext camelContext) {
        ExtendedCamelContext ecc = camelContext.getCamelContextExtension();

        // we need to load these configurations early as they control the startup recorder when using camel-jfr
        // and we want to start jfr recording as early as possible to also capture details during bootstrapping Camel

        // load properties
        Properties prop = camelContext.getPropertiesComponent().loadProperties(name -> name.startsWith("camel."),
                MainHelper::optionKey);

        Object value = prop.remove("camel.main.startupRecorder");
        if (ObjectHelper.isNotEmpty(value)) {
            mainConfigurationProperties.setStartupRecorder(value.toString());
        }

        value = prop.remove("camel.main.startupRecorderRecording");
        if (ObjectHelper.isNotEmpty(value)) {
            mainConfigurationProperties.setStartupRecorderRecording("true".equalsIgnoreCase(value.toString()));
        }
        value = prop.remove("camel.main.startupRecorderProfile");
        if (ObjectHelper.isNotEmpty(value)) {
            mainConfigurationProperties.setStartupRecorderProfile(
                    CamelContextHelper.parseText(camelContext, value.toString()));
        }
        value = prop.remove("camel.main.startupRecorderDuration");
        if (ObjectHelper.isNotEmpty(value)) {
            mainConfigurationProperties.setStartupRecorderDuration(Long.parseLong(value.toString()));
        }
        value = prop.remove("camel.main.startupRecorderMaxDepth");
        if (ObjectHelper.isNotEmpty(value)) {
            mainConfigurationProperties.setStartupRecorderMaxDepth(Integer.parseInt(value.toString()));
        }

        if ("off".equals(mainConfigurationProperties.getStartupRecorder())
                || "false".equals(mainConfigurationProperties.getStartupRecorder())) {
            ecc.getStartupStepRecorder().setEnabled(false);
        } else if ("logging".equals(mainConfigurationProperties.getStartupRecorder())) {
            if (!(ecc.getStartupStepRecorder() instanceof LoggingStartupStepRecorder)) {
                ecc.setStartupStepRecorder(new LoggingStartupStepRecorder());
            }
        } else if ("backlog".equals(mainConfigurationProperties.getStartupRecorder())) {
            if (!(ecc.getStartupStepRecorder() instanceof BacklogStartupStepRecorder)) {
                ecc.setStartupStepRecorder(new BacklogStartupStepRecorder());
            }
        } else if ("jfr".equals(mainConfigurationProperties.getStartupRecorder())
                || "java-flight-recorder".equals(mainConfigurationProperties.getStartupRecorder())
                || mainConfigurationProperties.getStartupRecorder() == null) {
            // try to auto discover camel-jfr to use
            StartupStepRecorder fr = ecc.getBootstrapFactoryFinder()
                    .newInstance(StartupStepRecorder.FACTORY, StartupStepRecorder.class).orElse(null);
            if (fr != null) {
                LOG.debug("Discovered startup recorder: {} from classpath", fr);
                fr.setRecording(mainConfigurationProperties.isStartupRecorderRecording());
                fr.setStartupRecorderDuration(mainConfigurationProperties.getStartupRecorderDuration());
                fr.setRecordingProfile(mainConfigurationProperties.getStartupRecorderProfile());
                fr.setMaxDepth(mainConfigurationProperties.getStartupRecorderMaxDepth());
                camelContext.getCamelContextExtension().setStartupStepRecorder(fr);
            }
        }
    }

    protected void configurePackageScan(CamelContext camelContext) {
        if (mainConfigurationProperties.isBasePackageScanEnabled()) {
            // only set the base package if enabled
            String base = mainConfigurationProperties.getBasePackageScan();
            String current = camelContext.getCamelContextExtension().getBasePackageScan();
            if (base != null && !base.equals(current)) {
                camelContext.getCamelContextExtension().setBasePackageScan(base);
                LOG.info("Classpath scanning enabled from base package: {}", base);
            }
        }
    }

    protected void configureMainListener(CamelContext camelContext) throws Exception {
        // any custom listener in registry
        camelContext.getRegistry().findByType(MainListener.class).forEach(this::addMainListener);
        // listener from configuration
        mainConfigurationProperties.getMainListeners().forEach(this::addMainListener);
        if (mainConfigurationProperties.getMainListenerClasses() != null) {
            for (String fqn : mainConfigurationProperties.getMainListenerClasses().split(",")) {
                fqn = fqn.trim();
                Class<? extends MainListener> clazz
                        = camelContext.getClassResolver().resolveMandatoryClass(fqn, MainListener.class);
                addMainListener(camelContext.getInjector().newInstance(clazz));
            }
        }
    }

    protected void configureRoutesLoader(CamelContext camelContext) {
        // use main based routes loader
        ExtendedCamelContext ecc = camelContext.getCamelContextExtension();

        // need to configure compile work dir as its used from routes loader when it discovered code to dynamic compile
        if (mainConfigurationProperties.getCompileWorkDir() != null) {
            CompileStrategy cs = camelContext.getCamelContextExtension().getContextPlugin(CompileStrategy.class);
            if (cs == null) {
                cs = new DefaultCompileStrategy();
                ecc.addContextPlugin(CompileStrategy.class, cs);
            }
            cs.setWorkDir(mainConfigurationProperties.getCompileWorkDir());
        }

        RoutesLoader loader = new DefaultRoutesLoader();
        loader.setIgnoreLoadingError(mainConfigurationProperties.isRoutesCollectorIgnoreLoadingError());
        ecc.addContextPlugin(RoutesLoader.class, loader);
    }

    protected void modelineRoutes(CamelContext camelContext) throws Exception {
        // then configure and add the routes
        RoutesConfigurer configurer = doCommonRouteConfiguration(camelContext);

        configurer.configureModeline(camelContext);
    }

    protected void configureRoutes(CamelContext camelContext) throws Exception {
        RoutesConfigurer configurer = doCommonRouteConfiguration(camelContext);

        configurer.configureRoutes(camelContext);
    }

    private RoutesConfigurer doCommonRouteConfiguration(CamelContext camelContext) {
        // then configure and add the routes
        RoutesConfigurer configurer = new RoutesConfigurer();

        routesCollector.setIgnoreLoadingError(mainConfigurationProperties.isRoutesCollectorIgnoreLoadingError());
        if (mainConfigurationProperties.isRoutesCollectorEnabled()) {
            configurer.setRoutesCollector(routesCollector);
            configurer.setIgnoreLoadingError(mainConfigurationProperties.isRoutesCollectorIgnoreLoadingError());
        }

        configurer.setBeanPostProcessor(PluginHelper.getBeanPostProcessor(camelContext));
        configurer.setRoutesBuilders(mainConfigurationProperties.getRoutesBuilders());
        configurer.setRoutesBuilderClasses(mainConfigurationProperties.getRoutesBuilderClasses());
        if (mainConfigurationProperties.isBasePackageScanEnabled()) {
            // only set the base package if enabled
            configurer.setBasePackageScan(mainConfigurationProperties.getBasePackageScan());
        }
        configurer.setJavaRoutesExcludePattern(mainConfigurationProperties.getJavaRoutesExcludePattern());
        configurer.setJavaRoutesIncludePattern(mainConfigurationProperties.getJavaRoutesIncludePattern());
        configurer.setRoutesExcludePattern(mainConfigurationProperties.getRoutesExcludePattern());
        configurer.setRoutesIncludePattern(mainConfigurationProperties.getRoutesIncludePattern());
        return configurer;
    }

    /**
     * A specialized {@link LifecycleStrategy} that can handle autowiring of Camel components, dataformats, languages.
     */
    protected LifecycleStrategy createLifecycleStrategy(CamelContext camelContext) {
        return new MainAutowiredLifecycleStrategy(camelContext);
    }

    protected void postProcessCamelContext(CamelContext camelContext) throws Exception {
        // gathers the properties (key=value) that was used as property placeholders during bootstrap
        final OrderedLocationProperties propertyPlaceholders = new OrderedLocationProperties();

        // use the main autowired lifecycle strategy instead of the default
        camelContext.getLifecycleStrategies().removeIf(s -> s instanceof AutowiredLifecycleStrategy);
        camelContext.addLifecycleStrategy(createLifecycleStrategy(camelContext));

        // setup properties
        configurePropertiesService(camelContext);
        // register listener on properties component so we can capture them
        PropertiesComponent pc = camelContext.getPropertiesComponent();
        pc.addPropertiesLookupListener(new PropertyPlaceholderListener(propertyPlaceholders));
        // setup startup recorder before building context
        configureStartupRecorder(camelContext);
        // setup package scan
        configurePackageScan(camelContext);
        // configure to use our main routes loader
        configureRoutesLoader(camelContext);
        // configure custom main listeners
        configureMainListener(camelContext);

        // ensure camel context is build
        camelContext.build();

        for (MainListener listener : listeners) {
            listener.beforeInitialize(this);
        }

        // allow doing custom configuration before camel is started
        for (MainListener listener : listeners) {
            listener.beforeConfigure(this);
        }

        // we want to capture startup events for import tasks during main bootstrap
        StartupStepRecorder recorder = camelContext.getCamelContextExtension().getStartupStepRecorder();
        StartupStep step;

        if (standalone) {
            step = recorder.beginStep(BaseMainSupport.class, "autoconfigure", "Auto Configure");
            autoconfigure(camelContext);
            recorder.endStep(step);
        }

        configureLifecycle(camelContext);

        if (standalone) {
            // detect if camel-debug JAR is on classpath as we need to know this before configuring routes
            detectCamelDebugJar(camelContext);
            step = recorder.beginStep(BaseMainSupport.class, "configureRoutes", "Collect Routes");
            configureRoutes(camelContext);
            recorder.endStep(step);
        }

        // after the routes are read (org.apache.camel.spi.RoutesBuilderLoader did their work), we may have
        // new classes defined, so main implementations may have to reconfigure the registry using newly
        // available bean definitions
        postProcessCamelRegistry(camelContext, mainConfigurationProperties);

        // allow doing custom configuration before camel is started
        for (MainListener listener : listeners) {
            listener.afterConfigure(this);
        }

        // we want to log the property placeholder summary after routes has been started,
        // but before camel context logs that it has been started, so we need to use an event listener
        if (standalone && mainConfigurationProperties.isAutoConfigurationLogSummary()) {
            camelContext.getManagementStrategy().addEventNotifier(new PlaceholderSummaryEventNotifier(propertyPlaceholders));
        }
    }

    protected void detectCamelDebugJar(CamelContext camelContext) {
        DebuggerFactory df = camelContext.getCamelContextExtension().getBootstrapFactoryFinder()
                .newInstance(Debugger.FACTORY, DebuggerFactory.class).orElse(null);
        if (df != null) {
            // if camel-debug is on classpath then we need to eager to turn on source location which is needed for Java DSL
            camelContext.setSourceLocationEnabled(true);
        }
    }

    protected void autoConfigurationFailFast(CamelContext camelContext, OrderedLocationProperties autoConfiguredProperties)
            throws Exception {
        // load properties
        OrderedLocationProperties prop = (OrderedLocationProperties) camelContext.getPropertiesComponent()
                .loadProperties(name -> name.startsWith("camel."), MainHelper::optionKey);
        LOG.debug("Properties from Camel properties component:");
        for (String key : prop.stringPropertyNames()) {
            LOG.debug("    {}={}", key, prop.getProperty(key));
        }

        // special for environment-variable-enabled as we need to know this early before we set all the other options
        Object envEnabled = prop.remove("camel.main.autoConfigurationEnvironmentVariablesEnabled");
        if (ObjectHelper.isNotEmpty(envEnabled)) {
            mainConfigurationProperties.setAutoConfigurationEnvironmentVariablesEnabled(
                    CamelContextHelper.parseBoolean(camelContext, envEnabled.toString()));
            String loc = prop.getLocation("camel.main.autoConfigurationEnvironmentVariablesEnabled");
            autoConfiguredProperties.put(loc, "camel.main.autoConfigurationEnvironmentVariablesEnabled",
                    envEnabled.toString());
        }
        // special for system-properties-enabled as we need to know this early before we set all the other options
        Object jvmEnabled = prop.remove("camel.main.autoConfigurationSystemPropertiesEnabled");
        if (ObjectHelper.isNotEmpty(jvmEnabled)) {
            mainConfigurationProperties.setAutoConfigurationSystemPropertiesEnabled(
                    CamelContextHelper.parseBoolean(camelContext, jvmEnabled.toString()));
            String loc = prop.getLocation("camel.main.autoConfigurationSystemPropertiesEnabled");
            autoConfiguredProperties.put(loc, "camel.autoConfigurationSystemPropertiesEnabled",
                    jvmEnabled.toString());
        }

        // load properties from ENV (override existing)
        Properties propENV = null;
        if (mainConfigurationProperties.isAutoConfigurationEnvironmentVariablesEnabled()) {
            propENV = MainHelper.loadEnvironmentVariablesAsProperties(new String[] { "camel.main." });
            if (!propENV.isEmpty()) {
                prop.putAll(propENV);
                LOG.debug("Properties from OS environment variables:");
                for (String key : propENV.stringPropertyNames()) {
                    LOG.debug("    {}={}", key, propENV.getProperty(key));
                }
            }
        }
        // load properties from JVM (override existing)
        Properties propJVM = null;
        if (mainConfigurationProperties.isAutoConfigurationSystemPropertiesEnabled()) {
            propJVM = MainHelper.loadJvmSystemPropertiesAsProperties(new String[] { "camel.main." });
            if (!propJVM.isEmpty()) {
                prop.putAll(propJVM);
                LOG.debug("Properties from JVM system properties:");
                for (String key : propJVM.stringPropertyNames()) {
                    LOG.debug("    {}={}", key, propJVM.getProperty(key));
                }
            }
        }

        // special for fail-fast as we need to know this early before we set all the other options
        String loc = "ENV";
        Object failFast = propENV != null ? propENV.remove("camel.main.autoconfigurationfailfast") : null;
        if (ObjectHelper.isNotEmpty(propJVM)) {
            Object val = propJVM.remove("camel.main.autoconfigurationfailfast");
            if (ObjectHelper.isNotEmpty(val)) {
                loc = "SYS";
                failFast = val;
            }
        }
        if (ObjectHelper.isNotEmpty(failFast)) {
            mainConfigurationProperties
                    .setAutoConfigurationFailFast(CamelContextHelper.parseBoolean(camelContext, failFast.toString()));
            autoConfiguredProperties.put(loc, "camel.main.autoConfigurationFailFast", failFast.toString());
        } else {
            loc = prop.getLocation("camel.main.autoConfigurationFailFast");
            failFast = prop.remove("camel.main.autoConfigurationFailFast");
            if (ObjectHelper.isNotEmpty(failFast)) {
                mainConfigurationProperties
                        .setAutoConfigurationFailFast(CamelContextHelper.parseBoolean(camelContext, failFast.toString()));
                autoConfiguredProperties.put(loc, "camel.main.autoConfigurationFailFast", failFast.toString());
            }
        }
    }

    protected void autoConfigurationSingleOption(
            CamelContext camelContext, OrderedLocationProperties autoConfiguredProperties,
            String optionName, Function<String, Object> setter) {

        String lowerOptionName = optionName.toLowerCase(Locale.US);

        // load properties
        OrderedLocationProperties prop = (OrderedLocationProperties) camelContext.getPropertiesComponent()
                .loadProperties(name -> name.startsWith("camel."), MainHelper::optionKey);
        LOG.debug("Properties from Camel properties component:");
        for (String key : prop.stringPropertyNames()) {
            LOG.debug("    {}={}", key, prop.getProperty(key));
        }
        // load properties from ENV (override existing)
        Properties propENV = null;
        if (mainConfigurationProperties.isAutoConfigurationEnvironmentVariablesEnabled()) {
            propENV = MainHelper.loadEnvironmentVariablesAsProperties(new String[] { "camel.main." });
            if (!propENV.isEmpty()) {
                prop.putAll("ENV", propENV);
                LOG.debug("Properties from OS environment variables:");
                for (String key : propENV.stringPropertyNames()) {
                    LOG.debug("    {}={}", key, propENV.getProperty(key));
                }
            }
        }
        // load properties from JVM (override existing)
        Properties propJVM = null;
        if (mainConfigurationProperties.isAutoConfigurationSystemPropertiesEnabled()) {
            propJVM = MainHelper.loadJvmSystemPropertiesAsProperties(new String[] { "camel.main." });
            if (!propJVM.isEmpty()) {
                prop.putAll("SYS", propJVM);
                LOG.debug("Properties from JVM system properties:");
                for (String key : propJVM.stringPropertyNames()) {
                    LOG.debug("    {}={}", key, propJVM.getProperty(key));
                }
            }
        }
        // SYS and ENV take priority (ENV are in lower-case keys)
        String loc = "ENV";
        Object value = propENV != null ? propENV.remove(lowerOptionName) : null;
        if (ObjectHelper.isNotEmpty(propJVM)) {
            Object val = propJVM.remove(optionName);
            if (ObjectHelper.isNotEmpty(val)) {
                // SYS override ENV
                loc = "SYS";
                value = val;
            }
        }
        if (ObjectHelper.isEmpty(value)) {
            // then try properties
            loc = prop.getLocation(optionName);
            value = prop.remove(optionName);
        }
        if (ObjectHelper.isEmpty(value)) {
            // fallback to initial properties
            value = getInitialProperties().getProperty(optionName);
            loc = "initial";
        }

        // set the option if we have a value
        if (ObjectHelper.isNotEmpty(value)) {
            String str = CamelContextHelper.parseText(camelContext, value.toString());
            setter.apply(str);
            autoConfiguredProperties.put(loc, optionName, value.toString());
        }
    }

    /**
     * Configures CamelContext from the {@link MainConfigurationProperties} properties.
     */
    protected void doConfigureCamelContextFromMainConfiguration(
            CamelContext camelContext, MainConfigurationProperties config,
            OrderedLocationProperties autoConfiguredProperties)
            throws Exception {

        if (ObjectHelper.isNotEmpty(config.getFileConfigurations())) {
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

        // org.apache.camel.spring.boot.CamelAutoConfiguration (camel-spring-boot) also calls the methods
        // on DefaultConfigurationConfigurer, but the CamelContext being configured is already
        // org.apache.camel.spring.boot.SpringBootCamelContext, which has access to Spring's ApplicationContext.
        // That's why DefaultConfigurationConfigurer.afterConfigure() can alter CamelContext using beans from
        // Spring's ApplicationContext.
        // so here, before configuring Camel Context, we can process the registry and let Main implementations
        // decide how to do it
        preProcessCamelRegistry(camelContext, config);

        // lookup and configure SPI beans
        DefaultConfigurationConfigurer.afterConfigure(camelContext);

        // now configure context/resilience4j/rest with additional properties
        OrderedLocationProperties prop = (OrderedLocationProperties) camelContext.getPropertiesComponent()
                .loadProperties(name -> name.startsWith("camel."), MainHelper::optionKey);

        // load properties from ENV (override existing)
        if (mainConfigurationProperties.isAutoConfigurationEnvironmentVariablesEnabled()) {
            Properties propENV
                    = MainHelper.loadEnvironmentVariablesAsProperties(new String[] { "camel.component.properties." });
            if (!propENV.isEmpty()) {
                prop.putAll("ENV", propENV);
                LOG.debug("Properties from OS environment variables:");
                for (String key : propENV.stringPropertyNames()) {
                    LOG.debug("    {}={}", key, propENV.getProperty(key));
                }
            }
        }
        // load properties from JVM (override existing)
        if (mainConfigurationProperties.isAutoConfigurationSystemPropertiesEnabled()) {
            Properties propJVM = MainHelper.loadJvmSystemPropertiesAsProperties(new String[] { "camel.component.properties." });
            if (!propJVM.isEmpty()) {
                prop.putAll("SYS", propJVM);
                LOG.debug("Properties from JVM system properties:");
                for (String key : propJVM.stringPropertyNames()) {
                    LOG.debug("    {}={}", key, propJVM.getProperty(key));
                }
            }
        }

        OrderedLocationProperties contextProperties = new OrderedLocationProperties();
        OrderedLocationProperties resilience4jProperties = new OrderedLocationProperties();
        OrderedLocationProperties faultToleranceProperties = new OrderedLocationProperties();
        OrderedLocationProperties restProperties = new OrderedLocationProperties();
        OrderedLocationProperties vaultProperties = new OrderedLocationProperties();
        OrderedLocationProperties threadPoolProperties = new OrderedLocationProperties();
        OrderedLocationProperties healthProperties = new OrderedLocationProperties();
        OrderedLocationProperties lraProperties = new OrderedLocationProperties();
        OrderedLocationProperties otelProperties = new OrderedLocationProperties();
        OrderedLocationProperties metricsProperties = new OrderedLocationProperties();
        OrderedLocationProperties routeTemplateProperties = new OrderedLocationProperties();
        OrderedLocationProperties variableProperties = new OrderedLocationProperties();
        OrderedLocationProperties beansProperties = new OrderedLocationProperties();
        OrderedLocationProperties devConsoleProperties = new OrderedLocationProperties();
        OrderedLocationProperties globalOptions = new OrderedLocationProperties();
        OrderedLocationProperties httpServerProperties = new OrderedLocationProperties();
        OrderedLocationProperties sslProperties = new OrderedLocationProperties();
        OrderedLocationProperties debuggerProperties = new OrderedLocationProperties();
        OrderedLocationProperties tracerProperties = new OrderedLocationProperties();
        OrderedLocationProperties routeControllerProperties = new OrderedLocationProperties();
        for (String key : prop.stringPropertyNames()) {
            String loc = prop.getLocation(key);
            if (startsWithIgnoreCase(key, "camel.context.")) {
                // grab the value
                String value = prop.getProperty(key);
                String option = key.substring(14);
                validateOptionAndValue(key, option, value);
                contextProperties.put(loc, optionKey(option), value);
            } else if (startsWithIgnoreCase(key, "camel.resilience4j.")) {
                // grab the value
                String value = prop.getProperty(key);
                String option = key.substring(19);
                validateOptionAndValue(key, option, value);
                resilience4jProperties.put(loc, optionKey(option), value);
            } else if (startsWithIgnoreCase(key, "camel.faulttolerance.")) {
                // grab the value
                String value = prop.getProperty(key);
                String option = key.substring(21);
                validateOptionAndValue(key, option, value);
                faultToleranceProperties.put(loc, optionKey(option), value);
            } else if (startsWithIgnoreCase(key, "camel.rest.")) {
                // grab the value
                String value = prop.getProperty(key);
                String option = key.substring(11);
                validateOptionAndValue(key, option, value);
                restProperties.put(loc, optionKey(option), value);
            } else if (startsWithIgnoreCase(key, "camel.vault.")) {
                // grab the value
                String value = prop.getProperty(key);
                String option = key.substring(12);
                validateOptionAndValue(key, option, value);
                vaultProperties.put(loc, optionKey(option), value);
            } else if (startsWithIgnoreCase(key, "camel.threadpool.")) {
                // grab the value
                String value = prop.getProperty(key);
                String option = key.substring(17);
                validateOptionAndValue(key, option, value);
                threadPoolProperties.put(loc, optionKey(option), value);
            } else if (startsWithIgnoreCase(key, "camel.health.")) {
                // grab the value
                String value = prop.getProperty(key);
                String option = key.substring(13);
                validateOptionAndValue(key, option, value);
                healthProperties.put(loc, optionKey(option), value);
            } else if (startsWithIgnoreCase(key, "camel.lra.")) {
                // grab the value
                String value = prop.getProperty(key);
                String option = key.substring(10);
                validateOptionAndValue(key, option, value);
                lraProperties.put(loc, optionKey(option), value);
            } else if (startsWithIgnoreCase(key, "camel.opentelemetry.")) {
                // grab the value
                String value = prop.getProperty(key);
                String option = key.substring(20);
                validateOptionAndValue(key, option, value);
                otelProperties.put(loc, optionKey(option), value);
            } else if (startsWithIgnoreCase(key, "camel.metrics.")) {
                // grab the value
                String value = prop.getProperty(key);
                String option = key.substring(14);
                validateOptionAndValue(key, option, value);
                metricsProperties.put(loc, optionKey(option), value);
            } else if (startsWithIgnoreCase(key, "camel.routeTemplate")) {
                // grab the value
                String value = prop.getProperty(key);
                String option = key.substring(19);
                validateOptionAndValue(key, option, value);
                routeTemplateProperties.put(loc, optionKey(option), value);
            } else if (startsWithIgnoreCase(key, "camel.devConsole.")) {
                // grab the value
                String value = prop.getProperty(key);
                String option = key.substring(17);
                validateOptionAndValue(key, option, value);
                devConsoleProperties.put(loc, optionKey(option), value);
            } else if (startsWithIgnoreCase(key, "camel.variable.")) {
                // grab the value
                String value = prop.getProperty(key);
                String option = key.substring(15);
                validateOptionAndValue(key, option, value);
                variableProperties.put(loc, optionKey(option), value);
            } else if (startsWithIgnoreCase(key, "camel.beans.")) {
                // grab the value
                String value = prop.getProperty(key);
                String option = key.substring(12);
                validateOptionAndValue(key, option, value);
                beansProperties.put(loc, optionKey(option), value);
            } else if (startsWithIgnoreCase(key, "camel.globalOptions.")) {
                // grab the value
                String value = prop.getProperty(key);
                String option = key.substring(20);
                validateOptionAndValue(key, option, value);
                globalOptions.put(loc, optionKey(option), value);
            } else if (startsWithIgnoreCase(key, "camel.server.")) {
                // grab the value
                String value = prop.getProperty(key);
                String option = key.substring(13);
                validateOptionAndValue(key, option, value);
                httpServerProperties.put(loc, optionKey(option), value);
            } else if (startsWithIgnoreCase(key, "camel.ssl.")) {
                // grab the value
                String value = prop.getProperty(key);
                String option = key.substring(10);
                validateOptionAndValue(key, option, value);
                sslProperties.put(loc, optionKey(option), value);
            } else if (startsWithIgnoreCase(key, "camel.debug.")) {
                // grab the value
                String value = prop.getProperty(key);
                String option = key.substring(12);
                validateOptionAndValue(key, option, value);
                debuggerProperties.put(loc, optionKey(option), value);
            } else if (startsWithIgnoreCase(key, "camel.trace.")) {
                // grab the value
                String value = prop.getProperty(key);
                String option = key.substring(12);
                validateOptionAndValue(key, option, value);
                tracerProperties.put(loc, optionKey(option), value);
            } else if (startsWithIgnoreCase(key, "camel.routeController.")) {
                // grab the value
                String value = prop.getProperty(key);
                String option = key.substring(22);
                validateOptionAndValue(key, option, value);
                routeControllerProperties.put(loc, optionKey(option), value);
            }
        }

        // global options first
        if (!globalOptions.isEmpty()) {
            for (var name : globalOptions.stringPropertyNames()) {
                Object value = globalOptions.getProperty(name);
                mainConfigurationProperties.addGlobalOption(name, value);
            }
        }
        // create variables first as they may be used later
        if (!variableProperties.isEmpty()) {
            LOG.debug("Auto-configuring Variables from loaded properties: {}", variableProperties.size());
            MainSupportModelConfigurer.setVariableProperties(camelContext, variableProperties, autoConfiguredProperties);
        }
        // create beans first as they may be used later
        if (!beansProperties.isEmpty()) {
            LOG.debug("Creating and binding beans to registry from loaded properties: {}", beansProperties.size());
            bindBeansToRegistry(camelContext, beansProperties, "camel.beans.",
                    mainConfigurationProperties.isAutoConfigurationFailFast(),
                    mainConfigurationProperties.isAutoConfigurationLogSummary(), true, autoConfiguredProperties);
        }
        if (!contextProperties.isEmpty()) {
            LOG.debug("Auto-configuring CamelContext from loaded properties: {}", contextProperties.size());
            setPropertiesOnTarget(camelContext, camelContext, contextProperties, "camel.context.",
                    mainConfigurationProperties.isAutoConfigurationFailFast(), true, autoConfiguredProperties);
            setPropertiesOnTarget(camelContext, camelContext.getCamelContextExtension(), contextProperties,
                    "camel.context.",
                    mainConfigurationProperties.isAutoConfigurationFailFast(), true, autoConfiguredProperties);
        }
        if (!restProperties.isEmpty() || mainConfigurationProperties.hasRestConfiguration()) {
            RestConfigurationProperties rest = mainConfigurationProperties.rest();
            LOG.debug("Auto-configuring Rest DSL from loaded properties: {}", restProperties.size());
            setPropertiesOnTarget(camelContext, rest, restProperties, "camel.rest.",
                    mainConfigurationProperties.isAutoConfigurationFailFast(), true, autoConfiguredProperties);
            camelContext.setRestConfiguration(rest);
        }
        if (!httpServerProperties.isEmpty() || mainConfigurationProperties.hasHttpServerConfiguration()) {
            LOG.debug("Auto-configuring HTTP Server from loaded properties: {}", httpServerProperties.size());
            setHttpServerProperties(camelContext, httpServerProperties,
                    mainConfigurationProperties.isAutoConfigurationFailFast(),
                    autoConfiguredProperties);
        }
        if (!vaultProperties.isEmpty() || mainConfigurationProperties.hasVaultConfiguration()) {
            LOG.debug("Auto-configuring Vault from loaded properties: {}", vaultProperties.size());
            setVaultProperties(camelContext, vaultProperties, mainConfigurationProperties.isAutoConfigurationFailFast(),
                    autoConfiguredProperties);
        }
        if (!threadPoolProperties.isEmpty() || mainConfigurationProperties.hasThreadPoolConfiguration()) {
            LOG.debug("Auto-configuring Thread Pool from loaded properties: {}", threadPoolProperties.size());
            MainSupportModelConfigurer.setThreadPoolProperties(camelContext, mainConfigurationProperties, threadPoolProperties,
                    autoConfiguredProperties);
        }
        // need to let camel-main setup health-check using its convention over configuration
        boolean hc = mainConfigurationProperties.health().getEnabled() != null; // health-check is enabled by default
        if (hc || !healthProperties.isEmpty() || mainConfigurationProperties.hasHealthCheckConfiguration()) {
            LOG.debug("Auto-configuring HealthCheck from loaded properties: {}", healthProperties.size());
            setHealthCheckProperties(camelContext, healthProperties,
                    autoConfiguredProperties);
        }
        if (!routeTemplateProperties.isEmpty()) {
            LOG.debug("Auto-configuring Route templates from loaded properties: {}", routeTemplateProperties.size());
            setRouteTemplateProperties(camelContext, routeTemplateProperties,
                    autoConfiguredProperties);
        }
        if (!lraProperties.isEmpty() || mainConfigurationProperties.hasLraConfiguration()) {
            LOG.debug("Auto-configuring Saga LRA from loaded properties: {}", lraProperties.size());
            setLraCheckProperties(camelContext, lraProperties, mainConfigurationProperties.isAutoConfigurationFailFast(),
                    autoConfiguredProperties);
        }
        if (!otelProperties.isEmpty() || mainConfigurationProperties.hasOtelConfiguration()) {
            LOG.debug("Auto-configuring OpenTelemetry from loaded properties: {}", otelProperties.size());
            setOtelProperties(camelContext, otelProperties, mainConfigurationProperties.isAutoConfigurationFailFast(),
                    autoConfiguredProperties);
        }
        if (!metricsProperties.isEmpty() || mainConfigurationProperties.hasMetricsConfiguration()) {
            LOG.debug("Auto-configuring Micrometer metrics from loaded properties: {}", metricsProperties.size());
            setMetricsProperties(camelContext, metricsProperties, mainConfigurationProperties.isAutoConfigurationFailFast(),
                    autoConfiguredProperties);
        }
        if (!devConsoleProperties.isEmpty()) {
            LOG.debug("Auto-configuring Dev Console from loaded properties: {}", devConsoleProperties.size());
            setDevConsoleProperties(camelContext, devConsoleProperties,
                    mainConfigurationProperties.isAutoConfigurationFailFast(),
                    autoConfiguredProperties);
        }
        if (!sslProperties.isEmpty() || mainConfigurationProperties.hasSslConfiguration()) {
            LOG.debug("Auto-configuring SSL from loaded properties: {}", sslProperties.size());
            setSslProperties(camelContext, sslProperties,
                    mainConfigurationProperties.isAutoConfigurationFailFast(),
                    autoConfiguredProperties);
        }
        if (!debuggerProperties.isEmpty() || mainConfigurationProperties.hasDebuggerConfiguration()) {
            LOG.debug("Auto-configuring Debugger from loaded properties: {}", debuggerProperties.size());
            setDebuggerProperties(camelContext, debuggerProperties,
                    mainConfigurationProperties.isAutoConfigurationFailFast(),
                    autoConfiguredProperties);
        }
        if (!tracerProperties.isEmpty() || mainConfigurationProperties.hasTracerConfiguration()) {
            LOG.debug("Auto-configuring Tracer from loaded properties: {}", tracerProperties.size());
            setTracerProperties(camelContext, tracerProperties,
                    mainConfigurationProperties.isAutoConfigurationFailFast(),
                    autoConfiguredProperties);
        }
        if (!routeControllerProperties.isEmpty() || mainConfigurationProperties.hasRouteControllerConfiguration()) {
            LOG.debug("Auto-configuring Route Controller from loaded properties: {}", routeControllerProperties.size());
            setRouteControllerProperties(camelContext, routeControllerProperties,
                    mainConfigurationProperties.isAutoConfigurationFailFast(),
                    autoConfiguredProperties);
        }

        // configure which requires access to the model
        MainSupportModelConfigurer.configureModelCamelContext(camelContext, mainConfigurationProperties,
                autoConfiguredProperties, resilience4jProperties, faultToleranceProperties);

        // log which options was not set
        if (!variableProperties.isEmpty()) {
            variableProperties.forEach((k, v) -> {
                LOG.warn("Property not auto-configured: camel.variable.{}={}", k, v);
            });
        }
        if (!beansProperties.isEmpty()) {
            beansProperties.forEach((k, v) -> {
                LOG.warn("Property not auto-configured: camel.beans.{}={}", k, v);
            });
        }
        if (!contextProperties.isEmpty()) {
            contextProperties.forEach((k, v) -> {
                LOG.warn("Property not auto-configured: camel.context.{}={}", k, v);
            });
        }
        if (!resilience4jProperties.isEmpty()) {
            resilience4jProperties.forEach((k, v) -> {
                LOG.warn("Property not auto-configured: camel.resilience4j.{}={}", k, v);
            });
        }
        if (!faultToleranceProperties.isEmpty()) {
            faultToleranceProperties.forEach((k, v) -> {
                LOG.warn("Property not auto-configured: camel.faulttolerance.{}={}", k, v);
            });
        }
        if (!restProperties.isEmpty()) {
            restProperties.forEach((k, v) -> {
                LOG.warn("Property not auto-configured: camel.rest.{}={}", k, v);
            });
        }
        if (!vaultProperties.isEmpty()) {
            vaultProperties.forEach((k, v) -> {
                LOG.warn("Property not auto-configured: camel.vault.{}={}", k, v);
            });
        }
        if (!threadPoolProperties.isEmpty()) {
            threadPoolProperties.forEach((k, v) -> {
                LOG.warn("Property not auto-configured: camel.threadpool.{}={}", k, v);
            });
        }
        if (!healthProperties.isEmpty()) {
            healthProperties.forEach((k, v) -> {
                LOG.warn("Property not auto-configured: camel.health.{}={}", k, v);
            });
        }
        if (!sslProperties.isEmpty()) {
            sslProperties.forEach((k, v) -> {
                LOG.warn("Property not auto-configured: camel.ssl.{}={}", k, v);
            });
        }
        if (!debuggerProperties.isEmpty()) {
            debuggerProperties.forEach((k, v) -> {
                LOG.warn("Property not auto-configured: camel.debug.{}={}", k, v);
            });
        }
        if (!routeControllerProperties.isEmpty()) {
            routeControllerProperties.forEach((k, v) -> {
                LOG.warn("Property not auto-configured: camel.routeController.{}={}", k, v);
            });
        }
        if (!devConsoleProperties.isEmpty()) {
            devConsoleProperties.forEach((k, v) -> {
                LOG.warn("Property not auto-configured: camel.devConsole.{}={}", k, v);
            });
        }
        if (!routeTemplateProperties.isEmpty()) {
            routeTemplateProperties.forEach((k, v) -> {
                LOG.warn("Property not auto-configured: camel.routetemplate.{}={}", k, v);
            });
        }
        if (!lraProperties.isEmpty()) {
            lraProperties.forEach((k, v) -> {
                LOG.warn("Property not auto-configured: camel.lra.{}={}", k, v);
            });
        }
        if (!otelProperties.isEmpty()) {
            otelProperties.forEach((k, v) -> {
                LOG.warn("Property not auto-configured: camel.opentelemetry.{}={}", k, v);
            });
        }
        if (!httpServerProperties.isEmpty()) {
            httpServerProperties.forEach((k, v) -> {
                LOG.warn("Property not auto-configured: camel.server.{}={}", k, v);
            });
        }

        // and call after all properties are set
        DefaultConfigurationConfigurer.afterPropertiesSet(camelContext);
        // and configure vault
        DefaultConfigurationConfigurer.configureVault(camelContext);
    }

    /**
     * Main implementation may do some additional configuration of the {@link Registry} before it's used to
     * (re)configure Camel context.
     */
    protected void preProcessCamelRegistry(CamelContext camelContext, MainConfigurationProperties config) {
    }

    /**
     * Main implementation may do some additional configuration of the {@link Registry} after loading the routes, but
     * before the routes are started.
     */
    protected void postProcessCamelRegistry(CamelContext camelContext, MainConfigurationProperties config) {
    }

    private void setRouteTemplateProperties(
            CamelContext camelContext, OrderedLocationProperties routeTemplateProperties,
            OrderedLocationProperties autoConfiguredProperties) {

        // store the route template parameters as a source and register it on the camel context
        PropertiesRouteTemplateParametersSource source = new PropertiesRouteTemplateParametersSource();
        for (Map.Entry<Object, Object> entry : routeTemplateProperties.entrySet()) {
            String key = entry.getKey().toString();
            String id = StringHelper.between(key, "[", "]");
            key = StringHelper.after(key, "].");
            source.addParameter(id, key, entry.getValue());
        }
        camelContext.getRegistry().bind("CamelMainRouteTemplateParametersSource", RouteTemplateParameterSource.class, source);

        // lets sort by keys
        Map<String, Object> sorted = new TreeMap<>(routeTemplateProperties.asMap());
        sorted.forEach((k, v) -> {
            String loc = routeTemplateProperties.getLocation(k);
            autoConfiguredProperties.put(loc, "camel.routeTemplate" + k, v.toString());
        });
        routeTemplateProperties.clear();
    }

    private void setHealthCheckProperties(
            CamelContext camelContext, OrderedLocationProperties healthCheckProperties,
            OrderedLocationProperties autoConfiguredProperties) {

        HealthConfigurationProperties health = mainConfigurationProperties.health();

        setPropertiesOnTarget(camelContext, health, healthCheckProperties, "camel.health.",
                mainConfigurationProperties.isAutoConfigurationFailFast(), true, autoConfiguredProperties);

        if (health.getEnabled() != null && !health.getEnabled()) {
            // health-check is disabled
            return;
        }

        // auto-detect camel-health on classpath
        HealthCheckRegistry hcr = camelContext.getCamelContextExtension().getContextPlugin(HealthCheckRegistry.class);
        if (hcr == null) {
            if (health.getEnabled() != null && health.getEnabled()) {
                LOG.warn("Cannot find HealthCheckRegistry from classpath. Add camel-health to classpath.");
            }
            return;
        }

        if (health.getEnabled() != null) {
            hcr.setEnabled(health.getEnabled());
        }
        if (health.getExcludePattern() != null) {
            hcr.setExcludePattern(health.getExcludePattern());
        }
        if (health.getExposureLevel() != null) {
            hcr.setExposureLevel(health.getExposureLevel());
        }
        if (health.getInitialState() != null) {
            hcr.setInitialState(camelContext.getTypeConverter().convertTo(HealthCheck.State.class, health.getInitialState()));
        }

        // context is enabled by default
        if (hcr.isEnabled()) {
            HealthCheck hc = (HealthCheck) hcr.resolveById("context");
            if (hc != null) {
                hcr.register(hc);
            }
        }
        // routes are enabled by default
        if (hcr.isEnabled()) {
            HealthCheckRepository hc = hcr.getRepository("routes").orElse((HealthCheckRepository) hcr.resolveById("routes"));
            if (hc != null) {
                if (health.getRoutesEnabled() != null) {
                    hc.setEnabled(health.getRoutesEnabled());
                }
                hcr.register(hc);
            }
        }
        // producers are disabled by default
        if (hcr.isEnabled()) {
            HealthCheckRepository hc
                    = hcr.getRepository("producers").orElse((HealthCheckRepository) hcr.resolveById("producers"));
            if (hc != null) {
                if (health.getProducersEnabled() == null) {
                    hc.setEnabled(false); // disabled by default
                } else {
                    hc.setEnabled(health.getProducersEnabled());
                }
                hcr.register(hc);
            }
        }
        // consumers are enabled by default
        if (hcr.isEnabled()) {
            HealthCheckRepository hc
                    = hcr.getRepository("consumers").orElse((HealthCheckRepository) hcr.resolveById("consumers"));
            if (hc != null) {
                if (health.getConsumersEnabled() != null) {
                    hc.setEnabled(health.getConsumersEnabled());
                }
                hcr.register(hc);
            }
        }
        // registry are enabled by default
        if (hcr.isEnabled()) {
            HealthCheckRepository hc
                    = hcr.getRepository("registry").orElse((HealthCheckRepository) hcr.resolveById("registry"));
            if (hc != null) {
                if (health.getRegistryEnabled() != null) {
                    hc.setEnabled(health.getRegistryEnabled());
                }
                hcr.register(hc);
            }
        }
    }

    private void setLraCheckProperties(
            CamelContext camelContext, OrderedLocationProperties lraProperties,
            boolean failIfNotSet, OrderedLocationProperties autoConfiguredProperties)
            throws Exception {

        String loc = lraProperties.getLocation("enabled");
        Object obj = lraProperties.remove("enabled");
        if (ObjectHelper.isNotEmpty(obj)) {
            autoConfiguredProperties.put(loc, "camel.lra.enabled", obj.toString());
        }
        boolean enabled = obj != null ? CamelContextHelper.parseBoolean(camelContext, obj.toString()) : true;
        if (enabled) {
            CamelSagaService css = resolveLraSagaService(camelContext);
            setPropertiesOnTarget(camelContext, css, lraProperties, "camel.lra.", failIfNotSet, true, autoConfiguredProperties);
            // add as service so saga can be active
            camelContext.addService(css, true, true);
        }
    }

    private void setOtelProperties(
            CamelContext camelContext, OrderedLocationProperties otelProperties,
            boolean failIfNotSet, OrderedLocationProperties autoConfiguredProperties)
            throws Exception {

        String loc = otelProperties.getLocation("enabled");
        Object obj = otelProperties.remove("enabled");
        if (ObjectHelper.isNotEmpty(obj)) {
            autoConfiguredProperties.put(loc, "camel.opentelemetry.enabled", obj.toString());
        }
        boolean enabled = obj != null ? CamelContextHelper.parseBoolean(camelContext, obj.toString()) : true;
        if (enabled) {
            CamelTracingService otel = resolveOtelService(camelContext);
            setPropertiesOnTarget(camelContext, otel, otelProperties, "camel.opentelemetry.", failIfNotSet, true,
                    autoConfiguredProperties);
            if (camelContext.hasService(CamelTracingService.class) == null) {
                // add as service so tracing can be active
                camelContext.addService(otel, true, true);
            }
        }
    }

    private void setMetricsProperties(
            CamelContext camelContext, OrderedLocationProperties metricsProperties,
            boolean failIfNotSet, OrderedLocationProperties autoConfiguredProperties)
            throws Exception {

        String loc = metricsProperties.getLocation("enabled");
        Object obj = metricsProperties.remove("enabled");
        if (ObjectHelper.isNotEmpty(obj)) {
            autoConfiguredProperties.put(loc, "camel.metrics.enabled", obj.toString());
        }
        boolean enabled = obj != null ? CamelContextHelper.parseBoolean(camelContext, obj.toString()) : true;
        if (enabled) {
            CamelMetricsService micrometer = resolveMicrometerService(camelContext);
            setPropertiesOnTarget(camelContext, micrometer, metricsProperties, "camel.metrics.", failIfNotSet, true,
                    autoConfiguredProperties);
            if (camelContext.hasService(CamelMetricsService.class) == null) {
                // add as service so micrometer can be active
                camelContext.addService(micrometer, true, true);
            }
        }
    }

    private void setDevConsoleProperties(
            CamelContext camelContext, OrderedLocationProperties properties,
            boolean failIfNotSet, OrderedLocationProperties autoConfiguredProperties) {

        // make defensive copy as we mutate the map
        Set<String> keys = new LinkedHashSet<>(properties.asMap().keySet());
        // set properties per console
        for (String key : keys) {
            String name = StringHelper.before(key, ".");
            DevConsole console
                    = camelContext.getCamelContextExtension().getContextPlugin(DevConsoleRegistry.class).resolveById(name);
            if (console == null) {
                throw new IllegalArgumentException(
                        "Cannot resolve DevConsole with id: " + name);
            }
            // configure all the properties on the console at once (to ensure they are configured in right order)
            OrderedLocationProperties config = MainHelper.extractProperties(properties, name + ".");
            setPropertiesOnTarget(camelContext, console, config, "camel.devConsole." + name + ".", failIfNotSet, true,
                    autoConfiguredProperties);
        }
    }

    private void setHttpServerProperties(
            CamelContext camelContext, OrderedLocationProperties properties,
            boolean failIfNotSet, OrderedLocationProperties autoConfiguredProperties)
            throws Exception {

        HttpServerConfigurationProperties server = mainConfigurationProperties.httpServer();

        setPropertiesOnTarget(camelContext, server, properties, "camel.server.",
                mainConfigurationProperties.isAutoConfigurationFailFast(), true, autoConfiguredProperties);

        if (!server.isEnabled()) {
            // http server is disabled
            return;
        }

        // auto-detect camel-platform-http-main on classpath
        MainHttpServerFactory sf = resolveMainHttpServerFactory(camelContext);
        // create http server as a service managed by camel context
        Service http = sf.newHttpServer(server);
        // force eager starting as embedded http server is used for
        // container platform to check readiness and need to be started eager
        camelContext.addService(http, true, true);
    }

    private void setVaultProperties(
            CamelContext camelContext, OrderedLocationProperties properties,
            boolean failIfNotSet, OrderedLocationProperties autoConfiguredProperties) {

        if (mainConfigurationProperties.hasVaultConfiguration()) {
            camelContext.setVaultConfiguration(mainConfigurationProperties.vault());
        }
        VaultConfiguration target = camelContext.getVaultConfiguration();

        // make defensive copy as we mutate the map
        Set<String> keys = new LinkedHashSet<>(properties.asMap().keySet());
        // set properties per different vault component
        for (String key : keys) {
            String name = StringHelper.before(key, ".");
            if ("aws".equalsIgnoreCase(name)) {
                target = target.aws();
            }
            if ("gcp".equalsIgnoreCase(name)) {
                target = target.gcp();
            }
            if ("azure".equalsIgnoreCase(name)) {
                target = target.azure();
            }
            if ("hashicorp".equalsIgnoreCase(name)) {
                target = target.hashicorp();
            }
            // configure all the properties on the vault at once (to ensure they are configured in right order)
            OrderedLocationProperties config = MainHelper.extractProperties(properties, name + ".");
            setPropertiesOnTarget(camelContext, target, config, "camel.vault." + name + ".", failIfNotSet, true,
                    autoConfiguredProperties);
        }
    }

    private void setSslProperties(
            CamelContext camelContext, OrderedLocationProperties properties,
            boolean failIfNotSet, OrderedLocationProperties autoConfiguredProperties) {

        SSLConfigurationProperties sslConfig = mainConfigurationProperties.sslConfig();
        setPropertiesOnTarget(camelContext, sslConfig, properties, "camel.ssl.",
                failIfNotSet, true, autoConfiguredProperties);

        if (!sslConfig.isEnabled()) {
            return;
        }

        String password = sslConfig.getKeystorePassword();
        KeyStoreParameters ksp = new KeyStoreParameters();
        ksp.setResource(sslConfig.getKeyStore());
        ksp.setPassword(password);

        KeyManagersParameters kmp = new KeyManagersParameters();
        kmp.setKeyPassword(password);
        kmp.setKeyStore(ksp);

        final SSLContextParameters sslContextParameters = createSSLContextParameters(sslConfig, kmp);

        camelContext.setSSLContextParameters(sslContextParameters);
    }

    private static SSLContextParameters createSSLContextParameters(
            SSLConfigurationProperties sslConfig, KeyManagersParameters kmp) {
        TrustManagersParameters tmp = null;
        if (sslConfig.getTrustStore() != null) {
            KeyStoreParameters tsp = new KeyStoreParameters();
            tsp.setResource(sslConfig.getTrustStore());
            tsp.setPassword(sslConfig.getTrustStorePassword());

            tmp = new TrustManagersParameters();
            tmp.setKeyStore(tsp);
        }

        SSLContextServerParameters scsp = new SSLContextServerParameters();
        scsp.setClientAuthentication(sslConfig.getClientAuthentication());

        SSLContextParameters sslContextParameters = new SSLContextParameters();
        sslContextParameters.setProvider(sslConfig.getProvider());
        sslContextParameters.setSecureSocketProtocol(sslConfig.getSecureSocketProtocol());
        sslContextParameters.setCertAlias(sslConfig.getCertAlias());
        if (sslConfig.getSessionTimeout() > 0) {
            sslContextParameters.setSessionTimeout("" + sslConfig.getSessionTimeout());
        }
        sslContextParameters.setKeyManagers(kmp);
        sslContextParameters.setTrustManagers(tmp);
        sslContextParameters.setServerParameters(scsp);
        return sslContextParameters;
    }

    private void setDebuggerProperties(
            CamelContext camelContext, OrderedLocationProperties properties,
            boolean failIfNotSet, OrderedLocationProperties autoConfiguredProperties)
            throws Exception {

        DebuggerConfigurationProperties config = mainConfigurationProperties.debuggerConfig();
        setPropertiesOnTarget(camelContext, config, properties, "camel.debug.",
                failIfNotSet, true, autoConfiguredProperties);

        if (!config.isEnabled() && !config.isStandby()) {
            return;
        }

        // must enable source location and history
        // so debugger tooling knows to map breakpoints to source code
        camelContext.setSourceLocationEnabled(true);
        camelContext.setMessageHistory(true);

        // enable debugger on camel
        camelContext.setDebugging(config.isEnabled());
        camelContext.setDebugStandby(config.isStandby());

        BacklogDebugger debugger = DefaultBacklogDebugger.createDebugger(camelContext);
        debugger.setStandby(config.isStandby());
        debugger.setInitialBreakpoints(config.getBreakpoints());
        debugger.setSingleStepIncludeStartEnd(config.isSingleStepIncludeStartEnd());
        debugger.setBodyMaxChars(config.getBodyMaxChars());
        debugger.setBodyIncludeStreams(config.isBodyIncludeStreams());
        debugger.setBodyIncludeFiles(config.isBodyIncludeFiles());
        debugger.setIncludeExchangeProperties(config.isIncludeExchangeProperties());
        debugger.setIncludeExchangeVariables(config.isIncludeExchangeVariables());
        debugger.setIncludeException(config.isIncludeException());
        debugger.setLoggingLevel(config.getLoggingLevel().name());
        debugger.setSuspendMode(config.isWaitForAttach()); // this option is named wait-for-attach
        debugger.setFallbackTimeout(config.getFallbackTimeout());

        // enable jmx connector if port is set
        if (config.isJmxConnectorEnabled()) {
            DebuggerJmxConnectorService connector = new DebuggerJmxConnectorService();
            connector.setCreateConnector(true);
            connector.setRegistryPort(config.getJmxConnectorPort());
            camelContext.addService(connector);
        }

        // start debugger after context is started
        camelContext.addLifecycleStrategy(new LifecycleStrategySupport() {
            @Override
            public void onContextStarted(CamelContext context) {
                // only enable debugger if not in standby mode
                if (!debugger.isStandby()) {
                    debugger.enableDebugger();
                }
            }

            @Override
            public void onContextStopping(CamelContext context) {
                debugger.disableDebugger();
            }
        });

        camelContext.addService(debugger);
    }

    private void setTracerProperties(
            CamelContext camelContext, OrderedLocationProperties properties,
            boolean failIfNotSet, OrderedLocationProperties autoConfiguredProperties)
            throws Exception {

        TracerConfigurationProperties config = mainConfigurationProperties.tracerConfig();
        setPropertiesOnTarget(camelContext, config, properties, "camel.trace.",
                failIfNotSet, true, autoConfiguredProperties);

        if (!config.isEnabled() && !config.isStandby()) {
            return;
        }

        // must enable source location so tracer tooling knows to map breakpoints to source code
        camelContext.setSourceLocationEnabled(true);

        // enable tracer on camel
        camelContext.setBacklogTracing(config.isEnabled());
        camelContext.setBacklogTracingStandby(config.isStandby());
        camelContext.setBacklogTracingTemplates(config.isTraceTemplates());

        BacklogTracer tracer = org.apache.camel.impl.debugger.BacklogTracer.createTracer(camelContext);
        tracer.setEnabled(config.isEnabled());
        tracer.setStandby(config.isStandby());
        tracer.setBacklogSize(config.getBacklogSize());
        tracer.setRemoveOnDump(config.isRemoveOnDump());
        tracer.setBodyMaxChars(config.getBodyMaxChars());
        tracer.setBodyIncludeStreams(config.isBodyIncludeStreams());
        tracer.setBodyIncludeFiles(config.isBodyIncludeFiles());
        tracer.setIncludeExchangeProperties(config.isIncludeExchangeProperties());
        tracer.setIncludeExchangeVariables(config.isIncludeExchangeVariables());
        tracer.setIncludeException(config.isIncludeException());
        tracer.setTraceRests(config.isTraceRests());
        tracer.setTraceTemplates(config.isTraceTemplates());
        tracer.setTracePattern(config.getTracePattern());
        tracer.setTraceFilter(config.getTraceFilter());

        camelContext.getCamelContextExtension().addContextPlugin(BacklogTracer.class, tracer);
        camelContext.addService(tracer);
    }

    private void setRouteControllerProperties(
            CamelContext camelContext, OrderedLocationProperties properties,
            boolean failIfNotSet, OrderedLocationProperties autoConfiguredProperties)
            throws Exception {

        RouteControllerConfigurationProperties config = mainConfigurationProperties.routeControllerConfig();
        setPropertiesOnTarget(camelContext, config, properties, "camel.routeController.",
                failIfNotSet, true, autoConfiguredProperties);

        // supervising route controller
        if (config.isEnabled()) {
            SupervisingRouteController src = camelContext.getRouteController().supervising();
            if (config.getIncludeRoutes() != null) {
                src.setIncludeRoutes(config.getIncludeRoutes());
            }
            if (config.getExcludeRoutes() != null) {
                src.setExcludeRoutes(config.getExcludeRoutes());
            }
            if (config.getThreadPoolSize() > 0) {
                src.setThreadPoolSize(config.getThreadPoolSize());
            }
            if (config.getBackOffDelay() > 0) {
                src.setBackOffDelay(config.getBackOffDelay());
            }
            if (config.getInitialDelay() > 0) {
                src.setInitialDelay(config.getInitialDelay());
            }
            if (config.getBackOffMaxAttempts() > 0) {
                src.setBackOffMaxAttempts(config.getBackOffMaxAttempts());
            }
            if (config.getBackOffMaxDelay() > 0) {
                src.setBackOffMaxDelay(config.getBackOffDelay());
            }
            if (config.getBackOffMaxElapsedTime() > 0) {
                src.setBackOffMaxElapsedTime(config.getBackOffMaxElapsedTime());
            }
            if (config.getBackOffMultiplier() > 0) {
                src.setBackOffMultiplier(config.getBackOffMultiplier());
            }
            src.setUnhealthyOnExhausted(config.isUnhealthyOnExhausted());
            src.setUnhealthyOnRestarting(config.isUnhealthyOnRestarting());
        }
    }

    private void bindBeansToRegistry(
            CamelContext camelContext, OrderedLocationProperties properties,
            String optionPrefix, boolean failIfNotSet, boolean logSummary, boolean ignoreCase,
            OrderedLocationProperties autoConfiguredProperties)
            throws Exception {

        // make defensive copy as we mutate the map
        Set<String> keys = new LinkedHashSet<>(properties.asMap().keySet());
        // find names of beans (dot style)
        final Set<String> beansDot
                = properties.keySet().stream()
                        .map(k -> StringHelper.before(k.toString(), ".", k.toString()))
                        .filter(k -> k.indexOf('[') == -1)
                        .collect(Collectors.toSet());

        // find names of beans (map style)
        final Set<String> beansMap
                = properties.keySet().stream()
                        .map(k -> StringHelper.before(k.toString(), "[", k.toString()))
                        .filter(k -> k.indexOf('.') == -1)
                        .collect(Collectors.toSet());

        // then create beans first (beans with #class values etc)
        for (String key : keys) {
            if (key.indexOf('.') == -1 && key.indexOf('[') == -1) {
                Object value = properties.remove(key);
                Object bean = PropertyBindingSupport.resolveBean(camelContext, value);
                if (bean == null) {
                    throw new IllegalArgumentException(
                            "Cannot create/resolve bean with name " + key + " from value: " + value);
                }
                // register bean
                if (logSummary) {
                    LOG.info("Binding bean: {} (type: {}) to the registry", key, ObjectHelper.classCanonicalName(bean));
                } else {
                    LOG.debug("Binding bean: {} (type: {}) to the registry", key, ObjectHelper.classCanonicalName(bean));
                }
                camelContext.getRegistry().bind(key, bean);
            }
        }
        // create map beans if none already exists
        for (String name : beansMap) {
            if (camelContext.getRegistry().lookupByName(name) == null) {

                // is the config list or map style
                OrderedLocationProperties config = MainHelper.extractProperties(properties, name + "[", "]", false);
                boolean list = config.keySet().stream().map(Object::toString).allMatch(StringHelper::isDigit);

                // register bean as a list or map
                Object bean = list ? new ArrayList<>() : new LinkedHashMap<>();
                if (logSummary) {
                    LOG.info("Binding bean: {} (type: {}) to the registry", name, ObjectHelper.classCanonicalName(bean));
                } else {
                    LOG.debug("Binding bean: {} (type: {}) to the registry", name, ObjectHelper.classCanonicalName(bean));
                }
                camelContext.getRegistry().bind(name, bean);
            }
        }

        // then set properties per bean (dot style)
        for (String name : beansDot) {
            Object bean = camelContext.getRegistry().lookupByName(name);
            if (bean == null) {
                throw new IllegalArgumentException(
                        "Cannot resolve bean with name " + name);
            }
            // configure all the properties on the bean at once (to ensure they are configured in right order)
            OrderedLocationProperties config = MainHelper.extractProperties(properties, name + ".");
            setPropertiesOnTarget(camelContext, bean, config, optionPrefix + name + ".", failIfNotSet, ignoreCase,
                    autoConfiguredProperties);
        }
        // then set properties per bean (map/list style)
        for (String name : beansMap) {
            Object bean = camelContext.getRegistry().lookupByName(name);
            if (bean == null) {
                throw new IllegalArgumentException(
                        "Cannot resolve bean with name " + name);
            }
            // configure all the properties on the bean at once (to ensure they are configured in right order)
            OrderedLocationProperties config = MainHelper.extractProperties(properties, name + "[", "]", true, key -> {
                // when configuring map/list we want the keys to include the square brackets
                // (so we know it is a map/list style and not dot style syntax)
                // and therefore only remove the option prefix name
                if (key.startsWith(name + "[")) {
                    return key.substring(name.length());
                }
                return key;
            });
            setPropertiesOnTarget(camelContext, bean, config, optionPrefix + name + ".", failIfNotSet, ignoreCase,
                    autoConfiguredProperties);
        }
    }

    protected void autoConfigurationPropertiesComponent(
            CamelContext camelContext, OrderedLocationProperties autoConfiguredProperties)
            throws Exception {
        // load properties
        OrderedLocationProperties prop = (OrderedLocationProperties) camelContext.getPropertiesComponent()
                .loadProperties(name -> name.startsWith("camel."), MainHelper::optionKey);

        // load properties from ENV (override existing)
        if (mainConfigurationProperties.isAutoConfigurationEnvironmentVariablesEnabled()) {
            Properties propENV
                    = MainHelper.loadEnvironmentVariablesAsProperties(new String[] { "camel.component.properties." });
            if (!propENV.isEmpty()) {
                prop.putAll("ENV", propENV);
            }
        }
        // load properties from JVM (override existing)
        if (mainConfigurationProperties.isAutoConfigurationSystemPropertiesEnabled()) {
            Properties propJVM = MainHelper.loadJvmSystemPropertiesAsProperties(new String[] { "camel.component.properties." });
            if (!propJVM.isEmpty()) {
                prop.putAll("SYS", propJVM);
            }
        }

        OrderedLocationProperties properties = new OrderedLocationProperties();

        for (String key : prop.stringPropertyNames()) {
            if (key.startsWith("camel.component.properties.")) {
                int dot = key.indexOf('.', 26);
                String option = dot == -1 ? "" : key.substring(dot + 1);
                String value = prop.getProperty(key, "");
                validateOptionAndValue(key, option, value);
                String loc = prop.getLocation(key);
                properties.put(loc, optionKey(option), value);
            }
        }

        if (!properties.isEmpty()) {
            LOG.debug("Auto-configuring properties component from loaded properties: {}", properties.size());
            setPropertiesOnTarget(camelContext, camelContext.getPropertiesComponent(), properties,
                    "camel.component.properties.",
                    mainConfigurationProperties.isAutoConfigurationFailFast(), true, autoConfiguredProperties);
        }

        // log which options was not set
        if (!properties.isEmpty()) {
            properties.forEach((k, v) -> {
                LOG.warn("Property not auto-configured: camel.component.properties.{}={} on object: {}", k, v,
                        camelContext.getPropertiesComponent());
            });
        }
    }

    protected void autoConfigurationMainConfiguration(
            CamelContext camelContext, MainConfigurationProperties config, OrderedLocationProperties autoConfiguredProperties)
            throws Exception {
        // load properties
        OrderedLocationProperties prop = (OrderedLocationProperties) camelContext.getPropertiesComponent()
                .loadProperties(name -> name.startsWith("camel."), MainHelper::optionKey);

        // load properties from ENV (override existing)
        if (mainConfigurationProperties.isAutoConfigurationEnvironmentVariablesEnabled()) {
            Properties propENV = MainHelper.loadEnvironmentVariablesAsProperties(new String[] { "camel.main." });
            // special handling of these so remove them
            // ENV variables cannot use dash so replace with dot
            propENV.remove(MainConstants.INITIAL_PROPERTIES_LOCATION.replace('-', '.'));
            propENV.remove(MainConstants.OVERRIDE_PROPERTIES_LOCATION.replace('-', '.'));
            propENV.remove(MainConstants.PROPERTY_PLACEHOLDER_LOCATION.replace('-', '.'));
            if (!propENV.isEmpty()) {
                prop.putAll("ENV", propENV);
            }
        }
        // load properties from JVM (override existing)
        if (mainConfigurationProperties.isAutoConfigurationSystemPropertiesEnabled()) {
            Properties propJVM = MainHelper.loadJvmSystemPropertiesAsProperties(new String[] { "camel.main." });
            // special handling of these so remove them
            propJVM.remove(MainConstants.INITIAL_PROPERTIES_LOCATION);
            propJVM.remove(StringHelper.dashToCamelCase(MainConstants.INITIAL_PROPERTIES_LOCATION));
            propJVM.remove(MainConstants.OVERRIDE_PROPERTIES_LOCATION);
            propJVM.remove(StringHelper.dashToCamelCase(MainConstants.OVERRIDE_PROPERTIES_LOCATION));
            propJVM.remove(MainConstants.PROPERTY_PLACEHOLDER_LOCATION);
            propJVM.remove(StringHelper.dashToCamelCase(MainConstants.PROPERTY_PLACEHOLDER_LOCATION));
            if (!propJVM.isEmpty()) {
                prop.putAll("SYS", propJVM);
            }
        }

        OrderedLocationProperties properties = new OrderedLocationProperties();

        for (String key : prop.stringPropertyNames()) {
            if (key.startsWith("camel.main.")) {
                // grab the value
                String value = prop.getProperty(key);
                String option = key.substring(11);
                validateOptionAndValue(key, option, value);
                String loc = prop.getLocation(key);
                properties.put(loc, optionKey(option), value);
            }
        }

        if (!properties.isEmpty()) {
            LOG.debug("Auto-configuring main from loaded properties: {}", properties.size());
            setPropertiesOnTarget(camelContext, config, properties, "camel.main.",
                    mainConfigurationProperties.isAutoConfigurationFailFast(), true, autoConfiguredProperties);
        }

        // log which options was not set
        if (!properties.isEmpty()) {
            properties.forEach((k, v) -> {
                LOG.warn("Property not auto-configured: camel.main.{}={} on bean: {}", k, v, config);
            });
        }
    }

    protected void autoConfigurationFromProperties(
            CamelContext camelContext, OrderedLocationProperties autoConfiguredProperties)
            throws Exception {
        OrderedLocationProperties prop = new OrderedLocationProperties();

        // load properties from properties component (override existing)
        OrderedLocationProperties propPC = (OrderedLocationProperties) camelContext.getPropertiesComponent()
                .loadProperties(name -> name.startsWith("camel."));
        prop.putAll(propPC);

        // load properties from ENV (override existing)
        if (mainConfigurationProperties.isAutoConfigurationEnvironmentVariablesEnabled()) {
            Map<String, String> env = MainHelper
                    .filterEnvVariables(new String[] { "camel.component.", "camel.dataformat.", "camel.language." });
            LOG.debug("Gathered {} ENV variables to configure components, dataformats, languages", env.size());

            // special configuration when using ENV variables as we need to extract the ENV variables
            // that are for the out of the box components first, and then afterwards for any 3rd party custom components
            Properties propENV = new OrderedProperties();
            helper.addComponentEnvVariables(env, propENV, false);
            helper.addDataFormatEnvVariables(env, propENV, false);
            helper.addLanguageEnvVariables(env, propENV, false);

            if (!env.isEmpty()) {
                LOG.debug("Remaining {} ENV variables to configure custom components, dataformats, languages", env.size());
                helper.addComponentEnvVariables(env, propENV, true);
                helper.addDataFormatEnvVariables(env, propENV, true);
                helper.addLanguageEnvVariables(env, propENV, true);
            }

            if (!propENV.isEmpty()) {
                prop.putAll("ENV", propENV);
            }
        }
        // load properties from JVM (override existing)
        if (mainConfigurationProperties.isAutoConfigurationSystemPropertiesEnabled()) {
            Properties propJVM = MainHelper.loadJvmSystemPropertiesAsProperties(
                    new String[] { "camel.component.", "camel.dataformat.", "camel.language." });
            if (!propJVM.isEmpty()) {
                prop.putAll("SYS", propJVM);
            }
        }

        Map<PropertyOptionKey, OrderedLocationProperties> properties = new LinkedHashMap<>();

        // filter out wildcard properties
        for (String key : prop.stringPropertyNames()) {
            if (key.contains("*")) {
                String loc = prop.getLocation(key);
                wildcardProperties.put(loc, key, prop.getProperty(key));
            }
        }
        // and remove wildcards
        for (String key : wildcardProperties.stringPropertyNames()) {
            prop.remove(key);
        }

        doAutoConfigurationFromProperties(camelContext, prop, properties, false, autoConfiguredProperties);
    }

    protected void autoConfigurationFromReloadedProperties(
            CamelContext camelContext, OrderedLocationProperties reloadedProperties)
            throws Exception {

        Map<PropertyOptionKey, OrderedLocationProperties> properties = new LinkedHashMap<>();

        // filter out wildcard properties
        for (String key : reloadedProperties.stringPropertyNames()) {
            if (key.contains("*")) {
                String loc = reloadedProperties.getLocation(key);
                wildcardProperties.put(loc, key, reloadedProperties.getProperty(key));
            }
        }
        // and remove wildcards
        for (String key : wildcardProperties.stringPropertyNames()) {
            reloadedProperties.remove(key);
        }

        OrderedLocationProperties autoConfiguredProperties = new OrderedLocationProperties();
        doAutoConfigurationFromProperties(camelContext, reloadedProperties, properties, true, autoConfiguredProperties);

        // log summary of configurations
        if (mainConfigurationProperties.isAutoConfigurationLogSummary() && !autoConfiguredProperties.isEmpty()) {
            logConfigurationSummary(autoConfiguredProperties);
        }
    }

    protected void doAutoConfigurationFromProperties(
            CamelContext camelContext, OrderedLocationProperties prop,
            Map<PropertyOptionKey, OrderedLocationProperties> properties, boolean reload,
            OrderedLocationProperties autoConfiguredProperties)
            throws Exception {

        for (String key : prop.stringPropertyNames()) {
            computeProperties("camel.component.", key, prop, properties, name -> {
                boolean optional = name.startsWith("?");
                if (optional) {
                    name = name.substring(1);
                }
                if (reload) {
                    // force re-creating component on reload
                    camelContext.removeComponent(name);
                }
                // its an existing component name
                Component target = optional ? camelContext.hasComponent(name) : camelContext.getComponent(name);
                if (target == null && !optional) {
                    throw new IllegalArgumentException(
                            "Error configuring property: " + key + " because cannot find component with name " + name
                                                       + ". Make sure you have the component on the classpath");
                }
                if (target == null) {
                    return Collections.EMPTY_LIST;
                }
                return Collections.singleton(target);
            });
            computeProperties("camel.dataformat.", key, prop, properties, name -> {
                DataFormat target = camelContext.resolveDataFormat(name);
                if (target == null) {
                    throw new IllegalArgumentException(
                            "Error configuring property: " + key + " because cannot find dataformat with name " + name
                                                       + ". Make sure you have the dataformat on the classpath");
                }
                return Collections.singleton(target);
            });
            computeProperties("camel.language.", key, prop, properties, name -> {
                Language target;
                try {
                    target = camelContext.resolveLanguage(name);
                } catch (NoSuchLanguageException e) {
                    throw new IllegalArgumentException(
                            "Error configuring property: " + key + " because cannot find language with name " + name
                                                       + ". Make sure you have the language on the classpath");
                }
                return Collections.singleton(target);
            });
        }

        if (!properties.isEmpty()) {
            long total = properties.values().stream().mapToLong(Map::size).sum();
            LOG.debug("Auto-configuring {} components/dataformat/languages from loaded properties: {}", properties.size(),
                    total);
        }

        for (Map.Entry<PropertyOptionKey, OrderedLocationProperties> entry : properties.entrySet()) {
            setPropertiesOnTarget(
                    camelContext,
                    entry.getKey().getInstance(),
                    entry.getValue(),
                    entry.getKey().getOptionPrefix(),
                    mainConfigurationProperties.isAutoConfigurationFailFast(),
                    true,
                    autoConfiguredProperties);
        }

        // log which options was not set
        if (!properties.isEmpty()) {
            for (Map.Entry<PropertyOptionKey, OrderedLocationProperties> entry : properties.entrySet()) {
                PropertyOptionKey pok = entry.getKey();
                OrderedLocationProperties values = entry.getValue();
                values.forEach((k, v) -> {
                    String stringValue = v != null ? v.toString() : null;
                    LOG.warn("Property ({}={}) not auto-configured with name: {} on bean: {} with value: {}",
                            pok.getOptionPrefix() + "." + k, stringValue, k, pok.getInstance(), stringValue);
                });
            }
        }
    }

    protected void autowireWildcardProperties(CamelContext camelContext) {
        if (wildcardProperties.isEmpty()) {
            return;
        }

        // autowire any pre-existing components as they have been added before we are invoked
        for (String name : camelContext.getComponentNames()) {
            Component comp = camelContext.getComponent(name);
            doAutowireWildcardProperties(name, comp);
        }

        // and autowire any new components that may be added in the future
        camelContext.addLifecycleStrategy(new LifecycleStrategySupport() {
            @Override
            public void onComponentAdd(String name, Component component) {
                doAutowireWildcardProperties(name, component);
            }
        });

        // clear in case we reload later
        wildcardProperties.clear();
    }

    protected void doAutowireWildcardProperties(String name, Component component) {
        Map<PropertyOptionKey, OrderedLocationProperties> properties = new LinkedHashMap<>();
        OrderedLocationProperties autoConfiguredProperties = new OrderedLocationProperties();
        String match = ("camel.component." + name).toLowerCase(Locale.ENGLISH);

        for (String key : wildcardProperties.stringPropertyNames()) {
            String mKey = key.substring(0, key.indexOf('*')).toLowerCase(Locale.ENGLISH);
            if (match.startsWith(mKey)) {
                computeProperties("camel.component.", key, wildcardProperties, properties,
                        s -> Collections.singleton(component));
            }
        }

        try {
            for (Map.Entry<PropertyOptionKey, OrderedLocationProperties> entry : properties.entrySet()) {
                setPropertiesOnTarget(
                        camelContext,
                        entry.getKey().getInstance(),
                        entry.getValue(),
                        entry.getKey().getOptionPrefix(),
                        mainConfigurationProperties.isAutoConfigurationFailFast(),
                        true,
                        autoConfiguredProperties);
            }
            // log summary of configurations
            if (mainConfigurationProperties.isAutoConfigurationLogSummary() && !autoConfiguredProperties.isEmpty()) {
                boolean header = false;
                for (var entry : autoConfiguredProperties.entrySet()) {
                    String k = entry.getKey().toString();
                    Object v = entry.getValue();
                    String loc = locationSummary(autoConfiguredProperties, k);

                    // tone down logging noise for our own internal configurations
                    boolean debug = loc.contains("[camel-main]");
                    if (debug && !LOG.isDebugEnabled()) {
                        continue;
                    }

                    if (!header) {
                        LOG.info("Auto-configuration component {} summary", name);
                        header = true;
                    }

                    MainHelper.sensitiveAwareLogging(LOG, k, v, loc, debug);
                }
            }
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeException(e);
        }
    }

    private static CamelSagaService resolveLraSagaService(CamelContext camelContext) throws Exception {
        CamelSagaService answer = camelContext.hasService(CamelSagaService.class);
        if (answer == null) {
            answer = camelContext.getRegistry().findSingleByType(CamelSagaService.class);
        }
        if (answer == null) {
            answer = camelContext.getCamelContextExtension().getBootstrapFactoryFinder()
                    .newInstance("lra-saga-service", CamelSagaService.class)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Cannot find LRASagaService on classpath. Add camel-lra to classpath."));
        }
        return answer;
    }

    private static CamelTracingService resolveOtelService(CamelContext camelContext) throws Exception {
        CamelTracingService answer = camelContext.hasService(CamelTracingService.class);
        if (answer == null) {
            answer = camelContext.getRegistry().findSingleByType(CamelTracingService.class);
        }
        if (answer == null) {
            answer = camelContext.getCamelContextExtension().getBootstrapFactoryFinder()
                    .newInstance("opentelemetry-tracer", CamelTracingService.class)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Cannot find OpenTelemetryTracer on classpath. Add camel-opentelemetry to classpath."));
        }
        return answer;
    }

    private static CamelMetricsService resolveMicrometerService(CamelContext camelContext) throws Exception {
        CamelMetricsService answer = camelContext.hasService(CamelMetricsService.class);
        if (answer == null) {
            answer = camelContext.getRegistry().findSingleByType(CamelMetricsService.class);
        }
        if (answer == null) {
            answer = camelContext.getCamelContextExtension().getBootstrapFactoryFinder()
                    .newInstance("micrometer-prometheus", CamelMetricsService.class)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Cannot find CamelMetricsService on classpath. Add camel-micrometer-prometheus to classpath."));
        }
        return answer;
    }

    private static MainHttpServerFactory resolveMainHttpServerFactory(CamelContext camelContext) {
        // lookup in service registry first
        MainHttpServerFactory answer = camelContext.getRegistry().findSingleByType(MainHttpServerFactory.class);
        if (answer == null) {
            answer = camelContext.getCamelContextExtension().getBootstrapFactoryFinder()
                    .newInstance(MainConstants.PLATFORM_HTTP_SERVER, MainHttpServerFactory.class)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Cannot find MainHttpServerFactory on classpath. Add camel-platform-http-main to classpath."));
        }
        return CamelContextAware.trySetCamelContext(answer, camelContext);
    }

    private static final class PropertyPlaceholderListener implements PropertiesLookupListener {

        private final OrderedLocationProperties olp;

        public PropertyPlaceholderListener(OrderedLocationProperties olp) {
            this.olp = olp;
        }

        @Override
        public void onLookup(String name, String value, String defaultValue, String source) {
            if (source == null) {
                source = "unknown";
            }
            olp.put(source, name, value, defaultValue);
        }
    }

    private static class PlaceholderSummaryEventNotifier extends SimpleEventNotifierSupport implements NonManagedService {
        private final OrderedLocationProperties propertyPlaceholders;

        public PlaceholderSummaryEventNotifier(OrderedLocationProperties propertyPlaceholders) {
            this.propertyPlaceholders = propertyPlaceholders;
        }

        @Override
        public boolean isEnabled(CamelEvent event) {
            return event instanceof CamelEvent.CamelContextRoutesStartedEvent;
        }

        @Override
        public void notify(CamelEvent event) throws Exception {
            // log summary of configurations
            if (!propertyPlaceholders.isEmpty()) {
                boolean header = true;
                for (var entry : propertyPlaceholders.entrySet()) {
                    String k = entry.getKey().toString();
                    Object v = entry.getValue();
                    Object dv = propertyPlaceholders.getDefaultValue(k);
                    // skip logging configurations that are using default-value
                    // or a kamelet that uses templateId as a parameter
                    boolean same = ObjectHelper.equal(v, dv);
                    boolean skip = "templateId".equals(k);
                    if (!same && !skip) {
                        if (header) {
                            LOG.info("Property-placeholders summary");
                            header = false;
                        }
                        String loc = locationSummary(propertyPlaceholders, k);
                        if (SensitiveUtils.containsSensitive(k)) {
                            LOG.info("    {} {}=xxxxxx", loc, k);
                        } else {
                            LOG.info("    {} {}={}", loc, k, v);
                        }
                    }
                }
            }
        }
    }
}
