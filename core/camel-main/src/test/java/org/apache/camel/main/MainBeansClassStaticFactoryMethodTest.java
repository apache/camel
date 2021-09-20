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

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class MainBeansClassStaticFactoryMethodTest {

    @Test
    public void testBindBeans() throws Exception {
        MyFoo myFoo = new MyFoo();

        Main main = new Main();
        main.configure().addRoutesBuilder(new MyRouteBuilder());
        main.bind("myFoolish", myFoo);

        // create by class
        main.addProperty("myUrl", "localhost:2121");
        main.addProperty("myUsername", "scott");
        main.addProperty("myPassword", "tiger");
        // use static factory method (notice the # syntax)
        main.addProperty("camel.beans.driver",
                "#class:" + MyDriver.class.getName() + "#connect('{{myUrl}}', '{{myUsername}}', '{{myPassword}}')");

        main.start();

        CamelContext camelContext = main.getCamelContext();
        assertNotNull(camelContext);

        MyDriver driver = camelContext.getRegistry().lookupByNameAndType("driver", MyDriver.class);
        assertNotNull(driver);
        assertEquals("localhost:2121", driver.getUrl());
        assertEquals("scott", driver.getUsername());
        assertEquals("tiger", driver.getPassword());

        main.stop();
    }

    public static class MyRouteBuilder extends RouteBuilder {
        @Override
        public void configure() throws Exception {
            from("direct:start").to("mock:foo");
        }
    }

    public static final class MyDriver {

        private String url;
        private String username;
        private String password;

        private MyDriver() {
        }

        public static MyDriver connect(String url, String username, String password) {
            MyDriver driver = new MyDriver();
            driver.url = url;
            driver.username = username;
            driver.password = password;
            return driver;
        }

        public String getUrl() {
            return url;
        }

        public String getUsername() {
            return username;
        }

        public String getPassword() {
            return password;
        }
    }

}
