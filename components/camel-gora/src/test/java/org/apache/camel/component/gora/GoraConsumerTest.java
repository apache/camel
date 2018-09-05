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
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.gora.persistency.Persistent;
import org.apache.gora.query.Query;
import org.apache.gora.store.DataStore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.Mockito.when;

/**
 * GORA Consumer Tests
 */
@RunWith(MockitoJUnitRunner.class)
public class GoraConsumerTest extends GoraTestSupport {

    /**
     * Mock CamelExchange
     */
    @Mock
    private Exchange mockCamelExchange;

    /**
     * Mock Gora Endpoint
     */
    @Mock
    private GoraEndpoint mockGoraEndpoint;

    /**
     * Mock Gora Configuration
     */
    @Mock
    private GoraConfiguration mockGoraConfiguration;

    /**
     * Mock Camel Message
     */
    @Mock
    private Message mockCamelMessage;

    /**
     * Mock Gora DataStore
     */
    @Mock
    private DataStore<Object, Persistent> mockDatastore;

    /**
     * Mock Processor
     */
    private Processor mockGoraProcessor;

    /**
     * Mock Query
     */
    @Mock
    private Query<Object, Persistent> mockQuery;

    @Test
    public void consumerInstantiationWithMocksShouldSucceed() throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        when(mockDatastore.newQuery()).thenReturn(mockQuery);
        new GoraConsumer(mockGoraEndpoint, mockGoraProcessor, mockGoraConfiguration, mockDatastore);
    }

}
