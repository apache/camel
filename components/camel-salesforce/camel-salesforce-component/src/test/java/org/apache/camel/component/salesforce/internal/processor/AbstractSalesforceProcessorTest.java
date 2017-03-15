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
package org.apache.camel.component.salesforce.internal.processor;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.component.salesforce.SalesforceComponent;
import org.apache.camel.component.salesforce.SalesforceEndpoint;
import org.apache.camel.component.salesforce.SalesforceEndpointConfig;
import org.apache.camel.component.salesforce.api.SalesforceException;
import org.apache.camel.component.salesforce.internal.PayloadFormat;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.impl.DefaultMessage;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AbstractSalesforceProcessorTest {

    private static final class NonConvertable {
    }

    private static final class TestSalesforceProcessor extends AbstractSalesforceProcessor {
        private TestSalesforceProcessor(final SalesforceEndpoint endpoint) {
            super(endpoint);
        }

        @Override
        public boolean process(final Exchange exchange, final AsyncCallback callback) {
            return false;
        }

        @Override
        public void start() throws Exception {
        }

        @Override
        public void stop() throws Exception {
        }
    }

    private DefaultMessage message;

    private DefaultExchange exchange;

    public AbstractSalesforceProcessor defaultProcessor() {
        return processorFor(new SalesforceEndpointConfig());
    }

    public AbstractSalesforceProcessor processorFor(final SalesforceEndpointConfig endpointConfig) {
        final SalesforceEndpoint salesforceEndpoint = mock(SalesforceEndpoint.class);
        when(salesforceEndpoint.getConfiguration()).thenReturn(endpointConfig);
        when(salesforceEndpoint.getComponent()).thenReturn(new SalesforceComponent());

        return new TestSalesforceProcessor(salesforceEndpoint);
    }

    @Before
    public void setupExchange() {
        message = new DefaultMessage();

        final CamelContext context = new DefaultCamelContext();

        exchange = new DefaultExchange(context);
        exchange.setIn(message);
    }

    @Test
    public void shouldFetchParametersFromBody() throws SalesforceException {
        message.setBody("object-id");

        final String objectId = defaultProcessor().getParameter(SalesforceEndpointConfig.SOBJECT_ID, exchange,
                                                                true, false);

        assertEquals("Should fetch parameters from message body", "object-id", objectId);
    }

    @Test
    public void shouldFetchPropertyFromEndpointConfiguration() throws SalesforceException {
        final SalesforceEndpointConfig endpointConfig = new SalesforceEndpointConfig();
        endpointConfig.setFormat(PayloadFormat.XML);

        final AbstractSalesforceProcessor processor = processorFor(endpointConfig);

        final String format = processor.getParameter(SalesforceEndpointConfig.FORMAT, exchange, false, false);

        assertEquals("Should fetch properties from endpoint config", "xml", format);
    }

    @Test
    public void shouldFetchPropertyFromEndpointConfigurationAndConvertToProperType()
        throws SalesforceException {
        final SalesforceEndpointConfig endpointConfig = new SalesforceEndpointConfig();
        endpointConfig.setIncludeDetails(true);

        final AbstractSalesforceProcessor processor = processorFor(endpointConfig);

        final String includeDetails = processor.getParameter(SalesforceEndpointConfig.INCLUDE_DETAILS,
                                                             exchange, false, false, String.class);

        assertEquals("Should fetch properties from endpoint config", "true", includeDetails);
    }

    @Test
    public void shouldGetParametersFromHeaders() throws SalesforceException {
        message.setHeader("param", "value");

        final String value = defaultProcessor().getParameter("param", exchange, false, false);

        assertEquals("Value should be fetched", "value", value);
    }

    @Test
    public void shouldResolveProperties() throws SalesforceException {
        message.setHeader("testValue", "value");
        message.setHeader("param", "${header.testValue}");

        final String value = defaultProcessor().getParameter("param", exchange, false, false);

        assertEquals("Value should be resolved", "value", value);
    }

    @Test
    public void shouldReturnNullIfOptionalParameterWasNotFound() throws SalesforceException {
        final String value = defaultProcessor().getParameter(SalesforceEndpointConfig.SOBJECT_ID, exchange,
                                                             false, true);

        assertNull("Optional arguments if not found should resolve to `null`", value);
    }

    @Test
    public void shouldThrowIfParameterCannotBeConverted() throws SalesforceException {
        message.setHeader("param", "value");

        try {
            defaultProcessor().getParameter("param", exchange, false, false, NonConvertable.class);
            fail("IllegalArgumentException was not thrown!");
        } catch (final IllegalArgumentException e) {
            assertEquals("IllegalArgumentException should have descriptive message",
                         "Header param could not be converted to type " + NonConvertable.class.getName(),
                         e.getMessage());
        }
    }

    @Test
    public void shouldThrowIfParameterCannotBeConvertedFromEndpointConfiguration() {
        final SalesforceEndpointConfig endpointConfig = new SalesforceEndpointConfig();
        endpointConfig.setIncludeDetails(true);

        final AbstractSalesforceProcessor processor = processorFor(endpointConfig);

        try {
            processor.getParameter(SalesforceEndpointConfig.INCLUDE_DETAILS, exchange, false, false,
                                   NonConvertable.class);
            fail("SalesforceException was not thrown!");
        } catch (final SalesforceException e) {
            assertTrue("SalesforceException should have NoTypeConversionAvailableException as cause",
                       e.getCause() instanceof NoTypeConversionAvailableException);
        }
    }

    @Test
    public void shouldThrowSalesforceExceptionIfParameterWasNotFound() {
        try {
            defaultProcessor().getParameter(SalesforceEndpointConfig.SOBJECT_ID, exchange, false, false);
            fail("SalesforceException was not thrown!");
        } catch (final SalesforceException e) {
            assertEquals("SalesforceException message should be descriptive", "Missing property sObjectId",
                         e.getMessage());
        }
    }

    @Test
    public void shouldThrowSalesforceExceptionIfParameterWasNotFoundInBody() {
        try {
            defaultProcessor().getParameter(SalesforceEndpointConfig.SOBJECT_ID, exchange, true, false);
            fail("SalesforceException was not thrown!");
        } catch (final SalesforceException e) {
            assertEquals("SalesforceException message should be descriptive",
                         "Missing property sObjectId, message body could not be converted to type java.lang.String",
                         e.getMessage());
        }
    }
}
