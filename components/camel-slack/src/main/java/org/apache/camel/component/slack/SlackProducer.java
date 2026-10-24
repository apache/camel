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

import java.io.IOException;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.slack.api.Slack;
import com.slack.api.SlackConfig;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import com.slack.api.model.Message;
import com.slack.api.util.http.SlackHttpClient;
import com.slack.api.util.json.GsonFactory;
import com.slack.api.webhook.WebhookResponse;
import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.component.slack.helper.SlackHelper;
import org.apache.camel.component.slack.helper.SlackMessage;
import org.apache.camel.support.DefaultAsyncProducer;

public class SlackProducer extends DefaultAsyncProducer {

    // Slack reads the JSON keys in snake_case, such as icon_emoji, thread_ts and image_url
    private static final Gson GSON = GsonFactory.createSnakeCase();

    private final SlackEndpoint slackEndpoint;
    private Slack slack;

    public SlackProducer(SlackEndpoint endpoint) {
        super(endpoint);
        this.slackEndpoint = endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        SlackConfig config = SlackHelper.createSlackConfig(slackEndpoint.getServerUrl());
        SlackHttpClient client = new SlackHttpClient();
        this.slack = Slack.getInstance(config, client);
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        if (slack != null) {
            slack.close();
        }
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        if (slackEndpoint.getToken() != null) {
            return sendMessageByToken(exchange, callback);
        } else {
            return sendMessageByWebhookURL(exchange, callback);
        }
    }

    private boolean sendMessageByToken(Exchange exchange, AsyncCallback callback) {
        ChatPostMessageResponse response;
        Object payload = exchange.getIn().getBody();

        try {
            if (payload instanceof SlackMessage slackmessage2) {
                response = sendLegacySlackMessage(slackmessage2);
            } else if (payload instanceof Message message2) {
                response = sendMessage(message2);
            } else {
                SlackMessage slackMessage = new SlackMessage();
                slackMessage.setText(exchange.getIn().getBody(String.class));
                response = sendLegacySlackMessage(slackMessage);
            }
            if (!response.isOk()) {
                exchange.setException(
                        new CamelExchangeException("Error POSTing to Slack API: " + response, exchange));
            }
        } catch (Exception e) {
            exchange.setException(e);
            return true;
        } finally {
            callback.done(true);
        }

        return true;
    }

    private ChatPostMessageResponse sendLegacySlackMessage(SlackMessage slackMessage) throws IOException, SlackApiException {
        String attachments = slackMessage.getAttachments() != null ? GSON.toJson(slackMessage.getAttachments()) : null;
        return slack.methods(slackEndpoint.getToken()).chatPostMessage(req -> req
                .channel(slackEndpoint.getChannel())
                .username(slackEndpoint.getUsername())
                .iconUrl(slackEndpoint.getIconUrl())
                .iconEmoji(slackEndpoint.getIconEmoji())
                .text(slackMessage.getText())
                .attachmentsAsString(attachments));
    }

    private ChatPostMessageResponse sendMessage(Message message) throws IOException, SlackApiException {
        return slack.methods(slackEndpoint.getToken()).chatPostMessage(req -> req
                .channel(slackEndpoint.getChannel())
                .username(slackEndpoint.getUsername())
                .iconUrl(slackEndpoint.getIconUrl())
                .iconEmoji(slackEndpoint.getIconEmoji())
                .text(message.getText())
                .blocks(message.getBlocks())
                .attachments(message.getAttachments()));
    }

    private boolean sendMessageByWebhookURL(Exchange exchange, AsyncCallback callback) {
        try {
            WebhookResponse response = slack.send(slackEndpoint.getWebhookUrl(), createWebhookJson(exchange));
            if (response.getCode() < 200 || response.getCode() > 299) {
                exchange.setException(
                        new CamelExchangeException("Error POSTing to Slack API: " + response, exchange));
            }
        } catch (Exception e) {
            exchange.setException(e);
        }

        callback.done(true);
        return true;
    }

    /**
     * Creates the JSON of the webhook request with the channel, username and icons of the endpoint. They are set on the
     * JSON only, so the message body of the caller is not changed.
     */
    private String createWebhookJson(Exchange exchange) {
        Object payload = exchange.getIn().getBody();
        JsonObject json;
        if (payload instanceof SlackMessage || payload instanceof Message) {
            json = GSON.toJsonTree(payload).getAsJsonObject();
        } else {
            json = new JsonObject();
            addProperty(json, "text", exchange.getIn().getBody(String.class));
        }
        addProperty(json, "channel", slackEndpoint.getChannel());
        addProperty(json, "username", slackEndpoint.getUsername());
        addProperty(json, "icon_url", slackEndpoint.getIconUrl());
        addProperty(json, "icon_emoji", slackEndpoint.getIconEmoji());
        return GSON.toJson(json);
    }

    private static void addProperty(JsonObject json, String name, String value) {
        if (value != null) {
            json.addProperty(name, value);
        }
    }
}
