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

import org.apache.camel.component.cometd.CometdProducer.ProducerService;
import org.cometd.bayeux.server.LocalSession;
import org.cometd.server.BayeuxServerImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CometdProducerTest {

    private CometdProducer testObj;
    @Mock
    private CometdEndpoint endpoint;
    @Mock
    private BayeuxServerImpl bayeuxServerImpl;
    @Mock
    private LocalSession localSession;

    @BeforeEach
    public void before() {
        when(bayeuxServerImpl.newLocalSession(ArgumentMatchers.isNull())).thenReturn(localSession);
        testObj = new CometdProducer(endpoint);
        testObj.setBayeux(bayeuxServerImpl);
    }

    @Test
    void testStartDoesNotCreateNewProducerService() {
        // setup
        testObj.start();
        ProducerService expectedService = testObj.getProducerService();
        testObj.start();

        // act
        ProducerService result = testObj.getProducerService();

        // assert
        assertEquals(expectedService, result);
    }
}
