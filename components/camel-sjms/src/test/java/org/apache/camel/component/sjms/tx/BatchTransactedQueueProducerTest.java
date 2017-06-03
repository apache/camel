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
package org.apache.camel.component.sjms.tx;

import org.apache.camel.component.sjms.BatchMessage;
import org.junit.Test;

/**
 * Test used to verify the batch transaction capability of the SJMS Component
 * for a Queue Producer.
 */
public class BatchTransactedQueueProducerTest extends BatchTransactedProducerSupport {
    private static final String BROKER_URI = "vm://btqpt_test_broker?broker.persistent=false&broker.useJmx=false";

    /**
     * Verify that after processing a {@link BatchMessage} twice with 30
     * messages in for a total of 60 delivery attempts that we only see 30
     * messages end up at the final consumer. This is due to an exception being
     * thrown during the processing of the first 30 messages which causes a
     * redelivery.
     * 
     * @throws Exception
     */
    @Test
    public void testRoute() throws Exception {
        final String destinationName = "sjms:queue:one.consumer.one.route.batch.tx.test";
        int consumerRouteCount = 1;
        int messageCount = 20;
        int totalAttempts = 2;
        runTest(destinationName, consumerRouteCount, messageCount, totalAttempts);
    }
    
    @Override
    public String getBrokerUri() {
        return BROKER_URI;
    }
}
