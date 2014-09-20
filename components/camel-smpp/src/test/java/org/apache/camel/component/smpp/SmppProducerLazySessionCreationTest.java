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

import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.isA;
import static org.easymock.classextension.EasyMock.createMock;
import static org.easymock.classextension.EasyMock.replay;
import static org.easymock.classextension.EasyMock.verify;

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
        endpoint = createMock(SmppEndpoint.class);
        session = createMock(SMPPSession.class);
        
        producer = new SmppProducer(endpoint, configuration) {
            SMPPSession createSMPPSession() {
                return session;
            }
        };
    }

    @Test
    public void doStartShouldNotCreateTheSmppSession() throws Exception {
        expect(endpoint.getConnectionString()).andReturn("smpp://smppclient@localhost:2775");
        expect(endpoint.isSingleton()).andReturn(true);

        replay(endpoint, session);

        producer.doStart();

        verify(endpoint, session);
    }

    @Test
    public void processShouldCreateTheSmppSession() throws Exception {
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
        expect(endpoint.getConnectionString()).andReturn("smpp://smppclient@localhost:2775");
        expect(endpoint.isSingleton()).andReturn(true);
        SmppBinding binding = createMock(SmppBinding.class);
        Exchange exchange = createMock(Exchange.class);
        Message in = createMock(Message.class);
        SmppCommand command = createMock(SmppCommand.class);
        expect(endpoint.getBinding()).andReturn(binding);
        expect(binding.createSmppCommand(session, exchange)).andReturn(command);
        expect(exchange.getIn()).andReturn(in);
        expect(in.getHeader("CamelSmppSystemId", String.class)).andReturn(null);
        expect(in.getHeader("CamelSmppPassword", String.class)).andReturn(null);
        command.execute(exchange);
        
        replay(session, endpoint, binding, exchange, in, command);
        
        producer.doStart();
        producer.process(exchange);
        
        verify(session, endpoint, binding, exchange, in, command);
    }

    @Test
    public void processShouldCreateTheSmppSessionWithTheSystemIdAndPasswordFromTheExchange() throws Exception {
        expect(endpoint.getConnectionString())
            .andReturn("smpp://localhost:2775")
            .times(2);
        session.setEnquireLinkTimer(5000); //expectation
        session.setTransactionTimer(10000); //expectation
        session.addSessionStateListener(isA(SessionStateListener.class));
        expect(session.connectAndBind(
            "localhost",
            new Integer(2775),
            new BindParameter(
                    BindType.BIND_TX,
                    "smppclient2",
                    "password2",
                    "cp",
                    TypeOfNumber.UNKNOWN,
                    NumberingPlanIndicator.UNKNOWN,
                    ""))).andReturn("1");
        expect(endpoint.getConnectionString()).andReturn("smpp://localhost:2775");
        SmppBinding binding = createMock(SmppBinding.class);
        Exchange exchange = createMock(Exchange.class);
        Message in = createMock(Message.class);
        SmppCommand command = createMock(SmppCommand.class);
        expect(endpoint.getBinding()).andReturn(binding);
        expect(endpoint.isSingleton()).andReturn(true);
        expect(binding.createSmppCommand(session, exchange)).andReturn(command);
        expect(exchange.getIn()).andReturn(in);
        expect(in.getHeader("CamelSmppSystemId", String.class)).andReturn("smppclient2");
        expect(in.getHeader("CamelSmppPassword", String.class)).andReturn("password2");
        command.execute(exchange);
        
        replay(session, endpoint, binding, exchange, in, command);
        
        producer.doStart();
        producer.process(exchange);
        
        verify(session, endpoint, binding, exchange, in, command);
    }
}