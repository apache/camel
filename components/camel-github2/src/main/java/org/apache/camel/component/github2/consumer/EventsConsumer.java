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
package org.apache.camel.component.github2.consumer;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.github2.GitHub2Constants;
import org.apache.camel.component.github2.GitHub2Endpoint;
import org.apache.camel.component.github2.event.GitHub2EventFetchStrategy;
import org.kohsuke.github.GHEventInfo;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.PagedIterable;

public class EventsConsumer extends AbstractGitHub2Consumer {

    private final GitHub2EventFetchStrategy eventFetchStrategy;
    private long lastEventId;

    public EventsConsumer(GitHub2Endpoint endpoint, Processor processor) {
        super(endpoint, processor);

        GitHub2EventFetchStrategy strategy = endpoint.getEventFetchStrategy();
        if (strategy != null) {
            this.eventFetchStrategy = strategy;
        } else {
            this.eventFetchStrategy = new DefaultGitHub2EventFetchStrategy();
        }
    }

    @Override
    protected int poll() throws Exception {
        List<GHEventInfo> newEvents = new ArrayList<>();
        PagedIterable<GHEventInfo> events = eventFetchStrategy.fetchEvents(getRepository());

        for (GHEventInfo event : events) {
            if (event.getId() > lastEventId) {
                newEvents.add(event);
            }
        }

        Queue<Object> exchanges = new ArrayDeque<>();
        if (!newEvents.isEmpty()) {
            newEvents.sort((e1, e2) -> Long.compare(e1.getId(), e2.getId()));
            GHEventInfo latestEvent = newEvents.get(newEvents.size() - 1);
            lastEventId = latestEvent.getId();

            for (GHEventInfo event : newEvents) {
                Exchange e = createExchange(true);
                e.getMessage().setBody(event.getType().name());
                // Store the event info object itself - payload extraction requires specific class
                e.getMessage().setHeader(GitHub2Constants.GITHUB_EVENT_PAYLOAD, event);
                exchanges.add(e);
            }
        }
        return processBatch(exchanges);
    }

    /**
     * Default {@link GitHub2EventFetchStrategy} to fetch events for the GitHub repository configured on the endpoint.
     */
    private static final class DefaultGitHub2EventFetchStrategy implements GitHub2EventFetchStrategy {

        @Override
        public PagedIterable<GHEventInfo> fetchEvents(GHRepository repository) throws IOException {
            return repository.listEvents();
        }
    }
}
