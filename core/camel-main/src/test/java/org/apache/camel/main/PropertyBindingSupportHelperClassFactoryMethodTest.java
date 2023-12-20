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

import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.PropertyBindingSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit test for PropertyBindingSupport
 */
public class PropertyBindingSupportHelperClassFactoryMethodTest {

    @Test
    public void testFactory() throws Exception {
        CamelContext context = new DefaultCamelContext();

        context.start();

        MyApp target = new MyApp();

        PropertyBindingSupport.build()
                .withCamelContext(context)
                .withTarget(target)
                .withProperty("name", "Donald")
                .withProperty("myDriver", "#class:" + MyDriver.class.getName()
                                          + "#" + MyDriverHelper.class.getName()
                                          + ":createDriver('localhost:2121', 'scott', 'tiger')")
                .withRemoveParameters(false).bind();

        assertEquals("Donald", target.getName());
        assertEquals("localhost:2121", target.getMyDriver().getUrl());
        assertEquals("scott", target.getMyDriver().getUsername());
        assertEquals("tiger", target.getMyDriver().getPassword());

        context.stop();
    }

    @Test
    public void testFactoryPropertyPlaceholder() throws Exception {
        CamelContext context = new DefaultCamelContext();

        Properties prop = new Properties();
        prop.put("myUsername", "scott");
        prop.put("myPassword", "tiger");
        prop.put("myUrl", "localhost:2121");
        context.getPropertiesComponent().setInitialProperties(prop);

        context.start();

        MyApp target = new MyApp();

        PropertyBindingSupport.build()
                .withCamelContext(context)
                .withTarget(target)
                .withProperty("name", "Donald")
                .withProperty("myDriver",
                        "#class:" + MyDriver.class.getName()
                                          + "#" + MyDriverHelper.class.getName()
                                          + ":createDriver('{{myUrl}}', '{{myUsername}}', '{{myPassword}}')")
                .withRemoveParameters(false).bind();

        assertEquals("Donald", target.getName());
        assertEquals("localhost:2121", target.getMyDriver().getUrl());
        assertEquals("scott", target.getMyDriver().getUsername());
        assertEquals("tiger", target.getMyDriver().getPassword());

        context.stop();
    }

    @Test
    public void testFactoryRef() throws Exception {
        CamelContext context = new DefaultCamelContext();

        context.getRegistry().bind("myDriverHelper", new MyDriverHelper());

        context.start();

        MyApp target = new MyApp();

        PropertyBindingSupport.build()
                .withCamelContext(context)
                .withTarget(target)
                .withProperty("name", "Donald")
                .withProperty("myDriver", "#class:" + MyDriver.class.getName()
                                          + "#myDriverHelper:createDriver('localhost:2121', 'scott', 'tiger')")
                .withRemoveParameters(false).bind();

        assertEquals("Donald", target.getName());
        assertEquals("localhost:2121", target.getMyDriver().getUrl());
        assertEquals("scott", target.getMyDriver().getUsername());
        assertEquals("tiger", target.getMyDriver().getPassword());

        context.stop();
    }

    public static class MyApp {

        private String name;
        private MyDriver myDriver;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public MyDriver getMyDriver() {
            return myDriver;
        }

        public void setMyDriver(MyDriver myDriver) {
            this.myDriver = myDriver;
        }
    }

    public static class MyDriverHelper {

        public static MyDriver createDriver(String url, String username, String password) {
            MyDriver driver = new MyDriver();
            driver.url = url;
            driver.username = username;
            driver.password = password;
            return driver;
        }

    }

    public static class MyDriver {

        private String url;
        private String username;
        private String password;

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
