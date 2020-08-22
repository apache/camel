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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ManagedCamelContextDumpRouteTemplatesAsXmlTest extends ManagementTestSupport {

    @Test
    public void testDumpAsXml() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        MBeanServer mbeanServer = getMBeanServer();

        ObjectName on = ObjectName.getInstance("org.apache.camel:context=camel-1,type=context,name=\"camel-1\"");

        String xml = (String) mbeanServer.invoke(on, "dumpRouteTemplatesAsXml", null, null);
        assertNotNull(xml);
        log.info(xml);

        assertTrue(xml.contains("routeTemplate"));
        assertTrue(xml.contains("myTemplate"));
        assertTrue(xml.contains("<templateParameter name=\"foo\""));
        assertTrue(xml.contains("<templateParameter name=\"bar\""));
        assertTrue(xml.contains("direct:{{foo}}"));
        assertTrue(xml.contains("myOtherTemplate"));
        assertTrue(xml.contains("<templateParameter name=\"aaa\""));
        assertTrue(xml.contains("<header>{{aaa}}</header>"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                routeTemplate("myTemplate").templateParameter("foo").templateParameter("bar")
                        .from("direct:{{foo}}")
                        .log("Got ${body}")
                        .to("{{bar}}");

                routeTemplate("myOtherTemplate").templateParameter("aaa")
                        .from("seda:bar")
                        .filter().header("{{aaa}}")
                        .to("ref:bar")
                        .end();
            }
        };
    }

}
