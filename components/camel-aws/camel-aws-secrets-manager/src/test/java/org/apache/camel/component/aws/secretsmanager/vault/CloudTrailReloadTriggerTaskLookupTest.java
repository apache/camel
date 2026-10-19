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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.ContextReloadStrategy;
import org.apache.camel.vault.AwsVaultConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.cloudtrail.CloudTrailClient;
import software.amazon.awssdk.services.cloudtrail.model.Event;
import software.amazon.awssdk.services.cloudtrail.model.LookupAttribute;
import software.amazon.awssdk.services.cloudtrail.model.LookupAttributeKey;
import software.amazon.awssdk.services.cloudtrail.model.LookupEventsRequest;
import software.amazon.awssdk.services.cloudtrail.model.LookupEventsResponse;
import software.amazon.awssdk.services.cloudtrail.model.Resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests the CloudTrail lookups of the refresh task against a fake {@code LookupEvents} that answers like the service:
 * only the events that match the single lookup attribute and have an event time at or after {@code startTime}, newest
 * first, at most 50 per page, and a next token while events are left.
 */
class CloudTrailReloadTriggerTaskLookupTest {

    private static final String SOURCE = "secretsmanager.amazonaws.com";
    private static final int MAX_PAGE_SIZE = 50;

    private final List<Event> visibleEvents = new ArrayList<>();
    private final List<LookupEventsRequest> requests = new ArrayList<>();
    private final CloudTrailClient client = mock(CloudTrailClient.class);
    private final ContextReloadStrategy reload = mock(ContextReloadStrategy.class);
    private DefaultCamelContext context;
    private int eventIds;
    // run in each LookupEvents call, to change the task while its lookups run
    private Runnable onLookup;

    @BeforeEach
    void setUp() throws Exception {
        when(client.lookupEvents(any(LookupEventsRequest.class))).thenAnswer(inv -> lookup(inv.getArgument(0)));
        context = new DefaultCamelContext();
        context.addService(reload);
        context.start();
    }

    @AfterEach
    void tearDown() {
        context.stop();
    }

    private LookupEventsResponse lookup(LookupEventsRequest request) {
        requests.add(request);
        if (onLookup != null) {
            onLookup.run();
        }
        // the service takes at most one lookup attribute
        assertTrue(request.lookupAttributes().size() <= 1, "LookupEvents takes at most one lookup attribute");
        LookupAttribute attribute = request.lookupAttributes().isEmpty() ? null : request.lookupAttributes().get(0);
        List<Event> matching = visibleEvents.stream()
                .filter(e -> attribute == null || matches(attribute, e))
                .filter(e -> request.startTime() == null || !e.eventTime().isBefore(request.startTime()))
                .sorted(Comparator.comparing(Event::eventTime).reversed())
                .toList();
        int pageSize = request.maxResults() == null ? MAX_PAGE_SIZE : Math.min(request.maxResults(), MAX_PAGE_SIZE);
        int from = request.nextToken() == null ? 0 : Integer.parseInt(request.nextToken());
        int to = Math.min(from + pageSize, matching.size());
        LookupEventsResponse.Builder response = LookupEventsResponse.builder().events(matching.subList(from, to));
        if (to < matching.size()) {
            response.nextToken(Integer.toString(to));
        }
        return response.build();
    }

    private static boolean matches(LookupAttribute attribute, Event event) {
        return switch (attribute.attributeKey()) {
            case EVENT_NAME -> attribute.attributeValue().equals(event.eventName());
            case EVENT_SOURCE -> attribute.attributeValue().equals(event.eventSource());
            default -> throw new IllegalArgumentException("Unexpected lookup attribute " + attribute);
        };
    }

    private void addEvent(String name, String secret, Instant time) {
        addEvent(SOURCE, name, secret, time);
    }

    private void addEvent(String source, String name, String secret, Instant time) {
        visibleEvents.add(Event.builder().eventId("id-" + eventIds++).eventSource(source).eventName(name)
                .eventTime(time).resources(Resource.builder().resourceName(secret).build()).build());
    }

