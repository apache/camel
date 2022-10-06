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
package org.apache.camel.component.aws2.eventbridge.localstack;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws2.eventbridge.EventbridgeConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Assert;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResponse;

public class EventbridgePutEventsIT extends Aws2EventbridgeBase {

    @EndpointInject
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @Test
    public void sendIn() throws Exception {
        result.expectedMessageCount(1);

        template.send("direct:evs-events", new Processor() {

            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(EventbridgeConstants.EVENT_RESOURCES_ARN,
                        "arn:aws:sqs:eu-west-1:780410022472:camel-connector-test");
                exchange.getIn().setHeader(EventbridgeConstants.EVENT_SOURCE, "com.pippo");
                exchange.getIn().setHeader(EventbridgeConstants.EVENT_DETAIL_TYPE, "peppe");
                exchange.getIn().setBody("Test Event");
            }
        });
        MockEndpoint.assertIsSatisfied(context);
        Assert.assertTrue(result.getExchanges().get(0).getMessage().getBody(PutEventsResponse.class).hasEntries());
        Assert.assertEquals(1, result.getExchanges().get(0).getMessage().getBody(PutEventsResponse.class).entries().size());
        Assert.assertNotNull(
                result.getExchanges().get(0).getMessage().getBody(PutEventsResponse.class).entries().get(0).eventId());

    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                String event = "aws2-eventbridge://default?operation=putEvent";
                from("direct:evs-events").to(event).log("${body}").to("mock:result");
            }
        };
    }
}
