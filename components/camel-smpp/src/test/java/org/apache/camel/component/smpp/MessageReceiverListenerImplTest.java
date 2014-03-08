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
import org.apache.camel.Processor;
import org.apache.camel.spi.ExceptionHandler;
import org.jsmpp.PDUStringException;
import org.jsmpp.bean.AlertNotification;
import org.jsmpp.bean.DataSm;
import org.jsmpp.bean.DeliverSm;
import org.jsmpp.bean.OptionalParameter;
import org.jsmpp.session.DataSmResult;
import org.jsmpp.session.SMPPSession;
import org.jsmpp.util.MessageIDGenerator;
import org.jsmpp.util.MessageId;
import org.junit.Before;
import org.junit.Test;

import static org.easymock.EasyMock.expect;
import static org.easymock.classextension.EasyMock.createMock;
import static org.easymock.classextension.EasyMock.replay;
import static org.easymock.classextension.EasyMock.verify;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class MessageReceiverListenerImplTest {
    
    private MessageReceiverListenerImpl listener;
    private SmppEndpoint endpoint;
    private Processor processor;
    private ExceptionHandler exceptionHandler;
    
    @Before
    public void setUp() {
        endpoint = createMock(SmppEndpoint.class);
        processor = createMock(Processor.class);
        exceptionHandler = createMock(ExceptionHandler.class);
        
        listener = new MessageReceiverListenerImpl(endpoint, processor, exceptionHandler);
        listener.setMessageIDGenerator(new MessageIDGenerator() {
            public MessageId newMessageId() {
                try {
                    return new MessageId("1");
                } catch (PDUStringException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    @Test
    public void onAcceptAlertNotificationSuccess() throws Exception {
        AlertNotification alertNotification = createMock(AlertNotification.class);
        Exchange exchange = createMock(Exchange.class);
        
        expect(endpoint.createOnAcceptAlertNotificationExchange(alertNotification))
            .andReturn(exchange);
        processor.process(exchange);
        expect(exchange.getException()).andReturn(null);
        
        replay(endpoint, processor, exceptionHandler, alertNotification, exchange);
        
        listener.onAcceptAlertNotification(alertNotification);
        
        verify(endpoint, processor, exceptionHandler, alertNotification, exchange);
    }
    
    @Test
    public void onAcceptDeliverSmException() throws Exception {
        DeliverSm deliverSm = createMock(DeliverSm.class);
        Exchange exchange = createMock(Exchange.class);
        
        expect(endpoint.createOnAcceptDeliverSmExchange(deliverSm))
            .andReturn(exchange);
        processor.process(exchange);
        expect(exchange.getException()).andReturn(null);

        replay(endpoint, processor, exceptionHandler, deliverSm, exchange);
        
        listener.onAcceptDeliverSm(deliverSm);
        
        verify(endpoint, processor, exceptionHandler, deliverSm, exchange);
    }
    
    @Test
    public void onAcceptDataSmSuccess() throws Exception {
        SMPPSession session = createMock(SMPPSession.class);
        DataSm dataSm = createMock(DataSm.class);
        Exchange exchange = createMock(Exchange.class);
        OptionalParameter[] optionalParameters = new OptionalParameter[]{};
        
        expect(endpoint.createOnAcceptDataSm(dataSm, "1"))
            .andReturn(exchange);
        processor.process(exchange);
        expect(exchange.getException()).andReturn(null);
        expect(dataSm.getOptionalParametes())
            .andReturn(optionalParameters);
        
        replay(endpoint, processor, exceptionHandler, session, dataSm, exchange);
        
        DataSmResult result = listener.onAcceptDataSm(dataSm, session);
        
        verify(endpoint, processor, exceptionHandler, session, dataSm, exchange);
        
        assertEquals("1", result.getMessageId());
        assertSame(optionalParameters, result.getOptionalParameters());
    }
    
}