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

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.apache.camel.test.infra.jaeger.services.JaegerLocalContainerInfraService;

/**
 * Manual test application that starts a Jaeger container, sends sample OTEL traces, and waits for the user to press
 * Enter before shutting down.
 *
 * Run with: mvn test-compile exec:java -Dexec.mainClass=org.apache.camel.test.infra.jaeger.JaegerTestApp
 * -Dexec.classpathScope=test -pl test-infra/camel-test-infra-jaeger
 */
public final class JaegerTestApp {

    private static final String SERVICE_NAME = "camel-jaeger-test-app";

    private JaegerTestApp() {
    }

    public static void main(String[] args) throws Exception {
        JaegerLocalContainerInfraService jaeger = new JaegerLocalContainerInfraService();
        jaeger.initialize();

        System.out.println("=================================================");
        System.out.println("Jaeger UI:        " + jaeger.queryUiUrl());
        System.out.println("OTLP gRPC:        " + jaeger.collectorGrpcEndpoint());
        System.out.println("OTLP HTTP:        " + jaeger.collectorHttpEndpoint());
        System.out.println("=================================================");

        OtlpHttpSpanExporter exporter = OtlpHttpSpanExporter.builder()
                .setEndpoint(jaeger.collectorHttpEndpoint() + "/v1/traces")
                .build();

        Resource resource = Resource.getDefault().merge(
                Resource.create(Attributes.of(AttributeKey.stringKey("service.name"), SERVICE_NAME)));

        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .setResource(resource)
                .build();

        try (OpenTelemetrySdk sdk = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build()) {
            Tracer tracer = sdk.getTracer(SERVICE_NAME);
            sendSampleTraces(tracer);

            tracerProvider.forceFlush().join(10, TimeUnit.SECONDS);

            System.out.println();
            System.out.println("Sample traces sent. Open the Jaeger UI at: " + jaeger.queryUiUrl());
            System.out.println("Press Enter to stop...");
            System.in.read();
        } finally {
            jaeger.shutdown();
        }
    }

    private static void sendSampleTraces(Tracer tracer) throws InterruptedException {
        traceGetProductRoute(tracer);
        traceSubmitOrderRoute(tracer);
        traceProcessPaymentRoute(tracer);
    }

    /**
     * Simulates: GET /api/products/{id}
     *
     * Flow: try cache → miss → DB lookup → populate cache → build response
     */
    private static void traceGetProductRoute(Tracer tracer) throws InterruptedException {
        Span route = tracer.spanBuilder("camel-route: get-product-route")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("camel.route.id", "get-product-route")
                .setAttribute("camel.uri", "platform-http:/api/products/{id}")
                .setAttribute("http.method", "GET")
                .setAttribute("http.url", "/api/products/42")
                .startSpan();

        try (Scope ignored = route.makeCurrent()) {
            // Step 1: cache probe
            Span cacheGet = tracer.spanBuilder("spring-redis://products")
                    .setSpanKind(SpanKind.CLIENT)
                    .setAttribute("camel.uri", "spring-redis://products?command=GET")
                    .setAttribute("db.system", "redis")
                    .setAttribute("db.operation", "GET")
                    .setAttribute("cache.key", "product:42")
                    .startSpan();
            sleep(5, 15);
            cacheGet.addEvent("cache.miss");
            cacheGet.end();

            // Step 2: DB lookup on cache miss
            Span dbSelect = tracer.spanBuilder("jpa://org.apache.camel.example.Product")
                    .setSpanKind(SpanKind.CLIENT)
                    .setAttribute("camel.uri", "jpa://org.apache.camel.example.Product?query=findById")
                    .setAttribute("db.system", "postgresql")
                    .setAttribute("db.name", "cameldb")
                    .setAttribute("db.operation", "SELECT")
                    .setAttribute("db.statement", "SELECT * FROM products WHERE id = 42")
                    .startSpan();
            sleep(60, 110);
            dbSelect.end();

            // Step 3: processor — map JPA entity to response DTO
            Span mapProcessor = tracer.spanBuilder("camel-processor: ProductResponseMapper")
                    .setAttribute("camel.processor.type", "bean")
                    .setAttribute("camel.bean.method", "toResponse")
                    .startSpan();
            sleep(5, 15);
            mapProcessor.end();

            // Step 4: populate cache for next request
            Span cachePut = tracer.spanBuilder("spring-redis://products")
                    .setSpanKind(SpanKind.CLIENT)
                    .setAttribute("camel.uri", "spring-redis://products?command=SET&timeToLive=300")
                    .setAttribute("db.system", "redis")
                    .setAttribute("db.operation", "SET")
                    .setAttribute("cache.key", "product:42")
                    .startSpan();
            sleep(3, 10);
            cachePut.end();

            // post-processing: marshal to JSON
            sleep(5, 15);
            route.setAttribute("http.status_code", 200L);
        } finally {
            route.end();
        }
        System.out.println("Sent trace: get-product-route");
    }

