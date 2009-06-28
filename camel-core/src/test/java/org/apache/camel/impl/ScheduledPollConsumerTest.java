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
package org.apache.camel.impl;

import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.PollingConsumerPollStrategy;

public class ScheduledPollConsumerTest extends ContextTestSupport {

    private static boolean rollback;
    
    public void testExceptionOnPollAndCanStartAgain() throws Exception {

        final Exception expectedException = new Exception("Hello, I should be thrown on shutdown only!");
        MockScheduledPollConsumer consumer = new MockScheduledPollConsumer(expectedException);

        consumer.setPollStrategy(new PollingConsumerPollStrategy() {
            public void begin(Consumer consumer, Endpoint endpoint) {
            }

            public void commit(Consumer consumer, Endpoint endpoint) {
            }

            public void rollback(Consumer consumer, Endpoint endpoint, Exception e) throws Exception {
                if (e == expectedException) {
                    rollback = true;
                }

            }
        });

        consumer.start();
        // poll that throws an exception
        consumer.run();
        consumer.stop();

        assertEquals("Should have rollback", true, rollback);

        // prepare for 2nd run but this time it should not thrown an exception on poll
        rollback = false;
        consumer.setExceptionToThrowOnPoll(null);
        // start it again and we should be able to run
        consumer.start();
        consumer.run();
        // should be able to stop with no problem
        consumer.stop();

        assertEquals("Should not have rollback", false, rollback);
    }
    
    public void testNoExceptionOnPoll() throws Exception {
        MockScheduledPollConsumer consumer = new MockScheduledPollConsumer(null);
        consumer.start();
        consumer.run(); 
        consumer.stop();
    }

}
