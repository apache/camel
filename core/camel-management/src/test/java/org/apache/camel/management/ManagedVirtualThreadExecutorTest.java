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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.LifecycleStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;

import static org.apache.camel.management.DefaultManagementObjectNameStrategy.TYPE_THREAD_POOL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledForJreRange(min = JRE.JAVA_21)
public class ManagedVirtualThreadExecutorTest extends ManagementTestSupport {

    private ExecutorService vte;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext ctx = super.createCamelContext();
        // register the virtual thread executor during context creation (before it starts)
        // so that shouldRegister returns true
        vte = (ExecutorService) Executors.class
                .getMethod("newVirtualThreadPerTaskExecutor")
                .invoke(null);
        for (LifecycleStrategy lifecycle : ctx.getLifecycleStrategies()) {
            lifecycle.onThreadPoolAdd(ctx, vte, "myVirtualPool", "test", null, null);
        }
        return ctx;
    }

    @Test
    public void testManagedVirtualThreadExecutor() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();

        ObjectName on = getCamelObjectName(TYPE_THREAD_POOL, "myVirtualPool(test)");

        Boolean shutdown = (Boolean) mbeanServer.getAttribute(on, "Shutdown");
        assertFalse(shutdown.booleanValue());

        Boolean virtualThread = (Boolean) mbeanServer.getAttribute(on, "VirtualThread");
        assertTrue(virtualThread.booleanValue());

        String id = (String) mbeanServer.getAttribute(on, "Id");
        assertEquals("myVirtualPool", id);

        String sourceId = (String) mbeanServer.getAttribute(on, "SourceId");
        assertEquals("test", sourceId);

        // cleanup
        for (LifecycleStrategy lifecycle : context.getLifecycleStrategies()) {
            lifecycle.onThreadPoolRemove(context, vte);
        }
        vte.shutdown();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to("mock:result");
            }
        };
    }
}
