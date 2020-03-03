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

import java.util.ArrayList;
import java.util.Collection;

import com.amazonaws.services.sqs.model.DeleteMessageResult;
import com.amazonaws.services.sqs.model.SendMessageBatchResult;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class SqsComponentSpringTest extends CamelSpringTestSupport {

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
        assertEquals("6a1559560f67c5e7a7d5d838bf0272ee", resultExchange.getIn().getHeader(SqsConstants.MD5_OF_BODY));
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

    @Test
    public void sendBatchMessage() throws Exception {
        result.expectedMessageCount(1);

        template.send("direct:start-batch", new Processor() {

            @Override
            public void process(Exchange exchange) throws Exception {
                Collection c = new ArrayList<Integer>();
                c.add("team1");
                c.add("team2");
                c.add("team3");
                c.add("team4");
                exchange.getIn().setBody(c);
            }
        });
        assertMockEndpointsSatisfied();
        SendMessageBatchResult res = result.getExchanges().get(0).getIn().getBody(SendMessageBatchResult.class);
        assertEquals(2, res.getFailed().size());
        assertEquals(2, res.getSuccessful().size());
    }

    @Test
    public void deleteMessage() throws Exception {
        result.expectedMessageCount(1);

        template.send("direct:start-delete", new Processor() {

            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(SqsConstants.RECEIPT_HANDLE, "123456");
            }
        });
        assertMockEndpointsSatisfied();
        DeleteMessageResult res = result.getExchanges().get(0).getIn().getBody(DeleteMessageResult.class);
        assertNotNull(res);
    }

    @Override
    protected ClassPathXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/aws/sqs/SqsComponentSpringTest-context.xml");
    }
}
