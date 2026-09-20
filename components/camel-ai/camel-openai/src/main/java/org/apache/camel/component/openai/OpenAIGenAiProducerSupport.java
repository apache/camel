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
package org.apache.camel.component.openai;

import org.apache.camel.Exchange;
import org.apache.camel.component.ai.observability.GenAiErrorSupport;
import org.apache.camel.component.ai.observability.GenAiObservability;
import org.apache.camel.component.ai.observability.GenAiObservation;
import org.apache.camel.component.ai.observability.GenAiObservationContext;
import org.apache.camel.component.ai.observability.GenAiOperationName;
import org.apache.camel.component.ai.observability.GenAiUsage;

/**
 * Shared GenAI observability helpers for OpenAI producers.
 */
final class OpenAIGenAiProducerSupport {

    private OpenAIGenAiProducerSupport() {
    }

    static GenAiObservationContext context(GenAiOperationName operationName, String requestModel) {
        return GenAiObservationContext.builder()
                .operationName(operationName)
                .system("openai")
                .requestModel(requestModel != null ? requestModel : "unknown")
                .componentScheme("openai")
                .build();
    }

    static GenAiObservation start(Exchange exchange, GenAiOperationName operationName, String requestModel) {
        return GenAiObservability.start(exchange, context(operationName, requestModel));
    }

    static void recordSuccess(GenAiObservation observation, GenAiUsage usage) {
        observation.recordSuccess(usage);
    }

    static void recordSuccess(GenAiObservation observation, String responseModel) {
        observation.recordSuccess(GenAiUsage.of((Long) null, null, null, responseModel));
    }

    static void recordFailure(Exchange exchange, GenAiObservation observation, Exception error) {
        GenAiErrorSupport.apply(exchange, error);
        observation.recordError(error);
    }

    static void applyErrorMetadata(Exchange exchange, Exception error) {
        GenAiErrorSupport.apply(exchange, error);
    }
}
