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

import java.util.List;

import org.apache.camel.BindToRegistry;
import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The batchSeparator splits a String body into the messages of the batch: it is a separator, not a regular expression.
 */
class SqsProducerBatchSeparatorTest extends CamelTestSupport {

    @BindToRegistry("client")
    AmazonSQSClientMock mock = new AmazonSQSClientMock();

    @EndpointInject("direct:start")
    private ProducerTemplate template;

    @Test
    void pipeSeparator() {
        template.sendBodyAndHeader("direct:start", "team1|team2|team3", "separator", "pipe");

        assertEquals(List.of("team1", "team2", "team3"), sentBodies());
    }

    @Test
    void dotSeparator() {
        template.sendBodyAndHeader("direct:start", "team1.team2", "separator", "dot");

        assertEquals(List.of("team1", "team2"), sentBodies());
    }

    @Test
    void defaultCommaSeparator() {
        template.sendBodyAndHeader("direct:start", "team1,team2,team3", "separator", "comma");

        assertEquals(List.of("team1", "team2", "team3"), sentBodies());
    }

    private List<String> sentBodies() {
        return mock.getSendMessageBatchRequests().stream()
                .flatMap(request -> request.entries().stream())
                .map(SendMessageBatchRequestEntry::messageBody)
                .toList();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .choice()
                        .when(header("separator").isEqualTo("pipe"))
                        .to("aws2-sqs://camel-1?amazonSQSClient=#client&operation=sendBatchMessage&batchSeparator=|")
                        .when(header("separator").isEqualTo("dot"))
                        .to("aws2-sqs://camel-1?amazonSQSClient=#client&operation=sendBatchMessage&batchSeparator=.")
                        .otherwise()
                        .to("aws2-sqs://camel-1?amazonSQSClient=#client&operation=sendBatchMessage");
            }
        };
    }
}
