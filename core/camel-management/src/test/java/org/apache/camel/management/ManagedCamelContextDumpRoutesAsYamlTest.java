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

import java.util.Properties;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.Endpoint;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledOnOs(OS.AIX)
public class ManagedCamelContextDumpRoutesAsYamlTest extends ManagementTestSupport {

    @Test
    public void testDumpAsYaml() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();

        ObjectName on = getContextObjectName();

        String yaml = (String) mbeanServer.invoke(on, "dumpRoutesAsYaml", null, null);
        assertNotNull(yaml);
        log.info(yaml);

        assertTrue(yaml.contains("route"));
        assertTrue(yaml.contains("myRoute"));
        assertTrue(yaml.contains("myOtherRoute"));
        assertTrue(yaml.contains("direct:start"));
        assertTrue(yaml.contains("{{result}}"));
        assertTrue(yaml.contains("seda:bar?size=1234&multipleConsumers=true"));
        assertTrue(yaml.contains("ref:bar"));
        assertTrue(yaml.contains("expression: bar"));
    }

    @Test
    public void testDumpAsYamlResolvePlaceholder() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();

        ObjectName on = getContextObjectName();

        String yaml = (String) mbeanServer.invoke(on, "dumpRoutesAsYaml", new Object[] { true }, new String[] { "boolean" });
        assertNotNull(yaml);
        log.info(yaml);

        assertTrue(yaml.contains("route"));
        assertTrue(yaml.contains("myRoute"));
        assertTrue(yaml.contains("myOtherRoute"));
        assertTrue(yaml.contains("direct:start"));
        assertTrue(yaml.contains("mock:result"));
        assertTrue(yaml.contains("seda:bar?size=1234&multipleConsumers=true"));
        assertTrue(yaml.contains("ref:bar"));
        assertTrue(yaml.contains("expression: bar"));
    }

    @Test
    public void testDumpAsYamlUriAsParameters() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();

        ObjectName on = getContextObjectName();

        String yaml = (String) mbeanServer.invoke(on, "dumpRoutesAsYaml", new Object[] { true, true },
                new String[] { "boolean", "boolean" });
        assertNotNull(yaml);
        log.info(yaml);

        assertTrue(yaml.contains("route"));
        assertTrue(yaml.contains("myRoute"));
        assertTrue(yaml.contains("myOtherRoute"));
        assertTrue(yaml.contains("direct"));
        assertTrue(yaml.contains("name: start"));
        assertTrue(yaml.contains("mock"));
        assertTrue(yaml.contains("name: result"));
        assertTrue(yaml.contains("ref"));
        assertTrue(yaml.contains("name: bar"));
        assertTrue(yaml.contains("seda"));
        assertTrue(yaml.contains("name: bar"));
        assertTrue(yaml.contains("parameters:"));
        assertTrue(yaml.contains("size: 1234"));
        assertTrue(yaml.contains("multipleConsumers: true"));
        assertTrue(yaml.contains("expression: bar"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                Properties props = new Properties();
                props.put("result", "mock:result");
                context.getPropertiesComponent().setOverrideProperties(props);

                Endpoint bar = context.getEndpoint("mock:bar");
                bindToRegistry("bar", bar);

                from("direct:start").routeId("myRoute")
                        .log("Got ${body}")
                        .to("{{result}}");

                from("seda:bar?size=1234&multipleConsumers=true").routeId("myOtherRoute")
                        .filter().header("bar")
                        .to("ref:bar")
                        .end();
            }
        };
    }

}
