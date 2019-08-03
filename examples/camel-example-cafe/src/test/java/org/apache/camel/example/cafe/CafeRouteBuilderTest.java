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
package org.apache.camel.example.cafe;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.example.cafe.stuff.Barista;
import org.apache.camel.example.cafe.stuff.CafeAggregationStrategy;
import org.apache.camel.example.cafe.stuff.OrderSplitter;
import org.apache.camel.example.cafe.test.TestDrinkRouter;
import org.apache.camel.example.cafe.test.TestWaiter;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class CafeRouteBuilderTest extends CamelTestSupport {
    protected TestWaiter waiter = new TestWaiter();
    protected TestDrinkRouter driverRouter = new TestDrinkRouter();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        bindBeans(context.getRegistry());
        return context;
    }

    protected void bindBeans(Registry registry) throws Exception {
        registry.bind("drinkRouter", driverRouter);
        registry.bind("orderSplitter", new OrderSplitter());
        registry.bind("barista", new Barista());
        registry.bind("waiter", waiter);
        registry.bind("aggregatorStrategy", new CafeAggregationStrategy());
    }

    @Test
    public void testSplitter() throws InterruptedException {
        MockEndpoint coldDrinks = context.getEndpoint("mock:coldDrinks", MockEndpoint.class);
        MockEndpoint hotDrinks = context.getEndpoint("mock:hotDrinks", MockEndpoint.class);
        
        Order order = new Order(1);
        order.addItem(DrinkType.ESPRESSO, 2, true);
        order.addItem(DrinkType.CAPPUCCINO, 2, false);
        
        coldDrinks.expectedBodiesReceived(new OrderItem(order, DrinkType.ESPRESSO, 2, true));
        hotDrinks.expectedBodiesReceived(new OrderItem(order, DrinkType.CAPPUCCINO, 2, false));
        
        template.sendBody("direct:cafe", order);
        
        assertMockEndpointsSatisfied();
    }
    
    @Test
    public void testCafeRoute() throws Exception {
        driverRouter.setTestModel(false);
        List<Drink> drinks = new ArrayList<>();
        Order order = new Order(2);
        order.addItem(DrinkType.ESPRESSO, 2, true);
        order.addItem(DrinkType.CAPPUCCINO, 4, false);
        order.addItem(DrinkType.LATTE, 4, false);
        order.addItem(DrinkType.MOCHA, 2, false);
        
        drinks.add(new Drink(2, DrinkType.ESPRESSO, true, 2));
        drinks.add(new Drink(2, DrinkType.CAPPUCCINO, false, 4));
        drinks.add(new Drink(2, DrinkType.LATTE, false, 4));
        drinks.add(new Drink(2, DrinkType.MOCHA, false, 2));
        
        waiter.setVerfiyDrinks(drinks);
        
        template.sendBody("direct:cafe", order);

        // wait enough time to let the aggregate complete
        Thread.sleep(10000);
        
        waiter.verifyDrinks();
    }
    
    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new CafeRouteBuilder();
    }

}
