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
package org.apache.camel.zipkin;

import brave.Span;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import zipkin2.reporter.Reporter;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ZipkinTracerTest extends CamelTestSupport {

    private ZipkinTracer zipkin;

    private Endpoint endpoint;

    protected void setSpanReporter(ZipkinTracer zipkin) {
        zipkin.setSpanReporter(Reporter.NOOP);
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        zipkin = new ZipkinTracer();
        setSpanReporter(zipkin);
        // attaching ourself to CamelContext
        zipkin.init(context);
        return context;

    }

    @Test
    public void testJmsProducerEndpoint() {
        endpoint = mock(Endpoint.class);
        when(endpoint.getEndpointBaseUri()).thenReturn("jms:queue");
        Span.Kind spankind = zipkin.getProducerComponentSpanKind(endpoint);
        Assertions.assertThat(spankind).isEqualTo(Span.Kind.PRODUCER);
    }

    @Test
    public void testKafkaProducerEndpoint() {
        endpoint = mock(Endpoint.class);
        when(endpoint.getEndpointBaseUri()).thenReturn("kafka:topic");
        Span.Kind spankind = zipkin.getProducerComponentSpanKind(endpoint);
        Assertions.assertThat(spankind).isEqualTo(Span.Kind.PRODUCER);
    }

    @Test
    public void testSJMSProducerEndpoint() {
        endpoint = mock(Endpoint.class);
        when(endpoint.getEndpointBaseUri()).thenReturn("sjms:queue");
        Span.Kind spankind = zipkin.getProducerComponentSpanKind(endpoint);
        Assertions.assertThat(spankind).isEqualTo(Span.Kind.PRODUCER);
    }

    @Test
    public void testActiveMQProducerEndpoint() {
        endpoint = mock(Endpoint.class);
        when(endpoint.getEndpointBaseUri()).thenReturn("activemq:queue");
        Span.Kind spankind = zipkin.getProducerComponentSpanKind(endpoint);
        Assertions.assertThat(spankind).isEqualTo(Span.Kind.PRODUCER);
    }

    @Test
    public void testNonProducerEndpoint() {
        endpoint = mock(Endpoint.class);
        when(endpoint.getEndpointBaseUri()).thenReturn("http:www");
        Span.Kind spankind = zipkin.getProducerComponentSpanKind(endpoint);
        Assertions.assertThat(spankind).isEqualTo(Span.Kind.CLIENT);
    }

    @Test
    public void testNonProducerInvalidEndpoint() {
        endpoint = mock(Endpoint.class);
        when(endpoint.getEndpointBaseUri()).thenReturn("jms&queue");
        Span.Kind spankind = zipkin.getProducerComponentSpanKind(endpoint);
        Assertions.assertThat(spankind).isEqualTo(Span.Kind.CLIENT);
    }

    @Test
    public void testJmsConsumerEndpoint() {
        endpoint = mock(Endpoint.class);
        when(endpoint.getEndpointBaseUri()).thenReturn("jms:queue");
        Span.Kind spankind = zipkin.getConsumerComponentSpanKind(endpoint);
        Assertions.assertThat(spankind).isEqualTo(Span.Kind.CONSUMER);
    }

    @Test
    public void testKafkaConsumerEndpoint() {
        endpoint = mock(Endpoint.class);
        when(endpoint.getEndpointBaseUri()).thenReturn("kafka:topic");
        Span.Kind spankind = zipkin.getConsumerComponentSpanKind(endpoint);
        Assertions.assertThat(spankind).isEqualTo(Span.Kind.CONSUMER);
    }

    @Test
    public void testSJMSConsumerEndpoint() {
        endpoint = mock(Endpoint.class);
        when(endpoint.getEndpointBaseUri()).thenReturn("sjms:queue");
        Span.Kind spankind = zipkin.getConsumerComponentSpanKind(endpoint);
        Assertions.assertThat(spankind).isEqualTo(Span.Kind.CONSUMER);
    }

    @Test
    public void testActiveMQConsumerEndpoint() {
        endpoint = mock(Endpoint.class);
        when(endpoint.getEndpointBaseUri()).thenReturn("activemq:queue");
        Span.Kind spankind = zipkin.getConsumerComponentSpanKind(endpoint);
        Assertions.assertThat(spankind).isEqualTo(Span.Kind.CONSUMER);
    }

    @Test
    public void testNonConsumerEndpoint() {
        endpoint = mock(Endpoint.class);
        when(endpoint.getEndpointBaseUri()).thenReturn("rest:customer?");
        Span.Kind spankind = zipkin.getConsumerComponentSpanKind(endpoint);
        Assertions.assertThat(spankind).isEqualTo(Span.Kind.SERVER);
    }

    @Test
    public void testNonConsumerInvalidEndpoint() {
        endpoint = mock(Endpoint.class);
        when(endpoint.getEndpointBaseUri()).thenReturn("rest&customer?");
        Span.Kind spankind = zipkin.getConsumerComponentSpanKind(endpoint);
        Assertions.assertThat(spankind).isEqualTo(Span.Kind.SERVER);
    }

}
