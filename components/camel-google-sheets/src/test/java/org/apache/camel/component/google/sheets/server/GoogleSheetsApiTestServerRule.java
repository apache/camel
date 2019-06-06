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
package org.apache.camel.component.google.sheets.server;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import com.consol.citrus.dsl.endpoint.CitrusEndpoints;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.util.SocketUtils;

import static org.apache.camel.component.google.sheets.server.GoogleSheetsApiTestServerAssert.assertThatGoogleApi;

public class GoogleSheetsApiTestServerRule implements TestRule {

    public static final String SERVER_KEYSTORE = "googleapis.jks";
    public static final String SERVER_KEYSTORE_PASSWORD = "secret";

    private GoogleSheetsApiTestServer googleApiTestServer;
    private int serverPort = SocketUtils.findAvailableTcpPort();

    public GoogleSheetsApiTestServerRule(String optionFile) {
        try {
            Map<String, Object> testOptions = getTestOptions(optionFile);

            googleApiTestServer = new GoogleSheetsApiTestServer.Builder(CitrusEndpoints.http().server().port(serverPort).timeout(15000).defaultStatus(HttpStatus.REQUEST_TIMEOUT)
                .autoStart(true)).keyStorePath(new ClassPathResource(SERVER_KEYSTORE).getFile().toPath()).keyStorePassword(SERVER_KEYSTORE_PASSWORD).securePort(serverPort)
                    .clientId(testOptions.get("clientId").toString()).clientSecret(testOptions.get("clientSecret").toString())
                    .accessToken(testOptions.get("accessToken").toString()).refreshToken(testOptions.get("refreshToken").toString()).build();

            assertThatGoogleApi(googleApiTestServer).isRunning();
        } catch (Exception e) {
            throw new IllegalStateException("Error while reading server keystore file", e);
        }
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new GoogleSheetsApiTestServerStatement(base);
    }

    /**
     * Read component configuration from TEST_OPTIONS_PROPERTIES.
     * 
     * @return Map of component options.
     */
    private Map<String, Object> getTestOptions(String optionFile) throws IOException {
        final Properties properties = new Properties();
        properties.load(getClass().getResourceAsStream(optionFile));

        Map<String, Object> options = new HashMap<>();
        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            options.put(entry.getKey().toString(), entry.getValue());
        }

        return options;
    }

    /**
     * Rule statement initializes and resets test server after each method.
     */
    private class GoogleSheetsApiTestServerStatement extends Statement {
        private final Statement base;

        GoogleSheetsApiTestServerStatement(Statement base) {
            this.base = base;
        }

        @Override
        public void evaluate() throws Throwable {
            googleApiTestServer.init();
            try {
                base.evaluate();
            } finally {
                googleApiTestServer.reset();
            }
        }
    }

    public GoogleSheetsApiTestServer getGoogleApiTestServer() {
        return googleApiTestServer;
    }

    public int getServerPort() {
        return serverPort;
    }
}
