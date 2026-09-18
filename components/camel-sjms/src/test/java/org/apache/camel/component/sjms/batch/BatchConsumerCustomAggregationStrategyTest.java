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
package org.apache.camel.component.sjms.batch;

import java.util.ArrayList;
import java.util.List;

import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.TextMessage;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.sjms.SjmsMessage;
import org.apache.camel.component.sjms.support.JmsTestSupport;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class BatchConsumerCustomAggregationStrategyTest extends JmsTestSupport {

    private static final String SJMS_FROMF_URI
            = "%s?batching=true&batchingSize=5&batchingAggregationStrategy=#jmsMessageListAggregationStrategy";
    private static final String SJMS_QUEUE_NAME
            = "sjms:queue:batch.consumer.queue.BatchConsumerCustomAggregationStrategyTest";
    private static final String MOCK_RESULT = "mock:result";

    @BindToRegistry("jmsMessageListAggregationStrategy")
    public JmsMessageListAggregationStrategy jmsMessageListAggregationStrategy() {
        return new JmsMessageListAggregationStrategy();
    }

    @Test
    public void testCustomAggregationStrategyProducesJmsMessageList() throws Exception {
        MockEndpoint mock = getMockEndpoint(MOCK_RESULT);
        mock.expectedMessageCount(1); // one batch of 3

        template.sendBody(SJMS_QUEUE_NAME, "Hello");
        template.sendBody(SJMS_QUEUE_NAME, "World");
        template.sendBody(SJMS_QUEUE_NAME, "!");

        mock.assertIsSatisfied();

        List<?> body = mock.getExchanges().get(0).getIn().getBody(List.class);
        assertEquals(3, body.size());

        for (Object item : body) {
            // proves the custom strategy ran instead of the default List<Exchange> one
            assertInstanceOf(Message.class, item, "batch element was not a jakarta.jms.Message: " + item);
            assertInstanceOf(TextMessage.class, item, "expected a TextMessage");
        }

        assertEquals("Hello", textOf(body.get(0)));
        assertEquals("World", textOf(body.get(1)));
        assertEquals("!", textOf(body.get(2)));
    }

    private static String textOf(Object message) throws JMSException {
        return ((TextMessage) message).getText();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                fromF(SJMS_FROMF_URI, SJMS_QUEUE_NAME).to(MOCK_RESULT);
            }
        };
    }

    public static class JmsMessageListAggregationStrategy implements AggregationStrategy {

        @Override
        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            if (oldExchange == null) {
                oldExchange = new DefaultExchange(newExchange); // holder, not one of the grouped messages
                oldExchange.setProperty(ExchangePropertyKey.GROUPED_EXCHANGE, new ArrayList<Message>());
            }
            List<Message> list = oldExchange.getProperty(ExchangePropertyKey.GROUPED_EXCHANGE, List.class);
            list.add(newExchange.getIn(SjmsMessage.class).getJmsMessage());
            return oldExchange;
        }

        @Override
        public void onCompletion(Exchange exchange) {
            Object list = exchange.removeProperty(ExchangePropertyKey.GROUPED_EXCHANGE);
            if (list != null) {
                exchange.getIn().setBody(list);
            }
        }
    }
}
