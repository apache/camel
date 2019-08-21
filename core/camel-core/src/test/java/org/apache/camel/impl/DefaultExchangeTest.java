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
package org.apache.camel.impl;

import java.io.IOException;
import java.net.ConnectException;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangeTestSupport;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.TypeConversionException;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.DefaultMessage;
import org.junit.Test;

public class DefaultExchangeTest extends ExchangeTestSupport {

    @Test
    public void testBody() throws Exception {
        assertNotNull(exchange.getIn().getBody());

        assertEquals("<hello id='m123'>world!</hello>", exchange.getIn().getBody());
        assertEquals("<hello id='m123'>world!</hello>", exchange.getIn().getBody(String.class));

        assertEquals("<hello id='m123'>world!</hello>", exchange.getIn().getMandatoryBody());
        assertEquals("<hello id='m123'>world!</hello>", exchange.getIn().getMandatoryBody(String.class));
    }

    @Test
    public void testMandatoryBody() throws Exception {
        assertNotNull(exchange.getIn().getBody());

        assertEquals("<hello id='m123'>world!</hello>", exchange.getIn().getBody());
        try {
            assertEquals(null, exchange.getIn().getBody(Integer.class));
            fail("Should have thrown a TypeConversionException");
        } catch (TypeConversionException e) {
            // expected
        }

        assertEquals("<hello id='m123'>world!</hello>", exchange.getIn().getMandatoryBody());
        try {
            exchange.getIn().getMandatoryBody(Integer.class);
            fail("Should have thrown an InvalidPayloadException");
        } catch (InvalidPayloadException e) {
            // expected
        }
    }

    @Test
    public void testExceptionAsType() throws Exception {
        exchange.setException(RuntimeCamelException.wrapRuntimeCamelException(new ConnectException("Cannot connect to remote server")));

        ConnectException ce = exchange.getException(ConnectException.class);
        assertNotNull(ce);
        assertEquals("Cannot connect to remote server", ce.getMessage());

        IOException ie = exchange.getException(IOException.class);
        assertNotNull(ie);
        assertEquals("Cannot connect to remote server", ie.getMessage());

        Exception e = exchange.getException(Exception.class);
        assertNotNull(e);
        assertEquals("Cannot connect to remote server", e.getMessage());

        RuntimeCamelException rce = exchange.getException(RuntimeCamelException.class);
        assertNotNull(rce);
        assertNotSame("Cannot connect to remote server", rce.getMessage());
        assertEquals("Cannot connect to remote server", rce.getCause().getMessage());
    }

    @Test
    public void testHeader() throws Exception {
        assertNotNull(exchange.getIn().getHeaders());

        assertEquals(123, exchange.getIn().getHeader("bar"));
        assertEquals(new Integer(123), exchange.getIn().getHeader("bar", Integer.class));
        assertEquals("123", exchange.getIn().getHeader("bar", String.class));
        assertEquals(123, exchange.getIn().getHeader("bar", 234));
        assertEquals(123, exchange.getIn().getHeader("bar", () -> 456));
        assertEquals(456, exchange.getIn().getHeader("baz", () -> 456));

        assertEquals(123, exchange.getIn().getHeader("bar", 234));
        assertEquals(new Integer(123), exchange.getIn().getHeader("bar", 234, Integer.class));
        assertEquals("123", exchange.getIn().getHeader("bar", "234", String.class));
        assertEquals("123", exchange.getIn().getHeader("bar", () -> "456", String.class));
        assertEquals("456", exchange.getIn().getHeader("baz", () -> "456", String.class));

        assertEquals(234, exchange.getIn().getHeader("cheese", 234));
        assertEquals("234", exchange.getIn().getHeader("cheese", 234, String.class));
        assertEquals("456", exchange.getIn().getHeader("cheese", () -> 456, String.class));
    }

    @Test
    public void testProperty() throws Exception {
        exchange.removeProperty("foobar");
        assertFalse(exchange.hasProperties());

        exchange.setProperty("fruit", "apple");
        assertTrue(exchange.hasProperties());

        assertEquals("apple", exchange.getProperty("fruit"));
        assertEquals(null, exchange.getProperty("beer"));
        assertEquals(null, exchange.getProperty("beer", String.class));

        // Current TypeConverter support to turn the null value to false of
        // boolean,
        // as assertEquals needs the Object as the parameter, we have to use
        // Boolean.FALSE value in this case
        assertEquals(Boolean.FALSE, exchange.getProperty("beer", boolean.class));
        assertEquals(null, exchange.getProperty("beer", Boolean.class));

        assertEquals("apple", exchange.getProperty("fruit", String.class));
        assertEquals("apple", exchange.getProperty("fruit", "banana", String.class));
        assertEquals("banana", exchange.getProperty("beer", "banana"));
        assertEquals("banana", exchange.getProperty("beer", "banana", String.class));
    }

