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
package org.apache.camel.component.box.api;

import com.box.sdk.BoxAPIConnection;
import com.box.sdk.BoxAPIException;
import com.box.sdk.EventListener;
import com.box.sdk.EventStream;
import org.apache.camel.RuntimeCamelException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.box.api.BoxHelper.buildBoxApiErrorMessage;

/**
 * Provides operations to manage Box events.
 */
public class BoxEventsManager {

    private static final Logger LOG = LoggerFactory.getLogger(BoxEventsManager.class);

    /**
     * Box connection to authenticated user account.
     */
    private BoxAPIConnection boxConnection;

    private EventStream eventStream;

    /**
     * Create events manager to manage the events of Box connection's authenticated user.
     *
     * @param boxConnection - Box connection to authenticated user account.
     */
    public BoxEventsManager(BoxAPIConnection boxConnection) {
        this.boxConnection = boxConnection;
    }

    /**
     * Create an event stream with optional starting initial position and add listener that will be notified when an
     * event is received.
     *
     * @param startingPosition - the starting position of the event stream.
     * @param listener         - the listener to add to event stream.
     */
    public void listen(EventListener listener, Long startingPosition) {
        try {
            if (listener == null) {
                LOG.debug(BoxHelper.MISSING_LISTENER);
                return;
            }
            LOG.debug("Listening for events with listener={} at startingPosition={}", listener, startingPosition);

            if (startingPosition != null) {
                eventStream = new EventStream(boxConnection, startingPosition);
            } else {
                eventStream = new EventStream(boxConnection);
            }

            eventStream.addListener(listener);

            eventStream.start();
        } catch (BoxAPIException e) {
            throw new RuntimeCamelException(
                    buildBoxApiErrorMessage(e), e);
        }
    }

    public void stopListening() {
        if (eventStream != null && eventStream.isStarted()) {
            eventStream.stop();
        }
        eventStream = null;
    }
}
