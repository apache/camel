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

package org.apache.camel.main;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

class MainTemplatedRouteGroupTest {

    @Test
    void testMain() {
        Main main = new Main();
        main.configure().addRoutesBuilder(new RouteBuilder() {
            @Override
            public void configure() {
                routeTemplate("myTemplate")
                        .templateParameter("foo")
                        .templateParameter("bar")
                        .from("direct:{{foo}}")
                        .choice()
                        .when(header("foo"))
                        .log("${body}")
                        .id("myLog")
                        .otherwise()
                        .to("mock:{{bar}}")
                        .id("end");

                templatedRoute("myTemplate")
                        .routeId("my-route")
                        .group("cheese")
                        .parameter("foo", "fooVal")
                        .parameter("bar", "barVal");

                templatedRoute("myTemplate")
                        .routeId("my-route2")
                        .group("cheese")
                        .parameter("foo", "fooVal2")
                        .parameter("bar", "barVal2");

                templatedRoute("myTemplate")
                        .routeId("my-route3")
                        .group("cake")
                        .parameter("foo", "fooVal3")
                        .parameter("bar", "barVal3");
            }
        });

        main.start();

        CamelContext context = main.getCamelContext();
        assertEquals(3, context.getRoutes().size());
        assertEquals(2, context.getRoutesByGroup("cheese").size());
        assertEquals(1, context.getRoutesByGroup("cake").size());

        main.stop();
    }
}
