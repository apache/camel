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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.TimeUnit;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.apache.camel.test.infra.jaeger.services.JaegerService;
import org.apache.camel.test.infra.jaeger.services.JaegerServiceFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @since 4.21
 */
class JaegerServiceIT {

    @RegisterExtension
    static JaegerService service = JaegerServiceFactory.createService();

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    @Test
    void shouldReceiveTraceViaOtlpHttp() throws Exception {
        final String serviceName = "camel-jaeger-it-http";
        final String operationName = "http-test-operation";

        try (OtlpHttpSpanExporter exporter = OtlpHttpSpanExporter.builder()
                .setEndpoint(service.collectorHttpEndpoint() + "/v1/traces")
                .build()) {
            sendAndFlushTrace(exporter, serviceName, operationName);
        }

        assertTraceReceived(serviceName, operationName);
    }

    @Test
    void shouldReceiveTraceViaOtlpGrpc() throws Exception {
        final String serviceName = "camel-jaeger-it-grpc";
        final String operationName = "grpc-test-operation";

        try (OtlpGrpcSpanExporter exporter = OtlpGrpcSpanExporter.builder()
                .setEndpoint(service.collectorGrpcEndpoint())
                .build()) {
            sendAndFlushTrace(exporter, serviceName, operationName);
        }

        assertTraceReceived(serviceName, operationName);
    }

    private static void sendAndFlushTrace(SpanExporter exporter, String serviceName, String operationName)
            throws Exception {
        Resource resource = Resource.getDefault().merge(
                Resource.create(Attributes.of(AttributeKey.stringKey("service.name"), serviceName)));

        try (SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .setResource(resource)
                .build();
             OpenTelemetrySdk sdk = OpenTelemetrySdk.builder()
                     .setTracerProvider(tracerProvider)
                     .build()) {
            Tracer tracer = sdk.getTracer(serviceName);
            Span span = tracer.spanBuilder(operationName)
                    .setAttribute("test.key", "test-value")
                    .startSpan();
            span.end();

            CompletableResultCode flushResult = tracerProvider.forceFlush().join(10, TimeUnit.SECONDS);
            assertTrue(flushResult.isSuccess(), "Span flush timed out or failed after 10 seconds");
        }
    }

    private static void assertTraceReceived(String serviceName, String operationName) {
        String queryUrl = service.queryUiUrl() + "/api/traces?service=" + serviceName + "&limit=10";

        await().atMost(30, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    HttpResponse<String> response = HTTP_CLIENT.send(
                            HttpRequest.newBuilder(URI.create(queryUrl)).GET().build(),
                            HttpResponse.BodyHandlers.ofString());
                    assertEquals(200, response.statusCode());
                    String body = response.body();
                    assertTrue(body.contains(operationName),
                            "Expected '" + operationName + "' in Jaeger response but got: " + body);
                    assertTrue(body.contains("test-value"),
                            "Expected span attribute 'test-value' in Jaeger response but got: " + body);
                });
    }
}
