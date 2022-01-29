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
package org.apache.camel.opentracing;

import java.util.EnumMap;
import java.util.Map;

import io.opentracing.tag.AbstractTag;
import io.opentracing.tag.Tags;
import org.apache.camel.tracing.SpanAdapter;
import org.apache.camel.tracing.Tag;

@Deprecated
public class OpenTracingSpanAdapter implements SpanAdapter {

    static EnumMap<Tag, AbstractTag> tagMap = new EnumMap<>(Tag.class);

    static {
        tagMap.put(Tag.COMPONENT, Tags.COMPONENT);
        tagMap.put(Tag.DB_TYPE, Tags.DB_TYPE);
        tagMap.put(Tag.DB_STATEMENT, Tags.DB_STATEMENT);
        tagMap.put(Tag.DB_INSTANCE, Tags.DB_INSTANCE);
        tagMap.put(Tag.HTTP_METHOD, Tags.HTTP_METHOD);
        tagMap.put(Tag.HTTP_STATUS, Tags.HTTP_STATUS);
        tagMap.put(Tag.HTTP_URL, Tags.HTTP_URL);
        tagMap.put(Tag.ERROR, Tags.ERROR);
        tagMap.put(Tag.MESSAGE_BUS_DESTINATION, Tags.MESSAGE_BUS_DESTINATION);
    }

    private io.opentracing.Span span;

    OpenTracingSpanAdapter(io.opentracing.Span span) {
        this.span = span;
    }

    public io.opentracing.Span getOpenTracingSpan() {
        return this.span;
    }

    @Override
    public void setComponent(String component) {
        span.setTag(Tags.COMPONENT.getKey(), component);
    }

    @Override
    public void setError(boolean error) {
        span.setTag(Tags.ERROR.getKey(), error);
    }

    @Override
    public void setTag(Tag key, String value) {
        span.setTag(tagMap.get(key).getKey(), value);
    }

    @Override
    public void setTag(Tag key, Number value) {
        span.setTag(tagMap.get(key).getKey(), value);
    }

    @Override
    public void setTag(String key, String value) {
        span.setTag(key, value);
    }

    @Override
    public void setTag(String key, Number value) {
        span.setTag(key, value);
    }

    @Override
    public void setTag(String key, Boolean value) {
        span.setTag(key, value);
    }

    @Override
    public void log(Map<String, String> fields) {
        this.span.log(fields);
    }

    @Override
    public String traceId() {
        return this.span.context().toTraceId();
    }

    @Override
    public String spanId() {
        return this.span.context().toSpanId();
    }

}
