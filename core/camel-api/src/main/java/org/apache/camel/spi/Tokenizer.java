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

package org.apache.camel.spi;

import org.apache.camel.Exchange;

/**
 * An interface for tokenizing text data. Typically used for machine learning, artificial intelligence and interacting
 * with vector databases.
 *
 * Implementations of this interface should provide a way to configure the tokenizer, and then use that configuration to
 * tokenize the data in the Exchange.
 */
public interface Tokenizer {

    /**
     * A nested interface representing the configuration options for this tokenizer.
     *
     * Implementors of this interface can set the maximum number of tokens, the maximum overlap between tokens, and the
     * type of tokenization being performed.
     */
    interface Configuration {
        /**
         * Sets the maximum number of tokens to be produced by the tokenizer.
         *
         * @param maxTokens the new maximum number of tokens
         */
        void setMaxTokens(int maxTokens);

        /**
         * Sets the maximum overlap between tokens, where an overlap is defined as the number of characters that are
         * common between two adjacent segments.
         *
         * @param maxOverlap the new maximum overlap
         */
        void setMaxOverlap(int maxOverlap);

        /**
         * Sets the type of tokenization being performed by this tokenizer. This can typically be specific to the
         * implementation.
         *
         * @param type the tokenization type
         */
        void setType(String type);
    }

    /**
     * Creates a new configuration for this tokenizer, with default values.
     *
     * @return a new Configuration object
     */
    Configuration newConfiguration();

    /**
     * Configures this tokenizer using the provided configuration options.
     *
     * @param configuration the configuration to use
     */
    void configure(Configuration configuration);

    /**
     * Returns the name of this tokenizer, which can be used for identification or logging purposes.
     *
     * @return the name of this tokenizer
     */
    String name();

    /**
     * Tokenizes the data in the provided Exchange using the current configuration options.
     *
     * @param  exchange the Exchange to tokenize
     * @return          an array of tokens produced by the tokenizer
     */
    String[] tokenize(Exchange exchange);
}
