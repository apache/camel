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
package org.apache.camel.component.rest.swagger;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.impl.DefaultMessage;
import org.apache.camel.spi.RestProducerFactory;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyMapOf;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RestSwaggerProducerTest {

    CamelContext camelContext = mock(CamelContext.class);

    RestSwaggerComponent component = new RestSwaggerComponent();

    RestSwaggerEndpoint endpoint = new RestSwaggerEndpoint("rest-swagger:operation", "operation", component);

    Exchange exchange = new DefaultExchange(endpoint);

    RestProducerFactory restProducerFactory = mock(RestProducerFactory.class);

    @Before
    public void setupMocks() throws Exception {
        when(restProducerFactory.createProducer(any(CamelContext.class), anyString(), anyString(), anyString(),
            anyString(), anyString(), anyString(), anyString(), anyMapOf(String.class, Object.class)))
                .thenReturn(mock(Producer.class));

        endpoint.setCamelContext(camelContext);
    }

    @Test
    public void shouldCreateProducersFromUriTemplates() throws Exception {
        final RestSwaggerProducer producer = new RestSwaggerProducer(endpoint, restProducerFactory,
            "https://api.example.com/{a}", "GET", "/{b}");

        final DefaultMessage message = new DefaultMessage();
        message.setHeader("a", "base");
        message.setHeader("b", "path");

        exchange.setIn(message);

        producer.process(exchange);

        verify(restProducerFactory).createProducer(same(camelContext), eq("https://api.example.com"), eq("GET"),
            eq("/base"), eq("/path"), anyString(), anyString(), anyString(), anyMapOf(String.class, Object.class));
    }

    @Test
    public void shouldCreateSimpleProducers() throws Exception {
        final RestSwaggerProducer producer = new RestSwaggerProducer(endpoint, restProducerFactory,
            "https://api.example.com/base", "GET", "/path");

        producer.process(exchange);

        verify(restProducerFactory).createProducer(same(camelContext), eq("https://api.example.com"), eq("GET"),
            eq("/base"), eq("/path"), anyString(), anyString(), anyString(), anyMapOf(String.class, Object.class));
    }
}
