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
package org.apache.camel.component.langchain4j.agent.api;

import dev.langchain4j.model.moderation.Moderation;
import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.model.moderation.ModerationRequest;
import dev.langchain4j.model.moderation.ModerationResponse;

final class FlaggingModerationModel implements ModerationModel {

    private final String flaggedToken;

    FlaggingModerationModel(String flaggedToken) {
        this.flaggedToken = flaggedToken;
    }

    @Override
    public ModerationResponse doModerate(ModerationRequest request) {
        String text = request.texts() == null || request.texts().isEmpty() ? "" : request.texts().get(0);
        boolean flagged = text.contains(flaggedToken);
        return ModerationResponse.builder()
                .moderation(flagged ? Moderation.flagged(text) : Moderation.notFlagged())
                .build();
    }
}
