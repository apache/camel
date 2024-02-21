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
package org.apache.camel.component.slack;

import java.util.Collections;
import java.util.Map;

import com.google.gson.Gson;
import com.slack.api.Slack;
import com.slack.api.SlackConfig;
import com.slack.api.methods.response.conversations.ConversationsListResponse;
import com.slack.api.model.ConversationType;
import com.slack.api.webhook.WebhookResponse;
import org.apache.camel.component.extension.verifier.DefaultComponentVerifierExtension;
import org.apache.camel.component.extension.verifier.ResultBuilder;
import org.apache.camel.component.extension.verifier.ResultErrorBuilder;
import org.apache.camel.component.slack.helper.SlackHelper;
import org.apache.camel.component.slack.helper.SlackMessage;
import org.apache.camel.util.ObjectHelper;

public class SlackComponentVerifierExtension extends DefaultComponentVerifierExtension {

    private static final Gson GSON = new Gson();

    public SlackComponentVerifierExtension() {
        this("slack");
    }

    public SlackComponentVerifierExtension(String scheme) {
        super(scheme);
    }

    // *********************************
    // Parameters validation
    // *********************************
    @Override
    protected Result verifyParameters(Map<String, Object> parameters) {
        ResultBuilder builder = ResultBuilder.withStatusAndScope(Result.Status.OK, Scope.PARAMETERS);

        if (ObjectHelper.isEmpty(parameters.get("token")) && ObjectHelper.isEmpty(parameters.get("webhookUrl"))) {
            builder.error(ResultErrorBuilder.withCodeAndDescription(VerificationError.StandardCode.GENERIC,
                    "You must specify a webhookUrl (for producer) or a token (for producer and consumer)")
                    .parameterKey("webhookUrl").parameterKey("token").build());
        }
        if (ObjectHelper.isNotEmpty(parameters.get("token")) && ObjectHelper.isNotEmpty(parameters.get("webhookUrl"))) {
            builder.error(ResultErrorBuilder.withCodeAndDescription(VerificationError.StandardCode.GENERIC,
                    "You must specify a webhookUrl (for producer) or a token (for producer and consumer). You can't specify both.")
                    .parameterKey("webhookUrl").parameterKey("token").build());
        }
        return builder.build();
    }

    // *********************************
    // Connectivity validation
    // *********************************
    @Override
    protected Result verifyConnectivity(Map<String, Object> parameters) {
        return ResultBuilder.withStatusAndScope(Result.Status.OK, Scope.CONNECTIVITY).error(parameters, this::verifyCredentials)
                .build();
    }

    private void verifyCredentials(ResultBuilder builder, Map<String, Object> parameters) {
        String webhookUrl = (String) parameters.get("webhookUrl");
        String serverUrl = (String) parameters.get("serverUrl");

        if (ObjectHelper.isNotEmpty(webhookUrl)) {

            try {
                // Build Helper object
                SlackMessage slackMessage;
                slackMessage = new SlackMessage();
                slackMessage.setText("Test connection");

                SlackConfig config = SlackHelper.createSlackConfig(serverUrl);
                WebhookResponse response
                        = Slack.getInstance(config, new CustomSlackHttpClient()).send(webhookUrl, GSON.toJson(slackMessage));

                // 2xx is OK, anything else we regard as failure
                if (response.getCode() < 200 || response.getCode() > 299) {
                    builder.error(ResultErrorBuilder
                            .withCodeAndDescription(VerificationError.StandardCode.AUTHENTICATION, "Invalid webhookUrl")
                            .parameterKey("webhookUrl").build());
                }
            } catch (Exception e) {
                builder.error(ResultErrorBuilder
                        .withCodeAndDescription(VerificationError.StandardCode.AUTHENTICATION, "Invalid webhookUrl")
                        .parameterKey("webhookUrl").build());
            }
        }
        if (ObjectHelper.isNotEmpty(parameters.get("token"))) {
            String token = (String) parameters.get("token");

            try {
                SlackConfig config = SlackHelper.createSlackConfig(serverUrl);
                ConversationsListResponse response = Slack.getInstance(config, new CustomSlackHttpClient()).methods(token)
                        .conversationsList(req -> req
                                .types(Collections.singletonList(ConversationType.PUBLIC_CHANNEL))
                                .limit(1));

                if (!response.isOk()) {
                    builder.error(ResultErrorBuilder
                            .withCodeAndDescription(VerificationError.StandardCode.AUTHENTICATION, "Invalid token")
                            .parameterKey("token").build());
                }
            } catch (Exception e) {
                builder.error(ResultErrorBuilder
                        .withCodeAndDescription(VerificationError.StandardCode.AUTHENTICATION, "Invalid token")
                        .parameterKey("token").build());
            }
        }
    }
}
