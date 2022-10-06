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
import org.apache.camel.component.aws2.sqs.Sqs2Constants;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class SqsComponentLocalstackIT extends Aws2SQSBaseTest {

    @EndpointInject("direct:start")
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @Test
    public void sendInOnly() throws Exception {
        result.expectedMessageCount(1);

        Exchange exchange = template.send("direct:start", ExchangePattern.InOnly, new Processor() {
            public void process(Exchange exchange) {
                exchange.getIn().setBody("This is my message text.");
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        Exchange resultExchange = result.getExchanges().get(0);
        assertEquals("This is my message text.", resultExchange.getIn().getBody());
        assertNotNull(resultExchange.getIn().getHeader(Sqs2Constants.MESSAGE_ID));
        assertNotNull(resultExchange.getIn().getHeader(Sqs2Constants.RECEIPT_HANDLE));
        assertEquals("6a1559560f67c5e7a7d5d838bf0272ee", resultExchange.getIn().getHeader(Sqs2Constants.MD5_OF_BODY));
        assertNotNull(resultExchange.getIn().getHeader(Sqs2Constants.ATTRIBUTES));
        assertNotNull(resultExchange.getIn().getHeader(Sqs2Constants.MESSAGE_ATTRIBUTES));

        assertNotNull(exchange.getIn().getHeader(Sqs2Constants.MESSAGE_ID));
        assertEquals("6a1559560f67c5e7a7d5d838bf0272ee", exchange.getIn().getHeader(Sqs2Constants.MD5_OF_BODY));
    }

    @Test
    public void sendInOut() throws Exception {
        result.expectedMessageCount(1);

        Exchange exchange = template.send("direct:start", ExchangePattern.InOut, new Processor() {
            public void process(Exchange exchange) {
                exchange.getIn().setBody("This is my message text.");
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        Exchange resultExchange = result.getExchanges().get(0);
        assertEquals("This is my message text.", resultExchange.getIn().getBody());
        assertNotNull(resultExchange.getIn().getHeader(Sqs2Constants.RECEIPT_HANDLE));
        assertNotNull(resultExchange.getIn().getHeader(Sqs2Constants.MESSAGE_ID));
        assertEquals("6a1559560f67c5e7a7d5d838bf0272ee", resultExchange.getIn().getHeader(Sqs2Constants.MD5_OF_BODY));
        assertNotNull(resultExchange.getIn().getHeader(Sqs2Constants.ATTRIBUTES));
        assertNotNull(resultExchange.getIn().getHeader(Sqs2Constants.MESSAGE_ATTRIBUTES));

        assertNotNull(exchange.getMessage().getHeader(Sqs2Constants.MESSAGE_ID));
        assertEquals("6a1559560f67c5e7a7d5d838bf0272ee", exchange.getMessage().getHeader(Sqs2Constants.MD5_OF_BODY));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        final String sqsEndpointUri = String
                .format("aws2-sqs://%s?messageRetentionPeriod=%s&maximumMessageSize=%s&visibilityTimeout=%s&policy=%s&autoCreateQueue=true",
                        sharedNameGenerator.getName(),
                        "1209600", "65536", "60",
                        "file:src/test/resources/org/apache/camel/component/aws2/sqs/policy.txt");

        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to(sqsEndpointUri);

                from(sqsEndpointUri).to("mock:result");
            }
        };
    }
}
