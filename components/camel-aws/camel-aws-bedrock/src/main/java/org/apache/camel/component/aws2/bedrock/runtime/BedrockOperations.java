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
package org.apache.camel.component.aws2.bedrock.runtime;

public enum BedrockOperations {

    invokeTextModel,

    invokeImageModel,

    invokeEmbeddingsModel,

    invokeTextModelStreaming,

    /**
     * @deprecated the image generation models do not stream their response, use {@link #invokeImageModel} instead. With
     *             a text model, this operation is the same as {@link #invokeTextModelStreaming}.
     */
    @Deprecated(since = "4.24")
    invokeImageModelStreaming,

    /**
     * @deprecated the embedding models do not stream their response, use {@link #invokeEmbeddingsModel} instead. With a
     *             text model, this operation is the same as {@link #invokeTextModelStreaming}.
     */
    @Deprecated(since = "4.24")
    invokeEmbeddingsModelStreaming,

    converse,

    converseStream,

    applyGuardrail
}
