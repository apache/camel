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
package org.apache.camel.component.sjms.tx;

import org.junit.jupiter.api.Test;

public class TransactedQueueConsumerTest extends TransactedConsumerSupport {

    /**
     * We want to verify that when consuming from a single destination with multiple routes that we are thread safe and
     * behave accordingly.
     */
    @Test
    public void testRoute() throws Exception {
        final String destinationName = "sjms:queue:one.consumer.one.route.tx.test.TransactedQueueConsumerTest";
        int routeCount = 1;
        int concurrentConsumers = 1;
        int messageCount = 20;
        int maxAttemptsCount = 10;
        int totalRedeliverdFalse = 20;
        int totalRedeliveredTrue = 1;
        runTest(destinationName, routeCount, messageCount, totalRedeliverdFalse, totalRedeliveredTrue,
                concurrentConsumers, maxAttemptsCount);
    }

    @Override
    public String getBrokerUri() {
        return service.serviceAddress();
    }
}
