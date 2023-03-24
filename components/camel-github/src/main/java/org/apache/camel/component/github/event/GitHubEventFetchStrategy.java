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
package org.apache.camel.component.github.event;

import org.eclipse.egit.github.core.client.PageIterator;
import org.eclipse.egit.github.core.event.Event;
import org.eclipse.egit.github.core.service.EventService;

/**
 * An abstraction that allows customization of how the {@link org.apache.camel.component.github.consumer.EventsConsumer}
 * fetches GitHub events.
 *
 * The default strategy is to fetch events for the repository that was configured on the consumer.
 */
public interface GitHubEventFetchStrategy {

    /**
     * Fetches GitHub events from the {@link EventService}.
     *
     * @param  eventService The {@link EventService} for interacting with the GitHub event APIs
     * @return              {@link PageIterator} of event objects
     */
    PageIterator<Event> fetchEvents(EventService eventService);
}
