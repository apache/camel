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
package org.apache.camel.component.xslt;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

/**
 *
 */
public class ManagedXsltOutputBytesTest extends ContextTestSupport {

    @Override
    protected boolean useJmx() {
        return true;
    }

    protected MBeanServer getMBeanServer() {
        return context.getManagementStrategy().getManagementAgent().getMBeanServer();
    }

    @Test
    public void testXsltOutput() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("<?xml version=\"1.0\" encoding=\"UTF-8\"?><goodbye>world!</goodbye>");
        mock.message(0).body().isInstanceOf(byte[].class);

        template.sendBody("direct:start", "<hello>world!</hello>");

        assertMockEndpointsSatisfied();

        MBeanServer mbeanServer = getMBeanServer();

        ObjectName on = ObjectName.getInstance("org.apache.camel:context=camel-1,type=endpoints,name=\"xslt://org/apache/camel/component/xslt/example.xsl\\?output=bytes\"");
        String uri = (String) mbeanServer.getAttribute(on, "EndpointUri");
        assertEquals("xslt://org/apache/camel/component/xslt/example.xsl?output=bytes", uri);

        XsltOutput output = (XsltOutput) mbeanServer.getAttribute(on, "Output");
        assertEquals(XsltOutput.bytes, output);

        String state = (String) mbeanServer.getAttribute(on, "State");
        assertEquals("Started", state);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .to("xslt:org/apache/camel/component/xslt/example.xsl?output=bytes")
                    .to("mock:result");
            }
        };
    }
}
