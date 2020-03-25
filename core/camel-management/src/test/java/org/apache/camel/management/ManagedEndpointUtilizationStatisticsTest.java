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

import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.ManagementStatisticsLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.RuntimeEndpointRegistry;
import org.junit.Test;

public class ManagedEndpointUtilizationStatisticsTest extends ManagementTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        // turn on runtime statistics in extended mode
        camelContext.getManagementStrategy().getManagementAgent().setStatisticsLevel(ManagementStatisticsLevel.Extended);
        return camelContext;
    }

    @Test
    public void testManageEndpointUtilizationStatistics() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        getMockEndpoint("mock:result").expectedMessageCount(4);

        template.sendBody("seda:start", "Hello World");
        template.sendBody("seda:start", "Bye World");
        template.sendBody("seda:start", "Hi World");
        template.sendBody("seda:start", "Camel World");

        assertMockEndpointsSatisfied();

        List<RuntimeEndpointRegistry.Statistic> stats = context.getRuntimeEndpointRegistry().getEndpointStatistics();
        assertNotNull(stats);
        assertEquals(2, stats.size());
        assertEquals(4, stats.get(0).getHits());
        assertEquals(4, stats.get(1).getHits());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("seda:start").to("mock:result");
            }
        };
    }

}
