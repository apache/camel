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
import java.util.List;

import com.box.sdk.BoxAPIConnection;
import com.box.sdk.BoxAPIException;
import com.box.sdk.BoxEvent;
import com.box.sdk.EventLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Box Event Logs Manager
 * 
 * <p>
 * Provides operations to read Box enterprise (admin) event logs.
 * 
 * 
 *
 */
public class BoxEventLogsManager {

    private static final Logger LOG = LoggerFactory.getLogger(BoxEventLogsManager.class);

    /**
     * Box connection to authenticated user account.
     */
    private BoxAPIConnection boxConnection;

    /**
     * Create event logs manager to manage the event logs of Box connection's
     * authenticated user.
     * 
     * @param boxConnection
     *            - Box connection to authenticated user account.
     */
    public BoxEventLogsManager(BoxAPIConnection boxConnection) {
        this.boxConnection = boxConnection;
    }

    /**
     * Create an event stream with optional starting initial position and add
     * listener that will be notified when an event is received.
     * 
     * @param position
     *            - the starting position of the event stream. May be
     *            <code>null</code> in which case all events within bounds
     *            returned.
     * @param after
     *            - the lower bound on the timestamp of the events returned.
     * @param before
     *            - the upper bound on the timestamp of the events returned.
     * @param types
     *            - an optional list of event types to filter by.
     * 
     * @return A list of all the events that met the given criteria.
     */
    public List<BoxEvent> getEnterpriseEvents(String position, Date after, Date before, BoxEvent.Type... types) {
        try {
            LOG.debug("Getting all enterprise events occuring between "
                    + (after == null ? after : DateFormat.getDateTimeInstance().format(after)) + " and "
                    + (before == null ? before : DateFormat.getDateTimeInstance().format(before))
                    + (position == null ? position : (" starting at " + position)));

            if (after == null) {
                throw new IllegalArgumentException("Parameter 'after' can not be null");
            }
            if (before == null) {
                throw new IllegalArgumentException("Parameter 'before' can not be null");
            }

            if (types == null) {
                types = new BoxEvent.Type[0];
            }

            EventLog eventLog = EventLog.getEnterpriseEvents(boxConnection, position, after, before, types);

            List<BoxEvent> results = new ArrayList<>();
            for (BoxEvent event : eventLog) {
                results.add(event);
            }

            return results;
        } catch (BoxAPIException e) {
            throw new RuntimeException(
                    String.format("Box API returned the error code %d\n\n%s", e.getResponseCode(), e.getResponse()), e);
        }
    }

}
