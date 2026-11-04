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
package org.apache.camel.component.aws2.sqs.transform;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.cloudevents.CloudEvent;
import org.apache.camel.component.aws2.sqs.AmazonSQSClientMock;
import org.apache.camel.component.aws2.sqs.Sqs2Constants;
import org.apache.camel.component.aws2.sqs.Sqs2Consumer;
import org.apache.camel.component.aws2.sqs.Sqs2Endpoint;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.DataType;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.model.Message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The CloudEvent source of a message of the aws2-sqs consumer, when the transformer runs in another route, as with the
 * output data type of a Kamelet: that route gets a copy of the exchange with the headers of the consumer, and its own
 * from endpoint.
 */
class AWS2SQSCloudEventDataTypeTransformerRouteTest extends CamelTestSupport {

    @BindToRegistry("amazonSQSClient")
    private final AmazonSQSClientMock client = new AmazonSQSClientMock();

    @Test
    void theSourceIsTheQueueOfTheConsumer() throws Exception {
        // CAMEL-26300: the source was built from the receipt handle, which changes each time the message is received
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedBodiesReceived("Hello");
        result.expectedHeaderReceived(Sqs2Constants.QUEUE_NAME, "MyQueue");
        result.expectedHeaderReceived(CloudEvent.CAMEL_CLOUD_EVENT_SOURCE, "aws.sqs.queue.MyQueue");

        template.sendBody("direct:start", "Hello");

        MockEndpoint.assertIsSatisfied(context);
        Exchange received = result.getReceivedExchanges().get(0);
        assertNotNull(received.getMessage().getHeader(Sqs2Constants.RECEIPT_HANDLE));
        assertEquals("org.apache.camel.event.aws.sqs.receiveMessage",
                received.getMessage().getHeader(CloudEvent.CAMEL_CLOUD_EVENT_TYPE));
        assertEquals(received.getMessage().getHeader(Sqs2Constants.MESSAGE_ID),
                received.getMessage().getHeader(CloudEvent.CAMEL_CLOUD_EVENT_SUBJECT));
    }

    @Test
    void theSourceIsTheQueueNameOfAnArn() throws Exception {
        Sqs2Endpoint endpoint = context.getEndpoint(
                "aws2-sqs://arn:aws:sqs:eu-west-1:123456789012:MyArnQueue?amazonSQSClient=#amazonSQSClient",
                Sqs2Endpoint.class);
        Sqs2Consumer consumer = (Sqs2Consumer) endpoint.createConsumer(exchange -> {
        });
        Exchange exchange = consumer.createExchange(
                Message.builder().body("Hello").messageId("1234").receiptHandle("AQEBwJnKyrHigUMZj6rYigCgxlaS3SLy0a").build());
        assertEquals("MyArnQueue", exchange.getMessage().getHeader(Sqs2Constants.QUEUE_NAME));

        new AWS2SQSCloudEventDataTypeTransformer().transform(exchange.getMessage(), DataType.ANY, DataType.ANY);
        assertEquals("aws.sqs.queue.MyArnQueue", exchange.getMessage().getHeader(CloudEvent.CAMEL_CLOUD_EVENT_SOURCE));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to("aws2-sqs://MyQueue?amazonSQSClient=#amazonSQSClient");

                // seda hands a copy of the exchange to the next route, as the sink of a Kamelet does
                from("aws2-sqs://MyQueue?amazonSQSClient=#amazonSQSClient").to("seda:next");

                from("seda:next")
                        .transformDataType("aws2-sqs:application-cloudevents")
                        .to("mock:result");
            }
        };
    }
}
