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
import org.apache.camel.Predicate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.sjms.support.JmsTestSupport;
import org.junit.jupiter.api.Test;

public class BatchConsumerJmsSelectorTest extends JmsTestSupport {

    private static final String SJMS_FROMF_URI_X = "%s?batching=true&messageSelector=cheese='x'";
    private static final String SJMS_FROMF_URI_Y = "%s?batching=true&messageSelector=cheese='y'";
    private static final String SJMS_QUEUE_NAME
            = "sjms:test.b.BatchConsumerJmsSelectorTest";

    @Test
    public void testBatchConsumerJmsSelector() throws Exception {
        MockEndpoint mockResultY = getMockEndpoint("mock:resultY");
        MockEndpoint mockResultX = getMockEndpoint("mock:resultX");

        mockResultY.expectedMessageCount(1); // one batch
        mockResultY.expectedMessagesMatches(batchMatches(2, "cheese", "y"));

        mockResultX.expectedMessageCount(1);
        mockResultX.expectedMessagesMatches(batchMatches(3, "cheese", "x"));

        template.sendBodyAndHeader(SJMS_QUEUE_NAME, "Hello there!", "cheese", "y");
        template.sendBodyAndHeader(SJMS_QUEUE_NAME, "Some x!", "cheese", "x");
        template.sendBodyAndHeader(SJMS_QUEUE_NAME, "Even more x!", "cheese", "x");
        template.sendBodyAndHeader(SJMS_QUEUE_NAME, "Goodbye!", "cheese", "y");
        template.sendBodyAndHeader(SJMS_QUEUE_NAME, "Another x!", "cheese", "x");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                fromF(SJMS_FROMF_URI_Y, SJMS_QUEUE_NAME)
                        .to("log:test-after-y?showAll=true")
                        .to("mock:resultY");

                fromF(SJMS_FROMF_URI_X, SJMS_QUEUE_NAME)
                        .to("log:test-after-x?showAll=true")
                        .to("mock:resultX");
            }
        };
    }

    private Predicate batchMatches(int expectedSize, String headerName, String expectedHeaderValue) {
        return exchange -> {
            List<?> batch = exchange.getIn().getBody(List.class);
            if (batch == null) {
                return false;
            }

            return batch.size() == expectedSize && batch.stream().allMatch(item -> {
                if (item instanceof Exchange) {
                    return expectedHeaderValue.equals(((Exchange) item).getIn().getHeader(headerName, String.class));
                }
                return false;
            });
        };
    }
}
