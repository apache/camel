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
import java.sql.Timestamp;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Address;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.impl.LongStringHelper;
import org.apache.camel.Exchange;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import org.mockito.Mockito;

public class RabbitMQEndpointTest extends CamelTestSupport {

    private Envelope envelope = Mockito.mock(Envelope.class);
    private AMQP.BasicProperties properties = Mockito.mock(AMQP.BasicProperties.class);

    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();
        registry.bind("argsConfigurer", new ArgsConfigurer() {
            @Override
            public void configurArgs(Map<String, Object> args) {
                // do nothing here
            }

        });

        HashMap<String, Object> args = new HashMap<>();
        args.put("foo", "bar");
        registry.bind("args", args);

        HashMap<String, Object> moreArgs = new HashMap<>();
        moreArgs.put("fizz", "buzz");
        registry.bind("moreArgs", moreArgs);

        HashMap<String, Object> evenMoreArgs = new HashMap<>();
        evenMoreArgs.put("ping", "pong");
        registry.bind("evenMoreArgs", evenMoreArgs);

        return registry;
    }

    @Test
    public void testCreatingRabbitExchangeSetsStandardHeaders() throws Exception {
        RabbitMQEndpoint endpoint = context.getEndpoint("rabbitmq:localhost/exchange", RabbitMQEndpoint.class);

        String routingKey = UUID.randomUUID().toString();
        String exchangeName = UUID.randomUUID().toString();
        long tag = UUID.randomUUID().toString().hashCode();
        Boolean redelivery = new Random().nextBoolean();

        Mockito.when(envelope.getRoutingKey()).thenReturn(routingKey);
        Mockito.when(envelope.getExchange()).thenReturn(exchangeName);
        Mockito.when(envelope.getDeliveryTag()).thenReturn(tag);
        Mockito.when(envelope.isRedeliver()).thenReturn(redelivery);
        Mockito.when(properties.getHeaders()).thenReturn(null);

        byte[] body = new byte[20];
        Exchange exchange = endpoint.createRabbitExchange(envelope, properties, body);
        assertEquals(exchangeName, exchange.getIn().getHeader(RabbitMQConstants.EXCHANGE_NAME));
        assertEquals(routingKey, exchange.getIn().getHeader(RabbitMQConstants.ROUTING_KEY));
        assertEquals(tag, exchange.getIn().getHeader(RabbitMQConstants.DELIVERY_TAG));
        assertEquals(redelivery, exchange.getIn().getHeader(RabbitMQConstants.REDELIVERY_TAG));
        assertEquals(body, exchange.getIn().getBody());
    }

    @Test
    public void testExchangeNameIsOptional() throws Exception {
        RabbitMQEndpoint endpoint1 = context.getEndpoint("rabbitmq:localhost/", RabbitMQEndpoint.class);
        assertEquals("Get a wrong exchange name", "", endpoint1.getExchangeName());

        RabbitMQEndpoint endpoint2 = context.getEndpoint("rabbitmq:localhost/exchange", RabbitMQEndpoint.class);
        assertEquals("Get a wrong exchange name", "exchange", endpoint2.getExchangeName());
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

        Map<String, Object> customHeaders = new HashMap<>();
        customHeaders.put("stringHeader", "A string");
        customHeaders.put("bigDecimalHeader", new BigDecimal("12.34"));
        customHeaders.put("integerHeader", 42);
        customHeaders.put("doubleHeader", 42.24);
        customHeaders.put("booleanHeader", true);
        customHeaders.put("dateHeader", new Date(0));
        customHeaders.put("byteArrayHeader", "foo".getBytes());
        customHeaders.put("longStringHeader", LongStringHelper.asLongString("Some really long string"));
        customHeaders.put("timestampHeader", new Timestamp(4200));
        customHeaders.put("byteHeader", new Byte((byte)0));
        customHeaders.put("floatHeader", new Float(42.4242));
        customHeaders.put("longHeader", new Long(420000000000000000L));
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
        assertArrayEquals("foo".getBytes(), (byte[])exchange.getIn().getHeader("byteArrayHeader"));
        assertEquals("Some really long string", exchange.getIn().getHeader("longStringHeader"));
        assertEquals(new Timestamp(4200), exchange.getIn().getHeader("timestampHeader"));
        assertEquals(new Byte((byte)0), exchange.getIn().getHeader("byteHeader"));
        assertEquals(new Float(42.4242), exchange.getIn().getHeader("floatHeader"));
        assertEquals(new Long(420000000000000000L), exchange.getIn().getHeader("longHeader"));
        assertEquals(body, exchange.getIn().getBody());
    }

    @Test
    public void creatingExecutorUsesThreadPoolSettings() throws Exception {
        RabbitMQEndpoint endpoint = context.getEndpoint("rabbitmq:localhost/exchange?threadPoolSize=20", RabbitMQEndpoint.class);
        assertEquals(20, endpoint.getThreadPoolSize());

        ThreadPoolExecutor executor = assertIsInstanceOf(ThreadPoolExecutor.class, endpoint.createExecutor());
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
    public void testQueueArgsConfigurer() throws Exception {
        RabbitMQEndpoint endpoint = context.getEndpoint("rabbitmq:localhost/exchange?queueArgsConfigurer=#argsConfigurer", RabbitMQEndpoint.class);
        assertNotNull("We should get the queueArgsConfigurer here.", endpoint.getQueueArgsConfigurer());
        assertNull("We should not get the exchangeArgsConfigurer here.", endpoint.getExchangeArgsConfigurer());
        assertTrue("We should not get the bindingArgsConfigurer here.", endpoint.getBindingArgs().isEmpty());
    }

    @Test
    public void testBindingArgs() throws Exception {
        RabbitMQEndpoint endpoint = context.getEndpoint("rabbitmq:localhost/exchange?bindingArgs=#args", RabbitMQEndpoint.class);
        assertEquals("We should get the bindingArgsConfigurer here.", 1, endpoint.getBindingArgs().size());
        assertNull("We should not get the queueArgsConfigurer here.", endpoint.getQueueArgsConfigurer());
        assertNull("We should not get the exchangeArgsConfigurer here.", endpoint.getExchangeArgsConfigurer());
    }

    @Test
    public void testQueueArgs() throws Exception {
        RabbitMQEndpoint endpoint = context.getEndpoint("rabbitmq:localhost/exchange?queueArgs=#args", RabbitMQEndpoint.class);
        assertEquals("We should get the queueArgs here.", 1, endpoint.getQueueArgs().size());
        assertTrue("We should not get the binding args here.", endpoint.getBindingArgs().isEmpty());
        assertTrue("We should not get the exchange args here.", endpoint.getExchangeArgs().isEmpty());
        assertNull("We should not get the exchangeArgsConfigurer here.", endpoint.getExchangeArgsConfigurer());
        assertNull("We should not get the queueArgsConfigurer here.", endpoint.getQueueArgsConfigurer());
    }

    @Test
    public void testExchangeArgs() throws Exception {
        RabbitMQEndpoint endpoint = context.getEndpoint("rabbitmq:localhost/exchange?exchangeArgs=#args", RabbitMQEndpoint.class);
        assertEquals("We should get the exchangeArgs here.", 1, endpoint.getExchangeArgs().size());
        assertTrue("We should not get the binding args here.", endpoint.getBindingArgs().isEmpty());
        assertTrue("We should not get the queue args here.", endpoint.getQueueArgs().isEmpty());
        assertNull("We should not get the exchangeArgsConfigurer here.", endpoint.getExchangeArgsConfigurer());
        assertNull("We should not get the queueArgsConfigurer here.", endpoint.getQueueArgsConfigurer());
    }

    @Test
    public void testMultiArgsPopulateCorrectEndpointProperties() throws Exception {
        RabbitMQEndpoint endpoint = context.getEndpoint("rabbitmq:localhost/exchange?arg.exchange.e1=v1&arg.exchange.e2=v2&arg.queue.q1=v3&arg.binding.b1=v4",
                                                        RabbitMQEndpoint.class);
        assertEquals("Wrong number of args", 4, endpoint.getArgs().size());
        assertEquals("Wrong number of args", 1, endpoint.getBindingArgs().size());
        assertEquals("Wrong number of args", 2, endpoint.getExchangeArgs().size());
        assertEquals("Wrong number of args", 1, endpoint.getQueueArgs().size());
    }

    @Test
    public void testMultiArgsCombinedWithIndividuallySpecified() throws Exception {
        // setup two arguments for each rabbit fundamental.
        // Configured inline and via named map in the camel registry
        RabbitMQEndpoint endpoint = context.getEndpoint("rabbitmq:localhost/exchange" + "?arg.exchange.e1=v1&exchangeArgs=#args" + "&arg.queue.q1=v2&queueArgs=#moreArgs"
                                                        + "&arg.binding.b1=v3&bindingArgs=#evenMoreArgs", RabbitMQEndpoint.class);

        // The multi-value inline has 3
        Map<String, Object> inlineArgs = endpoint.getArgs();
        assertEquals("Wrong number of args", 3, inlineArgs.size());
        assertTrue(inlineArgs.containsKey("exchange.e1"));
        assertTrue(inlineArgs.containsKey("queue.q1"));
        assertTrue(inlineArgs.containsKey("binding.b1"));

        Map<String, Object> exchangeArgs = endpoint.getExchangeArgs();
        assertEquals("Wrong number of exchange args", 2, exchangeArgs.size());
        assertTrue("Should contain the individually specified exchange args", exchangeArgs.containsKey("foo"));
        assertTrue("Should contain the args in the multi-value map", exchangeArgs.containsKey("e1"));

        Map<String, Object> queueArgs = endpoint.getQueueArgs();
        assertEquals("Wrong number of queue args", 2, queueArgs.size());
        assertTrue("Should contain the individually specified queue args", queueArgs.containsKey("fizz"));
        assertTrue("Should contain the args in the multi-value map", queueArgs.containsKey("q1"));

        Map<String, Object> bindingArgs = endpoint.getBindingArgs();
        assertEquals("Wrong number of binding args", 2, bindingArgs.size());
        assertTrue("Should contain the individually specified binding args", bindingArgs.containsKey("ping"));
        assertTrue("Should contain the args in the multi-value map", bindingArgs.containsKey("b1"));
    }

    @Test
    public void brokerEndpointAddressesSettings() throws Exception {
        RabbitMQEndpoint endpoint = context.getEndpoint("rabbitmq:localhost/exchange?addresses=server1:12345,server2:12345", RabbitMQEndpoint.class);
        assertEquals("Wrong size of endpoint addresses.", 2, endpoint.getAddresses().length);
        assertEquals("Get a wrong endpoint address.", new Address("server1", 12345), endpoint.getAddresses()[0]);
        assertEquals("Get a wrong endpoint address.", new Address("server2", 12345), endpoint.getAddresses()[1]);
    }

    private ConnectionFactory createConnectionFactory(String uri) throws TimeoutException {
        RabbitMQEndpoint endpoint = context.getEndpoint(uri, RabbitMQEndpoint.class);
        try {
            endpoint.connect(Executors.newSingleThreadExecutor());
        } catch (IOException ioExc) {
            // Doesn't matter if RabbitMQ is not available
            log.debug("RabbitMQ not available", ioExc);
        }
        return endpoint.getConnectionFactory();
    }

    @Test
    public void testCreateConnectionFactoryDefault() throws Exception {
        ConnectionFactory connectionFactory = createConnectionFactory("rabbitmq:localhost:1234/exchange");

        assertEquals("localhost", connectionFactory.getHost());
        assertEquals(1234, connectionFactory.getPort());
        assertEquals(ConnectionFactory.DEFAULT_VHOST, connectionFactory.getVirtualHost());
        assertEquals(ConnectionFactory.DEFAULT_USER, connectionFactory.getUsername());
        assertEquals(ConnectionFactory.DEFAULT_PASS, connectionFactory.getPassword());
        assertEquals(ConnectionFactory.DEFAULT_CONNECTION_TIMEOUT, connectionFactory.getConnectionTimeout());
        assertEquals(ConnectionFactory.DEFAULT_CHANNEL_MAX, connectionFactory.getRequestedChannelMax());
        assertEquals(ConnectionFactory.DEFAULT_FRAME_MAX, connectionFactory.getRequestedFrameMax());
        assertEquals(ConnectionFactory.DEFAULT_HEARTBEAT, connectionFactory.getRequestedHeartbeat());
        assertFalse(connectionFactory.isSSL());
        assertTrue(connectionFactory.isAutomaticRecoveryEnabled());
        assertEquals(5000, connectionFactory.getNetworkRecoveryInterval());
        assertTrue(connectionFactory.isTopologyRecoveryEnabled());
    }

    @Test
    public void testCreateConnectionFactoryCustom() throws Exception {
        ConnectionFactory connectionFactory = createConnectionFactory("rabbitmq:localhost:1234/exchange" + "?username=userxxx" + "&password=passxxx" + "&connectionTimeout=123"
                                                                      + "&requestedChannelMax=456" + "&requestedFrameMax=789" + "&requestedHeartbeat=987" + "&sslProtocol=true"
                                                                      + "&automaticRecoveryEnabled=true" + "&networkRecoveryInterval=654" + "&topologyRecoveryEnabled=false");

        assertEquals("localhost", connectionFactory.getHost());
        assertEquals(1234, connectionFactory.getPort());
        assertEquals("userxxx", connectionFactory.getUsername());
        assertEquals("passxxx", connectionFactory.getPassword());
        assertEquals(123, connectionFactory.getConnectionTimeout());
        assertEquals(456, connectionFactory.getRequestedChannelMax());
        assertEquals(789, connectionFactory.getRequestedFrameMax());
        assertEquals(987, connectionFactory.getRequestedHeartbeat());
        assertTrue(connectionFactory.isSSL());
        assertTrue(connectionFactory.isAutomaticRecoveryEnabled());
        assertEquals(654, connectionFactory.getNetworkRecoveryInterval());
        assertFalse(connectionFactory.isTopologyRecoveryEnabled());
    }

    @Test
    public void createEndpointWithTransferExceptionEnabled() throws Exception {
        RabbitMQEndpoint endpoint = context.getEndpoint("rabbitmq:localhost/exchange?transferException=true", RabbitMQEndpoint.class);
        assertEquals(true, endpoint.isTransferException());
    }

    @Test
    public void createEndpointWithReplyTimeout() throws Exception {
        RabbitMQEndpoint endpoint = context.getEndpoint("rabbitmq:localhost/exchange?requestTimeout=2000", RabbitMQEndpoint.class);
        assertEquals(2000, endpoint.getRequestTimeout());
    }

    @Test
    public void createEndpointWithRequestTimeoutCheckerInterval() throws Exception {
        RabbitMQEndpoint endpoint = context.getEndpoint("rabbitmq:localhost/exchange?requestTimeoutCheckerInterval=1000", RabbitMQEndpoint.class);
        assertEquals(1000, endpoint.getRequestTimeoutCheckerInterval());
    }

    @Test
    public void createEndpointWithSkipQueueDeclareEnabled() throws Exception {
        RabbitMQEndpoint endpoint = context.getEndpoint("rabbitmq:localhost/exchange?skipQueueDeclare=true", RabbitMQEndpoint.class);
        assertTrue(endpoint.isSkipQueueDeclare());
    }

    @Test
    public void createEndpointWithSkipExchangeDeclareEnabled() throws Exception {
        RabbitMQEndpoint endpoint = context.getEndpoint("rabbitmq:localhost/exchange?skipExchangeDeclare=true", RabbitMQEndpoint.class);
        assertTrue(endpoint.isSkipExchangeDeclare());
    }

    @Test
    public void createEndpointWithSkipQueueBindEndabled() throws Exception {
        RabbitMQEndpoint endpoint = context.getEndpoint("rabbitmq:localhost/exchange?SkipQueueBind=true", RabbitMQEndpoint.class);
        assertTrue(endpoint.isSkipQueueBind());
    }

    @Test
    public void createEndpointWithExclusiveEnabled() throws Exception {
        RabbitMQEndpoint endpoint = context.getEndpoint("rabbitmq:localhost/exchange?exclusive=true", RabbitMQEndpoint.class);
        assertTrue(endpoint.isExclusive());
    }

    @Test
    public void createEndpointWithExclusiveConsumerEnabled() throws Exception {
        RabbitMQEndpoint endpoint = context.getEndpoint("rabbitmq:localhost/exchange?exclusiveConsumer=true", RabbitMQEndpoint.class);
        assertTrue(endpoint.isExclusiveConsumer());
    }

    @Test
    public void createEndpointWithPassiveEnabled() throws Exception {
        RabbitMQEndpoint endpoint = context.getEndpoint("rabbitmq:localhost/exchange?passive=true", RabbitMQEndpoint.class);
        assertTrue(endpoint.isPassive());
    }
}
