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
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;

import org.apache.camel.spi.Metadata;

/**
 * Camel AI: Tokenizer for splitting by sentences.
 */
@Metadata(firstVersion = "4.8.0", label = "eip,transformation,ai", title = "LangChain4J Tokenizer with sentence splitter")
@XmlRootElement(name = "langChain4jSentenceTokenizer")
@XmlAccessorType(XmlAccessType.FIELD)
public class LangChain4jSentenceTokenizerDefinition extends LangChain4jTokenizerDefinition {

    public LangChain4jSentenceTokenizerDefinition() {
    }

    public LangChain4jSentenceTokenizerDefinition(LangChain4jTokenizerDefinition source) {
        super(source);
    }

    @Override
    public LangChain4jSentenceTokenizerDefinition copyDefinition() {
        return new LangChain4jSentenceTokenizerDefinition(this);
    }

    @XmlTransient
    public static class SentenceBuilder extends Builder {
        @Override
        protected String name() {
            return toName("SentenceTokenizer");
        }

        @Override
        public LangChain4jSentenceTokenizerDefinition end() {
            LangChain4jSentenceTokenizerDefinition definition = new LangChain4jSentenceTokenizerDefinition();
            setup(definition);
            return definition;
        }
    }
}
