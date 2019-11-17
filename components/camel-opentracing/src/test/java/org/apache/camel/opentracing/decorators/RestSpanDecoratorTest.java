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

import java.util.Arrays;

import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.opentracing.SpanDecorator;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RestSpanDecoratorTest {

    @Test
    public void testGetOperation() {
        String path = "/persons/{personId}";
        String uri = "rest://put:/persons:/%7BpersonId%7D?routeId=route4";

        Endpoint endpoint = Mockito.mock(Endpoint.class);
        Exchange exchange = Mockito.mock(Exchange.class);

        Mockito.when(endpoint.getEndpointUri()).thenReturn(uri);
        Mockito.when(exchange.getFromEndpoint()).thenReturn(endpoint);

        SpanDecorator decorator = new RestSpanDecorator();

        assertEquals(path, decorator.getOperationName(exchange, endpoint));
    }

    @Test
    public void testGetParameters() {
        assertEquals(Arrays.asList("id1", "id2"), RestSpanDecorator.getParameters("/context/{id1}/{id2}"));
    }

    @Test
    public void testGetParametersNone() {
        assertTrue(RestSpanDecorator.getParameters("rest://put:/persons/hello/world?routeId=route4").isEmpty());
    }

    @Test
    public void testPreStringParameter() {
        testParameter("strParam", "strValue");
    }

    @Test
    public void testPreNumberParameter() {
        testParameter("numParam", 5.6);
    }

    @Test
    public void testPreBooleanParameter() {
        testParameter("boolParam", Boolean.TRUE);
    }

    protected void testParameter(String paramName, Object paramValue) {
        Endpoint endpoint = Mockito.mock(Endpoint.class);
        Exchange exchange = Mockito.mock(Exchange.class);
        Message message = Mockito.mock(Message.class);

        Mockito.when(endpoint.getEndpointUri()).thenReturn("rest://put:/context:/%7B" + paramName + "%7D?routeId=route4");
        Mockito.when(exchange.getFromEndpoint()).thenReturn(endpoint);
        Mockito.when(exchange.getIn()).thenReturn(message);
        Mockito.when(message.getHeader(paramName)).thenReturn(paramValue);

        SpanDecorator decorator = new RestSpanDecorator();

        MockTracer tracer = new MockTracer();
        MockSpan span = tracer.buildSpan("TestSpan").start();

        decorator.pre(span, exchange, endpoint);

        assertEquals(paramValue, span.tags().get(paramName));
    }

    @Test
    public void testGetPath() {
        assertEquals("/persons/{personId}", RestSpanDecorator.getPath("rest://put:/persons:/%7BpersonId%7D?routeId=route4"));
    }
}
