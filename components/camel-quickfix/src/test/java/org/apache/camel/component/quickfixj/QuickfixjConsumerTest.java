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
package org.apache.camel.component.quickfixj;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.component.quickfixj.converter.QuickfixjConverters;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import quickfix.Message;
import quickfix.Session;
import quickfix.SessionID;

import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class QuickfixjConsumerTest extends CamelTestSupport {

    private final SessionID requestSessionID = new SessionID("FIX.4.4", "MARKET", "TRADER");
    private final SessionID otherSessionID = new SessionID("FIX.4.4", "MARKET", "OTHER");

    @Test
    public void replyIsSentOnTheSessionTheRequestArrivedOn() throws Exception {
        Message reply = new Message();
        Session requestSession = Mockito.mock(Session.class);
        Mockito.when(requestSession.send(reply)).thenReturn(true);

        QuickfixjConsumer consumer = startConsumer(replyWith(reply, null), requestSession);
        Exchange exchange = receive(consumer);

        assertThat(exchange.getException(), nullValue());
        Mockito.verify(requestSession).send(reply);
    }

    @Test
    public void replyIgnoresSessionIdHeaderChangedDuringRouting() throws Exception {
        Message reply = new Message();
        Session requestSession = Mockito.mock(Session.class);
        Mockito.when(requestSession.send(reply)).thenReturn(true);

        QuickfixjConsumer consumer = startConsumer(replyWith(reply, otherSessionID), requestSession);
        Exchange exchange = receive(consumer);

        assertThat(exchange.getException(), nullValue());
        Mockito.verify(requestSession).send(reply);
        Mockito.verify(consumer, Mockito.never()).getSession(otherSessionID);
    }

    private QuickfixjConsumer startConsumer(Processor processor, Session requestSession) throws Exception {
        QuickfixjEndpoint endpoint = Mockito.mock(QuickfixjEndpoint.class);
        Mockito.when(endpoint.getCamelContext()).thenReturn(context);

        QuickfixjConsumer consumer = Mockito.spy(new QuickfixjConsumer(endpoint, processor));
        Mockito.doReturn(requestSession).when(consumer).getSession(requestSessionID);
        consumer.start();
        return consumer;
    }

    private Exchange receive(QuickfixjConsumer consumer) {
        Exchange exchange = QuickfixjConverters.toExchange(consumer, requestSessionID, new Message(),
                QuickfixjEventCategory.AppMessageReceived, ExchangePattern.InOut);
        consumer.onExchange(exchange);
        return exchange;
    }

    @SuppressWarnings("deprecation")
    private static Processor replyWith(Message reply, SessionID sessionIdHeader) {
        return exchange -> {
            if (sessionIdHeader != null) {
                exchange.getIn().setHeader(QuickfixjEndpoint.SESSION_ID_KEY, sessionIdHeader);
            }
            // an InOut reply is carried on the OUT message, as the bean component does for InOut exchanges
            exchange.getOut().setBody(reply);
        };
    }
}
