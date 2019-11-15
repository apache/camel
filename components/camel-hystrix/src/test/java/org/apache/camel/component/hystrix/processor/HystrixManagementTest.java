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
package org.apache.camel.component.hystrix.processor;

import java.util.stream.Stream;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.hystrix.metrics.HystrixEventStreamService;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class HystrixManagementTest extends CamelTestSupport {

    private HystrixEventStreamService stream = new HystrixEventStreamService();

    @Override
    protected boolean useJmx() {
        return true;
    }

    protected MBeanServer getMBeanServer() {
        return context.getManagementStrategy().getManagementAgent().getMBeanServer();
    }

    @Test
    public void testHystrix() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Bye World");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();

        // look inside jmx
        // get the stats for the route
        MBeanServer mbeanServer = getMBeanServer();

        // context name
        String name = context.getManagementName();

        // get the object name for the delayer
        ObjectName on = ObjectName.getInstance("org.apache.camel:context=" + name + ",type=processors,name=\"myHystrix\"");

        // should be on start
        String routeId = (String) mbeanServer.getAttribute(on, "RouteId");
        assertEquals("start", routeId);

        // should be id of the node
        String commandKey = (String) mbeanServer.getAttribute(on, "HystrixCommandKey");
        assertEquals("myHystrix", commandKey);

        String groupKey = (String) mbeanServer.getAttribute(on, "HystrixGroupKey");
        assertEquals("CamelHystrix", groupKey);

        // these metrics need a little time before updating
        Thread.sleep(1000);

        Long totalRequests = (Long) mbeanServer.getAttribute(on, "HystrixTotalRequests");
        assertEquals(1, totalRequests.longValue());

        Long errorCount = (Long) mbeanServer.getAttribute(on, "HystrixErrorCount");
        assertEquals(0, errorCount.longValue());

        Boolean open = (Boolean) mbeanServer.getAttribute(on, "CircuitBreakerOpen");
        assertEquals(false, open.booleanValue());

        // let it gather for a while
        Thread.sleep(1000);

        String latest = stream.oldestMetricsAsJSon();
        log.info("Oldest json stream: {}", latest);

        Stream<String> jsons = stream.streamMetrics();
        jsons.forEach(s -> {
            log.info("JSon: {}", s);
        });
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // add the stream
                stream.setQueueSize(10);
                stream.setDelay(100);
                context.addService(stream);

                from("direct:start").routeId("start")
                        .circuitBreaker().id("myHystrix")
                            .to("direct:foo")
                        .onFallback()
                            .transform().constant("Fallback message")
                        .end()
                        .to("mock:result");

                from("direct:foo")
                        .transform().constant("Bye World");
            }
        };
    }

}
