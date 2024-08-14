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
package org.apache.camel.tracing;

import java.util.Map;

/**
 * An adapter to allow Camel to interact with different tracing technologies.
 */
public interface SpanAdapter {

    /**
     * Sets the operation name of the span.
     *
     * @param component the component name.
     */
    void setComponent(String component);

    /**
     * Sets this span as an error span.
     *
     * @param error true if this span is an error span.
     */
    void setError(boolean error);

    /**
     * @deprecated use {@link #setTag(String, String)} instead.
     */
    @Deprecated
    default void setTag(Tag key, String value) {
        setTag(key.getAttribute(), value);
    }

    /**
     * @deprecated use {@link #setTag(String, Number)} instead.
     */
    @Deprecated
    default void setTag(Tag key, Number value) {
        setTag(key.getAttribute(), value);
    }

    /**
     * Sets a tag on the span.
     *
     * @param key   the tag key
     * @param value the tag value
     */
    void setTag(String key, String value);

    /**
     * Sets a tag on the span.
     *
     * @param key   the tag key
     * @param value the tag value
     */
    void setTag(String key, Number value);

    /**
     * Sets a tag on the span.
     *
     * @param key   the tag key
     * @param value the tag value
     */
    void setTag(String key, Boolean value);

    /**
     * @deprecated use {@link #setLowCardinalityTag(String, String)} instead.
     */
    @Deprecated
    default void setLowCardinalityTag(Tag key, String value) {
        setLowCardinalityTag(key.getAttribute(), value);
    }

    /**
     * @deprecated use {@link #setLowCardinalityTag(String, Number)} instead.
     */
    @Deprecated
    default void setLowCardinalityTag(Tag key, Number value) {
        setLowCardinalityTag(key.getAttribute(), value);
    }

    /**
     * @deprecated use {@link #setLowCardinalityTag(String, Boolean)} instead.
     */
    @Deprecated
    default void setLowCardinalityTag(Tag key, Boolean value) {
        setLowCardinalityTag(key.getAttribute(), value);
    }

    /**
     * Sets a low cardinality tag on the span.
     *
     * @param key   the tag key
     * @param value the tag value
     */
    default void setLowCardinalityTag(String key, String value) {
        setTag(key, value);
    }

    /**
     * Sets a low cardinality tag on the span.
     *
     * @param key   the tag key
     * @param value the tag value
     */
    default void setLowCardinalityTag(String key, Number value) {
        setTag(key, value);
    }

    /**
     * Sets a low cardinality tag on the span.
     *
     * @param key   the tag key
     * @param value the tag value
     */
    default void setLowCardinalityTag(String key, Boolean value) {
        setTag(key, value);
    }

    /**
     * Add log messages to a span.
     *
     * @param log the log messages
     */
    void log(Map<String, String> log);

    /**
     * Get the current trace id.
     */
    String traceId();

    /**
     * Get the current span id.
     */
    String spanId();

    /**
     * Makes the current span the active span.
     */
    default AutoCloseable makeCurrent() {
        return () -> {
        };
    }
}
