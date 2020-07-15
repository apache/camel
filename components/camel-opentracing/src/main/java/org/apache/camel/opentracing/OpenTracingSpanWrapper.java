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

import java.util.*;

import io.opentracing.Span;
import io.opentracing.tag.Tags;
import org.apache.camel.tracing.SpanWrap;

/**
 * @author rvargasp
 */
public class OpenTracingSpanWrapper implements SpanWrap {

    Span span;

    OpenTracingSpanWrapper(Span span) {
        this.span = span;
    }

    @Override public void setComponent(String component) {
        span.setTag(Tags.COMPONENT.getKey(), component);
    }

    @Override public void setHttpStatus(Integer responseCode) {
        span.setTag(Tags.HTTP_STATUS.getKey(), responseCode);
    }

    @Override public void setHttpMethod(String method) {
        span.setTag(Tags.HTTP_METHOD.getKey(), method);
    }

    @Override public void setHttpURL(String httpUrl) {
        span.setTag(Tags.HTTP_URL.getKey(), httpUrl);
    }

    @Override public void setMessageBusDestination(String dest) {
        span.setTag(Tags.MESSAGE_BUS_DESTINATION.getKey(), dest);
    }

    @Override public void setError(boolean error) {
        span.setTag(Tags.ERROR.getKey(), error);
    }

    @Override public void setDBType(String type) {
        span.setTag(Tags.DB_TYPE.getKey(), type);
    }

    @Override public void setDBInstance(String instance) {
        span.setTag(Tags.DB_INSTANCE.getKey(), instance);
    }

    @Override public void setDBStatement(String statement) {
        span.setTag(Tags.DB_STATEMENT.getKey(), statement);
    }

    @Override public void setTag(String key, String value) {
        span.setTag(key, value);
    }

    @Override public void setTag(String key, Number value) {
        span.setTag(key, value);
    }

    @Override public void setTag(String key, Boolean value) {
        span.setTag(key, value);
    }

    @Override public void log(Map<String, String> log) {

    }
}
