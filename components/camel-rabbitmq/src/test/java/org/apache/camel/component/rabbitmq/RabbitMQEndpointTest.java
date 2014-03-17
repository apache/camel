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

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadPoolExecutor;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Address;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.impl.LongStringHelper;
import org.apache.camel.Exchange;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import org.mockito.Mockito;

public class RabbitMQEndpointTest extends CamelTestSupport {

    private Envelope envelope = Mockito.mock(Envelope.class);
    private AMQP.BasicProperties properties = Mockito.mock(AMQP.BasicProperties.class);

    @Test
    public void testCreatingRabbitExchangeSetsStandardHeaders() throws Exception {
        RabbitMQEndpoint endpoint = context.getEndpoint("rabbitmq:localhost/exchange", RabbitMQEndpoint.class);

        String routingKey = UUID.randomUUID().toString();
        String exchangeName = UUID.randomUUID().toString();
        long tag = UUID.randomUUID().toString().hashCode();

        Mockito.when(envelope.getRoutingKey()).thenReturn(routingKey);
        Mockito.when(envelope.getExchange()).thenReturn(exchangeName);
        Mockito.when(envelope.getDeliveryTag()).thenReturn(tag);
        Mockito.when(properties.getHeaders()).thenReturn(null);

        byte[] body = new byte[20];
        Exchange exchange = endpoint.createRabbitExchange(envelope, properties, body);
        assertEquals(exchangeName, exchange.getIn().getHeader(RabbitMQConstants.EXCHANGE_NAME));
        assertEquals(routingKey, exchange.getIn().getHeader(RabbitMQConstants.ROUTING_KEY));
        assertEquals(tag, exchange.getIn().getHeader(RabbitMQConstants.DELIVERY_TAG));
        assertEquals(body, exchange.getIn().getBody());
    }

    @Test
    public void testCreatingRabbitExchangeSetsCustomHeaders() throws Exception {
        RabbitMQEndpoint endpoint = context.getEndpoint("rabbitmq:localhost/exchange", RabbitMQEndpoint.class);

        String routingKey = UUID.randomUUID().toString();
        String exchangeName = UUID.randomUUID().toString();
        long tag = UUID.randomUUID().toString().hashCode();

        Mockito.when(envelope.getRoutingKey()).thenReturn(routingKey);
        Mockito.when(envelope.getExchange()).thenReturn(exchangeName);
        Mockito.when(envelope.getDeliveryTag()).thenReturn(tag);

        Map<String, Object> customHeaders = new HashMap<String, Object>();
        customHeaders.put("stringHeader", "A string");
        customHeaders.put("bigDecimalHeader", new BigDecimal("12.34"));
        customHeaders.put("integerHeader", 42);
        customHeaders.put("doubleHeader", 42.24);
        customHeaders.put("booleanHeader", true);
        customHeaders.put("dateHeader", new Date(0));
        customHeaders.put("byteArrayHeader", "foo".getBytes());
        customHeaders.put("longStringHeader", LongStringHelper.asLongString("Some really long string"));
        Mockito.when(properties.getHeaders()).thenReturn(customHeaders);

        byte[] body = new byte[20];
        Exchange exchange = endpoint.createRabbitExchange(envelope, properties, body);
        assertEquals(exchangeName, exchange.getIn().getHeader(RabbitMQConstants.EXCHANGE_NAME));
        assertEquals(routingKey, exchange.getIn().getHeader(RabbitMQConstants.ROUTING_KEY));
        assertEquals(tag, exchange.getIn().getHeader(RabbitMQConstants.DELIVERY_TAG));
        assertEquals("A string", exchange.getIn().getHeader("stringHeader"));
        assertEquals(new BigDecimal("12.34"), exchange.getIn().getHeader("bigDecimalHeader"));
        assertEquals(42, exchange.getIn().getHeader("integerHeader"));
        assertEquals(42.24, exchange.getIn().getHeader("doubleHeader"));
        assertEquals(true, exchange.getIn().getHeader("booleanHeader"));
        assertEquals(new Date(0), exchange.getIn().getHeader("dateHeader"));
        assertArrayEquals("foo".getBytes(), (byte[]) exchange.getIn().getHeader("byteArrayHeader"));
        assertEquals("Some really long string", exchange.getIn().getHeader("longStringHeader"));
        assertEquals(body, exchange.getIn().getBody());
    }

    @Test
    public void creatingExecutorUsesThreadPoolSettings() throws Exception {
        RabbitMQEndpoint endpoint = context.getEndpoint("rabbitmq:localhost/exchange?threadPoolSize=20", RabbitMQEndpoint.class);
        assertEquals(20, endpoint.getThreadPoolSize());

        ThreadPoolExecutor executor = assertIsInstanceOf(ThreadPoolExecutor.class,  endpoint.createExecutor());
        assertEquals(20, executor.getCorePoolSize());
    }
    
    @Test
    public void createEndpointWithAutoAckDisabled() throws Exception {
        RabbitMQEndpoint endpoint = context.getEndpoint("rabbitmq:localhost/exchange?autoAck=false", RabbitMQEndpoint.class);
        assertEquals(false, endpoint.isAutoAck());
    }

    @Test
    public void assertSingleton() throws Exception {
        RabbitMQEndpoint endpoint = context.getEndpoint("rabbitmq:localhost/exchange", RabbitMQEndpoint.class);

        assertTrue(endpoint.isSingleton());
    }
    
    @Test
    public void brokerEndpointAddressesSettings() throws Exception {
        RabbitMQEndpoint endpoint = context.getEndpoint("rabbitmq:localhost/exchange?addresses=server1:12345,server2:12345", RabbitMQEndpoint.class);
        assertEquals("Wrong size of endpoint addresses.", 2, endpoint.getAddresses().length);
        assertEquals("Get a wrong endpoint address.", new Address("server1", 12345), endpoint.getAddresses()[0]);
        assertEquals("Get a wrong endpoint address.", new Address("server2", 12345), endpoint.getAddresses()[1]);
    }
}
