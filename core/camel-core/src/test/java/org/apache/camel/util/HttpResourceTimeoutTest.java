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
package org.apache.camel.util;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.engine.DefaultResourceResolvers;
import org.apache.camel.spi.Resource;
import org.apache.camel.support.ResourceHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An {@code http:} resource must not be able to stall the caller forever.
 * <p/>
 * The server here accepts the connection and then never answers, which is the only shape that reproduces the problem: a
 * refused connection fails fast on its own, so nothing is proven by pointing at a closed port.
 * <p/>
 * The timeouts below are deliberately {@code SEPARATE_THREAD}: the default mode only measures elapsed time once the
 * test method returns, so a regression that restores the indefinite wait would hang the build instead of failing it.
 */
public class HttpResourceTimeoutTest {

    private ServerSocket server;
    private Thread acceptor;
    private CountDownLatch accepted;
    private volatile boolean stopped;

    @BeforeEach
    void startMuteServer() throws Exception {
        server = new ServerSocket(0);
        accepted = new CountDownLatch(1);
        acceptor = new Thread(() -> {
            while (!stopped) {
                try (Socket socket = server.accept()) {
                    accepted.countDown();
                    // hold the connection open and write nothing at all, so the client is left waiting on a read
                    while (!stopped && !socket.isClosed()) {
                        Thread.onSpinWait();
                    }
                } catch (IOException e) {
                    return;
                }
            }
        }, "mute-http-server");
        acceptor.setDaemon(true);
        acceptor.start();
        stopped = false;
    }

    @AfterEach
    void stopMuteServer() throws Exception {
        stopped = true;
        server.close();
        acceptor.join(TimeUnit.SECONDS.toMillis(5));
    }

    private CamelContext contextWithReadTimeout(String millis) {
        CamelContext context = new DefaultCamelContext();
        Properties properties = new Properties();
        properties.setProperty(DefaultResourceResolvers.HTTP_READ_TIMEOUT_PROPERTY, millis);
        context.getPropertiesComponent().setInitialProperties(properties);
        context.start();
        return context;
    }

    private String muteUrl() {
        return "http://localhost:" + server.getLocalPort() + "/policy.rego";
    }

    @Test
    @Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    public void getInputStreamGivesUpOnAServerThatNeverAnswers() throws Exception {
        CamelContext context = contextWithReadTimeout("500");
        try {
            Resource resource = ResourceHelper.resolveResource(context, muteUrl());

            assertThrows(SocketTimeoutException.class, resource::getInputStream);
            assertTrue(accepted.await(5, TimeUnit.SECONDS),
                    "the server should have accepted the connection - a refused connect proves nothing");
        } finally {
            context.stop();
        }
    }

    @Test
    @Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    public void existsGivesUpOnAServerThatNeverAnswers() throws Exception {
        CamelContext context = contextWithReadTimeout("500");
        try {
            Resource resource = ResourceHelper.resolveResource(context, muteUrl());

            // exists() wraps the IOException rather than declaring it, so the timeout surfaces as the cause
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, resource::exists);
            assertEquals(SocketTimeoutException.class, e.getCause().getClass());
        } finally {
            context.stop();
        }
    }

    @Test
    @Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    public void httpsResourcesAreBoundedToo() throws Exception {
        CamelContext context = contextWithReadTimeout("500");
        try {
            // the https resolver hands back the same HttpResource, so it must inherit the same bound; the handshake
            // against a mute plain-text server is what fails here, and it must fail rather than hang
            Resource resource
                    = ResourceHelper.resolveResource(context, "https://localhost:" + server.getLocalPort() + "/x");

            assertThrows(IOException.class, resource::getInputStream);
        } finally {
            context.stop();
        }
    }

    @Test
    @Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    public void aResourceThatIsNotThereStillReportsAbsent() throws Exception {
        // guards against the timeouts turning an ordinary 404 into a failure
        CamelContext context = contextWithReadTimeout("5000");
        try (ServerSocket notFound = new ServerSocket(0)) {
            Thread responder = new Thread(() -> {
                try (Socket socket = notFound.accept()) {
                    socket.getOutputStream()
                            .write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                                    .getBytes("US-ASCII"));
                    socket.getOutputStream().flush();
                } catch (IOException e) {
                    // the assertion below is what reports the failure
                }
            }, "not-found-server");
            responder.setDaemon(true);
            responder.start();

            Resource resource
                    = ResourceHelper.resolveResource(context, "http://localhost:" + notFound.getLocalPort() + "/x");

            assertFalse(resource.exists());
        } finally {
            context.stop();
        }
    }
}
