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
package org.apache.camel.component.couchdb;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CouchDbEndpointTest {

    @Test
    void assertSingleton() throws Exception {
        try (CouchDbEndpoint endpoint
                = new CouchDbEndpoint("couchdb:http://localhost/db", "http://localhost/db", new CouchDbComponent())) {
            assertTrue(endpoint.isSingleton());
        }
    }

    @Test
    void testDbRequired() {
        final CouchDbComponent component = new CouchDbComponent();

        assertThrows(IllegalArgumentException.class, () -> {
            new CouchDbEndpoint("couchdb:http://localhost:80", "http://localhost:80", component);
        });
    }

    @Test
    void testDefaultPortIsSet() throws Exception {
        try (CouchDbEndpoint endpoint
                = new CouchDbEndpoint("couchdb:http://localhost/db", "http://localhost/db", new CouchDbComponent())) {
            assertEquals(CouchDbEndpoint.DEFAULT_PORT, endpoint.getPort());
        }
    }

    @Test
    void testHostnameRequired() {
        final CouchDbComponent component = new CouchDbComponent();

        assertThrows(IllegalArgumentException.class, () -> {
            new CouchDbEndpoint("couchdb:http://:80/db", "http://:80/db", component);
        });
    }

    @Test
    void testSchemeRequired() {
        final CouchDbComponent component = new CouchDbComponent();

        assertThrows(IllegalArgumentException.class, () -> {
            new CouchDbEndpoint("couchdb:localhost:80/db", "localhost:80/db", component);
        });
    }
}
