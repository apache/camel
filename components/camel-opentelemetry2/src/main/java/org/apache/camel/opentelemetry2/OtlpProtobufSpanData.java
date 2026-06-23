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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.protobuf.ByteString;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.InstrumentationScope;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import io.opentelemetry.proto.trace.v1.ScopeSpans;
import io.opentelemetry.proto.trace.v1.Span;
import io.opentelemetry.proto.trace.v1.Status;
import io.opentelemetry.sdk.common.InstrumentationLibraryInfo;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;

/**
 * Lightweight {@link SpanData} adapter that wraps an OTLP protobuf span. Used by the embedded OTLP receiver to feed
 * spans from the OpenTelemetry Java Agent into {@link DevSpanExporter}.
 */
final class OtlpProtobufSpanData implements SpanData {

    private final SpanContext spanContext;
    private final SpanContext parentSpanContext;
    private final String name;
    private final SpanKind kind;
    private final StatusData status;
    private final long startEpochNanos;
    private final long endEpochNanos;
    private final Attributes attributes;
    private final InstrumentationScopeInfo scopeInfo;
    private final Resource resource;

    private OtlpProtobufSpanData(SpanContext spanContext, SpanContext parentSpanContext, String name,
                                 SpanKind kind, StatusData status, long startEpochNanos, long endEpochNanos,
                                 Attributes attributes, InstrumentationScopeInfo scopeInfo, Resource resource) {
        this.spanContext = spanContext;
        this.parentSpanContext = parentSpanContext;
        this.name = name;
        this.kind = kind;
        this.status = status;
        this.startEpochNanos = startEpochNanos;
        this.endEpochNanos = endEpochNanos;
        this.attributes = attributes;
        this.scopeInfo = scopeInfo;
        this.resource = resource;
    }

    static List<SpanData> fromProtobuf(byte[] body) throws Exception {
        ExportTraceServiceRequest request = ExportTraceServiceRequest.parseFrom(body);
        List<SpanData> result = new ArrayList<>();

        for (ResourceSpans rs : request.getResourceSpansList()) {
            Resource resource = parseResource(rs.getResource());
            for (ScopeSpans ss : rs.getScopeSpansList()) {
                InstrumentationScopeInfo scopeInfo = parseScope(ss.getScope());
                for (Span span : ss.getSpansList()) {
                    result.add(fromSpan(span, resource, scopeInfo));
                }
            }
        }
        return result;
    }

    private static OtlpProtobufSpanData fromSpan(
            Span span, Resource resource, InstrumentationScopeInfo scopeInfo) {

        String traceId = hex(span.getTraceId());
        String spanId = hex(span.getSpanId());
        String parentSpanId = hex(span.getParentSpanId());

        SpanContext sc = SpanContext.create(traceId, spanId, TraceFlags.getSampled(), TraceState.getDefault());
        SpanContext parentSc;
        if (parentSpanId.isEmpty()) {
            parentSc = SpanContext.getInvalid();
        } else {
            parentSc = SpanContext.create(traceId, parentSpanId, TraceFlags.getSampled(), TraceState.getDefault());
        }

        return new OtlpProtobufSpanData(
                sc, parentSc, span.getName(),
                toSpanKind(span.getKind()),
                toStatusData(span.getStatus()),
                span.getStartTimeUnixNano(),
                span.getEndTimeUnixNano(),
                toAttributes(span.getAttributesList()),
                scopeInfo, resource);
    }

    private static Resource parseResource(io.opentelemetry.proto.resource.v1.Resource res) {
        if (res == null) {
            return Resource.getDefault();
        }
        return Resource.create(toAttributes(res.getAttributesList()));
    }

    private static InstrumentationScopeInfo parseScope(InstrumentationScope scope) {
        if (scope == null) {
            return InstrumentationScopeInfo.create("unknown");
        }
        String name = scope.getName().isEmpty() ? "unknown" : scope.getName();
        String version = scope.getVersion().isEmpty() ? null : scope.getVersion();
        if (version != null) {
            return InstrumentationScopeInfo.builder(name).setVersion(version).build();
        }
        return InstrumentationScopeInfo.create(name);
    }

    private static SpanKind toSpanKind(Span.SpanKind kind) {
        return switch (kind) {
            case SPAN_KIND_SERVER -> SpanKind.SERVER;
            case SPAN_KIND_CLIENT -> SpanKind.CLIENT;
            case SPAN_KIND_PRODUCER -> SpanKind.PRODUCER;
            case SPAN_KIND_CONSUMER -> SpanKind.CONSUMER;
            default -> SpanKind.INTERNAL;
        };
    }

    private static StatusData toStatusData(Status status) {
        if (status == null) {
            return StatusData.unset();
        }
        return switch (status.getCode()) {
            case STATUS_CODE_OK -> StatusData.ok();
            case STATUS_CODE_ERROR -> StatusData.create(StatusCode.ERROR, status.getMessage());
            default -> StatusData.unset();
        };
    }

    private static Attributes toAttributes(List<KeyValue> kvs) {
        if (kvs == null || kvs.isEmpty()) {
            return Attributes.empty();
        }
        AttributesBuilder ab = Attributes.builder();
        for (KeyValue kv : kvs) {
            String key = kv.getKey();
            AnyValue value = kv.getValue();
            if (value.hasStringValue()) {
                ab.put(AttributeKey.stringKey(key), value.getStringValue());
            } else if (value.hasIntValue()) {
                ab.put(AttributeKey.longKey(key), value.getIntValue());
            } else if (value.hasDoubleValue()) {
                ab.put(AttributeKey.doubleKey(key), value.getDoubleValue());
            } else if (value.hasBoolValue()) {
                ab.put(AttributeKey.booleanKey(key), value.getBoolValue());
            }
        }
        return ab.build();
    }

    private static String hex(ByteString bytes) {
        if (bytes == null || bytes.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(bytes.size() * 2);
        for (int i = 0; i < bytes.size(); i++) {
            int b = bytes.byteAt(i) & 0xFF;
            sb.append(Character.forDigit(b >>> 4, 16));
            sb.append(Character.forDigit(b & 0x0F, 16));
        }
        return sb.toString();
    }

    @Override
    public SpanContext getSpanContext() {
        return spanContext;
    }

    @Override
    public SpanContext getParentSpanContext() {
        return parentSpanContext;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public SpanKind getKind() {
        return kind;
    }

    @Override
    public StatusData getStatus() {
        return status;
    }

    @Override
    public long getStartEpochNanos() {
        return startEpochNanos;
    }

    @Override
    public long getEndEpochNanos() {
        return endEpochNanos;
    }

    @Override
    public Attributes getAttributes() {
        return attributes;
    }

    @Override
    public List<EventData> getEvents() {
        return Collections.emptyList();
    }

    @Override
    public List<LinkData> getLinks() {
        return Collections.emptyList();
    }

    @Override
    public boolean hasEnded() {
        return true;
    }

    @Override
    public int getTotalRecordedEvents() {
        return 0;
    }

    @Override
    public int getTotalRecordedLinks() {
        return 0;
    }

    @Override
    public int getTotalAttributeCount() {
        return attributes.size();
    }

    @Override
    @SuppressWarnings("deprecation")
    public InstrumentationLibraryInfo getInstrumentationLibraryInfo() {
        return InstrumentationLibraryInfo.create(scopeInfo.getName(), scopeInfo.getVersion());
    }

    @Override
    public InstrumentationScopeInfo getInstrumentationScopeInfo() {
        return scopeInfo;
    }

    @Override
    public Resource getResource() {
        return resource;
    }
}
