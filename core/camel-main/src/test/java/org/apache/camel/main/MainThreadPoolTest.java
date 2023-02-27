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
package org.apache.camel.main;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.ThreadPoolProfile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class MainThreadPoolTest {

    @Test
    public void testDefaultThreadPool() throws Exception {
        Main main = new Main();
        main.configure().addRoutesBuilder(new MyRouteBuilder());
        main.addProperty("camel.threadpool.pool-size", "5");
        main.addProperty("camel.threadpool.max-pool-size", "10");
        main.addProperty("camel.threadpool.max-queue-size", "20");
        main.addProperty("camel.threadpool.rejectedPolicy", "Abort");
        main.start();

        CamelContext camelContext = main.getCamelContext();
        assertNotNull(camelContext);

        ThreadPoolProfile tp = camelContext.getExecutorServiceManager().getDefaultThreadPoolProfile();
        assertEquals("default", tp.getId());
        assertEquals(Boolean.TRUE, tp.isDefaultProfile());
        assertEquals("5", tp.getPoolSize().toString());
        assertEquals("10", tp.getMaxPoolSize().toString());
        assertEquals("Abort", tp.getRejectedPolicy().toString());

        main.stop();
    }

    @Test
    public void testCustomThreadPool() throws Exception {
        Main main = new Main();
        main.configure().addRoutesBuilder(new MyRouteBuilder());
        main.addProperty("camel.threadpool.config[myPool].id", "myPool");
        main.addProperty("camel.threadpool.config[myPool].pool-size", "1");
        main.addProperty("camel.threadpool.config[myPool].max-pool-size", "2");
        main.addProperty("camel.threadpool.config[myPool].rejectedPolicy", "Abort");
        main.addProperty("camel.threadpool.config[myBigPool].id", "myBigPool");
        main.addProperty("camel.threadpool.config[myBigPool].pool-size", "10");
        main.addProperty("camel.threadpool.config[myBigPool].max-pool-size", "200");
        main.addProperty("camel.threadpool.config[myBigPool].rejectedPolicy", "CallerRuns");
        main.start();

        CamelContext camelContext = main.getCamelContext();
        assertNotNull(camelContext);

        ThreadPoolProfile tp = camelContext.getExecutorServiceManager().getThreadPoolProfile("myPool");
        assertEquals("myPool", tp.getId());
        assertEquals(Boolean.FALSE, tp.isDefaultProfile());
        assertEquals("1", tp.getPoolSize().toString());
        assertEquals("2", tp.getMaxPoolSize().toString());
        assertEquals("Abort", tp.getRejectedPolicy().toString());

        tp = camelContext.getExecutorServiceManager().getThreadPoolProfile("myBigPool");
        assertEquals("myBigPool", tp.getId());
        assertEquals(Boolean.FALSE, tp.isDefaultProfile());
        assertEquals("10", tp.getPoolSize().toString());
        assertEquals("200", tp.getMaxPoolSize().toString());
        assertEquals("CallerRuns", tp.getRejectedPolicy().toString());

        main.stop();
    }

    @Test
    public void testDefaultAndCustomThreadPool() throws Exception {
        Main main = new Main();
        main.configure().addRoutesBuilder(new MyRouteBuilder());
        main.addProperty("camel.threadpool.pool-size", "5");
        main.addProperty("camel.threadpool.max-pool-size", "10");
        main.addProperty("camel.threadpool.max-queue-size", "20");
        main.addProperty("camel.threadpool.rejectedPolicy", "CallerRuns");
        main.addProperty("camel.threadpool.config[myPool].id", "myPool");
        main.addProperty("camel.threadpool.config[myPool].pool-size", "1");
        main.addProperty("camel.threadpool.config[myPool].rejectedPolicy", "Abort");
        main.start();

        CamelContext camelContext = main.getCamelContext();
        assertNotNull(camelContext);

        ThreadPoolProfile tp = camelContext.getExecutorServiceManager().getDefaultThreadPoolProfile();
        assertEquals("default", tp.getId());
        assertEquals(Boolean.TRUE, tp.isDefaultProfile());
        assertEquals("5", tp.getPoolSize().toString());
        assertEquals("10", tp.getMaxPoolSize().toString());
        assertEquals("20", tp.getMaxQueueSize().toString());
        assertEquals("CallerRuns", tp.getRejectedPolicy().toString());

        tp = camelContext.getExecutorServiceManager().getThreadPoolProfile("myPool");
        assertEquals("myPool", tp.getId());
        assertEquals(Boolean.FALSE, tp.isDefaultProfile());
        assertEquals("1", tp.getPoolSize().toString());
        assertEquals("10", tp.getMaxPoolSize().toString());
        assertEquals("20", tp.getMaxQueueSize().toString());
        assertEquals("Abort", tp.getRejectedPolicy().toString());

        main.stop();
    }

    public static class MyRouteBuilder extends RouteBuilder {
        @Override
        public void configure() throws Exception {
            from("direct:start").to("seda:foo");
        }
    }

}
