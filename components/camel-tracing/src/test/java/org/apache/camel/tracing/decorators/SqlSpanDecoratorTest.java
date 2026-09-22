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

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.spi.PropertyConfigurer;
import org.apache.camel.spi.PropertyConfigurerGetter;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.tracing.MockSpanAdapter;
import org.apache.camel.tracing.SpanDecorator;
import org.apache.camel.tracing.TagConstants;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

@Deprecated(since = "4.19.0")
public class SqlSpanDecoratorTest {

    private static final String SQL_STATEMENT = "select * from customer";

    @Test
    public void testPreTagsTheQueryHeaderWhenTheEndpointAllowsIt() {
        MockSpanAdapter span = decorate(endpointAllowingQueryHeader(true));

        assertEquals("sql", span.tags().get(TagConstants.DB_SYSTEM));
        assertEquals(SQL_STATEMENT, span.tags().get(TagConstants.DB_STATEMENT));
    }

    @Test
    public void testPreIgnoresTheQueryHeaderWhenTheEndpointDisallowsIt() {
        MockSpanAdapter span = decorate(endpointAllowingQueryHeader(false));

        // allowQueryFromHeader is disabled by default, so this statement was never executed and
        // tagging it would put sender-controlled text into the span
        assertEquals("sql", span.tags().get(TagConstants.DB_SYSTEM));
        assertNull(span.tags().get(TagConstants.DB_STATEMENT));
    }

    @Test
    public void testPreIgnoresTheQueryHeaderForAnEndpointWithoutTheOption() {
        // e.g. jdbc, which takes its query from the message body
        MockSpanAdapter span = decorate(Mockito.mock(Endpoint.class));

        assertEquals("sql", span.tags().get(TagConstants.DB_SYSTEM));
        assertNull(span.tags().get(TagConstants.DB_STATEMENT));
    }

    @Test
    public void testQueryHeaderIsNotHonouredWhenTheComponentIsUnknown() {
        DefaultEndpoint endpoint = Mockito.mock(DefaultEndpoint.class);
        Mockito.when(endpoint.getComponent()).thenReturn(null);

        assertFalse(SqlQueryHeaderHelper.isQueryHeaderHonoured(endpoint));
    }

    private static MockSpanAdapter decorate(Endpoint endpoint) {
        Exchange exchange = Mockito.mock(Exchange.class);
        Message message = Mockito.mock(Message.class);

        Mockito.when(endpoint.getEndpointUri()).thenReturn("test");
        Mockito.when(exchange.getIn()).thenReturn(message);
        Mockito.when(message.getHeader(SqlSpanDecorator.CAMEL_SQL_QUERY, String.class)).thenReturn(SQL_STATEMENT);

        SpanDecorator decorator = new SqlSpanDecorator();
        MockSpanAdapter span = new MockSpanAdapter();
        decorator.pre(span, exchange, endpoint);
        return span;
    }

    private static Endpoint endpointAllowingQueryHeader(boolean allow) {
        Component component = Mockito.mock(Component.class);
        Mockito.when(component.getEndpointPropertyConfigurer()).thenReturn(new AllowQueryFromHeaderConfigurer(allow));

        DefaultEndpoint endpoint = Mockito.mock(DefaultEndpoint.class);
        Mockito.when(endpoint.getComponent()).thenReturn(component);
        return endpoint;
    }

    /**
     * Stands in for the generated {@code SqlEndpointConfigurer}, which camel-tracing cannot depend on.
     */
    private static class AllowQueryFromHeaderConfigurer implements PropertyConfigurer, PropertyConfigurerGetter {

        private final boolean allow;

        AllowQueryFromHeaderConfigurer(boolean allow) {
            this.allow = allow;
        }

        @Override
        public boolean configure(CamelContext camelContext, Object target, String name, Object value, boolean ignoreCase) {
            return false;
        }

        @Override
        public Class<?> getOptionType(String name, boolean ignoreCase) {
            return "allowQueryFromHeader".equals(name) ? boolean.class : null;
        }

        @Override
        public Object getOptionValue(Object target, String name, boolean ignoreCase) {
            return "allowQueryFromHeader".equals(name) ? allow : null;
        }
    }
}
