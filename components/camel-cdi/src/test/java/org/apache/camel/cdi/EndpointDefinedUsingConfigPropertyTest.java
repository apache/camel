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
package org.apache.camel.cdi;

import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.cdi.store.Item;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.deltaspike.core.api.config.ConfigProperty;
import org.junit.Test;

public class EndpointDefinedUsingConfigPropertyTest extends CdiContextTestSupport {

    @Inject @ConfigProperty(name = "directEndpoint")
    String directInjectEndpoint;

    @EndpointInject(uri = "mock:result")
    MockEndpoint mockResultEndpoint;

    @Produce(uri = "direct:inject")
    ProducerTemplate myProducer;

    @Test
    public void beanShouldBeInjected() throws InterruptedException {
        mockResultEndpoint.expectedMessageCount(1);
        myProducer.sendBody("hello");

        assertMockEndpointsSatisfied();

        Exchange exchange = mockResultEndpoint.getExchanges().get(0);
        List<?> results = exchange.getIn().getBody(List.class);
        List<Item> expected = itemsExpected();
        assertNotNull(results);
        assertNotNull(expected);
        assertEquals(expected.size(), results.size());
        assertEquals(expected, results);
    }

    private List<Item> itemsExpected() {
        List<Item> products = new ArrayList<Item>();
        for (int i = 1; i < 10; i++) {
            products.add(new Item("Item-" + i, 1500L * i));
        }
        return products;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(directInjectEndpoint)
                    .bean("shoppingBean", "listAllProducts")
                    .to(mockResultEndpoint);
            }
        };
    }
}
