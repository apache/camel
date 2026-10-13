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
package org.apache.camel.component.box;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import com.box.sdk.BoxAPIConnection;
import com.box.sdk.BoxAPIRequest;
import com.box.sdk.BoxAPIResponse;
import com.box.sdk.BoxEvent;
import com.box.sdk.BoxJSONResponse;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import org.apache.camel.component.box.api.BoxEventLogsManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The Box connection answers the enterprise events request from memory, as the service does: at most {@link #MAX_CHUNK}
 * events from the requested stream position, and the position of the next chunk.
 */
class BoxEventLogsManagerPagingTest {

    private static final int MAX_CHUNK = 500;
    private static final int MAX_REQUESTS = 20;
    private static final Date AFTER = new Date(0);
    private static final Date BEFORE = new Date(86_400_000L);

    /** The stream positions of the requests, in order ({@code null}: a request without position). */
    private final List<String> positions = new ArrayList<>();
    private int events;
    private boolean samePositionAgain;
    private BoxEventLogsManager manager;

    @BeforeEach
    void setUp() {
        BoxAPIConnection connection = new BoxAPIConnection("test-token");
        connection.setRequestInterceptor(this::respond);
        manager = new BoxEventLogsManager(connection);
    }

    private BoxAPIResponse respond(BoxAPIRequest request) {
        String position = null;
        int limit = MAX_CHUNK;
        for (String parameter : request.getUrl().getQuery().split("&")) {
            if (parameter.startsWith("stream_position=")) {
                position = parameter.substring("stream_position=".length());
            } else if (parameter.startsWith("limit=")) {
                limit = Math.min(MAX_CHUNK, Integer.parseInt(parameter.substring("limit=".length())));
            }
        }
        positions.add(position);
        if (positions.size() > MAX_REQUESTS) {
            throw new IllegalStateException("The manager sent more than " + MAX_REQUESTS + " requests");
        }
        int from = position == null ? 0 : Integer.parseInt(position);
        int to = Math.min(from + limit, events);
        JsonArray entries = new JsonArray();
        for (int i = from; i < to; i++) {
            entries.add(new JsonObject().add("type", "event").add("event_id", "event-" + i)
                    .add("event_type", "LOGIN").add("created_at", "2026-01-01T00:00:00-00:00"));
        }
        JsonObject body = new JsonObject()
                .add("chunk_size", entries.size())
                .add("next_stream_position", samePositionAgain ? String.valueOf(from) : String.valueOf(to))
                .add("entries", entries);
        return new BoxJSONResponse(200, request.getMethod(), request.getUrl().toString(), Map.of(), body);
    }

    private static List<String> ids(List<BoxEvent> result) {
        List<String> answer = new ArrayList<>();
        for (BoxEvent event : result) {
            answer.add(event.getID());
        }
        return answer;
    }

    private static List<String> expectedIds(int from, int to) {
        List<String> answer = new ArrayList<>();
        for (int i = from; i < to; i++) {
            answer.add("event-" + i);
        }
        return answer;
    }

    @Test
    void returnsTheEventsOfEveryChunk() {
        events = 2 * MAX_CHUNK + 200;

        List<BoxEvent> result = manager.getEnterpriseEvents(null, AFTER, BEFORE);

        assertEquals(events, result.size(), "all the events within bounds, not only the first chunk");
        assertEquals(expectedIds(0, events), ids(result), "each event once and in the order of the service");
        List<String> expectedPositions = new ArrayList<>();
        expectedPositions.add(null);
        expectedPositions.addAll(List.of("500", "1000", "1200"));
        assertEquals(expectedPositions, positions, "each chunk is requested at the next position of the chunk before");
    }

    @Test
    void startsAtThePositionOfTheCallerAndReadsToTheEnd() {
        events = 2 * MAX_CHUNK + 200;

        List<BoxEvent> result = manager.getEnterpriseEvents("100", AFTER, BEFORE);

        assertEquals(expectedIds(100, events), ids(result));
        assertEquals(List.of("100", "600", "1100", "1200"), positions);
    }

    @Test
    void stopsWhenTheServiceDoesNotMoveThePosition() {
        // a service that answers with the position it was asked for must not be asked for ever
        events = 2;
        samePositionAgain = true;

        List<BoxEvent> result = manager.getEnterpriseEvents(null, AFTER, BEFORE);

        assertEquals(expectedIds(0, 2), ids(result), "the two events once, although both responses contain them");
        assertEquals(2, positions.size());
    }
}
