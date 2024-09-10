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

package org.apache.camel.component.langchain4j.tokenizer;

import dev.langchain4j.data.document.DocumentSplitter;
import org.apache.camel.Exchange;
import org.apache.camel.component.langchain4j.tokenizer.config.LangChain4JConfiguration;
import org.apache.camel.component.langchain4j.tokenizer.util.SplitterUtil;
import org.apache.camel.spi.Tokenizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base tokenizer class for LangChain4j
 */
abstract class AbstractLangChain4JTokenizer<T extends LangChain4JConfiguration> implements Tokenizer {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractLangChain4JTokenizer.class);
    private DocumentSplitter splitter;

    protected void configure(String name, T configuration) {
        splitter = SplitterUtil.byName(name, configuration);
    }

    private void debugLog(String[] split) {
        for (int i = 0; i < split.length; i++) {
            LOG.debug("Split {} part: {}", i, split[i]);
        }
    }

    @Override
    public final String[] tokenize(Exchange exchange) {
        final String body = exchange.getIn().getBody(String.class);

        LOG.debug("Starting LangChain4j tokenizer for message: {}", body);

        final String[] split = SplitterUtil.split(splitter, body);

        if (LOG.isDebugEnabled()) {
            debugLog(split);
        }

        return split;
    }

    protected static String toName(String name) {
        return "langchain4j-" + name;
    }
}
