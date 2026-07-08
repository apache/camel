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
@Metadata(label = "rest",
          description = "Configures data binding for a REST service, controlling how request and response bodies are marshalled and unmarshalled")
@XmlRootElement(name = "restBinding")
@XmlAccessorType(XmlAccessType.FIELD)
public class RestBindingDefinition extends OptionalIdentifiedDefinition<RestBindingDefinition> {

    @XmlTransient
    private Map<String, String> defaultValues;
    @XmlTransient
    private Map<String, String> allowedValues;
    @XmlTransient
    private Boolean requiredBody;
    @XmlTransient
    private Set<String> requiredHeaders;
    @XmlTransient
    private Set<String> requiredQueryParameters;
    @XmlTransient
    private Map<String, String> responseCodes;
    @XmlTransient
    private Set<String> responseHeaders;

    @XmlAttribute
    @Metadata(description = "The content type the REST service accepts (consumes) as input, such as application/xml or application/json.")
    private String consumes;
    @XmlAttribute
    @Metadata(description = "The content type the REST service produces (uses for output), such as application/xml or application/json.")
    private String produces;
    @XmlAttribute
    @Metadata(description = "Sets the binding mode for automatic marshalling and unmarshalling of request and response bodies. off (default) disables binding. auto detects JSON or XML from the Content-Type header. json binds using a JSON data format only. xml binds using an XML data format only. json_xml supports both JSON and XML.",
              defaultValue = "off", enums = "off,auto,json,xml,json_xml")
    private String bindingMode;
    @XmlAttribute
    @Metadata(description = "Sets the class name to use for binding from input to POJO for the incoming data.",
              label = "advanced")
    private String type;
    @XmlTransient
    private Class<?> typeClass;
    @XmlAttribute
    @Metadata(description = "Sets the class name to use for binding from POJO to output for the outgoing data.",
              label = "advanced")
    private String outType;
    @XmlTransient
    private Class<?> outTypeClass;
    @XmlAttribute
    @Metadata(description = "Whether to skip binding on output if there is a custom HTTP error code header. This allows to build custom error messages that do not bind to json / xml etc, as success messages otherwise will do.",
              label = "advanced", javaType = "java.lang.Boolean", defaultValue = "false")
    private String skipBindingOnErrorCode;
    @XmlAttribute
    @Metadata(description = "Whether to enable validation of the client request to check whether Content-Type/Accept headers, required parameters, and message body are valid.",
              label = "advanced", javaType = "java.lang.Boolean", defaultValue = "false")
    private String clientRequestValidation;
    @XmlAttribute
    @Metadata(description = "Whether to validate what Camel is returning as response to the client, such as checking status-code, Content-Type, and headers match the Rest DSL response definition.",
              label = "advanced", javaType = "java.lang.Boolean", defaultValue = "false")
    private String clientResponseValidation;
    @XmlAttribute
    @Metadata(description = "Whether to enable CORS headers in the HTTP response.",
              label = "advanced", javaType = "java.lang.Boolean", defaultValue = "false")
    private String enableCORS;
    @XmlAttribute
    @Metadata(description = "Whether to return HTTP 204 with an empty body when a response contains an empty JSON object or XML root object.",
              label = "advanced", javaType = "java.lang.Boolean", defaultValue = "false")
    private String enableNoContentResponse;
    @XmlAttribute
    @Metadata(description = "Sets the component name that this definition will apply to.",
              label = "advanced")
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
     * Adds allowed value(s) for the query parameter
     *
     * @param paramName    query parameter name
     * @param allowedValue the allowed value (separate by comma)
     */
    public void addAllowedValue(String paramName, String allowedValue) {
        if (allowedValues == null) {
            allowedValues = new HashMap<>();
        }
        allowedValues.put(paramName, allowedValue);
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
     * Adds a response code
     */
    public void addResponseCode(String code, String contentType) {
        if (responseCodes == null) {
            responseCodes = new HashMap<>();
        }
        responseCodes.put(code, contentType);
    }

    public Map<String, String> getResponseCodes() {
        return responseCodes;
    }

    /**
     * Adds a response code
     */
    public void addResponseHeader(String headerName) {
        // content-type header should be skipped
        if ("content-type".equalsIgnoreCase(headerName)) {
            return;
        }
        if (responseHeaders == null) {
            responseHeaders = new HashSet<>();
        }
        responseHeaders.add(headerName);
    }

    public Set<String> getResponseHeaders() {
        return responseHeaders;
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
     * Gets the registered allowed values for query parameters
     */
    public Map<String, String> getAllowedValues() {
        return allowedValues;
    }

    public void setComponent(String component) {
        this.component = component;
    }

    public String getComponent() {
        return component;
    }

    public void setConsumes(String consumes) {
        this.consumes = consumes;
    }

    public String getProduces() {
        return produces;
    }

    public void setProduces(String produces) {
        this.produces = produces;
    }

    public String getBindingMode() {
        return bindingMode;
    }

    public void setBindingMode(String bindingMode) {
        this.bindingMode = bindingMode;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Class<?> getTypeClass() {
        return typeClass;
    }

    public void setTypeClass(Class<?> typeClass) {
        this.typeClass = typeClass;
    }

    public String getOutType() {
        return outType;
    }

    public void setOutType(String outType) {
        this.outType = outType;
    }

    public Class<?> getOutTypeClass() {
        return outTypeClass;
    }

    public void setOutTypeClass(Class<?> outTypeClass) {
        this.outTypeClass = outTypeClass;
    }

    public String getSkipBindingOnErrorCode() {
        return skipBindingOnErrorCode;
    }

    public void setSkipBindingOnErrorCode(String skipBindingOnErrorCode) {
        this.skipBindingOnErrorCode = skipBindingOnErrorCode;
    }

    public String getClientRequestValidation() {
        return clientRequestValidation;
    }

    public void setClientRequestValidation(String clientRequestValidation) {
        this.clientRequestValidation = clientRequestValidation;
    }

    public String getClientResponseValidation() {
        return clientResponseValidation;
    }

    public void setClientResponseValidation(String clientResponseValidation) {
        this.clientResponseValidation = clientResponseValidation;
    }

    public String getEnableCORS() {
        return enableCORS;
    }

    public void setEnableCORS(String enableCORS) {
        this.enableCORS = enableCORS;
    }

    public String getEnableNoContentResponse() {
        return enableNoContentResponse;
    }

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
