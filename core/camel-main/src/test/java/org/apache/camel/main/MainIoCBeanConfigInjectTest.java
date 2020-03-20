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

import org.apache.camel.BeanConfigInject;
import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Assert;
import org.junit.Test;

public class MainIoCBeanConfigInjectTest extends Assert {

    @Test
    public void testMainIoC() throws Exception {
        Main main = new Main();
        main.addRoutesBuilder(new MyRouteBuilder());
        main.addInitialProperty("bar.name", "Thirsty Bear");
        main.addInitialProperty("bar.age", "23");
        main.start();

        CamelContext camelContext = main.getCamelContext();
        assertNotNull(camelContext);

        MockEndpoint endpoint = camelContext.getEndpoint("mock:results", MockEndpoint.class);
        endpoint.expectedBodiesReceived("Thirsty Bear (minimum age: 23)");

        main.getCamelTemplate().sendBody("direct:start", "Which bar");

        endpoint.assertIsSatisfied();

        main.stop();
    }

    public static class MyBar {

        private final String description;

        public MyBar(String description) {
            this.description = description;
        }

        @Override
        public String toString() {
            return description;
        }
    }

    public static class MyBarConfig {

        private String name;
        private int age;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getAge() {
            return age;
        }

        public void setAge(int age) {
            this.age = age;
        }
    }

    public static class MyRouteBuilder extends RouteBuilder {

        @BindToRegistry("bar")
        public MyBar createBar(@BeanConfigInject("bar") MyBarConfig config) {
            String text = config.getName() + " (minimum age: " + config.getAge() + ")";
            return new MyBar(text);
        }

        @Override
        public void configure() throws Exception {
            from("direct:start").bean("bar").to("mock:results");
        }
    }
}
