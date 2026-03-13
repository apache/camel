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
package org.apache.camel.http.base;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.SendDynamicAware;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class HttpSendDynamicAwareTest {

    private CamelContext camelContext;

    @BeforeEach
    void setUp() throws Exception {
        camelContext = new DefaultCamelContext();
        camelContext.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (camelContext != null) {
            camelContext.stop();
        }
    }

    @Test
    void testHttpQueryHeaderWithNonStringValue() throws Exception {
        HttpSendDynamicAware aware = new HttpSendDynamicAware();
        aware.setScheme("http");

        Exchange exchange = new DefaultExchange(camelContext);
        // Set HTTP_QUERY as a non-String value (Integer) to verify type converter is used
        exchange.getIn().setHeader(Exchange.HTTP_QUERY, 12345);

        Map<String, Object> properties = new LinkedHashMap<>();
        Map<String, Object> lenientProperties = new LinkedHashMap<>();
        SendDynamicAware.DynamicAwareEntry entry
                = new SendDynamicAware.DynamicAwareEntry(
                        "http://localhost:8080", "http://localhost:8080", properties,
                        lenientProperties);

        Processor preProcessor = aware.createPreProcessor(exchange, entry);
        assertNotNull(preProcessor, "PreProcessor should not be null when HTTP_QUERY header is set");

        // Verify the pre-processor sets the query header correctly as a string
        preProcessor.process(exchange);
        assertEquals("12345", exchange.getIn().getHeader(Exchange.HTTP_QUERY));
    }

    @Test
    void testHttpPathHeaderWithNonStringValue() throws Exception {
        HttpSendDynamicAware aware = new HttpSendDynamicAware();
        aware.setScheme("http");

        Exchange exchange = new DefaultExchange(camelContext);
        // Set HTTP_PATH as a non-String value (Integer) to verify type converter is used
        exchange.getIn().setHeader(Exchange.HTTP_PATH, 42);

        Map<String, Object> properties = new LinkedHashMap<>();
        Map<String, Object> lenientProperties = new LinkedHashMap<>();
        SendDynamicAware.DynamicAwareEntry entry
                = new SendDynamicAware.DynamicAwareEntry(
                        "http://localhost:8080", "http://localhost:8080", properties,
                        lenientProperties);

        Processor preProcessor = aware.createPreProcessor(exchange, entry);
        assertNotNull(preProcessor, "PreProcessor should not be null when HTTP_PATH header is set");

        // Verify the pre-processor sets the path header correctly as a string
        preProcessor.process(exchange);
        assertEquals("42", exchange.getIn().getHeader(Exchange.HTTP_PATH));
    }

    @Test
    void testHttpQueryHeaderWithStringValue() throws Exception {
        HttpSendDynamicAware aware = new HttpSendDynamicAware();
        aware.setScheme("http");

        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getIn().setHeader(Exchange.HTTP_QUERY, "foo=bar");

        Map<String, Object> properties = new LinkedHashMap<>();
        Map<String, Object> lenientProperties = new LinkedHashMap<>();
        SendDynamicAware.DynamicAwareEntry entry
                = new SendDynamicAware.DynamicAwareEntry(
                        "http://localhost:8080", "http://localhost:8080", properties,
                        lenientProperties);

        Processor preProcessor = aware.createPreProcessor(exchange, entry);
        assertNotNull(preProcessor, "PreProcessor should not be null when HTTP_QUERY header is set");

        preProcessor.process(exchange);
        assertEquals("foo=bar", exchange.getIn().getHeader(Exchange.HTTP_QUERY));
    }
}
