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

package org.apache.camel.support.processor;

import java.util.Map;
import java.util.Set;

/**
 * Configuration for {@link RestBindingAdvice}.
 */
public class RestBindingConfiguration {

    private String consumes;
    private String produces;
    private String bindingMode;
    private String bindingPackageScan;
    private boolean skipBindingOnErrorCode;
    private boolean clientRequestValidation;
    private boolean clientResponseValidation;
    private boolean enableCORS;
    private boolean enableNoContentResponse;
    private Map<String, String> corsHeaders;
    private Map<String, String> queryDefaultValues;
    private Map<String, String> queryAllowedValues;
    private boolean requiredBody;
    private Set<String> requiredQueryParameters;
    private Set<String> requiredHeaders;
    private String type;
    private Class<?> typeClass;
    private String outType;
    private Class<?> outTypeClass;
    private Map<String, String> responseCodes;
    private Set<String> responseHeaders;

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

    public String getBindingMode() {
        return bindingMode;
    }

    public void setBindingMode(String bindingMode) {
        this.bindingMode = bindingMode;
    }

    public String getBindingPackageScan() {
        return bindingPackageScan;
    }

    public void setBindingPackageScan(String bindingPackageScan) {
        this.bindingPackageScan = bindingPackageScan;
    }

    public boolean isSkipBindingOnErrorCode() {
        return skipBindingOnErrorCode;
    }

    public void setSkipBindingOnErrorCode(boolean skipBindingOnErrorCode) {
        this.skipBindingOnErrorCode = skipBindingOnErrorCode;
    }

    public boolean isClientRequestValidation() {
        return clientRequestValidation;
    }

    public void setClientRequestValidation(boolean clientRequestValidation) {
        this.clientRequestValidation = clientRequestValidation;
    }

    public boolean isClientResponseValidation() {
        return clientResponseValidation;
    }

    public void setClientResponseValidation(boolean clientResponseValidation) {
        this.clientResponseValidation = clientResponseValidation;
    }

    public boolean isEnableCORS() {
        return enableCORS;
    }

    public void setEnableCORS(boolean enableCORS) {
        this.enableCORS = enableCORS;
    }

    public boolean isEnableNoContentResponse() {
        return enableNoContentResponse;
    }

    public void setEnableNoContentResponse(boolean enableNoContentResponse) {
        this.enableNoContentResponse = enableNoContentResponse;
    }

    public Map<String, String> getCorsHeaders() {
        return corsHeaders;
    }

    public void setCorsHeaders(Map<String, String> corsHeaders) {
        this.corsHeaders = corsHeaders;
    }

    public Map<String, String> getQueryDefaultValues() {
        return queryDefaultValues;
    }

    public void setQueryDefaultValues(Map<String, String> queryDefaultValues) {
        this.queryDefaultValues = queryDefaultValues;
    }

    public Map<String, String> getQueryAllowedValues() {
        return queryAllowedValues;
    }

    public void setQueryAllowedValues(Map<String, String> queryAllowedValues) {
        this.queryAllowedValues = queryAllowedValues;
    }

    public boolean isRequiredBody() {
        return requiredBody;
    }

    public void setRequiredBody(boolean requiredBody) {
        this.requiredBody = requiredBody;
    }

    public Set<String> getRequiredQueryParameters() {
        return requiredQueryParameters;
    }

    public void setRequiredQueryParameters(Set<String> requiredQueryParameters) {
        this.requiredQueryParameters = requiredQueryParameters;
    }

    public Set<String> getRequiredHeaders() {
        return requiredHeaders;
    }

    public void setRequiredHeaders(Set<String> requiredHeaders) {
        this.requiredHeaders = requiredHeaders;
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

    public Map<String, String> getResponseCodes() {
        return responseCodes;
    }

    public void setResponseCodes(Map<String, String> responseCodes) {
        this.responseCodes = responseCodes;
    }

    public Set<String> getResponseHeaders() {
        return responseHeaders;
    }

    public void setResponseHeaders(Set<String> responseHeaders) {
        this.responseHeaders = responseHeaders;
    }
}
