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
package org.apache.camel.component.aws.sqs;

import org.apache.camel.BindToRegistry;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class SqsComponentTest extends CamelTestSupport {

    @EndpointInject("direct:start")
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @BindToRegistry("amazonSQSClient")
    private AmazonSQSClientMock client = new AmazonSQSClientMock();

    @Test
    public void sendInOnly() throws Exception {
        result.expectedMessageCount(1);

        Exchange exchange = template.send("direct:start", ExchangePattern.InOnly, new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("This is my message text.");
            }
        });

        assertMockEndpointsSatisfied();

        Exchange resultExchange = result.getExchanges().get(0);
        assertEquals("This is my message text.", resultExchange.getIn().getBody());
        assertNotNull(resultExchange.getIn().getHeader(SqsConstants.MESSAGE_ID));
        assertNotNull(resultExchange.getIn().getHeader(SqsConstants.RECEIPT_HANDLE));
        assertEquals("6a1559560f67c5e7a7d5d838bf0272ee", resultExchange.getIn().getHeader(SqsConstants.MD5_OF_BODY));
        assertNotNull(resultExchange.getIn().getHeader(SqsConstants.ATTRIBUTES));
        assertNotNull(resultExchange.getIn().getHeader(SqsConstants.MESSAGE_ATTRIBUTES));

        assertEquals("This is my message text.", exchange.getIn().getBody());
        assertNotNull(exchange.getIn().getHeader(SqsConstants.MESSAGE_ID));
        assertEquals("6a1559560f67c5e7a7d5d838bf0272ee", exchange.getIn().getHeader(SqsConstants.MD5_OF_BODY));
    }

    @Test
    public void sendInOut() throws Exception {
        result.expectedMessageCount(1);

        Exchange exchange = template.send("direct:start", ExchangePattern.InOut, new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("This is my message text.");
            }
        });

        assertMockEndpointsSatisfied();

        Exchange resultExchange = result.getExchanges().get(0);
        assertEquals("This is my message text.", resultExchange.getIn().getBody());
        assertNotNull(resultExchange.getIn().getHeader(SqsConstants.RECEIPT_HANDLE));
        assertNotNull(resultExchange.getIn().getHeader(SqsConstants.MESSAGE_ID));
        assertEquals("6a1559560f67c5e7a7d5d838bf0272ee", resultExchange.getIn().getHeader(SqsConstants.MD5_OF_BODY));
        assertNotNull(resultExchange.getIn().getHeader(SqsConstants.ATTRIBUTES));
        assertNotNull(resultExchange.getIn().getHeader(SqsConstants.MESSAGE_ATTRIBUTES));

        assertEquals("This is my message text.", exchange.getMessage().getBody());
        assertNotNull(exchange.getMessage().getHeader(SqsConstants.MESSAGE_ID));
        assertEquals("6a1559560f67c5e7a7d5d838bf0272ee", exchange.getMessage().getHeader(SqsConstants.MD5_OF_BODY));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            final String sqsURI = String.format("aws-sqs://MyQueue?amazonSQSClient=#amazonSQSClient&messageRetentionPeriod=%s&maximumMessageSize=%s&policy=%s", "1209600", "65536",
                                                "");

            @Override
            public void configure() throws Exception {
                from("direct:start").to(sqsURI);

                from(sqsURI).to("mock:result");
            }
        };
    }
}
