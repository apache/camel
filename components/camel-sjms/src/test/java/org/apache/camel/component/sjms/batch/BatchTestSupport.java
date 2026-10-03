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

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.sjms.SjmsConstants;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SuppressWarnings("unchecked")
public final class BatchTestSupport {

    private BatchTestSupport() {
    }

    public static void sendMessages(ProducerTemplate template, String endpoint, int count) {
        for (int i = 0; i < count; i++) {
            String message = "Hello World " + i;
            template.sendBody(endpoint, message);
        }
    }

    /** Extracts the batch body as a List<Exchange> from a batch Exchange, asserting the type. */
    public static List<Exchange> getBatch(Exchange batchExchange) {
        List<Exchange> body = batchExchange.getIn().getBody(List.class);
        assertNotNull(body, "batch exchange body was null");
        for (Object o : body) {
            assertInstanceOf(Exchange.class, o, "batch element was not an Exchange: " + o);
        }
        return body;
    }

    /** Asserts a single batch exchange has exactly the given number of messages. */
    public static void assertBatchSize(Exchange batchExchange, int expectedSize) {
        List<Exchange> batch = getBatch(batchExchange);
        assertEquals(expectedSize, batch.size(), "unexpected batch size");
        assertEquals(expectedSize,
                batchExchange.getIn().getHeader(SjmsConstants.SJMS_BATCH_SIZE_HEADER, Integer.class),
                "CamelSjmsBatchSize header did not match actual batch size");
    }

    /**
     * Asserts the mock received exactly expectedSizes.length batch exchanges, in order, with each batch's size matching
     * the corresponding element. Requires concurrentConsumers=1 (or an otherwise deterministic single-worker setup) so
     * that arrival order is meaningful.
     */
    public static void assertBatchSizesInOrder(MockEndpoint mock, int... expectedSizes) {
        assertEquals(mock.getExchanges().size(), expectedSizes.length, "More mock exchanges than provided batch sizes.");
        List<Exchange> received = mock.getExchanges();
        for (int i = 0; i < expectedSizes.length; i++) {
            assertBatchSize(received.get(i), expectedSizes[i]);
        }
    }
}
