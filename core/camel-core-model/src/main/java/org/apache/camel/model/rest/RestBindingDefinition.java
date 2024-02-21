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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;

import org.apache.camel.model.OptionalIdentifiedDefinition;
import org.apache.camel.spi.Metadata;

/**
 * To configure rest binding
 */
@Metadata(label = "rest")
@XmlRootElement(name = "restBinding")
@XmlAccessorType(XmlAccessType.FIELD)
public class RestBindingDefinition extends OptionalIdentifiedDefinition<RestBindingDefinition> {

    @XmlTransient
    private Map<String, String> defaultValues;
    @XmlTransient
    private Boolean requiredBody;
    @XmlTransient
    private Set<String> requiredHeaders;
    @XmlTransient
    private Set<String> requiredQueryParameters;

    @XmlAttribute
    private String consumes;
    @XmlAttribute
    private String produces;
    @XmlAttribute
    @Metadata(defaultValue = "off", enums = "off,auto,json,xml,json_xml")
    private String bindingMode;
    @XmlAttribute
    @Metadata(label = "advanced")
    private String type;
    @XmlTransient
    private Class<?> typeClass;
    @XmlAttribute
    @Metadata(label = "advanced")
    private String outType;
    @XmlTransient
    private Class<?> outTypeClass;
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.lang.Boolean", defaultValue = "false")
    private String skipBindingOnErrorCode;
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.lang.Boolean", defaultValue = "false")
    private String clientRequestValidation;
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.lang.Boolean", defaultValue = "false")
    private String enableCORS;
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.lang.Boolean", defaultValue = "false")
    private String enableNoContentResponse;
    @XmlAttribute
    @Metadata(label = "advanced")
    private String component;

    public RestBindingDefinition() {
    }

    @Override
    public String toString() {
        return "RestBinding";
    }

    public String getConsumes() {
        return consumes;
    }

    /**
     * Adds a default value for the query parameter
     *
     * @param paramName    query parameter name
     * @param defaultValue the default value
     */
    public void addDefaultValue(String paramName, String defaultValue) {
        if (defaultValues == null) {
            defaultValues = new HashMap<>();
        }
        defaultValues.put(paramName, defaultValue);
    }

    /**
     * Adds a required query parameter
     *
     * @param paramName query parameter name
     */
    public void addRequiredQueryParameter(String paramName) {
        if (requiredQueryParameters == null) {
            requiredQueryParameters = new HashSet<>();
        }
        requiredQueryParameters.add(paramName);
    }

    public Set<String> getRequiredQueryParameters() {
        return requiredQueryParameters;
    }

    /**
     * Adds a required HTTP header
     *
     * @param headerName HTTP header name
     */
    public void addRequiredHeader(String headerName) {
        if (requiredHeaders == null) {
            requiredHeaders = new HashSet<>();
        }
        requiredHeaders.add(headerName);
    }

    public Set<String> getRequiredHeaders() {
        return requiredHeaders;
    }

    public Boolean getRequiredBody() {
        return requiredBody;
    }

    public void setRequiredBody(Boolean requiredBody) {
        this.requiredBody = requiredBody;
    }

    /**
     * Gets the registered default values for query parameters
     */
    public Map<String, String> getDefaultValues() {
        return defaultValues;
    }

    /**
     * Sets the component name that this definition will apply to
     */
    public void setComponent(String component) {
        this.component = component;
    }

    public String getComponent() {
        return component;
    }

    /**
     * To define the content type what the REST service consumes (accept as input), such as application/xml or
     * application/json
     */
    public void setConsumes(String consumes) {
        this.consumes = consumes;
    }

    public String getProduces() {
        return produces;
    }

    /**
     * To define the content type what the REST service produces (uses for output), such as application/xml or
     * application/json
     */
    public void setProduces(String produces) {
        this.produces = produces;
    }

    public String getBindingMode() {
        return bindingMode;
    }

    /**
     * Sets the binding mode to use.
     * <p/>
     * The default value is off
     */
    public void setBindingMode(String bindingMode) {
        this.bindingMode = bindingMode;
    }

    public String getType() {
        return type;
    }

    /**
     * Sets the class name to use for binding from input to POJO for the incoming data
     * <p/>
     * The name of the class of the input data. Append a [] to the end of the name if you want the input to be an array
     * type.
     */
    public void setType(String type) {
        this.type = type;
    }

    public Class<?> getTypeClass() {
        return typeClass;
    }

    /**
     * Sets the class to use for binding from input to POJO for the incoming data
     */
    public void setTypeClass(Class<?> typeClass) {
        this.typeClass = typeClass;
    }

    public String getOutType() {
        return outType;
    }

    /**
     * Sets the class name to use for binding from POJO to output for the outgoing data
     * <p/>
     * The name of the class of the input data. Append a [] to the end of the name if you want the input to be an array
     * type.
     */
    public void setOutType(String outType) {
        this.outType = outType;
    }

    public Class<?> getOutTypeClass() {
        return outTypeClass;
    }

    /**
     * Sets the class name to use for binding from POJO to output for the outgoing data
     */
    public void setOutTypeClass(Class<?> outTypeClass) {
        this.outTypeClass = outTypeClass;
    }

    public String getSkipBindingOnErrorCode() {
        return skipBindingOnErrorCode;
    }

    /**
     * Whether to skip binding on output if there is a custom HTTP error code header. This allows to build custom error
     * messages that do not bind to json / xml etc, as success messages otherwise will do.
     */
    public void setSkipBindingOnErrorCode(String skipBindingOnErrorCode) {
        this.skipBindingOnErrorCode = skipBindingOnErrorCode;
    }

    public String getClientRequestValidation() {
        return clientRequestValidation;
    }

    /**
     * Whether to enable validation of the client request to check:
     *
     * 1) Content-Type header matches what the Rest DSL consumes; returns HTTP Status 415 if validation error. 2) Accept
     * header matches what the Rest DSL produces; returns HTTP Status 406 if validation error. 3) Missing required data
     * (query parameters, HTTP headers, body); returns HTTP Status 400 if validation error. 4) Parsing error of the
     * message body (JSon, XML or Auto binding mode must be enabled); returns HTTP Status 400 if validation error.
     */
    public void setClientRequestValidation(String clientRequestValidation) {
        this.clientRequestValidation = clientRequestValidation;
    }

    public String getEnableCORS() {
        return enableCORS;
    }

    /**
     * Whether to enable CORS headers in the HTTP response.
     * <p/>
     * The default value is false.
     */
    public void setEnableCORS(String enableCORS) {
        this.enableCORS = enableCORS;
    }

    public String getEnableNoContentResponse() {
        return enableNoContentResponse;
    }

    /**
     * Whether to return HTTP 204 with an empty body when a response contains an empty JSON object or XML root object.
     * <p/>
     * The default value is false.
     */
    public void setEnableNoContentResponse(String enableNoContentResponse) {
        this.enableNoContentResponse = enableNoContentResponse;
    }

    @Override
    public String getShortName() {
        return "restBinding";
    }

    @Override
    public String getLabel() {
        return "";
    }
}
