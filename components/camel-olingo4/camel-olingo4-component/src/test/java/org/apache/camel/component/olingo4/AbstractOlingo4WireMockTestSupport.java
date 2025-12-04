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

package org.apache.camel.component.olingo4;

import static com.github.tomakehurst.wiremock.client.WireMock.recordSpec;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import java.io.IOException;
import java.util.Map;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.recording.RecordingStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractOlingo4WireMockTestSupport extends AbstractOlingo4TestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractOlingo4WireMockTestSupport.class);
    private static final String CAPTURED_HEADER_NAME = "WireMockScenario";
    protected static WireMockServer wireMockServer;
    protected static String serverUrlWithSessionId;

    @BeforeAll
    public static void startWireMockServer() {
        if (useMockedBackend()) {
            LOG.info("Starting WireMock server");
            wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
            wireMockServer.start();
            if (isRecordingEnabled()) {
                LOG.info("Starting WireMock recording");
                wireMockServer.startRecording(recordSpec()
                        .captureHeader(CAPTURED_HEADER_NAME)
                        .forTarget(ODATA_API_BASE_URL)
                        .allowNonProxied(false));
            }
        }
    }

    private static boolean isRecordingEnabled() {
        String recordEnabled = System.getProperty("wiremock.record", System.getenv("WIREMOCK_RECORD"));
        return Boolean.TRUE.toString().equals(recordEnabled);
    }

    @AfterAll
    public static void stopWireMockServer() {
        if (useMockedBackend()) {
            LOG.info("Stopping WireMock server");
            if (isRecordingEnabled()) {
                if (wireMockServer.getRecordingStatus().getStatus().equals(RecordingStatus.Recording)) {
                    wireMockServer.stopRecording();
                }
            }
            wireMockServer.stop();
            serverUrlWithSessionId = null;
        }
    }

    protected static boolean useMockedBackend() {
        return !Boolean.TRUE.toString().equals(System.getProperty("use.real.backend"));
    }

    @Override
    public String getResolvedTestServiceBaseUrl() throws IOException {
        if (useMockedBackend()) {
            // re-use the same session id per one WireMock server lifecycle
            if (serverUrlWithSessionId == null) {
                serverUrlWithSessionId = getRealServiceUrl(
                        "http://localhost:" + wireMockServer.port(),
                        Map.of(CAPTURED_HEADER_NAME, getClassIdentifier()));
            }
            return serverUrlWithSessionId;
        }
        return super.getResolvedTestServiceBaseUrl();
    }

    /**
     * Used for correct mapping of request to given test class by adding it to HTTP header.
     */
    public abstract String getClassIdentifier();

    @Override
    protected String modifyServiceUrl(String hostUri, String reqUri) {
        // for wiremock we have url in format http://localhost:<random-port> which redirects to the real service
        // but we want to use the session id but cannot append it the same as for real server because it would result to
        // http://localhost:<random-number>/TripPinRESTierService/(S(e2ikh24z4iexhwyj5mgw4v5p))/
        // but we need http://localhost:<random-number>/(S(e2ikh24z4iexhwyj5mgw4v5p))/
        return useMockedBackend()
                ? hostUri + reqUri.substring(reqUri.indexOf('/', reqUri.indexOf('/') + 1))
                : super.modifyServiceUrl(hostUri, reqUri);
    }
}
