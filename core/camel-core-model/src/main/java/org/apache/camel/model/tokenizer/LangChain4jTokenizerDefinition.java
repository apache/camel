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
package org.apache.camel.model.tokenizer;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlTransient;
import jakarta.xml.bind.annotation.XmlType;

import org.apache.camel.builder.TokenizerBuilder;
import org.apache.camel.model.TokenizerImplementationDefinition;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.Tokenizer;
import org.apache.camel.util.StringHelper;

/**
 * Camel AI: Tokenizer.
 */
@Metadata(firstVersion = "4.8.0", label = "eip,transformation,ai", title = "LangChain4J Tokenizer")
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "langChain4jTokenizerImplementation")
public class LangChain4jTokenizerDefinition extends TokenizerImplementationDefinition {

    @XmlAttribute(required = true)
    @Metadata(javaType = "org.apache.camel.model.tokenizer.TokenizerType", required = true,
              enums = "OPEN_AI,AZURE,QWEN")
    private String tokenizerType;

    @XmlAttribute(required = true)
    @Metadata(javaType = "java.lang.Integer", required = true)
    private String maxTokens;

    @XmlAttribute(required = true)
    @Metadata(javaType = "java.lang.Integer", required = true)
    private String maxOverlap;

    public LangChain4jTokenizerDefinition() {
    }

    public LangChain4jTokenizerDefinition(LangChain4jTokenizerDefinition source) {
        super(source);
        this.maxTokens = source.maxTokens;
        this.maxOverlap = source.maxOverlap;
        this.tokenizerType = source.tokenizerType;
    }

    /**
     * The maximum number of tokens on each segment
     */
    public String getMaxTokens() {
        return maxTokens;
    }

    /**
     * Sets the maximum number of tokens on each segment
     */
    public void setMaxTokens(String maxTokens) {
        this.maxTokens = maxTokens;
    }

    /**
     * Gets the maximum number of tokens that can overlap in each segment
     */
    public String getMaxOverlap() {
        return maxOverlap;
    }

    /**
     * Sets the maximum number of tokens that can overlap in each segment
     */
    public void setMaxOverlap(String maxOverlap) {
        this.maxOverlap = maxOverlap;
    }

    /**
     * Gets the tokenizer type
     */
    public String getTokenizerType() {
        return tokenizerType;
    }

    /**
     * Sets the tokenizer type
     */
    public void setTokenizerType(String tokenizerType) {
        this.tokenizerType = tokenizerType;
    }

    @Override
    public LangChain4jTokenizerDefinition copyDefinition() {
        throw new UnsupportedOperationException("Must be implemented in the concrete classes");
    }

    protected static String toName(String name) {
        return "langChain4j" + StringHelper.capitalize(name);
    }

    @XmlTransient
    public enum TokenizerType {
        OPEN_AI,
        AZURE,
        QWEN
    }

    @XmlTransient
    public static abstract class Builder implements TokenizerBuilder<LangChain4jTokenizerDefinition> {
        private int maxTokens;
        private int maxOverlap;
        private TokenizerType tokenizerType;
        private Tokenizer.Configuration configuration;

        /**
         * Sets the maximum number of tokens in each segment
         */
        public Builder maxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        /**
         * Sets the maximum number of tokens that can overlap in each segment
         */
        public Builder maxOverlap(int maxOverlap) {
            this.maxOverlap = maxOverlap;
            return this;
        }

        /**
         * Sets the tokenizer type. Must be one of TokenizerType.OPEN_AI (the default), TokenizerType.AZURE or
         * TokenizerType.QWEN
         */
        public Builder using(TokenizerType tokenizer) {
            this.tokenizerType = tokenizer;
            return this;
        }

        public Builder configuration(Tokenizer.Configuration configuration) {
            this.configuration = configuration;
            return this;
        }

        protected void setup(LangChain4jTokenizerDefinition tokenizer) {
            if (configuration != null) {
                tokenizer.setConfiguration(configuration);
            } else {
                tokenizer.setMaxTokens(Integer.toString(maxTokens));
                tokenizer.setMaxOverlap(Integer.toString(maxOverlap));
                tokenizer.setTokenizerType(tokenizerType.name());
            }

            tokenizer.setTokenizerName(name());
        }

        protected abstract String name();
    }

}
