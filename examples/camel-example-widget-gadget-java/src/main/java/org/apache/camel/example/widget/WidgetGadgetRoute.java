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
package org.apache.camel.example.widget;

import org.apache.camel.Endpoint;
import org.apache.camel.Predicate;
import org.apache.camel.builder.RouteBuilder;

/**
 * The widget and gadget route that pickup incoming orders from the <tt>newOrder</tt> queue
 * and route the orders to either the widget or gadget inventory system.
 */
public class WidgetGadgetRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        // you can define the endpoints and predicates here
        // it is more common to inline the endpoints and predicates in the route
        // as shown in the CreateOrderRoute

        Endpoint newOrder = endpoint("activemq:queue:newOrder");
        Predicate isWidget = xpath("/order/product = 'widget'");
        Endpoint widget = endpoint("activemq:queue:widget");
        Endpoint gadget = endpoint("activemq:queue:gadget");

        from(newOrder)
            .choice()
                .when(isWidget)
                    .to("log:widget") // add a log so we can see this happening in the shell
                    .to(widget)
                .otherwise()
                    .to("log:gadget") // add a log so we can see this happening in the shell
                    .to(gadget);
    }
}
