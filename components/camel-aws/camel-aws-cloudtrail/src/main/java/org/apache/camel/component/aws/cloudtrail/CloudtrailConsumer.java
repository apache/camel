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
package org.apache.camel.component.aws.cloudtrail;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.support.ScheduledBatchPollingConsumer;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.ObjectHelper;
import software.amazon.awssdk.services.cloudtrail.CloudTrailClient;
import software.amazon.awssdk.services.cloudtrail.model.*;

public class CloudtrailConsumer extends ScheduledBatchPollingConsumer {

    // Cursor for the next lookup window. Kept per-consumer (not static) so multiple cloudtrail
    // routes in the same JVM do not clobber each other's position.
    private Instant lastTime;
    // Event ids already delivered at exactly lastTime. startTime is inclusive, so these events are
    // returned again on the next poll and must be filtered to avoid duplicate delivery.
    private final Set<String> lastProcessedEventIds = new HashSet<>();

    public CloudtrailConsumer(CloudtrailEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (lastTime == null) {
            // Start tailing from the consumer start time rather than replaying CloudTrail history.
            lastTime = Instant.now();
        }
    }

    @Override
    protected int poll() throws Exception {
        List<LookupAttribute> attributes = new ArrayList<>();
        if (ObjectHelper.isNotEmpty(getEndpoint().getConfiguration().getEventSource())) {
            LookupAttribute eventSource = LookupAttribute.builder().attributeKey(LookupAttributeKey.EVENT_SOURCE)
                    .attributeValue(getEndpoint().getConfiguration().getEventSource()).build();
            attributes.add(eventSource);
        }

        // Drain every page of the current window so no event is silently left behind.
        List<Event> events = new ArrayList<>();
        String nextToken = null;
        do {
            LookupEventsRequest.Builder eventsRequestBuilder
                    = LookupEventsRequest.builder().maxResults(getEndpoint().getConfiguration().getMaxResults());
            if (!attributes.isEmpty()) {
                eventsRequestBuilder.lookupAttributes(attributes);
            }
            if (ObjectHelper.isNotEmpty(lastTime)) {
                eventsRequestBuilder.startTime(lastTime);
            }
            if (nextToken != null) {
                eventsRequestBuilder.nextToken(nextToken);
            }

            LookupEventsResponse response = getClient().lookupEvents(eventsRequestBuilder.build());
            events.addAll(response.events());
            nextToken = response.nextToken();
        } while (ObjectHelper.isNotEmpty(nextToken));

        // okay we have some response from aws so lets mark the consumer as ready
        forceConsumerAsReady();

        // CloudTrail returns events newest-first. Advance the cursor to the newest event time and
        // track the ids seen at that instant, skipping any event already delivered on a prior poll.
        Instant newest = lastTime;
        Set<String> newestEventIds = new HashSet<>();
        Queue<Exchange> exchanges = new ArrayDeque<>();
        for (Event event : events) {
            if (lastProcessedEventIds.contains(event.eventId())) {
                continue;
            }
            exchanges.add(createExchange(event));

            Instant eventTime = event.eventTime();
            if (newest == null || eventTime.isAfter(newest)) {
                newest = eventTime;
                newestEventIds.clear();
                newestEventIds.add(event.eventId());
            } else if (eventTime.equals(newest)) {
                newestEventIds.add(event.eventId());
            }
        }

        if (newest != null && newest.equals(lastTime)) {
            // No event newer than the previous boundary; keep accumulating ids at that instant.
            newestEventIds.addAll(lastProcessedEventIds);
        }
        lastTime = newest;
        lastProcessedEventIds.clear();
        lastProcessedEventIds.addAll(newestEventIds);

        return processBatch(CastUtils.cast(exchanges));
    }

    @Override
    public int processBatch(Queue<Object> exchanges) throws Exception {
        int processedExchanges = 0;
        while (!exchanges.isEmpty()) {
            final Exchange exchange = ObjectHelper.cast(Exchange.class, exchanges.poll());

            // use default consumer callback
            AsyncCallback cb = defaultConsumerCallback(exchange, true);
            getAsyncProcessor().process(exchange, cb);
            processedExchanges++;
        }
        return processedExchanges;
    }

    private CloudTrailClient getClient() {
        return getEndpoint().getClient();
    }

    @Override
    public CloudtrailEndpoint getEndpoint() {
        return (CloudtrailEndpoint) super.getEndpoint();
    }

    protected Exchange createExchange(Event event) {
        Exchange exchange = createExchange(true);
        exchange.getMessage().setBody(event.cloudTrailEvent().getBytes(StandardCharsets.UTF_8));
        exchange.getMessage().setHeader(CloudtrailConstants.EVENT_ID, event.eventId());
        exchange.getMessage().setHeader(CloudtrailConstants.EVENT_NAME, event.eventName());
        exchange.getMessage().setHeader(CloudtrailConstants.EVENT_SOURCE, event.eventSource());
        exchange.getMessage().setHeader(CloudtrailConstants.USERNAME, event.username());
        return exchange;
    }
}
