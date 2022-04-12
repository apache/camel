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

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Connection;
import org.apache.camel.CamelContext;
import org.apache.camel.Channel;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultMessage;
import org.apache.camel.util.ReflectionHelper;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

public class RabbitMQProducerTest {

    private CamelContext context = new DefaultCamelContext();
    private RabbitMQEndpoint endpoint = Mockito.mock(RabbitMQEndpoint.class);
    private Exchange exchange = Mockito.mock(Exchange.class);
    private Message message = new DefaultMessage(context);
    private Connection conn = Mockito.mock(Connection.class);

    @BeforeEach
    public void before() throws IOException, TimeoutException {
        RabbitMQMessageConverter converter = new RabbitMQMessageConverter();
        converter.setAllowCustomHeaders(true);
        Mockito.when(exchange.getIn()).thenReturn(message);
        Mockito.when(exchange.getMessage()).thenReturn(message);
        Mockito.when(endpoint.connect(any(ExecutorService.class))).thenReturn(conn);
        Mockito.when(conn.createChannel()).thenReturn(null);
        Mockito.when(endpoint.getMessageConverter()).thenReturn(converter);
        Mockito.when(endpoint.getCamelContext()).thenReturn(context);
    }

    @Test
    public void testPropertiesUsesContentTypeHeader() {
        RabbitMQProducer producer = new RabbitMQProducer(endpoint);
        message.setHeader(RabbitMQConstants.CONTENT_TYPE, "application/json");
        AMQP.BasicProperties props = producer.buildProperties(exchange).build();
        assertEquals("application/json", props.getContentType());
    }

    @Test
    public void testPropertiesUsesCorrelationHeader() {
        RabbitMQProducer producer = new RabbitMQProducer(endpoint);
        message.setHeader(RabbitMQConstants.CORRELATIONID, "124544");
        AMQP.BasicProperties props = producer.buildProperties(exchange).build();
        assertEquals("124544", props.getCorrelationId());
    }

    @Test
    public void testPropertiesUsesUserIdHeader() {
        RabbitMQProducer producer = new RabbitMQProducer(endpoint);
        message.setHeader(RabbitMQConstants.USERID, "abcd");
        AMQP.BasicProperties props = producer.buildProperties(exchange).build();
        assertEquals("abcd", props.getUserId());
    }

    @Test
    public void testPropertiesUsesMessageIdHeader() {
        RabbitMQProducer producer = new RabbitMQProducer(endpoint);
        message.setHeader(RabbitMQConstants.MESSAGE_ID, "abvasweaqQQ");
        AMQP.BasicProperties props = producer.buildProperties(exchange).build();
        assertEquals("abvasweaqQQ", props.getMessageId());
    }

    @Test
    public void testPropertiesUsesDeliveryModeHeader() {
        RabbitMQProducer producer = new RabbitMQProducer(endpoint);
        message.setHeader(RabbitMQConstants.DELIVERY_MODE, "444");
        AMQP.BasicProperties props = producer.buildProperties(exchange).build();
        assertEquals(444, props.getDeliveryMode().intValue());
    }

    @Test
    public void testPropertiesUsesClusterIdHeader() {
        RabbitMQProducer producer = new RabbitMQProducer(endpoint);
        message.setHeader(RabbitMQConstants.CLUSTERID, "abtasg5r");
        AMQP.BasicProperties props = producer.buildProperties(exchange).build();
        assertEquals("abtasg5r", props.getClusterId());
    }

    @Test
    public void testPropertiesUsesReplyToHeader() {
        RabbitMQProducer producer = new RabbitMQProducer(endpoint);
        message.setHeader(RabbitMQConstants.REPLY_TO, "bbbbdfgdfg");
        AMQP.BasicProperties props = producer.buildProperties(exchange).build();
        assertEquals("bbbbdfgdfg", props.getReplyTo());
    }

    @Test
    public void testPropertiesUsesPriorityHeader() {
        RabbitMQProducer producer = new RabbitMQProducer(endpoint);
        message.setHeader(RabbitMQConstants.PRIORITY, "15");
        AMQP.BasicProperties props = producer.buildProperties(exchange).build();
        assertEquals(15, props.getPriority().intValue());
    }

    @Test
    public void testPropertiesUsesExpirationHeader() {
        RabbitMQProducer producer = new RabbitMQProducer(endpoint);
        message.setHeader(RabbitMQConstants.EXPIRATION, "thursday");
        AMQP.BasicProperties props = producer.buildProperties(exchange).build();
        assertEquals("thursday", props.getExpiration());
    }

