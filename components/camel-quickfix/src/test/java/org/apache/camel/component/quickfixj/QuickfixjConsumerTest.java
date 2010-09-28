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

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.impl.ServiceSupport;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mockito;

import quickfix.FixVersions;
import quickfix.Message;
import quickfix.SessionID;

public class QuickfixjConsumerTest {
    private Exchange mockExchange;
    private Processor mockProcessor;
    private Endpoint mockEndpoint;

    @Before
    public void setUp() {
        mockExchange = Mockito.mock(Exchange.class);
        org.apache.camel.Message mockCamelMessage = Mockito.mock(org.apache.camel.Message.class);
        Mockito.when(mockExchange.getIn()).thenReturn(mockCamelMessage);
        
        mockProcessor = Mockito.mock(Processor.class);
        
        mockEndpoint = Mockito.mock(Endpoint.class);
        Mockito.when(mockEndpoint.createExchange(ExchangePattern.InOnly)).thenReturn(mockExchange);        
    }
    
    @Test
    public void processExchangeOnlyWhenStarted() throws Exception {
        QuickfixjConsumer consumer = new QuickfixjConsumer(mockEndpoint, mockProcessor);
        
        Assert.assertThat("Consumer should not be automatically started", 
            ((ServiceSupport)consumer).isStarted(), CoreMatchers.is(false));
        
        // Simulate a message from the FIX engine
        SessionID sessionID = new SessionID(FixVersions.BEGINSTRING_FIX44, "SENDER", "TARGET");       
        consumer.onEvent(QuickfixjEventCategory.AppMessageReceived, sessionID, new Message());
        
        // No expected interaction with processor since component is not started
        Mockito.verifyZeroInteractions(mockProcessor);
        
        consumer.start();
        Assert.assertThat(((ServiceSupport)consumer).isStarted(), CoreMatchers.is(true));
        
        // Simulate a message from the FIX engine
        consumer.onEvent(QuickfixjEventCategory.AppMessageReceived, sessionID, new Message());
        
        // Second message should be processed
        Mockito.verify(mockProcessor).process(Matchers.isA(Exchange.class));
    }
    
    @Test
    public void setExceptionOnExchange() throws Exception {
              
        QuickfixjConsumer consumer = new QuickfixjConsumer(mockEndpoint, mockProcessor);
        consumer.start();
        
        Throwable exception = new Exception("Throwable for test");
        Mockito.doThrow(exception).when(mockProcessor).process(mockExchange);
        
        // Simulate a message from the FIX engine
        SessionID sessionID = new SessionID(FixVersions.BEGINSTRING_FIX44, "SENDER", "TARGET");       
        consumer.onEvent(QuickfixjEventCategory.AppMessageReceived, sessionID, new Message());
        
        Mockito.verify(mockExchange).setException(exception);
    }
}
