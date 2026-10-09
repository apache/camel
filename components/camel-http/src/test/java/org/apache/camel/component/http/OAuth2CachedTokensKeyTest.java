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
package org.apache.camel.component.http;

import java.net.URI;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class OAuth2CachedTokensKeyTest {

    @Test
    void hostOnly() {
        OAuth2CachedTokensKey key = OAuth2CachedTokensKey.HOST_ONLY;
        assertEquals("https://example.com:443", key.resolveKey(URI.create("https://example.com/event/1?eventId=1")));
        assertEquals(key.resolveKey(URI.create("https://example.com/event/1")),
                key.resolveKey(URI.create("https://EXAMPLE.com:443/event/2")));
        assertEquals("http://example.com:80", key.resolveKey(URI.create("HTTP://example.com")));
        assertNotEquals(key.resolveKey(URI.create("http://example.com/a")),
                key.resolveKey(URI.create("http://example.com:8080/a")));
        assertNotEquals(key.resolveKey(URI.create("http://example.com/a")),
                key.resolveKey(URI.create("https://example.com/a")));
    }

    @Test
    void hostAndPath() {
        OAuth2CachedTokensKey key = OAuth2CachedTokensKey.HOST_AND_PATH;
        assertEquals("https://example.com:443/event/1", key.resolveKey(URI.create("https://example.com/event/1?eventId=1")));
        assertEquals(key.resolveKey(URI.create("https://example.com/event?eventId=1")),
                key.resolveKey(URI.create("https://example.com/event?eventId=2")));
        assertNotEquals(key.resolveKey(URI.create("https://example.com/event/1")),
                key.resolveKey(URI.create("https://example.com/event/2")));
    }

    @Test
    void fullUri() {
        OAuth2CachedTokensKey key = OAuth2CachedTokensKey.FULL_URI;
        assertEquals("https://example.com/event?eventId=1", key.resolveKey(URI.create("https://example.com/event?eventId=1")));
        assertNotEquals(key.resolveKey(URI.create("https://example.com/event?eventId=1")),
                key.resolveKey(URI.create("https://example.com/event?eventId=2")));
    }
}
