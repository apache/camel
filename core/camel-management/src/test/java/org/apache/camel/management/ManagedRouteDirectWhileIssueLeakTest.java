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

import java.util.Set;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class ManagedRouteDirectWhileIssueLeakTest extends ManagementTestSupport {

    @Test
    public void testInflightLeak() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        MBeanServer mbeanServer = getMBeanServer();

        getMockEndpoint("mock:result").expectedBodiesReceived("AAAA");

        template.sendBodyAndHeader("direct:start", "", "counter", 4);

        assertMockEndpointsSatisfied();

        // should not be any inflights
        Set<ObjectName> set = mbeanServer.queryNames(new ObjectName("*:type=routes,*"), null);
        assertEquals(1, set.size());
        ObjectName on = set.iterator().next();

        Long inflight = (Long) mbeanServer.getAttribute(on, "ExchangesInflight");
        assertEquals(0, inflight.longValue());
        Long ts = (Long) mbeanServer.getAttribute(on, "OldestInflightDuration");
        assertNull(ts);
        String id = (String) mbeanServer.getAttribute(on, "OldestInflightExchangeId");
        assertNull(id);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .choice().when(simple("${header.counter} > 0"))
                        .process(exchange -> {
                            String body = exchange.getIn().getBody(String.class);
                            body = "A" + body;
                            exchange.getIn().setBody(body);

                            int counter = exchange.getIn().getHeader("counter", int.class);
                            counter = counter - 1;
                            exchange.getIn().setHeader("counter", counter);
                        }).to("direct:start")
                    .otherwise()
                        .to("mock:result")
                    .end();
            }
        };
    }
}

