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
 * Details about a parsed and discovered Camel simple expression.
 */
public class CamelSimpleExpressionDetails {

    private String fileName;
    private String lineNumber;
    private String lineNumberEnd;
    private String className;
    private String methodName;
    private String simple;
    private boolean predicate;
    private boolean expression;

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

    public String getSimple() {
        return simple;
    }

    public void setSimple(String simple) {
        this.simple = simple;
    }

    public boolean isPredicate() {
        return predicate;
    }

    public void setPredicate(boolean predicate) {
        this.predicate = predicate;
    }

    public boolean isExpression() {
        return expression;
    }

    public void setExpression(boolean expression) {
        this.expression = expression;
    }

    @Override
    public String toString() {
        return simple;
    }
}
