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
package org.apache.camel.test.junit5;

import java.lang.annotation.Annotation;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.FluentProducerTemplate;
import org.apache.camel.Message;
import org.apache.camel.NamedNode;
import org.apache.camel.NoSuchEndpointException;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.RouteConfigurationsBuilder;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.Service;
import org.apache.camel.ServiceStatus;
import org.apache.camel.api.management.ManagedCamelContext;
import org.apache.camel.api.management.mbean.ManagedCamelContextMBean;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.InterceptSendToMockEndpointStrategy;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.debugger.DefaultDebugger;
import org.apache.camel.model.Model;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.spi.CamelBeanPostProcessor;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.spi.PropertiesSource;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.BreakpointSupport;
import org.apache.camel.support.EndpointHelper;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.test.CamelRouteCoverageDumper;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.URISupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.test.junit5.TestSupport.isCamelDebugPresent;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * A useful base class which creates a {@link org.apache.camel.CamelContext} with some routes along with a
 * {@link org.apache.camel.ProducerTemplate} for use in the test case Do <tt>not</tt> use this class for Spring Boot
 * testing.
 */
public abstract class CamelTestSupport
        implements BeforeEachCallback, AfterEachCallback, AfterAllCallback, BeforeAllCallback, BeforeTestExecutionCallback,
        AfterTestExecutionCallback {

    /**
     * JVM system property which can be set to true to turn on dumping route coverage statistics.
     */
    public static final String ROUTE_COVERAGE_ENABLED = "CamelTestRouteCoverage";

    private static final Logger LOG = LoggerFactory.getLogger(CamelTestSupport.class);
    private static final ThreadLocal<ModelCamelContext> THREAD_CAMEL_CONTEXT = new ThreadLocal<>();
    private static final ThreadLocal<ProducerTemplate> THREAD_TEMPLATE = new ThreadLocal<>();
    private static final ThreadLocal<FluentProducerTemplate> THREAD_FLUENT_TEMPLATE = new ThreadLocal<>();
    private static final ThreadLocal<ConsumerTemplate> THREAD_CONSUMER = new ThreadLocal<>();
    private static final ThreadLocal<Service> THREAD_SERVICE = new ThreadLocal<>();
    public static final String SEPARATOR = "********************************************************************************";
    protected Properties extra;
    protected volatile ModelCamelContext context;
    protected volatile ProducerTemplate template;
    protected volatile FluentProducerTemplate fluentTemplate;
    protected volatile ConsumerTemplate consumer;
    protected volatile Service camelContextService;
    @RegisterExtension
    protected CamelTestSupport camelTestSupportExtension = this;
    private boolean useRouteBuilder = true;
    private final DebugBreakpoint breakpoint = new DebugBreakpoint();
    private final StopWatch watch = new StopWatch();
    private final Map<String, String> fromEndpoints = new HashMap<>();
    private static final ThreadLocal<AtomicInteger> TESTS = new ThreadLocal<>();
    private static final ThreadLocal<CamelTestSupport> INSTANCE = new ThreadLocal<>();
    private String currentTestName;
    private boolean isCreateCamelContextPerClass = false;
    private final CamelRouteCoverageDumper routeCoverageDumper = new CamelRouteCoverageDumper();
    private ExtensionContext.Store globalStore;

    @Override
    public void afterTestExecution(ExtensionContext context) throws Exception {
        watch.taken();
    }

    @Override
    public void beforeTestExecution(ExtensionContext context) throws Exception {
        watch.restart();
    }

    public long timeTaken() {
        return watch.taken();
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        currentTestName = context.getDisplayName();
        globalStore = context.getStore(ExtensionContext.Namespace.GLOBAL);
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        DefaultCamelContext.clearOptions();
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        isCreateCamelContextPerClass
                = context.getTestInstanceLifecycle().filter(lc -> lc.equals(Lifecycle.PER_CLASS)).isPresent();
    }

    @Override
    public void afterAll(ExtensionContext context) {
        CamelTestSupport support = INSTANCE.get();
        if (support != null && support.isCreateCamelContextPerClass()) {
            try {
                support.tearDownCreateCamelContextPerClass();
            } catch (Exception e) {
                // ignore
            }
        }
    }

    /**
     * Use the RouteBuilder or not
     *
     * @return <tt>true</tt> then {@link CamelContext} will be auto started, <tt>false</tt> then {@link CamelContext}
     *         will <b>not</b> be auto started (you will have to start it manually)
     */
    public boolean isUseRouteBuilder() {
        return useRouteBuilder;
    }

    public void setUseRouteBuilder(boolean useRouteBuilder) {
        this.useRouteBuilder = useRouteBuilder;
    }

    /**
     * Whether to dump route coverage stats at the end of the test.
     * <p/>
     * This allows tooling or manual inspection of the stats, so you can generate a route trace diagram of which EIPs
     * have been in use and which have not. Similar concepts as a code coverage report.
     * <p/>
     * You can also turn on route coverage globally via setting JVM system property
     * <tt>CamelTestRouteCoverage=true</tt>.
     *
     * @return <tt>true</tt> to write route coverage status in an xml file in the <tt>target/camel-route-coverage</tt>
     *         directory after the test has finished.
     */
    public boolean isDumpRouteCoverage() {
        return false;
    }

    /**
     * Override when using <a href="http://camel.apache.org/advicewith.html">advice with</a> and return <tt>true</tt>.
     * This helps knowing advice with is to be used, and {@link CamelContext} will not be started before the advice with
     * takes place. This helps by ensuring the advice with has been property setup before the {@link CamelContext} is
     * started
     * <p/>
     * <b>Important:</b> Its important to start {@link CamelContext} manually from the unit test after you are done
     * doing all the advice with.
     *
     * @return <tt>true</tt> if you use advice with in your unit tests.
     */
    public boolean isUseAdviceWith() {
        return false;
    }

    /**
     * Tells whether {@link CamelContext} should be setup per test or per class.
     * <p/>
     * By default it will be setup/teardown per test method. This method returns <code>true</code> when the camel test
     * class is annotated with @TestInstance(TestInstance.Lifecycle.PER_CLASS).
     * <p/>
     * <b>Important:</b> Use this with care as the {@link CamelContext} will carry over state from previous tests, such
     * as endpoints, components etc. So you cannot use this in all your tests.
     * <p/>
     * Setting up {@link CamelContext} uses the {@link #doPreSetup()}, {@link #doSetUp()}, and {@link #doPostSetup()}
     * methods in that given order.
     *
     * @return <tt>true</tt> per class, <tt>false</tt> per test.
     */
    public final boolean isCreateCamelContextPerClass() {
        return isCreateCamelContextPerClass;
    }

    /**
     * Override to enable auto mocking endpoints based on the pattern.
     * <p/>
     * Return <tt>*</tt> to mock all endpoints.
     *
     * @see EndpointHelper#matchEndpoint(CamelContext, String, String)
     */
    public String isMockEndpoints() {
        return null;
    }

    /**
     * Override to enable auto mocking endpoints based on the pattern, and <b>skip</b> sending to original endpoint.
     * <p/>
     * Return <tt>*</tt> to mock all endpoints.
     *
     * @see EndpointHelper#matchEndpoint(CamelContext, String, String)
     */
    public String isMockEndpointsAndSkip() {
        return null;
    }

    public void replaceRouteFromWith(String routeId, String fromEndpoint) {
        fromEndpoints.put(routeId, fromEndpoint);
    }

    /**
     * Used for filtering routes matching the given pattern, which follows the following rules:
     * <p>
     * - Match by route id - Match by route input endpoint uri
     * <p>
     * The matching is using exact match, by wildcard and regular expression.
     * <p>
     * For example to only include routes which starts with foo in their route id's, use: include=foo&#42; And to
     * exclude routes which starts from JMS endpoints, use: exclude=jms:&#42;
     * <p>
     * Multiple patterns can be separated by comma, for example to exclude both foo and bar routes, use:
     * exclude=foo&#42;,bar&#42;
     * <p>
     * Exclude takes precedence over include.
     */
    public String getRouteFilterIncludePattern() {
        return null;
    }

    /**
     * Used for filtering routes matching the given pattern, which follows the following rules:
     * <p>
     * - Match by route id - Match by route input endpoint uri
     * <p>
     * The matching is using exact match, by wildcard and regular expression.
     * <p>
     * For example to only include routes which starts with foo in their route id's, use: include=foo&#42; And to
     * exclude routes which starts from JMS endpoints, use: exclude=jms:&#42;
     * <p>
     * Multiple patterns can be separated by comma, for example to exclude both foo and bar routes, use:
     * exclude=foo&#42;,bar&#42;
     * <p>
     * Exclude takes precedence over include.
     */
    public String getRouteFilterExcludePattern() {
        return null;
    }

    /**
     * Gets the name of the current test being executed.
     */
    public final String getCurrentTestName() {
        return currentTestName;
    }

    /**
     * Override to enable debugger
     * <p/>
     * Is default <tt>false</tt>
     */
    public boolean isUseDebugger() {
        return false;
    }

    public Service getCamelContextService() {
        return camelContextService;
    }

    public Service camelContextService() {
        return camelContextService;
    }

    public CamelContext context() {
        return context;
    }

    public ProducerTemplate template() {
        return template;
    }

    public FluentProducerTemplate fluentTemplate() {
        return fluentTemplate;
    }

    public ConsumerTemplate consumer() {
        return consumer;
    }

    /**
     * Allows a service to be registered a separate lifecycle service to start and stop the context; such as for Spring
     * when the ApplicationContext is started and stopped, rather than directly stopping the CamelContext
     */
    public void setCamelContextService(Service service) {
        camelContextService = service;
        THREAD_SERVICE.set(camelContextService);
    }

    @BeforeEach
    public void setUp() throws Exception {
        LOG.info(SEPARATOR);
        LOG.info("Testing: {} ({})", currentTestName, getClass().getName());
        LOG.info(SEPARATOR);

        doSpringBootCheck();
        doQuarkusCheck();

        if (isCreateCamelContextPerClass()) {
            INSTANCE.set(this);
            AtomicInteger v = TESTS.get();
            if (v == null) {
                v = new AtomicInteger();
                TESTS.set(v);
            }
            if (v.getAndIncrement() == 0) {
                LOG.debug("Setup CamelContext before running first test");
                // test is per class, so only setup once (the first time)
                doSpringBootCheck();
                setupResources();
                doPreSetup();
                doSetUp();
                doPostSetup();
            } else {
                LOG.debug("Reset between test methods");
                // and in between tests we must do IoC and reset mocks
                postProcessTest();
                MockEndpoint.resetMocks(context);
            }
        } else {
            // test is per test so always setup
            setupResources();
            doPreSetup();
            doSetUp();
            doPostSetup();
        }

        // only start timing after all the setup
        watch.restart();
    }

    /**
     * Strategy to perform any pre setup, before {@link CamelContext} is created
     */
    protected void doPreSetup() throws Exception {
        // noop
    }

    /**
     * Strategy to perform any post setup after {@link CamelContext} is created
     */
    protected void doPostSetup() throws Exception {
        // noop
    }

    /**
     * Detects if this is a Spring-Boot test and throws an exception, as these base classes is not intended for testing
     * Camel on Spring Boot.
     */
    protected void doSpringBootCheck() {
        boolean springBoot = hasClassAnnotation("org.springframework.boot.test.context.SpringBootTest");
        if (springBoot) {
            throw new RuntimeException(
                    "Spring Boot detected: The CamelTestSupport/CamelSpringTestSupport class is not intended for Camel testing with Spring Boot.");
        }
    }

    /**
     * Detects if this is a Camel-quarkus test and throw an exception, as these base classes is not intended for testing
     * Camel onQuarkus.
     */
    protected void doQuarkusCheck() {
        boolean quarkus = hasClassAnnotation("io.quarkus.test.junit.QuarkusTest") ||
                hasClassAnnotation("org.apache.camel.quarkus.test.CamelQuarkusTest");
        if (quarkus) {
            throw new RuntimeException(
                    "Quarkus detected: The CamelTestSupport/CamelSpringTestSupport class is not intended for Camel testing with Quarkus.");
        }
    }

    protected void doSetUp() throws Exception {
        LOG.debug("setUp test");
        // jmx is enabled if we have configured to use it, if dump route coverage is enabled (it requires JMX) or if
        // the component camel-debug is in the classpath
        if (useJmx() || isRouteCoverageEnabled() || isCamelDebugPresent()) {
            enableJMX();
        } else {
            disableJMX();
        }

        context = (ModelCamelContext) createCamelContext();
        THREAD_CAMEL_CONTEXT.set(context);

        assertNotNull(context, "No context found!");

        // add custom beans
        bindToRegistry(context.getRegistry());

        // reduce default shutdown timeout to avoid waiting for 300 seconds
        context.getShutdownStrategy().setTimeout(getShutdownTimeout());

        // set debugger if enabled
        if (isUseDebugger()) {
            if (context.getStatus().equals(ServiceStatus.Started)) {
                LOG.info("Cannot setting the Debugger to the starting CamelContext, stop the CamelContext now.");
                // we need to stop the context first to setup the debugger
                context.stop();
            }
            context.setDebugging(true);
            context.setDebugger(new DefaultDebugger());
            context.getDebugger().addBreakpoint(breakpoint);
            // when stopping CamelContext it will automatically remove the breakpoint
        }

        template = context.createProducerTemplate();
        template.start();
        fluentTemplate = context.createFluentProducerTemplate();
        fluentTemplate.start();
        consumer = context.createConsumerTemplate();
        consumer.start();

        THREAD_TEMPLATE.set(template);
        THREAD_FLUENT_TEMPLATE.set(fluentTemplate);
        THREAD_CONSUMER.set(consumer);

        // enable auto mocking if enabled
        String pattern = isMockEndpoints();
        if (pattern != null) {
            context.getCamelContextExtension()
                    .registerEndpointCallback(new InterceptSendToMockEndpointStrategy(pattern));
        }
        pattern = isMockEndpointsAndSkip();
        if (pattern != null) {
            context.getCamelContextExtension()
                    .registerEndpointCallback(new InterceptSendToMockEndpointStrategy(pattern, true));
        }

        // configure properties component (mandatory for testing)
        PropertiesComponent pc = context.getPropertiesComponent();
        if (extra == null) {
            extra = useOverridePropertiesWithPropertiesComponent();
        }
        if (extra != null && !extra.isEmpty()) {
            pc.setOverrideProperties(extra);
        }
        pc.addPropertiesSource(new PropertiesSource() {
            @Override
            public String getName() {
                return "junit-store";
            }

            @Override
            public String getProperty(String name) {
                return globalStore.get(name, String.class);
            }
        });
        Boolean ignore = ignoreMissingLocationWithPropertiesComponent();
        if (ignore != null) {
            pc.setIgnoreMissingLocation(ignore);
        }

        String include = getRouteFilterIncludePattern();
        String exclude = getRouteFilterExcludePattern();
        if (include != null || exclude != null) {
            LOG.info("Route filtering pattern: include={}, exclude={}", include, exclude);
            context.getCamelContextExtension().getContextPlugin(Model.class).setRouteFilterPattern(include, exclude);
        }

        // prepare for in-between tests
        postProcessTest();

        if (isUseRouteBuilder()) {
            RoutesBuilder[] builders = createRouteBuilders();
            // add configuration before routes
            for (RoutesBuilder builder : builders) {
                if (builder instanceof RouteConfigurationsBuilder) {
                    LOG.debug("Using created route configuration: {}", builder);
                    context.addRoutesConfigurations((RouteConfigurationsBuilder) builder);
                }
            }
            for (RoutesBuilder builder : builders) {
                LOG.debug("Using created route builder to add routes: {}", builder);
                context.addRoutes(builder);
            }
            for (RoutesBuilder builder : builders) {
                LOG.debug("Using created route builder to add templated routes: {}", builder);
                context.addTemplatedRoutes(builder);
            }
            replaceFromEndpoints();
            boolean skip = "true".equalsIgnoreCase(System.getProperty("skipStartingCamelContext"));
            if (skip) {
                LOG.info("Skipping starting CamelContext as system property skipStartingCamelContext is set to be true.");
            } else if (isUseAdviceWith()) {
                LOG.info("Skipping starting CamelContext as isUseAdviceWith is set to true.");
            } else {
                startCamelContext();
            }
        } else {
            replaceFromEndpoints();
            LOG.debug("Using route builder from the created context: {}", context);
        }
        LOG.debug("Routing Rules are: {}", context.getRoutes());

        assertValidContext(context);
    }

    private void replaceFromEndpoints() throws Exception {
        for (final Map.Entry<String, String> entry : fromEndpoints.entrySet()) {
            AdviceWith.adviceWith(context.getRouteDefinition(entry.getKey()), context, new AdviceWithRouteBuilder() {
                @Override
                public void configure() {
                    replaceFromWith(entry.getValue());
                }
            });
        }
    }

    private boolean isRouteCoverageEnabled() {
        return System.getProperty(ROUTE_COVERAGE_ENABLED, "false").equalsIgnoreCase("true") || isDumpRouteCoverage();
    }

    @AfterEach
    public void tearDown() throws Exception {
        long time = watch.taken();

        LOG.info(SEPARATOR);
        LOG.info("Testing done: {} ({})", currentTestName, getClass().getName());
        LOG.info("Took: {} ({} millis)", TimeUtils.printDuration(time, true), time);

        // if we should dump route stats, then write that to a file
        if (isRouteCoverageEnabled()) {
            String className = this.getClass().getSimpleName();
            String dir = "target/camel-route-coverage";
            String name = className + "-" + StringHelper.before(currentTestName, "(") + ".xml";

            ManagedCamelContext mc
                    = context != null ? context.getCamelContextExtension().getContextPlugin(ManagedCamelContext.class) : null;
            ManagedCamelContextMBean managedCamelContext = mc != null ? mc.getManagedCamelContext() : null;
            if (managedCamelContext == null) {
                LOG.warn("Cannot dump route coverage to file as JMX is not enabled. "
                         + "Add camel-management JAR as dependency and/or override useJmx() method to enable JMX in the unit test classes.");
            } else {
                routeCoverageDumper.dump(managedCamelContext, context, dir, name, getClass().getName(), currentTestName,
                        timeTaken());
            }
        }
        LOG.info(SEPARATOR);

        if (isCreateCamelContextPerClass()) {
            // will tear down test specially in afterAll callback
        } else {
            LOG.debug("tearDown()");
            doStopTemplates(consumer, template, fluentTemplate);
            doStopCamelContext(context, camelContextService);
            doPostTearDown();
            cleanupResources();
        }
    }

    void tearDownCreateCamelContextPerClass() throws Exception {
        LOG.debug("tearDownCreateCamelContextPerClass()");
        TESTS.remove();
        doStopTemplates(THREAD_CONSUMER.get(), THREAD_TEMPLATE.get(), THREAD_FLUENT_TEMPLATE.get());
        doStopCamelContext(THREAD_CAMEL_CONTEXT.get(), THREAD_SERVICE.get());
        doPostTearDown();
        cleanupResources();
    }

    /**
     * Strategy to perform any post action, after {@link CamelContext} is stopped
     */
    protected void doPostTearDown() throws Exception {
        // noop
    }

    /**
     * Strategy to perform resources setup, before {@link CamelContext} is created
     */
    protected void setupResources() throws Exception {
    }

    /**
     * Strategy to perform resources cleanup, after {@link CamelContext} is stopped
     */
    protected void cleanupResources() throws Exception {
        // noop
    }

    /**
     * Returns the timeout to use when shutting down (unit in seconds).
     * <p/>
     * Will default use 10 seconds.
     *
     * @return the timeout to use
     */
    protected int getShutdownTimeout() {
        return 10;
    }

    /**
     * Whether or not JMX should be used during testing.
     *
     * @return <tt>false</tt> by default.
     */
    protected boolean useJmx() {
        return false;
    }

    /**
     * Override this method to include and override properties with the Camel {@link PropertiesComponent}.
     *
     * @return additional properties to add/override.
     */
    protected Properties useOverridePropertiesWithPropertiesComponent() {
        return null;
    }

    /**
     * Whether to ignore missing locations with the {@link PropertiesComponent}. For example when unit testing you may
     * want to ignore locations that are not available in the environment used for testing.
     *
     * @return <tt>true</tt> to ignore, <tt>false</tt> to not ignore, and <tt>null</tt> to leave as configured on the
     *         {@link PropertiesComponent}
     */
    protected Boolean ignoreMissingLocationWithPropertiesComponent() {
        return null;
    }

    protected void postProcessTest() throws Exception {
        context = THREAD_CAMEL_CONTEXT.get();
        template = THREAD_TEMPLATE.get();
        fluentTemplate = THREAD_FLUENT_TEMPLATE.get();
        consumer = THREAD_CONSUMER.get();
        camelContextService = THREAD_SERVICE.get();
        applyCamelPostProcessor();
    }

    /**
     * Applies the {@link CamelBeanPostProcessor} to this instance.
     * <p>
     * Derived classes using IoC / DI frameworks may wish to turn this into a NoOp such as for CDI we would just use CDI
     * to inject this
     */
    protected void applyCamelPostProcessor() throws Exception {
        // use the bean post processor if the test class is not dependency
        // injected already by Spring Framework
        boolean spring = hasClassAnnotation("org.springframework.boot.test.context.SpringBootTest",
                "org.springframework.context.annotation.ComponentScan");
        if (!spring) {
            PluginHelper.getBeanPostProcessor(context).postProcessBeforeInitialization(this,
                    getClass().getName());
            PluginHelper.getBeanPostProcessor(context).postProcessAfterInitialization(this,
                    getClass().getName());
        }
    }

    /**
     * Does this test class have any of the following annotations on the class-level.
     */
    protected boolean hasClassAnnotation(String... names) {
        for (String name : names) {
            for (Annotation ann : getClass().getAnnotations()) {
                String annName = ann.annotationType().getName();
                if (annName.equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    protected void stopCamelContext() throws Exception {
        doStopCamelContext(context, camelContextService);
    }

    protected void doStopCamelContext(CamelContext context, Service camelContextService) {
        if (camelContextService != null) {
            if (camelContextService == THREAD_SERVICE.get()) {
                THREAD_SERVICE.remove();
            }
            camelContextService.stop();
        } else {
            if (context != null) {
                if (context == THREAD_CAMEL_CONTEXT.get()) {
                    THREAD_CAMEL_CONTEXT.remove();
                }
                context.stop();
            }
        }
    }

    private static void doStopTemplates(
            ConsumerTemplate consumer, ProducerTemplate template, FluentProducerTemplate fluentTemplate) {
        if (consumer != null) {
            if (consumer == THREAD_CONSUMER.get()) {
                THREAD_CONSUMER.remove();
            }
            consumer.stop();
        }
        if (template != null) {
            if (template == THREAD_TEMPLATE.get()) {
                THREAD_TEMPLATE.remove();
            }
            template.stop();
        }
        if (fluentTemplate != null) {
            if (fluentTemplate == THREAD_FLUENT_TEMPLATE.get()) {
                THREAD_FLUENT_TEMPLATE.remove();
            }
            fluentTemplate.stop();
        }
    }

    protected void startCamelContext() throws Exception {
        if (camelContextService != null) {
            camelContextService.start();
        } else {
            if (context instanceof DefaultCamelContext) {
                DefaultCamelContext defaultCamelContext = (DefaultCamelContext) context;
                if (!defaultCamelContext.isStarted()) {
                    defaultCamelContext.start();
                }
            } else {
                context.start();
            }
        }
    }

    protected CamelContext createCamelContext() throws Exception {
        Registry registry = createCamelRegistry();

        CamelContext retContext;
        if (registry != null) {
            retContext = new DefaultCamelContext(registry);
        } else {
            retContext = new DefaultCamelContext();
        }

        return retContext;
    }

    /**
     * Allows to bind custom beans to the Camel {@link Registry}.
     */
    protected void bindToRegistry(Registry registry) throws Exception {
        // noop
    }

    /**
     * Override to use a custom {@link Registry}.
     * <p>
     * However if you need to bind beans to the registry then this is possible already with the bind method on registry,
     * and there is no need to override this method.
     */
    protected Registry createCamelRegistry() throws Exception {
        return null;
    }

    /**
     * Factory method which derived classes can use to create a {@link RouteBuilder} to define the routes for testing
     */
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // no routes added by default
            }
        };
    }

    /**
     * Factory method which derived classes can use to create an array of {@link org.apache.camel.builder.RouteBuilder}s
     * to define the routes for testing
     *
     * @see #createRouteBuilder()
     */
    protected RoutesBuilder[] createRouteBuilders() throws Exception {
        return new RoutesBuilder[] { createRouteBuilder() };
    }

    /**
     * Resolves a mandatory endpoint for the given URI or an exception is thrown
     *
     * @param  uri the Camel <a href="">URI</a> to use to create or resolve an endpoint
     * @return     the endpoint
     */
    protected Endpoint resolveMandatoryEndpoint(String uri) {
        return TestSupport.resolveMandatoryEndpoint(context, uri);
    }

    /**
     * Resolves a mandatory endpoint for the given URI and expected type or an exception is thrown
     *
     * @param  uri the Camel <a href="">URI</a> to use to create or resolve an endpoint
     * @return     the endpoint
     */
    protected <T extends Endpoint> T resolveMandatoryEndpoint(String uri, Class<T> endpointType) {
        return TestSupport.resolveMandatoryEndpoint(context, uri, endpointType);
    }

    /**
     * Resolves the mandatory Mock endpoint using a URI of the form <code>mock:someName</code>
     *
     * @param  uri the URI which typically starts with "mock:" and has some name
     * @return     the mandatory mock endpoint or an exception is thrown if it could not be resolved
     */
    protected MockEndpoint getMockEndpoint(String uri) {
        return getMockEndpoint(uri, true);
    }

    /**
     * Resolves the {@link MockEndpoint} using a URI of the form <code>mock:someName</code>, optionally creating it if
     * it does not exist. This implementation will lookup existing mock endpoints and match on the mock queue name, eg
     * mock:foo and mock:foo?retainFirst=5 would match as the queue name is foo.
     *
     * @param  uri                     the URI which typically starts with "mock:" and has some name
     * @param  create                  whether or not to allow the endpoint to be created if it doesn't exist
     * @return                         the mock endpoint or an {@link NoSuchEndpointException} is thrown if it could not
     *                                 be resolved
     * @throws NoSuchEndpointException is the mock endpoint does not exist
     */
    protected MockEndpoint getMockEndpoint(String uri, boolean create) throws NoSuchEndpointException {
        // look for existing mock endpoints that have the same queue name, and
        // to
        // do that we need to normalize uri and strip out query parameters and
        // whatnot
        String n;
        try {
            n = URISupport.normalizeUri(uri);
        } catch (URISyntaxException e) {
            throw RuntimeCamelException.wrapRuntimeException(e);
        }
        // strip query
        final String target = StringHelper.before(n, "?", n);

        // lookup endpoints in registry and try to find it
        MockEndpoint found = (MockEndpoint) context.getEndpointRegistry().values().stream()
                .filter(e -> e instanceof MockEndpoint).filter(e -> {
                    String t = e.getEndpointUri();
                    // strip query
                    int idx2 = t.indexOf('?');
                    if (idx2 != -1) {
                        t = t.substring(0, idx2);
                    }
                    return t.equals(target);
                }).findFirst().orElse(null);

        if (found != null) {
            return found;
        }

        if (create) {
            return resolveMandatoryEndpoint(uri, MockEndpoint.class);
        } else {
            throw new NoSuchEndpointException(String.format("MockEndpoint %s does not exist.", uri));
        }
    }

    /**
     * Sends a message to the given endpoint URI with the body value
     *
     * @param endpointUri the URI of the endpoint to send to
     * @param body        the body for the message
     */
    protected void sendBody(String endpointUri, final Object body) {
        template.send(endpointUri, exchange -> {
            Message in = exchange.getIn();
            in.setBody(body);
        });
    }

    /**
     * Sends a message to the given endpoint URI with the body value and specified headers
     *
     * @param endpointUri the URI of the endpoint to send to
     * @param body        the body for the message
     * @param headers     any headers to set on the message
     */
    protected void sendBody(String endpointUri, final Object body, final Map<String, Object> headers) {
        template.send(endpointUri, exchange -> {
            Message in = exchange.getIn();
            in.setBody(body);
            for (Map.Entry<String, Object> entry : headers.entrySet()) {
                in.setHeader(entry.getKey(), entry.getValue());
            }
        });
    }

    /**
     * Sends messages to the given endpoint for each of the specified bodies
     *
     * @param endpointUri the endpoint URI to send to
     * @param bodies      the bodies to send, one per message
     */
    protected void sendBodies(String endpointUri, Object... bodies) {
        for (Object body : bodies) {
            sendBody(endpointUri, body);
        }
    }

    /**
     * Creates an exchange with the given body
     */
    protected Exchange createExchangeWithBody(Object body) {
        return TestSupport.createExchangeWithBody(context, body);
    }

    /**
     * Asserts that the given language name and expression evaluates to the given value on a specific exchange
     */
    protected void assertExpression(Exchange exchange, String languageName, String expressionText, Object expectedValue) {
        Language language = assertResolveLanguage(languageName);

        Expression expression = language.createExpression(expressionText);
        assertNotNull(expression, "No Expression could be created for text: " + expressionText + " language: " + language);

        TestSupport.assertExpression(expression, exchange, expectedValue);
    }

    /**
     * Asserts that the given language name and predicate expression evaluates to the expected value on the message
     * exchange
     */
    protected void assertPredicate(String languageName, String expressionText, Exchange exchange, boolean expected) {
        Language language = assertResolveLanguage(languageName);

        Predicate predicate = language.createPredicate(expressionText);
        assertNotNull(predicate, "No Predicate could be created for text: " + expressionText + " language: " + language);

        TestSupport.assertPredicate(predicate, exchange, expected);
    }

    /**
     * Asserts that the language name can be resolved
     */
    protected Language assertResolveLanguage(String languageName) {
        Language language = context.resolveLanguage(languageName);
        assertNotNull(language, "Nog language found for name: " + languageName);
        return language;
    }

    protected void assertValidContext(CamelContext context) {
        assertNotNull(context, "No context found!");
    }

    protected <T extends Endpoint> T getMandatoryEndpoint(String uri, Class<T> type) {
        T endpoint = context.getEndpoint(uri, type);
        assertNotNull(endpoint, "No endpoint found for uri: " + uri);
        return endpoint;
    }

    protected Endpoint getMandatoryEndpoint(String uri) {
        Endpoint endpoint = context.getEndpoint(uri);
        assertNotNull(endpoint, "No endpoint found for uri: " + uri);
        return endpoint;
    }

    /**
     * Disables the JMX agent. Must be called before the {@link #setUp()} method.
     */
    protected void disableJMX() {
        DefaultCamelContext.setDisableJmx(true);
    }

    /**
     * Enables the JMX agent. Must be called before the {@link #setUp()} method.
     */
    protected void enableJMX() {
        DefaultCamelContext.setDisableJmx(false);
    }

    /**
     * Single step debugs and Camel invokes this method before entering the given processor
     */
    protected void debugBefore(
            Exchange exchange, Processor processor, ProcessorDefinition<?> definition, String id, String label) {
    }

    /**
     * Single step debugs and Camel invokes this method after processing the given processor
     */
    protected void debugAfter(
            Exchange exchange, Processor processor, ProcessorDefinition<?> definition, String id, String label,
            long timeTaken) {
    }

    /**
     * To easily debug by overriding the <tt>debugBefore</tt> and <tt>debugAfter</tt> methods.
     */
    private class DebugBreakpoint extends BreakpointSupport {

        @Override
        public void beforeProcess(Exchange exchange, Processor processor, NamedNode definition) {
            CamelTestSupport.this.debugBefore(exchange, processor, (ProcessorDefinition<?>) definition, definition.getId(),
                    definition.getLabel());
        }

        @Override
        public void afterProcess(Exchange exchange, Processor processor, NamedNode definition, long timeTaken) {
            CamelTestSupport.this.debugAfter(exchange, processor, (ProcessorDefinition<?>) definition, definition.getId(),
                    definition.getLabel(), timeTaken);
        }
    }

}
