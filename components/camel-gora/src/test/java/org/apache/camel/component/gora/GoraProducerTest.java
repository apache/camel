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
package org.apache.camel.component.gora;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.gora.persistency.Persistent;
import org.apache.gora.store.DataStore;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * GORA Producer Tests
 */
@RunWith(MockitoJUnitRunner.class)
public class GoraProducerTest extends GoraTestSupport {

    // TODO: Query methods does not yet has tests

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

    @Override
    @Before
    public void setUp()  {
        //setup mocks
        mockCamelExchange = mock(Exchange.class);
        mockGoraEndpoint = mock(GoraEndpoint.class);
        mockGoraConfiguration = mock(GoraConfiguration.class);
        mockCamelMessage = mock(Message.class);

        //setup default conditions
        when(mockCamelExchange.getIn()).thenReturn(mockCamelMessage);
    }

    @Test(expected = RuntimeException.class)
    public void processShouldThrowExceptionIfOperationIsNull() throws Exception {
        final GoraProducer producer = new GoraProducer(mockGoraEndpoint, mockGoraConfiguration, mockDatastore);
        producer.process(mockCamelExchange);
    }

    @Test(expected = RuntimeException.class)
    public void shouldThrowExceptionIfOperationIsUnknown() throws Exception {
        when(mockCamelExchange.getIn()).thenReturn(mockCamelMessage);
        when(mockCamelMessage.getHeader(GoraAttribute.GORA_OPERATION.value)).thenReturn("dah");

        final GoraProducer producer = new GoraProducer(mockGoraEndpoint, mockGoraConfiguration, mockDatastore);
        producer.process(mockCamelExchange);

        verify(mockCamelExchange, atMost(1)).getIn();
        verify(mockCamelMessage, atMost(1)).getHeader(GoraAttribute.GORA_OPERATION.value);
    }

    @Test
    public void shouldInvokeDastorePut() throws Exception {
        when(mockCamelExchange.getIn()).thenReturn(mockCamelMessage);
        when(mockCamelMessage.getHeader(GoraAttribute.GORA_OPERATION.value)).thenReturn("PUT");

        final Long sampleKey = new Long(2);
        when(mockCamelMessage.getHeader(GoraAttribute.GORA_KEY.value)).thenReturn(sampleKey);

        final Persistent sampleValue = mock(Persistent.class);
        when(mockCamelMessage.getBody(Persistent.class)).thenReturn(sampleValue);

        final Message outMessage = mock(Message.class);
        when(mockCamelExchange.getOut()).thenReturn(outMessage);

        final GoraProducer producer = new GoraProducer(mockGoraEndpoint, mockGoraConfiguration, mockDatastore);
        producer.process(mockCamelExchange);

        verify(mockCamelExchange, atLeastOnce()).getIn();
        verify(mockCamelMessage, atLeastOnce()).getHeader(GoraAttribute.GORA_OPERATION.value);
        verify(mockCamelMessage, atLeastOnce()).getHeader(GoraAttribute.GORA_KEY.value);
        verify(mockCamelMessage, atLeastOnce()).getBody(Persistent.class);
        verify(mockDatastore, atMost(1)).put(sampleKey, sampleValue);
    }

    @Test
    public void shouldInvokeDastoreGet() throws Exception {
        when(mockCamelExchange.getIn()).thenReturn(mockCamelMessage);
        when(mockCamelMessage.getHeader(GoraAttribute.GORA_OPERATION.value)).thenReturn("get");

        final Long sampleKey = new Long(2);
        when(mockCamelMessage.getHeader(GoraAttribute.GORA_KEY.value)).thenReturn(sampleKey);

        final Message outMessage = mock(Message.class);
        when(mockCamelExchange.getOut()).thenReturn(outMessage);

        final GoraProducer producer = new GoraProducer(mockGoraEndpoint, mockGoraConfiguration, mockDatastore);
        producer.process(mockCamelExchange);

        verify(mockCamelExchange, atLeastOnce()).getIn();
        verify(mockCamelMessage, atLeastOnce()).getHeader(GoraAttribute.GORA_OPERATION.value);
        verify(mockCamelMessage, atLeastOnce()).getHeader(GoraAttribute.GORA_KEY.value);
        verify(mockDatastore, atMost(1)).get(sampleKey);
    }

