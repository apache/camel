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
package org.apache.camel.impl.console;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.console.DevConsole;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class BeanDevConsoleTest extends ContextTestSupport {

    @Override
    protected void doPostSetup() {
        context.getRegistry().bind("myBean", new TestBean("Hello", 42));
        context.getRegistry().bind("anotherBean", new TestBean("World", null));
    }

    @Test
    public void testBeanConsoleText() {
        DevConsole console = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("bean");
        Assertions.assertNotNull(console);
        Assertions.assertEquals("camel", console.getGroup());
        Assertions.assertEquals("bean", console.getId());

        String out = (String) console.call(DevConsole.MediaType.TEXT);
        Assertions.assertNotNull(out);
        log.info(out);
        Assertions.assertTrue(out.contains("myBean"));
        Assertions.assertTrue(out.contains("anotherBean"));
        Assertions.assertTrue(out.contains("TestBean"));
    }

    @Test
    public void testBeanConsoleJson() {
        DevConsole console = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("bean");
        Assertions.assertNotNull(console);

        JsonObject out = (JsonObject) console.call(DevConsole.MediaType.JSON);
        Assertions.assertNotNull(out);
        log.info(out.toJson());

        JsonObject beans = out.getMap("beans");
        Assertions.assertNotNull(beans);
        Assertions.assertTrue(beans.containsKey("myBean"));
        Assertions.assertTrue(beans.containsKey("anotherBean"));

        JsonObject myBean = beans.getMap("myBean");
        Assertions.assertEquals("myBean", myBean.getString("name"));
        Assertions.assertTrue(myBean.getString("type").contains("TestBean"));
    }

    @Test
    public void testBeanConsoleWithFilter() {
        DevConsole console = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("bean");
        Assertions.assertNotNull(console);

        Map<String, Object> options = new HashMap<>();
        options.put(BeanDevConsole.FILTER, "myBean");

        String out = (String) console.call(DevConsole.MediaType.TEXT, options);
        Assertions.assertNotNull(out);
        log.info(out);
        Assertions.assertTrue(out.contains("myBean"));
        Assertions.assertFalse(out.contains("anotherBean"));
    }

    @Test
    public void testBeanConsoleWithoutProperties() {
        DevConsole console = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("bean");
        Assertions.assertNotNull(console);

        Map<String, Object> options = new HashMap<>();
        options.put(BeanDevConsole.PROPERTIES, "false");

        JsonObject out = (JsonObject) console.call(DevConsole.MediaType.JSON, options);
        Assertions.assertNotNull(out);

        JsonObject beans = out.getMap("beans");
        JsonObject myBean = beans.getMap("myBean");
        Assertions.assertNotNull(myBean);
        // No properties should be present
        Assertions.assertFalse(myBean.containsKey("properties"));
    }

    @Test
    public void testBeanConsoleWithProperties() {
        DevConsole console = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("bean");
        Assertions.assertNotNull(console);

        Map<String, Object> options = new HashMap<>();
        options.put(BeanDevConsole.PROPERTIES, "true");

        JsonObject out = (JsonObject) console.call(DevConsole.MediaType.JSON, options);
        Assertions.assertNotNull(out);
        log.info(out.toJson());

        JsonObject beans = out.getMap("beans");
        JsonObject myBean = beans.getMap("myBean");
        Assertions.assertNotNull(myBean);

        JsonArray properties = myBean.getCollection("properties");
        Assertions.assertNotNull(properties);
        Assertions.assertFalse(properties.isEmpty());
    }

    @Test
    public void testBeanConsoleWithNulls() {
        DevConsole console = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("bean");
        Assertions.assertNotNull(console);

        Map<String, Object> options = new HashMap<>();
        options.put(BeanDevConsole.FILTER, "anotherBean");
        options.put(BeanDevConsole.NULLS, "true");

        JsonObject out = (JsonObject) console.call(DevConsole.MediaType.JSON, options);
        Assertions.assertNotNull(out);
        log.info(out.toJson());

        JsonObject beans = out.getMap("beans");
        JsonObject anotherBean = beans.getMap("anotherBean");
        Assertions.assertNotNull(anotherBean);

        JsonArray properties = anotherBean.getCollection("properties");
        Assertions.assertNotNull(properties);
        // Should include null value properties
        boolean hasNullValue = properties.stream()
                .map(p -> (JsonObject) p)
                .anyMatch(p -> p.get("value") == null);
        Assertions.assertTrue(hasNullValue);
    }

    @Test
    public void testBeanConsoleWithoutNulls() {
        DevConsole console = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("bean");
        Assertions.assertNotNull(console);

        Map<String, Object> options = new HashMap<>();
        options.put(BeanDevConsole.FILTER, "anotherBean");
        options.put(BeanDevConsole.NULLS, "false");

        JsonObject out = (JsonObject) console.call(DevConsole.MediaType.JSON, options);
        Assertions.assertNotNull(out);
        log.info(out.toJson());

        JsonObject beans = out.getMap("beans");
        JsonObject anotherBean = beans.getMap("anotherBean");
        Assertions.assertNotNull(anotherBean);

        JsonArray properties = anotherBean.getCollection("properties");
        if (properties != null) {
            // Should NOT include null value properties
            boolean hasNullValue = properties.stream()
                    .map(p -> (JsonObject) p)
                    .anyMatch(p -> p.get("value") == null);
            Assertions.assertFalse(hasNullValue);
        }
    }

    @Test
    public void testBeanConsoleExcludeInternal() {
        DevConsole console = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("bean");
        Assertions.assertNotNull(console);

        Map<String, Object> options = new HashMap<>();
        options.put(BeanDevConsole.INTERNAL, "false");

        String out = (String) console.call(DevConsole.MediaType.TEXT, options);
        Assertions.assertNotNull(out);
        log.info(out);
        // Our test beans are in org.apache.camel package, so they are treated as internal
        // and should be excluded when internal=false
        Assertions.assertFalse(out.contains("myBean"));
        Assertions.assertFalse(out.contains("anotherBean"));
    }

    @Test
    public void testBeanConsoleIncludeInternal() {
        DevConsole console = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("bean");
        Assertions.assertNotNull(console);

        Map<String, Object> options = new HashMap<>();
        options.put(BeanDevConsole.INTERNAL, "true");

        String out = (String) console.call(DevConsole.MediaType.TEXT, options);
        Assertions.assertNotNull(out);
        log.info(out);
        // When internal=true, all beans including our test beans should be present
        Assertions.assertTrue(out.contains("myBean"));
        Assertions.assertTrue(out.contains("anotherBean"));
    }

    public static class TestBean {
        private String name;
        private Integer count;

        public TestBean(String name, Integer count) {
            this.name = name;
            this.count = count;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Integer getCount() {
            return count;
        }

        public void setCount(Integer count) {
            this.count = count;
        }
    }

}
