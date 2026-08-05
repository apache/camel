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
package org.apache.camel.component.google.calendar.stream;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.Events;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.Processor;
import org.apache.camel.support.EmptyAsyncCallback;
import org.apache.camel.support.ScheduledBatchPollingConsumer;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The GoogleCalendar consumer.
 */
public class GoogleCalendarStreamConsumer extends ScheduledBatchPollingConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(GoogleCalendarStreamConsumer.class);

    private DateTime lastUpdate;
    // the updatedMin filter is inclusive, so the events carrying exactly the lastUpdate timestamp are
    // returned again on the next poll: remember them to not deliver them twice
    private final Set<String> lastUpdateEventIds = new HashSet<>();

    // sync and page tokens for synchronization flow
    // see https://developers.google.com/calendar/v3/sync
    private String syncToken;
    private String pageToken;

    public GoogleCalendarStreamConsumer(Endpoint endpoint, Processor processor) {
        super(endpoint, processor);
    }

    protected GoogleCalendarStreamConfiguration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    protected Calendar getClient() {
        return getEndpoint().getClient();
    }

    @Override
    public GoogleCalendarStreamEndpoint getEndpoint() {
        return (GoogleCalendarStreamEndpoint) super.getEndpoint();
    }

    @Override
    protected int poll() throws Exception {
        Calendar.Events.List request = getClient().events().list(getConfiguration().getCalendarId());
        if (ObjectHelper.isNotEmpty(getConfiguration().getQuery())) {
            request.setQ(getConfiguration().getQuery());
        }
        if (ObjectHelper.isNotEmpty(getConfiguration().getMaxResults())) {
            request.setMaxResults(getConfiguration().getMaxResults());
        }
        // in synchronization flow only set timeMin on first request
        if (getConfiguration().isConsumeFromNow() && syncToken == null) {
            Date date = new Date();
            request.setTimeMin(new DateTime(date));
        }
        if (getConfiguration().isConsiderLastUpdate()) {
            if (ObjectHelper.isNotEmpty(lastUpdate)) {
                request.setUpdatedMin(lastUpdate);
            }
        }

        Queue<Exchange> answer = new LinkedList<>();

        Events c;

        if (getConfiguration().isSyncFlow()) {
            if (syncToken == null && pageToken == null) {
                LOG.info("Performing full sync.");
            } else if (pageToken != null) {
                LOG.info("Requesting next page.");
            } else {
                LOG.info("Performing incremental sync.");
            }

            request.setSyncToken(syncToken);
            request.setPageToken(pageToken);

            try {
                c = request.execute();
            } catch (GoogleJsonResponseException e) {
                if (e.getStatusCode() != 410) {
                    throw e;
                }
                // A 410 status code, "Gone", indicates that the sync token is invalid. Drop the tokens and
                // perform a single full sync instead of recursing into poll() again
                LOG.info("Invalid sync token, clearing sync and page tokens and re-syncing.");
                syncToken = null;
                pageToken = null;
                request.setSyncToken(null);
                request.setPageToken(null);
                if (getConfiguration().isConsumeFromNow()) {
                    request.setTimeMin(new DateTime(new Date()));
                }
                c = request.execute();
            }

            if (c.getItems() == null || c.getItems().isEmpty()) {
                LOG.info("No new events to sync.");
            }

            pageToken = c.getNextPageToken();
            if (c.getNextSyncToken() != null) {
                // Store the sync token from the last request to be used during the next execution.
                syncToken = c.getNextSyncToken();
                LOG.info("Sync complete.");
            }
        } else {
            c = request.setOrderBy("updated").execute();
        }

        if (c != null && c.getItems() != null) {
            for (Event event : selectUndeliveredAndMoveCursor(c.getItems())) {
                answer.add(getEndpoint().createExchange(getEndpoint().getExchangePattern(), event));
            }
        }

        return processBatch(CastUtils.cast(answer));
    }

    /**
     * Returns the events of this poll that a previous poll did not already deliver, and moves the update cursor to the
     * newest event actually returned.
     * <p>
     * The cursor is only moved when something was delivered: a poll that returned nothing must not advance it, or the
     * events modified between the two polls would never be seen.
     */
    List<Event> selectUndeliveredAndMoveCursor(List<Event> events) {
        List<Event> answer = new ArrayList<>(events.size());
        long newestUpdate = lastUpdate != null ? lastUpdate.getValue() : Long.MIN_VALUE;
        Set<String> newestEventIds = new HashSet<>();

        for (Event event : events) {
            DateTime updated = event.getUpdated();
            if (alreadyDelivered(event, updated)) {
                continue;
            }

            answer.add(event);

            if (updated != null) {
                if (updated.getValue() > newestUpdate) {
                    newestUpdate = updated.getValue();
                    newestEventIds.clear();
                    newestEventIds.add(event.getId());
                } else if (updated.getValue() == newestUpdate) {
                    newestEventIds.add(event.getId());
                }
            }
        }

        if (!newestEventIds.isEmpty()) {
            if (lastUpdate != null && newestUpdate == lastUpdate.getValue()) {
                // still the same instant: the events remembered for it have to be kept, or they would be
                // delivered again by the next poll
                lastUpdateEventIds.addAll(newestEventIds);
            } else {
                lastUpdate = new DateTime(newestUpdate);
                lastUpdateEventIds.clear();
                lastUpdateEventIds.addAll(newestEventIds);
            }
        }

        return answer;
    }

    DateTime getLastUpdate() {
        return lastUpdate;
    }

    /**
     * The updatedMin filter is inclusive, so the events carrying exactly the lastUpdate timestamp come back on the next
     * poll. They were already delivered, and the cursor cannot be moved past them without risking events modified
     * within the same instant.
     */
    private boolean alreadyDelivered(Event event, DateTime updated) {
        return updated != null && lastUpdate != null
                && updated.getValue() == lastUpdate.getValue()
                && lastUpdateEventIds.contains(event.getId());
    }

    @Override
    public int processBatch(Queue<Object> exchanges) throws Exception {
        int total = exchanges.size();

        for (int index = 0; index < total && isBatchAllowed(); index++) {
            // only loop if we are started (allowed to run)
            final Exchange exchange = ObjectHelper.cast(Exchange.class, exchanges.poll());
            // add current index and total as properties
            exchange.setProperty(ExchangePropertyKey.BATCH_INDEX, index);
            exchange.setProperty(ExchangePropertyKey.BATCH_SIZE, total);
            exchange.setProperty(ExchangePropertyKey.BATCH_COMPLETE, index == total - 1);

            // update pending number of exchanges
            pendingExchanges = total - index - 1;

            getAsyncProcessor().process(exchange, EmptyAsyncCallback.get());
        }
        return total;
    }

}
