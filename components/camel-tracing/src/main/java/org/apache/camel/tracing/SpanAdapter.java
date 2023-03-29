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

public interface SpanAdapter {
    void setComponent(String component);

    void setError(boolean error);

    void setTag(Tag key, String value);

    void setTag(Tag key, Number value);

    void setTag(String key, String value);

    void setTag(String key, Number value);

    void setTag(String key, Boolean value);

    default void setLowCardinalityTag(Tag key, String value) {
        setTag(key, value);
    }

    default void setLowCardinalityTag(Tag key, Number value) {
        setTag(key, value);
    }

    default void setLowCardinalityTag(String key, String value) {
        setTag(key, value);
    }

    default void setLowCardinalityTag(String key, Number value) {
        setTag(key, value);
    }

    default void setLowCardinalityTag(String key, Boolean value) {
        setTag(key, value);
    }

    void log(Map<String, String> log);

    String traceId();

    String spanId();

    default AutoCloseable makeCurrent() {
        return Tracer.NOOP_CLOSEABLE;
    }
}
