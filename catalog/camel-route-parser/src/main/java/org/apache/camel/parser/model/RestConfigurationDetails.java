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
package org.apache.camel.parser.model;

import java.util.LinkedHashMap;
import java.util.Map;

public class RestConfigurationDetails {

    // source code details
    private String fileName;
    private String lineNumber;
    private String lineNumberEnd;
    private int linePosition;

    // java source code details
    private String className;
    private String methodName;

    // camel rest configuration details
    private String component;
    private String apiComponent;
    private String producerComponent;
    private String scheme;
    private String host;
    private String apiHost;
    private String port;
    private String producerApiDoc;
    private String contextPath;
    private String apiContextPath;
    private String apiContextRouteId;
    private String apiContextIdPattern;
    private String apiContextListening;
    private String apiVendorExtension;
    private String hostNameResolver;
    private String bindingMode;
    private String skipBindingOnErrorCode;
    private String clientRequestValidation;
    private String enableCORS;
    private String jsonDataFormat;
    private String xmlDataFormat;
    private Map<String, String> componentProperties;
    private Map<String, String> endpointProperties;
    private Map<String, String> consumerProperties;
    private Map<String, String> dataFormatProperties;
    private Map<String, String> apiProperties;
    private Map<String, String> corsHeaders;

    public RestConfigurationDetails() {
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getLineNumber() {
        return lineNumber;
    }

    public void setLineNumber(String lineNumber) {
        this.lineNumber = lineNumber;
    }

    public String getLineNumberEnd() {
        return lineNumberEnd;
    }

    public void setLineNumberEnd(String lineNumberEnd) {
        this.lineNumberEnd = lineNumberEnd;
    }

    public int getLinePosition() {
        return linePosition;
    }

    public void setLinePosition(int linePosition) {
        this.linePosition = linePosition;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public String getComponent() {
        return component;
    }

    public void setComponent(String component) {
        this.component = component;
    }

    public String getApiComponent() {
        return apiComponent;
    }

    public void setApiComponent(String apiComponent) {
        this.apiComponent = apiComponent;
    }

    public String getProducerComponent() {
        return producerComponent;
    }

    public void setProducerComponent(String producerComponent) {
        this.producerComponent = producerComponent;
    }

    public String getScheme() {
        return scheme;
    }

    public void setScheme(String scheme) {
        this.scheme = scheme;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getApiHost() {
        return apiHost;
    }

    public void setApiHost(String apiHost) {
        this.apiHost = apiHost;
    }

    public String getPort() {
        return port;
    }

    public void setPort(String port) {
        this.port = port;
    }

    public String getProducerApiDoc() {
        return producerApiDoc;
    }

    public void setProducerApiDoc(String producerApiDoc) {
        this.producerApiDoc = producerApiDoc;
    }

    public String getContextPath() {
        return contextPath;
    }

    public void setContextPath(String contextPath) {
        this.contextPath = contextPath;
    }

    public String getApiContextPath() {
        return apiContextPath;
    }

    public void setApiContextPath(String apiContextPath) {
        this.apiContextPath = apiContextPath;
    }

    public String getApiContextRouteId() {
        return apiContextRouteId;
    }

    public void setApiContextRouteId(String apiContextRouteId) {
        this.apiContextRouteId = apiContextRouteId;
    }

    public String getApiContextIdPattern() {
        return apiContextIdPattern;
    }

    public void setApiContextIdPattern(String apiContextIdPattern) {
        this.apiContextIdPattern = apiContextIdPattern;
    }

    public String getApiContextListening() {
        return apiContextListening;
    }

    public void setApiContextListening(String apiContextListening) {
        this.apiContextListening = apiContextListening;
    }

    public String getApiVendorExtension() {
        return apiVendorExtension;
    }

    public void setApiVendorExtension(String apiVendorExtension) {
        this.apiVendorExtension = apiVendorExtension;
    }

    public String getHostNameResolver() {
        return hostNameResolver;
    }

    public void setHostNameResolver(String hostNameResolver) {
        this.hostNameResolver = hostNameResolver;
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

    public String getEnableCORS() {
        return enableCORS;
    }

    public void setEnableCORS(String enableCORS) {
        this.enableCORS = enableCORS;
    }

    public String getJsonDataFormat() {
        return jsonDataFormat;
    }

    public void setJsonDataFormat(String jsonDataFormat) {
        this.jsonDataFormat = jsonDataFormat;
    }

    public String getXmlDataFormat() {
        return xmlDataFormat;
    }

    public void setXmlDataFormat(String xmlDataFormat) {
        this.xmlDataFormat = xmlDataFormat;
    }

    public Map<String, String> getComponentProperties() {
        return componentProperties;
    }

    public void setComponentProperties(Map<String, String> componentProperties) {
        this.componentProperties = componentProperties;
    }

    public Map<String, String> getEndpointProperties() {
        return endpointProperties;
    }

    public void setEndpointProperties(Map<String, String> endpointProperties) {
        this.endpointProperties = endpointProperties;
    }

    public Map<String, String> getConsumerProperties() {
        return consumerProperties;
    }

    public void setConsumerProperties(Map<String, String> consumerProperties) {
        this.consumerProperties = consumerProperties;
    }

    public Map<String, String> getDataFormatProperties() {
        return dataFormatProperties;
    }

    public void setDataFormatProperties(Map<String, String> dataFormatProperties) {
        this.dataFormatProperties = dataFormatProperties;
    }

    public Map<String, String> getApiProperties() {
        return apiProperties;
    }

    public void setApiProperties(Map<String, String> apiProperties) {
        this.apiProperties = apiProperties;
    }

    public Map<String, String> getCorsHeaders() {
        return corsHeaders;
    }

    public void setCorsHeaders(Map<String, String> corsHeaders) {
        this.corsHeaders = corsHeaders;
    }

    public void addComponentProperty(String key, String value) {
        if (componentProperties == null) {
            componentProperties = new LinkedHashMap<>();
        }
        componentProperties.put(key, value);
    }

    public void addEndpointProperty(String key, String value) {
        if (endpointProperties == null) {
            endpointProperties = new LinkedHashMap<>();
        }
        endpointProperties.put(key, value);
    }

    public void addConsumerProperty(String key, String value) {
        if (consumerProperties == null) {
            consumerProperties = new LinkedHashMap<>();
        }
        consumerProperties.put(key, value);
    }

    public void addDataFormatProperty(String key, String value) {
        if (dataFormatProperties == null) {
            dataFormatProperties = new LinkedHashMap<>();
        }
        dataFormatProperties.put(key, value);
    }

    public void addApiProperty(String key, String value) {
        if (apiProperties == null) {
            apiProperties = new LinkedHashMap<>();
        }
        apiProperties.put(key, value);
    }

    public void addCorsHeader(String key, String value) {
        if (corsHeaders == null) {
            corsHeaders = new LinkedHashMap<>();
        }
        corsHeaders.put(key, value);
    }
}
