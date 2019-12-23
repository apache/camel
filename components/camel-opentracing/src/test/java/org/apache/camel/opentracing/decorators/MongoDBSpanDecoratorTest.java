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
package org.apache.camel.opentracing.decorators;

import java.util.Map;

import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.opentracing.tag.Tags;
import org.apache.camel.Endpoint;
import org.apache.camel.opentracing.SpanDecorator;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MongoDBSpanDecoratorTest {

    private static final String MONGODB_STATEMENT =
            "mongodb:myDb?database=flights&collection=tickets&operation=findOneByQuery";

    @Test
    public void testGetOperationName() {
        Endpoint endpoint = Mockito.mock(Endpoint.class);

        Mockito.when(endpoint.getEndpointUri()).thenReturn(MONGODB_STATEMENT);

        SpanDecorator decorator = new MongoDBSpanDecorator();

        assertEquals("findOneByQuery", decorator.getOperationName(null, endpoint));
    }

    @Test
    public void testToQueryParameters() {
        Map<String, String> params = AbstractSpanDecorator.toQueryParameters(MONGODB_STATEMENT);
        assertEquals(3, params.size());
        assertEquals("flights", params.get("database"));
        assertEquals("tickets", params.get("collection"));
        assertEquals("findOneByQuery", params.get("operation"));
    }

    @Test
    public void testPre() {
        Endpoint endpoint = Mockito.mock(Endpoint.class);

        Mockito.when(endpoint.getEndpointUri()).thenReturn(MONGODB_STATEMENT);

        SpanDecorator decorator = new MongoDBSpanDecorator();

        MockTracer tracer = new MockTracer();
        MockSpan span = tracer.buildSpan("TestSpan").start();

        decorator.pre(span, null, endpoint);

        assertEquals("mongodb", span.tags().get(Tags.DB_TYPE.getKey()));
        assertEquals("flights", span.tags().get(Tags.DB_INSTANCE.getKey()));
        assertTrue(span.tags().containsKey(Tags.DB_STATEMENT.getKey()));
    }

}
