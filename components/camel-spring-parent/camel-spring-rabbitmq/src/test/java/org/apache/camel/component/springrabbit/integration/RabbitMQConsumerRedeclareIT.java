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
package org.apache.camel.component.springrabbit.integration;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.CamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A queue that the broker deletes when its consumer goes ({@code arg.queue.autoDelete=true}) is declared again, with
 * its binding, when the listener container reconnects after the broker closed the connection.
 */
public class RabbitMQConsumerRedeclareIT extends RabbitMQITSupport {

    private static final String CONNECTION_NAME = "camel-consumer";
    private static final Pattern CONNECTION = Pattern.compile("\\{[^{}]*}");
    private static final Pattern NAME = Pattern.compile("\"name\":\"([^\"]+)\"");

    private final HttpClient http = HttpClient.newHttpClient();
    // the test publishes and checks the queue with its own connection factory, so the consumer is alone on Camel's
    private ConnectionFactory testConnectionFactory;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getRegistry().lookupByNameAndType("myCF", CachingConnectionFactory.class)
                .setConnectionNameStrategy(cf -> CONNECTION_NAME);
        return context;
    }

    @AfterEach
    void destroyTestConnectionFactory() {
        if (testConnectionFactory != null) {
            ((CachingConnectionFactory) testConnectionFactory).destroy();
        }
    }

    @Test
    public void testAutoDeleteQueueIsDeclaredAgainAfterTheConnectionIsClosed() throws Exception {
        String exchange = uniqueName("news");
        String queue = uniqueName("instance");
        testConnectionFactory = createConnectionFactory(false);
        RabbitTemplate rabbitTemplate = new RabbitTemplate(testConnectionFactory);
        RabbitAdmin admin = new RabbitAdmin(testConnectionFactory);
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("news 1", "news 2");

        rabbitTemplate.convertAndSend(exchange, "", "news 1");
        await().atMost(30, TimeUnit.SECONDS).until(() -> mock.getReceivedCounter() == 1);

        closeConnections(CONNECTION_NAME);

        // the broker deletes the queue, as its consumer is gone
        await().atMost(30, TimeUnit.SECONDS).pollInterval(100, TimeUnit.MILLISECONDS)
                .until(() -> admin.getQueueProperties(queue) == null);
        // the listener container reconnects: the queue is declared and bound again, and consumed
        await().atMost(60, TimeUnit.SECONDS).until(() -> consumers(admin, queue) > 0);

        rabbitTemplate.convertAndSend(exchange, "", "news 2");

        MockEndpoint.assertIsSatisfied(context, 30, TimeUnit.SECONDS);
    }

    private static int consumers(RabbitAdmin admin, String queue) {
        Properties properties = admin.getQueueProperties(queue);
        return properties == null ? 0 : ((Number) properties.get(RabbitAdmin.QUEUE_CONSUMER_COUNT)).intValue();
    }

    /**
     * Closes the connections with the given name with the management API of RabbitMQ.
     */
    private void closeConnections(String connectionName) throws Exception {
        List<String> names = new ArrayList<>();
        // the management API lists a connection shortly after it is opened
        await().atMost(30, TimeUnit.SECONDS).until(() -> {
            names.clear();
            String json = send(HttpRequest.newBuilder(
                    URI.create(service.managementUri() + "/api/connections?columns=name,user_provided_name")).GET());
            Matcher connection = CONNECTION.matcher(json);
            while (connection.find()) {
                String fields = connection.group();
                Matcher name = NAME.matcher(fields);
                if (fields.contains("\"user_provided_name\":\"" + connectionName + "\"") && name.find()) {
                    names.add(name.group(1));
                }
            }
            return !names.isEmpty();
        });
        for (String name : names) {
            String path = URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
            send(HttpRequest.newBuilder(URI.create(service.managementUri() + "/api/connections/" + path)).DELETE());
        }
    }

    private String send(HttpRequest.Builder request) throws Exception {
        String credentials = service.managementUsername() + ":" + service.managementPassword();
        HttpResponse<String> response = http.send(
                request.header("Authorization",
                        "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(2, response.statusCode() / 100, response.body());
        return response.body();
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("spring-rabbitmq:" + uniqueName("news") + "?exchangeType=fanout&queues=" + uniqueName("instance")
                     + "&arg.queue.autoDelete=true")
                        .to("mock:result");
            }
        };
    }
}
