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

import java.io.IOException;

import org.apache.camel.Exchange;
import org.jsmpp.bean.BindType;
import org.jsmpp.bean.NumberingPlanIndicator;
import org.jsmpp.bean.TypeOfNumber;
import org.jsmpp.session.BindParameter;
import org.jsmpp.session.SMPPSession;
import org.jsmpp.session.SessionStateListener;
import org.junit.Before;
import org.junit.Test;

import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.isA;
import static org.easymock.classextension.EasyMock.createMock;
import static org.easymock.classextension.EasyMock.replay;
import static org.easymock.classextension.EasyMock.verify;
import static org.junit.Assert.assertSame;

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
        endpoint = createMock(SmppEndpoint.class);
        session = createMock(SMPPSession.class);
        
        producer = new SmppProducer(endpoint, configuration) {
            SMPPSession createSMPPSession() {
                return session;
            }
        };
    }

    private void doStartExpectations() throws IOException {
        expect(endpoint.getConnectionString())
            .andReturn("smpp://smppclient@localhost:2775")
            .times(2);
        session.setEnquireLinkTimer(5000); //expectation
        session.setTransactionTimer(10000); //expectation
        session.addSessionStateListener(isA(SessionStateListener.class));
        expect(session.connectAndBind(
            "localhost",
            new Integer(2775),
            new BindParameter(
                    BindType.BIND_TX,
                    "smppclient",
                    "password",
                    "cp",
                    TypeOfNumber.UNKNOWN,
                    NumberingPlanIndicator.UNKNOWN,
                    ""))).andReturn("1");
        expect(endpoint.getConnectionString())
            .andReturn("smpp://smppclient@localhost:2775");
        expect(endpoint.isSingleton()).andReturn(true);
    }
    
    @Test
    public void doStartShouldStartANewSmppSession() throws Exception {
        doStartExpectations();
    
        replay(endpoint, session);
    
        producer.doStart();
    
        verify(endpoint, session);
    }

    @Test
    public void doStopShouldNotCloseTheSMPPSessionIfItIsNull() throws Exception {
        expect(endpoint.getConnectionString())
            .andReturn("smpp://smppclient@localhost:2775")
            .times(3);
        expect(endpoint.isSingleton()).andReturn(true);

        replay(session, endpoint);
        
        producer.doStop();
        
        verify(session, endpoint);
    }
    
    @Test
    public void doStopShouldCloseTheSMPPSession() throws Exception {
        doStartExpectations();
        expect(endpoint.getConnectionString())
            .andReturn("smpp://smppclient@localhost:2775")
            .times(2);
        session.removeSessionStateListener(isA(SessionStateListener.class));
        session.unbindAndClose();
        expect(endpoint.getConnectionString())
            .andReturn("smpp://smppclient@localhost:2775");
        expect(endpoint.isSingleton()).andReturn(true);

        replay(session, endpoint);
        
        producer.doStart();
        producer.doStop();
        
        verify(session, endpoint);
    }
    
    @Test
    public void processInOnlyShouldExecuteTheCommand() throws Exception {
        doStartExpectations();
        SmppBinding binding = createMock(SmppBinding.class);
        Exchange exchange = createMock(Exchange.class);
        SmppCommand command = createMock(SmppCommand.class);
        expect(endpoint.getBinding()).andReturn(binding);
        expect(binding.createSmppCommand(session, exchange)).andReturn(command);
        command.execute(exchange);
        
        replay(session, endpoint, binding, exchange, command);
        
        producer.doStart();
        producer.process(exchange);
        
        verify(session, endpoint, binding, exchange, command);
    }
    
    @Test
    public void getterShouldReturnTheSetValues() {
        assertSame(endpoint, producer.getEndpoint());
        assertSame(configuration, producer.getConfiguration());
    }
}