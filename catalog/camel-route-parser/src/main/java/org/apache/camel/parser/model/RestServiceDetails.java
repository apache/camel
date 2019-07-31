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

import java.util.ArrayList;
import java.util.List;

public class RestServiceDetails {

    // source code details
    private String fileName;
    private String lineNumber;
    private String lineNumberEnd;
    private int linePosition;

    // java source code details
    private String className;
    private String methodName;

    // camel service details
    private String path;
    private String tag;
    private String consumes;
    private String produces;
    private String bindingMode;
    private String skipBindingOnErrorCode;
    private String clientRequestValidation;
    private String enableCORS;
    private String apiDocs;
    private String description;
    private List<RestVerbDetails> verbs;

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

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
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

    public String getApiDocs() {
        return apiDocs;
    }

    public void setApiDocs(String apiDocs) {
        this.apiDocs = apiDocs;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<RestVerbDetails> getVerbs() {
        return verbs;
    }

    public void setVerbs(List<RestVerbDetails> verbs) {
        this.verbs = verbs;
    }

    public void addVerb(RestVerbDetails verb) {
        if (verbs == null) {
            verbs = new ArrayList<>();
        }
        verbs.add(verb);
    }
}
