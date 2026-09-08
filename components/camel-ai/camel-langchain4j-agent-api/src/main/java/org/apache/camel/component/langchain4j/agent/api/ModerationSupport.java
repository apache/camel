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
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.ModerationException;
import org.apache.camel.util.ObjectHelper;

/**
 * Pre-invocation content moderation for agent chat requests.
 * <p/>
 * Camel runs moderation before building the LangChain4j AI service call so flagged user input is rejected before the
 * chat model, tools, or memory are updated. Custom {@link Agent} implementations should call
 * {@link #moderateUserMessage(ModerationModel, String)} with the same semantics when they bypass
 * {@link AgentWithMemory} or {@link AgentWithoutMemory}.
 *
 * @since 4.23
 */
public final class ModerationSupport {

    private ModerationSupport() {
    }

    /**
     * Moderates the user message when a {@link ModerationModel} is configured.
     * <p/>
     * Only non-empty user message text is moderated. Multimodal {@code Content} without a user message is not sent to
     * the moderation model. When a moderation model is configured, a missing verdict fails closed and raises
     * {@link ModerationException}.
     *
     * @param  moderationModel     the moderation model, may be {@code null}
     * @param  userMessage         the user message to check
     * @throws ModerationException when the moderation model flags the input or returns no verdict
     */
    public static void moderateUserMessage(ModerationModel moderationModel, String userMessage) {
        if (moderationModel == null || ObjectHelper.isEmpty(userMessage)) {
            return;
        }

        try {
            Response<Moderation> response = moderationModel.moderate(userMessage);
            if (response == null || response.content() == null) {
                throw new ModerationException(
                        "Moderation model returned no verdict for user message",
                        Moderation.flagged(userMessage));
            }

            Moderation moderation = response.content();
            if (moderation.flagged()) {
                throw new ModerationException("User message flagged by moderation model", moderation);
            }
        } catch (ModerationException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ModerationException(
                    "Moderation model failed to evaluate user message: " + e.getMessage(),
                    Moderation.flagged(userMessage));
        }
    }
}
