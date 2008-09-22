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

import java.io.File;
import java.util.List;

import junit.framework.TestCase;
import org.apache.camel.builder.Builder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.ValueBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.processor.DelegateAsyncProcessor;
import org.apache.camel.processor.DelegateProcessor;
import org.apache.camel.util.ExchangeHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * A bunch of useful testing methods
 *
 * @version $Revision$
 */
public abstract class TestSupport extends TestCase {

    protected transient Log log = LogFactory.getLog(getClass());

    // Builder methods for expressions used when testing
    // -------------------------------------------------------------------------

    /**
     * Returns a value builder for the given header
     */
    public <E extends Exchange> ValueBuilder<E> header(String name) {
        return Builder.header(name);
    }

    /**
     * Returns a predicate and value builder for the inbound body on an exchange
     */
    public ValueBuilder body() {
        return Builder.body();
    }

    /**
     * Returns a predicate and value builder for the inbound message body as a
     * specific type
     */
    public <T> ValueBuilder bodyAs(Class<T> type) {
        return Builder.bodyAs(type);
    }

    /**
     * Returns a predicate and value builder for the outbound body on an
     * exchange
     */
    public ValueBuilder outBody() {
        return Builder.outBody();
    }

    /**
     * Returns a predicate and value builder for the outbound message body as a
     * specific type
     */
    public <T> ValueBuilder outBodyAs(Class<T> type) {
        return Builder.outBodyAs(type);
    }

    /**
     * Returns a predicate and value builder for the fault body on an
     * exchange
     */
    public ValueBuilder faultBody() {
        return Builder.faultBody();
    }

    /**
     * Returns a predicate and value builder for the fault message body as a
     * specific type
     */
    public <T> ValueBuilder faultBodyAs(Class<T> type) {
        return Builder.faultBodyAs(type);
    }

    /**
     * Returns a value builder for the given system property
     */
    public ValueBuilder systemProperty(String name) {
        return Builder.systemProperty(name);
    }

    /**
     * Returns a value builder for the given system property
     */
    public ValueBuilder systemProperty(String name, String defaultValue) {
        return Builder.systemProperty(name, defaultValue);
    }

    // Assertions
    // -----------------------------------------------------------------------

    protected <T> T assertIsInstanceOf(Class<T> expectedType, Object value) {
        assertNotNull("Expected an instance of type: " + expectedType.getName() + " but was null", value);
        assertTrue("object should be a " + expectedType.getName() + " but was: " + value + " with type: "
                   + value.getClass().getName(), expectedType.isInstance(value));
        return expectedType.cast(value);
    }

    protected void assertEndpointUri(Endpoint<Exchange> endpoint, String uri) {
        assertNotNull("Endpoint is null when expecting endpoint for: " + uri, endpoint);
        assertEquals("Endoint uri for: " + endpoint, uri, endpoint.getEndpointUri());
    }

    /**
     * Asserts the In message on the exchange contains the expected value
     */
    protected Object assertInMessageHeader(Exchange exchange, String name, Object expected) {
        return assertMessageHeader(exchange.getIn(), name, expected);
    }

    /**
     * Asserts the Out message on the exchange contains the expected value
     */
    protected Object assertOutMessageHeader(Exchange exchange, String name, Object expected) {
        return assertMessageHeader(exchange.getOut(), name, expected);
    }

    /**
     * Asserts that the given exchange has an OUT message of the given body value
     *
     * @param exchange the exchange which should have an OUT message
     * @param expected the expected value of the OUT message
     * @throws InvalidPayloadException is thrown if the payload is not the expected class type
     */
    protected void assertInMessageBodyEquals(Exchange exchange, Object expected) throws InvalidPayloadException {
        assertNotNull("Should have a response exchange!", exchange);

        Object actual;
        if (expected == null) {
            actual = ExchangeHelper.getMandatoryInBody(exchange);
            assertEquals("in body of: " + exchange, expected, actual);
        } else {
            actual = ExchangeHelper.getMandatoryInBody(exchange, expected.getClass());
        }
        assertEquals("in body of: " + exchange, expected, actual);

        log.debug("Received response: " + exchange + " with in: " + exchange.getIn());
    }

    /**
     * Asserts that the given exchange has an OUT message of the given body value
     *
     * @param exchange the exchange which should have an OUT message
     * @param expected the expected value of the OUT message
     * @throws InvalidPayloadException is thrown if the payload is not the expected class type
     */
    protected void assertOutMessageBodyEquals(Exchange exchange, Object expected) throws InvalidPayloadException {
        assertNotNull("Should have a response exchange!", exchange);

        Object actual;
        if (expected == null) {
            actual = ExchangeHelper.getMandatoryOutBody(exchange);
            assertEquals("output body of: " + exchange, expected, actual);
        } else {
            actual = ExchangeHelper.getMandatoryOutBody(exchange, expected.getClass());
        }
        assertEquals("output body of: " + exchange, expected, actual);

        log.debug("Received response: " + exchange + " with out: " + exchange.getOut());
    }

