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
 * Expression for which a result type can be provided along with the source of the input (body, header or property).
 */
public abstract class SingleInputTypedExpressionDefinition extends TypedExpressionDefinition {

    @XmlAttribute
    @Metadata(label = "advanced")
    private String headerName;
    @XmlAttribute
    @Metadata(label = "advanced")
    private String propertyName;

    protected SingleInputTypedExpressionDefinition() {
    }

    protected SingleInputTypedExpressionDefinition(String expression) {
        super(expression);
    }

    protected SingleInputTypedExpressionDefinition(Expression expression) {
        super(expression);
    }

    protected SingleInputTypedExpressionDefinition(AbstractBuilder<?, ?> builder) {
        super(builder);
        this.headerName = builder.headerName;
        this.propertyName = builder.propertyName;
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
     * {@code AbstractBuilder} is the base builder for typed expression.
     */
    @XmlTransient
    @SuppressWarnings("unchecked")
    public abstract static class AbstractBuilder<
            T extends AbstractBuilder<T, E>, E extends SingleInputTypedExpressionDefinition>
            extends TypedExpressionDefinition.AbstractBuilder<T, E> {

        private String headerName;
        private String propertyName;

        /**
         * Name of header to use as input, instead of the message body
         * </p>
         * It has as higher precedent than the propertyName if both are set.
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
