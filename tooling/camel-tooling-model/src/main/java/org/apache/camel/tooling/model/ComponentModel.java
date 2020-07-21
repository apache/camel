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
package org.apache.camel.tooling.model;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ComponentModel extends ArtifactModel<ComponentModel.ComponentOptionModel> {

    protected String scheme;
    protected String extendsScheme;
    protected String alternativeSchemes;
    protected String syntax;
    protected String alternativeSyntax;
    protected boolean async;
    protected boolean consumerOnly;
    protected boolean producerOnly;
    protected boolean lenientProperties;
    protected String verifiers;
    protected final List<EndpointOptionModel> endpointOptions = new ArrayList<>();

    public ComponentModel() {
    }

    @Override
    public String getKind() {
        return "component";
    }

    public String getScheme() {
        return scheme;
    }

    public void setScheme(String scheme) {
        this.scheme = scheme;
    }

    public String getExtendsScheme() {
        return extendsScheme;
    }

    public void setExtendsScheme(String extendsScheme) {
        this.extendsScheme = extendsScheme;
    }

    public String getAlternativeSchemes() {
        return alternativeSchemes;
    }

    public void setAlternativeSchemes(String alternativeSchemes) {
        this.alternativeSchemes = alternativeSchemes;
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

    public boolean isAsync() {
        return async;
    }

    public void setAsync(boolean async) {
        this.async = async;
    }

    public boolean isConsumerOnly() {
        return consumerOnly;
    }

    public void setConsumerOnly(boolean consumerOnly) {
        this.consumerOnly = consumerOnly;
    }

    public boolean isProducerOnly() {
        return producerOnly;
    }

    public void setProducerOnly(boolean producerOnly) {
        this.producerOnly = producerOnly;
    }

    public boolean isLenientProperties() {
        return lenientProperties;
    }

    public void setLenientProperties(boolean lenientProperties) {
        this.lenientProperties = lenientProperties;
    }

    public String getVerifiers() {
        return verifiers;
    }

    public void setVerifiers(String verifiers) {
        this.verifiers = verifiers;
    }

    public List<ComponentOptionModel> getComponentOptions() {
        return super.getOptions();
    }

    public void addComponentOption(ComponentOptionModel option) {
        super.addOption(option);
    }

    public List<EndpointOptionModel> getEndpointOptions() {
        return endpointOptions;
    }

    public void addEndpointOption(EndpointOptionModel option) {
        endpointOptions.add(option);
    }

    public List<EndpointOptionModel> getEndpointParameterOptions() {
        return endpointOptions.stream()
                .filter(o -> "parameter".equals(o.getKind()))
                .collect(Collectors.toList());
    }

    public List<EndpointOptionModel> getEndpointPathOptions() {
        return endpointOptions.stream()
                .filter(o -> "path".equals(o.getKind()))
                .collect(Collectors.toList());
    }

    public static class ComponentOptionModel extends BaseOptionModel {

    }

    public static class EndpointOptionModel extends BaseOptionModel {

    }
}
