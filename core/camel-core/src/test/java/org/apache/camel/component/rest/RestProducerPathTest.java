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
package org.apache.camel.component.rest;

import java.util.HashMap;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.Assert;
import org.junit.Test;

public class RestProducerPathTest {
    private final RestComponent restComponent;

    public RestProducerPathTest() {
        DefaultCamelContext context = new DefaultCamelContext();
        context.addComponent("mock-rest", new RestEndpointTest.MockRest());

        restComponent = new RestComponent();
        restComponent.setCamelContext(context);
    }

    private RestProducer createProducer(String uri) throws Exception {
        final RestEndpoint restEndpoint = (RestEndpoint) restComponent.createEndpoint(uri);
        restEndpoint.setConsumerComponentName("mock-rest");
        restEndpoint.setParameters(new HashMap<>());
        restEndpoint.setHost("http://localhost");
        restEndpoint.setBindingMode("json");

        return (RestProducer) restEndpoint.createProducer();
    }

    @Test
    public void testEmptyParam() throws Exception {
        RestProducer producer = createProducer("rest:get:list//{id}");
        Exchange exchange = producer.createExchange();
        Message message = exchange.getIn();
        message.setHeader("id", 1);

        producer.process(exchange);

        String actual = (String) message.getHeader(Exchange.REST_HTTP_URI);
        Assert.assertEquals("http://localhost/list//1", actual);
    }

    @Test
    public void testNoHeaders() throws Exception {
        RestProducer producer = createProducer("rest:get:list/{id}_{val}");
        Exchange exchange = producer.createExchange();
        Message message = exchange.getIn();

        producer.process(exchange);

        String actual = (String) message.getHeader(Exchange.REST_HTTP_URI);
        Assert.assertNull(actual);
    }

    @Test
    public void testMissingHeader() throws Exception {
        RestProducer producer = createProducer("rest:get:list/{id}/{val}");
        Exchange exchange = producer.createExchange();
        Message message = exchange.getIn();
        message.setHeader("id", 1);

        producer.process(exchange);

        String actual = (String) message.getHeader(Exchange.REST_HTTP_URI);
        // Backward compatibility: if one of the params is resolved
        Assert.assertEquals("http://localhost/list/1/{val}", actual);
    }

    @Test
    public void testMissingHeaderSingleParam() throws Exception {
        RestProducer producer = createProducer("rest:get:list/{id}_{val}");
        Exchange exchange = producer.createExchange();
        Message message = exchange.getIn();
        message.setHeader("id", 1);

        producer.process(exchange);

        String actual = (String) message.getHeader(Exchange.REST_HTTP_URI);
        Assert.assertNull(actual);
    }

    @Test
    public void testMissingStartCurlyBrace() throws Exception {
        RestProducer producer = createProducer("rest:get:list/{id}_val}");
        Exchange exchange = producer.createExchange();
        Message message = exchange.getIn();
        message.setHeader("id", 1);
        message.setHeader("val", "test");

        producer.process(exchange);

        String actual = (String) message.getHeader(Exchange.REST_HTTP_URI);
        Assert.assertNull(actual);
    }

    @Test
    public void testSingleMissingStartCurlyBrace() throws Exception {
        RestProducer producer = createProducer("rest:get:list/id}");
        Exchange exchange = producer.createExchange();
        Message message = exchange.getIn();
        message.setHeader("id", 1);

        producer.process(exchange);

        String actual = (String) message.getHeader(Exchange.REST_HTTP_URI);
        Assert.assertNull(actual);
    }

    @Test
    public void testSingleMissingEndCurlyBrace() throws Exception {
        RestProducer producer = createProducer("rest:get:list/{id");
        Exchange exchange = producer.createExchange();
        Message message = exchange.getIn();
        message.setHeader("id", 1);

        producer.process(exchange);

        String actual = (String) message.getHeader(Exchange.REST_HTTP_URI);
        Assert.assertNull(actual);
    }

    @Test
    public void testMissingEndCurlyBrace() throws Exception {
        RestProducer producer = createProducer("rest:get:list/{id_{val}");
        Exchange exchange = producer.createExchange();
        Message message = exchange.getIn();
        message.setHeader("id", 1);
        message.setHeader("val", "test");

        producer.process(exchange);

        String actual = (String) message.getHeader(Exchange.REST_HTTP_URI);
        Assert.assertNull(actual);
    }

    @Test
    public void testSingleParam() throws Exception {
        RestProducer producer = createProducer("rest:get:list/{id}");
        Exchange exchange = producer.createExchange();
        Message message = exchange.getIn();
        message.setHeader("id", 1);

        producer.process(exchange);

        String actual = (String) message.getHeader(Exchange.REST_HTTP_URI);
        Assert.assertEquals("http://localhost/list/1", actual);
    }

    @Test
    public void testUnderscoreSeparator() throws Exception {
        RestProducer producer = createProducer("rest:get:list/{id}_{val}");
        Exchange exchange = producer.createExchange();
        Message message = exchange.getIn();
        message.setHeader("id", 1);
        message.setHeader("val", "test");

        producer.process(exchange);

        String actual = (String) message.getHeader(Exchange.REST_HTTP_URI);
        Assert.assertEquals("http://localhost/list/1_test", actual);
    }

    @Test
    public void testDotSeparator() throws Exception {
        RestProducer producer = createProducer("rest:get:items/item.{content-type}");
        Exchange exchange = producer.createExchange();
        Message message = exchange.getIn();
        message.setHeader("content-type", "xml");

        producer.process(exchange);

        String actual = (String) message.getHeader(Exchange.REST_HTTP_URI);
        Assert.assertEquals("http://localhost/items/item.xml", actual);
    }
}
