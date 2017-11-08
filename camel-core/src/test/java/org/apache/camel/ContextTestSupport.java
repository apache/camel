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
package org.apache.camel;

import java.util.Map;

import javax.naming.Context;

import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.management.JmxSystemPropertyKeys;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.spi.Language;
import org.apache.camel.util.jndi.JndiTest;

/**
 * A useful base class which creates a {@link CamelContext} with some routes
 * along with a {@link ProducerTemplate} for use in the test case
 *
 * @version 
 */
public abstract class ContextTestSupport extends TestSupport {
    
    protected volatile ModelCamelContext context;
    protected volatile ProducerTemplate template;
    protected volatile ConsumerTemplate consumer;
    protected volatile NotifyBuilder oneExchangeDone;
    private boolean useRouteBuilder = true;
    private Service camelContextService;
    
    /**
     * Use the RouteBuilder or not
     * @return 
     *  If the return value is true, the camel context will be started in the setup method.
     *  If the return value is false, the camel context will not be started in the setup method.
     */
    public boolean isUseRouteBuilder() {
        return useRouteBuilder;
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
    public void setCamelContextService(Service camelContextService) {
        this.camelContextService = camelContextService;
    }

    /**
     * Convenient api to create a NotifyBuilder to be notified of a specific event
     */
    protected NotifyBuilder event() {
        return new NotifyBuilder(context);
    }
    
    @Override
    protected void setUp() throws Exception {
        // make SEDA testing faster
        System.setProperty("CamelSedaPollTimeout", "10");

        if (!useJmx()) {
            disableJMX();
        } else {
            enableJMX();
        }
        CamelContext c2 = createCamelContext();
        if (c2 instanceof ModelCamelContext) {
            context = (ModelCamelContext)c2;
        } else {
            throw new Exception("Context must be a ModelCamelContext");
        }
        assertValidContext(context);

        // reduce default shutdown timeout to avoid waiting for 300 seconds
        context.getShutdownStrategy().setTimeout(10);

        template = context.createProducerTemplate();
        template.start();
        consumer = context.createConsumerTemplate();
        consumer.start();

        // create a default notifier when 1 exchange is done which is the most common case
        oneExchangeDone = event().whenDone(1).create();

        if (isUseRouteBuilder()) {
            RouteBuilder[] builders = createRouteBuilders();
            for (RouteBuilder builder : builders) {
                log.debug("Using created route builder: {}", builder);
                context.addRoutes(builder);
            }
            startCamelContext();
        } else {
            log.debug("isUseRouteBuilder() is false");
        }
        
    }

    @Override
    protected void tearDown() throws Exception {
        log.debug("tearDown test: {}", getName());
        if (consumer != null) {
            consumer.stop();
        }
        if (template != null) {
            template.stop();
        }
        stopCamelContext();
        System.clearProperty(JmxSystemPropertyKeys.DISABLED);
        System.clearProperty("CamelSedaPollTimeout");
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
     *
     * @return <tt>false</tt> by default.
     */
    @Deprecated
    protected boolean isLazyLoadingTypeConverter() {
        return false;
    }

    /**
     * Whether to load additional type converters by scanning the classpath.
     * This should only be enabled for tests that uses custom type converters.
     */
    protected boolean isLoadTypeConverters() {
        return false;
    }

    protected void stopCamelContext() throws Exception {
        if (camelContextService != null) {
            camelContextService.stop();
        } else {
            if (context != null) {
                context.stop();
            }    
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

    @SuppressWarnings("deprecation")
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = new DefaultCamelContext(createRegistry());
        context.setLazyLoadTypeConverters(isLazyLoadingTypeConverter());
        context.setLoadTypeConverters(isLoadTypeConverters());
        return context;
    }

    protected JndiRegistry createRegistry() throws Exception {
        return new JndiRegistry(createJndiContext());
    }

    protected Context createJndiContext() throws Exception {
        return JndiTest.createInitialContext();
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
     * {@link RouteBuilder}s to define the routes for testing
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
                in.setHeader("testCase", getName());
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
                in.setHeader("testCase", getName());
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
     * Sets the assert period on all the Mock endpoints
     */
    protected void setAssertPeriod(long period) {
        MockEndpoint.setAssertPeriod(context, period);
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
}
