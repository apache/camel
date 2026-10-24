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
import java.util.List;

import org.apache.camel.BindToRegistry;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SqsProducerBatchTest extends CamelTestSupport {

    @BindToRegistry("client")
    AmazonSQSClientMock mock = new AmazonSQSClientMock();

    @EndpointInject("direct:start")
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @Test
    public void sendBatchMessage() throws Exception {
        result.expectedMessageCount(1);

        template.send("direct:start", new Processor() {

            @Override
            public void process(Exchange exchange) {
                Collection c = new ArrayList<Integer>();
                c.add("team1");
                c.add("team2");
                c.add("team3");
                c.add("team4");
                exchange.getIn().setBody(c);
            }
        });
        MockEndpoint.assertIsSatisfied(context);
        SendMessageBatchResponse res = result.getExchanges().get(0).getIn().getBody(SendMessageBatchResponse.class);
        assertEquals(2, res.failed().size());
        assertEquals(2, res.successful().size());
    }

    @Test
    public void sendBatchMessageOfMoreThanTenEntriesHasTheResultsOfAllRequests() throws Exception {
        result.expectedMessageCount(1);

        List<String> body = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            body.add("message" + i);
        }
        template.sendBody("direct:start", body);

        MockEndpoint.assertIsSatisfied(context);
        // 25 entries are sent in 3 requests, and the mock answers each request with 2 successful and 2 failed entries
        assertEquals(3, mock.getSendMessageBatchRequests().size());
        Exchange out = result.getExchanges().get(0);
        SendMessageBatchResponse res = out.getIn().getBody(SendMessageBatchResponse.class);
        assertEquals(6, res.successful().size());
        assertEquals(6, res.failed().size());
        assertEquals(6, out.getIn().getHeader(Sqs2Constants.SUCCESSFUL_MESSAGE_COUNT));
        assertEquals(6, out.getIn().getHeader(Sqs2Constants.FAILED_MESSAGE_COUNT));
    }

    @Test
    public void sendBatchMessageConvertsElementsToString() throws Exception {
        result.expectedMessageCount(1);

        template.sendBody("direct:start", List.of(1, 2, "three"));

        MockEndpoint.assertIsSatisfied(context);
        List<SendMessageBatchRequest> requests = mock.getSendMessageBatchRequests();
        assertEquals(1, requests.size());
        List<String> bodies = requests.get(0).entries().stream().map(SendMessageBatchRequestEntry::messageBody).toList();
        assertEquals(List.of("1", "2", "three"), bodies);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to("aws2-sqs://camel-1?amazonSQSClient=#client&operation=sendBatchMessage")
                        .to("mock:result");
            }
        };
    }

}
