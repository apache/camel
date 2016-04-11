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
package org.apache.camel.component.http;

import java.io.IOException;

import org.apache.camel.Exchange;
import org.apache.camel.http.common.HttpOperationFailedException;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.commons.httpclient.HttpMethod;
import org.junit.Test;

import static org.apache.camel.component.http.HttpMethods.GET;
import static org.apache.camel.component.http.HttpMethods.POST;

/**
 * Unit test to verify the algorithm for selecting either GET or POST.
 */
public class HttpProducerSelectMethodTest extends CamelTestSupport {

    @Test
    public void testNoDataDefaultIsGet() throws Exception {
        HttpComponent component = context.getComponent("http", HttpComponent.class);
        
        HttpEndpoint endpoiont = (HttpEndpoint) component.createEndpoint("http://www.google.com");
        MyHttpProducer producer = new MyHttpProducer(endpoiont, "GET", null);

        Exchange exchange = producer.createExchange();
        exchange.getIn().setBody(null);
        try {
            producer.process(exchange);
            fail("Should have thrown HttpOperationFailedException");
        } catch (HttpOperationFailedException e) {
            assertEquals(500, e.getStatusCode());
        }
        producer.stop();
    }

    @Test
    public void testDataDefaultIsPost() throws Exception {
        HttpComponent component = context.getComponent("http", HttpComponent.class);

        HttpEndpoint endpoiont = (HttpEndpoint) component.createEndpoint("http://www.google.com");
        MyHttpProducer producer = new MyHttpProducer(endpoiont, "POST", null);

        Exchange exchange = producer.createExchange();
        exchange.getIn().setBody("This is some data to post");
        try {
            producer.process(exchange);
            fail("Should have thrown HttpOperationFailedException");
        } catch (HttpOperationFailedException e) {
            assertEquals(500, e.getStatusCode());
        }
        producer.stop();
    }

    @Test
    public void testWithMethodPostInHeader() throws Exception {
        HttpComponent component = context.getComponent("http", HttpComponent.class);

        HttpEndpoint endpoiont = (HttpEndpoint) component.createEndpoint("http://www.google.com");
        MyHttpProducer producer = new MyHttpProducer(endpoiont, "POST", null);

        Exchange exchange = producer.createExchange();
        exchange.getIn().setBody("");
        exchange.getIn().setHeader(Exchange.HTTP_METHOD, POST);
        try {
            producer.process(exchange);
            fail("Should have thrown HttpOperationFailedException");
        } catch (HttpOperationFailedException e) {
            assertEquals(500, e.getStatusCode());
        }
        producer.stop();
    }

    @Test
    public void testWithMethodGetInHeader() throws Exception {
        HttpComponent component = context.getComponent("http", HttpComponent.class);

        HttpEndpoint endpoiont = (HttpEndpoint) component.createEndpoint("http://www.google.com");
        MyHttpProducer producer = new MyHttpProducer(endpoiont, "GET", null);

        Exchange exchange = producer.createExchange();
        exchange.getIn().setBody("");
        exchange.getIn().setHeader(Exchange.HTTP_METHOD, GET);
        try {
            producer.process(exchange);
            fail("Should have thrown HttpOperationFailedException");
        } catch (HttpOperationFailedException e) {
            assertEquals(500, e.getStatusCode());
        }
        producer.stop();
    }

    @Test
    public void testWithEndpointQuery() throws Exception {
        HttpComponent component = context.getComponent("http", HttpComponent.class);

        HttpEndpoint endpoiont = (HttpEndpoint) component.createEndpoint("http://www.google.com?q=Camel");
        MyHttpProducer producer = new MyHttpProducer(endpoiont, "GET", "q=Camel");

        Exchange exchange = producer.createExchange();
        // no body should be GET
        exchange.getIn().setBody(null);
        try {
            producer.process(exchange);
            fail("Should have thrown HttpOperationFailedException");
        } catch (HttpOperationFailedException e) {
            assertEquals(500, e.getStatusCode());
        }
        producer.stop();
    }

    @Test
    public void testWithQueryInHeader() throws Exception {
        HttpComponent component = context.getComponent("http", HttpComponent.class);

        HttpEndpoint endpoiont = (HttpEndpoint) component.createEndpoint("http://www.google.com");
        MyHttpProducer producer = new MyHttpProducer(endpoiont, "GET", "q=Camel");

        Exchange exchange = producer.createExchange();
        // no body should be GET
        exchange.getIn().setBody(null);
        exchange.getIn().setHeader(Exchange.HTTP_QUERY, "q=Camel");
        try {
            producer.process(exchange);
            fail("Should have thrown HttpOperationFailedException");
        } catch (HttpOperationFailedException e) {
            assertEquals(500, e.getStatusCode());
        }
        producer.stop();
    }

    @Test
    public void testWithQueryInHeaderOverrideEndpoint() throws Exception {
        HttpComponent component = context.getComponent("http", HttpComponent.class);

        HttpEndpoint endpoiont = (HttpEndpoint) component.createEndpoint("http://www.google.com?q=Donkey");
        MyHttpProducer producer = new MyHttpProducer(endpoiont, "GET", "q=Camel");

        Exchange exchange = producer.createExchange();
        // no body should be GET
        exchange.getIn().setBody(null);
        exchange.getIn().setHeader(Exchange.HTTP_QUERY, "q=Camel");
        try {
            producer.process(exchange);
            fail("Should have thrown HttpOperationFailedException");
        } catch (HttpOperationFailedException e) {
            assertEquals(500, e.getStatusCode());
        }
        producer.stop();
    }

    private static class MyHttpProducer extends HttpProducer {
        private String name;
        private String queryString;

        MyHttpProducer(HttpEndpoint endpoint, String name, String queryString) {
            super(endpoint);
            this.name = name;
            this.queryString = queryString;
        }

        @Override
        protected int executeMethod(HttpMethod method) throws IOException {
            // do the assertion what to expected either GET or POST
            assertEquals(name, method.getName());
            assertEquals(queryString, method.getQueryString());
            // return 500 to not extract response as we dont have any
            return 500;
        }
    }
}
