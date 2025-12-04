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

package org.apache.camel.model.rest;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;

import org.apache.camel.model.OptionalIdentifiedDefinition;
import org.apache.camel.spi.Metadata;

/**
 * To use OpenApi as contract-first with Camel Rest DSL.
 */
@Metadata(label = "rest")
@XmlRootElement(name = "openApi")
@XmlAccessorType(XmlAccessType.FIELD)
public class OpenApiDefinition extends OptionalIdentifiedDefinition<OpenApiDefinition> {

    @XmlTransient
    private RestDefinition rest;

    @XmlAttribute(required = true)
    private String specification;

    @XmlAttribute
    private String apiContextPath;

    @XmlAttribute
    private String routeId;

    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.lang.Boolean")
    private String disabled;

    @XmlAttribute
    @Metadata(enums = "fail,ignore,mock", defaultValue = "fail")
    private String missingOperation;

    @XmlAttribute
    @Metadata(label = "advanced", defaultValue = "classpath:camel-mock/**")
    private String mockIncludePattern;

    public void setRest(RestDefinition rest) {
        this.rest = rest;
    }

    @Override
    public String getShortName() {
        return "openApi";
    }

    @Override
    public String getLabel() {
        return "openApi";
    }

    public String getSpecification() {
        return specification;
    }

    public void setSpecification(String specification) {
        this.specification = specification;
    }

    public String getApiContextPath() {
        return apiContextPath;
    }

    /**
     * Whether to enable api-doc that exposes the OpenAPI specification file as a REST endpoint. This allows clients to
     * obtain the specification from the running Camel application.
     */
    public void setApiContextPath(String apiContextPath) {
        this.apiContextPath = apiContextPath;
    }

    public String getDisabled() {
        return disabled;
    }

    /**
     * Whether to disable all the REST services from the OpenAPI contract from the route during build time. Once an REST
     * service has been disabled then it cannot be enabled later at runtime.
     */
    public void setDisabled(String disabled) {
        this.disabled = disabled;
    }

    public String getMissingOperation() {
        return missingOperation;
    }

    /**
     * Whether to fail, ignore or return a mock response for OpenAPI operations that are not mapped to a corresponding
     * route.
     */
    public void setMissingOperation(String missingOperation) {
        this.missingOperation = missingOperation;
    }

    public String getMockIncludePattern() {
        return mockIncludePattern;
    }

    /**
     * Used for inclusive filtering of mock data from directories. The pattern is using Ant-path style pattern. Multiple
     * patterns can be specified separated by comma.
     */
    public void setMockIncludePattern(String mockIncludePattern) {
        this.mockIncludePattern = mockIncludePattern;
    }

    public String getRouteId() {
        return routeId;
    }

    /**
     * Sets the id of the route
     */
    public void setRouteId(String routeId) {
        this.routeId = routeId;
    }

    // Fluent API
    // -------------------------------------------------------------------------

    /**
     * Path to the OpenApi specification file.
     */
    public OpenApiDefinition specification(String specification) {
        this.specification = specification;
        return this;
    }

    /**
     * Whether to enable api-doc that exposes the OpenAPI specification file as a REST endpoint. This allows clients to
     * obtain the specification from the running Camel application.
     */
    public OpenApiDefinition apiContextPath(String apiContextPath) {
        this.apiContextPath = apiContextPath;
        return this;
    }

    /**
     * Whether to disable the OpenAPI entirely. Once the OpenAPI has been disabled then it cannot be enabled later at
     * runtime.
     */
    public OpenApiDefinition disabled(String disabled) {
        this.disabled = disabled;
        return this;
    }

    /**
     * Whether to disable the OpenAPI entirely. Once the OpenAPI has been disabled then it cannot be enabled later at
     * runtime.
     */
    public OpenApiDefinition disabled(boolean disabled) {
        this.disabled = disabled ? "true" : "false";
        return this;
    }

    /**
     * Whether to disable the OpenAPI entirely. Once the OpenAPI has been disabled then it cannot be enabled later at
     * runtime.
     */
    public OpenApiDefinition disabled() {
        return disabled(true);
    }

    /**
     * Whether to fail, ignore or return a mock response for OpenAPI operations that are not mapped to a corresponding
     * route.
     */
    public OpenApiDefinition missingOperation(String missingOperation) {
        this.missingOperation = missingOperation;
        return this;
    }

    /**
     * Used for inclusive filtering of mock data from directories. The pattern is using Ant-path style pattern. Multiple
     * patterns can be specified separated by comma.
     */
    public OpenApiDefinition mockIncludePattern(String mockIncludePattern) {
        this.mockIncludePattern = mockIncludePattern;
        return this;
    }

    /**
     * Sets the id of the route
     */
    public OpenApiDefinition routeId(String routeId) {
        this.routeId = routeId;
        return this;
    }

    public RestDefinition end() {
        return rest;
    }
}