    protected Object assertMessageHeader(Message message, String name, Object expected) {
        Object value = message.getHeader(name);
        assertEquals("Header: " + name + " on Message: " + message, expected, value);
        return value;
    }

    /**
     * Asserts that the given expression when evaluated returns the given answer
     */
    protected Object assertExpression(Expression expression, Exchange exchange, Object expected) {
        Object value = expression.evaluate(exchange);

        // lets try convert to the type of the expected
        if (expected != null) {
            value = ExchangeHelper.convertToType(exchange, expected.getClass(), value);
        }

        log.debug("Evaluated expression: " + expression + " on exchange: " + exchange + " result: " + value);

        assertEquals("Expression: " + expression + " on Exchange: " + exchange, expected, value);
        return value;
    }

    /**
     * Asserts that the predicate returns the expected value on the exchange
     */
    protected void assertPredicateMatches(Predicate predicate, Exchange exchange) {
        assertPredicate(predicate, exchange, true);
    }

    /**
     * Asserts that the predicate returns the expected value on the exchange
     */
    protected void assertPredicateDoesNotMatch(Predicate predicate, Exchange exchange) {
        try {
            predicate.assertMatches("Predicate should match", exchange);
        } catch (AssertionError e) {
            log.debug("Caught expected assertion error: " + e);
        }
        assertPredicate(predicate, exchange, false);
    }

    /**
     * Asserts that the predicate returns the expected value on the exchange
     */
    protected boolean assertPredicate(Predicate predicate, Exchange exchange, boolean expected) {
        if (expected) {
            predicate.assertMatches("Predicate failed", exchange);
        }
        boolean value = predicate.matches(exchange);

        log.debug("Evaluated predicate: " + predicate + " on exchange: " + exchange + " result: " + value);

        assertEquals("Predicate: " + predicate + " on Exchange: " + exchange, expected, value);
        return value;
    }

    /**
     * Resolves an endpoint and asserts that it is found
     */
    protected Endpoint resolveMandatoryEndpoint(CamelContext context, String uri) {
        Endpoint endpoint = context.getEndpoint(uri);

        assertNotNull("No endpoint found for URI: " + uri, endpoint);

        return endpoint;
    }

    /**
     * Resolves an endpoint and asserts that it is found
     */
    protected <T extends Endpoint> T resolveMandatoryEndpoint(CamelContext context, String uri,
                                                              Class<T> endpointType) {
        T endpoint = context.getEndpoint(uri, endpointType);

        assertNotNull("No endpoint found for URI: " + uri, endpoint);

        return endpoint;
    }

    /**
     * Creates an exchange with the given body
     */
    protected Exchange createExchangeWithBody(CamelContext camelContext, Object body) {
        Exchange exchange = new DefaultExchange(camelContext);
        Message message = exchange.getIn();
        message.setHeader("testName", getName());
        message.setHeader("testClass", getClass().getName());
        message.setBody(body);
        return exchange;
    }

    protected <T> T assertOneElement(List<T> list) {
        assertEquals("Size of list should be 1: " + list, 1, list.size());
        return list.get(0);
    }

    /**
     * Asserts that a list is of the given size
     */
    protected <T> List<T> assertListSize(List<T> list, int size) {
        assertEquals("List should be of size: " + size + " but is: " + list, size, list.size());
        return list;
    }

    /**
     * A helper method to create a list of Route objects for a given route builder
     */
    protected List<Route> getRouteList(RouteBuilder builder) throws Exception {
        CamelContext context = new DefaultCamelContext();
        context.addRoutes(builder);
        context.start();
        List<Route> answer = context.getRoutes();
        context.stop();
        return answer;
    }

    /**
     * Asserts that the text contains the given string
     *
     * @param text the text to compare
     * @param containedText the text which must be contained inside the other text parameter
     */
    protected void assertStringContains(String text, String containedText) {
        assertNotNull("Text should not be null!", text);
        assertTrue("Text: " + text + " does not contain: " + containedText, text.contains(containedText));
    }

    /**
     * If a processor is wrapped with a bunch of DelegateProcessor or DelegateAsyncProcessor objects
     * this call will drill through them and return the wrapped Processor.
     */
    protected Processor unwrap(Processor processor) {
        while (true) {
            if (processor instanceof DelegateAsyncProcessor) {
                processor = ((DelegateAsyncProcessor)processor).getProcessor();
            } else if (processor instanceof DelegateProcessor) {
                processor = ((DelegateProcessor)processor).getProcessor();
            } else {
                return processor;
            }
        }
    }

    /**
     * Recursively delete a directory, useful to zapping test data
     *
     * @param file the directory to be deleted
     */
    public static void deleteDirectory(String file) {
        deleteDirectory(new File(file));
    }

    /**
     * Recursively delete a directory, useful to zapping test data
     *
     * @param file the directory to be deleted
     */
    public static void deleteDirectory(File file) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            for (int i = 0; i < files.length; i++) {
                deleteDirectory(files[i]);
            }
        }
        file.delete();
    }

    /**
     * create the directory
     *
     * @param file the directory to be created
     */
    public static void createDirectory(String file) {
        File dir = new File(file);
        dir.mkdirs();
    }
}
