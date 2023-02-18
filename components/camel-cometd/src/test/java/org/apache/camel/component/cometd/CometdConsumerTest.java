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
package org.apache.camel.component.cometd;

import java.util.HashSet;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.Processor;
import org.apache.camel.component.cometd.CometdConsumer.ConsumerService;
import org.apache.camel.spi.ExchangeFactory;
import org.cometd.bayeux.MarkedReference;
import org.cometd.bayeux.server.LocalSession;
import org.cometd.bayeux.server.ServerChannel;
import org.cometd.bayeux.server.ServerSession;
import org.cometd.server.BayeuxServerImpl;
import org.eclipse.jetty.util.log.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CometdConsumerTest {

    private static final String USER_NAME = "userName";
    private CometdConsumer testObj;
    @Mock
    private CamelContext context;
    @Mock
    private ExtendedCamelContext ecc;
    @Mock
    private ExchangeFactory exchangeFactory;
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
    @Mock
    private MarkedReference<ServerChannel> markedReferenceServerChannel;

    @BeforeEach
    public void before() {
        when(bayeuxServerImpl.newLocalSession(ArgumentMatchers.isNull())).thenReturn(localSession);
        when(bayeuxServerImpl.createChannelIfAbsent(ArgumentMatchers.isNull())).thenReturn(markedReferenceServerChannel);
        when(markedReferenceServerChannel.getReference()).thenReturn(serverChannel);

        when(endpoint.getCamelContext()).thenReturn(context);
        when(context.getCamelContextExtension()).thenReturn(ecc);
        when(ecc.getExchangeFactory()).thenReturn(exchangeFactory);
        when(exchangeFactory.newExchangeFactory(any())).thenReturn(exchangeFactory);

        testObj = new CometdConsumer(endpoint, processor);
        testObj.setBayeux(bayeuxServerImpl);

        Set<String> attributeNames = new HashSet<>();
        String attributeKey = USER_NAME;
        attributeNames.add(attributeKey);
    }

    @Test
    void testStartDoesntCreateMultipleServices() {
        // setup
        testObj.start();
        ConsumerService expectedService = testObj.getConsumerService();
        testObj.start();

        // act
        ConsumerService result = testObj.getConsumerService();

        // assert
        assertEquals(expectedService, result);
    }

}
