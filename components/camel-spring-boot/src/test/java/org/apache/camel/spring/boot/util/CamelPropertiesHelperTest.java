/**
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
package org.apache.camel.spring.boot.util;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@DirtiesContext
@SpringBootApplication
@SpringBootTest(
    classes = {
        CamelPropertiesHelperTest.TestConfiguration.class
    }
)
public class CamelPropertiesHelperTest {

    @Autowired
    ApplicationContext context;

    @Autowired
    CamelContext camelContext;

    @Configuration
    static class TestConfiguration {
        @Bean(name = "myCoolOption")
        MyOption myCoolBean() {
            return new MyOption();
        }
    }

    static class MyOption {
    }

    static class MyClass {

        private int id;
        private String name;
        private MyOption option;

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public MyOption getOption() {
            return option;
        }

        public void setOption(MyOption option) {
            this.option = option;
        }
    }

    @Test
    public void testSetCamelProperties() throws Exception {
        MyClass target = new MyClass();

        Map<String, Object> map = new HashMap<>();
        map.put("id", "123");
        map.put("name", "Donald Duck");
        map.put("option", "myCoolOption");

        CamelPropertiesHelper.setCamelProperties(camelContext, target, map, true);

        Assert.assertEquals("Should configure all options", 0, map.size());
        Assert.assertEquals(123, target.getId());
        Assert.assertEquals("Donald Duck", target.getName());
        Assert.assertSame(context.getBean("myCoolOption"), target.getOption());
    }

    @Test
    public void testSetCamelPropertiesReference() throws Exception {
        MyClass target = new MyClass();

        Map<String, Object> map = new HashMap<>();
        map.put("id", "123");
        map.put("name", "Donald Duck");
        map.put("option", "#myCoolOption");

        CamelPropertiesHelper.setCamelProperties(camelContext, target, map, true);

        Assert.assertEquals("Should configure all options", 0, map.size());
        Assert.assertEquals(123, target.getId());
        Assert.assertEquals("Donald Duck", target.getName());
        Assert.assertSame(context.getBean("myCoolOption"), target.getOption());
    }

    @Test
    public void testSetCamelPropertiesUnknownOption() throws Exception {
        MyClass target = new MyClass();

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", "123");
        map.put("name", "Donald Duck");
        map.put("option", "#myCoolOption");
        map.put("unknown", "foo");

        try {
            CamelPropertiesHelper.setCamelProperties(camelContext, target, map, true);
            Assert.fail("Should have thrown exception");
        } catch (IllegalArgumentException e) {
            // expected
            Assert.assertTrue(e.getMessage().startsWith("Cannot configure option [unknown] with value [foo]"));
        }

        Assert.assertEquals("Should configure the three first options", 1, map.size());
        Assert.assertEquals(123, target.getId());
        Assert.assertEquals("Donald Duck", target.getName());
        Assert.assertSame(context.getBean("myCoolOption"), target.getOption());
    }

    @Test
    public void testSetCamelPropertiesUnknownOptionIgnore() throws Exception {
        MyClass target = new MyClass();

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", "123");
        map.put("name", "Donald Duck");
        map.put("option", "#myCoolOption");
        map.put("unknown", "foo");

        CamelPropertiesHelper.setCamelProperties(camelContext, target, map, false);

        Assert.assertEquals("Should configure the three first options", 1, map.size());
        Assert.assertEquals(123, target.getId());
        Assert.assertEquals("Donald Duck", target.getName());
        Assert.assertSame(context.getBean("myCoolOption"), target.getOption());
    }

}
