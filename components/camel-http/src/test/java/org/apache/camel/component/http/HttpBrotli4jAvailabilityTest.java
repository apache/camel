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

import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicReference;

import com.aayushatharva.brotli4j.Brotli4jLoader;
import org.apache.hc.client5.http.entity.InputStreamFactory;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class HttpBrotli4jAvailabilityTest extends BaseHttpTest {

    private HttpServer localServer;
    private final AtomicReference<String> capturedAcceptEncoding = new AtomicReference<>();

    @Override
    public void setupResources() throws Exception {
        HttpRequestHandler handler = (request, response, context) -> {
            capturedAcceptEncoding.set(
                    request.getFirstHeader("Accept-Encoding") != null
                            ? request.getFirstHeader("Accept-Encoding").getValue()
                            : null);
            response.setCode(HttpStatus.SC_OK);
            response.setEntity(new StringEntity(getExpectedContent()));
        };

        localServer = ServerBootstrap.bootstrap()
                .setCanonicalHostName("localhost")
                .register("/", handler)
                .create();
        localServer.start();
    }

    @Override
    public void cleanupResources() throws Exception {
        if (localServer != null) {
            localServer.stop();
        }
    }

    @Test
    void brotli4jAvailabilityShouldMatchLoaderState() {
        // The brotli4j API jar is on the test classpath (test-scope dependency).
        // Whether the native library loads depends on the test machine (e.g. Homebrew
        // brotli on macOS). What matters is that our reflective check returns the same
        // result as calling Brotli4jLoader.isAvailable() directly.
        assertThat(HttpComponent.isBrotli4jAvailable())
                .as("isBrotli4jAvailable() must agree with Brotli4jLoader.isAvailable()")
                .isEqualTo(Brotli4jLoader.isAvailable());
    }

    @Test
    void brotli4jLoaderClassShouldBeOnClasspath() {
        // Verify the API jar is actually on the test classpath, so the test above
        // is validating native-lib detection, not just a missing class.
        try {
            Class.forName("com.aayushatharva.brotli4j.Brotli4jLoader");
        } catch (ClassNotFoundException e) {
            throw new AssertionError("brotli4j API jar should be on the test classpath as a test-scope dependency", e);
        }
    }

    @Test
    void buildDecodersWithoutBrotliShouldExcludeBr() {
        LinkedHashMap<String, InputStreamFactory> decoders = HttpComponent.buildDecodersWithoutBrotli();
        assertThat(decoders).doesNotContainKey("br");
        assertThat(decoders).containsKey("gzip");
        assertThat(decoders).containsKey("deflate");
    }

    @Test
    void acceptEncodingShouldReflectBrotli4jAvailability() {
        template.request("http://localhost:" + localServer.getLocalPort() + "/",
                exchange -> exchange.getIn().setBody("test"));

        String acceptEncoding = capturedAcceptEncoding.get();
        assertThat(acceptEncoding).as("Accept-Encoding header").isNotNull();
        assertThat(acceptEncoding).contains("gzip", "deflate");

        if (Brotli4jLoader.isAvailable()) {
            assertThat(acceptEncoding).as("brotli4j is available, Accept-Encoding should include br").contains("br");
        } else {
            assertThat(acceptEncoding).as("brotli4j is unavailable, Accept-Encoding must not include br")
                    .doesNotContain("br");
        }
    }
}
