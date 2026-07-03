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
package org.apache.camel.component.ai.tools;

/**
 * Constants for the AI Tool component.
 *
 * @since 4.22
 */
public final class AiTool {

    public static final String SCHEME = "ai-tool";

    /**
     * Exchange variable name under which {@link AiToolArguments} is set by the executor.
     */
    public static final String TOOL_ARGUMENTS = "AiToolArguments";

    private AiTool() {
    }
}
