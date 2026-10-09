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

import java.util.Collections;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.sjms.support.JmsTestSupport;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.sjms.batch.BatchTestHelper.DEFAULT_MESSAGE_TEXT;
import static org.apache.camel.component.sjms.batch.BatchTestHelper.assertBatchSizesInOrder;
import static org.apache.camel.component.sjms.batch.BatchTestHelper.getBatchBodiesAsString;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class BatchConsumerSizeTest extends JmsTestSupport {

    private static final String SJMS_FROMF_URI = "%s?batching=true&batchSize=5&batchInterval=5000";
    private static final String SJMS_QUEUE_NAME
            = "sjms:queue:batch.consumer.queue.BatchConsumerSizeTest";
    private static final String MOCK_RESULT = "mock:result";

    @Test
    public void testBatchConsumerSize() throws Exception {
        MockEndpoint mock = getMockEndpoint(MOCK_RESULT);
        mock.expectedMessageCount(2);

        BatchTestHelper.sendMessages(template, SJMS_QUEUE_NAME, 7);

        mock.assertIsSatisfied();
        assertBatchSizesInOrder(mock, 5, 2);
        assertEquals(Collections.nCopies(5, DEFAULT_MESSAGE_TEXT),
                getBatchBodiesAsString(mock.getExchanges().get(0)));
        assertEquals(Collections.nCopies(2, DEFAULT_MESSAGE_TEXT),
                getBatchBodiesAsString(mock.getExchanges().get(1)));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                fromF(SJMS_FROMF_URI, SJMS_QUEUE_NAME)
                        .to(MOCK_RESULT);
            }
        };
    }
}
