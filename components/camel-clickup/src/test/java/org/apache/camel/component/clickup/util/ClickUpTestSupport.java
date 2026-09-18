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
package org.apache.camel.component.clickup.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.CamelContext;
import org.apache.camel.component.clickup.ClickUpComponent;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * A support test class for ClickUp tests.
 */
public class ClickUpTestSupport extends CamelTestSupport {

    protected static final Long WORKSPACE_ID = 12345L;
    protected static final String AUTHORIZATION_TOKEN = "mock-authorization-token";
    protected static final String WEBHOOK_SECRET = "mock-webhook-secret";

    @RegisterExtension
    protected static AvailablePortFinder.Port port = AvailablePortFinder.find();

    private ClickUpMockRoutes mockRoutes;

    /**
     * Retrieves a response from a JSON file on classpath.
     *
     * @param  fileName the filename in the classpath
     * @param  clazz    the target class
     * @param  <T>      the type of the returned object
     * @return          the object representation of the JSON file
     */
    public static <T> T getJSONResource(String fileName, Class<T> clazz) {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream stream = ClickUpTestSupport.class.getClassLoader().getResourceAsStream(fileName)) {
            return mapper.readValue(stream, clazz);
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to load file " + fileName, e);
        }
    }

    /**
     * Waits until the ClickUp mock API health endpoint responds with HTTP 200.
     */
    protected void waitForClickUpMockAPI() {
        Awaitility.await()
                .atMost(5, TimeUnit.SECONDS)
                .until(() -> {
                    HttpClient client = HttpClient.newBuilder().build();
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create("http://localhost:" + port + "/clickup-api-mock/health")).GET().build();
                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                    return response.statusCode() == 200;
                });
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        final CamelContext context = super.createCamelContext();
        final ClickUpComponent component = new ClickUpComponent();

        context.addComponent("clickup", component);

        return context;
    }

    protected ClickUpMockRoutes getMockRoutes() {
        if (mockRoutes == null) {
            mockRoutes = createMockRoutes();
        }
        return mockRoutes;
    }

    protected ClickUpMockRoutes createMockRoutes() {
        throw new UnsupportedOperationException();
    }

}
