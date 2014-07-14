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

package org.apache.camel.component.gora;

import java.lang.reflect.InvocationTargetException;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.gora.query.Query;
import org.apache.gora.store.DataStore;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * GORA Consumer Tests
 *
 */
public class GoraConsumerTest extends GoraTestSupport {

    /**
     * Mock CamelExchange
     */
    private Exchange mockCamelExchange;

    /**
     * Mock Gora Endpoint
     */
    private GoraEndpoint mockGoraEndpoint;

    /**
     * Mock Gora Configuration
     */
    private GoraConfiguration mockGoraConfiguration;

    /**
     * Mock Camel Message
     */
    private Message mockCamelMessage;

    /**
     * Mock Gora DataStore
     */
    private DataStore mockDatastore;

    /**
     * Mock Processor
     */
    private Processor mockGoraProcessor;
    

    @Before
    public void setUp()  {

        //setup mocks
        mockCamelExchange = mock(Exchange.class);
        mockGoraEndpoint = mock(GoraEndpoint.class);
        mockGoraConfiguration = mock(GoraConfiguration.class);
        mockCamelMessage = mock(Message.class);
        mockDatastore = mock(DataStore.class);
        

        //setup default conditions
        when(mockCamelExchange.getIn()).thenReturn(mockCamelMessage);
        when(mockCamelExchange.getPattern()).thenReturn(ExchangePattern.InOnly);
    }


    @Test
    public void consumerInstantiationWithMocksShouldSucceed() throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {


        final Query mockQuery = mock(Query.class);
        when(mockDatastore.newQuery()).thenReturn(mockQuery);
        GoraConsumer goraConsumer = new GoraConsumer(mockGoraEndpoint, mockGoraProcessor, mockGoraConfiguration, mockDatastore);
    }

}
