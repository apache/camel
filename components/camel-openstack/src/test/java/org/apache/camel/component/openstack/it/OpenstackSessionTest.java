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
package org.apache.camel.component.openstack.it;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.openstack.common.OpenstackConstants;
import org.apache.camel.component.openstack.swift.SwiftConstants;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openstack4j.model.storage.object.SwiftObject;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.head;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The producer keeps using the token that openstack4j renewed when the first token expired, from any thread.
 */
public class OpenstackSessionTest extends OpenstackWiremockTestSupport {

    private static final String URI_FORMAT
            = "openstack-swift://%s?username=user&password=secret&project=project&operation=%s&subsystem="
              + SwiftConstants.SWIFT_SUBSYSTEM_OBJECTS;

    private static final String AUTH_URL = "/auth/tokens";
    private static final String OBJECT_URL = "/12345321ae80457abc3728fa1e6feb78/test-container/test-file";

    @BeforeAll
    public static void stubExpiredToken() throws Exception {
        String body;
        try (InputStream is = OpenstackSessionTest.class.getResourceAsStream("/mappings/keystone/authv3_project.json")) {
            JsonNode mapping = new ObjectMapper().readTree(is);
            body = mapping.get("response").get("body").asText();
        }

        // the first authentication gives a token that has expired, the next one a valid token
        server().stubFor(post(urlEqualTo(AUTH_URL)).atPriority(1)
                .inScenario("token").whenScenarioStateIs(STARTED).willSetStateTo("renewed")
                .willReturn(tokenResponse(body, "expired")));
        server().stubFor(post(urlEqualTo(AUTH_URL)).atPriority(1)
                .inScenario("token").whenScenarioStateIs("renewed")
                .willReturn(tokenResponse(body, "renewed")));
        server().stubFor(head(urlEqualTo(OBJECT_URL)).atPriority(1)
                .withHeader("X-Auth-Token", equalTo("expired"))
                .willReturn(aResponse().withStatus(401)));
    }

    private static ResponseDefinitionBuilder tokenResponse(String body, String token) {
        return aResponse().withStatus(201)
                .withHeader("Content-Type", "application/json; charset=UTF-8")
                .withHeader("X-Subject-Token", token)
                .withBody(body)
                .withTransformers("response-template");
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("seda:get?concurrentConsumers=2")
                        .to(String.format(URI_FORMAT, url(), OpenstackConstants.GET));
            }
        };
    }

    @Test
    void renewedTokenShouldBeKept() {
        Map<String, Object> headers = new HashMap<>();
        headers.put(SwiftConstants.CONTAINER_NAME, "test-container");
        headers.put(SwiftConstants.OBJECT_NAME, "test-file");

        for (int i = 0; i < 5; i++) {
            SwiftObject swiftObject = template.requestBodyAndHeaders("seda:get", null, headers, SwiftObject.class);
            assertEquals("test-file", swiftObject.getName());
        }

        // the authentication of the producer, and the one renewal of the expired token
        server().verify(2, postRequestedFor(urlEqualTo(AUTH_URL)));
    }

}
