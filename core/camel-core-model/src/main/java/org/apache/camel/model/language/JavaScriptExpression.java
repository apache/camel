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
 * Evaluates a JavaScript expression.
 */
@Metadata(firstVersion = "3.20.0", label = "language,javascript", title = "JavaScript")
@XmlRootElement(name = "js")
@XmlAccessorType(XmlAccessType.FIELD)
public class JavaScriptExpression extends ExpressionDefinition {

    @XmlAttribute(name = "resultType")
    private String resultTypeName;
    @XmlTransient
    private Class<?> resultType;
    @XmlAttribute
    @Metadata(label = "advanced")
    private String headerName;
    @Metadata(label = "advanced")
    private String propertyName;

    public JavaScriptExpression() {
    }

    public JavaScriptExpression(String expression) {
        super(expression);
    }

    private JavaScriptExpression(JavaScriptExpression.Builder builder) {
        super(builder);
        this.resultTypeName = builder.resultTypeName;
        this.resultType = builder.resultType;
        this.headerName = builder.headerName;
        this.propertyName = builder.propertyName;
    }

    @Override
    public String getLanguage() {
        return "js";
    }

    public String getResultTypeName() {
        return resultTypeName;
    }

    /**
     * Sets the class of the result type (type from output)
     */
    public void setResultTypeName(String resultTypeName) {
        this.resultTypeName = resultTypeName;
    }

    public Class<?> getResultType() {
        return resultType;
    }

    /**
     * Sets the class name of the result type (type from output)
     */
    public void setResultType(Class<?> resultType) {
        this.resultType = resultType;
    }

    public String getHeaderName() {
        return headerName;
    }

    /**
     * Name of header to use as input, instead of the message body
     * </p>
     * It has as higher precedent than the propertyName if both are set.
     */
    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }

    public String getPropertyName() {
        return propertyName;
    }

    /**
     * Name of property to use as input, instead of the message body.
     * </p>
     * It has a lower precedent than the headerName if both are set.
     */
    public void setPropertyName(String propertyName) {
        this.propertyName = propertyName;
    }

    /**
     * {@code Builder} is a specific builder for {@link JavaScriptExpression}.
     */
    @XmlTransient
    public static class Builder extends AbstractBuilder<JavaScriptExpression.Builder, JavaScriptExpression> {

        private String resultTypeName;
        private Class<?> resultType;
        private String headerName;
        private String propertyName;

        /**
         * Sets the class name of the result type (type from output)
         */
        public JavaScriptExpression.Builder resultType(Class<?> resultType) {
            this.resultType = resultType;
            return this;
        }

        /**
         * Sets the class of the result type (type from output)
         */
        public JavaScriptExpression.Builder resultTypeName(String resultTypeName) {
            this.resultTypeName = resultTypeName;
            return this;
        }

        /**
         * Name of header to use as input, instead of the message body
         * </p>
         * It has as higher precedent than the propertyName if both are set.
         */
        public JavaScriptExpression.Builder headerName(String headerName) {
            this.headerName = headerName;
            return this;
        }

        /**
         * Name of property to use as input, instead of the message body.
         * </p>
         * It has a lower precedent than the headerName if both are set.
         */
        public JavaScriptExpression.Builder propertyName(String propertyName) {
            this.propertyName = propertyName;
            return this;
        }

        @Override
        public JavaScriptExpression end() {
            return new JavaScriptExpression(this);
        }
    }
}
