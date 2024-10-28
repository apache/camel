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

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElements;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;

import org.apache.camel.model.tokenizer.LangChain4jCharacterTokenizerDefinition;
import org.apache.camel.model.tokenizer.LangChain4jParagraphTokenizerDefinition;
import org.apache.camel.model.tokenizer.LangChain4jSentenceTokenizerDefinition;
import org.apache.camel.model.tokenizer.LangChain4jTokenizerDefinition;
import org.apache.camel.model.tokenizer.LangChain4jWordTokenizerDefinition;
import org.apache.camel.spi.Metadata;

/**
 * Represents a Camel tokenizer for AI.
 */
@Metadata(firstVersion = "4.8.0", label = "eip,transformation,ai", title = "Specialized tokenizer for AI applications")
@XmlRootElement(name = "tokenizer")
@XmlAccessorType(XmlAccessType.FIELD)
public class TokenizerDefinition extends NoOutputDefinition<TokenizerDefinition> {

    @XmlElements({
            @XmlElement(name = "langChain4jCharacterTokenizer", type = LangChain4jCharacterTokenizerDefinition.class),
            @XmlElement(name = "langChain4jLineTokenizer", type = LangChain4jTokenizerDefinition.class),
            @XmlElement(name = "langChain4jParagraphTokenizer", type = LangChain4jParagraphTokenizerDefinition.class),
            @XmlElement(name = "langChain4jSentenceTokenizer", type = LangChain4jSentenceTokenizerDefinition.class),
            @XmlElement(name = "langChain4jWordTokenizer", type = LangChain4jWordTokenizerDefinition.class),
    })
    private TokenizerImplementationDefinition tokenizerImplementation;

    @XmlTransient
    private String tokenizerName;

    public TokenizerDefinition() {
    }

    protected TokenizerDefinition(TokenizerDefinition source) {
        this.tokenizerName = source.tokenizerName;
        this.tokenizerImplementation = source.tokenizerImplementation;
    }

    public TokenizerDefinition(TokenizerImplementationDefinition tokenizerImplementation) {
        this.tokenizerImplementation = tokenizerImplementation;
        this.tokenizerName = tokenizerImplementation.tokenizerName();
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
     * Gets the tokenizer implementation
     */
    public TokenizerImplementationDefinition getTokenizerImplementation() {
        return tokenizerImplementation;
    }

    /**
     * Sets the tokenizer implementation
     */
    public void setTokenizerImplementation(TokenizerImplementationDefinition tokenizerImplementation) {
        this.tokenizerImplementation = tokenizerImplementation;
    }

    @Override
    public String getShortName() {
        return "tokenizer";
    }

    @Override
    public String getLabel() {
        return "tokenizer";
    }

    @Override
    public TokenizerDefinition copyDefinition() {
        return new TokenizerDefinition(this);
    }
}
