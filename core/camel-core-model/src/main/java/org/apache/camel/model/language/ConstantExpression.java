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
 * A fixed value set only once during the route startup.
 */
@Metadata(firstVersion = "1.5.0", label = "language,core", title = "Constant")
@XmlRootElement(name = "constant")
@XmlAccessorType(XmlAccessType.FIELD)
public class ConstantExpression extends ExpressionDefinition {

    @XmlAttribute(name = "resultType")
    private String resultTypeName;
    @XmlTransient
    private Class<?> resultType;

    public ConstantExpression() {
    }

    public ConstantExpression(String expression) {
        super(expression);
    }

    private ConstantExpression(Builder builder) {
        super(builder);
        this.resultTypeName = builder.resultTypeName;
        this.resultType = builder.resultType;
    }

    @Override
    public String getLanguage() {
        return "constant";
    }

    public Class<?> getResultType() {
        return resultType;
    }

    /**
     * Sets the class of the constant type
     */
    public void setResultType(Class<?> resultType) {
        this.resultType = resultType;
    }

    public String getResultTypeName() {
        return resultTypeName;
    }

    /**
     * Sets the class name of the constant type
     */
    public void setResultTypeName(String resultTypeName) {
        this.resultTypeName = resultTypeName;
    }

    /**
     * {@code Builder} is a specific builder for {@link ConstantExpression}.
     */
    @XmlTransient
    public static class Builder extends AbstractBuilder<Builder, ConstantExpression> {

        private String resultTypeName;
        private Class<?> resultType;

        /**
         * Sets the class of the constant type
         */
        public Builder resultType(Class<?> resultType) {
            this.resultType = resultType;
            return this;
        }

        /**
         * Sets the class name of the constant type
         */
        public Builder resultTypeName(String resultTypeName) {
            this.resultTypeName = resultTypeName;
            return this;
        }

        @Override
        public ConstantExpression end() {
            return new ConstantExpression(this);
        }
    }
}
