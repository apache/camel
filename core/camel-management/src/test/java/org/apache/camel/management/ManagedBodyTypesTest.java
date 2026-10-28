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

import java.util.LinkedHashMap;
import java.util.Map;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.w3c.dom.Document;

import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.apache.camel.management.DefaultManagementObjectNameStrategy.TYPE_PROCESSOR;
import static org.apache.camel.management.DefaultManagementObjectNameStrategy.TYPE_ROUTE;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The body types each step has been reached with, over JMX and in the route coverage dump (CAMEL-25750).
 */
@DisabledOnOs(OS.AIX)
public class ManagedBodyTypesTest extends ManagementTestSupport {

    @Test
    public void testBodyTypesOverJmx() throws Exception {
        sendTextAndData();

        MBeanServer mbeanServer = getMBeanServer();
        ObjectName result = getCamelObjectName(TYPE_PROCESSOR, "result");
        assertArrayEquals(new String[] { "java.lang.String", "java.util.LinkedHashMap" },
                (String[]) mbeanServer.getAttribute(result, "BodyTypes"));
        assertArrayEquals(new String[] { "payload", "data" }, (String[]) mbeanServer.getAttribute(result, "BodyKinds"));

        ObjectName maybeMap = getCamelObjectName(TYPE_PROCESSOR, "maybeMap");
        assertArrayEquals(new String[] { "java.lang.String" }, (String[]) mbeanServer.getAttribute(maybeMap, "BodyTypes"));

        // the route counts its steps: the choice and the mock were reached with text and with data
        ObjectName route = getCamelObjectName(TYPE_ROUTE, "orders");
        assertEquals(2, mbeanServer.getAttribute(route, "VariedBodyTypes"));
        assertEquals(2, mbeanServer.getAttribute(route, "MixedBodyKinds"));
    }

    @Test
    public void testNoBodyTypesBeforeAMessage() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();
        ObjectName result = getCamelObjectName(TYPE_PROCESSOR, "result");
        assertArrayEquals(new String[0], (String[]) mbeanServer.getAttribute(result, "BodyTypes"));
        assertEquals(0, mbeanServer.getAttribute(getCamelObjectName(TYPE_ROUTE, "orders"), "MixedBodyKinds"));
    }

    @Test
    public void testRouteCoverageHasTheBodyTypes() throws Exception {
        sendTextAndData();

        String xml = (String) getMBeanServer().invoke(getContextObjectName(), "dumpRoutesCoverageAsXml", null, null);
        log.info(xml);

        assertTrue(xml.contains("bodyTypes=\"java.lang.String,java.util.LinkedHashMap\""), xml);
        assertTrue(xml.contains("bodyKinds=\"payload,data\""), xml);
        // should be valid XML
        Document doc = context.getTypeConverter().convertTo(Document.class, xml);
        assertNotNull(doc);
    }

    private void sendTextAndData() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(2);
        template.sendBody("direct:start", "hello");
        template.sendBody("direct:start", "map");
        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                context.setMessageHistory(true);

                from("direct:start").routeId("orders")
                        .process(e -> {
                            if ("map".equals(e.getMessage().getBody())) {
                                e.getMessage().setBody(new LinkedHashMap<>(Map.of("id", 1)));
                            }
                        }).id("maybeMap")
                        .choice().id("choice")
                        .when(body().isInstanceOf(Map.class))
                        .log("a map").id("onlyMaps")
                        .end()
                        .to("mock:result").id("result");
            }
        };
    }
}
