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
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.w3c.dom.Document;

import org.apache.camel.CamelContext;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.TypeConverterExists;
import org.apache.camel.component.properties.PropertiesComponent;
import org.apache.camel.dsl.support.SourceLoader;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.engine.DefaultCompileStrategy;
import org.apache.camel.main.download.AutoConfigureDownloadListener;
import org.apache.camel.main.download.BasePackageScanDownloadListener;
import org.apache.camel.main.download.CamelCustomClassLoader;
import org.apache.camel.main.download.CircuitBreakerDownloader;
import org.apache.camel.main.download.CommandLineDependencyDownloader;
import org.apache.camel.main.download.DependencyDownloaderClassLoader;
import org.apache.camel.main.download.DependencyDownloaderClassResolver;
import org.apache.camel.main.download.DependencyDownloaderComponentResolver;
import org.apache.camel.main.download.DependencyDownloaderDataFormatResolver;
import org.apache.camel.main.download.DependencyDownloaderKamelet;
import org.apache.camel.main.download.DependencyDownloaderLanguageResolver;
import org.apache.camel.main.download.DependencyDownloaderPeriodTaskResolver;
import org.apache.camel.main.download.DependencyDownloaderPropertiesComponent;
import org.apache.camel.main.download.DependencyDownloaderPropertiesFunctionResolver;
import org.apache.camel.main.download.DependencyDownloaderPropertyBindingListener;
import org.apache.camel.main.download.DependencyDownloaderResourceLoader;
import org.apache.camel.main.download.DependencyDownloaderRoutesLoader;
import org.apache.camel.main.download.DependencyDownloaderStrategy;
import org.apache.camel.main.download.DependencyDownloaderTransformerResolver;
import org.apache.camel.main.download.DependencyDownloaderUriFactoryResolver;
import org.apache.camel.main.download.DownloadEndpointStrategy;
import org.apache.camel.main.download.DownloadListener;
import org.apache.camel.main.download.ExportPropertiesParser;
import org.apache.camel.main.download.ExportTypeConverter;
import org.apache.camel.main.download.JavaKnownImportsDownloader;
import org.apache.camel.main.download.KameletAutowiredLifecycleStrategy;
import org.apache.camel.main.download.KameletMainInjector;
import org.apache.camel.main.download.KameletOptimisedComponentResolver;
import org.apache.camel.main.download.KnownDependenciesResolver;
import org.apache.camel.main.download.KnownDependenciesVersionResolver;
import org.apache.camel.main.download.KnownReposResolver;
import org.apache.camel.main.download.MavenDependencyDownloader;
import org.apache.camel.main.download.PackageNameSourceLoader;
import org.apache.camel.main.download.PromptPropertyPlaceholderSource;
import org.apache.camel.main.download.SagaDownloader;
import org.apache.camel.main.download.StubBeanRepository;
import org.apache.camel.main.download.StubComponentAutowireStrategy;
import org.apache.camel.main.download.TransactedDownloader;
import org.apache.camel.main.download.TypeConverterLoaderDownloadListener;
import org.apache.camel.main.injection.AnnotationDependencyInjection;
import org.apache.camel.main.reload.OpenApiGeneratorReloadStrategy;
import org.apache.camel.main.util.ClipboardReloadStrategy;
import org.apache.camel.main.util.ExtraClassesClassLoader;
import org.apache.camel.main.util.ExtraFilesClassLoader;
import org.apache.camel.main.xml.blueprint.BlueprintXmlBeansHandler;
import org.apache.camel.main.xml.spring.SpringXmlBeansHandler;
import org.apache.camel.model.OnExceptionDefinition;
import org.apache.camel.reifier.OnExceptionReifier;
import org.apache.camel.reifier.ProcessorReifier;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.spi.CliConnector;
import org.apache.camel.spi.CliConnectorFactory;
import org.apache.camel.spi.CompileStrategy;
import org.apache.camel.spi.ComponentResolver;
import org.apache.camel.spi.ContextServiceLoaderPluginResolver;
import org.apache.camel.spi.DataFormatResolver;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.spi.FactoryFinderResolver;
import org.apache.camel.spi.LanguageResolver;
import org.apache.camel.spi.LifecycleStrategy;
import org.apache.camel.spi.OptimisedComponentResolver;
import org.apache.camel.spi.PeriodTaskResolver;
import org.apache.camel.spi.PeriodTaskScheduler;
import org.apache.camel.spi.Registry;
import org.apache.camel.spi.ResourceLoader;
import org.apache.camel.spi.RoutesLoader;
import org.apache.camel.spi.TransformerResolver;
import org.apache.camel.spi.UriFactoryResolver;
import org.apache.camel.startup.jfr.FlightRecorderStartupStepRecorder;
import org.apache.camel.support.DefaultContextReloadStrategy;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.RouteOnDemandReloadStrategy;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.startup.BacklogStartupStepRecorder;
import org.apache.camel.tooling.maven.MavenGav;

