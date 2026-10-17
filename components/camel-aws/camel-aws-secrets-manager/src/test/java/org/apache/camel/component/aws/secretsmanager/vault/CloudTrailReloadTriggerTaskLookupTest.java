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
 * only the events with an event time at or after {@code startTime}, newest first, at most 50 per page, and a next token
 * while events are left.
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
        List<Event> matching = visibleEvents.stream()
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

    private void addEvent(String name, String secret, Instant time) {
        visibleEvents.add(Event.builder().eventId("id-" + eventIds++).eventSource(SOURCE).eventName(name)
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
        // the secret is updated, then other applications read secrets 60 times before the next check
        addEvent("PutSecretValue", "tracked-secret", t0.plusSeconds(1));
        for (int i = 0; i < 60; i++) {
            addEvent("GetSecretValue", "other-secret", t0.plusSeconds(2 + i % 20));
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
        addEvent("GetSecretValue", "other-secret", t1);

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

            assertNotNull(requests.get(0).startTime(), "the first lookup must not read the whole event history");
            assertFalse(requests.get(0).startTime().isBefore(before.minusSeconds(1)));
            assertFalse(task.getUpdates().containsKey("tracked-secret"));
            verify(reload, times(0)).onReload(task);
            assertEquals(1, requests.size());
        } finally {
            setField(task, "cloudTrailClient", null);
            task.stop();
        }
    }
}
