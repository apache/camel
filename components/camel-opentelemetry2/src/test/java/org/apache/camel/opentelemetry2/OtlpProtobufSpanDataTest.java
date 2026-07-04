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
package org.apache.camel.opentelemetry2;

import java.util.List;

import com.google.protobuf.ByteString;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.InstrumentationScope;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.resource.v1.Resource;
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import io.opentelemetry.proto.trace.v1.ScopeSpans;
import io.opentelemetry.proto.trace.v1.Span;
import io.opentelemetry.proto.trace.v1.Status;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OtlpProtobufSpanDataTest {

    @Test
    void testFromProtobufBasicSpan() throws Exception {
        byte[] traceIdBytes = hexToBytes("0af7651916cd43dd8448eb211c80319c");
        byte[] spanIdBytes = hexToBytes("00f067aa0ba902b7");
        byte[] parentSpanIdBytes = hexToBytes("b7ad6b7169203331");

        Span span = Span.newBuilder()
                .setTraceId(ByteString.copyFrom(traceIdBytes))
                .setSpanId(ByteString.copyFrom(spanIdBytes))
                .setParentSpanId(ByteString.copyFrom(parentSpanIdBytes))
                .setName("GET /api/users")
                .setKind(Span.SpanKind.SPAN_KIND_SERVER)
                .setStartTimeUnixNano(1_000_000_000L)
                .setEndTimeUnixNano(2_000_000_000L)
                .setStatus(Status.newBuilder().setCode(Status.StatusCode.STATUS_CODE_OK).build())
                .build();

        ExportTraceServiceRequest request = buildRequest("camel", "1.0.0", span);
        List<SpanData> spans = OtlpProtobufSpanData.fromProtobuf(request.toByteArray());

        assertEquals(1, spans.size());
        SpanData sd = spans.get(0);
        assertEquals("GET /api/users", sd.getName());
        assertEquals(SpanKind.SERVER, sd.getKind());
        assertEquals("0af7651916cd43dd8448eb211c80319c", sd.getSpanContext().getTraceId());
        assertEquals("00f067aa0ba902b7", sd.getSpanContext().getSpanId());
        assertEquals("0af7651916cd43dd8448eb211c80319c", sd.getParentSpanContext().getTraceId());
        assertEquals("b7ad6b7169203331", sd.getParentSpanContext().getSpanId());
        assertEquals(1_000_000_000L, sd.getStartEpochNanos());
        assertEquals(2_000_000_000L, sd.getEndEpochNanos());
        assertEquals(StatusCode.OK, sd.getStatus().getStatusCode());
        assertTrue(sd.hasEnded());
    }

    @Test
    void testFromProtobufScopeInfo() throws Exception {
        Span span = buildSimpleSpan("test-span");
        ExportTraceServiceRequest request = buildRequest("io.opentelemetry.jdk-http-client", "2.12.0", span);

        List<SpanData> spans = OtlpProtobufSpanData.fromProtobuf(request.toByteArray());

        assertEquals(1, spans.size());
        assertEquals("io.opentelemetry.jdk-http-client", spans.get(0).getInstrumentationScopeInfo().getName());
        assertEquals("2.12.0", spans.get(0).getInstrumentationScopeInfo().getVersion());
    }

    @Test
    void testFromProtobufCamelScope() throws Exception {
        Span span = buildSimpleSpan("camel-route");
        ExportTraceServiceRequest request = buildRequest("camel", null, span);

        List<SpanData> spans = OtlpProtobufSpanData.fromProtobuf(request.toByteArray());

        assertEquals(1, spans.size());
        assertEquals("camel", spans.get(0).getInstrumentationScopeInfo().getName());
    }

    @Test
    void testFromProtobufAttributes() throws Exception {
        Span span = Span.newBuilder()
                .setTraceId(ByteString.copyFrom(hexToBytes("0af7651916cd43dd8448eb211c80319c")))
                .setSpanId(ByteString.copyFrom(hexToBytes("00f067aa0ba902b7")))
                .setName("test")
                .addAttributes(stringAttr("http.method", "GET"))
                .addAttributes(longAttr("http.status_code", 200))
                .addAttributes(doubleAttr("response.time", 1.5))
                .addAttributes(boolAttr("error", false))
                .build();

        ExportTraceServiceRequest request = buildRequest("camel", null, span);
        List<SpanData> spans = OtlpProtobufSpanData.fromProtobuf(request.toByteArray());

        SpanData sd = spans.get(0);
        assertEquals("GET", sd.getAttributes().get(AttributeKey.stringKey("http.method")));
        assertEquals(200L, sd.getAttributes().get(AttributeKey.longKey("http.status_code")));
        assertEquals(1.5, sd.getAttributes().get(AttributeKey.doubleKey("response.time")));
        assertEquals(false, sd.getAttributes().get(AttributeKey.booleanKey("error")));
    }

    @Test
    void testFromProtobufSpanKindMapping() throws Exception {
        for (var entry : List.of(
                new Object[] { Span.SpanKind.SPAN_KIND_CLIENT, SpanKind.CLIENT },
                new Object[] { Span.SpanKind.SPAN_KIND_SERVER, SpanKind.SERVER },
                new Object[] { Span.SpanKind.SPAN_KIND_PRODUCER, SpanKind.PRODUCER },
                new Object[] { Span.SpanKind.SPAN_KIND_CONSUMER, SpanKind.CONSUMER },
                new Object[] { Span.SpanKind.SPAN_KIND_INTERNAL, SpanKind.INTERNAL })) {

            Span span = Span.newBuilder()
                    .setTraceId(ByteString.copyFrom(hexToBytes("0af7651916cd43dd8448eb211c80319c")))
                    .setSpanId(ByteString.copyFrom(hexToBytes("00f067aa0ba902b7")))
                    .setName("test")
                    .setKind((Span.SpanKind) entry[0])
                    .build();

            ExportTraceServiceRequest request = buildRequest("camel", null, span);
            List<SpanData> spans = OtlpProtobufSpanData.fromProtobuf(request.toByteArray());
            assertEquals(entry[1], spans.get(0).getKind(), "Kind mapping failed for " + entry[0]);
        }
    }

    @Test
    void testFromProtobufErrorStatus() throws Exception {
        Span span = Span.newBuilder()
                .setTraceId(ByteString.copyFrom(hexToBytes("0af7651916cd43dd8448eb211c80319c")))
                .setSpanId(ByteString.copyFrom(hexToBytes("00f067aa0ba902b7")))
                .setName("failing-span")
                .setStatus(Status.newBuilder()
                        .setCode(Status.StatusCode.STATUS_CODE_ERROR)
                        .setMessage("Connection refused")
                        .build())
                .build();

        ExportTraceServiceRequest request = buildRequest("camel", null, span);
        List<SpanData> spans = OtlpProtobufSpanData.fromProtobuf(request.toByteArray());

        assertEquals(StatusCode.ERROR, spans.get(0).getStatus().getStatusCode());
        assertEquals("Connection refused", spans.get(0).getStatus().getDescription());
    }

    @Test
    void testFromProtobufRootSpan() throws Exception {
        Span span = Span.newBuilder()
                .setTraceId(ByteString.copyFrom(hexToBytes("0af7651916cd43dd8448eb211c80319c")))
                .setSpanId(ByteString.copyFrom(hexToBytes("00f067aa0ba902b7")))
                .setName("root")
                .build();

        ExportTraceServiceRequest request = buildRequest("camel", null, span);
        List<SpanData> spans = OtlpProtobufSpanData.fromProtobuf(request.toByteArray());

        assertFalse(spans.get(0).getParentSpanContext().isValid());
    }

    @Test
    void testFromProtobufMultipleSpans() throws Exception {
        Span span1 = Span.newBuilder()
                .setTraceId(ByteString.copyFrom(hexToBytes("0af7651916cd43dd8448eb211c80319c")))
                .setSpanId(ByteString.copyFrom(hexToBytes("00f067aa0ba902b7")))
                .setName("span-1")
                .build();
        Span span2 = Span.newBuilder()
                .setTraceId(ByteString.copyFrom(hexToBytes("0af7651916cd43dd8448eb211c80319c")))
                .setSpanId(ByteString.copyFrom(hexToBytes("b7ad6b7169203331")))
                .setName("span-2")
                .build();

        ScopeSpans scopeSpans = ScopeSpans.newBuilder()
                .setScope(InstrumentationScope.newBuilder().setName("camel").build())
                .addSpans(span1)
                .addSpans(span2)
                .build();

        ExportTraceServiceRequest request = ExportTraceServiceRequest.newBuilder()
                .addResourceSpans(ResourceSpans.newBuilder()
                        .setResource(Resource.newBuilder().build())
                        .addScopeSpans(scopeSpans)
                        .build())
                .build();

        List<SpanData> spans = OtlpProtobufSpanData.fromProtobuf(request.toByteArray());
        assertEquals(2, spans.size());
        assertEquals("span-1", spans.get(0).getName());
        assertEquals("span-2", spans.get(1).getName());
    }

    @Test
    void testFromProtobufResourceAttributes() throws Exception {
        Span span = buildSimpleSpan("test");
        ExportTraceServiceRequest request = ExportTraceServiceRequest.newBuilder()
                .addResourceSpans(ResourceSpans.newBuilder()
                        .setResource(Resource.newBuilder()
                                .addAttributes(stringAttr("service.name", "my-camel-app"))
                                .build())
                        .addScopeSpans(ScopeSpans.newBuilder()
                                .setScope(InstrumentationScope.newBuilder().setName("camel").build())
                                .addSpans(span)
                                .build())
                        .build())
                .build();

        List<SpanData> spans = OtlpProtobufSpanData.fromProtobuf(request.toByteArray());
        assertNotNull(spans.get(0).getResource());
        assertEquals("my-camel-app",
                spans.get(0).getResource().getAttributes().get(AttributeKey.stringKey("service.name")));
    }

    @Test
    void testSpanDataDefaults() throws Exception {
        Span span = buildSimpleSpan("test");
        ExportTraceServiceRequest request = buildRequest("camel", null, span);
        List<SpanData> spans = OtlpProtobufSpanData.fromProtobuf(request.toByteArray());

        SpanData sd = spans.get(0);
        assertTrue(sd.getEvents().isEmpty());
        assertTrue(sd.getLinks().isEmpty());
        assertEquals(0, sd.getTotalRecordedEvents());
        assertEquals(0, sd.getTotalRecordedLinks());
    }

    private static Span buildSimpleSpan(String name) {
        return Span.newBuilder()
                .setTraceId(ByteString.copyFrom(hexToBytes("0af7651916cd43dd8448eb211c80319c")))
                .setSpanId(ByteString.copyFrom(hexToBytes("00f067aa0ba902b7")))
                .setName(name)
                .build();
    }

    private static ExportTraceServiceRequest buildRequest(String scopeName, String scopeVersion, Span span) {
        InstrumentationScope.Builder scopeBuilder = InstrumentationScope.newBuilder().setName(scopeName);
        if (scopeVersion != null) {
            scopeBuilder.setVersion(scopeVersion);
        }
        return ExportTraceServiceRequest.newBuilder()
                .addResourceSpans(ResourceSpans.newBuilder()
                        .setResource(Resource.newBuilder().build())
                        .addScopeSpans(ScopeSpans.newBuilder()
                                .setScope(scopeBuilder.build())
                                .addSpans(span)
                                .build())
                        .build())
                .build();
    }

    private static KeyValue stringAttr(String key, String value) {
        return KeyValue.newBuilder()
                .setKey(key)
                .setValue(AnyValue.newBuilder().setStringValue(value).build())
                .build();
    }

    private static KeyValue longAttr(String key, long value) {
        return KeyValue.newBuilder()
                .setKey(key)
                .setValue(AnyValue.newBuilder().setIntValue(value).build())
                .build();
    }

    private static KeyValue doubleAttr(String key, double value) {
        return KeyValue.newBuilder()
                .setKey(key)
                .setValue(AnyValue.newBuilder().setDoubleValue(value).build())
                .build();
    }

    private static KeyValue boolAttr(String key, boolean value) {
        return KeyValue.newBuilder()
                .setKey(key)
                .setValue(AnyValue.newBuilder().setBoolValue(value).build())
                .build();
    }

    private static byte[] hexToBytes(String hex) {
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }
}