/**
 * A Main class for booting up Camel with Kamelet in standalone mode.
 */
public class KameletMain extends MainCommandLineSupport {

    public static final String DEFAULT_KAMELETS_LOCATION = "classpath:kamelets";

    private final String instanceType;
    protected final MainRegistry registry = new MainRegistry();
    private String profile = "dev";
    private boolean download = true;
    private boolean packageScanJars;
    private String repositories;
    private boolean fresh;
    private boolean verbose;
    private String mavenSettings;
    private String mavenSettingsSecurity;
    boolean mavenCentralEnabled = true;
    boolean mavenApacheSnapshotEnabled = true;
    private String stubPattern;
    private boolean silent;
    private DownloadListener downloadListener;
    private DependencyDownloaderClassLoader classLoader;

    private final SpringXmlBeansHandler springXmlBeansHandler = new SpringXmlBeansHandler();
    private final BlueprintXmlBeansHandler blueprintXmlBeansHandler = new BlueprintXmlBeansHandler();

    /**
     * Deprecated constructor - to tightly bound to Camel JBang. Do not use.
     */
    @Deprecated(since = "4.9.0")
    KameletMain() {
        this("camel.jbang");
    }

    public KameletMain(String instanceType) {
        this.instanceType = instanceType;

        configureInitialProperties(DEFAULT_KAMELETS_LOCATION);
    }

    public KameletMain(String instanceType, String overrides) {
        this.instanceType = instanceType;
        Objects.requireNonNull(overrides);
        String locations = overrides + "," + DEFAULT_KAMELETS_LOCATION;
        configureInitialProperties(locations);
    }

    public static void main(String... args) throws Exception {
        KameletMain main = new KameletMain();
        int code = main.run(args);
        if (code != 0) {
            System.exit(code);
        }
        // normal exit
    }

    /**
     * Binds the given <code>name</code> to the <code>bean</code> object, so that it can be looked up inside the
     * CamelContext this command line tool runs with.
     *
     * @param name the used name through which we do bind
     * @param bean the object to bind
     */
    public void bind(String name, Object bean) {
        registry.bind(name, bean);
    }

    /**
     * Using the given <code>name</code> does lookup for the bean being already bound using the
     * {@link #bind(String, Object)} method.
     *
     * @see Registry#lookupByName(String)
     */
    public Object lookup(String name) {
        return registry.lookupByName(name);
    }

    /**
     * Using the given <code>name</code> and <code>type</code> does lookup for the bean being already bound using the
     * {@link #bind(String, Object)} method.
     *
     * @see Registry#lookupByNameAndType(String, Class)
     */
    public <T> T lookup(String name, Class<T> type) {
        return registry.lookupByNameAndType(name, type);
    }

    /**
     * Using the given <code>type</code> does lookup for the bean being already bound using the
     * {@link #bind(String, Object)} method.
     *
     * @see Registry#findByTypeWithName(Class)
     */
    public <T> Map<String, T> lookupByType(Class<T> type) {
        return registry.findByTypeWithName(type);
    }

    public String getProfile() {
        return profile;
    }

    /**
     * Camel profile to use (dev = development, prod = production). The default is dev.
     */
    public void setProfile(String profile) {
        this.profile = profile;
    }

    public boolean isDownload() {
        return download;
    }

    /**
     * Whether to allow automatic downloaded JAR dependencies, over the internet, that Kamelets requires. This is by
     * default enabled.
     */
    public void setDownload(boolean download) {
        this.download = download;
    }

    public boolean isPackageScanJars() {
        return packageScanJars;
    }

    /**
     * Whether to automatic package scan JARs for custom Spring or Quarkus beans making them available for Camel JBang
     */
    public void setPackageScanJars(boolean packageScanJars) {
        this.packageScanJars = packageScanJars;
    }

    public String getRepositories() {
        return repositories;
    }

    /**
     * Additional maven repositories for download on-demand (Use commas to separate multiple repositories).
     */
    public void setRepositories(String repositories) {
        this.repositories = repositories;
    }

    public boolean isFresh() {
        return fresh;
    }

    /**
     * Make sure we use fresh (i.e. non-cached) resources.
     */
    public void setFresh(boolean fresh) {
        this.fresh = fresh;
    }

    /**
     * Whether to use stub endpoints instead of creating the actual endpoints. This allows to simulate using real
     * components but run without them on the classpath.
     *
     * @param stubPattern endpoint pattern (Use * for all).
     */
    public void setStubPattern(String stubPattern) {
        this.stubPattern = stubPattern;
    }

    public String getStubPattern() {
        return stubPattern;
    }

    public boolean isSilent() {
        return silent;
    }

