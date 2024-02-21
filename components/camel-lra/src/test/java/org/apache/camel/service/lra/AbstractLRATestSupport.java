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
package org.apache.camel.service.lra;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.infra.microprofile.lra.services.MicroprofileLRAService;
import org.apache.camel.test.infra.microprofile.lra.services.MicroprofileLRAServiceFactory;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Base class for LRA based tests.
 */
public abstract class AbstractLRATestSupport extends CamelTestSupport {

    @RegisterExtension
    static MicroprofileLRAService service = MicroprofileLRAServiceFactory.createService();

    private Integer serverPort;

    private int activeLRAs;

    @BeforeEach
    public void getActiveLRAs() throws IOException, InterruptedException {
        this.activeLRAs = getNumberOfActiveLRAs();
    }

    @AfterEach
    public void checkActiveLRAs() throws IOException, InterruptedException {
        await().atMost(2, SECONDS).until(() -> getNumberOfActiveLRAs(), equalTo(activeLRAs));
        assertEquals(activeLRAs, getNumberOfActiveLRAs(), "Some LRA have been left pending");
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        context.addService(createLRASagaService());

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                restConfiguration()
                        .port(getServerPort());
            }
        });

        return context;
    }

    protected LRASagaService createLRASagaService() {
        LRASagaService sagaService = new LRASagaService();
        sagaService.setCoordinatorUrl(getCoordinatorURL());
        sagaService.setLocalParticipantUrl(
                String.format("http://%s:%d", service.callbackHost(), getServerPort()));
        return sagaService;
    }

    protected int getNumberOfActiveLRAs() throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getCoordinatorURL() + "/lra-coordinator"))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        ObjectMapper mapper = new ObjectMapper();
        JsonNode lras = mapper.readTree(response.body());
        return lras.size();
    }

    private String getCoordinatorURL() {
        return service.getServiceAddress();
    }

    protected int getServerPort() {
        if (serverPort == null) {
            serverPort = AvailablePortFinder.getNextAvailable();
        }
        return serverPort;
    }
}
