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
package org.apache.camel.support;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.clock.Clock;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.DataTypeAware;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The unit test for the class {@link AbstractExchange}.
 */
public class AbstractExchangeTest {

    private static final String URI = "mock://foo";

    private final CamelContext context = new DefaultCamelContext();

    static class CustomAbstractExchange extends AbstractExchange {
        private final Clock clock = new MonotonicClock();

        CustomAbstractExchange(CustomAbstractExchange abstractExchange) {
            super(abstractExchange);
        }

        public CustomAbstractExchange(CamelContext context) {
            super(context);
        }

        @Override
        AbstractExchange newCopy() {
            return new CustomAbstractExchange(this);
        }

        @Override
        public Clock getClock() {
            return clock;
        }
    }

    @Test
    void shouldPreserveDataTypeOnCopy() {
        AbstractExchange e1 = new CustomAbstractExchange(new DefaultCamelContext());
        Object body1 = new Object();
        DataType type1 = new DataType("foo1");
        DefaultMessage in = new DefaultMessage((Exchange) null);
        in.setBody(body1, type1);
        e1.setIn(in);
        Object body2 = new Object();
        DataType type2 = new DataType("foo2");
        DefaultMessage out = new DefaultMessage((Exchange) null);
        out.setBody(body2, type2);
        e1.setOut(out);

        Exchange e2 = e1.copy();
        assertSame(body1, e2.getIn().getBody());
        assertInstanceOf(DataTypeAware.class, e2.getIn());
        assertSame(type1, ((DataTypeAware) e2.getIn()).getDataType());
        assertSame(body2, e2.getMessage().getBody());
        assertInstanceOf(DataTypeAware.class, e2.getMessage());
        assertSame(type2, ((DataTypeAware) e2.getMessage()).getDataType());
    }

    @Test
    void shouldNotCreateInternalPropertiesForToEndpoint() {
        // every send sets this key, so it must not cost every exchange the map sized for all the keys
        DefaultExchange exchange = new DefaultExchange(context);
        assertNull(exchange.internalProperties);

        exchange.setProperty(ExchangePropertyKey.TO_ENDPOINT, URI);
        assertEquals(URI, exchange.getProperty(ExchangePropertyKey.TO_ENDPOINT));
        assertNull(exchange.internalProperties);

        AbstractExchange copy = (AbstractExchange) exchange.copy();
        assertNull(copy.internalProperties);
    }

    @Test
    void shouldKeepToEndpointBesideOtherInternalProperties() {
        DefaultExchange exchange = new DefaultExchange(context);
        exchange.setProperty(ExchangePropertyKey.TO_ENDPOINT, URI);
        exchange.setProperty(ExchangePropertyKey.CHARSET_NAME, "UTF-8");

        assertNotNull(exchange.internalProperties);
        assertEquals(URI, exchange.getProperty(ExchangePropertyKey.TO_ENDPOINT));
        assertEquals("UTF-8", exchange.getProperty(ExchangePropertyKey.CHARSET_NAME));

        assertEquals("UTF-8", exchange.removeProperty(ExchangePropertyKey.CHARSET_NAME));
        assertEquals(URI, exchange.getProperty(ExchangePropertyKey.TO_ENDPOINT));
    }

    @Test
    void shouldReadOtherInternalPropertiesWithoutTheMap() {
        DefaultExchange exchange = new DefaultExchange(context);

        assertNull(exchange.getProperty(ExchangePropertyKey.CHARSET_NAME));
        assertNull(exchange.getProperty(Exchange.CHARSET_NAME));
        assertNull(exchange.removeProperty(ExchangePropertyKey.CHARSET_NAME));
        assertTrue(exchange.getInternalProperties().isEmpty());
        assertTrue(exchange.getAllProperties().isEmpty());
    }

    @Test
    void shouldAccessToEndpointByKeyAndByName() {
        // the name is the public constant that routes and components use
        DefaultExchange exchange = new DefaultExchange(context);

        exchange.setProperty(Exchange.TO_ENDPOINT, URI);
        assertEquals(URI, exchange.getProperty(ExchangePropertyKey.TO_ENDPOINT));
        assertEquals(URI, exchange.getProperty(Exchange.TO_ENDPOINT));
        assertEquals(URI, exchange.getProperty(Exchange.TO_ENDPOINT, String.class));

        assertEquals(URI, exchange.removeProperty(Exchange.TO_ENDPOINT));
        assertNull(exchange.getProperty(ExchangePropertyKey.TO_ENDPOINT));

        exchange.setProperty(ExchangePropertyKey.TO_ENDPOINT, URI);
        assertEquals(URI, exchange.removeProperty(ExchangePropertyKey.TO_ENDPOINT));
        assertNull(exchange.getProperty(Exchange.TO_ENDPOINT));
    }

    @Test
    void shouldListToEndpointWithTheInternalProperties() {
        // the dev consoles and tracers dump the exchange with these
        DefaultExchange exchange = new DefaultExchange(context);
        exchange.setProperty(ExchangePropertyKey.TO_ENDPOINT, URI);
        exchange.setProperty("foo", "bar");

        assertEquals(Map.of("CamelToEndpoint", URI), exchange.getInternalProperties());
        assertEquals(Map.of("CamelToEndpoint", URI, "foo", "bar"), exchange.getAllProperties());
    }

