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

import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.example.cafe.stuff.Barista;
import org.apache.camel.example.cafe.stuff.CafeAggregationStrategy;
import org.apache.camel.example.cafe.stuff.DrinkRouter;
import org.apache.camel.example.cafe.stuff.OrderSplitter;
import org.apache.camel.example.cafe.stuff.Waiter;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.spi.Registry;

/**
 * A simple example router from Cafe Demo
 */
public class CafeRouteBuilder extends RouteBuilder {
    
    public static void main(String[] args) throws Exception {
        CafeRouteBuilder builder = new CafeRouteBuilder();
        builder.runCafeRouteDemo();
    }
    
    protected void bindBeans(Registry registry) throws Exception {
        registry.bind("drinkRouter", new DrinkRouter());
        registry.bind("orderSplitter", new OrderSplitter());
        registry.bind("barista", new Barista());
        registry.bind("waiter", new Waiter());
        registry.bind("aggregatorStrategy", new CafeAggregationStrategy());
    }
    
    public void runCafeRouteDemo() throws Exception {
        // create CamelContext
        DefaultCamelContext camelContext = new DefaultCamelContext();

        // bind beans to the Camel
        bindBeans(camelContext.getRegistry());

        // add the routes
        camelContext.addRoutes(this);

        // start Camel
        camelContext.start();

        // create a producer so we can send messages to Camel
        ProducerTemplate template = camelContext.createProducerTemplate();
        
        Order order = new Order(2);
        order.addItem(DrinkType.ESPRESSO, 2, true);
        order.addItem(DrinkType.CAPPUCCINO, 4, false);
        order.addItem(DrinkType.LATTE, 4, false);
        order.addItem(DrinkType.MOCHA, 2, false);
        
        template.sendBody("direct:cafe", order);

        // wait 6 seconds and stop
        Thread.sleep(6000);


        camelContext.stop();
        
    }
    //START SNIPPET: RouteConfig
    public void configure() {
        
        from("direct:cafe")
            .split().method("orderSplitter").to("direct:drink");
        
        from("direct:drink").recipientList().method("drinkRouter");
        
        from("seda:coldDrinks?concurrentConsumers=2").to("bean:barista?method=prepareColdDrink").to("direct:deliveries");
        from("seda:hotDrinks?concurrentConsumers=3").to("bean:barista?method=prepareHotDrink").to("direct:deliveries");
        
        from("direct:deliveries")
            .aggregate(new CafeAggregationStrategy()).method("waiter", "checkOrder").completionTimeout(5 * 1000L)
            .to("bean:waiter?method=prepareDelivery")
            .to("bean:waiter?method=deliverCafes");

    }
    //END SNIPPET: RouteConfig

}
