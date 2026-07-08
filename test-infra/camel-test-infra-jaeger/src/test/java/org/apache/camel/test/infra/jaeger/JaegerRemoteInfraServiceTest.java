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
package org.apache.camel.test.infra.jaeger;

import org.apache.camel.test.infra.jaeger.common.JaegerProperties;
import org.apache.camel.test.infra.jaeger.services.JaegerRemoteInfraService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @since 4.21
 */
class JaegerRemoteInfraServiceTest {

    private JaegerRemoteInfraService service;

    @BeforeEach
    void setUp() {
        service = new JaegerRemoteInfraService();
    }

    @AfterEach
    void tearDown() {
        System.clearProperty(JaegerProperties.HOST);
        System.clearProperty(JaegerProperties.COLLECTOR_GRPC_PORT);
        System.clearProperty(JaegerProperties.COLLECTOR_HTTP_PORT);
        System.clearProperty(JaegerProperties.QUERY_UI_PORT);
        service.shutdown();
    }

    @Test
    void hostReturnsValueFromSystemProperty() {
        System.setProperty(JaegerProperties.HOST, "my-jaeger-host");
        assertEquals("my-jaeger-host", service.host());
    }

    @Test
    void hostThrowsWhenPropertyNotSet() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.host());
        assertTrue(ex.getMessage().contains(JaegerProperties.HOST));
    }

    @Test
    void collectorGrpcPortReturnsDefaultWhenNotSet() {
        assertEquals(JaegerProperties.DEFAULT_COLLECTOR_GRPC_PORT, service.collectorGrpcPort());
    }

    @Test
    void collectorGrpcPortReturnsValueFromSystemProperty() {
        System.setProperty(JaegerProperties.COLLECTOR_GRPC_PORT, "9317");
        assertEquals(9317, service.collectorGrpcPort());
    }

    @Test
    void collectorHttpPortReturnsDefaultWhenNotSet() {
        assertEquals(JaegerProperties.DEFAULT_COLLECTOR_HTTP_PORT, service.collectorHttpPort());
    }

    @Test
    void collectorHttpPortReturnsValueFromSystemProperty() {
        System.setProperty(JaegerProperties.COLLECTOR_HTTP_PORT, "9318");
        assertEquals(9318, service.collectorHttpPort());
    }

    @Test
    void queryUiPortReturnsDefaultWhenNotSet() {
        assertEquals(JaegerProperties.DEFAULT_QUERY_UI_PORT, service.queryUiPort());
    }

    @Test
    void queryUiPortReturnsValueFromSystemProperty() {
        System.setProperty(JaegerProperties.QUERY_UI_PORT, "18686");
        assertEquals(18686, service.queryUiPort());
    }

    @Test
    void collectorGrpcEndpointFormatsCorrectly() {
        System.setProperty(JaegerProperties.HOST, "my-jaeger-host");
        System.setProperty(JaegerProperties.COLLECTOR_GRPC_PORT, "9317");
        assertEquals("http://my-jaeger-host:9317", service.collectorGrpcEndpoint());
        // Verify the OTel-required http:// scheme is used, not grpc://
        assertFalse(service.collectorGrpcEndpoint().startsWith("grpc://"),
                "OTLP gRPC endpoint must use http:// scheme, not grpc://");
    }

    @Test
    void collectorHttpEndpointFormatsCorrectly() {
        System.setProperty(JaegerProperties.HOST, "my-jaeger-host");
        System.setProperty(JaegerProperties.COLLECTOR_HTTP_PORT, "9318");
        assertEquals("http://my-jaeger-host:9318", service.collectorHttpEndpoint());
    }

    @Test
    void queryUiUrlFormatsCorrectly() {
        System.setProperty(JaegerProperties.HOST, "my-jaeger-host");
        System.setProperty(JaegerProperties.QUERY_UI_PORT, "18686");
        assertEquals("http://my-jaeger-host:18686", service.queryUiUrl());
    }
}
