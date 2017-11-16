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
package org.apache.camel.component.smpp;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.jsmpp.bean.BindType;
import org.jsmpp.bean.NumberingPlanIndicator;
import org.jsmpp.bean.TypeOfNumber;
import org.jsmpp.session.BindParameter;
import org.jsmpp.session.SMPPSession;
import org.jsmpp.session.SessionStateListener;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * JUnit test class for <code>org.apache.camel.component.smpp.SmppProducer</code>
 * 
 * @version 
 */
public class SmppProducerLazySessionCreationTest {
    
    private SmppProducer producer;
    private SmppConfiguration configuration;
    private SmppEndpoint endpoint;
    private SMPPSession session;

    @Before
    public void setUp() {
        configuration = new SmppConfiguration();
        configuration.setLazySessionCreation(true);
        endpoint = mock(SmppEndpoint.class);
        session = mock(SMPPSession.class);
        
        producer = new SmppProducer(endpoint, configuration) {
            SMPPSession createSMPPSession() {
                return session;
            }
        };
    }

    @Test
    public void doStartShouldNotCreateTheSmppSession() throws Exception {
        when(endpoint.getConnectionString()).thenReturn("smpp://smppclient@localhost:2775");
        when(endpoint.isSingleton()).thenReturn(true);

        producer.doStart();

        verify(endpoint).getConnectionString();
        verify(endpoint).isSingleton();
        verifyNoMoreInteractions(endpoint, session);
    }

    @Test
    public void processShouldCreateTheSmppSession() throws Exception {
        when(endpoint.getConnectionString())
            .thenReturn("smpp://smppclient@localhost:2775");
        BindParameter expectedBindParameter = new BindParameter(
                BindType.BIND_TX,
                "smppclient",
                "password",
                "cp",
                TypeOfNumber.UNKNOWN,
                NumberingPlanIndicator.UNKNOWN,
                "");
        when(session.connectAndBind("localhost", new Integer(2775), expectedBindParameter))
            .thenReturn("1");
        when(endpoint.isSingleton()).thenReturn(true);
        SmppBinding binding = mock(SmppBinding.class);
        Exchange exchange = mock(Exchange.class);
        Message in = mock(Message.class);
        SmppCommand command = mock(SmppCommand.class);
        when(endpoint.getBinding()).thenReturn(binding);
        when(binding.createSmppCommand(session, exchange)).thenReturn(command);
        when(exchange.getIn()).thenReturn(in);
        when(in.getHeader("CamelSmppSystemId", String.class)).thenReturn(null);
        when(in.getHeader("CamelSmppPassword", String.class)).thenReturn(null);
        command.execute(exchange);
        
        producer.doStart();
        producer.process(exchange);
        
        verify(session).setEnquireLinkTimer(5000);
        verify(session).setTransactionTimer(10000);
        verify(session).addSessionStateListener(isA(SessionStateListener.class));
        verify(session).connectAndBind("localhost", new Integer(2775), expectedBindParameter);
    }

    @Test
    public void processShouldCreateTheSmppSessionWithTheSystemIdAndPasswordFromTheExchange() throws Exception {
        when(endpoint.getConnectionString())
            .thenReturn("smpp://localhost:2775");
        BindParameter expectedBindParameter = new BindParameter(
                BindType.BIND_TX,
                "smppclient2",
                "password2",
                "cp",
                TypeOfNumber.UNKNOWN,
                NumberingPlanIndicator.UNKNOWN,
                "");
        when(session.connectAndBind("localhost", new Integer(2775), expectedBindParameter))
            .thenReturn("1");
        SmppBinding binding = mock(SmppBinding.class);
        Exchange exchange = mock(Exchange.class);
        Message in = mock(Message.class);
        SmppCommand command = mock(SmppCommand.class);
        when(endpoint.getBinding()).thenReturn(binding);
        when(endpoint.isSingleton()).thenReturn(true);
        when(binding.createSmppCommand(session, exchange)).thenReturn(command);
        when(exchange.getIn()).thenReturn(in);
        when(in.getHeader("CamelSmppSystemId", String.class)).thenReturn("smppclient2");
        when(in.getHeader("CamelSmppPassword", String.class)).thenReturn("password2");
        command.execute(exchange);
        
        producer.doStart();
        producer.process(exchange);
        
        verify(session).connectAndBind("localhost", new Integer(2775), expectedBindParameter);
    }
}
