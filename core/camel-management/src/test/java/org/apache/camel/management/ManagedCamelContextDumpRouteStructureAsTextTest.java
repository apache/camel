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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledOnOs(OS.AIX)
public class ManagedCamelContextDumpRouteStructureAsTextTest extends ManagementTestSupport {

    @Test
    public void testDumpAsText() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();

        ObjectName on = getContextObjectName();

        String text = (String) mbeanServer.invoke(on, "dumpStructureRoutesAsText", new Object[] { false },
                new String[] { "boolean" });
        assertNotNull(text);
        log.info(text);

        assertTrue(text.contains("route[myRoute]"));
        assertTrue(text.contains("to[mock:result]"));
        assertTrue(text.contains("filter[header{bar}]"));
    }

    @Test
    public void testDumpAsTextBrief() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();

        ObjectName on = getContextObjectName();

        String text = (String) mbeanServer.invoke(on, "dumpStructureRoutesAsText", new Object[] { true },
                new String[] { "boolean" });
        assertNotNull(text);
        log.info(text);

        assertTrue(text.contains("route[myRoute]"));
        assertFalse(text.contains("to[mock:result]"));
        assertTrue(text.contains("to"));
        assertFalse(text.contains("filter[header{bar}]"));
        assertTrue(text.contains("filter"));
    }

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
