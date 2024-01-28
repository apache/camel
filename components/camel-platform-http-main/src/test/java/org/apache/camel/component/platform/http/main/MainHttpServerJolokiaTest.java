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

package org.apache.camel.component.platform.http.main;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.AvailablePortFinder;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MainHttpServerJolokiaTest {

    private CamelContext camelContext;

    private final int port = AvailablePortFinder.getNextAvailable();

    @Test
    public void jolokiaIsUp() throws IOException, InterruptedException, ParseException {
        MainHttpServer server = new MainHttpServer();

        camelContext = new DefaultCamelContext();
        server.setCamelContext(camelContext);

        server.setHost("0.0.0.0");
        server.setPort(port);
        server.setPath("/");

        server.setJolokiaEnabled(true);
        server.start();

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/q/jolokia"))
                .build();

        HttpResponse<String> response = HttpClient.newBuilder().build().send(httpRequest, HttpResponse.BodyHandlers.ofString());
        JSONParser parser = new JSONParser();
        JSONObject responseBody = (JSONObject) parser.parse(response.body());

        JSONObject value = (JSONObject) responseBody.get("value");
        String agentVersion = (String) value.get("agent");

        JSONObject request = (JSONObject) responseBody.get("request");
        String type = (String) request.get("type");

        assertEquals(200, response.statusCode());
        assertEquals("version", type);
        assertEquals("2.0.1", agentVersion);
    }

}
