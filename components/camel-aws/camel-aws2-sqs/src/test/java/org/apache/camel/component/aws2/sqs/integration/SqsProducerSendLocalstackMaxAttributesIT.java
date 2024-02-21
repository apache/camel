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

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Assert;
import org.junit.jupiter.api.Test;

public class SqsProducerSendLocalstackMaxAttributesIT extends Aws2SQSBaseTest {

    @EndpointInject("direct:start")
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @Test
    public void sendInOnly() throws Exception {
        result.expectedMessageCount(1);

        Exchange exchange = template.send("direct:start", ExchangePattern.InOnly, new Processor() {
            public void process(Exchange exchange) {
                exchange.getIn().setHeader("value1", "value1");
                exchange.getIn().setHeader("value2", "value2");
                exchange.getIn().setHeader("value3", "value3");
                exchange.getIn().setHeader("value4", "value4");
                exchange.getIn().setHeader("value5", "value5");
                exchange.getIn().setHeader("value6", "value6");
                exchange.getIn().setHeader("value7", "value7");
                exchange.getIn().setHeader("value8", "value8");
                exchange.getIn().setHeader("value9", "value9");
                exchange.getIn().setHeader("value10", "value10");
                exchange.getIn().setHeader("value11", "value11");
                exchange.getIn().setBody("Test");
            }
        });

        MockEndpoint.assertIsSatisfied(context);
        Assert.assertEquals(13, result.getExchanges().get(0).getMessage().getHeaders().size());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {

        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").startupOrder(2)
                        .toF("aws2-sqs://%s?autoCreateQueue=true", sharedNameGenerator.getName()).to("mock:result");

                fromF("aws2-sqs://%s?deleteAfterRead=true&autoCreateQueue=true", sharedNameGenerator.getName())
                        .startupOrder(1).log("${body}");
            }
        };
    }
}
