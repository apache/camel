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
package org.apache.camel.component.knative.spi;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.camel.spi.Configurer;

@Configurer
public final class KnativeResource {
    private String name;
    private String url;
    private Knative.Type type;
    private Knative.EndpointKind endpointKind;
    private Boolean reply;
    private String contentType;
    private String cloudEventType;
    private String path;
    private String objectApiVersion;
    private String objectKind;
    private String objectName;
    private Map<String, String> metadata;
    private Map<String, String> ceOverrides;
    private Map<String, String> filters;

    public String getName() {
        return this.name;
    }

    @JsonProperty(required = true)
    public void setName(String name) {
        this.name = name;
    }

    @JsonProperty(required = true)
    public Knative.Type getType() {
        Knative.Type answer = this.type;
        if (answer == null) {
            String stringValue = getMetadata(Knative.KNATIVE_TYPE);
            if (stringValue != null) {
                answer = Knative.Type.valueOf(stringValue);
            }
        }

        return answer;
    }

    public void setType(Knative.Type type) {
        this.type = type;
    }

    public Knative.EndpointKind getEndpointKind() {
        Knative.EndpointKind answer = this.endpointKind;
        if (answer == null) {
            String stringValue = getMetadata(Knative.CAMEL_ENDPOINT_KIND);
            if (stringValue != null) {
                answer = Knative.EndpointKind.valueOf(stringValue);
            }
        }

        return answer;
    }

    public void setEndpointKind(Knative.EndpointKind endpointKind) {
        this.endpointKind = endpointKind;
    }

    public String getUrl() {
        return this.url != null ? this.url : getMetadata(Knative.SERVICE_META_URL);
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Map<String, String> getMetadata() {
        if (this.metadata == null) {
            this.metadata = new HashMap<>();
        }

        return this.metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    @JsonIgnore
    public String getMetadata(String key) {
        return this.metadata != null ? metadata.get(key) : null;
    }

    public void setMetadata(String key, String value) {
        if (this.metadata == null) {
            this.metadata = new HashMap<>();
        }

        this.metadata.put(key, value);
    }

    @JsonIgnore
    public Optional<String> getOptionalMetadata(String key) {
        return Optional.ofNullable(getMetadata(key));
    }

    public String getCloudEventType() {
        return this.cloudEventType != null
                ? this.cloudEventType
                : getMetadata(Knative.KNATIVE_CLOUD_EVENT_TYPE);
    }

    public void setCloudEventType(String cloudEventType) {
        this.cloudEventType = cloudEventType;
    }

    public String getPath() {
        return this.path != null
                ? this.path
                : getMetadata(Knative.SERVICE_META_PATH);
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getObjectApiVersion() {
        return this.objectApiVersion != null
                ? this.objectApiVersion
                : getMetadata(Knative.KNATIVE_OBJECT_API_VERSION);
    }

    public void setObjectApiVersion(String objectApiVersion) {
        this.objectApiVersion = objectApiVersion;
    }

    public String getObjectKind() {
        return this.objectKind != null
                ? this.objectKind
                : getMetadata(Knative.KNATIVE_OBJECT_KIND);
    }

    public void setObjectKind(String objectKind) {
        this.objectKind = objectKind;
    }

    public String getObjectName() {
        return this.objectName != null
                ? this.objectName
                : getMetadata(Knative.KNATIVE_OBJECT_NAME);
    }

    public void setObjectName(String objectName) {
        this.objectName = objectName;
    }

    public Map<String, String> getCeOverrides() {
        Map<String, String> answer = new HashMap<>();
        if (this.ceOverrides != null) {
            answer.putAll(this.ceOverrides);
        }
        if (this.metadata != null) {
            for (Map.Entry<String, String> entry : this.metadata.entrySet()) {
                if (entry.getKey().startsWith(Knative.KNATIVE_CE_OVERRIDE_PREFIX)) {
                    final String key = entry.getKey().substring(Knative.KNATIVE_CE_OVERRIDE_PREFIX.length());
                    final String val = entry.getValue();

                    answer.put(key, val);
                }
            }
        }

        return answer;
    }

    public void setCeOverrides(Map<String, String> ceOverride) {
        this.ceOverrides = ceOverride;
    }

    public void addCeOverride(String key, String value) {
        if (this.ceOverrides == null) {
            this.ceOverrides = new HashMap<>();
        }

        this.ceOverrides.put(key, value);
    }

    public Map<String, String> getFilters() {
        Map<String, String> answer = new HashMap<>();
        if (this.filters != null) {
            answer.putAll(this.filters);
        }
        if (this.metadata != null) {
            for (Map.Entry<String, String> entry : this.metadata.entrySet()) {
                if (entry.getKey().startsWith(Knative.KNATIVE_FILTER_PREFIX)) {
                    final String key = entry.getKey().substring(Knative.KNATIVE_FILTER_PREFIX.length());
                    final String val = entry.getValue();

                    answer.put(key, val);
                }
            }
        }

        return answer;
    }

    public void setFilters(Map<String, String> filters) {
        this.filters = filters;
    }

    public void addFilter(String key, String value) {
        if (this.filters == null) {
            this.filters = new HashMap<>();
        }

        this.filters.put(key, value);
    }

    public Boolean getReply() {
        return this.reply != null
                ? this.reply
                : getOptionalMetadata(Knative.KNATIVE_REPLY).map(Boolean::parseBoolean).orElse(true);
    }

    public void setReply(Boolean reply) {
        this.reply = reply;
    }

    public String getContentType() {
        return this.contentType != null
                ? this.contentType
                : getMetadata(Knative.CONTENT_TYPE);
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public boolean matches(Knative.Type type, String name) {
        if (type == null || name == null) {
            return false;
        }

        return Objects.equals(type, getType())
                && Objects.equals(name, getName());
    }

    @Override
    public String toString() {
        return "KnativeResource{" +
               "name='" + name + '\'' +
               ", url='" + url + '\'' +
               ", metadata=" + metadata +
               ", ceOverrides=" + ceOverrides +
               ", filters=" + filters +
               ", type=" + type +
               ", endpointKind=" + endpointKind +
               ", reply=" + reply +
               ", contentType='" + contentType + '\'' +
               '}';
    }

    public static KnativeResource from(KnativeResource resource) {
        KnativeResource answer = new KnativeResource();

        answer.name = resource.name;
        answer.url = resource.url;
        answer.type = resource.type;
        answer.endpointKind = resource.endpointKind;
        answer.reply = resource.reply;
        answer.contentType = resource.contentType;
        answer.cloudEventType = resource.cloudEventType;
        answer.path = resource.path;

        if (resource.metadata != null) {
            answer.metadata = new HashMap<>(resource.metadata);
        }
        if (resource.ceOverrides != null) {
            answer.ceOverrides = new HashMap<>(resource.ceOverrides);
        }
        if (resource.filters != null) {
            answer.filters = new HashMap<>(resource.filters);
        }

        return answer;
    }
}
