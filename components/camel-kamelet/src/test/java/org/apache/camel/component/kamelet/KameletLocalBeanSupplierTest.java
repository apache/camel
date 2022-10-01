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
package org.apache.camel.component.kamelet;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

public class KameletLocalBeanSupplierTest extends CamelTestSupport {

    private final AtomicInteger counter = new AtomicInteger();

    @Test
    public void testSupplier() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello John go to number 2", "Hi Mary go to number 1");

        template.sendBody("direct:hello", "John");
        template.sendBody("direct:hi", "Mary");

        MockEndpoint.assertIsSatisfied(context);
    }

    // **********************************************
    //
    // test set-up
    //
    // **********************************************

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                routeTemplate("whereTo")
                        .templateParameter("foo")
                        .templateBean("myBar", () -> new MyStaticBar(counter.incrementAndGet()))
                        .from("kamelet:source")
                        // must use {{myBar}} to refer to the local bean
                        .setBody(simple("{{foo}} ${body}"))
                        .to("bean:{{myBar}}");

                from("direct:hi")
                        .kamelet("whereTo?foo=Hi")
                        .to("mock:result");

                from("direct:hello")
                        .kamelet("whereTo?foo=Hello")
                        .to("mock:result");
            }
        };
    }

    private class MyStaticBar {

        private int number;

        public MyStaticBar(int number) {
            this.number = number;
        }

        public String whereTo(String name) {
            return name + " go to number " + number;
        }

    }

}
