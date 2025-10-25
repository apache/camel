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

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledOnOs(OS.AIX)
public class ManagedCamelContextDumpRouteStructureAsYamlTest extends ManagementTestSupport {

    @Test
    public void testDumpAsYaml() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();

        ObjectName on = getContextObjectName();

        String yaml = (String) mbeanServer.invoke(on, "dumpStructureRoutesAsYaml", null, null);
        assertNotNull(yaml);
        log.info(yaml);

        assertTrue(yaml.contains("route"));
        assertTrue(yaml.contains("myRoute"));
        assertTrue(yaml.contains("myOtherRoute"));
        assertTrue(yaml.contains("direct:start"));
        assertTrue(yaml.contains("mock:result"));
        assertTrue(yaml.contains("seda:bar?size=1234&multipleConsumers=true"));
        assertTrue(yaml.contains("mock:bar"));
        assertTrue(yaml.contains("message"));
        assertTrue(yaml.contains("sourceLineNumber"));
        assertTrue(yaml.contains("sourceLocation"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                context.setDebugging(true);

                from("direct:start").routeId("myRoute")
                        .log("Got ${body}")
                        .to("mock:result");

                from("seda:bar?size=1234&multipleConsumers=true").routeId("myOtherRoute")
                        .filter().header("bar")
                        .to("mock:bar")
                        .end();
            }
        };
    }

}
