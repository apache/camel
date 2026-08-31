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

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

/**
 * Configuration for the {@link AiResourceComponent}: the resource uri, its description, MIME type and tags.
 *
 * @since 4.23
 */
@Configurer
@UriParams
public class AiResourceConfiguration implements Cloneable {

    @Metadata(label = "consumer", required = true)
    @UriParam(description = "The resource uri clients use to read this resource, for example "
                            + "camel:///config/app.json or s3://reports/latest.pdf. It must be unique within the "
                            + "CamelContext and is what an MCP client sends in a resources/read request.")
    private String resourceUri;

    @Metadata(label = "consumer")
    @UriParam(description = "Comma-separated list of tags used to group resources. Adapters filter the registry by "
                            + "these tags to select which resources to expose. When omitted, the resource goes into a "
                            + "default pool which camel-mcp-server never exposes.")
    private String tags;

    @Metadata(label = "consumer")
    @UriParam(description = "Human-readable description of what this resource contains. Passed verbatim to the "
                            + "client. When omitted, defaults to the resource name.")
    private String description;

    @Metadata(label = "consumer", defaultValue = AiResource.DEFAULT_MIME_TYPE)
    @UriParam(defaultValue = AiResource.DEFAULT_MIME_TYPE,
              description = "MIME type of the content the route produces. Types denoting text, such as any text subtype, "
                            + "application/json, and any subtype ending in json, xml or yaml, are read as a string. "
                            + "Every other type is read as raw bytes and delivered to clients as a binary blob.")
    private String mimeType = AiResource.DEFAULT_MIME_TYPE;

    @Metadata(label = "consumer")
    @UriParam(description = "Optional display title for resource listings. Advisory hint for clients only.")
    private String title;

    public AiResourceConfiguration() {
    }

    public String getResourceUri() {
        return resourceUri;
    }

    public void setResourceUri(String resourceUri) {
        this.resourceUri = resourceUri;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public AiResourceConfiguration copy() {
        try {
            return (AiResourceConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
