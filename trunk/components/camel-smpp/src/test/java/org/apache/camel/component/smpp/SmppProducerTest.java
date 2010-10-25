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
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.jsmpp.bean.Alphabet;
import org.jsmpp.bean.BindType;
import org.jsmpp.bean.ESMClass;
import org.jsmpp.bean.GeneralDataCoding;
import org.jsmpp.bean.MessageClass;
import org.jsmpp.bean.NumberingPlanIndicator;
import org.jsmpp.bean.RegisteredDelivery;
import org.jsmpp.bean.SMSCDeliveryReceipt;
import org.jsmpp.bean.SubmitSm;
import org.jsmpp.bean.TypeOfNumber;
import org.jsmpp.session.BindParameter;
import org.jsmpp.session.SMPPSession;
import org.jsmpp.session.SessionStateListener;
import org.junit.Before;
import org.junit.Test;

import static org.easymock.EasyMock.aryEq;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.isA;
import static org.easymock.classextension.EasyMock.createMock;
import static org.easymock.classextension.EasyMock.replay;
import static org.easymock.classextension.EasyMock.verify;
import static org.junit.Assert.assertSame;

/**
 * JUnit test class for <code>org.apache.camel.component.smpp.SmppProducer</code>
 * 
 * @version $Revision$
 * @author muellerc
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
    }
    
    private void submitSmExpectations(Exchange exchange, SmppBinding binding, SubmitSm submitSm) throws Exception {
        expect(submitSm.getServiceType()).andReturn("CMT");
        expect(submitSm.getSourceAddrTon()).andReturn(TypeOfNumber.UNKNOWN.value());
        expect(submitSm.getSourceAddrNpi()).andReturn(NumberingPlanIndicator.UNKNOWN.value());
        expect(submitSm.getSourceAddr()).andReturn("1616");
        expect(submitSm.getDestAddrTon()).andReturn(TypeOfNumber.UNKNOWN.value());
        expect(submitSm.getDestAddrNpi()).andReturn(NumberingPlanIndicator.UNKNOWN.value());
        expect(submitSm.getDestAddress()).andReturn("1717");
        expect(submitSm.getProtocolId()).andReturn((byte) 0);
        expect(submitSm.getPriorityFlag()).andReturn((byte) 1);
        expect(submitSm.getScheduleDeliveryTime()).andReturn("090830230627004+");
        expect(submitSm.getValidityPeriod()).andReturn("090831232000004+");
        expect(submitSm.getRegisteredDelivery())
            .andReturn(SMSCDeliveryReceipt.SUCCESS_FAILURE.value());
        expect(submitSm.getReplaceIfPresent()).andReturn((byte) 0);
        expect(submitSm.getDataCoding()).andReturn((byte) 0);
        expect(submitSm.getShortMessage()).andReturn("Hello SMPP world!".getBytes("ISO-8859-1"));
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
        session.close();
        expect(endpoint.getConnectionString())
            .andReturn("smpp://smppclient@localhost:2775");
        
        replay(session, endpoint);
        
        producer.doStart();
        producer.doStop();
        
        verify(session, endpoint);
    }
    
    @Test
    public void processInOnlyShouldSendASubmitSmAndUpdateTheExchangeInMessage() throws Exception {
        doStartExpectations();
        SmppBinding binding = createMock(SmppBinding.class);
        Exchange exchange = createMock(Exchange.class);
        Message message = createMock(Message.class);
        SubmitSm submitSm = createMock(SubmitSm.class);
        expect(exchange.getExchangeId()).andReturn("ID-muellerc-macbookpro/3690-1214458315718/2-0");
        expect(endpoint.getBinding()).andReturn(binding);
        expect(binding.createSubmitSm(exchange)).andReturn(submitSm);
        submitSmExpectations(exchange, binding, submitSm);
        expect(session.submitShortMessage(
                eq("CMT"),
                eq(TypeOfNumber.UNKNOWN),
                eq(NumberingPlanIndicator.UNKNOWN),
                eq("1616"),
                eq(TypeOfNumber.UNKNOWN),
                eq(NumberingPlanIndicator.UNKNOWN),
                eq("1717"),
                isA(ESMClass.class),
                eq((byte) 0),
                eq((byte) 1),
                eq("090830230627004+"),
                eq("090831232000004+"),
                eq(new RegisteredDelivery(SMSCDeliveryReceipt.SUCCESS_FAILURE)),
                eq((byte) 0),
                eq(new GeneralDataCoding(
                        false,
                        false,
                        MessageClass.CLASS1,
                        Alphabet.ALPHA_DEFAULT)),
                eq((byte) 0),
                aryEq("Hello SMPP world!".getBytes("ISO-8859-1"))))
            .andReturn("1");
        expect(exchange.getPattern()).andReturn(ExchangePattern.InOnly);
        expect(exchange.getIn()).andReturn(message);
        message.setHeader(SmppBinding.ID, "1"); // expectation without return value
        expect(exchange.getExchangeId()).andReturn("ID-muellerc-macbookpro/3690-1214458315718/2-0");
        
        replay(session, endpoint, binding, exchange, message, submitSm);
        
        producer.doStart();
        producer.process(exchange);
        
        verify(session, endpoint, binding, exchange, message, submitSm);
    }
    
    @Test
    public void processInOutShouldSendASubmitSmAndUpdateTheExchangeOutMessage() throws Exception {
        doStartExpectations();
        SmppBinding binding = createMock(SmppBinding.class);
        Exchange exchange = createMock(Exchange.class);
        Message message = createMock(Message.class);
        SubmitSm submitSm = createMock(SubmitSm.class);
        expect(exchange.getExchangeId()).andReturn("ID-muellerc-macbookpro/3690-1214458315718/2-0");
        expect(endpoint.getBinding()).andReturn(binding);
        expect(binding.createSubmitSm(exchange)).andReturn(submitSm);
        submitSmExpectations(exchange, binding, submitSm);
        expect(session.submitShortMessage(
                eq("CMT"),
                eq(TypeOfNumber.UNKNOWN),
                eq(NumberingPlanIndicator.UNKNOWN),
                eq("1616"),
                eq(TypeOfNumber.UNKNOWN),
                eq(NumberingPlanIndicator.UNKNOWN),
                eq("1717"),
                isA(ESMClass.class),
                eq((byte) 0),
                eq((byte) 1),
                eq("090830230627004+"),
                eq("090831232000004+"),
                eq(new RegisteredDelivery(SMSCDeliveryReceipt.SUCCESS_FAILURE)),
                eq((byte) 0),
                eq(new GeneralDataCoding(
                        false,
                        false,
                        MessageClass.CLASS1,
                        Alphabet.ALPHA_DEFAULT)),
                eq((byte) 0),
                aryEq("Hello SMPP world!".getBytes("ISO-8859-1"))))
            .andReturn("1");
        expect(exchange.getPattern()).andReturn(ExchangePattern.InOut);
        expect(exchange.getOut()).andReturn(message);
        message.setHeader(SmppBinding.ID, "1"); // expectation without return value
        expect(exchange.getExchangeId()).andReturn("ID-muellerc-macbookpro/3690-1214458315718/2-0");
        
        replay(session, endpoint, binding, exchange, message, submitSm);
        
        producer.doStart();
        producer.process(exchange);
        
        verify(session, endpoint, binding, exchange, message, submitSm);
    }
    
    @Test
    public void processInOutShouldHonorTheDataCoding() throws Exception {
        configuration.setDataCoding((byte) 4);
        
        doStartExpectations();
        SmppBinding binding = createMock(SmppBinding.class);
        Exchange exchange = createMock(Exchange.class);
        Message message = createMock(Message.class);
        SubmitSm submitSm = createMock(SubmitSm.class);
        expect(exchange.getExchangeId()).andReturn("ID-muellerc-macbookpro/3690-1214458315718/2-0");
        expect(endpoint.getBinding()).andReturn(binding);
        expect(binding.createSubmitSm(exchange)).andReturn(submitSm);
        expect(submitSm.getServiceType()).andReturn("CMT");
        expect(submitSm.getSourceAddrTon()).andReturn(TypeOfNumber.UNKNOWN.value());
        expect(submitSm.getSourceAddrNpi()).andReturn(NumberingPlanIndicator.UNKNOWN.value());
        expect(submitSm.getSourceAddr()).andReturn("1616");
        expect(submitSm.getDestAddrTon()).andReturn(TypeOfNumber.UNKNOWN.value());
        expect(submitSm.getDestAddrNpi()).andReturn(NumberingPlanIndicator.UNKNOWN.value());
        expect(submitSm.getDestAddress()).andReturn("1717");
        expect(submitSm.getProtocolId()).andReturn((byte) 0);
        expect(submitSm.getPriorityFlag()).andReturn((byte) 1);
        expect(submitSm.getScheduleDeliveryTime()).andReturn("090830230627004+");
        expect(submitSm.getValidityPeriod()).andReturn("090831232000004+");
        expect(submitSm.getRegisteredDelivery())
            .andReturn(SMSCDeliveryReceipt.SUCCESS_FAILURE.value());
        expect(submitSm.getReplaceIfPresent()).andReturn((byte) 0);
        expect(submitSm.getDataCoding()).andReturn((byte) 4);
        expect(submitSm.getShortMessage()).andReturn("Hello SMPP world!".getBytes("ISO-8859-1"));
        expect(session.submitShortMessage(
                eq("CMT"),
                eq(TypeOfNumber.UNKNOWN),
                eq(NumberingPlanIndicator.UNKNOWN),
                eq("1616"),
                eq(TypeOfNumber.UNKNOWN),
                eq(NumberingPlanIndicator.UNKNOWN),
                eq("1717"),
                isA(ESMClass.class),
                eq((byte) 0),
                eq((byte) 1),
                eq("090830230627004+"),
                eq("090831232000004+"),
                eq(new RegisteredDelivery(SMSCDeliveryReceipt.SUCCESS_FAILURE)),
                eq((byte) 0),
                eq(new GeneralDataCoding(
                        false,
                        false,
                        MessageClass.CLASS1,
                        Alphabet.ALPHA_8_BIT)),
                eq((byte) 0),
                aryEq("Hello SMPP world!".getBytes("ISO-8859-1"))))
            .andReturn("1");
        expect(exchange.getPattern()).andReturn(ExchangePattern.InOut);
        expect(exchange.getOut()).andReturn(message);
        message.setHeader(SmppBinding.ID, "1"); // expectation without return value
        expect(exchange.getExchangeId()).andReturn("ID-muellerc-macbookpro/3690-1214458315718/2-0");
        
        replay(session, endpoint, binding, exchange, message, submitSm);
        
        producer.doStart();
        producer.process(exchange);
        
        verify(session, endpoint, binding, exchange, message, submitSm);
    }
    
    @Test
    public void getterShouldReturnTheSetValues() {
        assertSame(endpoint, producer.getEndpoint());
        assertSame(configuration, producer.getConfiguration());
    }
}