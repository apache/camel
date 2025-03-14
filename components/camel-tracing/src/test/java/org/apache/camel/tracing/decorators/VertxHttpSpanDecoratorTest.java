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

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class VertxHttpSpanDecoratorTest {

    private VertxHttpSpanDecorator decorator;

    @BeforeEach
    public void before() {
        this.decorator = new VertxHttpSpanDecorator();
    }

    @Test
    public void testMethodInHttpMethodParam() {
        Endpoint endpoint = Mockito.mock(Endpoint.class);
        Exchange exchange = Mockito.mock(Exchange.class);
        Message message = Mockito.mock(Message.class);

        Mockito.when(endpoint.getEndpointUri()).thenReturn("vertx-http://localhost:8080/endpoint?httpMethod=POST");
        Mockito.when(exchange.getIn()).thenReturn(message);
        Mockito.when(message.getHeader(Exchange.HTTP_URI, String.class))
                .thenReturn("http://localhost:8080/endpoint?httpMethod=POST");

        assertEquals(AbstractHttpSpanDecorator.POST_METHOD,
                decorator.getHttpMethod(exchange, endpoint));
    }

    @Test
    public void testMethodInHttpMethodParamUsingHeader() {
        Endpoint endpoint = Mockito.mock(Endpoint.class);
        Exchange exchange = Mockito.mock(Exchange.class);
        Message message = Mockito.mock(Message.class);

        Mockito.when(endpoint.getEndpointUri()).thenReturn("vertx-http://localhost:8080/endpoint?httpMethod=POST");
        Mockito.when(exchange.getIn()).thenReturn(message);
        Mockito.when(message.getHeader(Exchange.HTTP_METHOD)).thenReturn(HttpMethods.GET);
        Mockito.when(message.getHeader(Exchange.HTTP_URI, String.class))
                .thenReturn("vertx-http://localhost:8080/endpoint?httpMethod=POST");

        assertEquals(AbstractHttpSpanDecorator.POST_METHOD,
                decorator.getHttpMethod(exchange, endpoint));
    }
}
