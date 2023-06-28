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
package org.apache.camel.management;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.TabularData;

import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.Transformer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@DisabledOnOs(OS.AIX)
public class ManagedTransformerRegistryTest extends ManagementTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(ManagedTransformerRegistryTest.class);

    @Test
    public void testManageTransformerRegistry() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();

        // get the stats for the route
        MBeanServer mbeanServer = getMBeanServer();
        Set<ObjectName> set = mbeanServer.queryNames(new ObjectName("*:type=services,*"), null);
        List<ObjectName> list = new ArrayList<>(set);
        ObjectName on = null;
        for (ObjectName name : list) {
            if (name.getCanonicalName().contains("DefaultTransformerRegistry")) {
                on = name;
                break;
            }
        }

        assertNotNull(on, "Should have found TransformerRegistry");

        Integer max = (Integer) mbeanServer.getAttribute(on, "MaximumCacheSize");
        assertEquals(1000, max.intValue());

        Integer current = (Integer) mbeanServer.getAttribute(on, "Size");
        assertEquals(2, current.intValue());

        current = (Integer) mbeanServer.getAttribute(on, "StaticSize");
        assertEquals(2, current.intValue());

        current = (Integer) mbeanServer.getAttribute(on, "DynamicSize");
        assertEquals(0, current.intValue());

        String source = (String) mbeanServer.getAttribute(on, "Source");
        assertTrue(source.startsWith("TransformerRegistry"));
        assertTrue(source.endsWith("capacity: 1000]"));

        TabularData data = (TabularData) mbeanServer.invoke(on, "listTransformers", null, null);
        for (Object row : data.values()) {
            CompositeData composite = (CompositeData) row;
            String name = (String) composite.get("name");
            String from = (String) composite.get("from");
            String to = (String) composite.get("to");
            String description = (String) composite.get("description");
            boolean isStatic = (boolean) composite.get("static");
            boolean isDynamic = (boolean) composite.get("dynamic");
            LOG.info("[{}][{}][{}][{}][{}][{}]", name, from, to, isStatic, isDynamic, description);
            if (description.startsWith("ProcessorTransformer")) {
                assertEquals(null, name);
                assertEquals("xml:foo", from);
                assertEquals("json:bar", to);
            } else if (description.startsWith("DataFormatTransformer")) {
                assertEquals(null, name);
                assertEquals("java:" + ManagedTransformerRegistryTest.class.getName(), from);
                assertEquals("xml:test", to);
            } else if (description.startsWith("MyTransformer")) {
                assertEquals("custom", name);
                assertEquals("camel:any", from);
                assertEquals("camel:any", to);
            } else {
                fail("Unexpected transformer:" + description);
            }
        }
        assertEquals(2, data.size());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                transformer()
                        .fromType("xml:foo")
                        .toType("json:bar")
                        .withUri("direct:transformer");
                transformer()
                        .name("custom")
                        .withJava(MyTransformer.class);

                from("direct:start").to("mock:result");
            }
        };
    }

    public static class MyTransformer extends Transformer {
        @Override
        public void transform(Message message, DataType from, DataType to) throws Exception {
            // empty
        }
    }
}
