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
package org.apache.camel.component.sjms.threadpool;

import java.lang.management.ManagementFactory;
import java.util.Set;

import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectInstance;
import javax.management.ObjectName;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.sjms.support.JmsTestSupport;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit test for CAMEL-7715.
 *
 */
@Disabled("TODO: investigate for Camel 3.0")
public class ThreadPoolTest extends JmsTestSupport {

    private static final Logger LOGGER = LoggerFactory.getLogger(ThreadPoolTest.class);
    private static final String FROM_ROUTE = "from";
    private static final String TO_ROUTE = "to";

    @Override
    protected boolean useJmx() {
        return true;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to("sjms:queue:foo.ThreadPoolTest").routeId(FROM_ROUTE);
                from("sjms:queue:foo.ThreadPoolTest").to("log:test.log.1?showBody=true").routeId(TO_ROUTE);
            }
        };
    }

    /**
     * Test that only 2 thread pools are created on start
     *
     * @throws Exception
     */
    @Test
    public void testContextStart() throws Exception {
        assertProducerThreadPoolCount(1);
        assertConsumerThreadPoolCount(1);
    }

    /**
     * Test that ThreadPool is removed when producer is removed
     *
     * @throws Exception
     */
    @Test
    public void testProducerThreadThreadPoolRemoved() throws Exception {
        context.getRouteController().stopRoute(FROM_ROUTE);
        assertProducerThreadPoolCount(0);
    }

    /**
     * Test that ThreadPool is removed when consumer is removed
     *
     * @throws Exception
     */
    @Test
    public void testConsumerThreadThreadPoolRemoved() throws Exception {
        context.getRouteController().stopRoute(TO_ROUTE);
        assertConsumerThreadPoolCount(0);
    }

    private void assertProducerThreadPoolCount(final int count) throws Exception {
        assertEquals(count, getMbeanCount("\"InOnlyProducer"));
    }

    private void assertConsumerThreadPoolCount(final int count) throws Exception {
        assertEquals(count, getMbeanCount("\"SjmsConsumer"));
    }

    private int getMbeanCount(final String name) throws MalformedObjectNameException {
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        Set<ObjectInstance> mbeans = mbs.queryMBeans(new ObjectName("org.apache.camel:type=threadpools,*"), null);
        LOGGER.debug("mbeans size: " + mbeans.size());
        int count = 0;
        for (ObjectInstance mbean : mbeans) {
            LOGGER.debug("mbean: {}", mbean);
            if (mbean.getObjectName().getKeyProperty("name").startsWith(name)) {
                count++;
            }
        }
        return count;
    }

}
