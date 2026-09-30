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

import javax.management.ObjectName;
import javax.management.openmbean.TabularData;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.processor.SendProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.apache.camel.management.DefaultManagementObjectNameStrategy.TYPE_PROCESSOR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledOnOs(OS.AIX)
class ManagedSwitchTest extends ManagementTestSupport {
    @Test
    void exposesCaseDestinationsCountsAndCaseMBeans() throws Exception {
        template.sendBodyAndHeader("direct:start", "first", "decision", "BILLING");
        template.sendBodyAndHeader("direct:start", "second", "decision", "billing");
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

    @ParameterizedTest
    @NullSource
    @ValueSource(booleans = { true, false })
    void masksCaseAndFallbackUris(Boolean mask) throws Exception {
        // A null parameter exercises the agent's default without overriding it.
        if (mask != null) {
            context.getManagementStrategy().getManagementAgent().setMask(mask);
        }
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:masked").nodePrefixId("p-")
                        .doSwitch(header("decision")).id("masked")
                        .doCase("billing", "mock:billing?password=caseSecret")
                        .otherwise("mock:review?password=fallbackSecret");
            }
        });
        TabularData table = (TabularData) getMBeanServer().invoke(
                getCamelObjectName(TYPE_PROCESSOR, "p-masked"), "extendedInformation", null, null);
        boolean sanitized = !Boolean.FALSE.equals(mask);
        assertEquals("mock:billing?password=" + (sanitized ? "xxxxxx" : "caseSecret"),
                table.get(new Object[] { 0 }).get("uri"));
        assertEquals("mock:review?password=" + (sanitized ? "xxxxxx" : "fallbackSecret"),
                table.get(new Object[] { 1 }).get("uri"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("switchRoute").nodePrefixId("p-")
                        .doSwitch(header("decision")).id("dispatch")
                        .doCase("billing").id("urgentCase").to("mock:urgent")
                        .otherwise("mock:review");
            }
        };
    }
}
