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

import org.apache.camel.Processor;
import org.apache.camel.component.cometd.CometdConsumer.ConsumerService;
import org.cometd.bayeux.server.LocalSession;
import org.cometd.bayeux.server.ServerChannel;
import org.cometd.server.BayeuxServerImpl;
import org.eclipse.jetty.util.log.Logger;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class CometdConsumerTest {

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

    @Before
    public void before() {
        when(bayeuxServerImpl.newLocalSession(anyString())).thenReturn(localSession);
        when(bayeuxServerImpl.getLogger()).thenReturn(logger);
        when(bayeuxServerImpl.getChannel(anyString())).thenReturn(serverChannel);

        testObj = new CometdConsumer(endpoint, processor);
        testObj.setBayeux(bayeuxServerImpl);
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
}
