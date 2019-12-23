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
package org.apache.camel.component.bean;

import java.util.concurrent.atomic.AtomicLong;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.TimeUtils;
import org.junit.Test;

/**
 *
 */
public class BeanPerformanceTest extends ContextTestSupport {

    private static final AtomicLong INVOKED = new AtomicLong();
    private final int times = 100000;

    public static void doSomething(String payload) {
        assertEquals("Hello World", payload);
        INVOKED.incrementAndGet();
    }

    @Test
    public void testBeanPerformance() throws Exception {
        StopWatch watch = new StopWatch();

        log.info("Invoking a bean in a route {} times", times);
        for (int i = 0; i < times; i++) {
            template.sendBody("direct:start", "Hello World");
        }
        log.info("Took {} to invoke the bean {} times", TimeUtils.printDuration(watch.taken()), times);

        assertEquals(times, INVOKED.get());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").bean(BeanPerformanceTest.class, "doSomething");
            }
        };
    }
}
