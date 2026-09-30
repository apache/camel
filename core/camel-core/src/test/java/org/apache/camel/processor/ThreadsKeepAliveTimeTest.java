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
package org.apache.camel.processor;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class ThreadsKeepAliveTimeTest extends ContextTestSupport {

    @Test
    public void testKeepAliveTime() {
        // a plain number is in the time unit, which is seconds by default
        assertKeepAliveTime("plain", 10);
        assertKeepAliveTime("plainMinutes", 120);
        // a duration is converted to the time unit
        assertKeepAliveTime("duration", 90);
        assertKeepAliveTime("durationSeconds", 90);
        assertKeepAliveTime("durationMillis", 1);
    }

    private void assertKeepAliveTime(String id, long expectedSeconds) {
        ThreadsProcessor threads = context.getProcessor(id, ThreadsProcessor.class);
        ThreadPoolExecutor pool = assertInstanceOf(ThreadPoolExecutor.class, threads.getExecutorService());
        assertEquals(expectedSeconds, pool.getKeepAliveTime(TimeUnit.SECONDS), id);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:plain").threads(1, 2).keepAliveTime(10).id("plain").to("mock:result");
                from("direct:plainMinutes").threads(1, 2).keepAliveTime(2).timeUnit(TimeUnit.MINUTES).id("plainMinutes")
                        .to("mock:result");
                from("direct:duration").threads(1, 2).keepAliveTime("1m30s").id("duration").to("mock:result");
                from("direct:durationSeconds").threads(1, 2).keepAliveTime("1m30s").timeUnit(TimeUnit.SECONDS)
                        .id("durationSeconds").to("mock:result");
                from("direct:durationMillis").threads(1, 2).keepAliveTime("1500ms").id("durationMillis").to("mock:result");
            }
        };
    }
}
