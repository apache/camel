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

import java.util.Optional;

import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.model.TokenizerDefinition;
import org.apache.camel.processor.TokenizerProcessor;
import org.apache.camel.reifier.ProcessorReifier;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.spi.Tokenizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class TokenizerReifier<T extends TokenizerDefinition> extends ProcessorReifier<T> {
    private static final Logger LOG = LoggerFactory.getLogger(TokenizerReifier.class);
    private static final String TOKENIZER_PATH = FactoryFinder.DEFAULT_PATH + "tokenizer/";

    public TokenizerReifier(Route route, T definition) {
        super(route, definition);
    }

    public Processor createProcessor() throws Exception {
        Processor childProcessor = createChildProcessor(false);

        final FactoryFinder factoryFinder
                = camelContext.getCamelContextExtension().getFactoryFinder(TOKENIZER_PATH);

        final Optional<Tokenizer> tokenize = factoryFinder.newInstance(
                definition.tokenizerName(), Tokenizer.class);

        if (tokenize.isEmpty()) {
            throw new RuntimeCamelException(
                    "Cannot find a tokenizer named: " + definition.tokenizerName() + " in the classpath");
        }

        final Tokenizer tokenizer = tokenize.get();
        LOG.info("Creating a tokenizer of type {}", tokenizer.getClass().getName());
        configure(tokenizer);

        return new TokenizerProcessor(childProcessor, tokenizer);
    }

    protected abstract void configure(Tokenizer tokenizer);
}
