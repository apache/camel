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
package org.apache.camel.component.smpp;

import org.apache.camel.CamelContext;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.Processor;
import org.apache.camel.spi.ExchangeFactory;
import org.jsmpp.bean.BindType;
import org.jsmpp.bean.NumberingPlanIndicator;
import org.jsmpp.bean.TypeOfNumber;
import org.jsmpp.session.BindParameter;
import org.jsmpp.session.MessageReceiverListener;
import org.jsmpp.session.SMPPSession;
import org.jsmpp.session.SessionStateListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * JUnit test class for <code>org.apache.camel.component.smpp.SmppConsumer</code>
 */
public class SmppConsumerTest {

    private ExchangeFactory exchangeFactory;
    private CamelContext context;
    private ExtendedCamelContext ecc;
    private SmppConsumer consumer;
    private SmppEndpoint endpoint;
    private SmppConfiguration configuration;
    private Processor processor;
    private SMPPSession session;

    @BeforeEach
    public void setUp() {
        configuration = new SmppConfiguration();
        configuration.setServiceType("CMT");
        configuration.setSystemType("cp");
        configuration.setPassword("password");
        context = mock(CamelContext.class);
        ecc = mock(ExtendedCamelContext.class);
        exchangeFactory = mock(ExchangeFactory.class);
        endpoint = mock(SmppEndpoint.class);
        processor = mock(Processor.class);
        session = mock(SMPPSession.class);

        when(endpoint.getCamelContext()).thenReturn(context);
        when(context.getCamelContextExtension()).thenReturn(ecc);
        when(ecc.getExchangeFactory()).thenReturn(exchangeFactory);
        when(exchangeFactory.newExchangeFactory(any())).thenReturn(exchangeFactory);

        // the construction of SmppConsumer will trigger the getCamelContext call
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
        when(endpoint.getConnectionString())
                .thenReturn("smpp://smppclient@localhost:2775");
        BindParameter expectedBindParameter = new BindParameter(
                BindType.BIND_RX,
                "smppclient",
                "password",
                "cp",
                TypeOfNumber.UNKNOWN,
                NumberingPlanIndicator.UNKNOWN,
                "");
        when(session.connectAndBind("localhost", Integer.valueOf(2775), expectedBindParameter))
                .thenReturn("1");

        consumer.doStart();

        verify(session).setEnquireLinkTimer(60000);
        verify(session).setTransactionTimer(10000);
        verify(session).addSessionStateListener(isA(SessionStateListener.class));
        verify(session).setMessageReceiverListener(isA(MessageReceiverListener.class));
        verify(session).connectAndBind("localhost", Integer.valueOf(2775), expectedBindParameter);
    }

    @Test
    public void doStopShouldNotCloseTheSMPPSessionIfItIsNull() throws Exception {
        when(endpoint.getConnectionString())
                .thenReturn("smpp://smppclient@localhost:2775");

        consumer.doStop();
    }

    @Test
    public void doStopShouldCloseTheSMPPSession() throws Exception {
        doStartShouldStartANewSmppSession();
        reset(endpoint, processor, session);

        when(endpoint.getConnectionString())
                .thenReturn("smpp://smppclient@localhost:2775");

        consumer.doStop();

        verify(session).removeSessionStateListener(isA(SessionStateListener.class));
        verify(session).unbindAndClose();
    }

    @Test
    public void addressRangeFromConfigurationIsUsed() throws Exception {
        configuration.setAddressRange("(111*|222*|333*)");
        BindParameter expectedBindParameter = new BindParameter(
                BindType.BIND_RX,
                "smppclient",
                "password",
                "cp",
                TypeOfNumber.UNKNOWN,
                NumberingPlanIndicator.UNKNOWN,
                "(111*|222*|333*)");
        when(session.connectAndBind("localhost",
                Integer.valueOf(2775),
                expectedBindParameter))
                .thenReturn("1");

        consumer.doStart();

        verify(session).connectAndBind("localhost",
                Integer.valueOf(2775),
                expectedBindParameter);
    }

    @Test
    public void getterShouldReturnTheSetValues() {
        assertSame(endpoint, consumer.getEndpoint());
        assertSame(configuration, consumer.getConfiguration());
    }
}
