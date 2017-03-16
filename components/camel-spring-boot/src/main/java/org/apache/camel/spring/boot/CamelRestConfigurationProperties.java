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
package org.apache.camel.spring.boot;

import java.util.Map;

import org.apache.camel.spi.RestConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "camel.rest")
public class CamelRestConfigurationProperties {
    // Delegate it so we do not have the CamelRestConfigurationProperties
    // registered available also as RestConfiguration
    private final RestConfiguration delegate;

    public CamelRestConfigurationProperties() {
        this.delegate = new RestConfiguration();
    }

    public String getComponent() {
        return delegate.getComponent();
    }

    public void setComponent(String componentName) {
        delegate.setComponent(componentName);
    }

    public String getApiComponent() {
        return delegate.getApiComponent();
    }

    public void setApiComponent(String apiComponent) {
        delegate.setApiComponent(apiComponent);
    }

    public String getProducerComponent() {
        return delegate.getProducerComponent();
    }

    public void setProducerComponent(String componentName) {
        delegate.setProducerComponent(componentName);
    }

    public String getProducerApiDoc() {
        return delegate.getProducerApiDoc();
    }

    public void setProducerApiDoc(String producerApiDoc) {
        delegate.setProducerApiDoc(producerApiDoc);
    }

    public String getHost() {
        return delegate.getHost();
    }

    public void setHost(String host) {
        delegate.setHost(host);
    }

    public String getScheme() {
        return delegate.getScheme();
    }

    public void setScheme(String scheme) {
        delegate.setScheme(scheme);
    }

    public int getPort() {
        return delegate.getPort();
    }

    public void setPort(int port) {
        delegate.setPort(port);
    }

    public String getContextPath() {
        return delegate.getContextPath();
    }

    public void setContextPath(String contextPath) {
        delegate.setContextPath(contextPath);
    }

    public String getApiContextPath() {
        return delegate.getApiContextPath();
    }

    public void setApiContextPath(String contextPath) {
        delegate.setApiContextPath(contextPath);
    }

    public String getApiContextRouteId() {
        return delegate.getApiContextRouteId();
    }

    public void setApiContextRouteId(String apiContextRouteId) {
        delegate.setApiContextRouteId(apiContextRouteId);
    }

    public String getApiContextIdPattern() {
        return delegate.getApiContextIdPattern();
    }

    public void setApiContextIdPattern(String apiContextIdPattern) {
        delegate.setApiContextIdPattern(apiContextIdPattern);
    }

    public boolean isApiContextListing() {
        return delegate.isApiContextListing();
    }

    public void setApiContextListing(boolean apiContextListing) {
        delegate.setApiContextListing(apiContextListing);
    }

    public RestConfiguration.RestHostNameResolver getRestHostNameResolver() {
        return delegate.getRestHostNameResolver();
    }

    public void setRestHostNameResolver(RestConfiguration.RestHostNameResolver restHostNameResolver) {
        delegate.setRestHostNameResolver(restHostNameResolver);
    }

    public void setRestHostNameResolver(String restHostNameResolver) {
        delegate.setRestHostNameResolver(restHostNameResolver);
    }

    public RestConfiguration.RestBindingMode getBindingMode() {
        return delegate.getBindingMode();
    }

    public void setBindingMode(RestConfiguration.RestBindingMode bindingMode) {
        delegate.setBindingMode(bindingMode);
    }

    public void setBindingMode(String bindingMode) {
        delegate.setBindingMode(bindingMode);
    }

    public boolean isSkipBindingOnErrorCode() {
        return delegate.isSkipBindingOnErrorCode();
    }

    public void setSkipBindingOnErrorCode(boolean skipBindingOnErrorCode) {
        delegate.setSkipBindingOnErrorCode(skipBindingOnErrorCode);
    }

    public boolean isEnableCORS() {
        return delegate.isEnableCORS();
    }

    public void setEnableCORS(boolean enableCORS) {
        delegate.setEnableCORS(enableCORS);
    }

    public String getJsonDataFormat() {
        return delegate.getJsonDataFormat();
    }

    public void setJsonDataFormat(String name) {
        delegate.setJsonDataFormat(name);
    }

    public String getXmlDataFormat() {
        return delegate.getXmlDataFormat();
    }

    public void setXmlDataFormat(String name) {
        delegate.setXmlDataFormat(name);
    }

    public Map<String, Object> getComponentProperties() {
        return delegate.getComponentProperties();
    }

    public void setComponentProperties(Map<String, Object> componentProperties) {
        delegate.setComponentProperties(componentProperties);
    }

    public Map<String, Object> getEndpointProperties() {
        return delegate.getEndpointProperties();
    }

    public void setEndpointProperties(Map<String, Object> endpointProperties) {
        delegate.setEndpointProperties(endpointProperties);
    }

    public Map<String, Object> getConsumerProperties() {
        return delegate.getConsumerProperties();
    }

    public void setConsumerProperties(Map<String, Object> consumerProperties) {
        delegate.setConsumerProperties(consumerProperties);
    }

    public Map<String, Object> getDataFormatProperties() {
        return delegate.getDataFormatProperties();
    }

    public void setDataFormatProperties(Map<String, Object> dataFormatProperties) {
        delegate.setDataFormatProperties(dataFormatProperties);
    }

    public Map<String, Object> getApiProperties() {
        return delegate.getApiProperties();
    }

    public void setApiProperties(Map<String, Object> apiProperties) {
        delegate.setApiProperties(apiProperties);
    }

    public Map<String, String> getCorsHeaders() {
        return delegate.getCorsHeaders();
    }

    public void setCorsHeaders(Map<String, String> corsHeaders) {
        delegate.setCorsHeaders(corsHeaders);
    }
}
