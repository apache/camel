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
import org.apache.camel.spi.Label;
import org.apache.camel.util.ObjectHelper;

/**
 * For JSonPath expressions and predicates
 *
 * @version 
 */
@Label("language")
@XmlRootElement(name = "jsonpath")
@XmlAccessorType(XmlAccessType.FIELD)
public class JsonPathExpression extends ExpressionDefinition {

    @XmlAttribute(name = "resultType")
    private String resultTypeName;

    @XmlTransient
    private Class<?> resultType;

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
        super.configureExpression(camelContext, expression);
    }

    @Override
    protected void configurePredicate(CamelContext camelContext, Predicate predicate) {
        if (resultType != null) {
            setProperty(predicate, "resultType", resultType);
        }
        super.configurePredicate(camelContext, predicate);
    }

}