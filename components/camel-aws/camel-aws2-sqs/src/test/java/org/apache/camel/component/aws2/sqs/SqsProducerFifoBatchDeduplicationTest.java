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
package org.apache.camel.component.aws2.sqs;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.camel.BindToRegistry;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class SqsProducerFifoBatchDeduplicationTest extends CamelTestSupport {

    @BindToRegistry("client")
    AmazonSQSClientMock mock = new AmazonSQSClientMock();

    @EndpointInject("direct:start")
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @Test
    public void eachBatchEntryGetsADistinctDeduplicationId() throws Exception {
        result.expectedMessageCount(1);

        template.send("direct:start", new Processor() {
            @Override
            public void process(Exchange exchange) {
                Collection<String> c = new ArrayList<>();
                c.add("team1");
                c.add("team2");
                c.add("team3");
                c.add("team4");
                exchange.getIn().setBody(c);
            }
        });
        MockEndpoint.assertIsSatisfied(context);

        List<SendMessageBatchRequest> requests = mock.getSendMessageBatchRequests();
        assertEquals(1, requests.size());
        List<SendMessageBatchRequestEntry> entries = requests.get(0).entries();
        assertEquals(4, entries.size());

        // On a FIFO queue every entry must carry a distinct MessageDeduplicationId, otherwise SQS keeps
        // only the first message of the batch. The group id may legitimately be shared across the batch.
        Set<String> dedupIds = new HashSet<>();
        for (SendMessageBatchRequestEntry entry : entries) {
            assertNotNull(entry.messageDeduplicationId(), "each FIFO batch entry must set a deduplication id");
            assertNotNull(entry.messageGroupId(), "each FIFO batch entry must set a group id");
            dedupIds.add(entry.messageDeduplicationId());
        }
        assertEquals(4, dedupIds.size(), "the four batch entries must have four distinct deduplication ids");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .to("aws2-sqs://camel-1.fifo?amazonSQSClient=#client&operation=sendBatchMessage"
                            + "&messageGroupIdStrategy=useExchangeId")
                        .to("mock:result");
            }
        };
    }
}
