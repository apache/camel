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

import org.apache.camel.Expression;
import org.apache.camel.spi.Metadata;

/**
 * Evaluates a Camel simple expression.
 */
@Metadata(firstVersion = "1.1.0", label = "language,core,java", title = "Simple")
@XmlRootElement(name = "simple")
@XmlAccessorType(XmlAccessType.FIELD)
public class SimpleExpression extends ExpressionDefinition {

    @XmlAttribute(name = "resultType")
    private String resultTypeName;
    @XmlTransient
    private Class<?> resultType;

    public SimpleExpression() {
    }

    public SimpleExpression(String expression) {
        super(expression);
    }

    public SimpleExpression(Expression expression) {
        super(expression);
    }

    private SimpleExpression(Builder builder) {
        super(builder);
        this.resultTypeName = builder.resultTypeName;
        this.resultType = builder.resultType;
    }

    @Override
    public String getLanguage() {
        return "simple";
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

    public String getResultTypeName() {
        return resultTypeName;
    }

    /**
     * Sets the class name of the result type (type from output)
     */
    public void setResultTypeName(String resultTypeName) {
        this.resultTypeName = resultTypeName;
    }

    /**
     * {@code Builder} is a specific builder for {@link SimpleExpression}.
     */
    @XmlTransient
    public static class Builder extends AbstractBuilder<Builder, SimpleExpression> {

        private String resultTypeName;
        private Class<?> resultType;

        /**
         * Sets the class of the result type (type from output)
         */
        public Builder resultType(Class<?> resultType) {
            this.resultType = resultType;
            return this;
        }

        /**
         * Sets the class name of the result type (type from output)
         */
        public Builder resultTypeName(String resultTypeName) {
            this.resultTypeName = resultTypeName;
            return this;
        }

        @Override
        public SimpleExpression end() {
            return new SimpleExpression(this);
        }
    }
}
