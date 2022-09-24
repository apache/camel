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
package org.apache.camel.model.language;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.spi.Metadata;

/**
 * Evaluates a JSONPath expression against a JSON message body.
 */
@Metadata(firstVersion = "2.13.0", label = "language,json", title = "JSONPath")
@XmlRootElement(name = "jsonpath")
@XmlAccessorType(XmlAccessType.FIELD)
public class JsonPathExpression extends ExpressionDefinition {

    @XmlAttribute(name = "resultType")
    private String resultTypeName;
    @XmlTransient
    private Class<?> resultType;
    @XmlAttribute
    @Metadata(defaultValue = "false", javaType = "java.lang.Boolean")
    private String suppressExceptions;
    @XmlAttribute
    @Metadata(label = "advanced", defaultValue = "true", javaType = "java.lang.Boolean")
    private String allowSimple;
    @XmlAttribute
    @Metadata(label = "advanced", defaultValue = "true", javaType = "java.lang.Boolean")
    private String allowEasyPredicate;
    @XmlAttribute
    @Metadata(label = "advanced", defaultValue = "true", javaType = "java.lang.Boolean")
    private String supportPojoAsMapAndList;
    @XmlAttribute
    @Metadata(defaultValue = "false", javaType = "java.lang.Boolean")
    private String writeAsString;
    @XmlAttribute
    @Metadata(label = "advanced")
    private String headerName;
    @XmlAttribute
    @Metadata(label = "advanced",
              enums = "DEFAULT_PATH_LEAF_TO_NULL,ALWAYS_RETURN_LIST,AS_PATH_LIST,SUPPRESS_EXCEPTIONS,REQUIRE_PROPERTIES")
    private String option;

    public JsonPathExpression() {
    }

    public JsonPathExpression(String expression) {
        super(expression);
    }

    public String getResultTypeName() {
        return resultTypeName;
    }

    /**
     * Sets the class name of the result type (type from output)
     */
    public void setResultTypeName(String resultTypeName) {
        this.resultTypeName = resultTypeName;
    }

    public Class<?> getResultType() {
        return resultType;
    }

    /**
     * Sets the class of the result type (type from output)
     */
    public void setResultType(Class<?> resultType) {
        this.resultType = resultType;
    }

    public String getSuppressExceptions() {
        return suppressExceptions;
    }

    public String getAllowSimple() {
        return allowSimple;
    }

    /**
     * Whether to allow in inlined Simple exceptions in the JSONPath expression
     */
    public void setAllowSimple(String allowSimple) {
        this.allowSimple = allowSimple;
    }

    public String getAllowEasyPredicate() {
        return allowEasyPredicate;
    }

    /**
     * Whether to allow using the easy predicate parser to pre-parse predicates.
     */
    public void setAllowEasyPredicate(String allowEasyPredicate) {
        this.allowEasyPredicate = allowEasyPredicate;
    }

    /**
     * Whether to suppress exceptions such as PathNotFoundException.
     */
    public void setSuppressExceptions(String suppressExceptions) {
        this.suppressExceptions = suppressExceptions;
    }

    public String getWriteAsString() {
        return writeAsString;
    }

    /**
     * Whether to write the output of each row/element as a JSON String value instead of a Map/POJO value.
     */
    public void setWriteAsString(String writeAsString) {
        this.writeAsString = writeAsString;
    }

    public String getHeaderName() {
        return headerName;
    }

    /**
     * Name of header to use as input, instead of the message body
     */
    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }

    public String getOption() {
        return option;
    }

    /**
     * To configure additional options on JSONPath. Multiple values can be separated by comma.
     */
    public void setOption(String option) {
        this.option = option;
    }

    public String getSupportPojoAsMapAndList() {
        return supportPojoAsMapAndList;
    }

    /**
     * To indicate whether a {@code Map} or {@code List} should be processed natively or the default behavior is
     * expected (Auto converted into {@code InputStream}). This flag has been added for backward compatibility reasons.
     */
    public void setSupportPojoAsMapAndList(String supportPojoAsMapAndList) {
        this.supportPojoAsMapAndList = supportPojoAsMapAndList;
    }

    @Override
    public String getLanguage() {
        return "jsonpath";
    }

}
