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

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Connection;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultMessage;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;

public class RabbitMQProducerTest {

    private RabbitMQEndpoint endpoint = Mockito.mock(RabbitMQEndpoint.class);
    private Exchange exchange = Mockito.mock(Exchange.class);
    private Message message = new DefaultMessage(new DefaultCamelContext());
    private Connection conn = Mockito.mock(Connection.class);

    @Before
    public void before() throws IOException, TimeoutException {
        Mockito.when(exchange.getIn()).thenReturn(message);
        Mockito.when(endpoint.connect(any(ExecutorService.class))).thenReturn(conn);
        Mockito.when(conn.createChannel()).thenReturn(null);
        Mockito.when(endpoint.getMessageConverter()).thenReturn(new RabbitMQMessageConverter());
    }

    @Test
    public void testPropertiesUsesContentTypeHeader() throws IOException {
        RabbitMQProducer producer = new RabbitMQProducer(endpoint);
        message.setHeader(RabbitMQConstants.CONTENT_TYPE, "application/json");
        AMQP.BasicProperties props = producer.buildProperties(exchange).build();
        assertEquals("application/json", props.getContentType());
    }

    @Test
    public void testPropertiesUsesCorrelationHeader() throws IOException {
        RabbitMQProducer producer = new RabbitMQProducer(endpoint);
        message.setHeader(RabbitMQConstants.CORRELATIONID, "124544");
        AMQP.BasicProperties props = producer.buildProperties(exchange).build();
        assertEquals("124544", props.getCorrelationId());
    }

    @Test
    public void testPropertiesUsesUserIdHeader() throws IOException {
        RabbitMQProducer producer = new RabbitMQProducer(endpoint);
        message.setHeader(RabbitMQConstants.USERID, "abcd");
        AMQP.BasicProperties props = producer.buildProperties(exchange).build();
        assertEquals("abcd", props.getUserId());
    }

    @Test
    public void testPropertiesUsesMessageIdHeader() throws IOException {
        RabbitMQProducer producer = new RabbitMQProducer(endpoint);
        message.setHeader(RabbitMQConstants.MESSAGE_ID, "abvasweaqQQ");
        AMQP.BasicProperties props = producer.buildProperties(exchange).build();
        assertEquals("abvasweaqQQ", props.getMessageId());
    }

    @Test
    public void testPropertiesUsesDeliveryModeHeader() throws IOException {
        RabbitMQProducer producer = new RabbitMQProducer(endpoint);
        message.setHeader(RabbitMQConstants.DELIVERY_MODE, "444");
        AMQP.BasicProperties props = producer.buildProperties(exchange).build();
        assertEquals(444, props.getDeliveryMode().intValue());
    }

    @Test
    public void testPropertiesUsesClusterIdHeader() throws IOException {
        RabbitMQProducer producer = new RabbitMQProducer(endpoint);
        message.setHeader(RabbitMQConstants.CLUSTERID, "abtasg5r");
        AMQP.BasicProperties props = producer.buildProperties(exchange).build();
        assertEquals("abtasg5r", props.getClusterId());
    }

    @Test
    public void testPropertiesUsesReplyToHeader() throws IOException {
        RabbitMQProducer producer = new RabbitMQProducer(endpoint);
        message.setHeader(RabbitMQConstants.REPLY_TO, "bbbbdfgdfg");
        AMQP.BasicProperties props = producer.buildProperties(exchange).build();
        assertEquals("bbbbdfgdfg", props.getReplyTo());
    }

    @Test
    public void testPropertiesUsesPriorityHeader() throws IOException {
        RabbitMQProducer producer = new RabbitMQProducer(endpoint);
        message.setHeader(RabbitMQConstants.PRIORITY, "15");
        AMQP.BasicProperties props = producer.buildProperties(exchange).build();
        assertEquals(15, props.getPriority().intValue());
    }

    @Test
    public void testPropertiesUsesExpirationHeader() throws IOException {
        RabbitMQProducer producer = new RabbitMQProducer(endpoint);
        message.setHeader(RabbitMQConstants.EXPIRATION, "thursday");
        AMQP.BasicProperties props = producer.buildProperties(exchange).build();
        assertEquals("thursday", props.getExpiration());
    }

    @Test
    public void testPropertiesUsesTypeHeader() throws IOException {
        RabbitMQProducer producer = new RabbitMQProducer(endpoint);
        message.setHeader(RabbitMQConstants.TYPE, "sometype");
        AMQP.BasicProperties props = producer.buildProperties(exchange).build();
        assertEquals("sometype", props.getType());
    }

    @Test
    public void testPropertiesUsesContentEncodingHeader() throws IOException {
        RabbitMQProducer producer = new RabbitMQProducer(endpoint);
        message.setHeader(RabbitMQConstants.CONTENT_ENCODING, "qwergghdfdfgdfgg");
        AMQP.BasicProperties props = producer.buildProperties(exchange).build();
        assertEquals("qwergghdfdfgdfgg", props.getContentEncoding());
    }

    @Test
    public void testPropertiesAppIdHeader() throws IOException {
        RabbitMQProducer producer = new RabbitMQProducer(endpoint);
        message.setHeader(RabbitMQConstants.APP_ID, "qweeqwe");
        AMQP.BasicProperties props = producer.buildProperties(exchange).build();
        assertEquals("qweeqwe", props.getAppId());
    }

    @Test
    public void testPropertiesUsesTimestampHeaderAsLongValue() throws IOException {
        RabbitMQProducer producer = new RabbitMQProducer(endpoint);
        message.setHeader(RabbitMQConstants.TIMESTAMP, "12345123");
        AMQP.BasicProperties props = producer.buildProperties(exchange).build();
        assertEquals(12345123, props.getTimestamp().getTime());
    }

    @Test
    public void testPropertiesUsesTimestampHeaderAsDateValue() throws IOException {
        Date timestamp = new Date();
        RabbitMQProducer producer = new RabbitMQProducer(endpoint);
        message.setHeader(RabbitMQConstants.TIMESTAMP, timestamp);
        AMQP.BasicProperties props = producer.buildProperties(exchange).build();
        assertEquals(timestamp, props.getTimestamp());
    }

    @Test
    public void testPropertiesUsesCustomHeaders() throws IOException {
        RabbitMQProducer producer = new RabbitMQProducer(endpoint);
        Map<String, Object> customHeaders = new HashMap<String, Object>();
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

    private static class Something {
    }
}
