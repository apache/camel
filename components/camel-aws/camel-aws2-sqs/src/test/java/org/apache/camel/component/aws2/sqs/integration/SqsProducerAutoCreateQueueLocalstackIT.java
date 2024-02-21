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
package org.apache.camel.component.aws2.sqs.integration;

import java.util.ArrayList;
import java.util.Collection;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws2.sqs.Sqs2Constants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.aws2.clients.AWSSDKClientUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;

public class SqsProducerAutoCreateQueueLocalstackIT extends Aws2SQSBaseTest {

    @EndpointInject("direct:start")
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        super.beforeEach(context);

        SqsClient client = AWSSDKClientUtils.newSQSClient();
        for (int i = 0; i < 2000; i++) {
            client.createQueue(CreateQueueRequest.builder().queueName("queue-" + String.valueOf(i)).build());
        }
    }

    @Test
    public void sendInOnly() throws Exception {
        result.expectedMessageCount(5);

        Exchange exchange = template.send("direct:start", ExchangePattern.InOnly, new Processor() {
            public void process(Exchange exchange) {
                Collection c = new ArrayList<Integer>();
                c.add("1");
                c.add("2");
                c.add("3");
                c.add("4");
                c.add("5");
                exchange.getIn().setBody(c);
            }
        });

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {

        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").startupOrder(2).setHeader(Sqs2Constants.SQS_OPERATION, constant("sendBatchMessage"))
                        .toF("aws2-sqs://%s?autoCreateQueue=true", "queue-2001");

                fromF("aws2-sqs://%s?deleteAfterRead=true&autoCreateQueue=true", "queue-2001")
                        .startupOrder(1).log("${body}").to("mock:result");
            }
        };
    }
}
