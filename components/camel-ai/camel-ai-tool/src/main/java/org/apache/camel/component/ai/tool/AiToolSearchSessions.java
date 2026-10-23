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
package org.apache.camel.component.ai.tool;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Conversation-scoped {@link AiToolSearchSession}s.
 * <p>
 * A missing or blank key means the caller should keep a request-local session instead of sharing one. Distinct keys
 * never see each other's discovered names.
 *
 * @since 4.24
 */
public final class AiToolSearchSessions {

    private final ConcurrentMap<String, AiToolSearchSession> sessions = new ConcurrentHashMap<>();

    /**
     * Returns the session for a conversation id, creating it on first use.
     *
     * @param  conversationId stable id for one conversation; must not be blank
     * @return                the session for that id
     */
    public AiToolSearchSession session(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException("A conversation id is required for a shared tool-search session");
        }
        return sessions.computeIfAbsent(conversationId, id -> new AiToolSearchSession());
    }
}
