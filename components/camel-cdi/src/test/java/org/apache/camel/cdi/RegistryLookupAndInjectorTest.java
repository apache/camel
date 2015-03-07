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

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.cdi.store.Item;
import org.apache.camel.cdi.store.ShoppingBean;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class RegistryLookupAndInjectorTest extends CdiContextTestSupport {

    private MockEndpoint resultEndpoint;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        resultEndpoint = getMockEndpoint("mock:result");
    }

    @Test
    public void shouldLookupBeanByName() throws InterruptedException {
        resultEndpoint.expectedMessageCount(1);
        template.sendBody("direct:injectByName", "hello");

        assertMockEndpointsSatisfied();

        Exchange exchange = resultEndpoint.getExchanges().get(0);
        List<?> results = exchange.getIn().getBody(List.class);
        List<Item> expected = itemsExpected();
        assertNotNull(results);
        assertNotNull(expected);
        assertEquals(expected.size(), results.size());
        assertEquals(expected, results);
    }

    @Test
    public void shouldLookupBeanByTypeAndInjectFields() throws InterruptedException {
        resultEndpoint.expectedMessageCount(1);
        template.sendBody("direct:injectByType", "hello");

        assertMockEndpointsSatisfied();

        Exchange exchange = resultEndpoint.getExchanges().get(0);
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
                from("direct:injectByName")
                    .bean("shoppingBean", "listAllProducts")
                    .to("mock:result");
                from("direct:injectByType")
                    .bean(ShoppingBean.class, "listAllProducts")
                     .to("mock:result");
            }
        };
    }
}
