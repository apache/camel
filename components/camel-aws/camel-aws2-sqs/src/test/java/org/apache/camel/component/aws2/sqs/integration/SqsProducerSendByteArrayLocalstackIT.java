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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.bouncycastle.util.Strings;

@Disabled("Not working due localstack update (Incorrect padding error), it is working against real SQS")
public class SqsProducerSendByteArrayLocalstackIT extends Aws2SQSBaseTest {

    @EndpointInject("direct:start")
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @Test
    public void sendInOnly() throws Exception {
        result.expectedMessageCount(1);

        Exchange exchange = template.send("direct:start", ExchangePattern.InOnly, new Processor() {
            public void process(Exchange exchange) {
                byte[] headerValue = "HeaderTest".getBytes();
                exchange.getIn().setHeader("value1", headerValue);
                exchange.getIn().setBody("Test");
            }
        });

        MockEndpoint.assertIsSatisfied(context);
        Assert.assertEquals(3, result.getExchanges().get(0).getMessage().getHeaders().size());
        Assert.assertEquals("HeaderTest",
                Strings.fromByteArray((byte[]) result.getExchanges().get(0).getMessage().getHeaders().get("value1")));
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
