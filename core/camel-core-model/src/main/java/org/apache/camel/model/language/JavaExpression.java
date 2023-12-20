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
 * Evaluates a Java (Java compiled once at runtime) expression.
 */
@Metadata(firstVersion = "4.3.0", label = "language", title = "Java")
@XmlRootElement(name = "java")
@XmlAccessorType(XmlAccessType.FIELD)
public class JavaExpression extends TypedExpressionDefinition {

    @XmlAttribute
    @Metadata(label = "advanced", defaultValue = "true", javaType = "java.lang.Boolean")
    private String preCompile;
    @XmlAttribute
    @Metadata(label = "advanced", defaultValue = "true", javaType = "java.lang.Boolean")
    private String singleQuotes;

    public JavaExpression() {
    }

    public JavaExpression(String expression) {
        super(expression);
    }

    private JavaExpression(Builder builder) {
        super(builder);
        this.preCompile = builder.preCompile;
        this.singleQuotes = builder.singleQuotes;
    }

    @Override
    public String getLanguage() {
        return "java";
    }

    public String getPreCompile() {
        return preCompile;
    }

    /**
     * Whether the expression should be pre compiled once during initialization phase. If this is turned off, then the
     * expression is reloaded and compiled on each evaluation.
     */
    public void setPreCompile(String preCompile) {
        this.preCompile = preCompile;
    }

    public String getSingleQuotes() {
        return singleQuotes;
    }

    /**
     * Whether single quotes can be used as replacement for double quotes. This is convenient when you need to work with
     * strings inside strings.
     */
    public void setSingleQuotes(String singleQuotes) {
        this.singleQuotes = singleQuotes;
    }

    /**
     * {@code Builder} is a specific builder for {@link JavaExpression}.
     */
    @XmlTransient
    public static class Builder extends AbstractBuilder<Builder, JavaExpression> {

        private String preCompile;
        private String singleQuotes;

        /**
         * Whether the expression should be pre compiled once during initialization phase. If this is turned off, then
         * the expression is reloaded and compiled on each evaluation.
         */
        public Builder preCompile(String preCompile) {
            this.preCompile = preCompile;
            return this;
        }

        /**
         * Whether the expression should be pre compiled once during initialization phase. If this is turned off, then
         * the expression is reloaded and compiled on each evaluation.
         */
        public Builder preCompile(boolean preCompile) {
            this.preCompile = Boolean.toString(preCompile);
            return this;
        }

        /**
         * Whether single quotes can be used as replacement for double quotes. This is convenient when you need to work
         * with strings inside strings.
         */
        public Builder singleQuotes(String singleQuotes) {
            this.singleQuotes = singleQuotes;
            return this;
        }

        /**
         * Whether single quotes can be used as replacement for double quotes. This is convenient when you need to work
         * with strings inside strings.
         */
        public Builder singleQuotes(boolean singleQuotes) {
            this.singleQuotes = Boolean.toString(singleQuotes);
            return this;
        }

        @Override
        public JavaExpression end() {
            return new JavaExpression(this);
        }
    }
}
