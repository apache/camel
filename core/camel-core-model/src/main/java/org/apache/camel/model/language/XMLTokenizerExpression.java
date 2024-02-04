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
 * Tokenize XML payloads.
 */
@Metadata(firstVersion = "2.14.0", label = "language,core,xml", title = "XML Tokenize")
@XmlRootElement(name = "xtokenize")
@XmlAccessorType(XmlAccessType.FIELD)
public class XMLTokenizerExpression extends NamespaceAwareExpression {

    @XmlAttribute
    @Metadata(defaultValue = "i", enums = "i,w,u,t")
    private String mode;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Integer")
    private String group;

    public XMLTokenizerExpression() {
    }

    public XMLTokenizerExpression(String expression) {
        super(expression);
    }

    public XMLTokenizerExpression(Expression expression) {
        setExpressionValue(expression);
    }

    private XMLTokenizerExpression(Builder builder) {
        super(builder);
        this.mode = builder.mode;
        this.group = builder.group;
    }

    @Override
    public String getLanguage() {
        return "xtokenize";
    }

    public String getMode() {
        return mode;
    }

    /**
     * The extraction mode. The available extraction modes are:
     * <ul>
     * <li>i - injecting the contextual namespace bindings into the extracted token (default)</li>
     * <li>w - wrapping the extracted token in its ancestor context</li>
     * <li>u - unwrapping the extracted token to its child content</li>
     * <li>t - extracting the text content of the specified element</li>
     * </ul>
     */
    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getGroup() {
        return group;
    }

    /**
     * To group N parts together
     */
    public void setGroup(String group) {
        this.group = group;
    }

    /**
     * {@code Builder} is a specific builder for {@link XMLTokenizerExpression}.
     */
    @XmlTransient
    public static class Builder extends AbstractNamespaceAwareBuilder<Builder, XMLTokenizerExpression> {

        private String mode;
        private String group;

        /**
         * The extraction mode. The available extraction modes are:
         * <ul>
         * <li>i - injecting the contextual namespace bindings into the extracted token (default)</li>
         * <li>w - wrapping the extracted token in its ancestor context</li>
         * <li>u - unwrapping the extracted token to its child content</li>
         * <li>t - extracting the text content of the specified element</li>
         * </ul>
         */
        public Builder mode(String mode) {
            this.mode = mode;
            return this;
        }

        /**
         * To group N parts together
         */
        public Builder group(String group) {
            this.group = group;
            return this;
        }

        /**
         * To group N parts together
         */
        public Builder group(int group) {
            this.group = Integer.toString(group);
            return this;
        }

        @Override
        public XMLTokenizerExpression end() {
            return new XMLTokenizerExpression(this);
        }
    }
}
