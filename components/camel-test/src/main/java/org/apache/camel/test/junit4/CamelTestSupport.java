/**
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
package org.apache.camel.test.junit4;

import java.io.InputStream;
import java.util.Hashtable;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.naming.Context;
import javax.naming.InitialContext;

import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Message;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.Service;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.BreakpointSupport;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultDebugger;
import org.apache.camel.impl.InterceptSendToMockEndpointStrategy;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.management.JmxSystemPropertyKeys;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.spi.Language;
import org.apache.camel.spring.CamelBeanPostProcessor;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A useful base class which creates a {@link org.apache.camel.CamelContext} with some routes
 * along with a {@link org.apache.camel.ProducerTemplate} for use in the test case
 *
 * @version 
 */
public abstract class CamelTestSupport extends TestSupport {    
    
    protected static volatile CamelContext context;
    protected static volatile ProducerTemplate template;
    protected static volatile ConsumerTemplate consumer;
    protected static volatile Service camelContextService;
    private static final Logger LOG = LoggerFactory.getLogger(TestSupport.class);
    private static final AtomicBoolean INIT = new AtomicBoolean();
    private boolean useRouteBuilder = true;
    private final DebugBreakpoint breakpoint = new DebugBreakpoint();

    /**
     * Use the RouteBuilder or not
     * @return <tt>true</tt> then {@link CamelContext} will be auto started,
     *        <tt>false</tt> then {@link CamelContext} will <b>not</b> be auto started (you will have to start it manually)
     */
    public boolean isUseRouteBuilder() {
        return useRouteBuilder;
    }

    /**
     * Override to control whether {@link CamelContext} should be setup per test or per class.
     * <p/>
     * By default it will be setup/teardown per test (per test method). If you want to re-use
     * {@link CamelContext} between test methods you can override this method and return <tt>true</tt>
     * <p/>
     * <b>Important:</b> Use this with care as the {@link CamelContext} will carry over state
     * from previous tests, such as endpoints, components etc. So you cannot use this in all your tests.
     *
     * @return <tt>true</tt> per class, <tt>false</tt> per test.
     */
    public boolean isCreateCamelContextPerClass() {
        return false;
    }

    /**
     * Override to enable auto mocking endpoints based on the pattern.
     * <p/>
     * Return <tt>*</tt> to mock all endpoints.
     *
     * @see org.apache.camel.util.EndpointHelper#matchEndpoint(String, String)
     */
    public String isMockEndpoints() {
        return null;
    }

    public void setUseRouteBuilder(boolean useRouteBuilder) {
        this.useRouteBuilder = useRouteBuilder;
    }

    public Service getCamelContextService() {
        return camelContextService;
    }

    /**
     * Allows a service to be registered a separate lifecycle service to start
     * and stop the context; such as for Spring when the ApplicationContext is
     * started and stopped, rather than directly stopping the CamelContext
     */
    public void setCamelContextService(Service service) {
        camelContextService = service;
    }

    @Before
    public void setUp() throws Exception {
        log.info("********************************************************************************");
        log.info("Testing: " + getTestMethodName() + "(" + getClass().getName() + ")");
        log.info("********************************************************************************");

        boolean first = INIT.compareAndSet(false, true);
        if (isCreateCamelContextPerClass()) {
            // test is per class, so only setup once (the first time)
            if (first) {
                doSetUp();
            } else {
                // and in between tests we must do IoC and reset mocks
                postProcessTest();
                resetMocks();
            }
        } else {
            // test is per test so always setup
            doSetUp();
        }
    }

    protected void doSetUp() throws Exception {
        log.debug("setUp test");
        if (!useJmx()) {
            disableJMX();
        } else {
            enableJMX();
        }

        context = createCamelContext();
        assertNotNull("No context found!", context);

        // reduce default shutdown timeout to avoid waiting for 300 seconds
        context.getShutdownStrategy().setTimeout(getShutdownTimeout());

        // set debugger
        context.setDebugger(new DefaultDebugger());
        context.getDebugger().addBreakpoint(breakpoint);
        // note: when stopping CamelContext it will automatic remove the breakpoint

        template = context.createProducerTemplate();
        template.start();
        consumer = context.createConsumerTemplate();
        consumer.start();

        // enable auto mocking if enabled
        String pattern = isMockEndpoints();
        if (pattern != null) {
            context.addRegisterEndpointCallback(new InterceptSendToMockEndpointStrategy(pattern));
        }

        postProcessTest();
        
        if (isUseRouteBuilder()) {
            RouteBuilder[] builders = createRouteBuilders();
            for (RouteBuilder builder : builders) {
                log.debug("Using created route builder: " + builder);
                context.addRoutes(builder);
            }
            if (!"true".equalsIgnoreCase(System.getProperty("skipStartingCamelContext"))) {
                startCamelContext();
            } else {
                log.info("Skipping starting CamelContext as system property skipStartingCamelContext is set to be true.");
            }
        } else {
            log.debug("Using route builder from the created context: " + context);
        }
        log.debug("Routing Rules are: " + context.getRoutes());

        assertValidContext(context);

        INIT.set(true);
    }

