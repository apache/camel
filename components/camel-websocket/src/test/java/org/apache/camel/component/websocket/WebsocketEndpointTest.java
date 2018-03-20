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
package org.apache.camel.component.websocket;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;

@RunWith(MockitoJUnitRunner.class)
public class WebsocketEndpointTest {

    private static final String REMAINING = "foo/bar";
    private static final String URI = "websocket://" + REMAINING;

    @Mock
    private WebsocketComponent component;

    @Mock
    private Processor processor;

    private WebsocketEndpoint websocketEndpoint;

    @Before
    public void setUp() throws Exception {
        websocketEndpoint = new WebsocketEndpoint(component, URI, REMAINING, null);
        component = new WebsocketComponent();
        component.setPort(1988);
        component.setHost("localhost");
        component.createServer();
    }

    /**
     * Test method for {@link org.apache.camel.component.websocket.WebsocketEndpoint#createConsumer(org.apache.camel.Processor)} .
     */
    @Test
    public void testCreateConsumer() throws Exception {
        Consumer consumer = websocketEndpoint.createConsumer(processor);
        component.connect((WebsocketProducerConsumer) consumer);

        assertNotNull(consumer);
        assertEquals(WebsocketConsumer.class, consumer.getClass());
        InOrder inOrder = inOrder(component, processor);
        ArgumentCaptor<NodeSynchronization> synchronizationCaptor = ArgumentCaptor.forClass(NodeSynchronization.class);
        ArgumentCaptor<WebsocketConsumer> consumerCaptor = ArgumentCaptor.forClass(WebsocketConsumer.class);
        inOrder.verify(component, times(1)).addServlet(synchronizationCaptor.capture(), consumerCaptor.capture(), eq(REMAINING));
        inOrder.verifyNoMoreInteractions();

        assertEquals(DefaultNodeSynchronization.class, synchronizationCaptor.getValue().getClass());

        assertEquals(consumer, consumerCaptor.getValue());
    }

    /**
     * Test method for {@link org.apache.camel.component.websocket.WebsocketEndpoint#createProducer()} .
     */
    @Test
    public void testCreateProducer() throws Exception {
        Producer producer = websocketEndpoint.createProducer();
        component.connect((WebsocketProducerConsumer) producer);

        assertNotNull(producer);
        assertEquals(WebsocketProducer.class, producer.getClass());
        InOrder inOrder = inOrder(component, processor);
        ArgumentCaptor<NodeSynchronization> synchronizationCaptor = ArgumentCaptor.forClass(NodeSynchronization.class);
        inOrder.verify(component, times(1)).addServlet(synchronizationCaptor.capture(), (WebsocketConsumer) isNull(), eq(REMAINING));
        inOrder.verifyNoMoreInteractions();

        assertEquals(DefaultNodeSynchronization.class, synchronizationCaptor.getValue().getClass());
    }

    /**
     * Test method for {@link org.apache.camel.component.websocket.WebsocketEndpoint#isSingleton()} .
     */
    @Test
    public void testIsSingleton() {
        assertTrue(websocketEndpoint.isSingleton());
    }
}
