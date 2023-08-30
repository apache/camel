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
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import org.w3c.dom.Document;

import org.apache.camel.CamelContext;
import org.apache.camel.ManagementStatisticsLevel;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.dsl.support.SourceLoader;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.main.download.AutoConfigureDownloadListener;
import org.apache.camel.main.download.BasePackageScanDownloadListener;
import org.apache.camel.main.download.CircuitBreakerDownloader;
import org.apache.camel.main.download.CommandLineDependencyDownloader;
import org.apache.camel.main.download.DependencyDownloaderClassLoader;
import org.apache.camel.main.download.DependencyDownloaderClassResolver;
import org.apache.camel.main.download.DependencyDownloaderComponentResolver;
import org.apache.camel.main.download.DependencyDownloaderDataFormatResolver;
import org.apache.camel.main.download.DependencyDownloaderKamelet;
import org.apache.camel.main.download.DependencyDownloaderLanguageResolver;
import org.apache.camel.main.download.DependencyDownloaderPropertiesFunctionResolver;
import org.apache.camel.main.download.DependencyDownloaderPropertyBindingListener;
import org.apache.camel.main.download.DependencyDownloaderResourceLoader;
import org.apache.camel.main.download.DependencyDownloaderRoutesLoader;
import org.apache.camel.main.download.DependencyDownloaderStrategy;
import org.apache.camel.main.download.DependencyDownloaderUriFactoryResolver;
import org.apache.camel.main.download.DownloadListener;
import org.apache.camel.main.download.DownloadModelineParser;
import org.apache.camel.main.download.KameletAutowiredLifecycleStrategy;
import org.apache.camel.main.download.KameletMainInjector;
import org.apache.camel.main.download.KnownDependenciesResolver;
import org.apache.camel.main.download.KnownReposResolver;
import org.apache.camel.main.download.MavenDependencyDownloader;
import org.apache.camel.main.download.PackageNameSourceLoader;
import org.apache.camel.main.download.TypeConverterLoaderDownloadListener;
import org.apache.camel.main.injection.AnnotationDependencyInjection;
import org.apache.camel.main.util.ExtraFilesClassLoader;
import org.apache.camel.main.xml.blueprint.BlueprintXmlBeansHandler;
import org.apache.camel.main.xml.spring.SpringXmlBeansHandler;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.spi.CliConnector;
import org.apache.camel.spi.CliConnectorFactory;
import org.apache.camel.spi.ComponentResolver;
import org.apache.camel.spi.DataFormatResolver;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.spi.FactoryFinderResolver;
import org.apache.camel.spi.LanguageResolver;
import org.apache.camel.spi.LifecycleStrategy;
import org.apache.camel.spi.Registry;
import org.apache.camel.spi.ResourceLoader;
import org.apache.camel.spi.RoutesLoader;
import org.apache.camel.spi.UriFactoryResolver;
import org.apache.camel.startup.jfr.FlightRecorderStartupStepRecorder;
import org.apache.camel.support.DefaultContextReloadStrategy;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.RouteOnDemandReloadStrategy;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.tooling.maven.MavenGav;

/**
 * A Main class for booting up Camel with Kamelet in standalone mode.
 */
public class KameletMain extends MainCommandLineSupport {

    public static final String DEFAULT_KAMELETS_LOCATION = "classpath:/kamelets,github:apache:camel-kamelets/kamelets";

    protected final MainRegistry registry = new MainRegistry();
    private boolean download = true;
    private String repos;
    private boolean fresh;
    private boolean verbose;
    private String mavenSettings;
    private String mavenSettingsSecurity;
    private String stubPattern;
    private boolean silent;
    private DownloadListener downloadListener;
    private DependencyDownloaderClassLoader classLoader;

    private final SpringXmlBeansHandler springXmlBeansHandler = new SpringXmlBeansHandler();
    private final BlueprintXmlBeansHandler blueprintXmlBeansHandler = new BlueprintXmlBeansHandler();

    public KameletMain() {
        configureInitialProperties(DEFAULT_KAMELETS_LOCATION);
    }

