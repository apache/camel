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
package org.apache.camel.component.aws2.sns.integration;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.infra.common.SharedNameGenerator;
import org.apache.camel.test.infra.common.TestEntityNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;
import software.amazon.awssdk.services.sns.model.PublishBatchRequestEntry;
import software.amazon.awssdk.services.sns.model.PublishBatchResponse;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisabledIfSystemProperty(named = "ci.env.name", matches = "github.com", disabledReason = "Flaky on GitHub Actions")
public class SnsTopicProducerBatchIT extends Aws2SNSBase {

    @RegisterExtension
    public static SharedNameGenerator sharedNameGenerator = new TestEntityNameGenerator();

    @Test
    public void sendInOnly() {
        Exchange exchange = template.send("direct:start", ExchangePattern.InOnly, new Processor() {
            public void process(Exchange exchange) {
                PublishBatchRequestEntry publishBatchRequestEntry1 = PublishBatchRequestEntry.builder()
                        .id("message1")
                        .message("This is message 1")
                        .build();

                PublishBatchRequestEntry publishBatchRequestEntry2 = PublishBatchRequestEntry.builder()
                        .id("message2")
                        .message("This is message 2")
                        .build();

                PublishBatchRequestEntry publishBatchRequestEntry3 = PublishBatchRequestEntry.builder()
                        .id("message3")
                        .message("This is message 3")
                        .build();

                List<PublishBatchRequestEntry> pubList = new ArrayList<>();
                pubList.add(publishBatchRequestEntry1);
                pubList.add(publishBatchRequestEntry2);
                pubList.add(publishBatchRequestEntry3);
                exchange.getIn().setBody(pubList);
            }
        });

        assertNotNull(exchange.getMessage().getBody(PublishBatchResponse.class));
        System.err.println(exchange.getMessage().getBody(PublishBatchResponse.class).toString());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .toF("aws2-sns://%s?subject=The+subject+message&autoCreateTopic=true&batchEnabled=true",
                                sharedNameGenerator.getName());
            }
        };
    }
}
