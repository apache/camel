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
package org.apache.camel.component.langchain.embeddings;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultProducer;

public class LangChainEmbeddingsProducer extends DefaultProducer {
    public LangChainEmbeddingsProducer(LangChainEmbeddingsEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public LangChainEmbeddingsEndpoint getEndpoint() {
        return (LangChainEmbeddingsEndpoint) super.getEndpoint();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        final TextSegment in = exchange.getMessage().getMandatoryBody(TextSegment.class);
        final EmbeddingModel model = getEndpoint().getConfiguration().getEmbeddingModel();
        final Response<Embedding> result = model.embed(in);
        final Message message = exchange.getMessage();

        if (result.finishReason() != null) {
            message.setHeader(LangChainEmbeddings.Headers.FINISH_REASON, result.finishReason());
        }

        if (result.tokenUsage() != null) {
            message.setHeader(LangChainEmbeddings.Headers.INPUT_TOKEN_COUNT, result.tokenUsage().inputTokenCount());
            message.setHeader(LangChainEmbeddings.Headers.OUTPUT_TOKEN_COUNT, result.tokenUsage().outputTokenCount());
            message.setHeader(LangChainEmbeddings.Headers.TOTAL_TOKEN_COUNT, result.tokenUsage().totalTokenCount());
        }

        message.setHeader(LangChainEmbeddings.Headers.VECTOR, result.content().vector());
    }
}
