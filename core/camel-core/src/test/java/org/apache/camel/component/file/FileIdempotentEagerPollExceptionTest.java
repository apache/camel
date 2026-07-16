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
package org.apache.camel.component.file;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Registry;
import org.junit.jupiter.api.Test;

/**
 * Tests that eagerly-added idempotent keys are drained when pollDirectory throws mid-scan (CAMEL-24090).
 *
 * Scenario: two files exist (a.txt, b.txt); preSort=name guarantees a.txt is scanned first. A filter throws on b.txt
 * during the first poll only. Without the fix, a.txt's eagerly-added idempotent key leaks and the file is never
 * consumed on subsequent polls.
 */
public class FileIdempotentEagerPollExceptionTest extends ContextTestSupport {

    private final AtomicBoolean shouldThrow = new AtomicBoolean(true);

    @Override
    protected Registry createCamelRegistry() throws Exception {
        Registry jndi = super.createCamelRegistry();
        jndi.bind("throwOnceFilter", new ThrowOnceFilter());
        return jndi;
    }

    @Test
    public void testIdempotentKeysDrainedOnPollException() throws Exception {
        template.sendBodyAndHeader(fileUri(), "aaa", Exchange.FILE_NAME, "a.txt");
        template.sendBodyAndHeader(fileUri(), "bbb", Exchange.FILE_NAME, "b.txt");

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(2);

        context.getRouteController().startRoute("testRoute");

        MockEndpoint.assertIsSatisfied(context, 10, TimeUnit.SECONDS);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(fileUri("?noop=true&initialDelay=0&delay=100&preSort=name&filter=#throwOnceFilter"))
                        .routeId("testRoute").noAutoStartup()
                        .convertBodyTo(String.class)
                        .to("mock:result");
            }
        };
    }

    private class ThrowOnceFilter implements GenericFileFilter<Object> {
        @Override
        public boolean accept(GenericFile<Object> file) {
            if (file.isDirectory()) {
                return true;
            }
            if ("b.txt".equals(file.getFileName()) && shouldThrow.compareAndSet(true, false)) {
                throw new RuntimeException("Simulated mid-scan failure");
            }
            return true;
        }
    }
}
