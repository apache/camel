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

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;

import org.apache.camel.Expression;
import org.apache.camel.spi.Metadata;

/**
 * Evaluates a Camel simple expression.
 */
@Metadata(firstVersion = "1.1.0", label = "language,core,java", title = "Simple")
@XmlRootElement(name = "simple")
@XmlAccessorType(XmlAccessType.FIELD)
public class SimpleExpression extends TypedExpressionDefinition {

    @XmlAttribute
    @Metadata(defaultValue = "false", javaType = "java.lang.Boolean")
    private String trimResult;
    @XmlAttribute
    @Metadata(defaultValue = "false", javaType = "java.lang.Boolean")
    private String pretty;
    @XmlAttribute
    @Metadata(defaultValue = "false", javaType = "java.lang.Boolean", label = "advanced")
    private String nested;

    public SimpleExpression() {
    }

    protected SimpleExpression(SimpleExpression source) {
        super(source);
        this.trimResult = source.trimResult;
        this.pretty = source.pretty;
        this.nested = source.nested;
    }

    public SimpleExpression(String expression) {
        super(expression);
    }

    public SimpleExpression(Expression expression) {
        super(expression);
    }

    private SimpleExpression(Builder builder) {
        super(builder);
        this.trimResult = builder.trimResult;
        this.pretty = builder.pretty;
        this.nested = builder.nested;
    }

    @Override
    public SimpleExpression copyDefinition() {
        return new SimpleExpression(this);
    }

    @Override
    public String getLanguage() {
        return "simple";
    }

    public String getTrimResult() {
        return trimResult;
    }

    /**
     * Whether to trim the returned values when this language are in use.
     *
     * For example the output result may contain unwanted line breaks at the beginning and end such as when using Java
     * DSL with multi-line blocks.
     *
     * Is default false to be backwards compatible with existing behavior.
     */
    public void setTrimResult(String trimResult) {
        this.trimResult = trimResult;
    }

    public String getPretty() {
        return pretty;
    }

    /**
     * To pretty format the output (only JSon or XML supported)
     */
    public void setPretty(String pretty) {
        this.pretty = pretty;
    }

    public String getNested() {
        return nested;
    }

    /**
     * If the result is a nested simple expression should this expression be evaluated as well
     */
    public void setNested(String nested) {
        this.nested = nested;
    }

    /**
     * {@code Builder} is a specific builder for {@link SimpleExpression}.
     */
    @XmlTransient
    public static class Builder extends AbstractBuilder<Builder, SimpleExpression> {

        private String trimResult;
        private String pretty;
        private String nested;

        /**
         * Whether to trim the returned values when this language are in use.
         *
         * For example the output result may contain unwanted line breaks at the beginning and end such as when using
         * Java DSL with multi-line blocks.
         *
         * Is default false to be backwards compatible with existing behavior.
         */
        public Builder trimResult(String trimResult) {
            this.trimResult = trimResult;
            return this;
        }

        /**
         * Whether to trim the returned values when this language are in use.
         *
         * For example the output result may contain unwanted line breaks at the beginning and end such as when using
         * Java DSL with multi-line blocks.
         *
         * Is default false to be backwards compatible with existing behavior.
         */
        public Builder trimResult(boolean trimResult) {
            this.trimResult = Boolean.toString(trimResult);
            return this;
        }

        /**
         * To pretty format the output (only JSon or XML supported)
         */
        public Builder pretty(String pretty) {
            this.pretty = pretty;
            return this;
        }

        /**
         * To pretty format the output (only JSon or XML supported)
         */
        public Builder pretty(boolean pretty) {
            this.pretty = Boolean.toString(pretty);
            return this;
        }

        /**
         * If the result is a nested simple expression should this expression be evaluated as well
         */
        public Builder nested(String nested) {
            this.nested = nested;
            return this;
        }

        /**
         * If the result is a nested simple expression should this expression be evaluated as well
         */
        public Builder nested(boolean nested) {
            this.nested = Boolean.toString(nested);
            return this;
        }

        @Override
        public SimpleExpression end() {
            return new SimpleExpression(this);
        }
    }
}
