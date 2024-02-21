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
package org.apache.camel.component.azure.eventhubs;

import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;

import com.azure.messaging.eventhubs.models.EventContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventHubsCheckpointUpdaterTimerTask extends TimerTask {

    private static final Logger LOG = LoggerFactory.getLogger(EventHubsCheckpointUpdaterTimerTask.class);

    private EventContext eventContext;
    private final AtomicInteger processedEvents;

    public EventHubsCheckpointUpdaterTimerTask(EventContext eventContext, AtomicInteger processedEvents) {
        this.eventContext = eventContext;
        this.processedEvents = processedEvents;
    }

    @Override
    public void run() {
        if (processedEvents.get() > 0) {
            LOG.debug("checkpointing offset after reaching timeout, with a batch of {}", processedEvents.get());
            eventContext.updateCheckpointAsync()
                    .subscribe(unused -> LOG.debug("Processed one event..."),
                            error -> LOG.debug("Error when updating Checkpoint: {}", error.getMessage()),
                            () -> {
                                LOG.debug("Checkpoint updated.");
                                processedEvents.set(0);
                            });
        } else {
            LOG.debug("skip checkpointing offset even if timeout is reached. No events processed");
        }
    }

    public void setEventContext(EventContext eventContext) {
        this.eventContext = eventContext;
    }
}
