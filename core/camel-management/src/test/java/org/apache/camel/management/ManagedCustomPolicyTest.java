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
import java.util.concurrent.atomic.AtomicInteger;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.NamedNode;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.Policy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.apache.camel.management.DefaultManagementObjectNameStrategy.TYPE_PROCESSOR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledOnOs(OS.AIX)
public class ManagedCustomPolicyTest extends ManagementTestSupport {

    private final AtomicInteger counter = new AtomicInteger();

    @Test
    public void testPolicy() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);
        template.sendBody("direct:start", "Hello World");
        assertMockEndpointsSatisfied();

        assertEquals(1, counter.get());

        MBeanServer mbeanServer = getMBeanServer();

        Set<ObjectName> set = mbeanServer.queryNames(new ObjectName("*:type=processors,*"), null);
        assertEquals(3, set.size());

        ObjectName on = getCamelObjectName(TYPE_PROCESSOR, "foo");
        assertTrue(mbeanServer.isRegistered(on), "Should be registered: foo");

        on = getCamelObjectName(TYPE_PROCESSOR, "result");
        assertTrue(mbeanServer.isRegistered(on), "Should be registered: result");

        on = getCamelObjectName(TYPE_PROCESSOR, "bar");
        assertTrue(mbeanServer.isRegistered(on), "Should be registered: bar");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // custom policy but processors should be registered
                from("direct:start").policy(new MyPolicy())
                        .to("log:foo").id("foo")
                        .to("mock:result").id("result");

                // no policy but processors should be registered
                from("direct:bar")
                        .to("log:bar").id("bar");
            }
        };
    }

    private final class MyPolicy implements Policy {

        @Override
        public void beforeWrap(Route route, NamedNode definition) {
            // noop
        }

        @Override
        public Processor wrap(Route route, final Processor processor) {
            return exchange -> {
                counter.incrementAndGet();
                processor.process(exchange);
            };
        }
    }

}
