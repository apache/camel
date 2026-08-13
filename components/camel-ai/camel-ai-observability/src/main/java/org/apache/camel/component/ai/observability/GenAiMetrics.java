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
 * Micrometer metric names aligned with Spring AI GenAI observability.
 */
public final class GenAiMetrics {

    public static final String CLIENT_OPERATION = "gen_ai.client.operation";
    public static final String CLIENT_TOKEN_USAGE = "gen_ai.client.token.usage";

    public static final String TAG_OPERATION_NAME = "gen_ai.operation.name";
    public static final String TAG_SYSTEM = "gen_ai.system";
    public static final String TAG_REQUEST_MODEL = "gen_ai.request.model";
    public static final String TAG_TOKEN_TYPE = "gen_ai.token.type";
    public static final String TAG_ERROR_TYPE = "error.type";
    public static final String TAG_CAMEL_COMPONENT = "camel.component";

    public static final String TOKEN_TYPE_INPUT = "input";
    public static final String TOKEN_TYPE_OUTPUT = "output";

    private GenAiMetrics() {
    }
}
