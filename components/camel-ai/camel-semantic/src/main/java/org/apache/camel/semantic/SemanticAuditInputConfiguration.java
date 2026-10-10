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
package org.apache.camel.semantic;

import java.util.Objects;

/** Per-expert opt-in to retain the selected evaluation input, independently of telemetry. */
public final class SemanticAuditInputConfiguration {
    public static final int DEFAULT_MAX_CHARS = 4096;
    public static final int MAX_CHARS = 100000;
    public static final SemanticAuditInputConfiguration DISABLED
            = new SemanticAuditInputConfiguration(false, DEFAULT_MAX_CHARS, null);
    private final boolean enabled;
    private final int maxChars;
    private final String redactor;

    public SemanticAuditInputConfiguration(boolean enabled, int maxChars, String redactor) {
        if (maxChars < 1 || maxChars > MAX_CHARS || redactor != null && redactor.isBlank()) {
            throw new IllegalArgumentException(
                    "Audit input requires maxChars between 1 and 100000 and a nonblank redactor reference");
        }
        this.enabled = enabled;
        this.maxChars = maxChars;
        this.redactor = redactor;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** Total code points in strings, map keys and scalar representations; oversized input is omitted, not truncated. */
    public int getMaxChars() {
        return maxChars;
    }

    /** Optional registry name of a {@link SemanticAuditInputRedactor}. */
    public String getRedactor() {
        return redactor;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SemanticAuditInputConfiguration c && enabled == c.enabled && maxChars == c.maxChars
                && Objects.equals(redactor, c.redactor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, maxChars, redactor);
    }
}
