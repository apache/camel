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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class MessageReceiverListenerImplTest {
    
    private MessageReceiverListenerImpl listener;
    private SmppEndpoint endpoint;
    private Processor processor;
    private ExceptionHandler exceptionHandler;
    
    @Before
    public void setUp() {
        endpoint = mock(SmppEndpoint.class);
        processor = mock(Processor.class);
        exceptionHandler = mock(ExceptionHandler.class);
        
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
        AlertNotification alertNotification = mock(AlertNotification.class);
        Exchange exchange = mock(Exchange.class);
        
        when(endpoint.createOnAcceptAlertNotificationExchange(alertNotification))
            .thenReturn(exchange);
        when(exchange.getException()).thenReturn(null);
        
        listener.onAcceptAlertNotification(alertNotification);
        
        verify(endpoint).createOnAcceptAlertNotificationExchange(alertNotification);
        verify(processor).process(exchange);
    }
    
    @Test
    public void onAcceptDeliverSmException() throws Exception {
        DeliverSm deliverSm = mock(DeliverSm.class);
        Exchange exchange = mock(Exchange.class);
        
        when(endpoint.createOnAcceptDeliverSmExchange(deliverSm))
            .thenReturn(exchange);
        when(exchange.getException()).thenReturn(null);

        listener.onAcceptDeliverSm(deliverSm);
        
        verify(endpoint).createOnAcceptDeliverSmExchange(deliverSm);
        verify(processor).process(exchange);
    }
    
    @Test
    public void onAcceptDataSmSuccess() throws Exception {
        SMPPSession session = mock(SMPPSession.class);
        DataSm dataSm = mock(DataSm.class);
        Exchange exchange = mock(Exchange.class);
        OptionalParameter[] optionalParameters = new OptionalParameter[]{};
        
        when(endpoint.createOnAcceptDataSm(dataSm, "1"))
            .thenReturn(exchange);
        when(exchange.getException()).thenReturn(null);
        when(dataSm.getOptionalParameters())
            .thenReturn(optionalParameters);
        
        DataSmResult result = listener.onAcceptDataSm(dataSm, session);
        
        verify(endpoint).createOnAcceptDataSm(dataSm, "1");
        verify(processor).process(exchange);
        
        assertEquals("1", result.getMessageId());
        assertSame(optionalParameters, result.getOptionalParameters());
    }
    
}
