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
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class ComponentModel extends ArtifactModel<ComponentModel.ComponentOptionModel> {

    protected String scheme;
    protected String extendsScheme;
    protected String alternativeSchemes;
    protected String syntax;
    protected String alternativeSyntax;
    protected boolean async;
    protected boolean api;
    protected String apiSyntax;
    protected boolean consumerOnly;
    protected boolean producerOnly;
    protected boolean lenientProperties;
    protected boolean remote;
    protected String verifiers;
    protected final List<EndpointOptionModel> endpointOptions = new ArrayList<>();
    protected final List<EndpointHeaderModel> headers = new ArrayList<>();
    // lets sort apis A..Z so they are always in the same order
    protected final Collection<ApiModel> apiOptions = new TreeSet<>(Comparators.apiModelComparator());

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

    public boolean isApi() {
        return api;
    }

    public void setApi(boolean api) {
        this.api = api;
    }

    public String getApiSyntax() {
        return apiSyntax;
    }

    public void setApiSyntax(String apiSyntax) {
        this.apiSyntax = apiSyntax;
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

    public boolean isRemote() {
        return remote;
    }

    public void setRemote(boolean remote) {
        this.remote = remote;
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

    public List<EndpointHeaderModel> getEndpointHeaders() {
        return headers;
    }

    public void addEndpointHeader(EndpointHeaderModel header) {
        headers.add(header);
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

    public Collection<ApiModel> getApiOptions() {
        return apiOptions;
    }

    public static class EndpointHeaderModel extends BaseOptionModel {

        /**
         * The name of the constant that defines the header.
         */
        private String constantName;

        public String getConstantName() {
            return constantName;
        }

        public void setConstantName(String constantName) {
            this.constantName = constantName;
        }
    }

    public static class ComponentOptionModel extends BaseOptionModel {

    }

    public static class EndpointOptionModel extends BaseOptionModel {

    }

    public static class ApiOptionModel extends BaseOptionModel implements Cloneable {

        private boolean optional;

        public boolean isOptional() {
            return optional;
        }

        public void setOptional(boolean optional) {
            this.optional = optional;
        }

        // we need to be able to copy this option for api
        // options as we output the same options for each supported api methods,
        // however with a few changes per method

        public ApiOptionModel copy() {
            try {
                return (ApiOptionModel) clone();
            } catch (CloneNotSupportedException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        protected Object clone() throws CloneNotSupportedException {
            ApiOptionModel copy = (ApiOptionModel) super.clone();
            if (this.getEnums() != null) {
                copy.setEnums(new ArrayList<>(this.getEnums()));
            }
            if (this.getOneOfs() != null) {
                copy.setOneOfs(new ArrayList<>(this.getOneOfs()));
            }
            return copy;
        }
    }
}
