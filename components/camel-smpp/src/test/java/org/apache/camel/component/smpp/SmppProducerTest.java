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
import org.jsmpp.bean.BindType;
import org.jsmpp.bean.NumberingPlanIndicator;
import org.jsmpp.bean.TypeOfNumber;
import org.jsmpp.session.BindParameter;
import org.jsmpp.session.SMPPSession;
import org.jsmpp.session.SessionStateListener;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * JUnit test class for <code>org.apache.camel.component.smpp.SmppProducer</code>
 * 
 * @version 
 */
public class SmppProducerTest {
    
    private SmppProducer producer;
    private SmppConfiguration configuration;
    private SmppEndpoint endpoint;
    private SMPPSession session;

    @Before
    public void setUp() {
        configuration = new SmppConfiguration();
        endpoint = mock(SmppEndpoint.class);
        session = mock(SMPPSession.class);
        
        producer = new SmppProducer(endpoint, configuration) {
            SMPPSession createSMPPSession() {
                return session;
            }
        };
    }

    @Test
    public void doStartShouldStartANewSmppSession() throws Exception {
        when(endpoint.getConnectionString())
            .thenReturn("smpp://smppclient@localhost:2775");
        BindParameter expectedBindParameters = new BindParameter(
                BindType.BIND_TX,
                "smppclient",
                "password",
                "cp",
                TypeOfNumber.UNKNOWN,
                NumberingPlanIndicator.UNKNOWN,
                "");
        when(session.connectAndBind("localhost", new Integer(2775), expectedBindParameters))
            .thenReturn("1");
        when(endpoint.isSingleton()).thenReturn(true);
    
        producer.doStart();
    
        verify(session).setEnquireLinkTimer(5000);
        verify(session).setTransactionTimer(10000);
        verify(session).addSessionStateListener(isA(SessionStateListener.class));
        verify(session).connectAndBind("localhost", new Integer(2775), expectedBindParameters);
    }

    @Test
    public void doStopShouldNotCloseTheSMPPSessionIfItIsNull() throws Exception {
        when(endpoint.getConnectionString())
            .thenReturn("smpp://smppclient@localhost:2775");
        when(endpoint.isSingleton()).thenReturn(true);

        producer.doStop();
    }
    
    @Test
    public void doStopShouldCloseTheSMPPSession() throws Exception {
        when(endpoint.getConnectionString())
            .thenReturn("smpp://smppclient@localhost:2775");
        when(endpoint.isSingleton()).thenReturn(true);

        producer.doStart();
        producer.doStop();
        
        verify(session).removeSessionStateListener(isA(SessionStateListener.class));
        verify(session).unbindAndClose();
    }
    
    @Test
    public void processInOnlyShouldExecuteTheCommand() throws Exception {
        SmppBinding binding = mock(SmppBinding.class);
        Exchange exchange = mock(Exchange.class);
        SmppCommand command = mock(SmppCommand.class);
        when(endpoint.getBinding()).thenReturn(binding);
        when(binding.createSmppCommand(session, exchange)).thenReturn(command);
        
        producer.doStart();
        producer.process(exchange);
        
        verify(command).execute(exchange);
    }
    
    @Test
    public void getterShouldReturnTheSetValues() {
        assertSame(endpoint, producer.getEndpoint());
        assertSame(configuration, producer.getConfiguration());
    }
}
