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
package org.apache.camel.component.langchain4j.agent;

import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LangChain4jAgentMissingAgentTest {

    @Test
    void missingAgentFailsFastWithAClearError() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.start();

            // No agent / agentConfiguration / agentFactory, and no registry bean named "noSuchAgent".
            LangChain4jAgentEndpoint endpoint
                    = context.getEndpoint("langchain4j-agent:noSuchAgent", LangChain4jAgentEndpoint.class);
            Producer producer = endpoint.createProducer();

            Exception ex = assertThrows(Exception.class, producer::start);
            Throwable cause = ex;
            boolean clearError = false;
            while (cause != null) {
                if (cause instanceof IllegalArgumentException && cause.getMessage() != null
                        && cause.getMessage().contains("No agent could be resolved")) {
                    clearError = true;
                    break;
                }
                cause = cause.getCause();
            }
            assertTrue(clearError, "expected a clear 'No agent could be resolved' IllegalArgumentException, got: " + ex);
        }
    }
}