    /**
     * Simulates: POST /api/orders
     *
     * Flow: validate payload → persist order → publish to Kafka → reply
     */
    private static void traceSubmitOrderRoute(Tracer tracer) throws InterruptedException {
        Span route = tracer.spanBuilder("camel-route: submit-order-route")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("camel.route.id", "submit-order-route")
                .setAttribute("camel.uri", "platform-http:/api/orders")
                .setAttribute("http.method", "POST")
                .setAttribute("http.url", "/api/orders")
                .startSpan();

        try (Scope ignored = route.makeCurrent()) {
            // Step 1: validate incoming payload
            Span validator = tracer.spanBuilder("camel-processor: OrderPayloadValidator")
                    .setAttribute("camel.processor.type", "bean")
                    .setAttribute("camel.bean.method", "validate")
                    .startSpan();
            sleep(8, 20);
            validator.end();

            // Step 2: persist the order
            Span dbInsert = tracer.spanBuilder("jdbc://cameldb")
                    .setSpanKind(SpanKind.CLIENT)
                    .setAttribute("camel.uri", "jdbc://cameldb")
                    .setAttribute("db.system", "postgresql")
                    .setAttribute("db.name", "cameldb")
                    .setAttribute("db.operation", "INSERT")
                    .setAttribute("db.statement", "INSERT INTO orders (customer_id, total, status) VALUES (?, ?, 'PENDING')")
                    .startSpan();
            sleep(40, 80);
            dbInsert.end();

            // Step 3: enrich with inventory data before publishing
            Span inventoryCheck = tracer.spanBuilder("http://inventory-service/api/stock")
                    .setSpanKind(SpanKind.CLIENT)
                    .setAttribute("camel.uri", "http://inventory-service/api/stock?bridgeEndpoint=true")
                    .setAttribute("http.method", "GET")
                    .setAttribute("net.peer.name", "inventory-service")
                    .startSpan();
            sleep(25, 55);
            inventoryCheck.end();

            // Step 4: publish order-created event to Kafka
            Span kafkaPublish = tracer.spanBuilder("kafka://order-events")
                    .setSpanKind(SpanKind.PRODUCER)
                    .setAttribute("camel.uri", "kafka://order-events?brokers=kafka:9092")
                    .setAttribute("messaging.system", "kafka")
                    .setAttribute("messaging.destination", "order-events")
                    .setAttribute("messaging.operation", "publish")
                    .startSpan();
            sleep(15, 35);
            kafkaPublish.end();

            // post-processing: build 201 response
            sleep(5, 12);
            route.setAttribute("http.status_code", 201L);
        } finally {
            route.end();
        }
        System.out.println("Sent trace: submit-order-route");
    }

    /**
     * Simulates: payment processing triggered from a Kafka consumer
     *
     * Flow: consume event → call payment gateway → fraud check → persist transaction → dead-letter on fraud flag
     */
    private static void traceProcessPaymentRoute(Tracer tracer) throws InterruptedException {
        Span route = tracer.spanBuilder("camel-route: payment-processor-route")
                .setSpanKind(SpanKind.CONSUMER)
                .setAttribute("camel.route.id", "payment-processor-route")
                .setAttribute("camel.uri", "kafka://payment-requests?brokers=kafka:9092&groupId=payment-svc")
                .setAttribute("messaging.system", "kafka")
                .setAttribute("messaging.destination", "payment-requests")
                .setAttribute("messaging.operation", "receive")
                .startSpan();

        try (Scope ignored = route.makeCurrent()) {
            // Step 1: unmarshal and validate
            Span unmarshal = tracer.spanBuilder("camel-processor: PaymentRequestUnmarshaller")
                    .setAttribute("camel.processor.type", "unmarshal")
                    .setAttribute("camel.data.format", "jackson")
                    .startSpan();
            sleep(5, 12);
            unmarshal.end();

            // Step 2: call external payment gateway
            Span gateway = tracer.spanBuilder("http://payment-gateway.internal/v2/charge")
                    .setSpanKind(SpanKind.CLIENT)
                    .setAttribute("camel.uri", "http://payment-gateway.internal/v2/charge")
                    .setAttribute("http.method", "POST")
                    .setAttribute("net.peer.name", "payment-gateway.internal")
                    .setAttribute("http.url", "/v2/charge")
                    .startSpan();
            sleep(90, 160);
            gateway.setAttribute("http.status_code", 200L);
            gateway.end();

            // Step 3: fraud detection processor
            Span fraud = tracer.spanBuilder("camel-processor: FraudDetectionEnricher")
                    .setAttribute("camel.processor.type", "bean")
                    .setAttribute("camel.bean.method", "evaluate")
                    .startSpan();
            sleep(20, 45);
            fraud.addEvent("fraud.score.computed", Attributes.of(
                    AttributeKey.doubleKey("fraud.score"), ThreadLocalRandom.current().nextDouble(0.01, 0.15)));
            fraud.end();

            // Step 4: persist the transaction record
            Span txPersist = tracer.spanBuilder("jpa://org.apache.camel.example.Transaction")
                    .setSpanKind(SpanKind.CLIENT)
                    .setAttribute("camel.uri", "jpa://org.apache.camel.example.Transaction?usePersist=true")
                    .setAttribute("db.system", "postgresql")
                    .setAttribute("db.name", "cameldb")
                    .setAttribute("db.operation", "INSERT")
                    .setAttribute("db.statement", "INSERT INTO transactions (order_id, amount, status, gateway_ref)")
                    .startSpan();
            sleep(30, 65);
            txPersist.end();

            // Step 5: publish confirmation event
            Span confirmation = tracer.spanBuilder("kafka://payment-confirmations")
                    .setSpanKind(SpanKind.PRODUCER)
                    .setAttribute("camel.uri", "kafka://payment-confirmations?brokers=kafka:9092")
                    .setAttribute("messaging.system", "kafka")
                    .setAttribute("messaging.destination", "payment-confirmations")
                    .setAttribute("messaging.operation", "publish")
                    .startSpan();
            sleep(10, 25);
            confirmation.end();

            // post-processing: commit offset
            sleep(3, 8);
        } finally {
            route.end();
        }
        System.out.println("Sent trace: payment-processor-route");
    }

    // Intentional sleep: this is a manual demo app, not an automated test.
    // The delays produce realistic span durations in the Jaeger UI so traces look like real Camel route executions.
    private static void sleep(long minMs, long maxMs) throws InterruptedException {
        Thread.sleep(ThreadLocalRandom.current().nextLong(minMs, maxMs));
    }
}
