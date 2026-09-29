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

import java.util.Map;

import javax.management.ObjectName;
import javax.management.openmbean.TabularData;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.processor.SendProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.apache.camel.management.DefaultManagementObjectNameStrategy.TYPE_PROCESSOR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledOnOs(OS.AIX)
class ManagedSwitchTest extends ManagementTestSupport {
    @Test
    void exposesCaseDestinationsCountsAndCaseMBeans() throws Exception {
        template.sendBodyAndHeader("direct:start", "first", "decision", Map.of("department", "BILLING", "urgent", true));
        template.sendBodyAndHeader("direct:start", "second", "decision", Map.of("department", "billing", "urgent", true));
        template.sendBody("direct:start", "unmatched");
        ObjectName name = getCamelObjectName(TYPE_PROCESSOR, "p-dispatch");
        TabularData table = (TabularData) getMBeanServer().invoke(name, "extendedInformation", null, null);
        assertEquals(2, table.size());
        var first = table.get(new Object[] { 0 });
        assertEquals("urgentCase", first.get("id"));
        assertEquals("mock:urgent", first.get("uri"));
        assertEquals(2L, first.get("matches"));
        assertEquals(1L, table.get(new Object[] { 1 }).get("matches"));
        assertEquals("mock:review", table.get(new Object[] { 1 }).get("uri"));
        assertTrue(getMBeanServer().isRegistered(getCamelObjectName(TYPE_PROCESSOR, "p-urgentCase")),
                getMBeanServer().queryNames(new ObjectName("org.apache.camel:type=processors,*"), null).toString());
        assertTrue(getMBeanServer().isRegistered(getCamelObjectName(TYPE_PROCESSOR, "p-dispatch-otherwise")));
        assertNotNull(context.getProcessor("p-urgentCase", SendProcessor.class));
        assertNotNull(context.getProcessor("p-dispatch-otherwise", SendProcessor.class));
        getMBeanServer().invoke(name, "reset", null, null);
        table = (TabularData) getMBeanServer().invoke(name, "extendedInformation", null, null);
        assertEquals(0L, table.get(new Object[] { 0 }).get("matches"));
        assertEquals(0L, getMBeanServer().getAttribute(name, "UnmatchedCount"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("switchRoute").nodePrefixId("p-")
                        .doSwitch(header("decision")).id("dispatch").keys("department", "urgent")
                        .doCase().value("department", "billing").value("urgent", true).id("urgentCase").to("mock:urgent")
                        .otherwise("mock:review");
            }
        };
    }
}