    /**
     * Whether to run in silent mode (used during export or resolving dependencies)
     */
    public void setSilent(boolean silent) {
        this.silent = silent;
    }

    /**
     * Optionally set the location of Maven settings.xml if it's different than {@code ~/.m2/settings.xml}. If set to
     * {@code false}, no default settings file will be used at all.
     */
    public void setMavenSettings(String mavenSettings) {
        this.mavenSettings = mavenSettings;
    }

    public String getMavenSettings() {
        return mavenSettings;
    }

    /**
     * Optionally set the location of Maven settings-security.xml if it's different than
     * {@code ~/.m2/settings-security.xml}.
     */
    public void setMavenSettingsSecurity(String mavenSettingsSecurity) {
        this.mavenSettingsSecurity = mavenSettingsSecurity;
    }

    public String getMavenSettingsSecurity() {
        return mavenSettingsSecurity;
    }

    public DownloadListener getDownloadListener() {
        return downloadListener;
    }

    public boolean isMavenCentralEnabled() {
        return mavenCentralEnabled;
    }

    /**
     * Whether downloading JARs from Maven Central repository is enabled
     */
    public void setMavenCentralEnabled(boolean mavenCentralEnabled) {
        this.mavenCentralEnabled = mavenCentralEnabled;
    }

    public boolean isMavenApacheSnapshotEnabled() {
        return mavenApacheSnapshotEnabled;
    }

    /**
     * Whether downloading JARs from ASF Maven Snapshot repository is enabled
     */
    public void setMavenApacheSnapshotEnabled(boolean mavenApacheSnapshotEnabled) {
        this.mavenApacheSnapshotEnabled = mavenApacheSnapshotEnabled;
    }

    /**
     * Sets a custom download listener
     */
    public void setDownloadListener(DownloadListener downloadListener) {
        this.downloadListener = downloadListener;
    }

    // Implementation methods
    // -------------------------------------------------------------------------

    @Override
    public void showOptionsHeader() {
        System.out.println("Apache Camel (KameletMain) takes the following options");
        System.out.println();
    }

