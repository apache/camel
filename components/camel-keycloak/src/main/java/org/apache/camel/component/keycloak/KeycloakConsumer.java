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

package org.apache.camel.component.keycloak;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.support.ScheduledBatchPollingConsumer;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.ObjectHelper;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.AdminEventRepresentation;
import org.keycloak.representations.idm.EventRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Keycloak consumer that polls for events or admin events from a Keycloak realm.
 * <p>
 * The consumer supports:
 * <ul>
 * <li>Consuming regular user events (login, logout, etc.)</li>
 * <li>Consuming admin events (user created, role assigned, etc.)</li>
 * <li>Configurable polling interval and batch size</li>
 * <li>Automatic tracking of last consumed event to avoid duplicates</li>
 * </ul>
 */
public class KeycloakConsumer extends ScheduledBatchPollingConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(KeycloakConsumer.class);
    private static final int MAX_FINGERPRINT_CACHE_SIZE = 1000;

    private Long lastEventTime;
    private final Set<String> processedEventFingerprints = new HashSet<>();

    public KeycloakConsumer(KeycloakEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
    }

    @Override
    public KeycloakEndpoint getEndpoint() {
        return (KeycloakEndpoint) super.getEndpoint();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        LOG.info(
                "Started Keycloak consumer for realm: {}, event type: {}",
                getEndpoint().getConfiguration().getRealm(),
                getEndpoint().getConfiguration().getEventType());
    }

    @Override
    protected int poll() throws Exception {
        Queue<Exchange> queue;

        if ("admin-events".equalsIgnoreCase(getEndpoint().getConfiguration().getEventType())) {
            queue = pollAdminEvents();
        } else {
            queue = pollEvents();
        }

        return processBatch(CastUtils.cast(queue));
    }

    @Override
    public int processBatch(Queue<Object> exchanges) throws Exception {
        int total = exchanges.size();
        int processed = 0;

        for (int i = 0; i < total && isBatchAllowed(); i++) {
            Exchange exchange = ObjectHelper.cast(Exchange.class, exchanges.poll());
            if (exchange == null) {
                break;
            }

            try {
                getProcessor().process(exchange);
                processed++;
            } catch (Exception e) {
                exchange.setException(e);
            }
        }

        return processed;
    }

    private Queue<Exchange> pollEvents() throws Exception {
        Keycloak keycloakClient = getEndpoint().getKeycloakClient();
        KeycloakConfiguration config = getEndpoint().getConfiguration();
        String realm = config.getRealm();

        if (realm == null) {
            throw new IllegalArgumentException("Realm must be specified for consuming events");
        }

        // Query events from Keycloak
        // Don't use dateFrom filter to avoid issues with inclusive/exclusive semantics
        // Instead rely on client-side fingerprint-based deduplication
        List<String> eventTypes = parseCommaSeparatedList(config.getTypes());
        List<EventRepresentation> events = keycloakClient
                .realm(realm)
                .getEvents(
                        eventTypes, // types
                        config.getClient(), // client
                        config.getUser(), // user
                        config.getDateFrom(), // dateFrom
                        config.getDateTo(), // dateTo
                        config.getIpAddress(), // ipAddress
                        config.getFirst(), // first
                        config.getMaxResults() // max
                        );

        Queue<Exchange> queue = new LinkedList<>();
        long highestEventTime = lastEventTime != null ? lastEventTime : 0;

        for (EventRepresentation event : events) {
            long eventTime = event.getTime();

            // Skip events older than or equal to our last checkpoint (unless it's the same time with new fingerprint)
            if (lastEventTime != null && eventTime < lastEventTime) {
                continue;
            }

            // Create a fingerprint to detect duplicate events
            String fingerprint = getEventFingerprint(event);

            // Skip events we've already processed
            if (processedEventFingerprints.contains(fingerprint)) {
                LOG.trace("Skipping already processed event: {}", fingerprint);
                continue;
            }

            Exchange exchange = createExchange(false);
            exchange.getIn().setBody(event);
            exchange.getIn().setHeader(KeycloakConstants.EVENT_TYPE, "event");
            exchange.getIn().setHeader(KeycloakConstants.EVENT_ID, event.getTime());
            exchange.getIn().setHeader(KeycloakConstants.REALM_NAME, realm);
            queue.add(exchange);

            // Track this event's fingerprint
            processedEventFingerprints.add(fingerprint);

            // Track the highest timestamp we've seen
            if (eventTime > highestEventTime) {
                highestEventTime = eventTime;
            }

            // Prevent unbounded memory growth
            if (processedEventFingerprints.size() > MAX_FINGERPRINT_CACHE_SIZE) {
                processedEventFingerprints.clear();
                LOG.debug("Cleared fingerprint cache due to size limit");
            }
        }

        // Update lastEventTime if we processed events with newer timestamps
        if (highestEventTime > (lastEventTime != null ? lastEventTime : 0)) {
            // Clear fingerprints when moving to a new timestamp to free memory
            processedEventFingerprints.clear();
            lastEventTime = highestEventTime;
            LOG.debug("Updated last event time to: {}", lastEventTime);
        }

        return queue;
    }

    private String getEventFingerprint(EventRepresentation event) {
        // Create a composite key from event properties to uniquely identify it
        return event.getTime() + "|"
                + event.getType() + "|"
                + (event.getUserId() != null ? event.getUserId() : "") + "|"
                + (event.getSessionId() != null ? event.getSessionId() : "") + "|"
                + (event.getIpAddress() != null ? event.getIpAddress() : "");
    }

    private Queue<Exchange> pollAdminEvents() throws Exception {
        Keycloak keycloakClient = getEndpoint().getKeycloakClient();
        KeycloakConfiguration config = getEndpoint().getConfiguration();
        String realm = config.getRealm();

        if (realm == null) {
            throw new IllegalArgumentException("Realm must be specified for consuming admin events");
        }

        // Query admin events from Keycloak
        // Don't use dateFrom filter to avoid issues with inclusive/exclusive semantics
        // Instead rely on client-side fingerprint-based deduplication
        List<String> adminOperationTypes = parseCommaSeparatedList(config.getOperationTypes());
        List<AdminEventRepresentation> adminEvents = keycloakClient
                .realm(realm)
                .getAdminEvents(
                        adminOperationTypes, // operationTypes
                        config.getAuthRealmFilter(), // authRealm
                        config.getAuthClient(), // authClient
                        config.getAuthUser(), // authUser
                        config.getAuthIpAddress(), // authIpAddress
                        config.getResourcePath(), // resourcePath
                        config.getDateFrom(), // dateFrom
                        config.getDateTo(), // dateTo
                        config.getFirst(), // first
                        config.getMaxResults() // max
                        );

        Queue<Exchange> queue = new LinkedList<>();
        long highestEventTime = lastEventTime != null ? lastEventTime : 0;

        for (AdminEventRepresentation adminEvent : adminEvents) {
            long eventTime = adminEvent.getTime();

            // Skip events older than or equal to our last checkpoint (unless it's the same time with new fingerprint)
            if (lastEventTime != null && eventTime < lastEventTime) {
                continue;
            }

            // Create a fingerprint to detect duplicate events
            String fingerprint = getAdminEventFingerprint(adminEvent);

            // Skip events we've already processed
            if (processedEventFingerprints.contains(fingerprint)) {
                LOG.trace("Skipping already processed admin event: {}", fingerprint);
                continue;
            }

            Exchange exchange = createExchange(false);
            exchange.getIn().setBody(adminEvent);
            exchange.getIn().setHeader(KeycloakConstants.EVENT_TYPE, "admin-event");
            exchange.getIn().setHeader(KeycloakConstants.EVENT_ID, adminEvent.getTime());
            exchange.getIn().setHeader(KeycloakConstants.REALM_NAME, realm);
            queue.add(exchange);

            // Track this event's fingerprint
            processedEventFingerprints.add(fingerprint);

            // Track the highest timestamp we've seen
            if (eventTime > highestEventTime) {
                highestEventTime = eventTime;
            }

            // Prevent unbounded memory growth
            if (processedEventFingerprints.size() > MAX_FINGERPRINT_CACHE_SIZE) {
                // Keep only recent fingerprints when cache gets too large
                // This is a simple strategy - in production you'd want a more sophisticated LRU cache
                processedEventFingerprints.clear();
                LOG.debug("Cleared fingerprint cache due to size limit");
            }
        }

        // Update lastEventTime if we processed events with newer timestamps
        if (highestEventTime > (lastEventTime != null ? lastEventTime : 0)) {
            // Clear fingerprints when moving to a new timestamp to free memory
            processedEventFingerprints.clear();
            lastEventTime = highestEventTime;
            LOG.debug("Updated last admin event time to: {}", lastEventTime);
        }

        return queue;
    }

    private String getAdminEventFingerprint(AdminEventRepresentation event) {
        // Create a composite key from event properties to uniquely identify it
        return event.getTime() + "|"
                + (event.getOperationType() != null ? event.getOperationType() : "") + "|"
                + (event.getResourceType() != null ? event.getResourceType() : "") + "|"
                + (event.getResourcePath() != null ? event.getResourcePath() : "") + "|"
                + (event.getAuthDetails() != null && event.getAuthDetails().getUserId() != null
                        ? event.getAuthDetails().getUserId()
                        : "");
    }

    private List<String> parseCommaSeparatedList(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        List<String> result = new java.util.ArrayList<>();
        for (String item : value.split(",")) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result.isEmpty() ? null : result;
    }
}
