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
import jakarta.xml.bind.annotation.XmlTransient;
import jakarta.xml.bind.annotation.XmlType;

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.Tokenizer;

@Metadata(firstVersion = "4.8.0", label = "eip,transformation,ai", title = "LangChain4J Tokenizer")
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "tokenizerImplementation")
public class TokenizerImplementationDefinition extends IdentifiedType
        implements CopyableDefinition<TokenizerImplementationDefinition> {

    @XmlTransient
    private String tokenizerName;
    @XmlTransient
    private Tokenizer.Configuration configuration;

    public TokenizerImplementationDefinition() {
    }

    protected TokenizerImplementationDefinition(TokenizerImplementationDefinition source) {
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
    public TokenizerImplementationDefinition copyDefinition() {
        throw new UnsupportedOperationException("Must be implemented in the concrete classes");
    }
}
