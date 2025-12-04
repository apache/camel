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

import com.fasterxml.jackson.core.JsonProcessingException;

/**
 * Parser interface for extracting text and metadata from streaming responses of different Bedrock models
 */
public interface StreamResponseParser {

    /**
     * Extract text content from a streaming chunk
     *
     * @param  chunk the JSON chunk from the stream
     * @return       the extracted text content, or empty string if no text in this chunk
     */
    String extractText(String chunk) throws JsonProcessingException;

    /**
     * Extract the completion reason from a streaming chunk (if present)
     *
     * @param  chunk the JSON chunk from the stream
     * @return       the completion reason, or null if not present in this chunk
     */
    String extractCompletionReason(String chunk) throws JsonProcessingException;

    /**
     * Extract the token count from a streaming chunk (if present)
     *
     * @param  chunk the JSON chunk from the stream
     * @return       the token count, or null if not present in this chunk
     */
    Integer extractTokenCount(String chunk) throws JsonProcessingException;

    /**
     * Check if this chunk represents the end of the stream
     *
     * @param  chunk the JSON chunk from the stream
     * @return       true if this is the final chunk
     */
    boolean isFinalChunk(String chunk) throws JsonProcessingException;
}
