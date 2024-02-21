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
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.PropertyBindingSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit test for PropertyBindingSupport
 */
public class PropertyBindingSupportBuildMethodTest {

    @Test
    public void testBuildClass() throws Exception {
        CamelContext context = new DefaultCamelContext();

        context.start();

        MyDriver driver = PropertyBindingSupport.build()
                .withCamelContext(context)
                .withTarget(new MyDriverBuilder())
                .withFluentBuilder(true)
                .withProperty("url", "localhost:1234")
                .withProperty("username", "scott")
                .withProperty("password", "tiger")
                .build(MyDriver.class);

        Assertions.assertNotNull(driver);
        Assertions.assertEquals("localhost:1234", driver.getUrl());
        Assertions.assertEquals("scott", driver.getUsername());
        Assertions.assertEquals("tiger", driver.getPassword());

        context.stop();
    }

    public static class MyDriver {

        private final String url;
        private final String username;
        private final String password;

        public MyDriver(String url, String username, String password) {
            this.url = url;
            this.username = username;
            this.password = password;
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

    public static class MyDriverBuilder {

        private String url;
        private String username;
        private String password;

        public MyDriverBuilder url(String url) {
            this.url = url;
            return this;
        }

        public MyDriverBuilder username(String username) {
            this.username = username;
            return this;
        }

        public MyDriverBuilder password(String password) {
            this.password = password;
            return this;
        }

        public MyDriver build() {
            return new MyDriver(url, username, password);
        }
    }

}
