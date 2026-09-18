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
package org.apache.camel.component.ai.resource;

/**
 * Result of a resource read by {@link AiResourceExecutor}. Classifies the outcome without deciding error handling
 * policy: the caller decides what reaches the client.
 * <p>
 * <b>Security note:</b> {@link ExecutionError#message()} may contain raw exception messages from route execution, which
 * can include internal details (file paths, database errors, class names). Callers MUST NOT pass this message verbatim
 * to a remote client without sanitization.
 *
 * @since 4.23
 */
public sealed interface AiResourceResult {

    /**
     * The route produced textual content.
     *
     * @param value the content as a string
     */
    record Text(String value) implements AiResourceResult {
    }

    /**
     * The route produced binary content.
     *
     * @param value the content as raw bytes
     */
    record Binary(byte[] value) implements AiResourceResult {
    }

    /**
     * The route threw an exception, the exchange carries an exception after processing, or it produced no content.
     *
     * @param message a human-readable description of the failure
     * @param cause   the underlying exception
     */
    record ExecutionError(String message, Exception cause) implements AiResourceResult {
    }
}