    @Override
    protected void addInitialOptions() {
        addOption(new Option("h", "help", "Displays the help screen") {
            protected void doProcess(String arg, LinkedList<String> remainingArgs) {
                showOptions();
                completed();
            }
        });
        addOption(new ParameterOption(
                "download", "download", "Whether to allow automatic downloaded JAR dependencies, over the internet.",
                "download") {
            @Override
            protected void doProcess(String arg, String parameter, LinkedList<String> remainingArgs) {
                if (arg.equals("-download")) {
                    setDownload("true".equalsIgnoreCase(parameter));
                }
            }
        });
        addOption(new ParameterOption(
                "repos", "repositories", "Additional maven repositories for download on-demand.",
                "repos") {
            @Override
            protected void doProcess(String arg, String parameter, LinkedList<String> remainingArgs) {
                if (arg.equals("-repos")) {
                    setRepositories(parameter);
                }
            }
        });
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();
        initCamelContext();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (getCamelContext() != null) {
            try {
                // if we were vetoed started then mark as completed
                getCamelContext().start();
            } finally {
                if (getCamelContext().isVetoStarted()) {
                    completed();
                }
            }
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        if (getCamelContext() != null) {
            getCamelContext().stop();
        }
        springXmlBeansHandler.stop();
        blueprintXmlBeansHandler.stop();
    }

    @Override
    protected ProducerTemplate findOrCreateCamelTemplate() {
        if (getCamelContext() != null) {
            return getCamelContext().createProducerTemplate();
        } else {
            return null;
        }
    }

    @Override
    protected CamelContext createCamelContext() {
        this.verbose = "true".equals(getInitialProperties().get(getInstanceType() + ".verbose"));

        // do not build/init camel context yet
        DefaultCamelContext answer = new DefaultCamelContext(false);
        // setup backlog recorder from very start
        answer.getCamelContextExtension().setStartupStepRecorder(new BacklogStartupStepRecorder());

        boolean export = "true".equals(getInitialProperties().get(getInstanceType() + ".export"));
        if (export) {
            setupExport(answer, export);
        } else {
            PropertiesComponent pc = (PropertiesComponent) answer.getPropertiesComponent();
            pc.setPropertiesFunctionResolver(new DependencyDownloaderPropertiesFunctionResolver(answer, false));
        }

        // groovy scripts
        String groovyFiles = getInitialProperties().getProperty(getInstanceType() + ".groovyFiles");
        if (groovyFiles != null) {
            configure().withGroovyScriptPattern(groovyFiles);
        }

        boolean prompt = "true".equals(getInitialProperties().get(getInstanceType() + ".prompt"));
        if (prompt) {
            answer.getPropertiesComponent().addPropertiesSource(new PromptPropertyPlaceholderSource());
        }

        ClassLoader dynamicCL = createApplicationContextClassLoader(answer);
        answer.setApplicationContextClassLoader(dynamicCL);
        PluginHelper.getPackageScanClassResolver(answer).addClassLoader(dynamicCL);
        PluginHelper.getPackageScanResourceResolver(answer).addClassLoader(dynamicCL);

        final MavenDependencyDownloader downloader = createMavenDependencyDownloader(dynamicCL, answer);

        // register as extension
        try {
            answer.addService(downloader);
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeException(e);
        }

        // in case we use circuit breakers
        CircuitBreakerDownloader.registerDownloadReifiers();

        // in case we use transacted
        TransactedDownloader.registerDownloadReifiers(this);

        // in case we use saga
        SagaDownloader.registerDownloadReifiers(this);

        // if transforming DSL then disable processors as we just want to work on the model (not runtime processors)
        boolean transform = "true".equals(getInitialProperties().get(getInstanceType() + ".transform"));
        if (transform) {
            // we just want to transform, so disable custom bean or processors as they may use code that does not work
            answer.getGlobalOptions().put(ProcessorReifier.DISABLE_BEAN_OR_PROCESS_PROCESSORS, "true");
            // stub everything
            this.stubPattern = "*";
            // turn off inlining routes
            configure().rest().withInlineRoutes(false);
            blueprintXmlBeansHandler.setTransform(true);
        }
        if (silent) {
            // silent should not include http server
            configure().httpServer().withEnabled(false);
            configure().httpManagementServer().withEnabled(false);
        }

        if (silent || "*".equals(stubPattern) || "component:*".equals(stubPattern)) {
            // turn off auto-wiring when running in silent mode (or stub = *)
            mainConfigurationProperties.setAutowiredEnabled(false);
            // and turn off fail fast as we stub components
            mainConfigurationProperties.setAutoConfigurationFailFast(false);
        }

        var infos = startupInfo();
        infos.forEach(LOG::info);

        answer.getCamelContextExtension().setRegistry(registry);
        if (silent || "*".equals(stubPattern)) {
            registry.addBeanRepository(new StubBeanRepository(stubPattern));
        }

        // load camel component and custom health-checks
        answer.setLoadHealthChecks(true);

        // annotation based dependency injection for camel/spring/quarkus annotations in DSLs and Java beans
        boolean lazyBean = "true".equals(getInitialProperties().get(getInstanceType() + ".lazyBean"));
        new AnnotationDependencyInjection(answer, lazyBean);

        if (!silent) {
            // silent should not include cli-connector
            // setup cli-connector if not already done
            if (answer.hasService(CliConnector.class) == null) {
                CliConnectorFactory ccf = answer.getCamelContextExtension().getContextPlugin(CliConnectorFactory.class);
                if (ccf != null && ccf.isEnabled()) {
                    CliConnector connector = ccf.createConnector();
                    try {
                        answer.addService(connector, true);
                        // force start cli connector early as otherwise it will be deferred until context is started
                        // but, we want status available during startup phase
                        ServiceHelper.startService(connector);
                    } catch (Exception e) {
                        LOG.warn("Cannot start camel-cli-connector due: {}. This integration cannot be managed by Camel CLI.",
                                e.getMessage());
                    }
                }
            }
        }

        configure().withProfile(profile);

        // embed HTTP server if port is specified
        Object port = getInitialProperties().get(getInstanceType() + ".platform-http.port");
        if (port != null) {
            configure().httpServer().withEnabled(true);
            configure().httpServer().withPort(Integer.parseInt(port.toString()));
        }
        boolean console = "true".equals(getInitialProperties().get(getInstanceType() + ".console"));
        if (console) {
            configure().setDevConsoleEnabled(true);
            configure().httpManagementServer().withEnabled(true);
            configure().httpManagementServer().withDevConsoleEnabled(true);
            // also include health,info and jolokia
            configure().httpManagementServer().withHealthCheckEnabled(true);
            configure().httpManagementServer().withInfoEnabled(true);
            configure().httpManagementServer().withJolokiaEnabled(true);
        }
        boolean tracing = "true".equals(getInitialProperties().get(getInstanceType() + ".backlogTracing"));
        if (tracing) {
            configure().tracerConfig().withEnabled(true);
        }
        boolean infoConsole = "true".equals(getInitialProperties().get(getInstanceType() + ".info"));
        if (infoConsole) {
            configure().httpManagementServer().withEnabled(true);
            configure().httpManagementServer().withInfoEnabled(true);
        }
        // Deprecated: to be replaced by observe flag
        boolean health = "true".equals(getInitialProperties().get(getInstanceType() + ".health"));
        if (health) {
            configure().health().withEnabled(true);
            configure().httpManagementServer().withEnabled(true);
            configure().httpManagementServer().withHealthCheckEnabled(true);
        }
        // Deprecated: to be replaced by observe flag
        boolean metrics = "true".equals(getInitialProperties().get(getInstanceType() + ".metrics"));
        if (metrics) {
            configure().metrics()
                    .witheEnableRouteEventNotifier(true)
                    .withEnableMessageHistory(true)
                    .withEnableExchangeEventNotifier(true)
                    .withEnableRoutePolicy(true).withEnabled(true);
            configure().httpManagementServer().withEnabled(true);
            configure().httpManagementServer().withMetricsEnabled(true);
        }
        boolean ignoreLoading = "true".equals(getInitialProperties().get(getInstanceType() + ".ignoreLoadingError"));
        if (ignoreLoading) {
            configure().withRoutesCollectorIgnoreLoadingError(true);
            answer.getPropertiesComponent().setIgnoreMissingProperty(true);
            answer.getPropertiesComponent().setIgnoreMissingLocation(true);
        }

        // need to setup jfr early
        Object jfr = getInitialProperties().get(getInstanceType() + ".jfr");
        Object jfrProfile = getInitialProperties().get(getInstanceType() + ".jfr-profile");
        if ("jfr".equals(jfr) || jfrProfile != null) {
            FlightRecorderStartupStepRecorder recorder = new FlightRecorderStartupStepRecorder();
            recorder.setRecording(true);
            if (jfrProfile != null) {
                recorder.setRecordingProfile(jfrProfile.toString());
            }
            answer.getCamelContextExtension().setStartupStepRecorder(recorder);
        }

        // special for source compilation to a specific package based on Maven GAV
        String gav = getInitialProperties().getProperty(getInstanceType() + ".gav");
        if (gav != null) {
            MavenGav g = MavenGav.parseGav(gav);
            if (g.getGroupId() != null && g.getArtifactId() != null) {
                // plugin a custom source loader with package name based on GAV
                SourceLoader sl = new PackageNameSourceLoader(g.getGroupId(), g.getArtifactId());
                answer.getRegistry().bind("PackageNameSourceLoader", sl);
            }
        }

        // source-dir
        String sourceDir = getInitialProperties().getProperty(getInstanceType() + ".sourceDir");

        try {
            // dependencies from CLI
            Object dependencies = getInitialProperties().get(getInstanceType() + ".dependencies");
            if (dependencies != null) {
                answer.addService(new CommandLineDependencyDownloader(answer, dependencies.toString()));
            }

            String springBootVersion = (String) getInitialProperties().get(getInstanceType() + ".springBootVersion");
            String quarkusVersion = (String) getInitialProperties().get(getInstanceType() + ".quarkusVersion");

            KnownDependenciesResolver knownDeps = new KnownDependenciesResolver(answer, springBootVersion, quarkusVersion);
            knownDeps.loadKnownDependencies();
            DependencyDownloaderPropertyBindingListener listener
                    = new DependencyDownloaderPropertyBindingListener(answer, knownDeps);
            answer.getCamelContextExtension().getRegistry()
                    .bind(DependencyDownloaderPropertyBindingListener.class.getSimpleName(), listener);
            answer.getCamelContextExtension().getRegistry().bind(DependencyDownloaderStrategy.class.getSimpleName(),
                    new DependencyDownloaderStrategy(answer));
            // add support for automatic downloaded needed JARs from java imports
            new JavaKnownImportsDownloader(answer, knownDeps);

            // download class-resolver
            ClassResolver classResolver = new DependencyDownloaderClassResolver(answer, knownDeps, silent);
            answer.setClassResolver(classResolver);
            // re-create factory finder with download class-resolver
            FactoryFinderResolver ffr = PluginHelper.getFactoryFinderResolver(answer);
            FactoryFinder ff = ffr.resolveBootstrapFactoryFinder(classResolver);
            answer.getCamelContextExtension().setBootstrapFactoryFinder(ff);
            ff = ffr.resolveDefaultFactoryFinder(classResolver);
            answer.getCamelContextExtension().setDefaultFactoryFinder(ff);

            // period task resolver that can download needed dependencies
            Object camelVersion = getInitialProperties().get(getInstanceType() + ".camelVersion");
            PeriodTaskResolver ptr = new DependencyDownloaderPeriodTaskResolver(
                    ff, answer, Optional.ofNullable(camelVersion).map(Object::toString).orElse(null), export);
            answer.getCamelContextExtension().addContextPlugin(PeriodTaskResolver.class, ptr);

            answer.getCamelContextExtension().registerEndpointCallback(new DownloadEndpointStrategy(answer, silent));
            answer.getCamelContextExtension().addContextPlugin(ComponentResolver.class,
                    new DependencyDownloaderComponentResolver(answer, stubPattern, silent, transform));
            answer.getCamelContextExtension().addContextPlugin(DataFormatResolver.class,
                    new DependencyDownloaderDataFormatResolver(answer, stubPattern, silent));
            answer.getCamelContextExtension().addContextPlugin(LanguageResolver.class,
                    new DependencyDownloaderLanguageResolver(answer, stubPattern, silent));
            answer.getCamelContextExtension().addContextPlugin(TransformerResolver.class,
                    new DependencyDownloaderTransformerResolver(answer, stubPattern, silent));
            answer.getCamelContextExtension().addContextPlugin(UriFactoryResolver.class,
                    new DependencyDownloaderUriFactoryResolver(answer));
            answer.getCamelContextExtension().addContextPlugin(ResourceLoader.class,
                    new DependencyDownloaderResourceLoader(answer, sourceDir));
            answer.getCamelContextExtension().addContextPlugin(OptimisedComponentResolver.class,
                    new KameletOptimisedComponentResolver(answer));

            if (stubPattern != null) {
                // need to replace autowire strategy with stub capable
                answer.getLifecycleStrategies()
                        // NOTE: class not available at compilation time.
                        .removeIf(s -> s.getClass().getSimpleName().equals("DefaultAutowiredLifecycleStrategy")); // NOSONAR
                answer.getLifecycleStrategies().add(new StubComponentAutowireStrategy(answer, stubPattern));
            }
            answer.setInjector(new KameletMainInjector(answer.getInjector(), stubPattern, silent));
            Object kameletsVersion = getInitialProperties().get(getInstanceType() + ".kameletsVersion");
            if (kameletsVersion != null) {
                answer.addService(new DependencyDownloaderKamelet(answer, kameletsVersion.toString()));
            } else {
                answer.addService(new DependencyDownloaderKamelet(answer));
            }
            answer.addService(new DependencyDownloaderPropertiesComponent(answer, knownDeps, silent));

            // reloader
            if (sourceDir != null) {
                if (console || health) {
                    // allow to load static web content from source-dir
                    configure().httpServer().withStaticSourceDir(sourceDir);
                    configure().httpManagementServer().withEnabled(true);
                    // allow to upload/download source (source-dir is intended to be dynamic) via http when HTTP console enabled
                    configure().httpManagementServer().withUploadEnabled(true);
                    configure().httpManagementServer().withDownloadEnabled(true);
                    configure().httpManagementServer().withUploadSourceDir(sourceDir);
                }
                RouteOnDemandReloadStrategy reloader = new RouteOnDemandReloadStrategy(sourceDir, true);
                reloader.setPattern("*");
                answer.addService(reloader);

                // add source-dir as location for loading kamelets (if not already included)
                String loc = this.initialProperties.getProperty("camel.component.kamelet.location");
                String target = "file:" + sourceDir + ",";
                if (!loc.contains(target)) {
                    loc = target + loc;
                    addInitialProperty("camel.component.kamelet.location", loc);
                }
            } else {
                answer.addService(new DefaultContextReloadStrategy());
            }

            // special for reloading enabled on clipboard
            String reloadDir = getInitialProperties().getProperty("camel.main.routesIncludePattern");
            if (reloadDir != null && reloadDir.startsWith("file:.camel-jbang/generated-clipboard")) {
                String name = reloadDir.substring(5);
                File file = new File(name);
                ClipboardReloadStrategy reloader = new ClipboardReloadStrategy(file);
                answer.addService(reloader);
                PeriodTaskScheduler scheduler = PluginHelper.getPeriodTaskScheduler(answer);
                scheduler.schedulePeriodTask(reloader, 2000);
            }

            // reload with openapi
            String openapi = getInitialProperties().getProperty(getInstanceType() + ".open-api");
            String reload = getInitialProperties().getProperty("camel.main.routesReloadDirectory");
            if (openapi != null && (reload != null || sourceDir != null)) {
                // add open-api reloader that generate output to .camel-jbang/generated-openapi.yaml
                File file = Paths.get(openapi).toFile();
                OpenApiGeneratorReloadStrategy rs = new OpenApiGeneratorReloadStrategy(file);
                answer.addService(rs);
            }
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeException(e);
        }

        // Start the context service loader plugin after all dependencies and downloaders are set up
        // This ensures the classpath is enhanced and ServiceLoader can discover third-party plugins
        ContextServiceLoaderPluginResolver contextServicePlugin = answer
                .getCamelContextExtension().getContextPlugin(ContextServiceLoaderPluginResolver.class);
        if (contextServicePlugin != null) {
            // force start context service loader plugin to discover and load third-party plugins
            ServiceHelper.startService(contextServicePlugin);
        }

        return answer;
    }

    private MavenDependencyDownloader createMavenDependencyDownloader(ClassLoader dynamicCL, DefaultCamelContext answer) {
        KnownReposResolver knownRepos = new KnownReposResolver();
        knownRepos.loadKnownDependencies();

        MavenDependencyDownloader downloader = new MavenDependencyDownloader();
        downloader.setDownload(download);
        downloader.setKnownReposResolver(knownRepos);
        downloader.setClassLoader(dynamicCL);
        downloader.setCamelContext(answer);
        downloader.setVerbose(verbose);
        downloader.setRepositories(repositories);
        downloader.setFresh(fresh);
        downloader.setMavenSettings(mavenSettings);
        downloader.setMavenSettingsSecurity(mavenSettingsSecurity);
        downloader.setMavenCentralEnabled(mavenCentralEnabled);
        downloader.setMavenApacheSnapshotEnabled(mavenApacheSnapshotEnabled);
        if (downloadListener != null) {
            downloader.addDownloadListener(downloadListener);
        }
        downloader.addDownloadListener(new AutoConfigureDownloadListener());
        downloader.addArtifactDownloadListener(new TypeConverterLoaderDownloadListener());
        downloader.addArtifactDownloadListener(new BasePackageScanDownloadListener(packageScanJars));
        downloader.setVersionResolver(new KnownDependenciesVersionResolver(downloader));

        return downloader;
    }

    private void setupExport(DefaultCamelContext answer, boolean export) {
        // when exporting we should ignore some errors and keep attempting to export as far as we can
        addInitialProperty("camel.component.properties.ignore-missing-property", "true");
        addInitialProperty("camel.component.properties.ignore-missing-location", "true");
        PropertiesComponent pc = (PropertiesComponent) answer.getPropertiesComponent();
        pc.setPropertiesParser(new ExportPropertiesParser(answer));
        pc.setPropertiesFunctionResolver(new DependencyDownloaderPropertiesFunctionResolver(answer, export));

        // override default type converters with our export converter that is more flexible during exporting
        ExportTypeConverter ec = new ExportTypeConverter();
        answer.getTypeConverterRegistry().setTypeConverterExists(TypeConverterExists.Override);
        answer.getTypeConverterRegistry().addTypeConverter(Integer.class, String.class, ec);
        answer.getTypeConverterRegistry().addTypeConverter(Long.class, String.class, ec);
        answer.getTypeConverterRegistry().addTypeConverter(Double.class, String.class, ec);
        answer.getTypeConverterRegistry().addTypeConverter(Float.class, String.class, ec);
        answer.getTypeConverterRegistry().addTypeConverter(Byte.class, String.class, ec);
        answer.getTypeConverterRegistry().addTypeConverter(Boolean.class, String.class, ec);
        answer.getTypeConverterRegistry().addFallbackTypeConverter(ec, false);

        // turn of validator in onException during export
        ProcessorReifier.registerReifier(OnExceptionDefinition.class,
                (route, def) -> new OnExceptionReifier(route, (OnExceptionDefinition) def, false));
    }

    private String getInstanceType() {
        return instanceType;
    }

    @Override
    protected void autoconfigure(CamelContext camelContext) throws Exception {
        ClassLoader cl = createApplicationContextClassLoader(camelContext);
        // create classloader that may include additional JARs
        camelContext.setApplicationContextClassLoader(cl);
        // auto configure camel afterwards
        super.autoconfigure(camelContext);
    }

    @Override
    protected LifecycleStrategy createLifecycleStrategy(CamelContext camelContext) {
        return new KameletAutowiredLifecycleStrategy(camelContext, stubPattern, silent);
    }

    protected ClassLoader createApplicationContextClassLoader(CamelContext camelContext) {
        if (classLoader == null) {
            // jars need to be added to dependency downloader classloader
            List<String> jars = new ArrayList<>();
            List<String> classes = new ArrayList<>();
            // create class loader (that are download capable) only once
            // any additional files to add to classpath
            ClassLoader parentCL = KameletMain.class.getClassLoader();
            String cpFiles = getInitialProperties().getProperty(getInstanceType() + ".classpathFiles");
            if (cpFiles != null) {
                String[] arr = cpFiles.split(",");
                List<String> files = new ArrayList<>();
                for (String s : arr) {
                    if (s.endsWith(".jar")) {
                        jars.add(s);
                    } else if (s.endsWith(".class")) {
                        classes.add(s);
                    } else {
                        files.add(s);
                    }
                }
                if (!classes.isEmpty()) {
                    parentCL = new ExtraClassesClassLoader(parentCL, classes);
                    LOG.info("Additional classes added to classpath: {}", String.join(", ", classes));
                }
                if (!files.isEmpty()) {
                    parentCL = new ExtraFilesClassLoader(parentCL, files);
                    LOG.info("Additional files added to classpath: {}", String.join(", ", files));
                }
            }
            parentCL = new CamelCustomClassLoader(parentCL, camelContext);
            DependencyDownloaderClassLoader cl = new DependencyDownloaderClassLoader(parentCL);
            if (!jars.isEmpty()) {
                for (String jar : jars) {
                    File f = new File(jar).getAbsoluteFile();
                    if (f.isFile() && f.exists()) {
                        cl.addFile(f);
                    }
                }
                LOG.info("Additional jars added to classpath: {}", String.join(", ", jars));
            }
            classLoader = cl;
        }
        return classLoader;
    }

    @Override
    protected void configureRoutesLoader(CamelContext camelContext) {
        ExtendedCamelContext ecc = camelContext.getCamelContextExtension();

        // need to configure compile work dir as its used from routes loader when it discovered code to dynamic compile
        String dir = getInitialProperties().getProperty(getInstanceType() + ".compileWorkDir");
        if (dir != null) {
            CompileStrategy cs = camelContext.getCamelContextExtension().getContextPlugin(CompileStrategy.class);
            if (cs == null) {
                cs = new DefaultCompileStrategy();
                ecc.addContextPlugin(CompileStrategy.class, cs);
            }
            cs.setWorkDir(dir);
        }

        DependencyDownloaderRoutesLoader routesLoader;
        Object camelVersion = getInitialProperties().get(getInstanceType() + ".camelVersion");
        Object kameletsVersion = getInitialProperties().get(getInstanceType() + ".kameletsVersion");
        if (camelVersion != null || kameletsVersion != null) {
            routesLoader = new DependencyDownloaderRoutesLoader(
                    camelContext,
                    Optional.ofNullable(camelVersion).map(Object::toString).orElse(""),
                    Optional.ofNullable(kameletsVersion).map(Object::toString).orElse(""));
        } else {
            routesLoader = new DependencyDownloaderRoutesLoader(camelContext);
        }
        routesLoader.setIgnoreLoadingError(this.mainConfigurationProperties.isRoutesCollectorIgnoreLoadingError());

        // routes loader should ignore unknown extensions when using --source-dir as users may drop files
        // in this folder that are not Camel routes but resource files.
        String sourceDir = getInitialProperties().getProperty(getInstanceType() + ".sourceDir");
        if (sourceDir != null) {
            routesLoader.setIgnoreUnknownExtensions(true);
        }

        // use resolvers that can auto downloaded
        ecc.addContextPlugin(RoutesLoader.class, routesLoader);
    }

    /**
     * Sets initial properties that are specific to camel-kamelet-main
     */
    protected void configureInitialProperties(String location) {
        // optional configuration if these components are in-use
        addInitialProperty("camel.component.kamelet.location", location);
    }

    protected Stream<String> startupInfo() {
        List<String> infos = new ArrayList<>();

        StringBuilder sb = new StringBuilder();
        sb.append("Running ").append(System.getProperty("os.name")).append(" ").append(System.getProperty("os.version"));
        sb.append(" (").append(System.getProperty("os.arch")).append(")");
        infos.add(sb.toString());

        sb = new StringBuilder();
        sb.append("Using Java ").append(System.getProperty("java.version")).append(" (")
                .append(System.getProperty("java.vm.name")).append(")");
        sb.append(" with PID ").append(getPid());
        infos.add(sb.toString());

        sb = new StringBuilder();
        sb.append("Started by ").append(System.getProperty("user.name"));
        sb.append(" in ").append(System.getProperty("user.dir"));
        infos.add(sb.toString());

        return infos.stream();
    }

    @Override
    protected void preProcessCamelRegistry(CamelContext camelContext, MainConfigurationProperties config) {
        final Map<String, Document> springXmls = new TreeMap<>();
        final Map<String, Document> blueprintXmls = new TreeMap<>();

        Map<String, Document> xmlDocs = registry.findByTypeWithName(Document.class);
        if (xmlDocs != null) {
            xmlDocs.forEach((id, doc) -> {
                if (id.startsWith("camel-xml-io-dsl-spring-xml:")) {
                    springXmls.put(id, doc);
                } else if (id.startsWith("camel-xml-io-dsl-blueprint-xml:")) {
                    blueprintXmls.put(id, doc);
                }
            });
        }
        if (!springXmls.isEmpty()) {
            // camel-kamelet-main has access to Spring libraries, so we can grab XML documents representing
            // actual Spring Beans and read them using Spring's BeanFactory to populate Camel registry
            springXmlBeansHandler.processSpringBeans(camelContext, config, springXmls);
        }
        if (!blueprintXmls.isEmpty()) {
            blueprintXmlBeansHandler.processBlueprintBeans(camelContext, config, blueprintXmls);
        }
    }

    @Override
    protected void postProcessCamelRegistry(CamelContext camelContext, MainConfigurationProperties config) {
        springXmlBeansHandler.createAndRegisterBeans(camelContext);
        blueprintXmlBeansHandler.createAndRegisterBeans(camelContext);
    }

    private static String getPid() {
        return String.valueOf(ProcessHandle.current().pid());
    }

}
