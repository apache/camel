/**
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

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.api.management.mbean.ManagedProcessorMBean;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

/**
 * @version 
 */
public class ManagedInlinedProcessorTest extends ManagementTestSupport {

    @Test
    public void testManageInlinedProcessor() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = ObjectName.getInstance("org.apache.camel:context=camel-1,type=processors,name=\"custom\"");

        getMockEndpoint("mock:result").expectedMessageCount(1);
        template.sendBody("direct:start", "Hello World");
        assertMockEndpointsSatisfied();

        Long counter = (Long) mbeanServer.getAttribute(on, "ExchangesCompleted");
        assertEquals(1L, counter.longValue());

        ManagedProcessorMBean mb = context.getManagedProcessor("custom", ManagedProcessorMBean.class);
        assertNotNull(mb);
        assertEquals(1L, mb.getExchangesCompleted());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").routeId("foo")
                    .process(new Processor() {
                        @Override
                        public void process(Exchange exchange) throws Exception {
                            exchange.getOut().setBody("Bye World");
                        }
                    }).id("custom")
                    .to("mock:result");
            }
        };
    }


}
