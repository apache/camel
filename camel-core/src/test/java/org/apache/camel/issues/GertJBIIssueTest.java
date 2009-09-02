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
package org.apache.camel.issues;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.WaitForTaskToComplete;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.SynchronizationAdapter;

/**
 * @version $Revision$
 */
public class GertJBIIssueTest extends ContextTestSupport {

    private static Exception cause;

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    public void testSimulateJBIEndpointNotExistWait() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("mock:dlc").maximumRedeliveries(0).handled(false));

                from("direct:start")
                    // must wait for task to complete to know if there was an exception
                    // as its in-only based
                    .threads(2).waitForTaskToComplete(WaitForTaskToComplete.Always)
                    .to("mock:done")
                    .throwException(new IllegalArgumentException("Forced"));

            }
        });
        context.start();

        getMockEndpoint("mock:done").expectedMessageCount(1);
        getMockEndpoint("mock:dlc").expectedMessageCount(1);

        try {
            template.sendBody("direct:start", "Hello World");
            fail("Should thrown exception");
        } catch (Exception e) {
            Exception cause = assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
            assertEquals("Forced", cause.getMessage());
        }

        assertMockEndpointsSatisfied();
    }

    public void testSimulateJBIEndpointNotExist() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("mock:dlc").maximumRedeliveries(0).handled(false));

                from("direct:start")
                    // now we do not wait for task to complete but we get the future handle
                    // so we can use that to wait for the task to complete and see if it failed or not
                    .threads(2)
                    .to("mock:done")
                    .throwException(new IllegalArgumentException("Forced"));

            }
        });
        context.start();

        getMockEndpoint("mock:done").expectedMessageCount(1);
        getMockEndpoint("mock:dlc").expectedMessageCount(1);

        Exchange out = template.send("direct:start", new Processor() {
                public void process(Exchange exchange) throws Exception {
                    exchange.getIn().setBody("Hello World");
                }
            });

        assertMockEndpointsSatisfied();

        // we send in an in-only that was processed async. Then Camel provides the Future handle in OUT
        // that let us use it to check whether the task completed or failed (or even get the result etc.)
        Future<Exchange> future = out.getOut().getBody(Future.class);
        Exchange task = future.get();
        assertEquals("Should have failed", true, task.isFailed());

        Exception cause = assertIsInstanceOf(IllegalArgumentException.class, task.getException());
        assertEquals("Forced", cause.getMessage());
    }

    public void testSimulateJBIEndpointNotExistOnCompletion() throws Exception {
        cause = null;
        
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("mock:dlc").maximumRedeliveries(0).handled(false));

                from("direct:start")
                    // must wait for task to complete to know if there was an exception
                    // as its in-only based
                    .threads(2).waitForTaskToComplete(WaitForTaskToComplete.Always)
                    .to("mock:done")
                    .throwException(new IllegalArgumentException("Forced"));

            }
        });
        context.start();

        getMockEndpoint("mock:done").expectedMessageCount(1);
        getMockEndpoint("mock:dlc").expectedMessageCount(1);

        final CountDownLatch latch = new CountDownLatch(1);

        template.send("direct:start", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.addOnCompletion(new SynchronizationAdapter() {
                    @Override
                    public void onDone(Exchange exchange) {
                        cause = exchange.getException();
                        latch.countDown();
                    }
                });
            }
        });

        latch.await(10, TimeUnit.SECONDS);

        assertNotNull("Should have failed", cause);
        assertIsInstanceOf(IllegalArgumentException.class, cause);
        assertEquals("Forced", cause.getMessage());
    }

}
