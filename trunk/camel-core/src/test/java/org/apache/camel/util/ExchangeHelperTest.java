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
package org.apache.camel.util;

import java.util.ArrayList;
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
import org.apache.camel.impl.DefaultExchange;

/**
 * @version 
 */
public class ExchangeHelperTest extends ContextTestSupport {

    protected Exchange exchange;

    public void testValidProperty() throws Exception {
        String value = ExchangeHelper.getMandatoryProperty(exchange, "foo", String.class);
        assertEquals("foo property", "123", value);
    }

    public void testMissingProperty() throws Exception {
        try {
            String value = ExchangeHelper.getMandatoryProperty(exchange, "bar", String.class);
            fail("Should have failed but got: " + value);
        } catch (NoSuchPropertyException e) {
            assertEquals("bar", e.getPropertyName());
        }
    }

    public void testPropertyOfIncompatibleType() throws Exception {
        try {
            List<?> value = ExchangeHelper.getMandatoryProperty(exchange, "foo", List.class);
            fail("Should have failed but got: " + value);
        } catch (NoSuchPropertyException e) {
            assertEquals("foo", e.getPropertyName());
        }
    }

    public void testMissingHeader() throws Exception {
        try {
            String value = ExchangeHelper.getMandatoryHeader(exchange, "unknown", String.class);
            fail("Should have failed but got: " + value);
        } catch (NoSuchHeaderException e) {
            assertEquals("unknown", e.getHeaderName());
        }
    }

    public void testHeaderOfIncompatibleType() throws Exception {
        exchange.getIn().setHeader("foo", 123);
        try {
            List<?> value = ExchangeHelper.getMandatoryHeader(exchange, "foo", List.class);
            fail("Should have failed but got: " + value);
        } catch (NoSuchHeaderException e) {
            assertEquals("foo", e.getHeaderName());
        }
    }

    public void testNoSuchBean() throws Exception {
        try {
            ExchangeHelper.lookupMandatoryBean(exchange, "foo");
            fail("Should have thrown an exception");
        } catch (NoSuchBeanException e) {
            assertEquals("No bean could be found in the registry for: foo", e.getMessage());
            assertEquals("foo", e.getName());
        }
    }

    public void testNoSuchBeanType() throws Exception {
        try {
            ExchangeHelper.lookupMandatoryBean(exchange, "foo", String.class);
            fail("Should have thrown an exception");
        } catch (NoSuchBeanException e) {
            assertEquals("No bean could be found in the registry for: foo", e.getMessage());
            assertEquals("foo", e.getName());
        }
    }

    public void testGetExchangeById() throws Exception {
        List<Exchange> list = new ArrayList<Exchange>();
        Exchange e1 = context.getEndpoint("mock:foo").createExchange();
        Exchange e2 = context.getEndpoint("mock:foo").createExchange();
        list.add(e1);
        list.add(e2);

        assertNull(ExchangeHelper.getExchangeById(list, "unknown"));
        assertEquals(e1, ExchangeHelper.getExchangeById(list, e1.getExchangeId()));
        assertEquals(e2, ExchangeHelper.getExchangeById(list, e2.getExchangeId()));
    }

    public void testPopulateVariableMap() throws Exception {
        exchange.setPattern(ExchangePattern.InOut);
        exchange.getOut().setBody("bar");
        exchange.getOut().setHeader("quote", "Camel rocks");

        Map<String, Object> map = new HashMap<String, Object>();
        ExchangeHelper.populateVariableMap(exchange, map);

        assertEquals(8, map.size());
        assertSame(exchange, map.get("exchange"));
        assertSame(exchange.getIn(), map.get("in"));
        assertSame(exchange.getIn(), map.get("request"));
        assertSame(exchange.getOut(), map.get("out"));
        assertSame(exchange.getOut(), map.get("response"));
        assertSame(exchange.getIn().getHeaders(), map.get("headers"));
        assertSame(exchange.getIn().getBody(), map.get("body"));
        assertSame(exchange.getContext(), map.get("camelContext"));
    }

    public void testCreateVariableMap() throws Exception {
        exchange.setPattern(ExchangePattern.InOut);
        exchange.getOut().setBody("bar");
        exchange.getOut().setHeader("quote", "Camel rocks");

        Map<?, ?> map = ExchangeHelper.createVariableMap(exchange);

        assertEquals(8, map.size());
        assertSame(exchange, map.get("exchange"));
        assertSame(exchange.getIn(), map.get("in"));
        assertSame(exchange.getIn(), map.get("request"));
        assertSame(exchange.getOut(), map.get("out"));
        assertSame(exchange.getOut(), map.get("response"));
        assertSame(exchange.getIn().getHeaders(), map.get("headers"));
        assertSame(exchange.getIn().getBody(), map.get("body"));
        assertSame(exchange.getContext(), map.get("camelContext"));
    }

    public void testCreateVariableMapNoExistingOut() throws Exception {
        exchange.setPattern(ExchangePattern.InOut);
        exchange.getIn().setBody("bar");
        exchange.getIn().setHeader("quote", "Camel rocks");
        assertFalse(exchange.hasOut());

        Map<?, ?> map = ExchangeHelper.createVariableMap(exchange);

        // there should still be 8 in the map
        assertEquals(8, map.size());
        assertSame(exchange, map.get("exchange"));
        assertSame(exchange.getIn(), map.get("in"));
        assertSame(exchange.getIn(), map.get("request"));
        assertSame(exchange.getIn(), map.get("out"));
        assertSame(exchange.getIn(), map.get("response"));
        assertSame(exchange.getIn().getHeaders(), map.get("headers"));
        assertSame(exchange.getIn().getBody(), map.get("body"));
        assertSame(exchange.getContext(), map.get("camelContext"));

        // but the Exchange does still not have an OUT message to avoid
        // causing side effects with the createVariableMap method
        assertFalse(exchange.hasOut());
    }

    public void testGetContentType() throws Exception {
        exchange.getIn().setHeader(Exchange.CONTENT_TYPE, "text/xml");
        assertEquals("text/xml", ExchangeHelper.getContentType(exchange));
    }

    public void testGetContentEncpding() throws Exception {
        exchange.getIn().setHeader(Exchange.CONTENT_ENCODING, "iso-8859-1");
        assertEquals("iso-8859-1", ExchangeHelper.getContentEncoding(exchange));
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        exchange = new DefaultExchange(new DefaultCamelContext());
        exchange.setProperty("foo", 123);
    }
}
