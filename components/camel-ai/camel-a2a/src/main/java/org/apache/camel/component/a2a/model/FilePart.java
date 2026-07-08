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
package org.apache.camel.component.a2a.model;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A file content part per A2A v1.0. Exactly one of {@code raw} (base64-encoded bytes) or {@code url} must be set.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FilePart(
        String raw,
        String url,
        String mediaType,
        String filename,
        Map<String, Object> metadata) implements Part<FilePart> {
    public FilePart {
        if ((raw == null || raw.isEmpty()) == (url == null || url.isEmpty())) {
            throw new IllegalArgumentException("FilePart must contain exactly one of raw or url");
        }
    }

    public static FilePart ofBytes(String raw, String mediaType, String filename) {
        return new FilePart(raw, null, mediaType, filename, null);
    }

    public static FilePart ofUrl(String url, String mediaType, String filename) {
        return new FilePart(null, url, mediaType, filename, null);
    }

    @JsonIgnore
    public String kind() {
        return "file";
    }
}
