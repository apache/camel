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
package org.apache.camel.maven.packaging.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.camel.tooling.util.JSonSchemaHelper;
import org.apache.camel.tooling.util.Strings;

import static org.apache.camel.tooling.util.JSonSchemaHelper.getSafeValue;
import static org.apache.camel.tooling.util.Strings.cutLastZeroDigit;

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
    private String deprecationNote;
    private String consumerOnly;
    private String producerOnly;
    private String javaType;
    private String groupId;
    private String artifactId;
    private String version;
    private List<ComponentOptionModel> componentOptions = new ArrayList<>();
    private List<EndpointOptionModel> endpointPathOptions = new ArrayList<>();
    private List<EndpointOptionModel> endpointOptions = new ArrayList<>();

    public ComponentModel() {}

    public ComponentModel(String kind, String scheme, String syntax, String alternativeSyntax, String alternativeSchemes,
                          String title, String description, String firstVersion, String label, String deprecated, String deprecationNote,
                          String consumerOnly, String producerOnly, String javaType, String groupId, String artifactId, String version, List<ComponentOptionModel> componentOptions,
                          List<EndpointOptionModel> endpointPathOptions, List<EndpointOptionModel> endpointOptions) {
        this.kind = kind;
        this.scheme = scheme;
        this.syntax = syntax;
        this.alternativeSyntax = alternativeSyntax;
        this.alternativeSchemes = alternativeSchemes;
        this.title = title;
        this.description = description;
        this.firstVersion = firstVersion;
        this.label = label;
        this.deprecated = deprecated;
        this.deprecationNote = deprecationNote;
        this.consumerOnly = consumerOnly;
        this.producerOnly = producerOnly;
        this.javaType = javaType;
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.componentOptions = componentOptions;
        this.endpointPathOptions = endpointPathOptions;
        this.endpointOptions = endpointOptions;
    }

    public static ComponentModel generateComponentModelFromJsonString(final String json) {
        List<Map<String, String>> rows = JSonSchemaHelper.parseJsonSchema("component", json, false);
        final String kind = getSafeValue("kind", rows);
        final String schema = getSafeValue("scheme", rows);
        final String syntax = getSafeValue("syntax", rows);
        final String alternativeSyntax = getSafeValue("alternativeSyntax", rows);
        final String alternativeSchemes = getSafeValue("alternativeSchemes", rows);
        final String title = getSafeValue("title", rows);
        final String description = getSafeValue("description", rows);
        final String firstVersion = getSafeValue("firstVersion", rows);
        final String label = getSafeValue("label", rows);
        final String deprecated = getSafeValue("deprecated", rows);
        final String deprecationNote = getSafeValue("deprecationNote", rows);
        final String consumerOnly = getSafeValue("consumerOnly", rows);
        final String producerOnly = getSafeValue("producerOnly", rows);
        final String javaType = getSafeValue("javaType", rows);
        final String groupId = getSafeValue("groupId", rows);
        final String artifactId = getSafeValue("artifactId", rows);
        final String version = getSafeValue("version", rows);

        final List<ComponentOptionModel> componentOptions = ComponentOptionModel.generateComponentOptionsFromJsonString(json);
        final List<EndpointOptionModel> endpointPathOptions = Collections.emptyList(); // we need to fix this
        final List<EndpointOptionModel> endpointOptions = EndpointOptionModel.generateEndpointOptionsFromJsonString(json);

        return new ComponentModel(kind, schema, syntax, alternativeSyntax, alternativeSchemes, title, description, firstVersion, label, deprecated, deprecationNote, consumerOnly, producerOnly, javaType,
                groupId, artifactId, version, componentOptions, endpointPathOptions, endpointOptions);
    }

    private static String castValueToStringFromMap(final Map<String, Object> input, final String key) {
        return input.get(key).toString();
    }

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

    public boolean isDeprecated() {
        return "true".equals(deprecated);
    }

    public String getDeprecated() {
        return deprecated;
    }

    public void setDeprecated(String deprecated) {
        this.deprecated = deprecated;
    }

    public String getDeprecationNote() {
        return deprecationNote;
    }

    public void setDeprecationNote(String deprecationNote) {
        this.deprecationNote = deprecationNote;
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

    public List<EndpointOptionModel> getEndpointPathOptions() {
        return endpointPathOptions;
    }

    public void addEndpointOption(EndpointOptionModel option) {
        endpointOptions.add(option);
    }

    public void addEndpointPathOption(EndpointOptionModel option) {
        endpointPathOptions.add(option);
    }

    public void setComponentOptions(List<ComponentOptionModel> componentOptions) {
        this.componentOptions = componentOptions;
    }

    public void setEndpointPathOptions(List<EndpointOptionModel> endpointPathOptions) {
        this.endpointPathOptions = endpointPathOptions;
    }

    public void setEndpointOptions(List<EndpointOptionModel> endpointOptions) {
        this.endpointOptions = endpointOptions;
    }

    public String getShortJavaType() {
        return Strings.getClassShortName(javaType);
    }

    public String getFirstVersionShort() {
        return cutLastZeroDigit(firstVersion);
    }
}
