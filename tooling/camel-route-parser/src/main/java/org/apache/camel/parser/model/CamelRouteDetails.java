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
package org.apache.camel.parser.model;

/**
 * Details about a parsed and discovered Camel route.
 */
public class CamelRouteDetails {

    private String fileName;
    private String lineNumber;
    private String lineNumberEnd;
    private String className;
    private String methodName;
    private String routeId;

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

    public String getRouteId() {
        return routeId;
    }

    public void setRouteId(String routeId) {
        this.routeId = routeId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        CamelRouteDetails that = (CamelRouteDetails) o;

        if (!fileName.equals(that.fileName)) {
            return false;
        }
        if (lineNumber != null ? !lineNumber.equals(that.lineNumber) : that.lineNumber != null) {
            return false;
        }
        if (lineNumberEnd != null ? !lineNumberEnd.equals(that.lineNumberEnd) : that.lineNumberEnd != null) {
            return false;
        }
        if (className != null ? !className.equals(that.className) : that.className != null) {
            return false;
        }
        if (methodName != null ? !methodName.equals(that.methodName) : that.methodName != null) {
            return false;
        }
        if (routeId != null ? !routeId.equals(that.routeId) : that.routeId != null) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int result = fileName.hashCode();
        result = 31 * result + (lineNumber != null ? lineNumber.hashCode() : 0);
        result = 31 * result + (lineNumberEnd != null ? lineNumberEnd.hashCode() : 0);
        result = 31 * result + (className != null ? className.hashCode() : 0);
        result = 31 * result + (methodName != null ? methodName.hashCode() : 0);
        result = 31 * result + (routeId != null ? routeId.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "CamelRouteDetails["
                + "fileName='" + fileName + '\''
                + ", lineNumber='" + lineNumber + '\''
                + ", lineNumberEnd='" + lineNumberEnd + '\''
                + ", className='" + className + '\''
                + ", methodName='" + methodName + '\''
                + ", routeId='" + routeId + '\''
                + ']';
    }
}
