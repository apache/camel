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

import org.apache.camel.BeanInject;
import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelContext;
import org.apache.camel.PropertyInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Assert;
import org.junit.Test;

public class MainIoCNewRouteBuilderTest extends Assert {

    @Test
    public void testMainIoC() throws Exception {
        Main main = new Main();
        main.addConfiguration(new MyConfiguration());
        main.addRoutesBuilder(new MyRouteBuilder());
        main.start();

        CamelContext camelContext = main.getCamelContext();
        assertNotNull(camelContext);

        MockEndpoint endpoint = camelContext.getEndpoint("mock:results", MockEndpoint.class);
        endpoint.expectedBodiesReceived("World");

        main.getCamelTemplate().sendBody("direct:start", "<message>1</message>");

        endpoint.assertIsSatisfied();

        MainIoCNewRouteBuilderTest.MyConfiguration.MyCoolBean mcb = (MainIoCNewRouteBuilderTest.MyConfiguration.MyCoolBean) camelContext.getRegistry().lookupByName("MyCoolBean");
        assertNotNull(mcb);
        assertEquals("Tiger", mcb.getName());

        main.stop();
    }

    public static class MyConfiguration {

        @BeanInject
        private CamelContext camel;

        @BindToRegistry
        public static class MyCoolBean {

            private String name = "Tiger";

            public String getName() {
                return name;
            }

            public void setName(String name) {
                this.name = name;
            }
        }

        public void configure() {
            camel.getGlobalOptions().put("foo", "123");
        }
    }

    public static class MyBar {

        private final String name;

        public MyBar(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public static class MyRouteBuilder extends RouteBuilder {

        @BindToRegistry("bar")
        public MyBar createBar(@PropertyInject("hello") String hello) {
            return new MyBar(hello);
        }

        @Override
        public void configure() throws Exception {
            from("direct:start").bean("bar").to("mock:results");
        }
    }
}
