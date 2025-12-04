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

package org.apache.camel.test.infra.openai.mock;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Context object that parses and provides easy access to request information.
 */
public class RequestContext {
    private final JsonNode rootNode;
    private final JsonNode messagesNode;

    public RequestContext(JsonNode rootNode) {
        this.rootNode = rootNode;
        this.messagesNode = rootNode.path("messages");
    }

    public boolean hasToolRole() {
        if (!messagesNode.isArray() || messagesNode.size() == 0) {
            return false;
        }

        // Check if the last message has a tool role
        JsonNode lastMessage = messagesNode.get(messagesNode.size() - 1);
        String role = lastMessage.path("role").asText();
        return "tool".equals(role);
    }

    public String getLastUserMessage() {
        if (!messagesNode.isArray()) {
            return null;
        }

        for (int i = messagesNode.size(); i > 0; i--) {
            JsonNode messageNode = messagesNode.get(i - 1);

            String role = messageNode.path("role").asText();
            if ("user".equals(role)) {
                return messageNode.path("content").asText();
            }
        }

        return null;
    }

    public JsonNode getMessagesNode() {
        return messagesNode;
    }

    public JsonNode getRootNode() {
        return rootNode;
    }
}
