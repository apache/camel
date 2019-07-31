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

/**
 * Details about a parsed and discovered Camel endpoint.
 */
public class CamelEndpointDetails {

    private String fileName;
    private String lineNumber;
    private String lineNumberEnd;
    private int absolutePosition;
    private int linePosition;
    private String className;
    private String methodName;
    private String endpointComponentName;
    private String endpointInstance;
    private String endpointUri;
    private boolean consumerOnly;
    private boolean producerOnly;

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

    public int getAbsolutePosition() {
        return absolutePosition;
    }

    /**
     * The absolute position where 0 is the beginning of the file. This is only available for Java DSL.
     */
    public void setAbsolutePosition(int absolutePosition) {
        this.absolutePosition = absolutePosition;
    }

    public int getLinePosition() {
        return linePosition;
    }

    /**
     * The relative position in the line number (start line).
     */
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

    public String getEndpointComponentName() {
        return endpointComponentName;
    }

    public void setEndpointComponentName(String endpointComponentName) {
        this.endpointComponentName = endpointComponentName;
    }

    public String getEndpointInstance() {
        return endpointInstance;
    }

    public void setEndpointInstance(String endpointInstance) {
        this.endpointInstance = endpointInstance;
    }

    public String getEndpointUri() {
        return endpointUri;
    }

    public void setEndpointUri(String endpointUri) {
        this.endpointUri = endpointUri;
    }

    public boolean isConsumerOnly() {
        return consumerOnly;
    }

    public void setConsumerOnly(boolean consumerOnly) {
        this.consumerOnly = consumerOnly;
    }

    public boolean isProducerOnly() {
        return producerOnly;
    }

    public void setProducerOnly(boolean producerOnly) {
        this.producerOnly = producerOnly;
    }

    @Override
    public String toString() {
        return "CamelEndpointDetails["
                + "fileName='" + fileName + '\''
                + ", lineNumber='" + lineNumber + '\''
                + ", lineNumberEnd='" + lineNumberEnd + '\''
                + ", className='" + className + '\''
                + ", methodName='" + methodName + '\''
                + ", endpointComponentName='" + endpointComponentName + '\''
                + ", endpointInstance='" + endpointInstance + '\''
                + ", endpointUri='" + endpointUri + '\''
                + ", consumerOnly=" + consumerOnly
                + ", producerOnly=" + producerOnly
                + ']';
    }
}
