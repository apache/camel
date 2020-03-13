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
import org.apache.camel.spi.GeneratedPropertyConfigurer;
import org.junit.Assert;
import org.junit.Test;

public class MainIoCBeanConfigInjectConfigurerTest extends Assert {

    @Test
    public void testMainIoC() throws Exception {
        Main main = new Main();
        main.addRoutesBuilder(new MyRouteBuilder());
        main.addInitialProperty("bar.name", "Thirsty Bear");
        main.addInitialProperty("bar.age", "23");
        main.bind(MyBarConfig.class.getName() + "-configurer", new MyBarConfigConfigurer());
        main.start();

        CamelContext camelContext = main.getCamelContext();
        assertNotNull(camelContext);

        MockEndpoint endpoint = camelContext.getEndpoint("mock:results", MockEndpoint.class);
        endpoint.expectedBodiesReceived("The Thirsty Bear (minimum age: 46)");

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

    public static class MyBarConfigConfigurer implements GeneratedPropertyConfigurer {

        @Override
        public boolean configure(CamelContext camelContext, Object target, String name, Object value, boolean ignoreCase) {
            if (target instanceof MyBarConfig) {
                MyBarConfig config = (MyBarConfig) target;
                if ("name".equals(name)) {
                    // ensure the configurer was in use by prefix
                    config.setName("The " + value.toString());
                    return true;
                } else if ("age".equals(name)) {
                    // ensure the configurer was in use by * 2
                    int num = Integer.parseInt(value.toString()) * 2;
                    config.setAge(num);
                    return true;
                }
            }
            return false;
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
