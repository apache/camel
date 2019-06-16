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
package org.apache.camel.component.properties;

import java.util.Iterator;
import java.util.Properties;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.junit.Test;

public class PropertiesComponentLoadPropertiesTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testLoadProperties() throws Exception {
        context.start();

        org.apache.camel.spi.PropertiesComponent pc = context.getPropertiesComponent();
        Properties prop = pc.loadProperties();

        assertNotNull(prop);
        assertEquals(19, prop.size());

        assertEquals("{{cool.b}}", prop.getProperty("cool.a"));
        assertEquals("10", prop.getProperty("myQueueSize"));
    }

    @Test
    public void testLoadPropertiesLocation() throws Exception {
        context.start();

        org.apache.camel.spi.PropertiesComponent pc = context.getPropertiesComponent();
        Properties prop = pc.loadProperties("application.properties", "example.properties");

        assertNotNull(prop);
        assertEquals(5, prop.size());

        assertEquals("World", prop.getProperty("hello"));
        assertEquals("2000", prop.getProperty("millisecs"));

        // should be ordered keys
        Iterator it = prop.keySet().iterator();
        assertEquals("hello", it.next());
        assertEquals("camel.component.seda.concurrent-consumers", it.next());
        assertEquals("camel.component.seda.queueSize", it.next());
        assertEquals("camel.component.direct.timeout", it.next());
        assertEquals("millisecs", it.next());

        // should be ordered values
        it = prop.values().iterator();
        assertEquals("World", it.next());
        assertEquals("2", it.next());
        assertEquals("500", it.next());
        assertEquals("1234", it.next());
        assertEquals("2000", it.next());
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.addComponent("properties", new PropertiesComponent("classpath:org/apache/camel/component/properties/myproperties.properties"));
        return context;
    }

}
