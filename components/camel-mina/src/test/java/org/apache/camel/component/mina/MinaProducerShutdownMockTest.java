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
import org.apache.mina.transport.socket.nio.SocketConnector;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Unit testing for using a MinaProducer that it can shutdown properly (CAMEL-395)
 */
public class MinaProducerShutdownMockTest extends BaseMinaTest {

    @Test
    public void testProducerShutdownTestingWithMock() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        SocketConnector mockConnector = mock(SocketConnector.class);

        // normal camel code to get a producer
        Endpoint endpoint = context.getEndpoint("mina:tcp://localhost:{{port}}?textline=true&sync=false");
        Exchange exchange = endpoint.createExchange();
        Producer producer = endpoint.createProducer();
        producer.start();

        // set input and execute it
        exchange.getIn().setBody("Hello World");
        producer.process(exchange);

        // insert our mock instead of real MINA IoConnector
        Field field = producer.getClass().getDeclaredField("connector");
        field.setAccessible(true);
        field.set(producer, mockConnector);

        // stop using our mock
        producer.stop();

        verify(mockConnector).setWorkerTimeout(0);

        assertMockEndpointsSatisfied();
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("mina:tcp://localhost:{{port}}?textline=true&sync=false").to("mock:result");
            }
        };
    }

}