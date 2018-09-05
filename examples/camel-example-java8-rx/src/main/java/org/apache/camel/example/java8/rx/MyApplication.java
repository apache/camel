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
package org.apache.camel.example.java8.rx;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.main.Main;
import org.apache.camel.main.MainListenerSupport;
import org.apache.camel.rx.ReactiveCamel;
import rx.Observable;

public class MyApplication extends Main {

    public static void main(String[] args) throws Exception {
        Main main = new Main();
        main.addMainListener(new MyCamelContextConfigurer());
        main.addRouteBuilder(new MyRouteBuilder());
        main.run(args);
    }

    // ********************
    // Helpers
    // ********************

    private static class MyRouteBuilder extends RouteBuilder {
        @Override
        public void configure() throws Exception {
            from("timer:rx?period=1000")
                .routeId("orders")
                .transform().exchange(MyOrder::new)
                .to("seda:orders");

            from("seda:large-orders")
                .routeId("large-orders")
                .log("Got ${body}");
        }
    }

    private static class MyCamelContextConfigurer extends MainListenerSupport {
        @Override
        public void configure(final CamelContext context) {
            ReactiveCamel rx = new ReactiveCamel(context);

            Observable<String> orders = rx.toObservable("seda:orders", MyOrder.class)
                .filter(o -> o.getAmount() > 2)
                .map(o -> o.toString());

            rx.sendTo(orders, "seda:large-orders");
        }
    }

    // ********************
    // Model
    // ********************

    private static class MyOrder {
        private final String id;
        private final double amount;

        MyOrder(Exchange e) {
            this.amount = e.getProperty(Exchange.TIMER_COUNTER, Integer.class) % 5;
            this.id = e.getProperty(Exchange.TIMER_COUNTER, String.class);
        }

        @Override
        public String toString() {
            return "Order[id " + id + ", amount " + amount + "]";
        }

        public double getAmount() {
            return amount;
        }

        public String getId() {
            return id;
        }
    }
}
