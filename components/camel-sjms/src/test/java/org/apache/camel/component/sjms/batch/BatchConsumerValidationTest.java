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
package org.apache.camel.component.sjms.batch;

import org.apache.camel.Consumer;
import org.apache.camel.component.sjms.SjmsEndpoint;
import org.apache.camel.component.sjms.support.JmsTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BatchConsumerValidationTest extends JmsTestSupport {

    private static final String BASE = "sjms:queue:batch.consumer.BatchConsumerValidationTest";

    private SjmsEndpoint endpoint(String query) {
        return context.getEndpoint(BASE + "?" + query, SjmsEndpoint.class);
    }

    @Test
    public void testInOutRejectedInBatchingMode() {
        SjmsEndpoint endpoint = endpoint("batching=true&exchangePattern=InOut");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> endpoint.createConsumer(exchange -> {
                }));

        assertTrue(e.getMessage().contains("InOut"), e.getMessage());
    }

    @Test
    public void testInOutStillAllowedWithoutBatching() throws Exception {
        // regression guard: the new check must not leak into the regular consumer
        Consumer consumer = endpoint("batching=false&exchangePattern=InOut").createConsumer(exchange -> {
        });
        assertNotNull(consumer);
    }

    @Test
    public void testNegativeBatchIntervalRejected() {
        SjmsEndpoint endpoint = endpoint("batching=true&batchInterval=-1");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> endpoint.createConsumer(exchange -> {
                }));

        assertTrue(e.getMessage().contains("batchInterval"), e.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = { "batchSize=0&batchInterval=0", "batchSize=-1&batchInterval=0" })
    public void testNoDispatchTriggerRejected(String query) {
        SjmsEndpoint endpoint = endpoint("batching=true&" + query);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> endpoint.createConsumer(exchange -> {
                }));

        assertTrue(e.getMessage().contains("batchSize") || e.getMessage().contains("batchInterval"), e.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "&batchSize=5&batchInterval=0", // size trigger only
            "&batchSize=5&batchInterval=500", // both
            "" // defaults (100 / 1000)
    })
    public void testValidCombinationsAccepted(String query) throws Exception {
        Consumer consumer = endpoint("batching=true" + query).createConsumer(exchange -> {
        });
        assertNotNull(consumer);
    }
}