    @Test
    public void testPropertiesUsesTypeHeader() {
        RabbitMQProducer producer = new RabbitMQProducer(endpoint);
        message.setHeader(RabbitMQConstants.TYPE, "sometype");
        AMQP.BasicProperties props = producer.buildProperties(exchange).build();
        assertEquals("sometype", props.getType());
    }

    @Test
    public void testPropertiesUsesContentEncodingHeader() {
        RabbitMQProducer producer = new RabbitMQProducer(endpoint);
        message.setHeader(RabbitMQConstants.CONTENT_ENCODING, "qwergghdfdfgdfgg");
        AMQP.BasicProperties props = producer.buildProperties(exchange).build();
        assertEquals("qwergghdfdfgdfgg", props.getContentEncoding());
    }

    @Test
    public void testPropertiesAppIdHeader() {
        RabbitMQProducer producer = new RabbitMQProducer(endpoint);
        message.setHeader(RabbitMQConstants.APP_ID, "qweeqwe");
        AMQP.BasicProperties props = producer.buildProperties(exchange).build();
        assertEquals("qweeqwe", props.getAppId());
    }

    @Test
    public void testPropertiesOverrideNameHeader() {
        RabbitMQProducer producer = new RabbitMQProducer(endpoint);
        message.setHeader(RabbitMQConstants.EXCHANGE_OVERRIDE_NAME, "qweeqwe");
        AMQP.BasicProperties props = producer.buildProperties(exchange).build();
        assertNull(props.getHeaders().get(RabbitMQConstants.EXCHANGE_OVERRIDE_NAME));
    }

    @Test
    public void testPropertiesUsesTimestampHeaderAsLongValue() {
        RabbitMQProducer producer = new RabbitMQProducer(endpoint);
        message.setHeader(RabbitMQConstants.TIMESTAMP, "12345123");
        AMQP.BasicProperties props = producer.buildProperties(exchange).build();
        assertEquals(12345123, props.getTimestamp().getTime());
    }

    @Test
    public void testPropertiesUsesTimestampHeaderAsDateValue() {
        Date timestamp = new Date();
        RabbitMQProducer producer = new RabbitMQProducer(endpoint);
        message.setHeader(RabbitMQConstants.TIMESTAMP, timestamp);
        AMQP.BasicProperties props = producer.buildProperties(exchange).build();
        assertEquals(timestamp, props.getTimestamp());
    }

    @Test
    public void testPropertiesUsesCustomHeaders() {
        RabbitMQProducer producer = new RabbitMQProducer(endpoint);
        Map<String, Object> customHeaders = new HashMap<>();
        customHeaders.put("stringHeader", "A string");
        customHeaders.put("bigDecimalHeader", new BigDecimal("12.34"));
        customHeaders.put("integerHeader", 42);
        customHeaders.put("doubleHeader", 42.24);
        customHeaders.put("booleanHeader", true);
        customHeaders.put("dateHeader", new Date(0));
        customHeaders.put("byteArrayHeader", "foo".getBytes());
        customHeaders.put("invalidHeader", new Something());
        message.setHeaders(customHeaders);
        AMQP.BasicProperties props = producer.buildProperties(exchange).build();
        assertEquals("A string", props.getHeaders().get("stringHeader"));
        assertEquals(new BigDecimal("12.34"), props.getHeaders().get("bigDecimalHeader"));
        assertEquals(42, props.getHeaders().get("integerHeader"));
        assertEquals(42.24, props.getHeaders().get("doubleHeader"));
        assertEquals(true, props.getHeaders().get("booleanHeader"));
        assertEquals(new Date(0), props.getHeaders().get("dateHeader"));
        assertArrayEquals("foo".getBytes(), (byte[]) props.getHeaders().get("byteArrayHeader"));
        assertNull(props.getHeaders().get("invalidHeader"));
    }

    @Test
    public void testChannelPoolConfiguration() throws Exception {
        RabbitMQProducer producer = new RabbitMQProducer(endpoint);
        Mockito.when(endpoint.getChannelPoolMaxSize()).thenReturn(123);
        Mockito.when(endpoint.getChannelPoolMaxWait()).thenReturn(321L);
        producer.doStart();
        Object channelPool = ReflectionHelper.getField(producer.getClass().getDeclaredField("channelPool"), producer);
        assertNotNull(channelPool);
        assertTrue(channelPool instanceof GenericObjectPool);
        GenericObjectPool<Channel> genericObjectPool = (GenericObjectPool<Channel>) channelPool;
        assertEquals(123, genericObjectPool.getMaxTotal());
        assertEquals(321L, genericObjectPool.getMaxWaitDuration().toMillis());
    }

    private static class Something {
    }
}
