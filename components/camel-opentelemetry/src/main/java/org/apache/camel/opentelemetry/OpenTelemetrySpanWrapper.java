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
package org.apache.camel.opentelemetry;

import java.util.*;

import io.opentelemetry.common.AttributeValue;
import io.opentelemetry.common.Attributes;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.attributes.SemanticAttributes;
import org.apache.camel.tracing.SpanWrap;

/**
 * @author rvargasp
 */
public class OpenTelemetrySpanWrapper implements SpanWrap {
    private static final String DEFAULT_EVENT_NAME = "log";

    Span span;

    OpenTelemetrySpanWrapper(Span span) {
        this.span = span;
    }

    @Override public void setComponent(String component) {
        this.span.setAttribute("component", component);
    }

    @Override public void setHttpStatus(Integer status) {
        this.span.setAttribute(SemanticAttributes.HTTP_STATUS_CODE.key(), status.longValue());
    }

    @Override public void setHttpMethod(String method) {
        this.span.setAttribute(SemanticAttributes.HTTP_METHOD.key(), method);

    }

    @Override public void setHttpURL(String url) {
        this.span.setAttribute(SemanticAttributes.HTTP_URL.key(), url);
    }

    @Override public void setMessageBusDestination(String dest) {
        this.span.setAttribute("message.destination", dest);
    }

    @Override public void setError(boolean error) {
        this.span.setAttribute("error", error);
    }

    @Override public void setDBType(String type) {
        this.span.setAttribute(SemanticAttributes.DB_TYPE.key(), type);
    }

    @Override public void setDBInstance(String instance) {
        this.span.setAttribute(SemanticAttributes.DB_INSTANCE.key(), instance);
    }

    @Override public void setDBStatement(String statement) {
        this.span.setAttribute(SemanticAttributes.DB_STATEMENT.key(), statement);
    }

    @Override public void setTag(String key, String value) {
        this.span.setAttribute(key, value);
    }

    @Override public void setTag(String key, Number value) {
        this.span.setAttribute(key, value.intValue());
    }

    @Override public void setTag(String key, Boolean value) {
        this.span.setAttribute(key, value);
    }

    @Override public void log(Map<String, String> fields) {
        span.addEvent(getEventNameFromFields(fields), convertToAttributes(fields));

    }
    String getEventNameFromFields(Map<String, ?> fields) {
        Object eventValue = fields == null ? null : fields.get("event");
        if (eventValue != null) {
            return eventValue.toString();
        }

        return DEFAULT_EVENT_NAME;
    }
    Attributes convertToAttributes(Map<String, ?> fields) {
        Attributes.Builder attributesBuilder = Attributes.newBuilder();

        for (Map.Entry<String, ?> entry : fields.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value == null) {
                continue;
            }
            if (value instanceof Byte
                    || value instanceof Short
                    || value instanceof Integer
                    || value instanceof Long) {
                attributesBuilder.setAttribute(
                        key, AttributeValue.longAttributeValue(((Number) value).longValue()));
            } else if (value instanceof Float || value instanceof Double) {
                attributesBuilder.setAttribute(
                        key, AttributeValue.doubleAttributeValue(((Number) value).doubleValue()));
            } else if (value instanceof Boolean) {
                attributesBuilder.setAttribute(key, AttributeValue.booleanAttributeValue((Boolean) value));
            } else {
                attributesBuilder.setAttribute(key, AttributeValue.stringAttributeValue(value.toString()));
            }
        }
        return attributesBuilder.build();
    }
}
