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
package org.apache.camel.component.mina;

import java.lang.reflect.Field;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.mina.common.IoSession;
import org.junit.Test;

/**
 * For unit testing the <tt>logger</tt> option.
 */
public class MinaLoggerOptionTest extends BaseMinaTest {

    @Test
    public void testLoggerOptionTrue() throws Exception {
        final String uri = "mina:tcp://localhost:{{port}}?textline=true&minaLogger=true&sync=false";
        context.addRoutes(new RouteBuilder() {
            public void configure() throws Exception {
                from(uri).to("mock:result");
            }
        });

        MockEndpoint mock = this.getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        Endpoint endpoint = context.getEndpoint(uri);
        Exchange exchange = endpoint.createExchange();
        Producer producer = endpoint.createProducer();
        producer.start();

        // set input and execute it
        exchange.getIn().setBody("Hello World");
        producer.process(exchange);

        Field field = producer.getClass().getDeclaredField("session");
        field.setAccessible(true);
        IoSession session = (IoSession) field.get(producer);
        assertTrue("There should be a logger filter", session.getFilterChain().contains("logger"));

        producer.stop();

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testLoggerOptionFalse() throws Exception {
        final String uri = "mina:tcp://localhost:{{port}}?textline=true&minaLogger=false&sync=false";
        context.addRoutes(new RouteBuilder() {
            public void configure() throws Exception {
                from(uri).to("mock:result");
            }
        });

        MockEndpoint mock = this.getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        Endpoint endpoint = context.getEndpoint(uri);
        Exchange exchange = endpoint.createExchange();
        Producer producer = endpoint.createProducer();
        producer.start();

        // set input and execute it
        exchange.getIn().setBody("Hello World");
        producer.process(exchange);

        Field field = producer.getClass().getDeclaredField("session");
        field.setAccessible(true);
        IoSession session = (IoSession) field.get(producer);
        assertFalse("There should NOT be a logger filter", session.getFilterChain().contains("logger"));

        producer.stop();

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testNoLoggerOption() throws Exception {
        final String uri = "mina:tcp://localhost:{{port}}?textline=true&sync=false";
        context.addRoutes(new RouteBuilder() {
            public void configure() throws Exception {
                from(uri).to("mock:result");
            }
        });

        MockEndpoint mock = this.getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        Endpoint endpoint = context.getEndpoint(uri);
        Exchange exchange = endpoint.createExchange();
        Producer producer = endpoint.createProducer();
        producer.start();

        // set input and execute it
        exchange.getIn().setBody("Hello World");
        producer.process(exchange);

        Field field = producer.getClass().getDeclaredField("session");
        field.setAccessible(true);
        IoSession session = (IoSession) field.get(producer);
        assertFalse("There should NOT default be a logger filter", session.getFilterChain().contains("logger"));

        producer.stop();

        assertMockEndpointsSatisfied();
    }
}