    @Test
    public void shouldInvokeDatastoreDelete() throws Exception {
        when(mockCamelExchange.getIn()).thenReturn(mockCamelMessage);
        when(mockCamelMessage.getHeader(GoraAttribute.GORA_OPERATION.value)).thenReturn("dEletE");

        final Long sampleKey = new Long(2);
        when(mockCamelMessage.getHeader(GoraAttribute.GORA_KEY.value)).thenReturn(sampleKey);

        final Message outMessage = mock(Message.class);
        when(mockCamelExchange.getOut()).thenReturn(outMessage);

        final GoraProducer producer = new GoraProducer(mockGoraEndpoint, mockGoraConfiguration, mockDatastore);
        producer.process(mockCamelExchange);

        verify(mockCamelExchange, atLeastOnce()).getIn();
        verify(mockCamelMessage, atLeastOnce()).getHeader(GoraAttribute.GORA_OPERATION.value);
        verify(mockCamelMessage, atLeastOnce()).getHeader(GoraAttribute.GORA_KEY.value);
        verify(mockDatastore, atMost(1)).delete(sampleKey);
    }

    @Test
    public void shouldInvokeDastoreSchemaExists() throws Exception {
        when(mockCamelExchange.getIn()).thenReturn(mockCamelMessage);
        when(mockCamelMessage.getHeader(GoraAttribute.GORA_OPERATION.value)).thenReturn("schemaExists");

        final Message outMessage = mock(Message.class);
        when(mockCamelExchange.getOut()).thenReturn(outMessage);

        final GoraProducer producer = new GoraProducer(mockGoraEndpoint, mockGoraConfiguration, mockDatastore);
        producer.process(mockCamelExchange);

        verify(mockCamelExchange, atLeastOnce()).getIn();
        verify(mockCamelMessage, atLeastOnce()).getHeader(GoraAttribute.GORA_OPERATION.value);
        verify(mockDatastore, atMost(1)).schemaExists();
    }

    @Test
    public void shouldInvokeDastoreCreateSchema() throws Exception {
        when(mockCamelExchange.getIn()).thenReturn(mockCamelMessage);
        when(mockCamelMessage.getHeader(GoraAttribute.GORA_OPERATION.value)).thenReturn("createSchema");

        final Message outMessage = mock(Message.class);
        when(mockCamelExchange.getOut()).thenReturn(outMessage);

        final GoraProducer producer = new GoraProducer(mockGoraEndpoint, mockGoraConfiguration, mockDatastore);
        producer.process(mockCamelExchange);

        verify(mockCamelExchange, atLeastOnce()).getIn();
        verify(mockCamelMessage, atLeastOnce()).getHeader(GoraAttribute.GORA_OPERATION.value);
        verify(mockDatastore, atMost(1)).createSchema();
    }

    @Test
    public void shouldInvokeDastoreGetSchemaName() throws Exception {
        when(mockCamelExchange.getIn()).thenReturn(mockCamelMessage);
        when(mockCamelMessage.getHeader(GoraAttribute.GORA_OPERATION.value)).thenReturn("GetSchemANamE");

        final Message outMessage = mock(Message.class);
        when(mockCamelExchange.getOut()).thenReturn(outMessage);

        final GoraProducer producer = new GoraProducer(mockGoraEndpoint, mockGoraConfiguration, mockDatastore);
        producer.process(mockCamelExchange);

        verify(mockCamelExchange, atLeastOnce()).getIn();
        verify(mockCamelMessage, atLeastOnce()).getHeader(GoraAttribute.GORA_OPERATION.value);
        verify(mockDatastore, atMost(1)).getSchemaName();
    }

    @Test
    public void shouldInvokeDatastoreDeleteSchema() throws Exception {
        when(mockCamelExchange.getIn()).thenReturn(mockCamelMessage);
        when(mockCamelMessage.getHeader(GoraAttribute.GORA_OPERATION.value)).thenReturn("DeleteSChEmA");

        final Message outMessage = mock(Message.class);
        when(mockCamelExchange.getOut()).thenReturn(outMessage);

        final GoraProducer producer = new GoraProducer(mockGoraEndpoint, mockGoraConfiguration, mockDatastore);
        producer.process(mockCamelExchange);

        verify(mockCamelExchange, atLeastOnce()).getIn();
        verify(mockCamelMessage, atLeastOnce()).getHeader(GoraAttribute.GORA_OPERATION.value);
        verify(mockDatastore, atMost(1)).deleteSchema();
    }

}