    @Test
    public void testRemoveProperties() throws Exception {
        exchange.removeProperty("foobar");
        assertFalse(exchange.hasProperties());

        exchange.setProperty("fruit", "apple");
        exchange.setProperty("fruit1", "banana");
        exchange.setProperty("zone", "Africa");
        assertTrue(exchange.hasProperties());

        assertEquals("apple", exchange.getProperty("fruit"));
        assertEquals("banana", exchange.getProperty("fruit1"));
        assertEquals("Africa", exchange.getProperty("zone"));

        exchange.removeProperties("fr*");
        assertTrue(exchange.hasProperties());
        assertEquals(exchange.getProperties().size(), 1);
        assertEquals(null, exchange.getProperty("fruit", String.class));
        assertEquals(null, exchange.getProperty("fruit1", String.class));
        assertEquals("Africa", exchange.getProperty("zone", String.class));
    }

    @Test
    public void testRemoveAllProperties() throws Exception {
        exchange.removeProperty("foobar");
        assertFalse(exchange.hasProperties());

        exchange.setProperty("fruit", "apple");
        exchange.setProperty("fruit1", "banana");
        exchange.setProperty("zone", "Africa");
        assertTrue(exchange.hasProperties());

        exchange.removeProperties("*");
        assertFalse(exchange.hasProperties());
        assertEquals(exchange.getProperties().size(), 0);
    }

    @Test
    public void testRemovePropertiesWithExclusion() throws Exception {
        exchange.removeProperty("foobar");
        assertFalse(exchange.hasProperties());

        exchange.setProperty("fruit", "apple");
        exchange.setProperty("fruit1", "banana");
        exchange.setProperty("fruit2", "peach");
        exchange.setProperty("zone", "Africa");
        assertTrue(exchange.hasProperties());

        assertEquals("apple", exchange.getProperty("fruit"));
        assertEquals("banana", exchange.getProperty("fruit1"));
        assertEquals("peach", exchange.getProperty("fruit2"));
        assertEquals("Africa", exchange.getProperty("zone"));

        exchange.removeProperties("fr*", "fruit1", "fruit2");
        assertTrue(exchange.hasProperties());
        assertEquals(exchange.getProperties().size(), 3);
        assertEquals(null, exchange.getProperty("fruit", String.class));
        assertEquals("banana", exchange.getProperty("fruit1", String.class));
        assertEquals("peach", exchange.getProperty("fruit2", String.class));
        assertEquals("Africa", exchange.getProperty("zone", String.class));
    }

    @Test
    public void testRemovePropertiesPatternWithAllExcluded() throws Exception {
        exchange.removeProperty("foobar");
        assertFalse(exchange.hasProperties());

        exchange.setProperty("fruit", "apple");
        exchange.setProperty("fruit1", "banana");
        exchange.setProperty("fruit2", "peach");
        exchange.setProperty("zone", "Africa");
        assertTrue(exchange.hasProperties());

        assertEquals("apple", exchange.getProperty("fruit"));
        assertEquals("banana", exchange.getProperty("fruit1"));
        assertEquals("peach", exchange.getProperty("fruit2"));
        assertEquals("Africa", exchange.getProperty("zone"));

        exchange.removeProperties("fr*", "fruit", "fruit1", "fruit2", "zone");
        assertTrue(exchange.hasProperties());
        assertEquals(exchange.getProperties().size(), 4);
        assertEquals("apple", exchange.getProperty("fruit", String.class));
        assertEquals("banana", exchange.getProperty("fruit1", String.class));
        assertEquals("peach", exchange.getProperty("fruit2", String.class));
        assertEquals("Africa", exchange.getProperty("zone", String.class));
    }

    @Test
    public void testInType() throws Exception {
        exchange.setIn(new MyMessage(context));

        MyMessage my = exchange.getIn(MyMessage.class);
        assertNotNull(my);
    }

    @Test
    public void testOutType() throws Exception {
        exchange.setOut(new MyMessage(context));

        MyMessage my = exchange.getOut(MyMessage.class);
        assertNotNull(my);
    }

    @Test
    public void testCopy() {
        DefaultExchange sourceExchange = new DefaultExchange(context);
        MyMessage sourceIn = new MyMessage(context);
        sourceExchange.setIn(sourceIn);
        Exchange destExchange = sourceExchange.copy();
        Message destIn = destExchange.getIn();

        assertEquals("Dest message should be of the same type as source message", sourceIn.getClass(), destIn.getClass());
    }

    public static class MyMessage extends DefaultMessage {
        public MyMessage(CamelContext camelContext) {
            super(camelContext);
        }

        @Override
        public MyMessage newInstance() {
            return new MyMessage(getCamelContext());
        }
    }

}
