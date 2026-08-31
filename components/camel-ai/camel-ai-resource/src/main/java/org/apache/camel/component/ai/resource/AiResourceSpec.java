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
package org.apache.camel.component.ai.resource;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import org.apache.camel.support.DefaultConsumer;

/**
 * Framework-agnostic description of a Camel route registered as a read-only AI resource.
 * <p>
 * A resource is addressed by its {@link #getUri() URI}, which is what an MCP client sends in a {@code resources/read}
 * request; the {@link #getName() name} is the human-readable label shown in listings.
 *
 * @since 4.23
 */
public final class AiResourceSpec {

    private static final Set<String> TEXTUAL_MIME_TYPES = Set.of(
            "application/json", "application/xml", "application/yaml", "application/x-yaml",
            "application/javascript", "application/ecmascript", "application/sql",
            "application/graphql", "application/x-www-form-urlencoded");

    private final String name;
    private final String uri;
    private final String description;
    private final String mimeType;
    private final String title;
    private final DefaultConsumer consumer;

    public AiResourceSpec(String name, String uri, String description, String mimeType, String title,
                          DefaultConsumer consumer) {
        this.name = name;
        this.uri = uri;
        this.description = description;
        this.mimeType = mimeType;
        this.title = title;
        this.consumer = consumer;
    }

    public String getName() {
        return name;
    }

    /**
     * The resource URI clients use to read this resource.
     */
    public String getUri() {
        return uri;
    }

    public String getDescription() {
        return description;
    }

    /**
     * The MIME type of the content this resource produces.
     */
    public String getMimeType() {
        return mimeType;
    }

    /**
     * Whether the {@link #getMimeType() MIME type} denotes text. Textual resources are read as a string; everything
     * else is read as raw bytes and delivered to clients as a binary blob.
     */
    public boolean isTextual() {
        return isTextualMimeType(mimeType);
    }

    /**
     * Optional display title for resource listings, or {@code null} when none is configured.
     */
    public String getTitle() {
        return title;
    }

    /**
     * The Camel consumer whose route produces this resource's content when a client reads it.
     */
    public DefaultConsumer getConsumer() {
        return consumer;
    }

    static boolean isTextualMimeType(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            return true;
        }
        String type = mimeType.toLowerCase(Locale.ROOT).trim();
        int separator = type.indexOf(';');
        if (separator > 0) {
            type = type.substring(0, separator).trim();
        }
        if (type.startsWith("text/")) {
            return true;
        }
        // structured syntax suffixes: application/vnd.api+json, image/svg+xml, ...
        if (type.endsWith("+json") || type.endsWith("+xml") || type.endsWith("+yaml")) {
            return true;
        }
        return TEXTUAL_MIME_TYPES.contains(type);
    }

    // Consumer is included so that two endpoints declaring the same resource URI but different configurations
    // are treated as distinct specs in the registry sets.
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AiResourceSpec that = (AiResourceSpec) o;
        return Objects.equals(name, that.name)
                && Objects.equals(uri, that.uri)
                && Objects.equals(description, that.description)
                && Objects.equals(mimeType, that.mimeType)
                && Objects.equals(title, that.title)
                && Objects.equals(consumer, that.consumer);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, uri, description, mimeType, title, consumer);
    }

    @Override
    public String toString() {
        return "AiResourceSpec{uri=" + uri + '}';
    }
}
