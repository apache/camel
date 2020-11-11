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
package org.apache.camel.component.vertx.websocket;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class VertxWebsocketHelperTest {

    @Test
    public void extractHostNameTest() {
        assertEquals("test.host.com", VertxWebsocketHelper.extractHostName("test.host.com:8080/path"));
        assertEquals("0.0.0.0", VertxWebsocketHelper.extractHostName("/path"));
    }

    @Test
    public void extractPortTest() {
        assertEquals(8888, VertxWebsocketHelper.extractPortNumber("test.host.com:8888/path"));
        assertEquals(0, VertxWebsocketHelper.extractPortNumber("0.0.0.0/path"));
    }

    @Test
    public void extractPortInvalidTest() {
        assertThrows(IllegalArgumentException.class, () -> {
            VertxWebsocketHelper.extractPortNumber("test.host.com:/path");
        });

        assertThrows(IllegalArgumentException.class, () -> {
            VertxWebsocketHelper.extractPortNumber("test.host.com:port/path");
        });
    }

    @Test
    public void extractPathTest() {
        assertEquals("/web/socket/path", VertxWebsocketHelper.extractPath("test.host.com:8080/web/socket/path"));
        assertEquals("/", VertxWebsocketHelper.extractPath(""));
    }
}
