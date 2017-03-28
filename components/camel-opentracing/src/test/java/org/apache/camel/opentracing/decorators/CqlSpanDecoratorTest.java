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
package org.apache.camel.opentracing.decorators;

import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.opentracing.tag.Tags;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.opentracing.SpanDecorator;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class CqlSpanDecoratorTest {

    @Test
    public void testPreCqlFromUri() {
        String cql = "select%20*%20from%20users";
        String keyspace = "test";

        Endpoint endpoint = Mockito.mock(Endpoint.class);
        Exchange exchange = Mockito.mock(Exchange.class);
        Message message = Mockito.mock(Message.class);

        Mockito.when(endpoint.getEndpointUri()).thenReturn("cql://host1,host2:8080/" + keyspace + "?cql="
                + cql + "&consistencyLevel=quorum");
        Mockito.when(exchange.getIn()).thenReturn(message);

        SpanDecorator decorator = new CqlSpanDecorator();

        MockTracer tracer = new MockTracer();
        MockSpan span = (MockSpan)tracer.buildSpan("TestSpan").start();

        decorator.pre(span, exchange, endpoint);

        assertEquals(CqlSpanDecorator.CASSANDRA_DB_TYPE, span.tags().get(Tags.DB_TYPE.getKey()));
        assertEquals(cql, span.tags().get(Tags.DB_STATEMENT.getKey()));
        assertEquals(keyspace, span.tags().get(Tags.DB_INSTANCE.getKey()));
    }

    @Test
    public void testPreCqlFromHeader() {
        String cql = "select * from users";

        Endpoint endpoint = Mockito.mock(Endpoint.class);
        Exchange exchange = Mockito.mock(Exchange.class);
        Message message = Mockito.mock(Message.class);

        Mockito.when(endpoint.getEndpointUri()).thenReturn("cql://host1,host2?consistencyLevel=quorum");
        Mockito.when(exchange.getIn()).thenReturn(message);
        Mockito.when(message.getHeader(CqlSpanDecorator.CAMEL_CQL_QUERY)).thenReturn(cql);

        SpanDecorator decorator = new CqlSpanDecorator();

        MockTracer tracer = new MockTracer();
        MockSpan span = (MockSpan)tracer.buildSpan("TestSpan").start();

        decorator.pre(span, exchange, endpoint);

        assertEquals(CqlSpanDecorator.CASSANDRA_DB_TYPE, span.tags().get(Tags.DB_TYPE.getKey()));
        assertEquals(cql, span.tags().get(Tags.DB_STATEMENT.getKey()));
        assertNull(span.tags().get(Tags.DB_INSTANCE.getKey()));
    }

}
