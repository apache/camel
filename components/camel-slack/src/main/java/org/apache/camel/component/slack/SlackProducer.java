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
import com.slack.api.Slack;
import com.slack.api.SlackConfig;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import com.slack.api.model.Message;
import com.slack.api.webhook.WebhookResponse;
import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.component.slack.helper.SlackHelper;
import org.apache.camel.component.slack.helper.SlackMessage;
import org.apache.camel.support.DefaultAsyncProducer;

public class SlackProducer extends DefaultAsyncProducer {

    private static final Gson GSON = new Gson();

    private final SlackEndpoint slackEndpoint;
    private Slack slack;

    public SlackProducer(SlackEndpoint endpoint) {
        super(endpoint);
        this.slackEndpoint = endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        SlackConfig config = SlackHelper.createSlackConfig(slackEndpoint.getServerUrl());
        CustomSlackHttpClient client = new CustomSlackHttpClient();
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
            if (payload instanceof SlackMessage) {
                response = sendLegacySlackMessage((SlackMessage) payload);
            } else if (payload instanceof Message) {
                response = sendMessage((Message) payload);
            } else {
                SlackMessage slackMessage = new SlackMessage();
                slackMessage.setText(exchange.getIn().getBody(String.class));
                response = sendLegacySlackMessage(slackMessage);
            }
        } catch (Exception e) {
            exchange.setException(e);
            return true;
        } finally {
            callback.done(true);
        }

        if (!response.isOk()) {
            exchange.setException(new CamelExchangeException("Error POSTing to Slack API: " + response.toString(), exchange));
        }

        return false;
    }

    private ChatPostMessageResponse sendLegacySlackMessage(SlackMessage slackMessage) throws IOException, SlackApiException {
        return slack.methods(slackEndpoint.getToken()).chatPostMessage(req -> req
                .channel(slackEndpoint.getChannel())
                .username(slackEndpoint.getUsername())
                .iconUrl(slackEndpoint.getIconUrl())
                .iconEmoji(slackEndpoint.getIconEmoji())
                .text(slackMessage.getText()));
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
        String json;
        Object payload = exchange.getIn().getBody();
        if (payload instanceof SlackMessage) {
            json = GSON.toJson(addEndPointOptions((SlackMessage) payload));
        } else if (payload instanceof Message) {
            json = GSON.toJson(addEndPointOptions((Message) payload));
        } else {
            SlackMessage slackMessage = new SlackMessage();
            slackMessage.setText(exchange.getIn().getBody(String.class));
            json = GSON.toJson(addEndPointOptions(slackMessage));
        }

        WebhookResponse response;
        try {
            response = slack.send(slackEndpoint.getWebhookUrl(), json);
        } catch (IOException e) {
            exchange.setException(e);
            return true;
        } finally {
            callback.done(true);
        }

        if (response.getCode() < 200 || response.getCode() > 299) {
            exchange.setException(new CamelExchangeException("Error POSTing to Slack API: " + response.toString(), exchange));
        }

        return false;
    }

    private Message addEndPointOptions(Message slackMessage) {
        slackMessage.setChannel(slackEndpoint.getChannel());
        slackMessage.setUsername(slackEndpoint.getUsername());
        return slackMessage;
    }

    private SlackMessage addEndPointOptions(SlackMessage slackMessage) {
        slackMessage.setChannel(slackEndpoint.getChannel());
        slackMessage.setUsername(slackEndpoint.getUsername());
        slackMessage.setIconUrl(slackEndpoint.getIconUrl());
        slackMessage.setIconEmoji(slackEndpoint.getIconEmoji());
        return slackMessage;
    }
}
