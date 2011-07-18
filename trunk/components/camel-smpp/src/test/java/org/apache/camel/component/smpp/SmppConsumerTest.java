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

import org.apache.camel.Processor;
import org.jsmpp.bean.BindType;
import org.jsmpp.bean.NumberingPlanIndicator;
import org.jsmpp.bean.TypeOfNumber;
import org.jsmpp.session.BindParameter;
import org.jsmpp.session.MessageReceiverListener;
import org.jsmpp.session.SMPPSession;
import org.jsmpp.session.SessionStateListener;
import org.junit.Before;
import org.junit.Test;

import static org.easymock.classextension.EasyMock.*;
import static org.junit.Assert.assertSame;

/**
 * JUnit test class for <code>org.apache.camel.component.smpp.SmppConsumer</code>
 * 
 * @version 
 */
public class SmppConsumerTest {
    
    private SmppConsumer consumer;
    private SmppEndpoint endpoint;
    private SmppConfiguration configuration;
    private Processor processor;
    private SMPPSession session;

    @Before
    public void setUp() {
        configuration = new SmppConfiguration();
        endpoint = createMock(SmppEndpoint.class);
        processor = createMock(Processor.class);
        session = createMock(SMPPSession.class);
        
        consumer = new SmppConsumer(
                endpoint, 
                configuration,
                processor) {
            
            SMPPSession createSMPPSession() {
                return session;
            }
        };
    }

    @Test
    public void doStartShouldStartANewSmppSession() throws Exception {
        expect(endpoint.getConnectionString())
            .andReturn("smpp://smppclient@localhost:2775")
            .times(2);
        session.setEnquireLinkTimer(5000); //expectation
        session.setTransactionTimer(10000); //expectation
        session.addSessionStateListener(isA(SessionStateListener.class));
        session.setMessageReceiverListener(isA(MessageReceiverListener.class)); //expectation
        expect(session.connectAndBind(
                "localhost",
                new Integer(2775),
                new BindParameter(
                        BindType.BIND_RX,
                        "smppclient",
                        "password",
                        "cp",
                        TypeOfNumber.UNKNOWN,
                        NumberingPlanIndicator.UNKNOWN,
                        ""))).andReturn("1");
        expect(endpoint.getConnectionString()).andReturn("smpp://smppclient@localhost:2775");
        
        replay(endpoint, processor, session);
        
        consumer.doStart();
        
        verify(endpoint, processor, session);
    }

    @Test
    public void doStopShouldNotCloseTheSMPPSessionIfItIsNull() throws Exception {
        expect(endpoint.getConnectionString())
            .andReturn("smpp://smppclient@localhost:2775")
            .times(3);
        
        replay(session, endpoint);
        
        consumer.doStop();
        
        verify(session, endpoint);
    }
    
    @Test
    public void doStopShouldCloseTheSMPPSession() throws Exception {
        doStartShouldStartANewSmppSession();
        reset(endpoint, processor, session);
        
        expect(endpoint.getConnectionString())
            .andReturn("smpp://smppclient@localhost:2775")
            .times(3);
        session.removeSessionStateListener(isA(SessionStateListener.class));
        session.unbindAndClose();
        
        replay(session, endpoint);
        
        consumer.doStop();
        
        verify(session, endpoint);
    }

    @Test
    public void getterShouldReturnTheSetValues() {
        assertSame(endpoint, consumer.getEndpoint());
        assertSame(configuration, consumer.getConfiguration());
    }
}