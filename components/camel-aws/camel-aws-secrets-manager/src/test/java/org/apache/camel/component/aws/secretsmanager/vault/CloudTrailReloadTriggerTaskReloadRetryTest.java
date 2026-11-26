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
package org.apache.camel.component.aws.secretsmanager.vault;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.PropertiesFunction;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.DefaultContextReloadStrategy;
import org.apache.camel.support.DefaultEndpoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.cloudtrail.CloudTrailClient;
import software.amazon.awssdk.services.cloudtrail.model.Event;
import software.amazon.awssdk.services.cloudtrail.model.LookupEventsRequest;
import software.amazon.awssdk.services.cloudtrail.model.LookupEventsResponse;
import software.amazon.awssdk.services.cloudtrail.model.Resource;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A reload triggered by an update of a secret that fails (here a consumer that cannot start once) is tried again by a
 * later run of the refresh task, and the routes end up started with the new value of the secret.
 */
class CloudTrailReloadTriggerTaskReloadRetryTest {

    private static final String SECRET = "tracked-secret";

    // the value of the secret in the store, resolved when the route is created
    private final Map<String, String> store = new ConcurrentHashMap<>();
    // consumer starts that fail, as when the remote system of the route is briefly unreachable
    private final AtomicInteger failStarts = new AtomicInteger();
    // the password of each consumer that started
    private final List<String> startedWith = new CopyOnWriteArrayList<>();
    // the events CloudTrail returns
    private final List<Event> events = new CopyOnWriteArrayList<>();

    private final CloudTrailClient cloudTrail = mock(CloudTrailClient.class);
    private final SqsClient sqs = mock(SqsClient.class);
    private DefaultCamelContext context;
    private DefaultContextReloadStrategy reload;
    private CloudTrailReloadTriggerTask task;

    @BeforeEach
    void setUp() throws Exception {
        store.put("api", "pw-1");
        when(cloudTrail.lookupEvents(any(LookupEventsRequest.class))).thenAnswer(inv -> {
            LookupEventsRequest request = inv.getArgument(0);
            String eventName = request.lookupAttributes().get(0).attributeValue();
            return LookupEventsResponse.builder()
                    .events(events.stream().filter(e -> e.eventName().equals(eventName)).toList()).build();
        });

        context = new DefaultCamelContext();
        context.getPropertiesComponent().addPropertiesFunction(new PropertiesFunction() {
            @Override
            public String getName() {
                return "store";
            }

            @Override
            public String apply(String remainder) {
                return store.get(remainder);
            }
        });
        context.addComponent("flaky", new FlakyComponent());
        reload = new DefaultContextReloadStrategy();
        context.addService(reload);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("flaky:in?password={{store:api}}").routeId("secured").log("${body}");
            }
        });
        context.start();

        task = new CloudTrailReloadTriggerTask();
        task.setCamelContext(context);
        setField("secrets", SECRET);
        Instant now = Instant.now();
        setField("lastTime", now);
        setField("startedAt", now);
        setField("cloudTrailClient", cloudTrail);
    }

    @AfterEach
    void tearDown() {
        context.stop();
    }

    private void setField(String name, Object value) throws Exception {
        Field f = CloudTrailReloadTriggerTask.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(task, value);
    }

    private void useSqs() throws Exception {
        setField("useSqsNotification", true);
        setField("queueUrl", "http://localhost/queue");
        setField("sqsClient", sqs);
        setField("cloudTrailClient", null);
    }

    private void rotate(String password) {
        store.put("api", password);
        events.add(Event.builder().eventId("event-" + password).eventSource("secretsmanager.amazonaws.com")
                .eventName("PutSecretValue").eventTime(Instant.now())
                .resources(Resource.builder().resourceName(SECRET).build()).build());
    }

    private ServiceStatus routeStatus() {
        return context.getRouteController().getRouteStatus("secured");
    }

    @Test
    void failedReloadIsTriedAgainByTheNextRun() {
        task.run();
        assertEquals(List.of("pw-1"), startedWith);
        assertEquals(0, reload.getReloadCounter() + reload.getFailedCounter(), "no update yet, no reload");

        rotate("pw-2");
        failStarts.set(1);
        task.run();
        assertEquals(1, reload.getFailedCounter(), "the reload failed");
        assertEquals(ServiceStatus.Stopped, routeStatus());

        task.run();
        assertEquals(1, reload.getReloadCounter(), "the next run must reload again");
        assertEquals(ServiceStatus.Started, routeStatus());
        assertEquals(List.of("pw-1", "pw-2"), startedWith, "the route runs with the new value of the secret");

        task.run();
        assertEquals(1, reload.getReloadCounter(), "the update is marked as seen once the reload succeeded");
        assertEquals(1, reload.getFailedCounter());
    }

    @Test
    void failedReloadIsTriedAtMostMaxAttemptsTimes() {
        task.run();
        rotate("pw-2");
        failStarts.set(Integer.MAX_VALUE);
        for (int i = 0; i < CloudTrailReloadTriggerTask.MAX_RELOAD_ATTEMPTS + 3; i++) {
            task.run();
        }
        assertEquals(CloudTrailReloadTriggerTask.MAX_RELOAD_ATTEMPTS, reload.getFailedCounter(),
                "one reload per run, until the update is marked as seen");

        failStarts.set(0);
        task.run();
        assertEquals(0, reload.getReloadCounter(), "the update was marked as seen");

        // the next update reloads again
        rotate("pw-3");
        task.run();
        assertEquals(1, reload.getReloadCounter());
        assertEquals(ServiceStatus.Started, routeStatus());
        assertEquals("pw-3", startedWith.get(startedWith.size() - 1));
    }

    @Test
    void notificationIsDeletedOnlyOnceTheReloadSucceeded() throws Exception {
        useSqs();
        // the queue delivers the notification again as long as it is not deleted
        when(sqs.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(ReceiveMessageResponse.builder()
                .messages(Message.builder().receiptHandle("rh-1")
                        .body("{\"detail\":{\"eventSource\":\"secretsmanager.amazonaws.com\",\"eventName\":\"PutSecretValue\","
                              + "\"requestParameters\":{\"secretId\":\"" + SECRET + "\"},"
                              + "\"eventTime\":\"2026-01-01T00:00:00Z\"}}")
                        .build())
                .build());

        store.put("api", "pw-2");
        failStarts.set(1);
        task.run();
        assertEquals(1, reload.getFailedCounter(), "the reload failed");
        verify(sqs, never()).deleteMessage(any(DeleteMessageRequest.class));

        task.run();
        assertEquals(1, reload.getReloadCounter(), "the notification received again must reload again");
        verify(sqs, times(1)).deleteMessage(any(DeleteMessageRequest.class));
        assertEquals(ServiceStatus.Started, routeStatus());
        assertEquals(List.of("pw-1", "pw-2"), startedWith);
    }

    private final class FlakyComponent extends DefaultComponent {
        @Override
        protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) {
            String password = (String) parameters.remove("password");
            return new DefaultEndpoint(uri, this) {
                @Override
                public Producer createProducer() {
                    throw new UnsupportedOperationException();
                }

                @Override
                public Consumer createConsumer(Processor processor) {
                    return new DefaultConsumer(this, processor) {
                        @Override
                        protected void doStart() throws Exception {
                            if (failStarts.getAndUpdate(n -> n > 0 ? n - 1 : 0) > 0) {
                                throw new IllegalStateException("remote system unreachable");
                            }
                            startedWith.add(password);
                            super.doStart();
                        }
                    };
                }
            };
        }
    }
}
