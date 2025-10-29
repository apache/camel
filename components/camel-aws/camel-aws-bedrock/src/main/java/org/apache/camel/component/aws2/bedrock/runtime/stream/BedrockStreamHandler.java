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
package org.apache.camel.component.aws2.bedrock.runtime.stream;

import java.util.List;
import java.util.function.Consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelWithResponseStreamResponseHandler;

/**
 * Utility class for handling streaming responses from Bedrock models
 */
public final class BedrockStreamHandler {

    private static final Logger LOG = LoggerFactory.getLogger(BedrockStreamHandler.class);

    private BedrockStreamHandler() {
        // Utility class
    }

    /**
     * Create a response handler for complete mode (accumulates all chunks)
     *
     * @param  modelId  the model ID to determine parser
     * @param  metadata the metadata object to populate
     * @param  fullText the string builder to accumulate text
     * @return          the response handler
     */
    public static InvokeModelWithResponseStreamResponseHandler createCompleteHandler(
            String modelId,
            StreamMetadata metadata,
            StringBuilder fullText) {

        StreamResponseParser parser = StreamParserFactory.getParser(modelId);
        int[] chunkCount = { 0 };

        return InvokeModelWithResponseStreamResponseHandler.builder()
                .subscriber(InvokeModelWithResponseStreamResponseHandler.Visitor.builder()
                        .onChunk(part -> {
                            try {
                                String chunkJson = part.bytes().asUtf8String();
                                String text = parser.extractText(chunkJson);
                                if (text != null && !text.isEmpty()) {
                                    fullText.append(text);
                                }

                                // Extract metadata from final chunk
                                if (parser.isFinalChunk(chunkJson)) {
                                    String completionReason = parser.extractCompletionReason(chunkJson);
                                    if (completionReason != null) {
                                        metadata.setCompletionReason(completionReason);
                                    }

                                    Integer tokenCount = parser.extractTokenCount(chunkJson);
                                    if (tokenCount != null) {
                                        metadata.setTokenCount(tokenCount);
                                    }
                                }
                                chunkCount[0]++;
                            } catch (JsonProcessingException e) {
                                LOG.warn("Error parsing streaming chunk: {}", e.getMessage(), e);
                            }
                        })
                        .build())
                .onComplete(() -> {
                    metadata.setChunkCount(chunkCount[0]);
                    metadata.setFullText(fullText.toString());
                })
                .build();
    }

    /**
     * Create a response handler for chunks mode (emits each chunk)
     *
     * @param  modelId       the model ID to determine parser
     * @param  metadata      the metadata object to populate
     * @param  chunks        the list to collect chunks
     * @param  chunkConsumer consumer that receives each chunk
     * @return               the response handler
     */
    public static InvokeModelWithResponseStreamResponseHandler createChunksHandler(
            String modelId,
            StreamMetadata metadata,
            List<String> chunks,
            Consumer<String> chunkConsumer) {

        StreamResponseParser parser = StreamParserFactory.getParser(modelId);
        int[] chunkCount = { 0 };

        return InvokeModelWithResponseStreamResponseHandler.builder()
                .subscriber(InvokeModelWithResponseStreamResponseHandler.Visitor.builder()
                        .onChunk(part -> {
                            try {
                                String chunkJson = part.bytes().asUtf8String();
                                String text = parser.extractText(chunkJson);

                                if (text != null && !text.isEmpty()) {
                                    chunks.add(text);
                                    if (chunkConsumer != null) {
                                        chunkConsumer.accept(text);
                                    }
                                }

                                // Extract metadata from final chunk
                                if (parser.isFinalChunk(chunkJson)) {
                                    String completionReason = parser.extractCompletionReason(chunkJson);
                                    if (completionReason != null) {
                                        metadata.setCompletionReason(completionReason);
                                    }

                                    Integer tokenCount = parser.extractTokenCount(chunkJson);
                                    if (tokenCount != null) {
                                        metadata.setTokenCount(tokenCount);
                                    }
                                }
                                chunkCount[0]++;
                            } catch (JsonProcessingException e) {
                                LOG.warn("Error parsing streaming chunk: {}", e.getMessage(), e);
                            }
                        })
                        .build())
                .onComplete(() -> {
                    metadata.setChunkCount(chunkCount[0]);
                    metadata.setChunks(chunks);
                })
                .build();
    }

    /**
     * Metadata extracted from streaming response
     */
    public static class StreamMetadata {
        private String fullText;
        private List<String> chunks;
        private String completionReason;
        private Integer tokenCount;
        private int chunkCount;

        public String getFullText() {
            return fullText;
        }

        public void setFullText(String fullText) {
            this.fullText = fullText;
        }

        public List<String> getChunks() {
            return chunks;
        }

        public void setChunks(List<String> chunks) {
            this.chunks = chunks;
        }

        public String getCompletionReason() {
            return completionReason;
        }

        public void setCompletionReason(String completionReason) {
            this.completionReason = completionReason;
        }

        public Integer getTokenCount() {
            return tokenCount;
        }

        public void setTokenCount(Integer tokenCount) {
            this.tokenCount = tokenCount;
        }

        public int getChunkCount() {
            return chunkCount;
        }

        public void setChunkCount(int chunkCount) {
            this.chunkCount = chunkCount;
        }
    }
}
