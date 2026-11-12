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
package org.apache.camel.component.netty.http.handlers;

import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;
import org.apache.camel.component.netty.http.HttpPrincipal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * RFC 7617 encodes the Basic credentials with UTF-8, so what the server reads back must not depend on the JVM's
 * default charset.
 */
class HttpServerChannelHandlerBasicAuthTest {

    @Test
    void shouldDecodeCredentialsAsUtf8() {
        // "user:pässword" base64-encoded from its UTF-8 bytes, the way a client sends it
        HttpPrincipal principal = extract("Basic dXNlcjpww6Rzc3dvcmQ=");

        assertNotNull(principal);
        assertEquals("user", principal.getName());
        assertEquals("pässword", principal.getPassword());
    }

    @Test
    void shouldDecodeAsciiCredentials() {
        // "scott:tiger"
        HttpPrincipal principal = extract("Basic c2NvdHQ6dGlnZXI=");

        assertNotNull(principal);
        assertEquals("scott", principal.getName());
        assertEquals("tiger", principal.getPassword());
    }

    @Test
    void shouldIgnoreAnotherScheme() {
        assertNull(extract("Bearer c2NvdHQ6dGlnZXI="));
    }

    private static HttpPrincipal extract(String authorization) {
        HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/");
        request.headers().set("Authorization", authorization);
        return HttpServerChannelHandler.extractBasicAuthSubject(request);
    }

}
