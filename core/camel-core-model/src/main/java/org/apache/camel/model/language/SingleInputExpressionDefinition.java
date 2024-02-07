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

import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlTransient;

import org.apache.camel.Expression;
import org.apache.camel.spi.Metadata;

/**
 * Expression for which the source of the input (body, header or property) can be provided.
 */
@Deprecated
public abstract class SingleInputExpressionDefinition extends ExpressionDefinition {

    @XmlAttribute
    private String variableName;
    @XmlAttribute
    @Metadata(label = "advanced")
    private String headerName;
    @XmlAttribute
    @Metadata(label = "advanced")
    private String propertyName;

    protected SingleInputExpressionDefinition() {
    }

    protected SingleInputExpressionDefinition(String expression) {
        super(expression);
    }

    protected SingleInputExpressionDefinition(Expression expression) {
        super(expression);
    }

    protected SingleInputExpressionDefinition(AbstractBuilder<?, ?> builder) {
        super(builder);
        this.variableName = builder.variableName;
        this.headerName = builder.headerName;
        this.propertyName = builder.propertyName;
    }

    public String getVariableName() {
        return variableName;
    }

    /**
     * Name of variable to use as input, instead of the message body
     */
    public void setVariableName(String variableName) {
        this.variableName = variableName;
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
     * {@code AbstractBuilder} is the base builder for typed expression.
     */
    @XmlTransient
    @SuppressWarnings("unchecked")
    public abstract static class AbstractBuilder<
            T extends AbstractBuilder<T, E>, E extends SingleInputExpressionDefinition>
            extends ExpressionDefinition.AbstractBuilder<T, E> {

        private String variableName;
        private String headerName;
        private String propertyName;

        /**
         * Name of variable to use as input, instead of the message body
         */
        public T variableName(String variableName) {
            this.variableName = variableName;
            return (T) this;
        }

        /**
         * Name of header to use as input, instead of the message body
         */
        public T headerName(String headerName) {
            this.headerName = headerName;
            return (T) this;
        }

        /**
         * Name of property to use as input, instead of the message body.
         * </p>
         * It has a lower precedent than the headerName if both are set.
         */
        public T propertyName(String propertyName) {
            this.propertyName = propertyName;
            return (T) this;
        }
    }
}
