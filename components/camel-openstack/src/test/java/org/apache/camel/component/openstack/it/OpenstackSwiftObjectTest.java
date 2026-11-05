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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.openstack.common.OpenstackConstants;
import org.apache.camel.component.openstack.swift.SwiftConstants;
import org.junit.jupiter.api.Test;
import org.openstack4j.model.storage.object.SwiftObject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OpenstackSwiftObjectTest extends OpenstackWiremockTestSupport {

    private static final String URI_FORMAT
            = "openstack-swift://%s?username=user&password=secret&project=project&operation=%s&subsystem="
              + SwiftConstants.SWIFT_SUBSYSTEM_OBJECTS;

    private static final String OBJECT_CONTAINER_NAME = "test-container";
    private static final String OBJECT_NAME = "test-file";
    // the stub only answers a PUT of this content; WireMock adds a --gzip suffix to the ETag of a gzip request
    private static final String OBJECT_ETAG = "d8b3a3e8f1b7c9a2e4f6a8b0c2d4e6f8";
    private static final String OBJECT_CONTENT = "Hello Swift";

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // the producers are created when the routes start, and called from the consumer threads
                from("seda:get?concurrentConsumers=5")
                        .to(String.format(URI_FORMAT, url(), OpenstackConstants.GET));

                from("file:target/openstack-swift-outbox?initialDelay=0&delay=10&delete=true")
                        .setHeader(SwiftConstants.CONTAINER_NAME, constant(OBJECT_CONTAINER_NAME))
                        .setHeader(SwiftConstants.OBJECT_NAME, header(Exchange.FILE_NAME))
                        .to(String.format(URI_FORMAT, url(), OpenstackConstants.CREATE))
                        .to("mock:stored");
            }
        };
    }

    @Test
    void getShouldSucceed() {
        Map<String, Object> headers = new HashMap<>();
        headers.put(SwiftConstants.CONTAINER_NAME, OBJECT_CONTAINER_NAME);
        headers.put(SwiftConstants.OBJECT_NAME, OBJECT_NAME);

        String uri = String.format(URI_FORMAT, url(), OpenstackConstants.GET);
        SwiftObject swiftObject = template.requestBodyAndHeaders(uri, null, headers, SwiftObject.class);

        assertEquals(OBJECT_CONTAINER_NAME, swiftObject.getContainerName());
        assertEquals(OBJECT_NAME, swiftObject.getName());
        assertEquals(15, swiftObject.getSizeInBytes());
        assertEquals("application/json", swiftObject.getMimeType());
        assertEquals("12345678901234567890", swiftObject.getETag());
    }

    @Test
    void getFromAnotherThreadShouldSucceed() {
        Map<String, Object> headers = new HashMap<>();
        headers.put(SwiftConstants.CONTAINER_NAME, OBJECT_CONTAINER_NAME);
        headers.put(SwiftConstants.OBJECT_NAME, OBJECT_NAME);

        List<CompletableFuture<SwiftObject>> futures = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            futures.add(template.asyncRequestBodyAndHeaders("seda:get", null, headers, SwiftObject.class));
        }

        for (CompletableFuture<SwiftObject> future : futures) {
            SwiftObject swiftObject = future.join();
            assertEquals(OBJECT_NAME, swiftObject.getName());
            assertEquals("12345678901234567890", swiftObject.getETag());
        }
    }

    @Test
    void createFromTextShouldSucceed() {
        String uri = String.format(URI_FORMAT, url(), OpenstackConstants.CREATE);
        String etag = template.requestBodyAndHeaders(uri, OBJECT_CONTENT, objectHeaders(), String.class);

        assertNotNull(etag);
        assertTrue(etag.startsWith(OBJECT_ETAG), etag);
    }

    @Test
    void createFromBytesShouldSucceed() {
        String uri = String.format(URI_FORMAT, url(), OpenstackConstants.CREATE);
        String etag = template.requestBodyAndHeaders(uri, OBJECT_CONTENT.getBytes(StandardCharsets.UTF_8), objectHeaders(),
                String.class);

        assertNotNull(etag);
        assertTrue(etag.startsWith(OBJECT_ETAG), etag);
    }

    @Test
    void createFromFileConsumerShouldSucceed() throws Exception {
        MockEndpoint stored = getMockEndpoint("mock:stored");
        stored.expectedMessageCount(1);
        stored.message(0).body(String.class).startsWith(OBJECT_ETAG);

        template.sendBodyAndHeader("file:target/openstack-swift-outbox", OBJECT_CONTENT, Exchange.FILE_NAME, OBJECT_NAME);

        stored.assertIsSatisfied();
    }

    private static Map<String, Object> objectHeaders() {
        Map<String, Object> headers = new HashMap<>();
        headers.put(SwiftConstants.CONTAINER_NAME, OBJECT_CONTAINER_NAME);
        headers.put(SwiftConstants.OBJECT_NAME, OBJECT_NAME);
        return headers;
    }

}