    @After
    public void tearDown() throws Exception {
        log.info("********************************************************************************");
        log.info("Testing done: " + getTestMethodName() + "(" + getClass().getName() + ")");
        log.info("********************************************************************************");

        if (isCreateCamelContextPerClass()) {
            // we tear down in after class
            return;
        }

        LOG.debug("tearDown test");
        doStopTemplates();
        stopCamelContext();
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        INIT.set(false);
        LOG.debug("tearDownAfterClass test");
        doStopTemplates();
        doStopCamelContext();
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
     * Whether or not type converters should be lazy loaded (notice core converters is always loaded)
     * <p/>
     * We enabled lazy by default as it would speedup unit testing.
     *
     * @return <tt>true</tt> by default.
     */
    protected boolean isLazyLoadingTypeConverter() {
        return true;
    }

    /**
     * Lets post process this test instance to process any Camel annotations.
     * Note that using Spring Test or Guice is a more powerful approach.
     */
    protected void postProcessTest() throws Exception {
        CamelBeanPostProcessor processor = new CamelBeanPostProcessor();
        processor.setCamelContext(context);
        processor.postProcessBeforeInitialization(this, "this");
    }

    protected void stopCamelContext() throws Exception {
        doStopCamelContext();
    }

    private static void doStopCamelContext() throws Exception {
        if (camelContextService != null) {
            camelContextService.stop();
            camelContextService = null;
        } else {
            if (context != null) {
                context.stop();
                context = null;
            }
        }
    }

    private static void doStopTemplates() throws Exception {
        if (consumer != null) {
            consumer.stop();
            consumer = null;
        }
        if (template != null) {
            template.stop();
            template = null;
        }
    }

    protected void startCamelContext() throws Exception {
        if (camelContextService != null) {
            camelContextService.start();
        } else {
            if (context instanceof DefaultCamelContext) {
                DefaultCamelContext defaultCamelContext = (DefaultCamelContext)context;
                if (!defaultCamelContext.isStarted()) {
                    defaultCamelContext.start();
                }
            } else {
                context.start();
            }
        }
    }

    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = new DefaultCamelContext(createRegistry());
        context.setLazyLoadTypeConverters(isLazyLoadingTypeConverter());
        return context;
    }

    protected JndiRegistry createRegistry() throws Exception {
        return new JndiRegistry(createJndiContext());
    }

    @SuppressWarnings("unchecked")
    protected Context createJndiContext() throws Exception {
        Properties properties = new Properties();

        // jndi.properties is optional
        InputStream in = getClass().getClassLoader().getResourceAsStream("jndi.properties");
        if (in != null) {
            log.debug("Using jndi.properties from classpath root");
            properties.load(in);
        } else {            
            properties.put("java.naming.factory.initial", "org.apache.camel.util.jndi.CamelInitialContextFactory");
        }
        return new InitialContext(new Hashtable(properties));
    }

