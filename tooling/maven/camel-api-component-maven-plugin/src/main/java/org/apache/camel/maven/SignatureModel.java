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
package org.apache.camel.maven;

import java.util.Map;

/**
 * Models of the method signatures from the parser.
 */
public class SignatureModel {

    private String apiName;
    private String apiDescription;
    private String methodDescription;
    private String signature;
    private Map<String, String> parameterDescriptions;
    private Map<String, String> parameterTypes;

    public String getApiName() {
        return apiName;
    }

    public void setApiName(String apiName) {
        this.apiName = apiName;
    }

    public String getApiDescription() {
        return apiDescription;
    }

    public void setApiDescription(String apiDescription) {
        this.apiDescription = apiDescription;
    }

    public String getMethodDescription() {
        return methodDescription;
    }

    public void setMethodDescription(String methodDescription) {
        this.methodDescription = methodDescription;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    public Map<String, String> getParameterDescriptions() {
        return parameterDescriptions;
    }

    public void setParameterDescriptions(Map<String, String> parameterDescriptions) {
        this.parameterDescriptions = parameterDescriptions;
    }

    public Map<String, String> getParameterTypes() {
        return parameterTypes;
    }

    public void setParameterTypes(Map<String, String> parameterTypes) {
        this.parameterTypes = parameterTypes;
    }
}
