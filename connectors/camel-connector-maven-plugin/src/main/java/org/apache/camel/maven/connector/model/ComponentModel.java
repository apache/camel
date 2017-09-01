/**
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
package org.apache.camel.maven.connector.model;

import java.util.ArrayList;
import java.util.List;

public class ComponentModel {

    private String kind;
    private String scheme;
    private String syntax;
    private String alternativeSyntax;
    private String alternativeSchemes;
    private String title;
    private String description;
    private String firstVersion;
    private String label;
    private String deprecated;
    private String consumerOnly;
    private String producerOnly;
    private String javaType;
    private String groupId;
    private String artifactId;
    private String version;
    private final List<ComponentOptionModel> componentOptions = new ArrayList<>();
    private final List<EndpointOptionModel> endpointOptions = new ArrayList<>();
    private final List<ConnectorOptionModel> connectorOptions = new ArrayList<>();

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getScheme() {
        return scheme;
    }

    public void setScheme(String scheme) {
        this.scheme = scheme;
    }

    public String getSyntax() {
        return syntax;
    }

    public void setSyntax(String syntax) {
        this.syntax = syntax;
    }

    public String getAlternativeSyntax() {
        return alternativeSyntax;
    }

    public void setAlternativeSyntax(String alternativeSyntax) {
        this.alternativeSyntax = alternativeSyntax;
    }

    public String getAlternativeSchemes() {
        return alternativeSchemes;
    }

    public void setAlternativeSchemes(String alternativeSchemes) {
        this.alternativeSchemes = alternativeSchemes;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getFirstVersion() {
        return firstVersion;
    }

    public void setFirstVersion(String firstVersion) {
        this.firstVersion = firstVersion;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getDeprecated() {
        return deprecated;
    }

    public void setDeprecated(String deprecated) {
        this.deprecated = deprecated;
    }

    public String getConsumerOnly() {
        return consumerOnly;
    }

    public void setConsumerOnly(String consumerOnly) {
        this.consumerOnly = consumerOnly;
    }

    public String getProducerOnly() {
        return producerOnly;
    }

    public void setProducerOnly(String producerOnly) {
        this.producerOnly = producerOnly;
    }

    public String getJavaType() {
        return javaType;
    }

    public void setJavaType(String javaType) {
        this.javaType = javaType;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public List<ComponentOptionModel> getComponentOptions() {
        return componentOptions;
    }

    public void addComponentOption(ComponentOptionModel option) {
        componentOptions.add(option);
    }

    public List<EndpointOptionModel> getEndpointOptions() {
        return endpointOptions;
    }

    public void addEndpointOption(EndpointOptionModel option) {
        endpointOptions.add(option);
    }

    public List<ConnectorOptionModel> getConnectorOptions() {
        return connectorOptions;
    }

    public void addConnectorOption(ConnectorOptionModel option) {
        connectorOptions.add(option);
    }

    public String getShortJavaType() {
        if (javaType.startsWith("java.util.Map")) {
            return "Map";
        } else if (javaType.startsWith("java.util.Set")) {
            return "Set";
        } else if (javaType.startsWith("java.util.List")) {
            return "List";
        }
        int pos = javaType.lastIndexOf(".");
        if (pos != -1) {
            return javaType.substring(pos + 1);
        } else {
            return javaType;
        }
    }

}
