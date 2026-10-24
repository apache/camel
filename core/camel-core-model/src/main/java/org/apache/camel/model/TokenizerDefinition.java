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
import org.apache.camel.model.tokenizer.LangChain4jLineTokenizerDefinition;
import org.apache.camel.model.tokenizer.LangChain4jParagraphTokenizerDefinition;
import org.apache.camel.model.tokenizer.LangChain4jSentenceTokenizerDefinition;
import org.apache.camel.model.tokenizer.LangChain4jWordTokenizerDefinition;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.Metadata;

/**
 * Represents a Camel tokenizer for AI.
 */
@Metadata(firstVersion = "4.8.0", label = "eip,transformation,ai", title = "Specialized tokenizer for AI applications",
          description = "Tokenizes the message body for AI processing, splitting text into chunks suitable for embedding or LLM input")
@XmlRootElement(name = "tokenizer")
@XmlAccessorType(XmlAccessType.FIELD)
@BodyTypes(eipIn = @BodyType(value = Carrier.TEXT,
                             description = "The text to split into chunks"),
           eipOut = @BodyType(value = Carrier.LIST,
                              javaType = "java.lang.String[]",
                              description = "The chunks of the text, as an array"))
public class TokenizerDefinition extends NoOutputDefinition<TokenizerDefinition> {

    @XmlElements({
            @XmlElement(name = "langChain4jCharacterTokenizer", type = LangChain4jCharacterTokenizerDefinition.class),
            @XmlElement(name = "langChain4jLineTokenizer", type = LangChain4jLineTokenizerDefinition.class),
            @XmlElement(name = "langChain4jParagraphTokenizer", type = LangChain4jParagraphTokenizerDefinition.class),
            @XmlElement(name = "langChain4jSentenceTokenizer", type = LangChain4jSentenceTokenizerDefinition.class),
            @XmlElement(name = "langChain4jWordTokenizer", type = LangChain4jWordTokenizerDefinition.class),
    })
    @Metadata(description = "The tokenizer implementation to use, such as LangChain4j character, line, paragraph, sentence, or word tokenizers.")
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
     * Gets the tokenizer name, which is the name of the tokenizer implementation when not set: the XML and YAML DSL set
     * only the implementation (CAMEL-25777)
     */
    public String tokenizerName() {
        if (tokenizerName == null && tokenizerImplementation != null) {
            return tokenizerImplementation.tokenizerName();
        }
        return tokenizerName;
    }

    public void setTokenizerName(String tokenizerName) {
        this.tokenizerName = tokenizerName;
    }

    public TokenizerImplementationDefinition getTokenizerImplementation() {
        return tokenizerImplementation;
    }

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
