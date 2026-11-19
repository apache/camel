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
package org.apache.camel.telemetry;

import java.util.Map;

/**
 * This interface represent the Span model used along the tracing component. Each technology has to provide the
 * implementation specific details.
 */
public interface Span {

    void log(Map<String, String> fields);

    void setTag(String key, String value);

    /**
     * Sets a numeric tag. Implementations that support typed attributes keep the number; the default stores its text.
     *
     * @since 4.24
     */
    default void setTag(String key, long value) {
        setTag(key, Long.toString(value));
    }

    /**
     * Sets a decimal tag. Implementations that support typed attributes keep the number; the default stores its text.
     *
     * @since 4.24
     */
    default void setTag(String key, double value) {
        setTag(key, Double.toString(value));
    }

    void setComponent(String component);

    void setError(boolean isError);

    /**
     * Makes this span the current one on the calling thread, so that spans started by libraries during a synchronous
     * call (an HTTP client, an AI framework) become its children. The returned scope must be closed on the same thread.
     * The default does nothing.
     *
     * @since 4.24
     */
    default Scope openScope() {
        return () -> {
        };
    }

    /**
     * The scope opened by {@link #openScope()}.
     *
     * @since 4.24
     */
    interface Scope extends AutoCloseable {
        @Override
        void close();
    }

}
