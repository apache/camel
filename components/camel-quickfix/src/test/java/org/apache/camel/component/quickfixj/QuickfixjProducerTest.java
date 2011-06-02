/**
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

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

import javax.management.JMException;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import quickfix.ConfigError;
import quickfix.FieldConvertError;
import quickfix.FixVersions;
import quickfix.Message;
import quickfix.MessageUtils;
import quickfix.Session;
import quickfix.SessionID;
import quickfix.field.BeginString;
import quickfix.field.MsgType;
import quickfix.field.SenderCompID;
import quickfix.field.TargetCompID;
import quickfix.fix42.Email;

public class QuickfixjProducerTest {
	private Exchange mockExchange;
	private QuickfixjEndpoint mockEndpoint;
	private org.apache.camel.Message mockCamelMessage;
	private QuickfixjProducer producer;
	private SessionID sessionID;
	private Message inboundFixMessage;
	private QuickfixjEngine quickfixjEngine;

	@Before
	public void setUp() throws ConfigError, FieldConvertError, IOException, JMException {
        mockExchange = Mockito.mock(Exchange.class);
        mockEndpoint = Mockito.mock(QuickfixjEndpoint.class);
        mockCamelMessage = Mockito.mock(org.apache.camel.Message.class);
        Mockito.when(mockExchange.getIn()).thenReturn(mockCamelMessage);
        Mockito.when(mockExchange.getPattern()).thenReturn(ExchangePattern.InOnly);
        
        quickfixjEngine = TestSupport.createEngine();
        Mockito.when(mockEndpoint.getEngine()).thenReturn(quickfixjEngine);
        
        inboundFixMessage = new Message();
        inboundFixMessage.getHeader().setString(BeginString.FIELD, FixVersions.BEGINSTRING_FIX44);
        inboundFixMessage.getHeader().setString(SenderCompID.FIELD, "SENDER");
        inboundFixMessage.getHeader().setString(TargetCompID.FIELD, "TARGET");
        sessionID = MessageUtils.getSessionID(inboundFixMessage);
   
        Mockito.when(mockCamelMessage.getBody(Message.class)).thenReturn(inboundFixMessage);

        Mockito.when(mockEndpoint.getSessionID()).thenReturn(sessionID);     

        producer = Mockito.spy(new QuickfixjProducer(mockEndpoint));
	}
	
	@SuppressWarnings("serial")
	public class TestException extends RuntimeException {
		
	}
	
    @Test
    public void setExceptionOnExchange() throws Exception {
        Session mockSession = Mockito.spy(TestSupport.createSession(sessionID));
		Mockito.doReturn(mockSession).when(producer).getSession(MessageUtils.getSessionID(inboundFixMessage));
		Mockito.doThrow(new TestException()).when(mockSession).send(Mockito.isA(Message.class));

		producer.process(mockExchange);    
        Mockito.verify(mockExchange).setException(Matchers.isA(TestException.class));
    }
    
    @Test
    public void processInOnlyExchange() throws Exception {        
        Session mockSession = Mockito.spy(TestSupport.createSession(sessionID));
		Mockito.doReturn(mockSession).when(producer).getSession(MessageUtils.getSessionID(inboundFixMessage));
		Mockito.doReturn(true).when(mockSession).send(Mockito.isA(Message.class));
        
        producer.process(mockExchange);
        
        Mockito.verify(mockExchange, Mockito.never()).setException(Matchers.isA(IllegalStateException.class));
        Mockito.verify(mockSession).send(inboundFixMessage);
    }

    @Test
    public void processInOutExchange() throws Exception {
        Mockito.when(mockExchange.getPattern()).thenReturn(ExchangePattern.InOut);
        Mockito.when(mockExchange.getProperty(QuickfixjProducer.CORRELATION_CRITERIA_KEY)).
        	thenReturn(new MessagePredicate(sessionID, MsgType.EMAIL));
        Mockito.when(mockExchange.getProperty(
				QuickfixjProducer.CORRELATION_TIMEOUT_KEY,
				1000L, Long.class)).thenReturn(5000L);
				
        org.apache.camel.Message mockOutboundCamelMessage = Mockito.mock(org.apache.camel.Message.class);
		Mockito.when(mockExchange.getOut()).thenReturn(mockOutboundCamelMessage);
        
        final Message outboundFixMessage = new Email();
        outboundFixMessage.getHeader().setString(SenderCompID.FIELD, "TARGET");
        outboundFixMessage.getHeader().setString(TargetCompID.FIELD, "SENDER");
        
        Session mockSession = Mockito.spy(TestSupport.createSession(sessionID));
		Mockito.doReturn(mockSession).when(producer).getSession(MessageUtils.getSessionID(inboundFixMessage));
		Mockito.doAnswer(new Answer<Boolean>() {
			@Override
			public Boolean answer(InvocationOnMock invocation) throws Throwable {
				new Timer().schedule(new TimerTask() {				
					@Override
					public void run() {
						try {
							quickfixjEngine.getMessageCorrelator().onEvent(QuickfixjEventCategory.AppMessageReceived, sessionID, outboundFixMessage);
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				}, 10);
				return true;
			}			
		}).when(mockSession).send(Mockito.isA(Message.class));

        producer.process(mockExchange);
        
        Mockito.verify(mockExchange, Mockito.never()).setException(Matchers.isA(IllegalStateException.class));
        Mockito.verify(mockSession).send(inboundFixMessage);
        Mockito.verify(mockOutboundCamelMessage).setBody(outboundFixMessage);
    }
}
