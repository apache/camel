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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.Throttler.TimeSlot;

/**
 * @version $Revision$
 */
public class ThrottlerTest extends ContextTestSupport {
    private static final int INTERVAL = 500;
    protected int messageCount = 6;

    public void testSendLotsOfMessagesButOnly3GetThrough() throws Exception {
        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectedMessageCount(3);
        resultEndpoint.setResultWaitTime(1000);

        for (int i = 0; i < messageCount; i++) {
            template.sendBody("seda:a", "<message>" + i + "</message>");
        }

        // lets pause to give the requests time to be processed
        // to check that the throttle really does kick in
        resultEndpoint.assertIsSatisfied();
    }
    
    public void testSendLotsOfMessagesSimultaneouslyButOnly3GetThrough() throws Exception {
        long start = System.currentTimeMillis();
        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectedMessageCount(messageCount);

        ExecutorService executor = Executors.newFixedThreadPool(messageCount);
        for (int i = 0; i < messageCount; i++) {
            executor.execute(new Runnable() {
                public void run() {
                    template.sendBody("direct:a", "<message>payload</message>");
                }                
            });
        }
        
        // let's wait for the exchanges to arrive
        resultEndpoint.assertIsSatisfied();
        
        // now assert that they have actually been throttled
        long minimumTime = (messageCount - 1) * INTERVAL;
        assertTrue("Should take at least " + minimumTime + "ms", System.currentTimeMillis() - start >= minimumTime);
    }
    
    public void testTimeSlotCalculus() throws Exception {
        Throttler throttler = new Throttler(null, 2, 1000);
        TimeSlot slot = throttler.nextSlot();
        // start a new time slot
        assertNotNull(slot);
        // make sure the same slot is used (2 exchanges per slot)
        assertSame(slot, throttler.nextSlot());
        assertTrue(slot.isFull());
        
        TimeSlot next = throttler.nextSlot();
        // now we should have a new slot that starts somewhere in the future
        assertNotSame(slot, next);
        assertFalse(next.isActive());
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // START SNIPPET: ex
                from("seda:a").throttler(3).timePeriodMillis(10000).to("mock:result");
                // END SNIPPET: ex
                
                from("direct:a").throttler(1).timePeriodMillis(INTERVAL).to("mock:result");
            }
        };
    }
}
