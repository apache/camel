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

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ExchangeTimedOutException;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import quickfix.FixVersions;
import quickfix.Message;
import quickfix.MessageUtils;
import quickfix.Session;
import quickfix.SessionID;
import quickfix.field.BeginString;
import quickfix.field.MsgType;
import quickfix.field.SenderCompID;
import quickfix.field.TargetCompID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.isA;

/**
 * An InOut exchange that gets no reply (timeout) or whose message cannot be sent must not leave its correlation rule
 * registered in the engine's {@link MessageCorrelator}.
 */
class QuickfixjProducerCorrelationCleanupTest {

    private CamelContext context;
    private QuickfixjEngine engine;
    private QuickfixjProducer producer;
    private Session session;
    private Message fixMessage;
    private SessionID responseSessionID;

    @BeforeEach
    void setUp() throws Exception {
        context = new DefaultCamelContext();
        context.start();
        engine = TestSupport.createEngine();

        fixMessage = new Message();
        fixMessage.getHeader().setString(BeginString.FIELD, FixVersions.BEGINSTRING_FIX44);
        fixMessage.getHeader().setString(SenderCompID.FIELD, "SENDER");
        fixMessage.getHeader().setString(TargetCompID.FIELD, "TARGET");
        SessionID sessionID = MessageUtils.getSessionID(fixMessage);
        responseSessionID = new SessionID(sessionID.getBeginString(), sessionID.getTargetCompID(), sessionID.getSenderCompID());

        QuickfixjEndpoint endpoint = Mockito.mock(QuickfixjEndpoint.class);
        Mockito.when(endpoint.getEngine()).thenReturn(engine);
        Mockito.when(endpoint.getSID()).thenReturn(sessionID);

        session = Mockito.spy(TestSupport.createSession(sessionID));
        producer = Mockito.spy(new QuickfixjProducer(endpoint));
        Mockito.doReturn(session).when(producer).getSession(sessionID);
    }

    @AfterEach
    void tearDown() {
        context.stop();
    }

    @Test
    void timedOutRequestsDoNotStayRegistered() throws Exception {
        Mockito.doReturn(true).when(session).send(isA(Message.class));

        for (int i = 0; i < 50; i++) {
            Exchange exchange = createInOutExchange(10L);
            producer.process(exchange);
            assertInstanceOf(ExchangeTimedOutException.class, exchange.getException());
        }

        assertEquals(0, pendingRules(), "correlation rules left after 50 timed out requests");
    }

    @Test
    void requestThatCannotBeSentDoesNotStayRegistered() throws Exception {
        Mockito.doReturn(false).when(session).send(isA(Message.class));

        Exchange exchange = createInOutExchange(5000L);
        producer.process(exchange);
        assertInstanceOf(CannotSendException.class, exchange.getException());

        assertEquals(0, pendingRules(), "correlation rules left after a failed send");
    }

    @Test
    void requestWhoseSendThrowsDoesNotStayRegistered() throws Exception {
        Mockito.doThrow(new IllegalStateException("not logged on")).when(session).send(isA(Message.class));

        Exchange exchange = createInOutExchange(5000L);
        producer.process(exchange);
        assertInstanceOf(IllegalStateException.class, exchange.getException());

        assertEquals(0, pendingRules(), "correlation rules left after a send that threw");
    }

    private Exchange createInOutExchange(long timeout) {
        Exchange exchange = new DefaultExchange(context, ExchangePattern.InOut);
        exchange.getIn().setBody(fixMessage);
        exchange.setProperty(QuickfixjProducer.CORRELATION_CRITERIA_KEY,
                new MessagePredicate(responseSessionID, MsgType.EXECUTION_REPORT));
        exchange.setProperty(QuickfixjProducer.CORRELATION_TIMEOUT_KEY, timeout);
        return exchange;
    }

    private int pendingRules() {
        return engine.getMessageCorrelator().pendingReplies();
    }
}
