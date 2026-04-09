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

public enum OpenAIOperations {

    chatCompletion("chat-completion"),
    embeddings("embeddings");

    private final String value;

    OpenAIOperations(String value) {
        this.value = value;
    }

    public static OpenAIOperations fromValue(String value) {
        for (OpenAIOperations op : values()) {
            if (op.value.equalsIgnoreCase(value) || op.name().equalsIgnoreCase(value)) {
                return op;
            }
        }
        throw new IllegalArgumentException(
                "Unknown operation: " + value + ". Supported: chat-completion, embeddings");
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}
