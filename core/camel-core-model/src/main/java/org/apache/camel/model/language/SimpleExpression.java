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
    private String pretty;

    public SimpleExpression() {
    }

    protected SimpleExpression(SimpleExpression source) {
        super(source);
        this.pretty = source.pretty;
    }

    public SimpleExpression(String expression) {
        super(expression);
    }

    public SimpleExpression(Expression expression) {
        super(expression);
    }

    private SimpleExpression(Builder builder) {
        super(builder);
        this.pretty = builder.pretty;
    }

    @Override
    public SimpleExpression copyDefinition() {
        return new SimpleExpression(this);
    }

    @Override
    public String getLanguage() {
        return "simple";
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

    /**
     * {@code Builder} is a specific builder for {@link SimpleExpression}.
     */
    @XmlTransient
    public static class Builder extends AbstractBuilder<Builder, SimpleExpression> {

        private String pretty;

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

        @Override
        public SimpleExpression end() {
            return new SimpleExpression(this);
        }
    }
}