    private CloudTrailReloadTriggerTask task(Instant lastTime) throws Exception {
        CloudTrailReloadTriggerTask task = new CloudTrailReloadTriggerTask();
        task.setCamelContext(context);
        setField(task, "cloudTrailClient", client);
        setField(task, "secrets", "tracked-secret");
        setField(task, "lastTime", lastTime);
        return task;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = CloudTrailReloadTriggerTask.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    @Test
    void updateBehindMoreThanOnePageOfOtherEventsReloadsTheContext() throws Exception {
        Instant t0 = Instant.parse("2026-10-17T10:00:00Z");
        CloudTrailReloadTriggerTask task = task(t0);
        // the secret is updated, then other secrets are updated 60 times before the next check
        addEvent("PutSecretValue", "tracked-secret", t0.plusSeconds(1));
        for (int i = 0; i < 60; i++) {
            addEvent("PutSecretValue", "other-secret", t0.plusSeconds(2 + i % 20));
        }

        task.run();

        assertTrue(task.getUpdates().containsKey("tracked-secret"), "the update on the second page must be seen");
        verify(reload, times(1)).onReload(task);
        assertTrue(requests.stream().allMatch(r -> r.maxResults() != null && r.maxResults() <= MAX_PAGE_SIZE),
                "LookupEvents accepts at most 50 results per page");
    }

    @Test
    void updateInTheSameSecondAsTheNewestEventSeenIsNotSkipped() throws Exception {
        Instant t0 = Instant.parse("2026-10-17T10:00:00Z");
        CloudTrailReloadTriggerTask task = task(t0);
        Instant t1 = t0.plusSeconds(10);
        addEvent("UpdateSecret", "other-secret", t1);

        task.run();
        assertFalse(task.getUpdates().containsKey("tracked-secret"));

        // CloudTrail event times are in seconds: an update in the same second becomes visible after the first check
        addEvent("PutSecretValue", "tracked-secret", t1);
        task.run();

        assertTrue(task.getUpdates().containsKey("tracked-secret"), "the update in the same second must be seen");
        verify(reload, times(1)).onReload(task);

        // the events already seen at that second do not reload the context again
        task.run();
        verify(reload, times(1)).onReload(task);
    }

    @Test
    void firstCheckLooksUpFromTheStartOfTheTask() throws Exception {
        AwsVaultConfiguration aws = context.getVaultConfiguration().aws();
        aws.setAccessKey("key");
        aws.setSecretKey("secret");
        aws.setRegion("eu-west-1");
        aws.setSecrets("tracked-secret");
        CloudTrailReloadTriggerTask task = new CloudTrailReloadTriggerTask();
        task.setCamelContext(context);
        Instant before = Instant.now();
        task.start();
        try {
            Field f = CloudTrailReloadTriggerTask.class.getDeclaredField("cloudTrailClient");
            f.setAccessible(true);
            ((CloudTrailClient) f.get(task)).close();
            f.set(task, client);
            // an update made long before the task started
            addEvent("PutSecretValue", "tracked-secret", before.minusSeconds(86400));

            task.run();

            assertEquals(2, requests.size(), "one lookup per update event name");
            for (LookupEventsRequest request : requests) {
                assertNotNull(request.startTime(), "the first lookup must not read the whole event history");
                assertFalse(request.startTime().isBefore(before.minusSeconds(1)));
            }
            assertFalse(task.getUpdates().containsKey("tracked-secret"));
            verify(reload, times(0)).onReload(task);
        } finally {
            setField(task, "cloudTrailClient", null);
            task.stop();
        }
    }

    @Test
    void readsOfSecretsDoNotAddLookupRequests() throws Exception {
        Instant t0 = Instant.parse("2026-10-17T10:00:00Z");
        CloudTrailReloadTriggerTask task = task(t0);
        // a busy account: other applications read secrets 200 times between two checks
        for (int i = 0; i < 200; i++) {
            addEvent(i % 2 == 0 ? "GetSecretValue" : "DescribeSecret", "other-secret", t0.plusSeconds(1 + i % 30));
        }
        addEvent("UpdateSecret", "tracked-secret", t0.plusSeconds(5));

        task.run();

        assertTrue(task.getUpdates().containsKey("tracked-secret"), "an UpdateSecret event must be seen");
        verify(reload, times(1)).onReload(task);
        assertEquals(2, requests.size(), "one request per update event name, whatever the number of reads");
        assertEquals(List.of("PutSecretValue", "UpdateSecret"),
                requests.stream().map(r -> {
                    LookupAttribute a = r.lookupAttributes().get(0);
                    assertEquals(LookupAttributeKey.EVENT_NAME, a.attributeKey());
                    return a.attributeValue();
                }).toList());

        // the next check looks up from the newest update seen and does not reload again
        requests.clear();
        task.run();
        verify(reload, times(1)).onReload(task);
        assertEquals(2, requests.size());
        assertTrue(requests.stream().allMatch(r -> t0.plusSeconds(5).equals(r.startTime())));
    }

    @Test
    void everyPageOfBothLookupsOfOneCheckUsesTheSameStartTime() throws Exception {
        Instant t0 = Instant.parse("2026-10-17T10:00:00Z");
        CloudTrailReloadTriggerTask task = task(t0);
        // two pages of PutSecretValue events and one page of UpdateSecret events
        for (int i = 0; i < 60; i++) {
            addEvent("PutSecretValue", "other-secret", t0.plusSeconds(1 + i % 20));
        }
        addEvent("UpdateSecret", "tracked-secret", t0.plusSeconds(5));
        // the task is shut down while the first page is read: doShutdown clears lastTime
        onLookup = () -> {
            if (requests.size() == 1) {
                try {
                    setField(task, "lastTime", null);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }
        };

        task.run();

        assertEquals(3, requests.size(), "two pages of PutSecretValue and one page of UpdateSecret");
        for (LookupEventsRequest request : requests) {
            assertEquals(t0, request.startTime(), "every page of both lookups must use the start time of the check");
        }
        assertTrue(task.getUpdates().containsKey("tracked-secret"));
    }

    @Test
    void updateEventOfAnotherServiceIsIgnored() throws Exception {
        Instant t0 = Instant.parse("2026-10-17T10:00:00Z");
        CloudTrailReloadTriggerTask task = task(t0);
        // the lookup filters by event name only, and event names are not unique across AWS services
        addEvent("other-service.amazonaws.com", "UpdateSecret", "tracked-secret", t0.plusSeconds(5));

        task.run();

        assertFalse(task.getUpdates().containsKey("tracked-secret"), "an event of another service is not an update");
        verify(reload, times(0)).onReload(task);
        // the event still moves the start time of the next check
        requests.clear();
        task.run();
        assertTrue(requests.stream().allMatch(r -> t0.plusSeconds(5).equals(r.startTime())));
    }
}
