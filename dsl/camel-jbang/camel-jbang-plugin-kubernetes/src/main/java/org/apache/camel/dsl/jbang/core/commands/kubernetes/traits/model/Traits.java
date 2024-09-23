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

package org.apache.camel.dsl.jbang.core.commands.kubernetes.traits.model;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "camel", "container", "environment", "ingress", "knative", "knative-service", "mount", "openapi", "pod", "route",
        "service", "service-binding" })
public class Traits {

    @JsonProperty("addons")
    @JsonPropertyDescription("The extension point with addon traits")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private Map<String, Addons> addons;

    @JsonProperty("builder")
    @JsonPropertyDescription("The configuration of Builder trait")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private Builder builder;

    @JsonProperty("camel")
    @JsonPropertyDescription("The configuration of Camel trait")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private Camel camel;

    @JsonProperty("container")
    @JsonPropertyDescription("The configuration of Container trait")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private Container container;

    @JsonProperty("environment")
    @JsonPropertyDescription("The configuration of Environment trait")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private Environment environment;

    @JsonProperty("ingress")
    @JsonPropertyDescription("The configuration of Ingress trait")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private Ingress ingress;

    @JsonProperty("knative")
    @JsonPropertyDescription("The configuration of Knative trait")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private Knative knative;

    @JsonProperty("knative-service")
    @JsonPropertyDescription("The configuration of Knative Service trait")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private KnativeService knativeService;

    @JsonProperty("mount")
    @JsonPropertyDescription("The configuration of Mount trait")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private Mount mount;

    @JsonProperty("openapi")
    @JsonPropertyDescription("The configuration of OpenAPI trait")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private Openapi openapi;

    @JsonProperty("route")
    @JsonPropertyDescription("The configuration of Route trait")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private Route route;

    @JsonProperty("service")
    @JsonPropertyDescription("The configuration of Service trait")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private Service service;
    @JsonProperty("service-binding")
    @JsonPropertyDescription("The configuration of Service Binding trait")
    @JsonSetter(
                nulls = Nulls.SKIP)
    private ServiceBinding serviceBinding;

    public Map<String, Addons> getAddons() {
        return this.addons;
    }

    public void setAddons(Map<String, Addons> addons) {
        this.addons = addons;
    }

    public Builder getBuilder() {
        return this.builder;
    }

    public void setBuilder(Builder builder) {
        this.builder = builder;
    }

    public Camel getCamel() {
        return camel;
    }

    public void setCamel(Camel camel) {
        this.camel = camel;
    }

    public Container getContainer() {
        return container;
    }

    public void setContainer(Container container) {
        this.container = container;
    }

    public Environment getEnvironment() {
        return environment;
    }

    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    public Ingress getIngress() {
        return ingress;
    }

    public void setIngress(Ingress ingress) {
        this.ingress = ingress;
    }

    public Knative getKnative() {
        return knative;
    }

    public void setKnative(Knative knative) {
        this.knative = knative;
    }

    public KnativeService getKnativeService() {
        return knativeService;
    }

    public void setKnativeService(KnativeService knativeService) {
        this.knativeService = knativeService;
    }

    public Mount getMount() {
        return mount;
    }

    public void setMount(Mount mount) {
        this.mount = mount;
    }

    public Openapi getOpenapi() {
        return openapi;
    }

    public void setOpenapi(Openapi openapi) {
        this.openapi = openapi;
    }

    public Route getRoute() {
        return route;
    }

    public void setRoute(Route route) {
        this.route = route;
    }

    public Service getService() {
        return service;
    }

    public void setService(Service service) {
        this.service = service;
    }

    public ServiceBinding getServiceBinding() {
        return serviceBinding;
    }

    public void setServiceBinding(ServiceBinding serviceBinding) {
        this.serviceBinding = serviceBinding;
    }
}
