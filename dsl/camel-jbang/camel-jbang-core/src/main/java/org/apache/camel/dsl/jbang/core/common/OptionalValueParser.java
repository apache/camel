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
package org.apache.camel.dsl.jbang.core.common;

import org.aesh.command.impl.internal.ProcessedOption;
import org.aesh.command.parser.OptionParser;
import org.aesh.command.parser.OptionParserException;
import org.aesh.parser.ParsedLineIterator;

/**
 * An option with an optional value, like picocli arity 0..1: it takes the next word as its value only when the word is
 * one, such as {@code --jvm-debug 5006} or {@code --background-wait 30s}. Otherwise the word is left for the next
 * option or the files, and aesh gives the option its fallbackValue (aesh #703).
 */
public abstract class OptionalValueParser implements OptionParser {

    /**
     * Whether the word after the option is its value.
     */
    protected abstract boolean isValue(String word);

    @Override
    public void parse(ParsedLineIterator iterator, ProcessedOption option) throws OptionParserException {
        String word = iterator.pollWord();
        int eq = word.indexOf('=');
        if (eq > 0) {
            option.addValue(word.substring(eq + 1));
        } else if (iterator.hasNextWord() && isValue(iterator.peekWord())) {
            option.addValue(iterator.pollWord());
        }
    }
}
