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
package org.apache.camel.processor;

import java.io.InterruptedIOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.AsyncCallback;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version $Revision$
 */
public class ThreadTest extends ContextTestSupport {
    
    protected MockEndpoint resultEndpoint;
    private CountDownLatch continueProcessing = new CountDownLatch(1);
    
    public void testSimpleAsyncThreadCase() throws Exception {

        // Send the exchange using the async completion interface.
        // This call returns before the exchange is completed.
        template.send("direct:a", new Processor() {
            public void process(Exchange exchange) {
                // now lets fire in a message
                Message in = exchange.getIn();
                in.setBody(1);
            }
        }, new AsyncCallback() {
            public void done(boolean doneSynchronously) {
                log.info("Exchange completed.");
            }
        });
        
        // Should not received anything since processing should not be complete.
        resultEndpoint.expectedMessageCount(0);
        resultEndpoint.assertIsSatisfied();
        
        // Release the processing latch..
        continueProcessing.countDown();

        resultEndpoint.expectedMessageCount(1);
        resultEndpoint.assertIsSatisfied();
    }

    public void testSimpleSyncThreadCase() throws Exception {

        // Release the processing latch in an async thread.
        releaseProcessingLatchIn(1000);

        // This call will block until the continueProcessing is released.
        template.send("direct:a", new Processor() {
            public void process(Exchange exchange) {
                // now lets fire in a message
                Message in = exchange.getIn();
                in.setBody(1);
            }
        });

        resultEndpoint.expectedMessageCount(1);
        resultEndpoint.assertIsSatisfied();
    }

    public void testQueuedUpExchangesCompleteOnShutdown() throws Exception {

        int exchangeCount = 10;
        final CountDownLatch completedExchanges = new CountDownLatch(exchangeCount);
        
        final Exchange exchanges[] = new Exchange[exchangeCount]; 
        for (int i = 0; i < exchangeCount; i++) { 
            final int index = i;
            // Send the exchange using the async completion interface.
            // This call returns before the exchange is completed.
            exchanges[i] = template.send("direct:a", new Processor() {
                public void process(Exchange exchange) {
                    // now lets fire in a message
                    Message in = exchange.getIn();
                    in.setBody(1);
                }
            }, new AsyncCallback() {
                public void done(boolean doneSynchronously) {
                    System.out.println("Completed: "+index+", exception: "+exchanges[index].getException());
                    completedExchanges.countDown();
                }
            });
        }
        
        // Should not received anything since processing should not be complete.
        resultEndpoint.expectedMessageCount(0);
        resultEndpoint.assertIsSatisfied();

        // Release it in a sec
        releaseProcessingLatchIn(1000);
        // Make sure we can shut down the context while there are 
        // concurrent requests outstanding.
        stopCamelContext();
        
        // All exchanges should get completed..
        assertTrue(completedExchanges.await(5, TimeUnit.SECONDS));        
    }

    protected void releaseProcessingLatchIn(final long delay) {
        new Thread() {
            @Override
            public void run() {
                try {
                    Thread.sleep(delay);
                    continueProcessing.countDown();
                } catch (InterruptedException e) {
                }
            }
        }.start();
    }
    
    
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        resultEndpoint = getMockEndpoint("mock:result");
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                inheritErrorHandler(false);

                // START SNIPPET: example
                from("direct:a").thread(1).process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        continueProcessing.await();
                    }
                }).to("mock:result");
                // END SNIPPET: example
            }
        };
    }

}
