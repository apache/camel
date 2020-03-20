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
package org.apache.camel.component.rabbitmq;

import java.util.HashMap;
import java.util.Map;

import com.rabbitmq.client.ConnectionFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.SimpleRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import org.mockito.Mockito;

public class RabbitMQComponentTest extends CamelTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return super.isUseRouteBuilder();
    }

    @Test
    public void testDefaultProperties() throws Exception {
        RabbitMQEndpoint endpoint = createEndpoint(new HashMap<String, Object>());

        assertEquals(14, endpoint.getPortNumber());
        assertEquals(10, endpoint.getThreadPoolSize());
        assertEquals(true, endpoint.isAutoAck());
        assertEquals(true, endpoint.isAutoDelete());
        assertEquals(true, endpoint.isDurable());
        assertEquals(false, endpoint.isExclusiveConsumer());
        assertEquals(false, endpoint.isAllowNullHeaders());
        assertEquals("direct", endpoint.getExchangeType());
        assertEquals(ConnectionFactory.DEFAULT_CONNECTION_TIMEOUT, endpoint.getConnectionTimeout());
        assertEquals(ConnectionFactory.DEFAULT_CHANNEL_MAX, endpoint.getRequestedChannelMax());
        assertEquals(ConnectionFactory.DEFAULT_FRAME_MAX, endpoint.getRequestedFrameMax());
        assertEquals(ConnectionFactory.DEFAULT_HEARTBEAT, endpoint.getRequestedHeartbeat());
        assertNull(endpoint.getConnectionFactory());
    }

    @Test
    public void testPropertiesSet() throws Exception {
        Map<String, Object> params = new HashMap<>();
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
        params.put("exclusiveConsumer", true);
        params.put("allowNullHeaders", true);

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
        assertEquals(true, endpoint.isExclusiveConsumer());
        assertEquals(true, endpoint.isAllowNullHeaders());
    }

    private RabbitMQEndpoint createEndpoint(Map<String, Object> params) throws Exception {
        String uri = "rabbitmq:special.host:14/queuey";
        String remaining = "special.host:14/queuey";

        RabbitMQComponent comp = context.getComponent("rabbitmq", RabbitMQComponent.class);
        comp.setAutoDetectConnectionFactory(false);
        return (RabbitMQEndpoint)comp.createEndpoint(uri, params);
    }

    @Test
    public void testConnectionFactoryRef() throws Exception {
        SimpleRegistry registry = new SimpleRegistry();
        ConnectionFactory connectionFactoryMock = Mockito.mock(ConnectionFactory.class);
        registry.bind("connectionFactoryMock", connectionFactoryMock);

        CamelContext defaultContext = new DefaultCamelContext(registry);

        Map<String, Object> params = new HashMap<>();
        params.put("connectionFactory", "#connectionFactoryMock");

        RabbitMQEndpoint endpoint = new RabbitMQComponent(defaultContext).createEndpoint("rabbitmq:localhost/exchange", "localhost/exchange", params);

        assertSame(connectionFactoryMock, endpoint.getConnectionFactory());

    }

}
