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

package org.apache.camel.model;

import jakarta.xml.bind.annotation.XmlTransient;

import org.apache.camel.spi.Tokenizer;

/**
 * Represents a Camel tokenizer
 */
public abstract class TokenizerDefinition extends NoOutputDefinition<TokenizerDefinition> {
    @XmlTransient
    private String tokenizerName;

    @XmlTransient
    private Tokenizer.Configuration configuration;

    public TokenizerDefinition() {
    }

    protected TokenizerDefinition(TokenizerDefinition source) {
        this.tokenizerName = source.tokenizerName;
        this.configuration = source.configuration;
    }

    /**
     * Gets the tokenizer name
     */
    public String tokenizerName() {
        return tokenizerName;
    }

    /**
     * Sets the tokenizer name
     */
    public void setTokenizerName(String tokenizerName) {
        this.tokenizerName = tokenizerName;
    }

    /**
     * Gets the tokenizer configuration
     */
    public Tokenizer.Configuration configuration() {
        return configuration;
    }

    /**
     * Sets the tokenizer configuration
     */
    public void setConfiguration(Tokenizer.Configuration configuration) {
        this.configuration = configuration;
    }

    @Override
    public String getShortName() {
        return "tokenizer";
    }

    @Override
    public String getLabel() {
        return "tokenizer";
    }
}
