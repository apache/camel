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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ShutdownRunningTask;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ShutdownCompleteAllTasksTest extends ContextTestSupport {

    public static final String FILE_URI_QUERY = "?initialDelay=0&delay=10&synchronous=true";

    private AtomicInteger counter = new AtomicInteger();
    private CountDownLatch latch = new CountDownLatch(2);

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        String url = fileUri(FILE_URI_QUERY);
        template.sendBodyAndHeader(url, "A", Exchange.FILE_NAME, "a.txt");
        template.sendBodyAndHeader(url, "B", Exchange.FILE_NAME, "b.txt");
        template.sendBodyAndHeader(url, "C", Exchange.FILE_NAME, "c.txt");
        template.sendBodyAndHeader(url, "D", Exchange.FILE_NAME, "d.txt");
        template.sendBodyAndHeader(url, "E", Exchange.FILE_NAME, "e.txt");
    }

    @Test
    public void testShutdownCompleteAllTasks() throws Exception {
        // give it 30 seconds to shutdown
        context.getShutdownStrategy().setTimeout(30);

        // start route
        context.getRouteController().startRoute("foo");

        MockEndpoint bar = getMockEndpoint("mock:bar");
        bar.expectedMinimumMessageCount(1);

        assertMockEndpointsSatisfied();

        int batch = bar.getReceivedExchanges().get(0).getProperty(Exchange.BATCH_SIZE, int.class);

        // wait for latch
        latch.await(10, TimeUnit.SECONDS);

        // shutdown during processing
        context.stop();

        // should route all
        assertEquals(batch, counter.get(), "Should complete all messages");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            // START SNIPPET: e1
            public void configure() throws Exception {
                from(fileUri(FILE_URI_QUERY)).routeId("foo").noAutoStartup()
                        // let it complete all tasks during shutdown
                        .shutdownRunningTask(ShutdownRunningTask.CompleteAllTasks).process(new MyProcessor()).to("mock:bar");
            }
            // END SNIPPET: e1
        };
    }

    public class MyProcessor implements Processor {

        @Override
        public void process(Exchange exchange) throws Exception {
            counter.incrementAndGet();
            latch.countDown();
        }
    }

}
