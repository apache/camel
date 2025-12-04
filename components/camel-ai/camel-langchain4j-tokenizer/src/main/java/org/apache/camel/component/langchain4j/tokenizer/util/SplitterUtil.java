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

package org.apache.camel.component.langchain4j.tokenizer.util;

import dev.langchain4j.community.model.dashscope.QwenTokenCountEstimator;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentByCharacterSplitter;
import dev.langchain4j.data.document.splitter.DocumentByLineSplitter;
import dev.langchain4j.data.document.splitter.DocumentByParagraphSplitter;
import dev.langchain4j.data.document.splitter.DocumentBySentenceSplitter;
import dev.langchain4j.data.document.splitter.DocumentByWordSplitter;
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.azure.AzureOpenAiTokenCountEstimator;
import dev.langchain4j.model.openai.OpenAiTokenCountEstimator;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.langchain4j.tokenizer.config.LangChain4JConfiguration;
import org.apache.camel.component.langchain4j.tokenizer.config.LangChain4JQwenConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SplitterUtil {
    private static final Logger LOG = LoggerFactory.getLogger(SplitterUtil.class);

    private SplitterUtil() {}

    public static DocumentSplitter byName(String name, LangChain4JConfiguration configuration) {
        assert name != null : "The splitter name must be provided";

        int maxTokens = configuration.getMaxTokens();
        int maxOverlap = configuration.getMaxOverlap();

        String type = configuration.getType();

        final TokenCountEstimator countEstimator = buildTokenizer(configuration, type);

        LOG.debug("Creating a {} splitter", name);
        switch (name) {
            case SplitterTypes.SENTENCE -> {
                return new DocumentBySentenceSplitter(maxTokens, maxOverlap, countEstimator);
            }
            case SplitterTypes.PARAGRAPH -> {
                return new DocumentByParagraphSplitter(maxTokens, maxOverlap, countEstimator);
            }
            case SplitterTypes.CHARACTER -> {
                return new DocumentByCharacterSplitter(maxTokens, maxOverlap, countEstimator);
            }
            case SplitterTypes.WORD -> {
                return new DocumentByWordSplitter(maxTokens, maxOverlap, countEstimator);
            }
            case SplitterTypes.LINE -> {
                return new DocumentByLineSplitter(maxTokens, maxOverlap, countEstimator);
            }
            default -> throw new IllegalArgumentException("Unknown splitter name: " + name);
        }
    }

    private static TokenCountEstimator buildTokenizer(LangChain4JConfiguration configuration, String type) {
        if (type == null) {
            throw new RuntimeCamelException("Invalid tokenizer type: null");
        }

        String model = configuration.getModelName();
        if (model == null) {
            return null;
        }

        return switch (type) {
            case TokenizerTypes.OPEN_AI -> new OpenAiTokenCountEstimator(model);
            case TokenizerTypes.AZURE -> new AzureOpenAiTokenCountEstimator(model);
            case TokenizerTypes.QWEN -> createQwenTokenizer(configuration);

            default -> throw new RuntimeCamelException("Unknown tokenizer type: " + type);
        };
    }

    private static QwenTokenCountEstimator createQwenTokenizer(LangChain4JConfiguration configuration) {
        if (configuration instanceof LangChain4JQwenConfiguration qwenConfiguration) {
            return new QwenTokenCountEstimator(qwenConfiguration.getApiKey(), qwenConfiguration.getModelName());
        }

        throw new RuntimeCamelException("Invalid configuration type for the QwenTokenCountEstimator: "
                + configuration.getClass().getSimpleName() + ". Use LangChain4JQwenConfiguration");
    }

    public static String[] split(DocumentSplitter splitter, String body) {
        if (splitter instanceof DocumentBySentenceSplitter ds) {
            return ds.split(body);
        }
        if (splitter instanceof DocumentByParagraphSplitter dp) {
            return dp.split(body);
        }
        if (splitter instanceof DocumentByCharacterSplitter dc) {
            return dc.split(body);
        }
        if (splitter instanceof DocumentByWordSplitter dw) {
            return dw.split(body);
        }
        if (splitter instanceof DocumentByLineSplitter dl) {
            return dl.split(body);
        }

        throw new RuntimeCamelException(
                "Unsupported splitter type: " + splitter.getClass().getSimpleName());
    }
}
