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

import org.apache.camel.spi.Metadata;

/**
 * Evaluate a compiled simple expression.
 */
@Metadata(firstVersion = "3.7.0", label = "language,java", title = "CSimple")
@XmlRootElement(name = "csimple")
@XmlAccessorType(XmlAccessType.FIELD)
public class CSimpleExpression extends TypedExpressionDefinition {

    @XmlAttribute
    @Metadata(defaultValue = "false", javaType = "java.lang.Boolean")
    private String trimResult;
    @XmlAttribute
    @Metadata(defaultValue = "false", javaType = "java.lang.Boolean")
    private String pretty;

    public CSimpleExpression() {
    }

    protected CSimpleExpression(CSimpleExpression source) {
        super(source);
        this.trimResult = source.trimResult;
        this.pretty = source.pretty;
    }

    public CSimpleExpression(String expression) {
        super(expression);
    }

    private CSimpleExpression(Builder builder) {
        super(builder);
        this.trimResult = builder.trimResult;
        this.pretty = builder.pretty;
    }

    @Override
    public CSimpleExpression copyDefinition() {
        return new CSimpleExpression(this);
    }

    @Override
    public String getLanguage() {
        return "csimple";
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

    /**
     * {@code Builder} is a specific builder for {@link CSimpleExpression}.
     */
    @XmlTransient
    public static class Builder extends AbstractBuilder<Builder, CSimpleExpression> {

        private String trimResult;
        private String pretty;

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

        @Override
        public CSimpleExpression end() {
            return new CSimpleExpression(this);
        }
    }
}
