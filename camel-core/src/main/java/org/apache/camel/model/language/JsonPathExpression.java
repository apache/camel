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
package org.apache.camel.model.language;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.CamelContext;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.spi.Metadata;
import org.apache.camel.util.ObjectHelper;

/**
 * To use JsonPath in Camel expressions or predicates.
 *
 * @version 
 */
@Metadata(firstVersion = "2.13.0", label = "language,json", title = "JsonPath")
@XmlRootElement(name = "jsonpath")
@XmlAccessorType(XmlAccessType.FIELD)
public class JsonPathExpression extends ExpressionDefinition {

    @XmlAttribute(name = "resultType")
    private String resultTypeName;
    @XmlTransient
    private Class<?> resultType;
    @XmlAttribute @Metadata(defaultValue = "false")
    private Boolean suppressExceptions;
    @XmlAttribute @Metadata(defaultValue = "true")
    private Boolean allowSimple;
    @XmlAttribute @Metadata(defaultValue = "true")
    private Boolean allowEasyPredicate;
    @XmlAttribute @Metadata(defaultValue = "false")
    private Boolean writeAsString;
    @XmlAttribute
    private String headerName;

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

    public Boolean getSuppressExceptions() {
        return suppressExceptions;
    }

    public Boolean getAllowSimple() {
        return allowSimple;
    }

    /**
     * Whether to allow in inlined simple exceptions in the JsonPath expression
     */
    public void setAllowSimple(Boolean allowSimple) {
        this.allowSimple = allowSimple;
    }

    public Boolean getAllowEasyPredicate() {
        return allowEasyPredicate;
    }

    /**
     * Whether to allow using the easy predicate parser to pre-parse predicates.
     */
    public void setAllowEasyPredicate(Boolean allowEasyPredicate) {
        this.allowEasyPredicate = allowEasyPredicate;
    }

    /**
     * Whether to suppress exceptions such as PathNotFoundException.
     */
    public void setSuppressExceptions(Boolean suppressExceptions) {
        this.suppressExceptions = suppressExceptions;
    }

    public Boolean getWriteAsString() {
        return writeAsString;
    }

    /**
     * Whether to write the output of each row/element as a JSON String value instead of a Map/POJO value.
     */
    public void setWriteAsString(Boolean writeAsString) {
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

    public String getLanguage() {
        return "jsonpath";
    }

    @Override
    public Expression createExpression(CamelContext camelContext) {
        if (resultType == null && resultTypeName != null) {
            try {
                resultType = camelContext.getClassResolver().resolveMandatoryClass(resultTypeName);
            } catch (ClassNotFoundException e) {
                throw ObjectHelper.wrapRuntimeCamelException(e);
            }
        }
        return super.createExpression(camelContext);
    }

    @Override
    protected void configureExpression(CamelContext camelContext, Expression expression) {
        if (resultType != null) {
            setProperty(expression, "resultType", resultType);
        }
        if (suppressExceptions != null) {
            setProperty(expression, "suppressExceptions", suppressExceptions);
        }
        if (allowSimple != null) {
            setProperty(expression, "allowSimple", allowSimple);
        }
        if (allowEasyPredicate != null) {
            setProperty(expression, "allowEasyPredicate", allowEasyPredicate);
        }
        if (writeAsString != null) {
            setProperty(expression, "writeAsString", writeAsString);
        }
        if (headerName != null) {
            setProperty(expression, "headerName", headerName);
        }
        super.configureExpression(camelContext, expression);
    }

    @Override
    protected void configurePredicate(CamelContext camelContext, Predicate predicate) {
        if (resultType != null) {
            setProperty(predicate, "resultType", resultType);
        }
        if (suppressExceptions != null) {
            setProperty(predicate, "suppressExceptions", suppressExceptions);
        }
        if (allowSimple != null) {
            setProperty(predicate, "allowSimple", allowSimple);
        }
        if (allowEasyPredicate != null) {
            setProperty(predicate, "allowEasyPredicate", allowEasyPredicate);
        }
        if (writeAsString != null) {
            setProperty(predicate, "writeAsString", writeAsString);
        }
        if (headerName != null) {
            setProperty(predicate, "headerName", headerName);
        }
        super.configurePredicate(camelContext, predicate);
    }

}