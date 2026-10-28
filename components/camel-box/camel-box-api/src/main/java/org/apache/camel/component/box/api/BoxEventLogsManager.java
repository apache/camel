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

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.box.sdk.BoxAPIConnection;
import com.box.sdk.BoxAPIException;
import com.box.sdk.BoxEvent;
import com.box.sdk.EnterpriseEventsRequest;
import com.box.sdk.EventLog;
import org.apache.camel.RuntimeCamelException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.box.api.BoxHelper.buildBoxApiErrorMessage;

/**
 * Provides operations to read Box enterprise (admin) event logs.
 */
public class BoxEventLogsManager {

    private static final Logger LOG = LoggerFactory.getLogger(BoxEventLogsManager.class);

    /**
     * Box connection to authenticated user account.
     */
    private BoxAPIConnection boxConnection;

    /**
     * Create event logs manager to manage the event logs of Box connection's authenticated user.
     *
     * @param boxConnection - Box connection to authenticated user account.
     */
    public BoxEventLogsManager(BoxAPIConnection boxConnection) {
        this.boxConnection = boxConnection;
    }

    /**
     * Create an event stream with optional starting initial position and add listener that will be notified when an
     * event is received.
     *
     * @param  position - the starting position of the event stream. May be <code>null</code> in which case all events
     *                  within bounds returned.
     * @param  after    - the lower bound on the timestamp of the events returned.
     * @param  before   - the upper bound on the timestamp of the events returned.
     * @param  types    - an optional list of event types to filter by.
     *
     * @return          A list of all the events that met the given criteria. The event log is read chunk by chunk (at
     *                  most 500 events per request) until its end, and all the events are kept in memory, so use a
     *                  narrow time window for a large event log.
     */
    public List<BoxEvent> getEnterpriseEvents(String position, Date after, Date before, BoxEvent.EventType... types) {
        try {
            LOG.debug("Getting all enterprise events occurring between {} and {} {}",
                    after == null ? "unspecified date" : DateFormat.getDateTimeInstance().format(after),
                    before == null ? "unspecified date" : DateFormat.getDateTimeInstance().format(before),
                    position == null ? "" : (" starting at " + position));

            BoxHelper.notNull(after, "after");
            BoxHelper.notNull(before, "before");

            if (types == null) {
                types = new BoxEvent.EventType[0];
            }

            EnterpriseEventsRequest request = new EnterpriseEventsRequest();
            request.after(after).before(before).types(types);

            // The service returns the events in chunks (of at most 500 events) with the position of the next
            // chunk. Read on from that position until a chunk is empty, which is the end of the events within
            // bounds. An event that the service returns in two chunks is kept once.
            Set<BoxEvent> results = new LinkedHashSet<>();
            String streamPosition = position;
            while (true) {
                EventLog eventLog = EventLog.getEnterpriseEvents(boxConnection, request.position(streamPosition));
                for (BoxEvent event : eventLog) {
                    results.add(event);
                }
                String nextStreamPosition = eventLog.getNextStreamPosition();
                // the SDK returns the text "null" for a response whose next position is a JSON null
                if (eventLog.getSize() == 0 || nextStreamPosition == null || "null".equals(nextStreamPosition)
                        || nextStreamPosition.equals(streamPosition)) {
                    break;
                }
                streamPosition = nextStreamPosition;
            }

            return new ArrayList<>(results);
        } catch (BoxAPIException e) {
            throw new RuntimeCamelException(
                    buildBoxApiErrorMessage(e), e);
        }
    }
}