    /**
     * Factory method which derived classes can use to create a {@link RouteBuilder}
     * to define the routes for testing
     */
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                // no routes added by default
            }
        };
    }

    /**
     * Factory method which derived classes can use to create an array of
     * {@link org.apache.camel.builder.RouteBuilder}s to define the routes for testing
     *
     * @see #createRouteBuilder()
     */
    protected RouteBuilder[] createRouteBuilders() throws Exception {
        return new RouteBuilder[] {createRouteBuilder()};
    }

    /**
     * Resolves a mandatory endpoint for the given URI or an exception is thrown
     *
     * @param uri the Camel <a href="">URI</a> to use to create or resolve an endpoint
     * @return the endpoint
     */
    protected Endpoint resolveMandatoryEndpoint(String uri) {
        return resolveMandatoryEndpoint(context, uri);
    }

    /**
     * Resolves a mandatory endpoint for the given URI and expected type or an exception is thrown
     *
     * @param uri the Camel <a href="">URI</a> to use to create or resolve an endpoint
     * @return the endpoint
     */
    protected <T extends Endpoint> T resolveMandatoryEndpoint(String uri, Class<T> endpointType) {
        return resolveMandatoryEndpoint(context, uri, endpointType);
    }

    /**
     * Resolves the mandatory Mock endpoint using a URI of the form <code>mock:someName</code>
     *
     * @param uri the URI which typically starts with "mock:" and has some name
     * @return the mandatory mock endpoint or an exception is thrown if it could not be resolved
     */
    protected MockEndpoint getMockEndpoint(String uri) {
        return resolveMandatoryEndpoint(uri, MockEndpoint.class);
    }

    /**
     * Sends a message to the given endpoint URI with the body value
     *
     * @param endpointUri the URI of the endpoint to send to
     * @param body        the body for the message
     */
    protected void sendBody(String endpointUri, final Object body) {
        template.send(endpointUri, new Processor() {
            public void process(Exchange exchange) {
                Message in = exchange.getIn();
                in.setBody(body);                
            }
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
        template.send(endpointUri, new Processor() {
            public void process(Exchange exchange) {
                Message in = exchange.getIn();
                in.setBody(body);                
                for (Map.Entry<String, Object> entry : headers.entrySet()) {
                    in.setHeader(entry.getKey(), entry.getValue());
                }
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
        return createExchangeWithBody(context, body);
    }

    /**
     * Asserts that the given language name and expression evaluates to the
     * given value on a specific exchange
     */
    protected void assertExpression(Exchange exchange, String languageName, String expressionText, Object expectedValue) {
        Language language = assertResolveLanguage(languageName);

        Expression expression = language.createExpression(expressionText);
        assertNotNull("No Expression could be created for text: " + expressionText + " language: " + language, expression);

        assertExpression(expression, exchange, expectedValue);
    }

    /**
     * Asserts that the given language name and predicate expression evaluates
     * to the expected value on the message exchange
     */
    protected void assertPredicate(String languageName, String expressionText, Exchange exchange, boolean expected) {
        Language language = assertResolveLanguage(languageName);

        Predicate predicate = language.createPredicate(expressionText);
        assertNotNull("No Predicate could be created for text: " + expressionText + " language: " + language, predicate);

        assertPredicate(predicate, exchange, expected);
    }

    /**
     * Asserts that the language name can be resolved
     */
    protected Language assertResolveLanguage(String languageName) {
        Language language = context.resolveLanguage(languageName);
        assertNotNull("No language found for name: " + languageName, language);
        return language;
    }

    /**
     * Asserts that all the expectations of the Mock endpoints are valid
     */
    protected void assertMockEndpointsSatisfied() throws InterruptedException {
        MockEndpoint.assertIsSatisfied(context);
    }

    /**
     * Asserts that all the expectations of the Mock endpoints are valid
     */
    protected void assertMockEndpointsSatisfied(long timeout, TimeUnit unit) throws InterruptedException {
        MockEndpoint.assertIsSatisfied(context, timeout, unit);
    }

    /**
     * Reset all Mock endpoints.
     */
    protected void resetMocks() {
        MockEndpoint.resetMocks(context);
    }

    protected void assertValidContext(CamelContext context) {
        assertNotNull("No context found!", context);
    }

    protected <T extends Endpoint> T getMandatoryEndpoint(String uri, Class<T> type) {
        T endpoint = context.getEndpoint(uri, type);
        assertNotNull("No endpoint found for uri: " + uri, endpoint);
        return endpoint;
    }

    protected Endpoint getMandatoryEndpoint(String uri) {
        Endpoint endpoint = context.getEndpoint(uri);
        assertNotNull("No endpoint found for uri: " + uri, endpoint);
        return endpoint;
    }

    /**
     * Disables the JMX agent. Must be called before the {@link #setUp()} method.
     */
    protected void disableJMX() {
        System.setProperty(JmxSystemPropertyKeys.DISABLED, "true");
    }

    /**
     * Enables the JMX agent. Must be called before the {@link #setUp()} method.
     */
    protected void enableJMX() {
        System.setProperty(JmxSystemPropertyKeys.DISABLED, "false");
    }

    /**
     * Single step debugs and Camel invokes this method before entering the given processor
     */
    protected void debugBefore(Exchange exchange, Processor processor, ProcessorDefinition definition,
                               String id, String label) {
    }

    /**
     * Single step debugs and Camel invokes this method after processing the given processor
     */
    protected void debugAfter(Exchange exchange, Processor processor, ProcessorDefinition definition,
                              String id, String label, long timeTaken) {
    }

    /**
     * To easily debug by overriding the <tt>debugBefore</tt> and <tt>debugAfter</tt> methods.
     */
    private class DebugBreakpoint extends BreakpointSupport {

        @Override
        public void beforeProcess(Exchange exchange, Processor processor, ProcessorDefinition definition) {
            CamelTestSupport.this.debugBefore(exchange, processor, definition, definition.getId(), definition.getLabel());
        }

        @Override
        public void afterProcess(Exchange exchange, Processor processor, ProcessorDefinition definition, long timeTaken) {
            CamelTestSupport.this.debugAfter(exchange, processor, definition, definition.getId(), definition.getLabel(), timeTaken);
        }
    }

}