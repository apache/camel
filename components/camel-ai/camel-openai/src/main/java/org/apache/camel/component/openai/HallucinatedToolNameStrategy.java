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

/**
 * Strategy for handling tool names hallucinated by the model (i.e. the model requests a tool that does not exist in any
 * configured MCP server).
 */
public enum HallucinatedToolNameStrategy {

    /**
     * Throw an {@link IllegalStateException}, failing the exchange immediately. This is the default behavior.
     */
    FAIL_EXCHANGE,

    /**
     * Send a corrective tool result back to the model listing the available tools, giving the model a chance to
     * self-correct and retry with a valid tool name. The agentic loop's {@code maxToolIterations} bounds the number of
     * retries.
     */
    REPROMPT_MODEL
}
