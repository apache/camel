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
package org.apache.camel.component.salesforce.internal.processor;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.component.salesforce.SalesforceComponent;
import org.apache.camel.component.salesforce.SalesforceEndpoint;
import org.apache.camel.component.salesforce.SalesforceEndpointConfig;
import org.apache.camel.component.salesforce.api.SalesforceException;
import org.apache.camel.component.salesforce.api.dto.AbstractSObjectBase;
import org.apache.camel.component.salesforce.internal.OperationName;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.DefaultMessage;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JsonRestProcessorTest {

    static class TestObject extends AbstractSObjectBase {

        private ZonedDateTime creationDate;

        public ZonedDateTime getCreationDate() {
            return creationDate;
        }

        public void setCreationDate(final ZonedDateTime creationDate) {
            this.creationDate = creationDate;
        }
    }

    @Test
    public void byDefaultItShouldNotSerializeNullValues() throws SalesforceException, IOException {
        final SalesforceComponent salesforce = new SalesforceComponent();
        final SalesforceEndpointConfig configuration = new SalesforceEndpointConfig();
        final SalesforceEndpoint endpoint
                = new SalesforceEndpoint("", salesforce, configuration, OperationName.UPDATE_SOBJECT, "");

        try (final JsonRestProcessor jsonProcessor = new JsonRestProcessor(endpoint)) {
            final Message in = new DefaultMessage(new DefaultCamelContext());
            try (InputStream stream = jsonProcessor.getRequestStream(in, new TestObject());
                 InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                final String json = IOUtils.toString(reader);
                assertThat(json).isEqualTo("{\"attributes\":{\"referenceId\":null,\"type\":null,\"url\":null}}");
            }
        }
    }

    @Test
    public void shouldSerializeNullValues() throws SalesforceException, IOException {
        final SalesforceComponent salesforce = new SalesforceComponent();
        final SalesforceEndpointConfig configuration = new SalesforceEndpointConfig();
        final SalesforceEndpoint endpoint
                = new SalesforceEndpoint("", salesforce, configuration, OperationName.UPDATE_SOBJECT, "");

        try (final JsonRestProcessor jsonProcessor = new JsonRestProcessor(endpoint)) {
            final Message in = new DefaultMessage(new DefaultCamelContext());
            TestObject testObject = new TestObject();
            testObject.getFieldsToNull().add("creationDate");
            try (InputStream stream = jsonProcessor.getRequestStream(in, testObject);
                 InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                final String json = IOUtils.toString(reader);
                assertThat(json)
                        .isEqualTo("{\"creationDate\":null,\"attributes\":{\"referenceId\":null,\"type\":null,\"url\":null}}");
            }
        }
    }

    @Test
    public void getRequestStream() throws Exception {
        final SalesforceComponent comp = new SalesforceComponent();
        final SalesforceEndpointConfig conf = new SalesforceEndpointConfig();
        final OperationName op = OperationName.CREATE_BATCH;
        final SalesforceEndpoint endpoint = new SalesforceEndpoint("", comp, conf, op, "");
        try (final JsonRestProcessor jsonRestProcessor = new JsonRestProcessor(endpoint)) {
            final DefaultCamelContext context = new DefaultCamelContext();
            final Exchange exchange = new DefaultExchange(context, ExchangePattern.InOut);
            final TestObject doc = new TestObject();
            doc.setCreationDate(ZonedDateTime.of(1717, 1, 2, 3, 4, 5, 6, ZoneId.systemDefault()));

            exchange.getIn().setBody(doc);
            try (InputStream stream = jsonRestProcessor.getRequestStream(exchange);
                 InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                final String result = IOUtils.toString(reader);
                assertThat(result.length()).isLessThanOrEqualTo(104);
            }
        }
    }

    public static class Contact {

        private final String lastName;

        public Contact(String lastName) {
            this.lastName = lastName;
        }

        public String getLastName() {
            return lastName;
        }
    }

    @Test
    public void getRequestStreamShouldMarshalMapAsJson() throws Exception {
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("Name", "x");
        body.put("NumberOfEmployees", 5);

        assertThat(requestOf(body)).isEqualTo("{\"Name\":\"x\",\"NumberOfEmployees\":5}");
    }

    @Test
    public void getRequestStreamShouldMarshalPojoAsJson() throws Exception {
        assertThat(requestOf(new Contact("Doe"))).isEqualTo("{\"lastName\":\"Doe\"}");
    }

    @Test
    public void getRequestStreamShouldSendTextAsIs() throws Exception {
        assertThat(requestOf("{ \"Name\" : \"x\" }")).isEqualTo("{ \"Name\" : \"x\" }");
        assertThat(requestOf(new StringReader("{ \"Name\" : \"x\" }"))).isEqualTo("{ \"Name\" : \"x\" }");
    }

    private static String requestOf(Object body) throws Exception {
        final SalesforceComponent comp = new SalesforceComponent();
        final SalesforceEndpointConfig conf = new SalesforceEndpointConfig();
        final SalesforceEndpoint endpoint = new SalesforceEndpoint("", comp, conf, OperationName.CREATE_SOBJECT, "");
        try (final JsonRestProcessor jsonRestProcessor = new JsonRestProcessor(endpoint)) {
            final Exchange exchange = new DefaultExchange(new DefaultCamelContext(), ExchangePattern.InOut);
            exchange.getIn().setBody(body);
            try (InputStream stream = jsonRestProcessor.getRequestStream(exchange)) {
                return IOUtils.toString(stream, StandardCharsets.UTF_8);
            }
        }
    }
}
