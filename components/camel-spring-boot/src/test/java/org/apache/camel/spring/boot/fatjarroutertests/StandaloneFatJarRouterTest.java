/**
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
package org.apache.camel.spring.boot.fatjarroutertests;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URL;
import java.util.concurrent.Callable;

import static java.util.concurrent.TimeUnit.MINUTES;

import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.util.SocketUtils;

import static com.jayway.awaitility.Awaitility.await;

public class StandaloneFatJarRouterTest extends Assert {

    @Test
    public void shouldStartCamelRoute() throws InterruptedException, IOException {
        // Given
        final int port = SocketUtils.findAvailableTcpPort();
        final URL httpEndpoint = new URL("http://localhost:" + port);
        new Thread() {
            @Override
            public void run() {
                TestFatJarRouter.main("--spring.main.sources=org.apache.camel.spring.boot.fatjarroutertests.TestFatJarRouter", "--http.port=" + port);
            }
        }.start();
        await().atMost(1, MINUTES).until(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                try {
                    httpEndpoint.openStream();
                } catch (ConnectException ex) {
                    return false;
                }
                return true;
            }
        });

        // When
        String response = IOUtils.toString(httpEndpoint);

        // Then
        assertEquals("stringBean", response);
    }

}

