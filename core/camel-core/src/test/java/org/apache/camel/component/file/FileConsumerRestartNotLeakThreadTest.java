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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class FileConsumerRestartNotLeakThreadTest extends ContextTestSupport {

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        deleteDirectory("target/data/leak");
        super.setUp();
    }

    @Test
    public void testLeak() throws Exception {
        int before = Thread.activeCount();

        getMockEndpoint("mock:foo").expectedMessageCount(1);
        template.sendBodyAndHeader("file:target/data/leak", "Hello World", Exchange.FILE_NAME, "hello.txt");
        assertMockEndpointsSatisfied();

        for (int i = 0; i < 50; i++) {
            context.getRouteController().stopRoute("foo");
            context.getRouteController().startRoute("foo");
        }

        resetMocks();

        getMockEndpoint("mock:foo").expectedMessageCount(1);
        template.sendBodyAndHeader("file:target/data/leak", "Bye World", Exchange.FILE_NAME, "bye.txt");
        assertMockEndpointsSatisfied();

        int active = Thread.activeCount() - before;
        log.info("Active threads after restarts: {}", active);

        assertTrue(active < 10, "There should not be so many active threads, was " + active);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file:target/data/leak").routeId("foo").to("mock:foo");
            }
        };
    }
}
