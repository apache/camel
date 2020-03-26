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
package org.apache.camel.component.rabbitmq.integration;

import java.util.stream.StreamSupport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.rabbitmq.RabbitMQConstants;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/**
 * Integration test to check if temporary queue's name change is properly handled after auto recovering
 * caused by connection failure.
 * This test takes advantage of RabbitMQ Management HTTP API provided by RabbitMQ Management Plugin.
 */
public class RabbitMQTemporaryQueueAutoRecoveryIntTest extends AbstractRabbitMQIntTest {

    private static final String EXCHANGE = "ex_temp-queue-test";
    private static final String QUEUE = "q_temp-queue-test";
    private static final String ROUTING_KEY = "k_temp-queue-test";
    private static final String TEMP_QUEUE_NAME = "tempQueueName";
    private static final String TEMP_QUEUE_CONN_NAME = "tempQueueConnName";
    private static final String REQUEST = "Foo request";
    private static final String REPLY = "Bar reply";

    @Produce(uri = "direct:rabbitMQ")
    protected ProducerTemplate directRabbitMQProducer;

    @Produce(uri = "direct:rabbitMQApi-forceCloseConnection")
    protected ProducerTemplate forceCloseConnectionProducer;

    @Produce(uri = "direct:rabbitMQApi-getExchangeBindings")
    protected ProducerTemplate getExchangeBindingsProducer;

    @EndpointInject(uri = "rabbitmq:" + EXCHANGE + "?addresses=localhost:5672&username=cameltest&password=cameltest"
            + "&autoAck=false&queue=" + QUEUE + "&routingKey=" + ROUTING_KEY)
    private Endpoint rabbitMQEndpoint;

    @EndpointInject(uri = "http:localhost:15672/api?authMethod=Basic&authUsername=cameltest&authPassword=cameltest")
    private Endpoint rabbitMQApiEndpoint;

    @EndpointInject(uri = "mock:consuming")
    private MockEndpoint consumingMockEndpoint;

    @EndpointInject(uri = "mock:producing")
    private MockEndpoint producingMockEndpoint;

    @Override
    protected RouteBuilder createRouteBuilder() {

        return new RouteBuilder() {

            @Override
            public void configure() {

                log.info("Building routes...");

                from("direct:rabbitMQ")
                        .id("producingRoute")
                        .log("Sending message to RabbitMQ broker")
                        .to(rabbitMQEndpoint)
                        .to(producingMockEndpoint);

                from(rabbitMQEndpoint)
                        .id("consumingRoute")
                        .log("Receiving message from RabbitMQ broker")
                        .to(consumingMockEndpoint)
                        .setBody(simple(REPLY));

                from("direct:rabbitMQApi-forceCloseConnection")
                        .id("forceCloseConnectionRoute")
                        .log("Getting temporary queue's connection name")
                        .setHeader(Exchange.HTTP_PATH, simple("/queues/%2F/${header." + TEMP_QUEUE_NAME + "}"))
                        .setHeader(Exchange.HTTP_METHOD, simple("GET"))
                        .to(rabbitMQApiEndpoint)
                        .process(exchange -> {
                            String responseJsonString = exchange.getMessage().getBody(String.class);
                            ObjectNode node = new ObjectMapper().readValue(responseJsonString, ObjectNode.class);
                            String connectionName = node.at("/owner_pid_details/name").asText();
                            exchange.getMessage().setHeader(TEMP_QUEUE_CONN_NAME, connectionName);
                        })
                        .log("Force closing temporary queue's connection")
                        .setHeader(Exchange.HTTP_PATH, simple("/connections/${header." + TEMP_QUEUE_CONN_NAME + "}"))
                        .setHeader(Exchange.HTTP_METHOD, simple("DELETE"))
                        .to(rabbitMQApiEndpoint);

                from("direct:rabbitMQApi-getExchangeBindings")
                        .id("getExchangeBindingsRoute")
                        .log("Getting temporary queue's routing key to verify rebinding was successful")
                        .setHeader(Exchange.HTTP_PATH, simple("/exchanges/%2F/" + EXCHANGE + "/bindings/source"))
                        .setHeader(Exchange.HTTP_METHOD, simple("GET"))
                        .to(rabbitMQApiEndpoint)
                        .process(exchange -> {
                            String responseJsonString = exchange.getMessage().getBody(String.class);
                            String tempQueueName = exchange.getMessage().getHeader(TEMP_QUEUE_NAME, String.class);
                            ArrayNode node = new ObjectMapper().readValue(responseJsonString, ArrayNode.class);
                            String tempQueueRoutingKey = StreamSupport.stream(node.spliterator(), false)
                                    .filter(binding -> tempQueueName.equals(binding.get("destination").textValue()))
                                    .findFirst()
                                    .map(binding -> binding.get("routing_key").textValue())
                                    .orElse(null);
                            exchange.getMessage().setBody(tempQueueRoutingKey);
                        });
            }
        };
    }

    /**
     * <p><b>NOTE:</b>Make sure RabbitMQ Management Plugin is enabled
     * and ConnectionFactory#automaticRecovery is set to <code>true</code> (default)</p>
     * <ul>
     * <li>Send first PRC request that automatically creates server-named temporary reply queue</li>
     * <li>Send another PRC request to verify reply-to property stays the same
     * if no connection failure occurred</li>
     * <li>Wait a few seconds to ensure all necessary bindings are created
     * and seen by the RabbitMQ Management HTTP API</li>
     * <li>Forcibly close temporary reply queue's connection and wait another few seconds
     * to let it recover automatically</li>
     * <li>Send one last RPC request and verify reply-to property is changed
     * (assuming the new server-generated name will not be exactly the same)</li>
     * <li>Get new temporary queue's bindings and verify routing key matches queue name</li>
     * </ul>
     *
     * @throws InterruptedException when Thread#sleep is interrupted
     */
    @Test
    public void testReplyToAndBindingsUpdated() throws InterruptedException {

        consumingMockEndpoint.expectedMessageCount(3);
        producingMockEndpoint.expectedMessageCount(3);

        directRabbitMQProducer.requestBody(REQUEST);
        String replyToOriginal = consumingMockEndpoint.getExchanges().get(0).getMessage().getHeader(RabbitMQConstants.REPLY_TO, String.class);

        directRabbitMQProducer.requestBody(REQUEST);
        String replyToVerify = consumingMockEndpoint.getExchanges().get(1).getMessage().getHeader(RabbitMQConstants.REPLY_TO, String.class);

        Thread.sleep(7000);

        forceCloseConnectionProducer.sendBodyAndHeader(null, TEMP_QUEUE_NAME, replyToOriginal);
        Thread.sleep(7000);

        directRabbitMQProducer.requestBody(REQUEST);
        String replyToRecovered = consumingMockEndpoint.getExchanges().get(2).getMessage().getHeader(RabbitMQConstants.REPLY_TO, String.class);

        String tempQueueRoutingKey = (String) getExchangeBindingsProducer.requestBodyAndHeader(null, TEMP_QUEUE_NAME, replyToRecovered);

        assertEquals(replyToVerify, replyToOriginal);
        assertNotEquals(replyToRecovered, replyToOriginal);
        assertEquals(tempQueueRoutingKey, replyToRecovered);
        consumingMockEndpoint.assertIsSatisfied();
        producingMockEndpoint.assertIsSatisfied();
    }
}