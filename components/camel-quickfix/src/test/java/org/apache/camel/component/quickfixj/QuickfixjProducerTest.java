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

import org.apache.camel.Exchange;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mockito;

import quickfix.FixVersions;
import quickfix.Message;
import quickfix.SessionID;

public class QuickfixjProducerTest {
    @Test
    public void setExceptionOnExchange() throws Exception {
        Exchange mockExchange = Mockito.mock(Exchange.class);

        QuickfixjEndpoint mockEndpoint = Mockito.mock(QuickfixjEndpoint.class);
        org.apache.camel.Message mockCamelMessage = Mockito.mock(org.apache.camel.Message.class);
        Mockito.when(mockExchange.getIn()).thenReturn(mockCamelMessage);
        Mockito.when(mockCamelMessage.getBody(Message.class)).thenReturn(new Message());

        SessionID sessionID = new SessionID(FixVersions.BEGINSTRING_FIX44, "SENDER", "TARGET");       
        Mockito.when(mockEndpoint.getSessionID()).thenReturn(sessionID);
        
        QuickfixjProducer producer = new QuickfixjProducer(mockEndpoint);
        
        producer.process(mockExchange);
        
        Mockito.verify(mockExchange).setException(Matchers.isA(IllegalStateException.class));
    }
}
