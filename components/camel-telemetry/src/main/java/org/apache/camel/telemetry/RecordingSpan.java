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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A {@link Span} wrapper that records all {@code setTag} and {@code setComponent} calls into a {@link Map} while
 * delegating every operation to the underlying span. This is used to capture span decorator attributes for the
 * BacklogTracer activity enrichment without running decorator logic twice.
 * <p>
 * The recording is only activated when the BacklogTracer activity feature is enabled, so in production there is no
 * wrapping overhead.
 */
class RecordingSpan implements Span {

    private final Span delegate;
    private final Map<String, String> tags = new LinkedHashMap<>();

    RecordingSpan(Span delegate) {
        this.delegate = delegate;
    }

    @Override
    public void setTag(String key, String value) {
        delegate.setTag(key, value);
        if (value != null) {
            tags.put(key, value);
        }
    }

    @Override
    public void setComponent(String component) {
        delegate.setComponent(component);
        if (component != null) {
            tags.put("component", component);
        }
    }

    @Override
    public void setError(boolean isError) {
        delegate.setError(isError);
    }

    @Override
    public void log(Map<String, String> fields) {
        delegate.log(fields);
    }

    /**
     * Returns the real span that this wrapper delegates to.
     */
    Span getDelegate() {
        return delegate;
    }

    /**
     * Returns the recorded tags as an unmodifiable snapshot.
     */
    Map<String, String> getRecordedTags() {
        return Map.copyOf(tags);
    }
}
