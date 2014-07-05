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
package org.apache.camel.component.rabbitmq;

import java.util.HashMap;
import java.util.Map;

import com.rabbitmq.client.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.SimpleRegistry;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class RabbitMQComponentTest {

    private CamelContext context = Mockito.mock(CamelContext.class);

    @Test
    public void testDefaultProperties() throws Exception {
        RabbitMQEndpoint endpoint = createEndpoint(new HashMap<String, Object>());

        assertEquals(14, endpoint.getPortNumber());
        assertEquals(10, endpoint.getThreadPoolSize());
        assertEquals(true, endpoint.isAutoAck());
        assertEquals(true, endpoint.isAutoDelete());
        assertEquals(true, endpoint.isDurable());
        assertEquals("direct", endpoint.getExchangeType());
        assertEquals(ConnectionFactory.DEFAULT_CONNECTION_TIMEOUT, endpoint.getConnectionTimeout());
        assertEquals(ConnectionFactory.DEFAULT_CHANNEL_MAX, endpoint.getRequestedChannelMax());
        assertEquals(ConnectionFactory.DEFAULT_FRAME_MAX, endpoint.getRequestedFrameMax());
        assertEquals(ConnectionFactory.DEFAULT_HEARTBEAT, endpoint.getRequestedHeartbeat());
        assertNull(endpoint.getConnectionFactory());
    }

    @Test
    public void testPropertiesSet() throws Exception {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("username", "coldplay");
        params.put("password", "chrism");
        params.put("autoAck", true);
        params.put("vhost", "vman");
        params.put("threadPoolSize", 515);
        params.put("portNumber", 14123);
        params.put("hostname", "special.host");
        params.put("queue", "queuey");
        params.put("exchangeType", "topic");
        params.put("connectionTimeout", 123);
        params.put("requestedChannelMax", 456);
        params.put("requestedFrameMax", 789);
        params.put("requestedHeartbeat", 321);

        RabbitMQEndpoint endpoint = createEndpoint(params);

        assertEquals("chrism", endpoint.getPassword());
        assertEquals("coldplay", endpoint.getUsername());
        assertEquals("queuey", endpoint.getQueue());
        assertEquals("vman", endpoint.getVhost());
        assertEquals("special.host", endpoint.getHostname());
        assertEquals(14123, endpoint.getPortNumber());
        assertEquals(515, endpoint.getThreadPoolSize());
        assertEquals(true, endpoint.isAutoAck());
        assertEquals(true, endpoint.isAutoDelete());
        assertEquals(true, endpoint.isDurable());
        assertEquals("topic", endpoint.getExchangeType());
        assertEquals(123, endpoint.getConnectionTimeout());
        assertEquals(456, endpoint.getRequestedChannelMax());
        assertEquals(789, endpoint.getRequestedFrameMax());
        assertEquals(321, endpoint.getRequestedHeartbeat());
    }

    private RabbitMQEndpoint createEndpoint(Map<String, Object> params) throws Exception {
        String uri = "rabbitmq:special.host:14/queuey";
        String remaining = "special.host:14/queuey";

        return new RabbitMQComponent(context).createEndpoint(uri, remaining, params);
    }

    @Test
    public void testConnectionFactoryRef() throws Exception {
        SimpleRegistry registry = new SimpleRegistry();
        ConnectionFactory connectionFactoryMock = Mockito.mock(ConnectionFactory.class);
        registry.put("connectionFactoryMock", connectionFactoryMock);

        CamelContext defaultContext = new DefaultCamelContext(registry);

        Map<String, Object> params = new HashMap<String, Object>();
        params.put("connectionFactory", "#connectionFactoryMock");

        RabbitMQEndpoint endpoint = new RabbitMQComponent(defaultContext).createEndpoint("rabbitmq:localhost/exchange", "localhost/exchange", params);

        assertSame(connectionFactoryMock, endpoint.getConnectionFactory());

    }

}
