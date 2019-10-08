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

import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelContext;
import org.apache.camel.PropertyInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Assert;
import org.junit.Test;

public class MainIoCNewRouteBuilderComplexTest extends Assert {

    @Test
    public void testMainIoC() throws Exception {
        Main main = new Main();
        main.addRoutesBuilder(new MyRouteBuilder());
        main.start();

        CamelContext camelContext = main.getCamelContext();
        assertNotNull(camelContext);

        MockEndpoint endpoint = camelContext.getEndpoint("mock:results", MockEndpoint.class);
        endpoint.expectedBodiesReceived("Hi dude");

        main.getCamelTemplate().sendBody("direct:start", "<message>1</message>");

        endpoint.assertIsSatisfied();

        main.stop();
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

    public static class MyDude {

        private final String name;

        public MyDude(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public static class MyRouteBuilder extends RouteBuilder {

        @BindToRegistry("foo")
        public MyFoo createFoo(MyBar bar) {
            // should be invoked #3
            return new MyFoo(bar.toString());
        }

        @BindToRegistry
        public MyDude myDude() {
            // should be invoked #1
            return new MyDude("dude");
        }

        @BindToRegistry("bar")
        public MyBar createBar(@PropertyInject(value = "bye", defaultValue = "Hi") String hello, MyDude dude) {
            // should be invoked #2
            return new MyBar(hello + " " + dude.name);
        }

        @Override
        public void configure() throws Exception {
            from("direct:start").bean("foo", "getName").to("mock:results");
        }
    }
}
