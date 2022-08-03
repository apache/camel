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

import java.lang.management.ManagementFactory;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;

import org.apache.camel.CamelContext;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.main.download.AutoConfigureDownloadListener;
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
import org.apache.camel.main.download.DownloadListener;
import org.apache.camel.main.download.KameletMainInjector;
import org.apache.camel.main.download.KnownDependenciesResolver;
import org.apache.camel.main.download.MavenDependencyDownloader;
import org.apache.camel.main.http.VertxHttpServer;
import org.apache.camel.main.injection.AnnotationDependencyInjection;
import org.apache.camel.main.util.ExtraFilesClassLoader;
import org.apache.camel.spi.Registry;
import org.apache.camel.startup.jfr.FlightRecorderStartupStepRecorder;

/**
 * A Main class for booting up Camel with Kamelet in standalone mode.
 */
public class KameletMain extends MainCommandLineSupport {

    public static final String DEFAULT_KAMELETS_LOCATION = "classpath:/kamelets,github:apache:camel-kamelets/kamelets";

    protected final MainRegistry registry = new MainRegistry();
    private boolean download = true;
    private String repos;
    private boolean fresh;
    private boolean stub;
    private DownloadListener downloadListener;
    private DependencyDownloaderClassLoader classLoader;

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

    public boolean isStub() {
        return stub;
    }

    /**
     * Whether to use stub endpoints instead of creating the actual endpoints. This allows to simulate using real
     * components but run without them on the classpath.
     */
    public void setStub(boolean stub) {
        this.stub = stub;
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
        // do not build/init camel context yet
        DefaultCamelContext answer = new DefaultCamelContext(false);
        answer.setLogJvmUptime(true);
        if (download) {
            answer.setApplicationContextClassLoader(createApplicationContextClassLoader());

            MavenDependencyDownloader downloader = new MavenDependencyDownloader();
            downloader.setCamelContext(answer);
            downloader.setRepos(repos);
            downloader.setFresh(fresh);
            if (downloadListener != null) {
                downloader.addDownloadListener(downloadListener);
            }
            downloader.addDownloadListener(new AutoConfigureDownloadListener());

            // register as extension
            try {
                answer.adapt(ExtendedCamelContext.class).addService(downloader);
            } catch (Exception e) {
                throw RuntimeCamelException.wrapRuntimeException(e);
            }
        }
        if (stub) {
            // turn off auto-wiring when running in stub mode
            mainConfigurationProperties.setAutowiredEnabled(false);
            // and turn off fail fast as we stub components
            mainConfigurationProperties.setAutoConfigurationFailFast(false);
        }

        String info = startupInfo();
        if (info != null) {
            LOG.info(info);
        }

        answer.setRegistry(registry);
        // load camel component and custom health-checks
        answer.setLoadHealthChecks(true);
        // annotation based dependency injection for camel/spring/quarkus annotations in DSLs and Java beans
        AnnotationDependencyInjection.initAnnotationBasedDependencyInjection(answer);

        // embed HTTP server if port is specified
        Object port = getInitialProperties().get("camel.jbang.platform-http.port");
        if (port != null) {
            VertxHttpServer.registerServer(answer, Integer.parseInt(port.toString()), stub);
        }
        boolean console = "true".equals(getInitialProperties().get("camel.jbang.console"));
        if (console && port == null) {
            // use default port 8080 if console is enabled
            VertxHttpServer.registerServer(answer, 8080, stub);
        }
        if (console) {
            // turn on developer console
            configure().withDevConsoleEnabled(true);
            // and include timestamps in events so we can see when they happened
            configure().withCamelEventsTimestampEnabled(true);
            VertxHttpServer.registerConsole(answer);
        }
        configure().withLoadHealthChecks(true);
        configure().withModeline(true);

        boolean health = "true".equals(getInitialProperties().get("camel.jbang.health"));
        if (health && port == null) {
            // use default port 8080 if console is enabled
            VertxHttpServer.registerServer(answer, 8080, stub);
        }
        if (health) {
            VertxHttpServer.registerHealthCheck(answer);
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

        try {
            // properties functions
            org.apache.camel.component.properties.PropertiesComponent pc
                    = (org.apache.camel.component.properties.PropertiesComponent) answer.getPropertiesComponent();
            pc.setPropertiesFunctionResolver(new DependencyDownloaderPropertiesFunctionResolver(answer));

            // dependencies from CLI
            Object dependencies = getInitialProperties().get("camel.jbang.dependencies");
            if (dependencies != null) {
                answer.addService(new CommandLineDependencyDownloader(answer, dependencies.toString()));
            }

            KnownDependenciesResolver known = new KnownDependenciesResolver(answer);
            known.loadKnownDependencies();
            DependencyDownloaderPropertyBindingListener listener
                    = new DependencyDownloaderPropertyBindingListener(answer, known);
            answer.getRegistry().bind(DependencyDownloaderPropertyBindingListener.class.getName(), listener);
            answer.getRegistry().bind(DependencyDownloaderStrategy.class.getName(),
                    new DependencyDownloaderStrategy(answer));
            answer.setClassResolver(new DependencyDownloaderClassResolver(answer, known));
            answer.setComponentResolver(new DependencyDownloaderComponentResolver(answer, stub));
            answer.setDataFormatResolver(new DependencyDownloaderDataFormatResolver(answer));
            answer.setLanguageResolver(new DependencyDownloaderLanguageResolver(answer));
            answer.setResourceLoader(new DependencyDownloaderResourceLoader(answer));
            answer.setInjector(new KameletMainInjector(answer.getInjector(), stub));
            answer.addService(new DependencyDownloaderKamelet(answer));
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeException(e);
        }

        return answer;
    }

    @Override
    protected void autoconfigure(CamelContext camelContext) throws Exception {
        // create classloader that may include additional JARs
        camelContext.setApplicationContextClassLoader(createApplicationContextClassLoader());
        // auto configure camel afterwards
        super.autoconfigure(camelContext);
    }

    protected ClassLoader createApplicationContextClassLoader() {
        if (classLoader == null) {
            // create class loader (that are download capable) only once
            // any additional files to add to classpath
            ClassLoader parentCL = KameletMain.class.getClassLoader();
            String cpFiles = getInitialProperties().getProperty("camel.jbang.classpathFiles");
            if (cpFiles != null) {
                parentCL = new ExtraFilesClassLoader(parentCL, cpFiles.split(","));
                LOG.info("Additional files added to classpath: {}", cpFiles);
            }
            classLoader = new DependencyDownloaderClassLoader(parentCL);
        }
        return classLoader;
    }

    @Override
    protected void configureRoutesLoader(CamelContext camelContext) {
        if (download) {
            // use resolvers that can auto downloaded
            camelContext.adapt(ExtendedCamelContext.class)
                    .setRoutesLoader(new DependencyDownloaderRoutesLoader(camelContext, configure()));
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

    private static String getPid() {
        try {
            return "" + ManagementFactory.getRuntimeMXBean().getPid();
        } catch (Throwable e) {
            return null;
        }
    }

}
