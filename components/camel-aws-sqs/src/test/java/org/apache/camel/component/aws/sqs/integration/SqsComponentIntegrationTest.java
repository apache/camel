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
package org.apache.camel.component.aws.sqs.integration;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws.sqs.SqsConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Ignore;
import org.junit.Test;

@Ignore("Must be manually tested. Provide your own accessKey and secretKey!")
public class SqsComponentIntegrationTest extends CamelTestSupport {

    private String accessKey = "xxx";
    private String secretKey = "yyy";

    @EndpointInject("direct:start")
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;

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

        assertNotNull(exchange.getMessage().getHeader(SqsConstants.MESSAGE_ID));
        assertEquals("6a1559560f67c5e7a7d5d838bf0272ee", exchange.getMessage().getHeader(SqsConstants.MD5_OF_BODY));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        final String sqsEndpointUri = String
            .format("aws-sqs://MyNewCamelQueue?accessKey=%s&secretKey=%s&messageRetentionPeriod=%s&maximumMessageSize=%s&visibilityTimeout=%s&policy=%s", accessKey, secretKey,
                    "1209600", "65536", "60",
                    "%7B%22Version%22%3A%222008-10-17%22%2C%22Id%22%3A%22%2F195004372649%2FMyNewCamelQueue%2FSQSDefaultPolicy%22%2C%22"
                                              + "Statement%22%3A%5B%7B%22Sid%22%3A%22Queue1ReceiveMessage%22%2C%22Effect%22%3A%22Allow%22%2C%22Principal%22%3A%7B%22AWS%22%3A%22*%22%7D%2C%22"
                                              + "Action%22%3A%22SQS%3AReceiveMessage%22%2C%22Resource%22%3A%22%2F195004372649%2FMyNewCamelQueue%22%7D%5D%7D");

        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to(sqsEndpointUri);

                from(sqsEndpointUri).to("mock:result");
            }
        };
    }
}
