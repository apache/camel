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


import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;

/**
 * Tests that a Seda producer supports the blockWhenFull option by blocking
 * when a message is sent while the queue is full.
 */
public class SedaBlockWhenFullTest extends ContextTestSupport {
    private static final int QUEUE_SIZE = 1;
    private static final int DELAY = 10;
    private static final int DELAY_LONG = 100;
    private static final String MOCK_URI = "mock:blockWhenFullOutput";
    private static final String SIZE_PARAM = "?size=%d";
    private static final String BLOCK_WHEN_FULL_URI = "seda:blockingFoo" + String.format(SIZE_PARAM, QUEUE_SIZE) + "&blockWhenFull=true&timeout=0";
    private static final String DEFAULT_URI = "seda:foo" + String.format(SIZE_PARAM, QUEUE_SIZE);

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(BLOCK_WHEN_FULL_URI).delay(DELAY_LONG).to(MOCK_URI);

                // use same delay as above on purpose
                from(DEFAULT_URI).delay(DELAY).to("mock:whatever");
            }
        };
    }

    public void testSedaDefaultWhenFull() throws Exception {
        try {
            SedaEndpoint seda = context.getEndpoint(DEFAULT_URI, SedaEndpoint.class);
            assertFalse("Seda Endpoint is not setting the correct default (should be false) for \"blockWhenFull\"", seda.isBlockWhenFull());

            sendTwoOverCapacity(DEFAULT_URI, QUEUE_SIZE);

            fail("The route didn't fill the queue beyond capacity: test class isn't working as intended");
        } catch (Exception e) {
            assertIsInstanceOf(IllegalStateException.class, e.getCause());
        }
    }

    public void testSedaBlockingWhenFull() throws Exception {
        getMockEndpoint(MOCK_URI).setExpectedMessageCount(QUEUE_SIZE + 2);
        
        SedaEndpoint seda = context.getEndpoint(BLOCK_WHEN_FULL_URI, SedaEndpoint.class);
        assertEquals(QUEUE_SIZE, seda.getQueue().remainingCapacity());

        sendTwoOverCapacity(BLOCK_WHEN_FULL_URI, QUEUE_SIZE);
        assertMockEndpointsSatisfied();
    }
    
    public void testAsyncSedaBlockingWhenFull() throws Exception {
        getMockEndpoint(MOCK_URI).setExpectedMessageCount(QUEUE_SIZE + 1);
        getMockEndpoint(MOCK_URI).setResultWaitTime(DELAY_LONG * 3);

        SedaEndpoint seda = context.getEndpoint(BLOCK_WHEN_FULL_URI, SedaEndpoint.class);
        assertEquals(QUEUE_SIZE, seda.getQueue().remainingCapacity());

        asyncSendTwoOverCapacity(BLOCK_WHEN_FULL_URI, QUEUE_SIZE + 4);
        assertMockEndpointsSatisfied();
    }

    /**
     * This method make sure that we hit the limit by sending two msg over the
     * given capacity which allows the delayer to kick in, leaving the 2nd msg
     * in the queue, blocking/throwing on the third one.
     */
    private void sendTwoOverCapacity(String uri, int capacity) {
        for (int i = 0; i < (capacity + 2); i++) {
            template.sendBody(uri, "Message " + i);
        }
    }
    
    private void asyncSendTwoOverCapacity(String uri, int capacity) {
        for (int i = 0; i < (capacity + 2); i++) {
            template.asyncSendBody(uri, "Message " + i);
        }
    }

}
