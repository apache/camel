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

package org.apache.camel.builder;

import org.apache.camel.model.tokenizer.LangChain4jTokenizerDefinition;

/**
 * {@code TokenizerBuilderFactory} is a factory class of builder of all supported tokenizers.
 */
public class TokenizerBuilderFactory {

    /**
     * Creates a new tokenizer builder for a tokenizer that splits texts in segments separated by paragraphs
     */
    public LangChain4jTokenizerDefinition.ParagraphBuilder byParagraph() {
        return new LangChain4jTokenizerDefinition.ParagraphBuilder();
    }

    /**
     * Creates a new tokenizer builder for a tokenizer that splits texts in segments separated by line
     */
    public LangChain4jTokenizerDefinition.LineBuilder byLine() {
        return new LangChain4jTokenizerDefinition.LineBuilder();
    }

    /**
     * Creates a new tokenizer builder for a tokenizer that splits texts in segments separated by word
     */
    public LangChain4jTokenizerDefinition.WordBuilder byWord() {
        return new LangChain4jTokenizerDefinition.WordBuilder();
    }

    /**
     * Creates a new tokenizer builder for a tokenizer that splits texts in segments separated by sentence
     */
    public LangChain4jTokenizerDefinition.SentenceBuilder bySentence() {
        return new LangChain4jTokenizerDefinition.SentenceBuilder();
    }

    /**
     * Creates a new tokenizer builder for a tokenizer that splits texts in segments separated by character
     */
    public LangChain4jTokenizerDefinition.CharacterBuilder byCharacter() {
        return new LangChain4jTokenizerDefinition.CharacterBuilder();
    }
}
