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

import java.util.ArrayList;
import java.util.List;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementRef;
import jakarta.xml.bind.annotation.XmlTransient;

import org.apache.camel.model.OptionalIdentifiedDefinition;
import org.apache.camel.model.ToDefinition;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.DslArg;

/**
 * A rest operation (such as GET, POST etc.)
 */
@Metadata(label = "rest")
@XmlAccessorType(XmlAccessType.FIELD)
public abstract class VerbDefinition extends OptionalIdentifiedDefinition<VerbDefinition> {

    @XmlTransient
    private RestDefinition rest;

    @XmlElementRef
    @Metadata(description = "The REST operation parameters such as query, path, header, or body parameters.")
    private List<ParamDefinition> params = new ArrayList<>();
    @XmlElementRef
    @Metadata(description = "The response messages with HTTP status codes and descriptions for this operation.")
    private List<ResponseMessageDefinition> responseMsgs = new ArrayList<>();
    @XmlElementRef
    @Metadata(description = "The security requirements for this operation.", label = "security")
    private List<SecurityDefinition> security = new ArrayList<>();

    @XmlAttribute
    @DslArg
    @Metadata(description = "The path mapping URIs of this REST operation such as /{id}.")
    private String path;
    @XmlAttribute
    @Metadata(description = "The content type the REST service consumes (accept as input), such as application/xml or application/json. This option will override what may be configured on a parent level.")
    private String consumes;
    @XmlAttribute
    @Metadata(description = "The content type the REST service produces (uses for output), such as application/xml or application/json. This option will override what may be configured on a parent level.")
    private String produces;
    @XmlAttribute
    @Metadata(description = "Whether to disable this REST service from the route during build time. Once an REST service has been disabled then it cannot be enabled later at runtime.",
              label = "advanced", javaType = "java.lang.Boolean")
    private String disabled;
    @XmlAttribute
    @Metadata(description = "Sets the class name to use for binding from input to POJO for the incoming data. This option will override what may be configured on a parent level.",
              label = "advanced")
    private String type;
    @XmlTransient
    private Class<?> typeClass;
    @XmlAttribute
    @Metadata(description = "Sets the class name to use for binding from POJO to output for the outgoing data. This option will override what may be configured on a parent level.",
              label = "advanced")
    private String outType;
    @XmlTransient
    private Class<?> outTypeClass;
    @XmlAttribute
    @Metadata(description = "Sets the binding mode for automatic marshalling and unmarshalling of request and response bodies. off (default) disables binding. auto detects JSON or XML from the Content-Type header. json binds using a JSON data format only. xml binds using an XML data format only. json_xml supports both JSON and XML. This option will override what may be configured on a parent level.",
              defaultValue = "off", enums = "off,auto,json,xml,json_xml")
    private String bindingMode;
    @XmlAttribute
    @Metadata(description = "Whether to skip binding on output if there is a custom HTTP error code header. This allows to build custom error messages that do not bind to json / xml etc, as success messages otherwise will do. This option will override what may be configured on a parent level.",
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
    @Metadata(description = "Whether to enable CORS headers in the HTTP response. This option will override what may be configured on a parent level.",
              label = "advanced", javaType = "java.lang.Boolean", defaultValue = "false")
    private String enableCORS;
    @XmlAttribute
    @Metadata(description = "Whether to return HTTP 204 with an empty body when a response contains an empty JSON object or XML root object.",
              label = "advanced", javaType = "java.lang.Boolean", defaultValue = "false")
    private String enableNoContentResponse;
    @XmlAttribute
    @Metadata(description = "Whether to include or exclude this rest operation in API documentation.",
              label = "advanced", javaType = "java.lang.Boolean", defaultValue = "true")
    private String apiDocs;
    @XmlAttribute
    @Metadata(description = "Marks this rest operation as deprecated in OpenApi documentation.",
              label = "advanced", javaType = "java.lang.Boolean", defaultValue = "false")
    private String deprecated;
    @XmlAttribute
    @Metadata(description = "Whether stream caching is enabled on this rest operation.",
              label = "advanced", javaType = "java.lang.Boolean")
    private String streamCache;
    @XmlAttribute
    @Metadata(description = "The route id this REST service will refer to.")
    private String routeId;
    @XmlElement(required = true)
    @Metadata(description = "The Camel endpoint this REST service will call, such as a direct endpoint to link to an existing route that handles this REST call.",
              required = true)
    private ToDefinition to;

    @Override
    public String getShortName() {
        return "verb";
    }

    @Override
    public String getLabel() {
        return "verb";
    }

    public String getDeprecated() {
        return deprecated;
    }

    public void setDeprecated(String deprecated) {
        this.deprecated = deprecated;
    }

    public VerbDefinition deprecated() {
        this.deprecated = "true";
        return this;
    }

    public String getRouteId() {
        return routeId;
    }

    public String getStreamCache() {
        return streamCache;
    }

    public void setStreamCache(String streamCache) {
        this.streamCache = streamCache;
    }

    /**
     * Enable or disables stream caching for this rest operation.
     *
     * @param  streamCache whether to use stream caching (true or false), the value can be a property placeholder
     * @return             the builder
     */
    public VerbDefinition streamCache(String streamCache) {
        setStreamCache(streamCache);
        return this;
    }

    public void setRouteId(String routeId) {
        this.routeId = routeId;
    }

    public List<ParamDefinition> getParams() {
        return params;
    }

    public void setParams(List<ParamDefinition> params) {
        this.params = params;
    }

    public List<ResponseMessageDefinition> getResponseMsgs() {
        return responseMsgs;
    }

    public void setResponseMsgs(List<ResponseMessageDefinition> responseMsgs) {
        this.responseMsgs = responseMsgs;
    }

    public List<SecurityDefinition> getSecurity() {
        return security;
    }

    public void setSecurity(List<SecurityDefinition> security) {
        this.security = security;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getConsumes() {
        return consumes;
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

    public String getDisabled() {
        return disabled;
    }

    public void setDisabled(String disabled) {
        this.disabled = disabled;
    }

    public String getBindingMode() {
        return bindingMode;
    }

    public void setBindingMode(String bindingMode) {
        this.bindingMode = bindingMode;
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

    public String getApiDocs() {
        return apiDocs;
    }

    public void setApiDocs(String apiDocs) {
        this.apiDocs = apiDocs;
    }

    public ToDefinition getTo() {
        return to;
    }

    public void setTo(ToDefinition to) {
        if (this.to != null) {
            throw new IllegalArgumentException(
                    "This verb has already set to endpoint. It is not possible to configure multiple 'to' with Rest DSL.");
        }
        this.to = to;
    }

    public RestDefinition getRest() {
        return rest;
    }

    public void setRest(RestDefinition rest) {
        this.rest = rest;
    }

    // Fluent API
    // -------------------------------------------------------------------------

    public RestDefinition get() {
        return rest.get();
    }

    public RestDefinition get(String uri) {
        return rest.get(uri);
    }

    public RestDefinition post() {
        return rest.post();
    }

    public RestDefinition post(String uri) {
        return rest.post(uri);
    }

    public RestDefinition put() {
        return rest.put();
    }

    public RestDefinition put(String uri) {
        return rest.put(uri);
    }

    public RestDefinition delete() {
        return rest.delete();
    }

    public RestDefinition delete(String uri) {
        return rest.delete(uri);
    }

    public RestDefinition head() {
        return rest.head();
    }

    public RestDefinition head(String uri) {
        return rest.head(uri);
    }

    public RestDefinition verb(String verb) {
        return rest.verb(verb);
    }

    public RestDefinition verb(String verb, String uri) {
        return rest.verb(verb, uri);
    }

    public abstract String asVerb();

}
