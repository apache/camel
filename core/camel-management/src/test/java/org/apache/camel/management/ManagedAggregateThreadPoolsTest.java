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
import java.util.Set;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.processor.aggregate.AggregateProcessor;
import org.apache.camel.processor.aggregate.UseLatestAggregationStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The aggregator creates several thread pools from the same source (the AggregateProcessor), which must each have their
 * own MBean.
 */
@DisabledOnOs(OS.AIX)
class ManagedAggregateThreadPoolsTest extends ManagementTestSupport {

    @Test
    void testThreadPoolsOfTheSameSource() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();

        List<ObjectName> pools = aggregateProcessorPools(mbeanServer);
        assertEquals(2, pools.size(), "The timeout checker and the optimistic locking pools should be registered: " + pools);

        List<String> sourceIds = pools.stream().map(on -> getSourceId(mbeanServer, on)).sorted().toList();
        assertEquals(List.of(AggregateProcessor.AGGREGATE_OPTIMISTIC_LOCKING_EXECUTOR,
                AggregateProcessor.AGGREGATE_TIMEOUT_CHECKER), sourceIds);
        for (ObjectName on : pools) {
            String name = ObjectName.unquote(on.getKeyProperty("name"));
            String id = (String) mbeanServer.getAttribute(on, "Id");
            assertTrue(id.startsWith("AggregateProcessor("), id);
            assertEquals(id + "(" + getSourceId(mbeanServer, on) + ")", name);
        }

        // and after a restart of the route
        context.getRouteController().stopRoute("foo");
        context.getRouteController().startRoute("foo");
        assertEquals(sourceIds, aggregateProcessorPools(mbeanServer).stream().map(on -> getSourceId(mbeanServer, on))
                .sorted().toList());

        // both are unregistered when the route is removed
        context.getRouteController().stopRoute("foo");
        context.removeRoute("foo");
        assertEquals(List.of(), aggregateProcessorPools(mbeanServer));
    }

    private static List<ObjectName> aggregateProcessorPools(MBeanServer mbeanServer) throws Exception {
        Set<ObjectName> pools = mbeanServer.queryNames(new ObjectName("*:type=threadpools,*"), null);
        return pools.stream().filter(on -> on.getKeyProperty("name").startsWith("\"AggregateProcessor(")).toList();
    }

    private static String getSourceId(MBeanServer mbeanServer, ObjectName on) {
        try {
            return (String) mbeanServer.getAttribute(on, "SourceId");
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:foo").routeId("foo")
                        .aggregate(constant(true), new UseLatestAggregationStrategy()).completionTimeout(1000)
                        .optimisticLocking()
                        .to("mock:result");
            }
        };
    }
}
