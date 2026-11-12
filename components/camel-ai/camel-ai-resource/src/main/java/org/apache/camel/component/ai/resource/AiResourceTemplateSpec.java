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

import java.util.Map;
import java.util.Optional;

import org.apache.camel.support.DefaultConsumer;

/**
 * Framework-agnostic description of a Camel route registered as a templated AI resource.
 *
 * @since 4.24
 */
public final class AiResourceTemplateSpec {

    private final String name;
    private final String uriTemplate;
    private final AiResourceUriTemplate matcher;
    private final String description;
    private final String mimeType;
    private final String title;
    private final DefaultConsumer consumer;

    public AiResourceTemplateSpec(String name, String uriTemplate, String description, String mimeType, String title,
                                  DefaultConsumer consumer) {
        this.name = name;
        this.uriTemplate = uriTemplate;
        this.matcher = new AiResourceUriTemplate(uriTemplate);
        this.description = description;
        this.mimeType = mimeType;
        this.title = title;
        this.consumer = consumer;
    }

    public String getName() {
        return name;
    }

    public String getUriTemplate() {
        return uriTemplate;
    }

    public String getDescription() {
        return description;
    }

    public String getMimeType() {
        return mimeType;
    }

    public String getTitle() {
        return title;
    }

    public DefaultConsumer getConsumer() {
        return consumer;
    }

    public Optional<Map<String, String>> match(String resourceUri) {
        return matcher.match(resourceUri);
    }

    public boolean overlaps(AiResourceTemplateSpec other) {
        return other != null && matcher.overlaps(other.matcher);
    }

    /**
     * Materializes this template as the fixed resource specification for one matched URI. This lets adapters reuse
     * {@link AiResourceExecutor}'s existing MIME conversion and route execution path without introducing MCP types into
     * this component.
     */
    public AiResourceSpec asResourceSpec(String resourceUri) {
        if (match(resourceUri).isEmpty()) {
            throw new IllegalArgumentException("URI does not match resource template: " + resourceUri);
        }
        return new AiResourceSpec(name, resourceUri, description, mimeType, title, consumer);
    }

    @Override
    public String toString() {
        return "AiResourceTemplateSpec{uriTemplate=" + uriTemplate + '}';
    }
}
