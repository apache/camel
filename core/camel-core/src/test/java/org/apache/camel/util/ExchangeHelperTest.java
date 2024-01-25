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
package org.apache.camel.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.NoSuchBeanException;
import org.apache.camel.NoSuchHeaderException;
import org.apache.camel.NoSuchPropertyException;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.ExchangeHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ExchangeHelperTest extends ContextTestSupport {

    protected Exchange exchange;

    @Test
    public void testValidProperty() throws Exception {
        String value = ExchangeHelper.getMandatoryProperty(exchange, "foo", String.class);
        assertEquals("123", value, "foo property");
    }

    @Test
    public void testMissingProperty() throws Exception {
        try {
            String value = ExchangeHelper.getMandatoryProperty(exchange, "bar", String.class);
            fail("Should have failed but got: " + value);
        } catch (NoSuchPropertyException e) {
            assertEquals("bar", e.getPropertyName());
        }
    }

    @Test
    public void testPropertyOfIncompatibleType() throws Exception {
        try {
            List<?> value = ExchangeHelper.getMandatoryProperty(exchange, "foo", List.class);
            fail("Should have failed but got: " + value);
        } catch (NoSuchPropertyException e) {
            assertEquals("foo", e.getPropertyName());
        }
    }

    @Test
    public void testMissingHeader() throws Exception {
        try {
            String value = ExchangeHelper.getMandatoryHeader(exchange, "unknown", String.class);
            fail("Should have failed but got: " + value);
        } catch (NoSuchHeaderException e) {
            assertEquals("unknown", e.getHeaderName());
        }
    }

    @Test
    public void testHeaderOfIncompatibleType() throws Exception {
        exchange.getIn().setHeader("foo", 123);
        try {
            List<?> value = ExchangeHelper.getMandatoryHeader(exchange, "foo", List.class);
            fail("Should have failed but got: " + value);
        } catch (NoSuchHeaderException e) {
            assertEquals("foo", e.getHeaderName());
        }
    }

    @Test
    public void testNoSuchBean() throws Exception {
        try {
            ExchangeHelper.lookupMandatoryBean(exchange, "foo");
            fail("Should have thrown an exception");
        } catch (NoSuchBeanException e) {
            assertEquals("No bean could be found in the registry for: foo", e.getMessage());
            assertEquals("foo", e.getName());
        }
    }

    @Test
    public void testNoSuchBeanType() throws Exception {
        try {
            ExchangeHelper.lookupMandatoryBean(exchange, "foo", String.class);
            fail("Should have thrown an exception");
        } catch (NoSuchBeanException e) {
            assertEquals("No bean could be found in the registry for: foo", e.getMessage());
            assertEquals("foo", e.getName());
        }
    }

    @Test
    public void testPopulateVariableMapBodyAndHeaderOnly() throws Exception {
        exchange.setPattern(ExchangePattern.InOut);
        exchange.getMessage().setBody("bar");
        exchange.getMessage().setHeader("quote", "Camel rocks");

        Map<String, Object> map = new HashMap<>();
        ExchangeHelper.populateVariableMap(exchange, map, false);

        assertEquals(3, map.size());
        assertNull(map.get("exchange"));
        assertNull(map.get("in"));
        assertNull(map.get("request"));
        assertNull(map.get("out"));
        assertNull(map.get("response"));
        assertSame(exchange.getIn().getHeaders(), map.get("headers"));
        assertSame(exchange.getIn().getBody(), map.get("body"));
        assertSame(exchange.getVariable("cheese"), map.get("cheese"));
        assertNull(map.get("camelContext"));
    }

    @Test
    public void testPopulateVariableMap() throws Exception {
        exchange.setPattern(ExchangePattern.InOut);
        exchange.getMessage().setBody("bar");
        exchange.getMessage().setHeader("quote", "Camel rocks");

        Map<String, Object> map = new HashMap<>();
        ExchangeHelper.populateVariableMap(exchange, map, true);

        assertEquals(10, map.size());
        assertSame(exchange, map.get("exchange"));
        assertSame(exchange.getIn(), map.get("in"));
        assertSame(exchange.getIn(), map.get("request"));
        assertSame(exchange.getMessage(), map.get("out"));
        assertSame(exchange.getMessage(), map.get("response"));
        assertSame(exchange.getIn().getHeaders(), map.get("headers"));
        assertSame(exchange.getIn().getBody(), map.get("body"));
        assertSame(exchange.getContext(), map.get("camelContext"));
    }

    @Test
    public void testCreateVariableMap() throws Exception {
        exchange.setPattern(ExchangePattern.InOut);
        exchange.getMessage().setBody("bar");
        exchange.getMessage().setHeader("quote", "Camel rocks");
        exchange.setVariable("cheese", "gauda");

        Map<?, ?> map = ExchangeHelper.createVariableMap(exchange, true);

        assertEquals(10, map.size());
        assertSame(exchange, map.get("exchange"));
        assertSame(exchange.getIn(), map.get("in"));
        assertSame(exchange.getIn(), map.get("request"));
        assertSame(exchange.getMessage(), map.get("out"));
        assertSame(exchange.getMessage(), map.get("response"));
        assertSame(exchange.getIn().getHeaders(), map.get("headers"));
        assertSame(exchange.getIn().getBody(), map.get("body"));
        assertSame(exchange.getContext(), map.get("camelContext"));
    }

    @Test
    public void testCreateVariableMapNoExistingOut() throws Exception {
        exchange.setPattern(ExchangePattern.InOut);
        exchange.getIn().setBody("bar");
        exchange.getIn().setHeader("quote", "Camel rocks");
        assertFalse(exchange.hasOut());

        Map<?, ?> map = ExchangeHelper.createVariableMap(exchange, true);

        // there should still be 10 in the map
        assertEquals(10, map.size());
        assertSame(exchange, map.get("exchange"));
        assertSame(exchange.getIn(), map.get("in"));
        assertSame(exchange.getIn(), map.get("request"));
        assertSame(exchange.getIn(), map.get("out"));
        assertSame(exchange.getIn(), map.get("response"));
        assertSame(exchange.getIn().getHeaders(), map.get("headers"));
        assertSame(exchange.getIn().getBody(), map.get("body"));
        assertSame(exchange.getVariable("cheese"), map.get("cheese"));
        assertSame(exchange.getContext(), map.get("camelContext"));

        // but the Exchange does still not have an OUT message to avoid
        // causing side effects with the createVariableMap method
        assertFalse(exchange.hasOut());
    }

    @Test
    public void testGetContentType() throws Exception {
        exchange.getIn().setHeader(Exchange.CONTENT_TYPE, "text/xml");
        assertEquals("text/xml", ExchangeHelper.getContentType(exchange));
    }

    @Test
    public void testGetContentEncoding() throws Exception {
        exchange.getIn().setHeader(Exchange.CONTENT_ENCODING, "iso-8859-1");
        assertEquals("iso-8859-1", ExchangeHelper.getContentEncoding(exchange));
    }

    @Test
    public void testIsStreamCaching() throws Exception {
        assertFalse(ExchangeHelper.isStreamCachingEnabled(exchange));
        exchange.getContext().getStreamCachingStrategy().setEnabled(true);
        assertTrue(ExchangeHelper.isStreamCachingEnabled(exchange));
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        exchange = new DefaultExchange(new DefaultCamelContext());
        exchange.setProperty("foo", 123);
    }
}
