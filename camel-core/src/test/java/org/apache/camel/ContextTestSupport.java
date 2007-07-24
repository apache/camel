/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.Language;

/**
 * A useful base class which creates a {@link CamelContext} with some routes along with a {@link CamelTemplate}
 * for use in the test case
 *
 * @version $Revision: 1.1 $
 */
public abstract class ContextTestSupport extends TestSupport {
    protected CamelContext context;
    protected CamelTemplate<Exchange> template;
    private boolean useRouteBuilder = true;
    private Service camelContextService;

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
     * Allows a service to be registered a separate lifecycle service to start and stop
     * the context; such as for Spring
     * when the ApplicationContext is started and stopped, rather than directly stopping the
     * CamelContext
     */
    public void setCamelContextService(Service camelContextService) {
        this.camelContextService = camelContextService;
    }

    @Override
    protected void setUp() throws Exception {
        context = createCamelContext();
        template = new CamelTemplate<Exchange>(context);

        if (useRouteBuilder) {
            RouteBuilder builder = createRouteBuilder();
            log.debug("Using created route builder: " + builder);
            context.addRoutes(builder);
        }
        else {
            log.debug("Using route builder from the created context: " + context);
        }

        if (camelContextService != null) {
            camelContextService.start();
        }
        else {
            context.start();
        }

        log.debug("Routing Rules are: " + context.getRoutes());
    }

    @Override
    protected void tearDown() throws Exception {
        log.debug("tearDown test: " + getName());
        template.stop();
        if (camelContextService != null) {
            camelContextService.stop();
        }
        else {
            context.stop();
        }
    }

    protected CamelContext createCamelContext() throws Exception {
        return new DefaultCamelContext();
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                // no routes added by default
            }
        };
    }

    protected Endpoint resolveMandatoryEndpoint(String uri) {
        return resolveMandatoryEndpoint(context, uri);
    }

    protected <T extends Endpoint> T resolveMandatoryEndpoint(String uri, Class<T> endpointType) {
        return resolveMandatoryEndpoint(context, uri, endpointType);
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
     * Asserts that the given language name and expression evaluates to the given value on a specific exchange
     */
    protected void assertExpression(Exchange exchange, String languageName, String expressionText, Object expectedValue) {
        Language language = assertResolveLanguage(languageName);

        Expression<Exchange> expression = language.createExpression(expressionText);
        assertNotNull("No Expression could be created for text: " + expressionText
                + " language: " + language, expression);

        assertExpression(expression, exchange, expectedValue);
    }


    /**
     * Asserts that the given language name and predicate expression evaluates to the expected value on the message exchange
     */
    protected void assertPredicate(String languageName, String expressionText, Exchange exchange, boolean expected) {
        Language language = assertResolveLanguage(languageName);

        Predicate<Exchange> predicate = language.createPredicate(expressionText);
        assertNotNull("No Predicate could be created for text: " + expressionText
                + " language: " + language, predicate);

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
}
