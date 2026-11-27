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
package org.apache.camel.component.seda;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.support.service.ServiceHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A subclass of SedaConsumer that overrides doRun(int) can tell with isCurrentStart(int) whether the start of its
 * polling task is still the current start of the consumer.
 */
class SedaConsumerIsCurrentStartTest extends ContextTestSupport {

    @Test
    void testSubclassSeesItsStartEndAfterARestart() throws Exception {
        SedaEndpoint endpoint = context.getEndpoint("seda:sub?pollTimeout=100", SedaEndpoint.class);
        BlockingQueue<Integer> runStarts = new LinkedBlockingQueue<>();
        SedaConsumer consumer = new SedaConsumer(endpoint, exchange -> {
        }) {
            @Override
            protected void doRun(int start) {
                runStarts.add(start);
                super.doRun(start);
            }
        };
        try {
            ServiceHelper.startService(consumer);
            Integer first = runStarts.poll(5, TimeUnit.SECONDS);
            assertNotNull(first, "the polling task of the first start did not run");
            assertTrue(consumer.isCurrentStart(first));

            ServiceHelper.stopService(consumer);
            ServiceHelper.startService(consumer);
            Integer second = runStarts.poll(5, TimeUnit.SECONDS);
            assertNotNull(second, "the polling task of the second start did not run");
            assertFalse(consumer.isCurrentStart(first), "the first start is still the current one after a restart");
            assertTrue(consumer.isCurrentStart(second));
        } finally {
            ServiceHelper.stopService(consumer);
        }
    }
}
