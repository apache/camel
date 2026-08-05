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
package org.apache.camel.component.atmosphere.websocket;

import org.apache.camel.http.base.HttpHeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that the Exchange header constants follow the {@code Camel<Component><Feature>} naming convention, and
 * therefore sit inside the {@code Camel} namespace that the inherited {@link HttpHeaderFilterStrategy} handles.
 */
class WebsocketConstantsTest {

    private static final String[] HEADERS = {
            WebsocketConstants.CONNECTION_KEY,
            WebsocketConstants.CONNECTION_KEY_LIST,
            WebsocketConstants.SEND_TO_ALL,
            WebsocketConstants.EVENT_TYPE,
            WebsocketConstants.ERROR_TYPE
    };

    private final HeaderFilterStrategy strategy = new HttpHeaderFilterStrategy();

    @Test
    void testHeaderNamesFollowCamelNamingConvention() {
        assertEquals("CamelAtmosphereWebsocketConnectionKey", WebsocketConstants.CONNECTION_KEY);
        assertEquals("CamelAtmosphereWebsocketConnectionKeyList", WebsocketConstants.CONNECTION_KEY_LIST);
        assertEquals("CamelAtmosphereWebsocketSendToAll", WebsocketConstants.SEND_TO_ALL);
        assertEquals("CamelAtmosphereWebsocketEventType", WebsocketConstants.EVENT_TYPE);
        assertEquals("CamelAtmosphereWebsocketErrorType", WebsocketConstants.ERROR_TYPE);
    }

    @Test
    void testHeadersAreFilteredByInheritedHttpHeaderFilterStrategy() {
        // WebsocketEndpoint extends ServletEndpoint, so it inherits HttpHeaderFilterStrategy, which filters
        // the Camel namespace case-insensitively in both directions
        for (String header : HEADERS) {
            assertTrue(strategy.applyFilterToExternalHeaders(header, "aValue", null),
                    header + " should be filtered when mapping external headers in");
            assertTrue(strategy.applyFilterToCamelHeaders(header, "aValue", null),
                    header + " should be filtered when mapping Camel headers out");
        }
    }
}
