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

import org.apache.camel.RouteTemplateContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

public class KameletLocalBeanLanguageTest extends CamelTestSupport {

    @Test
    public void testOne() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Hi John we are going to Moes");

        template.sendBody("direct:moe", "John");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testTwo() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Hi Jack we are going to Shamrock",
                "Hi Mary we are going to Moes");

        template.sendBody("direct:shamrock", "Jack");
        template.sendBody("direct:moe", "Mary");

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
                        .templateParameter("bar") // name of bar
                        .templateBean("myBar", "bean",
                                "org.apache.camel.component.kamelet.KameletLocalBeanLanguageTest?method=createMyBar")
                        .from("kamelet:source")
                        // must use {{myBar}} to refer to the local bean
                        .to("bean:{{myBar}}");

                from("direct:shamrock")
                        .kamelet("whereTo?bar=Shamrock")
                        .to("mock:result");

                from("direct:moe")
                        .kamelet("whereTo?bar=Moes")
                        .to("mock:result");
            }
        };
    }

    public static MyBar createMyBar(RouteTemplateContext rtc) {
        // use rtc as parameter so we can use that to get the template parameters when creating the local bean
        // the method can be static or not (static is possible best)
        return new MyBar(rtc.getProperty("bar", String.class));
    }

    private static class MyBar {

        private final String bar;

        public MyBar(String bar) {
            this.bar = bar;
        }

        public String where(String name) {
            return "Hi " + name + " we are going to " + bar;
        }
    }

}
