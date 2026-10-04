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
package org.apache.camel.impl.engine;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.PropertyInject;
import org.apache.camel.spi.Registry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The separator of {@link PropertyInject} splits the property value: it is a separator, not a regular expression.
 */
class PropertyInjectSeparatorTest extends ContextTestSupport {

    private final Properties myProp = new Properties();

    @Override
    protected Registry createCamelRegistry() throws Exception {
        Registry jndi = super.createCamelRegistry();
        jndi.bind("myProp", myProp);
        return jndi;
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getPropertiesComponent().setLocation("ref:myProp");
        return context;
    }

    @Test
    void pipeSeparatedList() throws Exception {
        myProp.put("hosts", "serverA|serverB");

        assertEquals(List.of("serverA", "serverB"), inject("hosts"));
    }

    @Test
    void dotSeparatedArray() throws Exception {
        myProp.put("parts", "a.b.c");

        assertEquals(List.of("a", "b", "c"), Arrays.asList((Object[]) inject("parts")));
    }

    @Test
    void pipeSeparatedMap() throws Exception {
        myProp.put("servers", "serverA=4444|serverB=5555");

        assertEquals(Map.of("serverA", 4444, "serverB", 5555), inject("servers"));
    }

    @Test
    void semicolonSeparatedList() throws Exception {
        myProp.put("ports", "4444;5555");

        assertEquals(List.of(4444, 5555), inject("ports"));
    }

    private Object inject(String fieldName) throws Exception {
        Field field = MyBean.class.getField(fieldName);
        PropertyInject propertyInject = field.getAnnotation(PropertyInject.class);
        return new CamelPostProcessorHelper(context).getInjectionPropertyValue(field.getType(), field.getGenericType(),
                propertyInject.value(), "", propertyInject.separator());
    }

    public static class MyBean {

        @PropertyInject(value = "hosts", separator = "|")
        public List<String> hosts;

        @PropertyInject(value = "parts", separator = ".")
        public String[] parts;

        @PropertyInject(value = "servers", separator = "|")
        public Map<String, Integer> servers;

        @PropertyInject(value = "ports", separator = ";")
        public List<Integer> ports;
    }
}
