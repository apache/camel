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
package org.apache.camel.component.ai.observability;

/**
 * Exchange property names for structured AI error metadata.
 *
 * @since 4.23
 */
public final class GenAiErrorProperties {

    /**
     * Error category ({@link GenAiErrorCategory#name()}) derived from the underlying SDK exception.
     */
    public static final String ERROR_CATEGORY = "CamelAiErrorCategory";

    /**
     * Suggested retry delay in milliseconds when the provider exposes {@code Retry-After} (OpenAI only).
     */
    public static final String RETRY_AFTER_MILLIS = "CamelAiRetryAfterMillis";

    private GenAiErrorProperties() {
    }
}
