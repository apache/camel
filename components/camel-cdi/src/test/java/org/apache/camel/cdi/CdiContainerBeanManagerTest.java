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
import javax.enterprise.inject.spi.BeanManager;
import javax.inject.Inject;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.cdi.store.Item;
import org.apache.camel.cdi.store.ShoppingBean;
import org.apache.camel.component.cdi.CdiBeanRegistry;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.webbeans.cditest.CdiTestContainer;
import org.apache.webbeans.cditest.CdiTestContainerLoader;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class CdiContainerBeanManagerTest extends CamelTestSupport {

    private CdiTestContainer cdiContainer;

    @Inject
    private ShoppingBean shoppingBean;

    @Before
    public void setUp() throws Exception {
        cdiContainer = CdiTestContainerLoader.getCdiContainer();
        cdiContainer.bootContainer();
        cdiContainer.startContexts();

        super.setUp();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        BeanManager beanManager = cdiContainer.getBeanManager();
        context = new DefaultCamelContext(new CdiBeanRegistry(beanManager));
        return context;
    }

    @After
    public void shutDown() throws Exception {
        cdiContainer.shutdownContainer();
        context.stop();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testInjection() throws InterruptedException {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBody("direct:inject", "hello");

        assertMockEndpointsSatisfied();

        Exchange exchange = mock.getExchanges().get(0);
        List<Item> results = (List<Item>) exchange.getIn().getBody();

        Object[] items = results.toArray();
        Object[] itemsExpected = itemsExpected().toArray();
        for (int i = 0; i < items.length; ++i) {
            Item itemExpected = (Item) items[i];
            Item itemReceived = (Item) itemsExpected[i];
            assertEquals(itemExpected.getName(), itemReceived.getName());
            assertEquals(itemExpected.getPrice(), itemReceived.getPrice());
        }

        assertNotNull(results);
    }

    private ArrayList<Item> itemsExpected() {
        ArrayList<Item> products = new ArrayList<Item>();
        Item defaultItem = new Item();
        defaultItem.setName("Default Item");
        defaultItem.setPrice(1000L);

        for (int i = 1; i < 10; i++) {
            Item item = new Item("Item-" + i, i * 1500L);
            products.add(item);
        }

        return products;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:inject")
                        .beanRef("shoppingBean", "listAllProducts")
                        .to("mock:result");
            }
        };
    }

}
