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
package org.apache.camel.tracing.decorators;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.tracing.MockSpanAdapter;
import org.apache.camel.tracing.SpanDecorator;
import org.apache.camel.tracing.Tag;
import org.apache.camel.tracing.TagConstants;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AbstractSpanDecoratorTest {

    private static final String TEST_URI = "test:/uri?query=hello";

    @Test
    public void testGetOperationName() {
        Endpoint endpoint = Mockito.mock(Endpoint.class);

        Mockito.when(endpoint.getEndpointUri()).thenReturn(TEST_URI);

        SpanDecorator decorator = new AbstractSpanDecorator() {
            @Override
            public String getComponent() {
                return null;
            }

            @Override
            public String getComponentClassName() {
                return null;
            }
        };

        // Operation name is scheme, as no specific span decorator to
        // identify an appropriate name
        assertEquals("test", decorator.getOperationName(null, endpoint));
    }

    @Test
    public void testPre() {
        Endpoint endpoint = Mockito.mock(Endpoint.class);

        Mockito.when(endpoint.getEndpointUri()).thenReturn(TEST_URI);

        SpanDecorator decorator = new AbstractSpanDecorator() {
            @Override
            public String getComponent() {
                return null;
            }

            @Override
            public String getComponentClassName() {
                return null;
            }
        };

        MockSpanAdapter span = new MockSpanAdapter();

        decorator.pre(span, null, endpoint);

        assertEquals("camel-test", span.tags().get(Tag.COMPONENT.name()));
        assertEquals("camel-test", span.tags().get(TagConstants.COMPONENT));
        assertEquals("test", span.tags().get(TagConstants.URL_SCHEME));
        assertEquals("uri", span.tags().get(TagConstants.URL_PATH));
        assertEquals("query=hello", span.tags().get(TagConstants.URL_QUERY));
    }

    @Test
    public void testPostExchangeFailed() {
        Exchange exchange = Mockito.mock(Exchange.class);

        Mockito.when(exchange.isFailed()).thenReturn(true);

        Exception e = new Exception("Test Message");
        Mockito.when(exchange.getException()).thenReturn(e);

        SpanDecorator decorator = new AbstractSpanDecorator() {
            @Override
            public String getComponent() {
                return null;
            }

            @Override
            public String getComponentClassName() {
                return null;
            }
        };

        MockSpanAdapter span = new MockSpanAdapter();

        decorator.post(span, exchange, null);

        assertEquals(true, span.tags().get(Tag.ERROR.name()));
        assertEquals(true, span.tags().get(TagConstants.ERROR));
        assertEquals(1, span.logEntries().size());
        assertEquals("error", span.logEntries().get(0).fields().get("event"));
        assertEquals("Exception", span.logEntries().get(0).fields().get("error.kind"));
        assertEquals(e.getMessage(), span.logEntries().get(0).fields().get("message"));
    }

    @Test
    public void testStripSchemeNoOptions() {
        Endpoint endpoint = Mockito.mock(Endpoint.class);

        Mockito.when(endpoint.getEndpointUri()).thenReturn("direct:hello");

        assertEquals("hello", AbstractSpanDecorator.stripSchemeAndOptions(endpoint));
    }

    @Test
    public void testStripSchemeNoOptionsWithSlashes() {
        Endpoint endpoint = Mockito.mock(Endpoint.class);

        Mockito.when(endpoint.getEndpointUri()).thenReturn("direct://hello");

        assertEquals("hello", AbstractSpanDecorator.stripSchemeAndOptions(endpoint));
    }

    @Test
    public void testStripSchemeAndOptions() {
        Endpoint endpoint = Mockito.mock(Endpoint.class);

        Mockito.when(endpoint.getEndpointUri()).thenReturn("direct:hello?world=true");

        assertEquals("hello", AbstractSpanDecorator.stripSchemeAndOptions(endpoint));
    }

}