    @Test
    void shouldCopyToEndpointIndependently() {
        DefaultExchange exchange = new DefaultExchange(context);
        exchange.setProperty(ExchangePropertyKey.TO_ENDPOINT, URI);

        Exchange copy = exchange.copy();
        assertEquals(URI, copy.getProperty(ExchangePropertyKey.TO_ENDPOINT));

        copy.setProperty(ExchangePropertyKey.TO_ENDPOINT, "mock://bar");
        assertEquals(URI, exchange.getProperty(ExchangePropertyKey.TO_ENDPOINT));
    }

    @Test
    void shouldCopyInternalPropertiesIndependently() {
        DefaultExchange exchange = new DefaultExchange(context);
        exchange.setProperty(ExchangePropertyKey.CHARSET_NAME, "UTF-8");

        Exchange copy = exchange.copy();
        assertEquals("UTF-8", copy.getProperty(ExchangePropertyKey.CHARSET_NAME));

        copy.setProperty(ExchangePropertyKey.CHARSET_NAME, "ISO-8859-1");
        assertEquals("UTF-8", exchange.getProperty(ExchangePropertyKey.CHARSET_NAME));
    }

    @Test
    void shouldCreateCopyWithToEndpoint() {
        DefaultExchange exchange = new DefaultExchange(context);
        exchange.setProperty(ExchangePropertyKey.TO_ENDPOINT, URI);
        exchange.setProperty("foo", "bar");

        Exchange copy = exchange.getExchangeExtension().createCopyWithProperties(context);

        assertEquals(URI, copy.getProperty(ExchangePropertyKey.TO_ENDPOINT));
        assertEquals("bar", copy.getProperty("foo"));
        assertNull(((AbstractExchange) copy).internalProperties);
    }

    @Test
    void shouldCreateCopyWithInternalProperties() {
        DefaultExchange exchange = new DefaultExchange(context);
        exchange.setProperty(ExchangePropertyKey.CHARSET_NAME, "UTF-8");

        Exchange copy = exchange.getExchangeExtension().createCopyWithProperties(context);

        assertEquals("UTF-8", copy.getProperty(ExchangePropertyKey.CHARSET_NAME));
    }

    @Test
    void shouldCopyInternalPropertiesToTarget() {
        DefaultExchange source = new DefaultExchange(context);
        source.setProperty(ExchangePropertyKey.TO_ENDPOINT, URI);
        source.setProperty(ExchangePropertyKey.CHARSET_NAME, "UTF-8");

        DefaultExchange target = new DefaultExchange(context);
        source.getExchangeExtension().copyInternalProperties(target);

        assertEquals(URI, target.getProperty(ExchangePropertyKey.TO_ENDPOINT));
        assertEquals("UTF-8", target.getProperty(ExchangePropertyKey.CHARSET_NAME));
        // the target has its own map, so it can change without changing the source
        target.setProperty(ExchangePropertyKey.CHARSET_NAME, "ISO-8859-1");
        assertEquals("UTF-8", source.getProperty(ExchangePropertyKey.CHARSET_NAME));
    }

    @Test
    void shouldKeepPropertiesOfTargetThatTheSourceDoesNotHave() {
        DefaultExchange target = new DefaultExchange(context);
        target.setProperty(ExchangePropertyKey.TO_ENDPOINT, URI);
        target.setProperty(ExchangePropertyKey.CHARSET_NAME, "UTF-8");

        new DefaultExchange(context).getExchangeExtension().copyInternalProperties(target);

        assertEquals(URI, target.getProperty(ExchangePropertyKey.TO_ENDPOINT));
        assertEquals("UTF-8", target.getProperty(ExchangePropertyKey.CHARSET_NAME));
    }

    @Test
    void shouldRemoveToEndpointWithAllProperties() {
        DefaultExchange exchange = new DefaultExchange(context);
        exchange.setProperty(ExchangePropertyKey.TO_ENDPOINT, URI);

        assertTrue(exchange.removeProperties("*"));
        assertNull(exchange.getProperty(ExchangePropertyKey.TO_ENDPOINT));
    }

    @Test
    void shouldRemoveToEndpointByPattern() {
        DefaultExchange exchange = new DefaultExchange(context);
        exchange.setProperty(ExchangePropertyKey.TO_ENDPOINT, URI);

        assertTrue(exchange.removeProperties("CamelTo*"));
        assertNull(exchange.getProperty(ExchangePropertyKey.TO_ENDPOINT));
    }

    @Test
    void shouldKeepToEndpointExcludedFromPattern() {
        DefaultExchange exchange = new DefaultExchange(context);
        exchange.setProperty(ExchangePropertyKey.TO_ENDPOINT, URI);
        exchange.setProperty(ExchangePropertyKey.CHARSET_NAME, "UTF-8");

        exchange.removeProperties("Camel*", "CamelToEndpoint");

        assertEquals(URI, exchange.getProperty(ExchangePropertyKey.TO_ENDPOINT));
        assertNull(exchange.getProperty(ExchangePropertyKey.CHARSET_NAME));
    }

    @Test
    void shouldClearInternalPropertiesOfPooledExchangeWhenDone() {
        // a reused exchange must not see the endpoint of its previous use
        DefaultPooledExchange exchange = new DefaultPooledExchange(context);
        exchange.setProperty(ExchangePropertyKey.TO_ENDPOINT, URI);
        exchange.setProperty(ExchangePropertyKey.CHARSET_NAME, "UTF-8");
        assertFalse(exchange.getInternalProperties().isEmpty());

        exchange.done();

        assertTrue(exchange.getInternalProperties().isEmpty());
    }

}
