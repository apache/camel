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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VertxWebsocketHelperTest {

    @Test
    void webSocketHostExactPathMatches() {
        String hostPath = "/foo/bar/cheese/wine";
        String targetPath = "/foo/bar/cheese/wine";
        assertTrue(VertxWebsocketHelper.webSocketHostPathMatches(hostPath, targetPath));
    }

    @Test
    void webSocketHostExactPathNotMatches() {
        String hostPath = "/foo/bar/cheese/wine";
        String targetPath = "/foo/bar/wine/cheese";
        assertFalse(VertxWebsocketHelper.webSocketHostPathMatches(hostPath, targetPath));
    }

    @Test
    void webSocketHostExactPathNotEnoughElementsNotMatches() {
        String hostPath = "/foo/bar/cheese/wine";
        String targetPath = "/foo/bar";
        assertFalse(VertxWebsocketHelper.webSocketHostPathMatches(hostPath, targetPath));
    }

    @Test
    void webSocketHostExactPathWithParamsNotMatches() {
        String hostPath = "/foo/{bar}/cheese/{wine}";
        String targetPath = "/bad/bar/path/wine";
        assertFalse(VertxWebsocketHelper.webSocketHostPathMatches(hostPath, targetPath));
    }

    @Test
    void webSocketHostWildcardPathMatches() {
        String hostPath = "/foo/bar/cheese/wine*";
        String targetPath = "/foo/bar/cheese/wine/beer/additional/path";
        assertTrue(VertxWebsocketHelper.webSocketHostPathMatches(hostPath, targetPath));
    }

    @Test
    void webSocketHostWildcardPathNotMatches() {
        String hostPath = "/foo/bar/cheese/wine*";
        String targetPath = "/foo/bar/cheese/win";
        assertFalse(VertxWebsocketHelper.webSocketHostPathMatches(hostPath, targetPath));
    }

    @Test
    void webSocketHostWildcardPathWithParamsNotMatches() {
        String hostPath = "/foo/{bar}/cheese/{wine}*";
        String targetPath = "/foo/bar/invalid/wine/beer/additional/path";
        assertFalse(VertxWebsocketHelper.webSocketHostPathMatches(hostPath, targetPath));
    }

    @Test
    void webSocketHostWithTrailingSlashPathMatches() {
        String hostPath = "/foo/bar/cheese/wine";
        String targetPath = "/foo/bar/cheese/wine/";
        assertTrue(VertxWebsocketHelper.webSocketHostPathMatches(hostPath, targetPath));
    }

    @Test
    void webSocketHostWithTrailingMultipleSlashPathMatches() {
        String hostPath = "/foo/bar/cheese/wine";
        String targetPath = "/foo/bar/cheese/wine//";
        assertTrue(VertxWebsocketHelper.webSocketHostPathMatches(hostPath, targetPath));
    }

    @Test
    void webSocketHostDefaultPathMatches() {
        String hostPath = "/";
        String targetPath = "/";
        assertTrue(VertxWebsocketHelper.webSocketHostPathMatches(hostPath, targetPath));
    }

    @Test
    void webSocketHostEmptyPathNotMatches() {
        String hostPath = "";
        String targetPath = "";
        assertFalse(VertxWebsocketHelper.webSocketHostPathMatches(hostPath, targetPath));
    }

    @Test
    void webSocketHostNullPathNotMatches() {
        String hostPath = null;
        String targetPath = null;
        assertFalse(VertxWebsocketHelper.webSocketHostPathMatches(hostPath, targetPath));
    }
}
