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

import java.util.Map;
import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.FluentProducerTemplate;
import org.apache.camel.Message;
import org.apache.camel.NoSuchEndpointException;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.Service;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.spi.CamelBeanPostProcessor;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.EndpointHelper;
import org.apache.camel.test.junit5.util.CamelContextTestHelper;
import org.apache.camel.test.junit5.util.ExtensionHelper;
import org.apache.camel.test.junit5.util.RouteCoverageDumperExtension;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.StringHelper;
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

import static org.apache.camel.test.junit5.util.ExtensionHelper.normalizeUri;
import static org.apache.camel.test.junit5.util.ExtensionHelper.testStartHeader;
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

    protected volatile ModelCamelContext context;
    protected volatile ProducerTemplate template;
    protected volatile FluentProducerTemplate fluentTemplate;
    protected volatile ConsumerTemplate consumer;
    @RegisterExtension
    protected CamelTestSupport camelTestSupportExtension = this;
    private final StopWatch watch = new StopWatch();
    private String currentTestName;

    private final TestExecutionConfiguration testConfigurationBuilder;
    private final CamelContextConfiguration camelContextConfiguration;

    private CamelContextManager contextManager;

    protected CamelTestSupport() {
        testConfigurationBuilder = new TestExecutionConfiguration();
        testConfigurationBuilder.withJMX(useJmx())
                .withUseRouteBuilder(isUseRouteBuilder())
                .withUseAdviceWith(isUseAdviceWith())
                .withDumpRouteCoverage(isDumpRouteCoverage());

        camelContextConfiguration = new CamelContextConfiguration();

        camelContextConfiguration
                .withCamelContextSupplier(this::createCamelContext)
                .withRegistryBinder(this::bindToRegistry)
                .withPostProcessor(this::postProcessTest)
                .withRoutesSupplier(this::createRouteBuilders)
                .withUseOverridePropertiesWithPropertiesComponent(useOverridePropertiesWithPropertiesComponent())
                .withRouteFilterExcludePattern(getRouteFilterExcludePattern())
                .withRouteFilterIncludePattern(getRouteFilterIncludePattern())
                .withMockEndpoints(isMockEndpoints())
                .withMockEndpointsAndSkip(isMockEndpointsAndSkip());
    }

    @Override
    public void afterTestExecution(ExtensionContext context) throws Exception {
        watch.taken();
    }

    @Override
    public void beforeTestExecution(ExtensionContext context) throws Exception {
        LOG.trace("Before test execution {}", context.getDisplayName());
        watch.restart();
    }

    @Deprecated(since = "4.7.0")
    public long timeTaken() {
        return watch.taken();
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        if (contextManager == null) {
            LOG.trace("Creating a transient context manager for {}", context.getDisplayName());
            contextManager = new TransientCamelContextManager(testConfigurationBuilder, camelContextConfiguration);
        }

        currentTestName = context.getDisplayName();
        ExtensionContext.Store globalStore = context.getStore(ExtensionContext.Namespace.GLOBAL);
        contextManager.setGlobalStore(globalStore);
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        DefaultCamelContext.clearOptions();
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        final boolean perClassPresent
                = context.getTestInstanceLifecycle().filter(lc -> lc.equals(Lifecycle.PER_CLASS)).isPresent();
        if (perClassPresent) {
            LOG.trace("Creating a legacy context manager for {}", context.getDisplayName());
            testConfigurationBuilder.withCreateCamelContextPerClass(perClassPresent);
            contextManager = new LegacyCamelContextManager(testConfigurationBuilder, camelContextConfiguration);
        }

        ExtensionContext.Store globalStore = context.getStore(ExtensionContext.Namespace.GLOBAL);
        contextManager.setGlobalStore(globalStore);
    }

    @Override
    public void afterAll(ExtensionContext context) {
        contextManager.stop();
    }

    /**
     * Use the RouteBuilder or not
     *
     * @deprecated Use the accessors from {@link #testConfiguration()} method
     * @return     <tt>true</tt> then {@link CamelContext} will be auto started, <tt>false</tt> then
     *             {@link CamelContext} will <b>not</b> be auto started (you will have to start it manually)
     */
    @Deprecated(since = "4.7.0")
    public boolean isUseRouteBuilder() {
        return testConfigurationBuilder.useRouteBuilder();
    }

    @Deprecated(since = "4.7.0")
    public void setUseRouteBuilder(boolean useRouteBuilder) {
        testConfigurationBuilder.withUseRouteBuilder(useRouteBuilder);
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
     * @deprecated Use the accessors from {@link #testConfiguration()} method
     * @return     <tt>true</tt> to write route coverage status in an xml file in the
     *             <tt>target/camel-route-coverage</tt> directory after the test has finished.
     */
    @Deprecated(since = "4.7.0")
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
     * @deprecated Use the accessors from {@link #testConfiguration()} method
     * @return     <tt>true</tt> if you use advice with in your unit tests.
     */
    @Deprecated(since = "4.7.0")
    public boolean isUseAdviceWith() {
        return testConfigurationBuilder.isUseAdviceWith();
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
     * @deprecated Use the accessors from {@link #testConfiguration()} method
     * @return     <tt>true</tt> per class, <tt>false</tt> per test.
     */
    @Deprecated(since = "4.7.0")
    protected final boolean isCreateCamelContextPerClass() {
        return testConfigurationBuilder.isCreateCamelContextPerClass();
    }

    /**
     * Override to enable auto mocking endpoints based on the pattern.
     * <p/>
     * Return <tt>*</tt> to mock all endpoints.
     *
     * @see        EndpointHelper#matchEndpoint(CamelContext, String, String)
     * @deprecated Use the accessors from {@link #camelContextConfiguration()} method
     */
    @Deprecated(since = "4.7.0")
    public String isMockEndpoints() {
        return camelContextConfiguration().mockEndpoints();
    }

    /**
     * Override to enable auto mocking endpoints based on the pattern, and <b>skip</b> sending to original endpoint.
     * <p/>
     * Return <tt>*</tt> to mock all endpoints.
     *
     * @see        EndpointHelper#matchEndpoint(CamelContext, String, String)
     * @deprecated Use the accessors from {@link #camelContextConfiguration()} method
     */
    @Deprecated(since = "4.7.0")
    public String isMockEndpointsAndSkip() {
        return camelContextConfiguration().mockEndpointsAndSkip();
    }

    /**
     * To replace from routes
     *
     * @param      routeId
     * @param      fromEndpoint
     * @deprecated              Use the accessors from {@link #camelContextConfiguration()} method
     */
    @Deprecated(since = "4.7.0")
    public void replaceRouteFromWith(String routeId, String fromEndpoint) {
        camelContextConfiguration.replaceRouteFromWith(routeId, fromEndpoint);
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
    @Deprecated(since = "4.7.0")
    public String getRouteFilterIncludePattern() {
        return camelContextConfiguration.routeFilterIncludePattern();
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
    @Deprecated(since = "4.7.0")
    public String getRouteFilterExcludePattern() {
        return camelContextConfiguration.routeFilterExcludePattern();
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
     *
     * @deprecated Use the accessors from {@link #testConfiguration()} method
     */
    @Deprecated(since = "4.7.0")
    public boolean isUseDebugger() {
        return camelContextConfiguration.useDebugger();
    }

    @Deprecated(since = "4.7.0")
    public Service getCamelContextService() {
        return camelContextConfiguration.camelContextService();
    }

    @Deprecated(since = "4.7.0")
    public Service camelContextService() {
        return camelContextConfiguration.camelContextService();
    }

    /**
     * Gets a reference to the CamelContext. Must not be used during test setup.
     *
     * @return A reference to the CamelContext
     */
    public CamelContext context() {
        return context;
    }

    /**
     * Sets the CamelContext. Used by the manager to override tests that try to access the context during setup. DO NOT
     * USE.
     *
     * @param context
     */
    @Deprecated(since = "4.7.0")
    public void setContext(ModelCamelContext context) {
        this.context = context;
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
        camelContextConfiguration.withCamelContextService(service);
    }

    /**
     * Common test setup. For internal use.
     *
     * @deprecated           Use {@link #setupResources()} instead
     * @throws     Exception if unable to setup the test
     */
    @Deprecated(since = "4.7.0")
    @BeforeEach
    public void setUp() throws Exception {
        testStartHeader(getClass(), currentTestName);

        unsupportedCheck();

        setupResources();
        doPreSetup();

        contextManager.createCamelContext(this);
        context = contextManager.context();

        doPostSetup();

        // only start timing after all the setup
        watch.restart();
    }

    /**
     * Strategy to perform any pre-setup, before the {@link CamelContext} is created. This is for internal Camel usage.
     *
     * @deprecated Use {@link #setupResources()} instead
     */
    @Deprecated(since = "4.7.0")
    protected void doPreSetup() throws Exception {
        // noop
    }

    /**
     * Strategy to perform any post setup after {@link CamelContext} is created. This is for internal Camel usage.
     *
     * @deprecated Use {@link #setupResources()} instead
     */
    @Deprecated(since = "4.7.0")
    protected void doPostSetup() throws Exception {
        // noop
    }

    /**
     * Detects if this is a Spring-Boot test and throws an exception, as these base classes is not intended for testing
     * Camel on Spring Boot. Use ExtensionHelper.hasClassAnnotation instead
     */
    @Deprecated(since = "4.7.0")
    protected void doSpringBootCheck() {
        boolean springBoot
                = ExtensionHelper.hasClassAnnotation(getClass(), "org.springframework.boot.test.context.SpringBootTest");
        if (springBoot) {
            throw new RuntimeException(
                    "Spring Boot detected: The CamelTestSupport/CamelSpringTestSupport class is not intended for Camel testing with Spring Boot.");
        }
    }

    /**
     * Detects if this is a Camel-quarkus test and throw an exception, as these base classes is not intended for testing
     * Camel onQuarkus. Use ExtensionHelper.hasClassAnnotation instead.
     */
    @Deprecated(since = "4.7.0")
    protected void doQuarkusCheck() {
        boolean quarkus = ExtensionHelper.hasClassAnnotation(getClass(), "io.quarkus.test.junit.QuarkusTest",
                "org.apache.camel.quarkus.test.CamelQuarkusTest");
        if (quarkus) {
            throw new RuntimeException(
                    "Quarkus detected: The CamelTestSupport/CamelSpringTestSupport class is not intended for Camel testing with Quarkus.");
        }
    }

    /**
     * Temporary method for the child classes to modify the unsupported check.
     */
    @Deprecated(since = "4.7.0")
    protected void unsupportedCheck() {
        ExtensionHelper.hasUnsupported(getClass());
    }

    @Deprecated(since = "4.7.0")
    protected final void doSetUp() throws Exception {
        throw new UnsupportedOperationException("Do not use the doSetUp method");
    }

    private boolean isRouteCoverageEnabled() {
        return Boolean.parseBoolean(System.getProperty(ROUTE_COVERAGE_ENABLED, "false")) || isDumpRouteCoverage();
    }

    /**
     * Common test tear down. For internal use.
     *
     * @deprecated           Use {@link #cleanupResources()} instead
     * @throws     Exception if unable to setup the test
     */
    @Deprecated(since = "4.7.0")
    @AfterEach
    public void tearDown() throws Exception {
        long time = watch.taken();

        if (isRouteCoverageEnabled()) {
            ExtensionHelper.testEndFooter(getClass(), currentTestName, time, new RouteCoverageDumperExtension(context));
        } else {
            ExtensionHelper.testEndFooter(getClass(), currentTestName, time);
        }

        if (testConfigurationBuilder.isCreateCamelContextPerClass()) {
            // will tear down test specially in afterAll callback
            return;
        }

        LOG.debug("tearDown()");

        contextManager.stop();

        doPostTearDown();
        cleanupResources();

    }

    /**
     * Strategy to perform any post-action, after {@link CamelContext} is stopped. This is meant for internal Camel
     * usage and should not be used by user classes.
     *
     * @deprecated use {@link #cleanupResources()} instead.
     */
    @Deprecated(since = "4.7.0")
    protected void doPostTearDown() throws Exception {
        // noop
    }

    /**
     * Strategy to set up resources, before {@link CamelContext} is created. This is meant to be used by resources that
     * must be available before the context is created. Do not use this as a replacement for tasks that can be handled
     * using JUnit's annotations.
     */
    protected void setupResources() throws Exception {
    }

    /**
     * Strategy to cleanup resources, after {@link CamelContext} is stopped
     */
    protected void cleanupResources() throws Exception {
        // noop
    }

    /**
     * Returns the timeout to use when shutting down (unit in seconds).
     * <p/>
     * Will default use 10 seconds.
     *
     * @deprecated use the accessors from {@link #camelContextConfiguration()}
     * @return     the timeout to use
     */
    @Deprecated(since = "4.7.0")
    protected int getShutdownTimeout() {
        return camelContextConfiguration.shutdownTimeout();
    }

    /**
     * Whether JMX should be used during testing.
     *
     * @deprecated Use the methods {@link #testConfiguration()} to enable, disable or check JMX state.
     * @return     <tt>false</tt> by default.
     */
    @Deprecated(since = "4.7.0")
    protected boolean useJmx() {
        return testConfigurationBuilder.isJmxEnabled();
    }

    /**
     * Override this method to include and override properties with the Camel {@link PropertiesComponent}.
     *
     * @deprecated Use the accessors from {@link #camelContextConfiguration()} method
     * @return     additional properties to add/override.
     */
    @Deprecated(since = "4.7.0")
    protected Properties useOverridePropertiesWithPropertiesComponent() {
        return camelContextConfiguration.useOverridePropertiesWithPropertiesComponent();
    }

    /**
     * Whether to ignore missing locations with the {@link PropertiesComponent}. For example when unit testing you may
     * want to ignore locations that are not available in the environment used for testing.
     *
     * @deprecated Use the accessors from {@link #camelContextConfiguration()} method
     * @return     <tt>true</tt> to ignore, <tt>false</tt> to not ignore, and <tt>null</tt> to leave as configured on
     *             the {@link PropertiesComponent}
     */
    @Deprecated(since = "4.7.0")
    protected Boolean ignoreMissingLocationWithPropertiesComponent() {
        return camelContextConfiguration.ignoreMissingLocationWithPropertiesComponent();
    }

    /**
     * Internal method. Do not use.
     *
     * @deprecated           use {@link #setupResources()} or the JUnit's annotation instead of this method
     * @throws     Exception
     */
    @Deprecated(since = "4.7.0")
    protected void postProcessTest() throws Exception {
        context = contextManager.context();
        template = contextManager.template();
        fluentTemplate = contextManager.fluentTemplate();
        consumer = contextManager.consumer();
    }

    /**
     * Applies the {@link CamelBeanPostProcessor} to this instance.
     * <p>
     * Derived classes using IoC / DI frameworks may wish to turn this into a NoOp such as for CDI we would just use CDI
     * to inject this
     */
    @Deprecated(since = "4.7.0")
    protected void applyCamelPostProcessor() throws Exception {

    }

    /**
     * Does this test class have any of the following annotations on the class-level.
     */
    @Deprecated
    protected boolean hasClassAnnotation(String... names) {
        return ExtensionHelper.hasClassAnnotation(getClass(), names);
    }

    protected void stopCamelContext() throws Exception {
        contextManager.stopCamelContext();

    }

    protected void startCamelContext() throws Exception {
        contextManager.startCamelContext();
    }

    protected CamelContext createCamelContext() throws Exception {
        return CamelContextTestHelper.createCamelContext(createCamelRegistry());
    }

    /**
     * Allows binding custom beans to the Camel {@link Registry}.
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
    @Deprecated(since = "4.7.0")
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
     * @see        #createRouteBuilder()
     * @deprecated This method will be made private. Do not use
     */
    @Deprecated(since = "4.7.0")
    protected RoutesBuilder[] createRouteBuilders() throws Exception {
        return new RoutesBuilder[] { createRouteBuilder() };
    }

    /**
     * Resolves a mandatory endpoint for the given URI or an exception is thrown
     *
     * @param      uri the Camel <a href="">URI</a> to use to create or resolve an endpoint
     * @return         the endpoint
     * @deprecated     Use the methods from {@link TestSupport}
     */
    @Deprecated(since = "4.7.0")
    protected final Endpoint resolveMandatoryEndpoint(String uri) {
        return TestSupport.resolveMandatoryEndpoint(context, uri);
    }

    /**
     * Resolves a mandatory endpoint for the given URI and expected type or an exception is thrown
     *
     * @param      uri the Camel <a href="">URI</a> to use to create or resolve an endpoint
     * @return         the endpoint
     * @deprecated     Use the methods from {@link TestSupport}
     */
    @Deprecated(since = "4.7.0")
    protected final <T extends Endpoint> T resolveMandatoryEndpoint(String uri, Class<T> endpointType) {
        return TestSupport.resolveMandatoryEndpoint(context, uri, endpointType);
    }

    /**
     * Resolves the mandatory Mock endpoint using a URI of the form <code>mock:someName</code>
     *
     * @param  uri the URI which typically starts with "mock:" and has some name
     * @return     the mandatory mock endpoint or an exception is thrown if it could not be resolved
     */
    protected final MockEndpoint getMockEndpoint(String uri) {
        return getMockEndpoint(uri, true);
    }

    /**
     * Resolves the {@link MockEndpoint} using a URI of the form <code>mock:someName</code>, optionally creating it if
     * it does not exist. This implementation will lookup existing mock endpoints and match on the mock queue name, eg
     * mock:foo and mock:foo?retainFirst=5 would match as the queue name is foo.
     *
     * @param  uri                     the URI which typically starts with "mock:" and has some name
     * @param  create                  whether to allow the endpoint to be created if it doesn't exist
     * @return                         the mock endpoint or an {@link NoSuchEndpointException} is thrown if it could not
     *                                 be resolved
     * @throws NoSuchEndpointException is the mock endpoint does not exist
     */
    protected final MockEndpoint getMockEndpoint(String uri, boolean create) throws NoSuchEndpointException {
        // look for existing mock endpoints that have the same queue name, and
        // to
        // do that we need to normalize uri and strip out query parameters and
        // whatnot
        final String normalizedUri = normalizeUri(uri);
        // strip query
        final String target = StringHelper.before(normalizedUri, "?", normalizedUri);

        // lookup endpoints in registry and try to find it
        return CamelContextTestHelper.lookupEndpoint(context, uri, create, target);
    }

    /**
     * Sends a message to the given endpoint URI with the body value
     *
     * @param endpointUri the URI of the endpoint to send to
     * @param body        the body for the message
     */
    protected final void sendBody(String endpointUri, final Object body) {
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
    protected final void sendBody(String endpointUri, final Object body, final Map<String, Object> headers) {
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
    @Deprecated
    protected final void sendBodies(String endpointUri, Object... bodies) {
        for (Object body : bodies) {
            sendBody(endpointUri, body);
        }
    }

    /**
     * Creates an exchange with the given body
     */
    protected final Exchange createExchangeWithBody(Object body) {
        return TestSupport.createExchangeWithBody(context, body);
    }

    /**
     * Asserts that the given language name and expression evaluates to the given value on a specific exchange
     */
    protected final void assertExpression(Exchange exchange, String languageName, String expressionText, Object expectedValue) {
        Language language = assertResolveLanguage(languageName);

        Expression expression = language.createExpression(expressionText);
        assertNotNull(expression, "No Expression could be created for text: " + expressionText + " language: " + language);

        TestSupport.assertExpression(expression, exchange, expectedValue);
    }

    /**
     * Asserts that the given language name and predicate expression evaluates to the expected value on the message
     * exchange
     */
    protected final void assertPredicate(String languageName, String expressionText, Exchange exchange, boolean expected) {
        Language language = assertResolveLanguage(languageName);

        Predicate predicate = language.createPredicate(expressionText);
        assertNotNull(predicate, "No Predicate could be created for text: " + expressionText + " language: " + language);

        TestSupport.assertPredicate(predicate, exchange, expected);
    }

    /**
     * Asserts that the language name can be resolved
     */
    @Deprecated(since = "4.7.0")
    protected final Language assertResolveLanguage(String languageName) {
        Language language = context.resolveLanguage(languageName);
        assertNotNull(language, "Nog language found for name: " + languageName);
        return language;
    }

    /**
     * Asserts the validity of the context
     *
     * @deprecated         Use JUnit's assertions if needed
     * @param      context
     */
    @Deprecated(since = "4.7.0")
    protected final void assertValidContext(CamelContext context) {
        assertNotNull(context, "No context found!");
    }

    protected final <T extends Endpoint> T getMandatoryEndpoint(String uri, Class<T> type) {
        T endpoint = context.getEndpoint(uri, type);
        assertNotNull(endpoint, "No endpoint found for uri: " + uri);
        return endpoint;
    }

    protected final Endpoint getMandatoryEndpoint(String uri) {
        Endpoint endpoint = context.getEndpoint(uri);
        assertNotNull(endpoint, "No endpoint found for uri: " + uri);
        return endpoint;
    }

    /**
     * Disables the JMX agent. Must be called before the {@link #setUp()} method.
     *
     * @deprecated Use the methods {@link #testConfiguration()} to enable, disable or check JMX state.
     */
    @Deprecated(since = "4.7.0")
    protected void disableJMX() {
        testConfigurationBuilder.withDisableJMX();
    }

    /**
     * Enables the JMX agent. Must be called before the {@link #setUp()} method.
     *
     * @deprecated Use the methods {@link #testConfiguration()} to enable, disable or check JMX state.
     */
    @Deprecated(since = "4.7.0")
    protected void enableJMX() {
        testConfigurationBuilder.withEnableJMX();
    }

    /**
     * Single step debugs and Camel invokes this method before entering the given processor. This method is NOOP.
     *
     * @deprecated Use {@link #camelContextConfiguration()} to set an instance of {@link DebugBreakpoint}
     */
    @Deprecated(since = "4.7.0")
    protected void debugBefore(
            Exchange exchange, Processor processor, ProcessorDefinition<?> definition, String id, String label) {
    }

    /**
     * Single step debugs and Camel invokes this method after processing the given processor. This method is NOOP.
     *
     * @deprecated Use {@link #camelContextConfiguration()} to set an instance of {@link DebugBreakpoint}
     */
    @Deprecated(since = "4.7.0")
    protected void debugAfter(
            Exchange exchange, Processor processor, ProcessorDefinition<?> definition, String id, String label,
            long timeTaken) {
    }

    /**
     * Gets the {@link TestExecutionConfiguration} test execution configuration instance for the test
     *
     * @return the configuration instance for the test
     */
    public final TestExecutionConfiguration testConfiguration() {
        return testConfigurationBuilder;
    }

    /**
     * Gets the {@link CamelContextConfiguration} for the test
     *
     * @return the camel context configuration
     */
    public final CamelContextConfiguration camelContextConfiguration() {
        return camelContextConfiguration;
    }
}
