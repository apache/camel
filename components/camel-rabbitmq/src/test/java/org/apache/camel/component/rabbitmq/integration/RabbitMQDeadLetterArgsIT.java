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

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

import org.apache.camel.BindToRegistry;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.rabbitmq.RabbitMQEndpoint;
import org.apache.camel.test.infra.rabbitmq.services.ConnectionProperties;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class RabbitMQDeadLetterArgsIT extends AbstractRabbitMQIT {
    private static final String QUEUE = "queue";
    private static final String DLQ = QUEUE + "_dlq";
    private static final String QUEUE_SKIP_DECLARE = "queue_skip_declare";
    private static final String DLQ_SKIP_DECLARE = QUEUE_SKIP_DECLARE + "_dlq";

    @EndpointInject("mock:received_dlq")
    private MockEndpoint receivedDlqEndpoint;

    @EndpointInject("mock:received")
    private MockEndpoint receivedEndpoint;

    @Produce("direct:start")
    private ProducerTemplate template;

    @BindToRegistry("dlqArgs")
    private Map<String, Object> dlqArgs = new HashMap<String, Object>() {
        {
            put("dlq.queue.x-max-priority", 10);
        }
    };

    @BindToRegistry("args")
    private Map<String, Object> args = new HashMap<String, Object>() {
        {
            put("queue.x-max-priority", 5);
        }
    };

    @Override
    protected RouteBuilder createRouteBuilder() {
        ConnectionProperties connectionProperties = service.connectionProperties();
        final String localRabbitmqParams = String.format("hostname=%s&portNumber=%d&username=%s&password=%s",
                connectionProperties.hostname(), connectionProperties.port(), connectionProperties.username(),
                connectionProperties.password());

        return new RouteBuilder() {

            @Override
            public void configure() {
                final String endpointUri1 = String.format(
                        "rabbitmq:exchange?%s&queue=%s&deadLetterQueue=%s&autoAck=false&durable=true&args=#dlqArgs&deadLetterExchange=dlqexchange",
                        localRabbitmqParams, QUEUE, DLQ);
                from("direct:start")
                        .to(endpointUri1);
                fromF(endpointUri1)
                        .routeId("consumer")
                        .convertBodyTo(String.class)
                        .to(receivedEndpoint)
                        .throwException(new RuntimeCamelException("Simulated"));

                final String endpointUri2 = String.format(
                        "rabbitmq:anotherExchange?%s&queue=%s&deadLetterQueue=%s&autoAck=false&durable=true&deadLetterExchange=anotherExchange&skipDlqDeclare=true",
                        localRabbitmqParams, QUEUE_SKIP_DECLARE, DLQ_SKIP_DECLARE);
                from("direct:start_skip_dlq_declare")
                        .to(endpointUri2);
                from(endpointUri2)
                        .throwException(new RuntimeCamelException("Simulated"));
                fromF("rabbitmq:anotherExchange?%s&queue=%s&args=#args", localRabbitmqParams, DLQ_SKIP_DECLARE)
                        .convertBodyTo(String.class)
                        .to(receivedDlqEndpoint);
            }
        };
    }

    @Test
    public void testDlq() throws Exception {
        ConnectionProperties connectionProperties = service.connectionProperties();

        template.sendBody("direct:start_skip_dlq_declare", "Hi");
        receivedDlqEndpoint.expectedMessageCount(1);
        receivedDlqEndpoint.expectedBodiesReceived("Hi");

        template.sendBody("direct:start", "Hello");

        receivedEndpoint.expectedMinimumMessageCount(1);
        receivedEndpoint.assertIsSatisfied();

        RabbitMQEndpoint endpoint = (RabbitMQEndpoint) context.getRoute("consumer").getEndpoint();
        assertNotNull(endpoint.getDlqArgs());
        assertEquals(10, endpoint.getDlqArgs().get("x-max-priority"));

        String rabbitApiResponse = template.requestBody(
                String.format(
                        "http://%s:%s/api/queues?authUsername=%s&authPassword=%s&authMethod=Basic&httpMethod=GET",
                        connectionProperties.hostname(), service.getHttpPort(), connectionProperties.username(),
                        connectionProperties.password()),
                "", String.class);

        JsonArray rabbitApiResponseJson = (JsonArray) Jsoner.deserialize(rabbitApiResponse);
        JsonObject dlqObject = (JsonObject) rabbitApiResponseJson.stream().filter(jsonQueueFilter(DLQ)).findAny().orElse(null);
        JsonObject queueObject
                = (JsonObject) rabbitApiResponseJson.stream().filter(jsonQueueFilter(QUEUE)).findAny().orElse(null);
        JsonObject queueSkipDeclareObject = (JsonObject) rabbitApiResponseJson.stream()
                .filter(jsonQueueFilter(QUEUE_SKIP_DECLARE)).findAny().orElse(null);
        JsonObject dlqSkipDeclareObject
                = (JsonObject) rabbitApiResponseJson.stream().filter(jsonQueueFilter(DLQ_SKIP_DECLARE)).findAny().orElse(null);

        assertNotNull(dlqObject,
                String.format("Queue with name '%s' not found in REST API. API response was '%s'", DLQ, rabbitApiResponse));
        assertNotNull(queueObject,
                String.format("Queue with name '%s' not found in REST API. API response was '%s'", QUEUE, rabbitApiResponse));
        assertNotNull(queueObject, String.format("Queue with name '%s' not found in REST API. API response was '%s'",
                QUEUE_SKIP_DECLARE, rabbitApiResponse));
        assertNotNull(dlqSkipDeclareObject, String.format("Queue with name '%s' not found in REST API. API response was '%s'",
                DLQ_SKIP_DECLARE, rabbitApiResponse));

        assertEquals(BigDecimal.valueOf(10), dlqObject.getMap("arguments").get("x-max-priority"));
        assertEquals(BigDecimal.valueOf(5), dlqSkipDeclareObject.getMap("arguments").get("x-max-priority"));
        assertEquals("dlqexchange", queueObject.getMap("arguments").get("x-dead-letter-exchange"));
        assertEquals("anotherExchange", queueSkipDeclareObject.getMap("arguments").get("x-dead-letter-exchange"));
    }

    private Predicate<Object> jsonQueueFilter(String name) {
        return o -> name.equals(((JsonObject) o).getString("name"));
    }
}
