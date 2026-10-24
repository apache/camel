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
package org.apache.camel.component.netty.http;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A consumer at the root context with a {@code securityConstraint} rejects with HTTP 400 a request target that starts
 * with {@code //}. Such a target is parsed as a host followed by a path, so the empty segment is not in the decoded
 * path. The requests are written on a plain socket, so the request target reaches the consumer as written.
 */
class NettyHttpBasicAuthConstraintRootContextTest extends BaseNettyTestSupport {

    @Override
    public void doPreSetup() {
        System.setProperty("java.security.auth.login.config", "src/test/resources/myjaas.config");
    }

    @Override
    public void doPostTearDown() {
        System.clearProperty("java.security.auth.login.config");
    }

    @BindToRegistry("mySecurityConfig")
    public NettyHttpSecurityConfiguration loadSecConf() {
        NettyHttpSecurityConfiguration security = new NettyHttpSecurityConfiguration();
        security.setRealm("karaf");
        SecurityAuthenticator auth = new JAASSecurityAuthenticator();
        auth.setName("karaf");
        security.setSecurityAuthenticator(auth);

        SecurityConstraintMapping matcher = new SecurityConstraintMapping();
        matcher.addInclusion("/admin/*");
        security.setSecurityConstraint(matcher);

        return security;
    }

    @Test
    void restrictedPathIsChallenged() throws IOException {
        assertEquals(401, statusOf("/admin/x"));
    }

    @Test
    void otherPathIsPublic() throws IOException {
        assertEquals(200, statusOf("/x"));
    }

    @Test
    void leadingEmptySegmentIsRejected() throws IOException {
        assertEquals(400, statusOf("//admin/x"));
    }

    @Test
    void leadingEmptySegmentInAbsoluteFormIsRejected() throws IOException {
        assertEquals(400, statusOf("http://localhost:" + getPort() + "//admin/x"));
    }

    private int statusOf(String requestTarget) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", getPort()), 5000);
            socket.setSoTimeout(5000);
            String request = "GET " + requestTarget + " HTTP/1.1\r\nHost: localhost:" + getPort()
                             + "\r\nConnection: close\r\n\r\n";
            socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();

            // the status line is HTTP/1.1 <code> <reason>
            InputStream in = socket.getInputStream();
            StringBuilder statusLine = new StringBuilder();
            int b = in.read();
            while (b != -1 && b != '\r') {
                statusLine.append((char) b);
                b = in.read();
            }
            return Integer.parseInt(statusLine.toString().split(" ")[1]);
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("netty-http:http://0.0.0.0:{{port}}?matchOnUriPrefix=true&securityConfiguration=#mySecurityConfig")
                        .transform().constant("Bye World");
            }
        };
    }
}