    public KameletMain(String overrides) {
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

    public String getRepos() {
        return repos;
    }

    /**
     * Additional maven repositories for download on-demand (Use commas to separate multiple repositories).
     */
    public void setRepos(String repos) {
        this.repos = repos;
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
                    setRepos(parameter);
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
        this.verbose = "true".equals(getInitialProperties().get("camel.jbang.verbose"));

        // do not build/init camel context yet
        DefaultCamelContext answer = new DefaultCamelContext(false);
        if (download) {
            ClassLoader dynamicCL = createApplicationContextClassLoader();
            answer.setApplicationContextClassLoader(dynamicCL);
            PluginHelper.getPackageScanClassResolver(answer).addClassLoader(dynamicCL);
            PluginHelper.getPackageScanResourceResolver(answer).addClassLoader(dynamicCL);

            KnownReposResolver known = new KnownReposResolver(camelContext);
            known.loadKnownDependencies();
            MavenDependencyDownloader downloader = new MavenDependencyDownloader();
            downloader.setKnownReposResolver(known);
            downloader.setClassLoader(dynamicCL);
            downloader.setCamelContext(answer);
            downloader.setVerbose(verbose);
            downloader.setRepos(repos);
            downloader.setFresh(fresh);
            downloader.setMavenSettings(mavenSettings);
            downloader.setMavenSettingsSecurity(mavenSettingsSecurity);
            if (downloadListener != null) {
                downloader.addDownloadListener(downloadListener);
            }
            downloader.addDownloadListener(new AutoConfigureDownloadListener());
            downloader.addArtifactDownloadListener(new TypeConverterLoaderDownloadListener());
            downloader.addArtifactDownloadListener(new BasePackageScanDownloadListener());

            // register as extension
            try {
                answer.addService(downloader);
            } catch (Exception e) {
                throw RuntimeCamelException.wrapRuntimeException(e);
            }

            // in case we use circuit breakers
            CircuitBreakerDownloader.registerDownloadReifiers();
        }
        if (silent || "*".equals(stubPattern)) {
            // turn off auto-wiring when running in silent mode (or stub = *)
            mainConfigurationProperties.setAutowiredEnabled(false);
            // and turn off fail fast as we stub components
            mainConfigurationProperties.setAutoConfigurationFailFast(false);
        }

        String info = startupInfo();
        if (info != null) {
            LOG.info(info);
        }

        answer.getCamelContextExtension().setRegistry(registry);
        // load camel component and custom health-checks
        answer.setLoadHealthChecks(true);
        // annotation based dependency injection for camel/spring/quarkus annotations in DSLs and Java beans
        AnnotationDependencyInjection.initAnnotationBasedDependencyInjection(answer);

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
        // embed HTTP server if port is specified
        Object port = getInitialProperties().get("camel.jbang.platform-http.port");
        if (port != null) {
            configure().httpServer().withEnabled(true);
            configure().httpServer().withPort(Integer.parseInt(port.toString()));
        }
        boolean console = "true".equals(getInitialProperties().get("camel.jbang.console"));
        if (console) {
            configure().httpServer().withEnabled(true);
            configure().httpServer().withDevConsoleEnabled(true);
        }

        // always enable developer console as it is needed by camel-cli-connector
        configure().withDevConsoleEnabled(true);
        // and enable a bunch of other stuff that gives more details for developers
        configure().withCamelEventsTimestampEnabled(true);
        configure().withLoadHealthChecks(true);
        configure().withModeline(true);
        configure().withLoadStatisticsEnabled(true);
        configure().withMessageHistory(true);
        configure().withInflightRepositoryBrowseEnabled(true);
        configure().withEndpointRuntimeStatisticsEnabled(true);
        configure().withJmxManagementStatisticsLevel(ManagementStatisticsLevel.Extended);
        configure().withShutdownLogInflightExchangesOnTimeout(false);
        configure().withShutdownTimeout(10);

        boolean tracing = "true".equals(getInitialProperties().get("camel.jbang.backlogTracing"));
        if (tracing) {
            configure().withBacklogTracing(true);
        }

        boolean health = "true".equals(getInitialProperties().get("camel.jbang.health"));
        if (health) {
            configure().httpServer().withEnabled(true);
            configure().httpServer().withHealthCheckEnabled(true);
        }
        if (silent) {
            // silent should not include http server
            configure().httpServer().withEnabled(false);
        }

        // need to setup jfr early
        Object jfr = getInitialProperties().get("camel.jbang.jfr");
        Object jfrProfile = getInitialProperties().get("camel.jbang.jfr-profile");
        if ("jfr".equals(jfr) || jfrProfile != null) {
            FlightRecorderStartupStepRecorder recorder = new FlightRecorderStartupStepRecorder();
            recorder.setRecording(true);
            if (jfrProfile != null) {
                recorder.setRecordingProfile(jfrProfile.toString());
            }
            answer.setStartupStepRecorder(recorder);
        }

        // special for source compilation to a specific package based on Maven GAV
        String gav = getInitialProperties().getProperty("camel.jbang.gav");
        if (gav != null) {
            MavenGav g = MavenGav.parseGav(gav);
            if (g.getGroupId() != null && g.getArtifactId() != null) {
                // plugin a custom source loader with package name based on GAV
                String defaultPackageName = g.getGroupId().replace('-', '.') + "." + g.getArtifactId().replace('-', '.');
                SourceLoader sl = new PackageNameSourceLoader(defaultPackageName);
                answer.getRegistry().bind("PackageNameSourceLoader", sl);
            }
        }

        try {
            // dependencies from CLI
            Object dependencies = getInitialProperties().get("camel.jbang.dependencies");
            if (dependencies != null) {
                answer.addService(new CommandLineDependencyDownloader(answer, dependencies.toString()));
            }

            KnownDependenciesResolver known = new KnownDependenciesResolver(answer);
            known.loadKnownDependencies();
            if (download) {
                DependencyDownloaderPropertyBindingListener listener
                        = new DependencyDownloaderPropertyBindingListener(answer, known);
                answer.getCamelContextExtension().getRegistry()
                        .bind(DependencyDownloaderPropertyBindingListener.class.getSimpleName(), listener);
                answer.getCamelContextExtension().getRegistry().bind(DependencyDownloaderStrategy.class.getSimpleName(),
                        new DependencyDownloaderStrategy(answer));

                // download class-resolver
                ClassResolver classResolver = new DependencyDownloaderClassResolver(answer, known);
                answer.setClassResolver(classResolver);
                // re-create factory finder with download class-resolver
                FactoryFinderResolver ffr = PluginHelper.getFactoryFinderResolver(answer);
                FactoryFinder ff = ffr.resolveBootstrapFactoryFinder(classResolver);
                answer.getCamelContextExtension().setBootstrapFactoryFinder(ff);
                ff = ffr.resolveDefaultFactoryFinder(classResolver);
                answer.getCamelContextExtension().setDefaultFactoryFinder(ff);

                answer.getCamelContextExtension().addContextPlugin(ComponentResolver.class,
                        new DependencyDownloaderComponentResolver(answer, stubPattern, silent));
                answer.getCamelContextExtension().addContextPlugin(UriFactoryResolver.class,
                        new DependencyDownloaderUriFactoryResolver(answer));
                answer.getCamelContextExtension().addContextPlugin(DataFormatResolver.class,
                        new DependencyDownloaderDataFormatResolver(answer));
                answer.getCamelContextExtension().addContextPlugin(LanguageResolver.class,
                        new DependencyDownloaderLanguageResolver(answer));
                answer.getCamelContextExtension().addContextPlugin(ResourceLoader.class,
                        new DependencyDownloaderResourceLoader(answer));
            }
            answer.setInjector(new KameletMainInjector(answer.getInjector(), stubPattern, silent));
            if (download) {
                Object kameletsVersion = getInitialProperties().get("camel.jbang.kameletsVersion");
                if (kameletsVersion != null) {
                    answer.addService(new DependencyDownloaderKamelet(answer, kameletsVersion.toString()));
                } else {
                    answer.addService(new DependencyDownloaderKamelet(answer));
                }
                answer.getCamelContextExtension().getRegistry().bind(DownloadModelineParser.class.getSimpleName(),
                        new DownloadModelineParser(answer));
            }

            // reloader
            String sourceDir = getInitialProperties().getProperty("camel.jbang.sourceDir");
            if (sourceDir != null) {
                if (console || health) {
                    // allow to upload source via http when HTTP console enabled
                    configure().httpServer().withEnabled(true);
                    configure().httpServer().withUploadEnabled(true);
                    configure().httpServer().withUploadSourceDir(sourceDir);
                }
                RouteOnDemandReloadStrategy reloader = new RouteOnDemandReloadStrategy(sourceDir, true);
                reloader.setPattern("*");
                answer.addService(reloader);
            } else {
                answer.addService(new DefaultContextReloadStrategy());
            }

        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeException(e);
        }

        return answer;
    }

    @Override
    protected void configurePropertiesService(CamelContext camelContext) throws Exception {
        super.configurePropertiesService(camelContext);

        // properties functions, which can download
        if (download) {
            org.apache.camel.component.properties.PropertiesComponent pc
                    = (org.apache.camel.component.properties.PropertiesComponent) camelContext.getPropertiesComponent();
            pc.setPropertiesFunctionResolver(new DependencyDownloaderPropertiesFunctionResolver(camelContext));
        }
    }

    @Override
    protected void autoconfigure(CamelContext camelContext) throws Exception {
        // create classloader that may include additional JARs
        camelContext.setApplicationContextClassLoader(createApplicationContextClassLoader());
        // auto configure camel afterwards
        super.autoconfigure(camelContext);
    }

    @Override
    protected LifecycleStrategy createLifecycleStrategy(CamelContext camelContext) {
        return new KameletAutowiredLifecycleStrategy(camelContext, stubPattern, silent);
    }

    protected ClassLoader createApplicationContextClassLoader() {
        if (classLoader == null) {
            // jars need to be added to dependency downloader classloader
            List<String> jars = new ArrayList<>();
            // create class loader (that are download capable) only once
            // any additional files to add to classpath
            ClassLoader parentCL = KameletMain.class.getClassLoader();
            String cpFiles = getInitialProperties().getProperty("camel.jbang.classpathFiles");
            if (cpFiles != null) {
                String[] arr = cpFiles.split(",");
                List<String> files = new ArrayList<>();
                for (String s : arr) {
                    if (s.endsWith(".jar")) {
                        jars.add(s);
                    } else {
                        files.add(s);
                    }
                }
                if (!files.isEmpty()) {
                    parentCL = new ExtraFilesClassLoader(parentCL, files);
                    LOG.info("Additional files added to classpath: {}", String.join(", ", files));
                }
            }
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
        if (download) {
            DependencyDownloaderRoutesLoader routesLoader;

            Object kameletsVersion = getInitialProperties().get("camel.jbang.kameletsVersion");
            if (kameletsVersion != null) {
                routesLoader = new DependencyDownloaderRoutesLoader(camelContext, kameletsVersion.toString());
            } else {
                routesLoader = new DependencyDownloaderRoutesLoader(camelContext);
            }

            // use resolvers that can auto downloaded
            camelContext.getCamelContextExtension()
                    .addContextPlugin(RoutesLoader.class, routesLoader);
        } else {
            super.configureRoutesLoader(camelContext);
        }
    }

    /**
     * Sets initial properties that are specific to camel-kamelet-main
     */
    protected void configureInitialProperties(String location) {
        addInitialProperty("camel.component.kamelet.location", location);
        addInitialProperty("camel.component.rest.consumerComponentName", "platform-http");
        addInitialProperty("camel.component.rest.producerComponentName", "vertx-http");
    }

    protected String startupInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("Using Java ").append(System.getProperty("java.version"));
        String pid = getPid();
        if (pid != null) {
            sb.append(" with PID ").append(pid);
        }
        sb.append(". Started by ").append(System.getProperty("user.name"));
        sb.append(" in ").append(System.getProperty("user.dir"));

        return sb.toString();
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

    @Override
    protected void doShutdown() throws Exception {
        // TODO: manage BeanFactory as a field and clear the beans here
    }

    private static String getPid() {
        return String.valueOf(ProcessHandle.current().pid());
    }

}
