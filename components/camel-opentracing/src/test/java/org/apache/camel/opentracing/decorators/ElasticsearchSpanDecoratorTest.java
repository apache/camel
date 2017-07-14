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

public class ElasticsearchSpanDecoratorTest {

    @Test
    public void testOperationName() {
        String opName = "INDEX";
        Endpoint endpoint = Mockito.mock(Endpoint.class);

        Mockito.when(endpoint.getEndpointUri()).thenReturn("elasticsearch://local?operation="
                + opName + "&indexName=twitter&indexType=tweet");

        SpanDecorator decorator = new ElasticsearchSpanDecorator();

        assertEquals(opName, decorator.getOperationName(null, endpoint));
    }

    @Test
    public void testPre() {
        String indexName = "twitter";
        String cluster = "local";

        Endpoint endpoint = Mockito.mock(Endpoint.class);
        Exchange exchange = Mockito.mock(Exchange.class);
        Message message = Mockito.mock(Message.class);

        Mockito.when(endpoint.getEndpointUri()).thenReturn("elasticsearch://" + cluster
                + "?operation=INDEX&indexName=" + indexName + "&indexType=tweet");
        Mockito.when(exchange.getIn()).thenReturn(message);

        SpanDecorator decorator = new ElasticsearchSpanDecorator();

        MockTracer tracer = new MockTracer();
        MockSpan span = tracer.buildSpan("TestSpan").start();

        decorator.pre(span, exchange, endpoint);

        assertEquals(ElasticsearchSpanDecorator.ELASTICSEARCH_DB_TYPE, span.tags().get(Tags.DB_TYPE.getKey()));
        assertEquals(indexName, span.tags().get(Tags.DB_INSTANCE.getKey()));
        assertEquals(cluster, span.tags().get(ElasticsearchSpanDecorator.ELASTICSEARCH_CLUSTER_TAG));
    }

}
