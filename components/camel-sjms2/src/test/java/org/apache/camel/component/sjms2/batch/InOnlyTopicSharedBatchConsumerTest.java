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
package org.apache.camel.component.sjms2.batch;

import java.util.ArrayList;
import java.util.List;

import jakarta.jms.ConnectionFactory;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.sjms2.support.Jms2TestSupport;
import org.apache.camel.test.infra.artemis.services.ArtemisService;
import org.apache.camel.test.infra.artemis.services.ArtemisServiceFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class InOnlyTopicSharedBatchConsumerTest extends Jms2TestSupport {

    private static final String TEST_DESTINATION_NAME = "sjms2:topic:in.only.topic.batch.consumer.test";
    @RegisterExtension
    public static ArtemisService service = ArtemisServiceFactory.createTCPAllProtocolsService();

    @Test
    public void testSynchronous() throws Exception {
        final String expectedBody = "Hello World!";
        MockEndpoint mock1 = getMockEndpoint("mock:result");
        mock1.expectedMessageCount(1);

        MockEndpoint mock2 = getMockEndpoint("mock:result2");
        mock2.expectedMessageCount(1);

        MockEndpoint mock3 = getMockEndpoint("mock:result3");
        mock3.expectedMessageCount(1);

        for (int i = 0; i < 5; i++) {
            template.sendBody("direct:start", expectedBody);
        }

        MockEndpoint.assertIsSatisfied(context);

        List<?> batch1 = assertBatchBody(mock1, expectedBody);
        assertEquals(5, batch1.size());
        List<?> batch2 = assertBatchBody(mock2, expectedBody);
        List<?> batch3 = assertBatchBody(mock3, expectedBody);
        assertEquals(5, batch2.size() + batch3.size());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .to(TEST_DESTINATION_NAME);

                from(TEST_DESTINATION_NAME + "?batching=true&batchSize=5&batchInterval=5000")
                        .to("log:test.log.1?showBody=true", "mock:result");

                from(TEST_DESTINATION_NAME + "?batching=true&batchSize=5&batchInterval=5000" +
                     "&subscriptionId=sharedTest&shared=true")
                        .to("log:test.log.1?showBody=true", "mock:result2");

                from(TEST_DESTINATION_NAME + "?batching=true&batchSize=5&batchInterval=5000" +
                     "&subscriptionId=sharedTest&shared=true")
                        .to("log:test.log.1?showBody=true", "mock:result3");
            }
        };
    }

    protected ConnectionFactory getConnectionFactory() throws Exception {
        return getConnectionFactory(service.serviceAddress());
    }

    private List<String> assertBatchBody(MockEndpoint mock, String expectedBody) {
        List<String> result = new ArrayList<>();

        for (Exchange exchange : mock.getExchanges()) {
            List<?> batch = exchange.getMessage().getBody(List.class);

            assertNotNull(batch);
            assertTrue(batch.stream()
                    .map(Exchange.class::cast)
                    .map(e -> e.getMessage().getBody(String.class))
                    .allMatch(expectedBody::equals));

            result.addAll(batch.stream()
                    .map(Exchange.class::cast)
                    .map(e -> e.getMessage().getBody(String.class))
                    .toList());
        }

        return result;
    }
}
