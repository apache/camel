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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** An expert-authored finding supplementary to the returned value. Severity has no Camel-defined scale. */
public final class SemanticFinding {
    private final String code;
    private final String meaning;
    private final String source;
    private final String severity;
    private final String severityScale;

    public SemanticFinding(String code, String meaning, String source, String severity, String severityScale) {
        this.code = Objects.requireNonNull(code);
        this.meaning = Objects.requireNonNull(meaning);
        this.source = Objects.requireNonNull(source);
        if ((severity == null) != (severityScale == null)) {
            throw new IllegalArgumentException("A reported severity requires its source-defined scale");
        }
        this.severity = severity;
        this.severityScale = severityScale;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("code", code);
        fields.put("meaning", meaning);
        fields.put("source", source);
        if (severity != null) {
            fields.put("severity", Map.of("value", severity, "scale", severityScale, "source", source));
        }
        return Map.copyOf(fields);
    }
}
