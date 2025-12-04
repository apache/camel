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

package org.apache.camel.component.aws2.bedrock.agentruntime;

import org.apache.camel.spi.Metadata;

/**
 * Constants used in Camel AWS Bedrock Agent Runtime module SDK v2
 */
public interface BedrockAgentRuntimeConstants {
    @Metadata(description = "The operation we want to perform", javaType = "String")
    String OPERATION = "CamelAwsBedrockAgentRuntimeOperation";

    @Metadata(
            description = "When retrieving and generating a response, this header will contain the citations",
            javaType = "String")
    String CITATIONS = "CamelAwsBedrockAgentRuntimeCitations";

    @Metadata(
            description =
                    "When retrieving and generating a response, this header will contain he unique identifier of the session. "
                            + "Reuse the same value to continue the same session with the knowledge base.",
            javaType = "String")
    String SESSION_ID = "CamelAwsBedrockAgentRuntimeSessionId";
}
