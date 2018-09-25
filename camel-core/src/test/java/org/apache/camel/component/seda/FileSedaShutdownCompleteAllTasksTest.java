/**
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
package org.apache.camel.component.seda;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ShutdownRunningTask;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Before;
import org.junit.Test;

/**
 * @version 
 */
public class FileSedaShutdownCompleteAllTasksTest extends ContextTestSupport {

    private String url = "file:target/seda?initialDelay=0&delay=10";

    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory("target/seda");
        super.setUp();
    }

    @Test
    public void testShutdownCompleteAllTasks() throws Exception {
        // prepare 5 files to begin with
        template.sendBodyAndHeader(url, "A", Exchange.FILE_NAME, "a.txt");
        template.sendBodyAndHeader(url, "B", Exchange.FILE_NAME, "b.txt");
        template.sendBodyAndHeader(url, "C", Exchange.FILE_NAME, "c.txt");
        template.sendBodyAndHeader(url, "D", Exchange.FILE_NAME, "d.txt");
        template.sendBodyAndHeader(url, "E", Exchange.FILE_NAME, "e.txt");
        
        final CountDownLatch latch = new CountDownLatch(1);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(url).routeId("route1")
                    // let it complete all tasks during shutdown
                    .shutdownRunningTask(ShutdownRunningTask.CompleteAllTasks)
                    .to("log:delay")
                    .to("seda:foo");

                from("seda:foo").routeId("route2")
                    .to("log:bar")
                    .to("mock:bar")
                    .process(new Processor() {
                        boolean first = true;
                        @Override
                        public void process(Exchange exchange) throws Exception {
                            latch.countDown();
                            if (first) {
                                // only sleep on first
                                Thread.sleep(100);
                            }
                            first = false;
                        }
                    });
            }
        });
        context.start();

        MockEndpoint bar = getMockEndpoint("mock:bar");
        bar.expectedMinimumMessageCount(1);

        assertMockEndpointsSatisfied();

        latch.await(5, TimeUnit.SECONDS);

        // shutdown during processing
        context.stop();

        // should route all 5
        assertEquals("Should complete all messages", 5, bar.getReceivedCounter());
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }
}
