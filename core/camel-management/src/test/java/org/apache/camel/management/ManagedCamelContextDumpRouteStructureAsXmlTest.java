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
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.xml.jaxb.JaxbModelToXMLDumper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledOnOs(OS.AIX)
public class ManagedCamelContextDumpRouteStructureAsXmlTest extends ManagementTestSupport {

    @Test
    public void testDumpAsXml() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();

        ObjectName on = getContextObjectName();

        String xml = (String) mbeanServer.invoke(on, "dumpStructureRoutesAsXml", null, null);
        assertNotNull(xml);
        log.info(xml);

        assertTrue(xml.contains("route"));
        assertTrue(xml.contains("myRoute"));
        assertTrue(xml.contains("myOtherRoute"));
        assertTrue(xml.contains("direct:start"));
        assertTrue(xml.contains("mock:result"));
        assertTrue(xml.contains("seda:bar?size=1234&amp;multipleConsumers=true"));
        assertTrue(xml.contains("mock:bar"));
        assertTrue(xml.contains("message"));
        assertTrue(xml.contains("sourceLineNumber"));
        assertTrue(xml.contains("sourceLocation"));
        assertFalse(xml.contains("<header>"));
    }

    @Test
    public void testDumpAsXmlUsingJaxb() {
        JaxbModelToXMLDumper dumper = new JaxbModelToXMLDumper();
        RoutesDefinition def = new RoutesDefinition();
        def.setRoutes(context.getRouteDefinitions());
        Assertions.assertThrows(UnsupportedOperationException.class, () -> {
            dumper.dumpStructureModelAsXml(context, def);
        });
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
