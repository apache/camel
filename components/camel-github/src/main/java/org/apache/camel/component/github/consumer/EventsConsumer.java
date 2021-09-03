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
package org.apache.camel.component.github.consumer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.github.GitHubConstants;
import org.apache.camel.component.github.GitHubEndpoint;
import org.apache.camel.component.github.event.GitHubEventFetchStrategy;
import org.apache.camel.spi.Registry;
import org.eclipse.egit.github.core.Repository;
import org.eclipse.egit.github.core.client.PageIterator;
import org.eclipse.egit.github.core.event.Event;
import org.eclipse.egit.github.core.service.EventService;

public class EventsConsumer extends AbstractGitHubConsumer {

    private final EventService eventService;
    private final GitHubEventFetchStrategy eventFetchStrategy;
    private long lastEventId;

    public EventsConsumer(GitHubEndpoint endpoint, Processor processor) throws Exception {
        super(endpoint, processor);

        Registry registry = endpoint.getCamelContext().getRegistry();
        EventService service = registry.lookupByNameAndType(GitHubConstants.GITHUB_EVENT_SERVICE, EventService.class);
        if (service != null) {
            eventService = service;
        } else {
            eventService = new EventService();
        }

        initService(eventService);

        GitHubEventFetchStrategy strategy = endpoint.getEventFetchStrategy();
        if (strategy != null) {
            eventFetchStrategy = strategy;
        } else {
            eventFetchStrategy = new DefaultGitHubEventFetchStrategy(getRepository());
        }
    }

    @Override
    protected int poll() throws Exception {
        List<Event> newEvents = new ArrayList<>();
        PageIterator<Event> iterator = eventFetchStrategy.fetchEvents(eventService);

        while (iterator.hasNext()) {
            Collection<Event> events = iterator.next();
            for (Event event : events) {
                if (Long.parseLong(event.getId()) > lastEventId) {
                    newEvents.add(event);
                }
            }
        }

        if (!newEvents.isEmpty()) {
            newEvents.sort((e1, e2) -> Long.valueOf(e1.getId()).compareTo(Long.parseLong(e2.getId())));
            Event latestEvent = newEvents.get(newEvents.size() - 1);
            lastEventId = Long.parseLong(latestEvent.getId());

            for (Event event : newEvents) {
                Exchange exchange = createExchange(true);
                exchange.getMessage().setBody(event.getType());
                exchange.getMessage().setHeader(GitHubConstants.GITHUB_EVENT_PAYLOAD, event.getPayload());
                getProcessor().process(exchange);
            }
        }

        return newEvents.size();
    }

    /**
     * Default {@link GitHubEventFetchStrategy} to fetch events for the GitHub repository configured on the endpoint.
     */
    private static final class DefaultGitHubEventFetchStrategy implements GitHubEventFetchStrategy {
        private final Repository repository;

        private DefaultGitHubEventFetchStrategy(Repository repository) {
            this.repository = repository;
        }

        @Override
        public PageIterator<Event> fetchEvents(EventService eventService) {
            return eventService.pageEvents(repository);
        }
    }
}
