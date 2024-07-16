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

package org.apache.camel.reifier.tokenizer;

import org.apache.camel.Route;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.TokenizerDefinition;
import org.apache.camel.model.tokenizer.LangChain4jTokenizerDefinition;
import org.apache.camel.spi.Tokenizer;

public class LangChain4JTokenizerReifier extends TokenizerReifier<LangChain4jTokenizerDefinition> {
    public LangChain4JTokenizerReifier(Route route, ProcessorDefinition definition) {
        this(route, (LangChain4jTokenizerDefinition) definition);
    }

    public LangChain4JTokenizerReifier(Route route, TokenizerDefinition definition) {
        super(route, (LangChain4jTokenizerDefinition) definition);
    }

    @Override
    protected void configure(Tokenizer tokenizer) {
        Tokenizer.Configuration configuration = definition.configuration();
        if (configuration == null) {
            configuration = tokenizer.newConfiguration();

            configuration.setMaxOverlap(Integer.valueOf(definition.getMaxOverlap()));
            configuration.setMaxTokens(Integer.valueOf(definition.getMaxTokens()));
            configuration.setType(definition.getTokenizerType());
        }

        tokenizer.configure(configuration);
    }
}
