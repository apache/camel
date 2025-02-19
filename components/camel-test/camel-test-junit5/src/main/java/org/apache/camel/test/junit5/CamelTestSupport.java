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

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.NoSuchEndpointException;
import org.apache.camel.Processor;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.LanguageBuilderFactory;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.spi.CamelBeanPostProcessor;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.junit5.util.CamelContextTestHelper;
import org.apache.camel.test.junit5.util.ExtensionHelper;
import org.apache.camel.util.StopWatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * A useful base class which creates a {@link org.apache.camel.CamelContext} with some routes along with a
 * {@link org.apache.camel.ProducerTemplate} for use in the test case Do <tt>not</tt> use this class for Spring Boot
 * testing.
 */
public abstract class CamelTestSupport extends AbstractTestSupport
        implements BeforeTestExecutionCallback, AfterTestExecutionCallback {
    private static final Logger LOG = LoggerFactory.getLogger(CamelTestSupport.class);

    @RegisterExtension
    @Order(10)
    protected TestLoggerExtension testLoggerExtension = new TestLoggerExtension();

    @RegisterExtension
    protected CamelTestSupport camelTestSupportExtension = this;
    private final StopWatch watch = new StopWatch();

    @RegisterExtension
    @Order(1)
    public final ContextManagerExtension contextManagerExtension;
    private CamelContextManager contextManager;

    protected CamelTestSupport() {
        super(new TestExecutionConfiguration(), new CamelContextConfiguration());

        configureTest(testConfigurationBuilder);
        configureContext(camelContextConfiguration);
        contextManagerExtension = new ContextManagerExtension(testConfigurationBuilder, camelContextConfiguration);
    }

    @Override
    public void configureContext(CamelContextConfiguration camelContextConfiguration) {
        camelContextConfiguration
                .withCamelContextSupplier(this::createCamelContext)
                .withRegistryBinder(this::bindToRegistry)
                .withPostProcessor(this::postProcessTest)
                .withRoutesSupplier(this::createRouteBuilders)
                .withUseOverridePropertiesWithPropertiesComponent(useOverridePropertiesWithPropertiesComponent())
                .withRouteFilterExcludePattern(getRouteFilterExcludePattern())
                .withRouteFilterIncludePattern(getRouteFilterIncludePattern())
                .withMockEndpoints(isMockEndpoints())
                .withMockEndpointsAndSkip(isMockEndpointsAndSkip())
                .withAutoStartupExcludePatterns(isAutoStartupExcludePatterns())
                .withStubEndpoints(isStubEndpoints());
    }

    @Override
    public void configureTest(TestExecutionConfiguration testExecutionConfiguration) {
        boolean coverage = CamelContextTestHelper.isRouteCoverageEnabled(isDumpRouteCoverage());
        String dump = CamelContextTestHelper.getRouteDump(getDumpRoute());
        boolean jmx = coverage || useJmx(); // route coverage requires JMX

        testExecutionConfiguration.withJMX(jmx)
                .withUseRouteBuilder(isUseRouteBuilder())
                .withUseAdviceWith(isUseAdviceWith())
                .withDumpRouteCoverage(coverage)
                .withDumpRoute(dump);
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

    /**
     * Gets the name of the current test being executed.
     *
     * @deprecated Use JUnit's TestInfo class or the {@link TestNameExtension}
     */
    @Deprecated(since = "4.7.0")
    public final String getCurrentTestName() {
        return contextManagerExtension.getCurrentTestName();
    }

    /**
     * Common test setup. For internal use.
     *
     * @deprecated           Use {@link #setupResources()} instead
     * @throws     Exception if unable to setup the test
     */
    @Deprecated(since = "4.7.0")
    @BeforeEach
    public final void setUp() throws Exception {
        unsupportedCheck();

        setupResources();
        doPreSetup();

        contextManager = contextManagerExtension.getContextManager();
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
     * Strategy to perform any post-setup after {@link CamelContext} is created. This is for internal Camel usage.
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

    /**
     * Common test tear down. For internal use.
     *
     * @deprecated           Use {@link #cleanupResources()} instead
     * @throws     Exception if unable to setup the test
     */
    @Deprecated(since = "4.7.0")
    @AfterEach
    public final void tearDown(TestInfo testInfo) throws Exception {
        long time = watch.taken();
        LOG.debug("tearDown()");

        if (contextManager != null) {
            contextManager.dumpRouteCoverage(getClass(), testInfo.getDisplayName(), time);
            String dump = CamelContextTestHelper.getRouteDump(getDumpRoute());
            contextManager.dumpRoute(getClass(), testInfo.getDisplayName(), dump);
        } else {
            LOG.warn(
                    "A context manager is required to dump the route coverage for the Camel context but it's not available (it's null). "
                     + "It's likely that the test is misconfigured!");
        }

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

    @Deprecated(since = "4.7.0")
    protected void stopCamelContext() throws Exception {
        contextManager.stopCamelContext();
    }

    @Deprecated(since = "4.7.0")
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
     * A utility method allowing to build any language using a fluent syntax as shown in the next example:
     *
     * <pre>
     *  var exp = expression().tokenize().token("\n").end()
     * </pre>
     *
     * @return an entry point to the builder of all supported languages.
     */
    protected LanguageBuilderFactory expression() {
        return new LanguageBuilderFactory();
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
    @Deprecated(since = "4.7.0")
    protected final MockEndpoint getMockEndpoint(String uri, boolean create) throws NoSuchEndpointException {
        return TestSupport.getMockEndpoint(context, uri, create);
    }

    /**
     * Sends a message to the given endpoint URI with the body value
     *
     * @param endpointUri the URI of the endpoint to send to
     * @param body        the body for the message
     */
    @Deprecated(since = "4.7.0")
    protected final void sendBody(String endpointUri, final Object body) {
        TestSupport.sendBody(template, endpointUri, body);
    }

    /**
     * Sends a message to the given endpoint URI with the body value and specified headers
     *
     * @param endpointUri the URI of the endpoint to send to
     * @param body        the body for the message
     * @param headers     any headers to set on the message
     */
    @Deprecated(since = "4.7.0")
    protected final void sendBody(String endpointUri, final Object body, final Map<String, Object> headers) {
        TestSupport.sendBody(template, endpointUri, body, headers);
    }

    /**
     * Sends messages to the given endpoint for each of the specified bodies
     *
     * @param endpointUri the endpoint URI to send to
     * @param bodies      the bodies to send, one per message
     */
    @Deprecated(since = "4.7.0")
    protected final void sendBodies(String endpointUri, Object... bodies) {
        TestSupport.sendBodies(template, endpointUri, bodies);
    }

    /**
     * Creates an exchange with the given body
     */
    @Deprecated(since = "4.7.0")
    protected final Exchange createExchangeWithBody(Object body) {
        return TestSupport.createExchangeWithBody(context, body);
    }

    /**
     * Asserts that the given language name and expression evaluates to the given value on a specific exchange
     */
    @Deprecated(since = "4.7.0")
    protected final void assertExpression(Exchange exchange, String languageName, String expressionText, Object expectedValue) {
        TestSupport.assertExpression(context, exchange, languageName, expressionText, expectedValue);
    }

    /**
     * Asserts that the given language name and predicate expression evaluates to the expected value on the message
     * exchange
     */
    @Deprecated(since = "4.7.0")
    protected final void assertPredicate(String languageName, String expressionText, Exchange exchange, boolean expected) {
        TestSupport.assertPredicate(context, languageName, expressionText, exchange, expected);
    }

    /**
     * Asserts that the language name can be resolved
     */
    @Deprecated(since = "4.7.0")
    protected final Language assertResolveLanguage(String languageName) {
        return TestSupport.assertResolveLanguage(context, languageName);
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
        return TestSupport.getMandatoryEndpoint(context(), uri, type);
    }

    protected final Endpoint getMandatoryEndpoint(String uri) {
        return TestSupport.getMandatoryEndpoint(context(), uri);
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
}
