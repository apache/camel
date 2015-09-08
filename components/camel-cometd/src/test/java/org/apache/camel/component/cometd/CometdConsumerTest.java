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
package org.apache.camel.component.cometd;

import java.util.HashSet;
import java.util.Set;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.component.cometd.CometdConsumer.ConsumerService;
import org.cometd.bayeux.server.LocalSession;
import org.cometd.bayeux.server.ServerChannel;
import org.cometd.bayeux.server.ServerMessage;
import org.cometd.bayeux.server.ServerSession;
import org.cometd.server.BayeuxServerImpl;
import org.eclipse.jetty.util.log.Logger;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class CometdConsumerTest {

    private static final String USER_NAME = "userName";
    private static final String MEMEBER_USER_NAME = "bob";
    private CometdConsumer testObj;
    @Mock
    private CometdEndpoint endpoint;
    @Mock
    private Processor processor;
    @Mock
    private BayeuxServerImpl bayeuxServerImpl;
    @Mock
    private LocalSession localSession;
    @Mock
    private Logger logger;
    @Mock
    private ServerChannel serverChannel;
    @Mock
    private ServerSession remote;

    @Before
    public void before() {
        when(bayeuxServerImpl.newLocalSession(anyString())).thenReturn(localSession);
        when(bayeuxServerImpl.getLogger()).thenReturn(logger);
        when(bayeuxServerImpl.getChannel(anyString())).thenReturn(serverChannel);

        testObj = new CometdConsumer(endpoint, processor);
        testObj.setBayeux(bayeuxServerImpl);
        
        Set<String> attributeNames = new HashSet<String>();
        String attributeKey = USER_NAME;
        attributeNames.add(attributeKey);
        when(remote.getAttributeNames()).thenReturn(attributeNames);
        when(remote.getAttribute(attributeKey)).thenReturn(MEMEBER_USER_NAME);
    }

    @Test
    public void testStartDoesntCreateMultipleServices() throws Exception {
        // setup
        testObj.start();
        ConsumerService expectedService = testObj.getConsumerService();
        testObj.start();

        // act
        ConsumerService result = testObj.getConsumerService();

        // assert
        assertEquals(expectedService, result);
    }
    
    @Test
    public void testSessionHeadersAdded() throws Exception {
        // setup
        when(endpoint.isSessionHeadersEnabled()).thenReturn(true);
        testObj.start();
        ServerMessage cometdMessage = mock(ServerMessage.class);
        Exchange exchange = mock(Exchange.class);
        when(endpoint.createExchange()).thenReturn(exchange);
        ArgumentCaptor<Message> transferredMessage = ArgumentCaptor.forClass(Message.class);

        // act
        testObj.getConsumerService().push(remote, "channelName", cometdMessage, "messageId");

        // verify
        verify(exchange).setIn(transferredMessage.capture());
        Message message = transferredMessage.getValue();
        assertEquals(MEMEBER_USER_NAME, message.getHeader(USER_NAME));
    }
}



