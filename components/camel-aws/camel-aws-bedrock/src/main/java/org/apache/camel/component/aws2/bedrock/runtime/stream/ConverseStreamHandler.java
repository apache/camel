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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamResponseHandler;
import software.amazon.awssdk.services.bedrockruntime.model.TokenUsage;

/**
 * Utility class for handling Converse API streaming responses from Bedrock models
 */
public final class ConverseStreamHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ConverseStreamHandler.class);

    private ConverseStreamHandler() {
        // Utility class
    }

    /**
     * Create a response handler for complete mode (accumulates all chunks)
     *
     * @param  metadata the metadata object to populate
     * @param  fullText the string builder to accumulate text
     * @return          the response handler
     */
    public static ConverseStreamResponseHandler createCompleteHandler(
            StreamMetadata metadata,
            StringBuilder fullText) {

        int[] chunkCount = { 0 };

        return ConverseStreamResponseHandler.builder()
                .subscriber(ConverseStreamResponseHandler.Visitor.builder()
                        .onContentBlockDelta(delta -> {
                            if (delta.delta() != null && delta.delta().text() != null) {
                                fullText.append(delta.delta().text());
                            }
                            chunkCount[0]++;
                        })
                        .onMetadata(metadataEvent -> {
                            if (metadataEvent.usage() != null) {
                                metadata.setUsage(metadataEvent.usage());
                            }
                        })
                        .onMessageStop(stop -> {
                            if (stop.stopReason() != null) {
                                metadata.setStopReason(stop.stopReason().toString());
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
     * @param  metadata      the metadata object to populate
     * @param  chunks        the list to collect chunks
     * @param  chunkConsumer consumer that receives each chunk
     * @return               the response handler
     */
    public static ConverseStreamResponseHandler createChunksHandler(
            StreamMetadata metadata,
            List<String> chunks,
            Consumer<String> chunkConsumer) {

        int[] chunkCount = { 0 };

        return ConverseStreamResponseHandler.builder()
                .subscriber(ConverseStreamResponseHandler.Visitor.builder()
                        .onContentBlockDelta(delta -> {
                            if (delta.delta() != null && delta.delta().text() != null) {
                                String text = delta.delta().text();
                                chunks.add(text);
                                if (chunkConsumer != null) {
                                    chunkConsumer.accept(text);
                                }
                            }
                            chunkCount[0]++;
                        })
                        .onMetadata(metadataEvent -> {
                            if (metadataEvent.usage() != null) {
                                metadata.setUsage(metadataEvent.usage());
                            }
                        })
                        .onMessageStop(stop -> {
                            if (stop.stopReason() != null) {
                                metadata.setStopReason(stop.stopReason().toString());
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
     * Metadata extracted from Converse streaming response
     */
    public static class StreamMetadata {
        private String fullText;
        private List<String> chunks;
        private String stopReason;
        private TokenUsage usage;
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

        public String getStopReason() {
            return stopReason;
        }

        public void setStopReason(String stopReason) {
            this.stopReason = stopReason;
        }

        public TokenUsage getUsage() {
            return usage;
        }

        public void setUsage(TokenUsage usage) {
            this.usage = usage;
        }

        public int getChunkCount() {
            return chunkCount;
        }

        public void setChunkCount(int chunkCount) {
            this.chunkCount = chunkCount;
        }
    }
}
