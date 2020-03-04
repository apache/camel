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

import org.w3c.dom.Document;

import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class ManagedRouteDumpStatsAsXmlCustomDomainTest extends ManagementTestSupport {

    private static final String CUSTOM_DOMAIN_NAME = "custom";

    @Test
    public void testPerformanceCounterStats() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        // get the stats for the route
        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = ObjectName.getInstance(CUSTOM_DOMAIN_NAME + ":context=camel-1,type=routes,name=\"foo\"");

        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.asyncSendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();

        String xml = (String) mbeanServer.invoke(on, "dumpRouteStatsAsXml", new Object[]{false, true}, new String[]{"boolean", "boolean"});
        log.info(xml);

        // should be valid XML
        Document doc = context.getTypeConverter().convertTo(Document.class, xml);
        assertNotNull(doc);

        int processors = doc.getDocumentElement().getElementsByTagName("processorStat").getLength();
        assertEquals(3, processors);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
//              System.setProperty("org.apache.camel.jmx.mbeanObjectDomainName", CUSTOM_DOMAIN_NAME);
//              Or
                getContext().getManagementStrategy().getManagementAgent().setMBeanObjectDomainName(CUSTOM_DOMAIN_NAME);
                from("direct:start").routeId("foo")
                        .to("log:foo").id("to-log")
                        .delay(100)
                        .to("mock:result").id("to-mock");
            }
        };
    }

}
