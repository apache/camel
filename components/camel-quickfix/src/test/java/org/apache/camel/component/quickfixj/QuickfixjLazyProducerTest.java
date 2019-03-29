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
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import quickfix.FixVersions;
import quickfix.Message;
import quickfix.MessageUtils;
import quickfix.SessionID;
import quickfix.field.BeginString;
import quickfix.field.SenderCompID;
import quickfix.field.TargetCompID;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

@Ignore("Fails on CI server sometimes")
public class QuickfixjLazyProducerTest {
    private Exchange mockExchange;
    private QuickfixjEndpoint endpoint;
    private org.apache.camel.Message mockCamelMessage;
    private QuickfixjProducer producer;
    private SessionID sessionID;
    private Message inboundFixMessage;
    private QuickfixjEngine quickfixjEngine;

    @Before
    public void setUp() throws Exception {
        mockExchange = Mockito.mock(Exchange.class);
        mockCamelMessage = Mockito.mock(org.apache.camel.Message.class);
        Mockito.when(mockExchange.getIn()).thenReturn(mockCamelMessage);
        Mockito.when(mockExchange.getPattern()).thenReturn(ExchangePattern.InOnly);
        
        quickfixjEngine = TestSupport.createEngine(true);
        endpoint = Mockito.spy(new QuickfixjEndpoint(quickfixjEngine, "", new QuickfixjComponent()));

        inboundFixMessage = new Message();
        inboundFixMessage.getHeader().setString(BeginString.FIELD, FixVersions.BEGINSTRING_FIX44);
        inboundFixMessage.getHeader().setString(SenderCompID.FIELD, "SENDER");
        inboundFixMessage.getHeader().setString(TargetCompID.FIELD, "TARGET");
        sessionID = MessageUtils.getSessionID(inboundFixMessage);
   
        Mockito.when(mockCamelMessage.getBody(Message.class)).thenReturn(inboundFixMessage);

        Mockito.when(endpoint.getSessionID()).thenReturn(sessionID);     

        producer = Mockito.spy(new QuickfixjProducer(endpoint));
    }

    @Test
    public void processWithLazyEngine() throws Exception {
        QuickfixjEngine engine = (QuickfixjEngine) ReflectionTestUtils.getField(endpoint, "engine");
        assertThat(engine.isInitialized(), is(false));
        assertThat(engine.isStarted(), is(false));
//        Session mockSession = Mockito.spy(TestSupport.createSession(sessionID));
//        Mockito.doReturn(mockSession).when(producer).getSession(MessageUtils.getSessionID(inboundFixMessage));
//        Mockito.doReturn(true).when(mockSession).send(Matchers.isA(Message.class));

        producer.process(mockExchange);

        assertThat(engine.isInitialized(), is(true));
        assertThat(engine.isStarted(), is(true));
//
//        Mockito.verify(mockExchange, Mockito.never()).setException(Matchers.isA(IllegalStateException.class));
//        Mockito.verify(mockSession).send(inboundFixMessage);
    }
}
