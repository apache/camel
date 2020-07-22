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
import java.io.InputStream;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.Response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Base class for LRA based tests.
 */
public abstract class AbstractLRATestSupport extends CamelTestSupport {

    private Integer serverPort;

    private int activeLRAs;

    @BeforeEach
    public void getActiveLRAs() throws IOException {
        this.activeLRAs = getNumberOfActiveLRAs();
    }

    @AfterEach
    public void checkActiveLRAs() throws IOException {
        await().atMost(2, SECONDS).until(() -> getNumberOfActiveLRAs(), equalTo(activeLRAs));
        assertEquals(activeLRAs, getNumberOfActiveLRAs(), "Some LRA have been left pending");
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        context.addService(createLRASagaService());

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                restConfiguration()
                        .port(getServerPort());
            }
        });

        return context;
    }

    protected LRASagaService createLRASagaService() {
        LRASagaService sagaService = new LRASagaService();
        sagaService.setCoordinatorUrl(getCoordinatorURL());
        sagaService.setLocalParticipantUrl("http://localhost:" + getServerPort());
        return sagaService;
    }

    protected int getNumberOfActiveLRAs() throws IOException {
        Client client = ClientBuilder.newClient();

        Response response = client.target(getCoordinatorURL() + "/lra-coordinator")
                .request()
                .accept("application/json")
                .get();

        ObjectMapper mapper = new ObjectMapper();
        JsonNode lras = mapper.readTree(InputStream.class.cast(response.getEntity()));
        return lras.size();
    }

    private String getCoordinatorURL() {
        String url = System.getenv("LRA_COORDINATOR_URL");
        if (url == null) {
            throw new IllegalStateException("Cannot run test: environment variable LRA_COORDINATOR_URL is missing");
        }
        return url;
    }

    protected int getServerPort() {
        if (serverPort == null) {
            serverPort = AvailablePortFinder.getNextAvailable();
        }
        return serverPort;
    }
}
