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

import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.slack.helper.SlackMessage;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.ScheduledPollEndpoint;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.json.JsonObject;

/**
 * The slack component allows you to send messages to Slack.
 */
@UriEndpoint(firstVersion = "2.16.0", scheme = "slack", title = "Slack", syntax = "slack:channel", label = "social")
public class SlackEndpoint extends ScheduledPollEndpoint {

    @UriPath
    @Metadata(required = true)
    private String channel;
    @UriParam(label = "producer")
    private String webhookUrl;
    @UriParam(label = "producer", secret = true)
    private String username;
    @UriParam(label = "producer")
    private String iconUrl;
    @UriParam(label = "producer")
    private String iconEmoji;
    @UriParam(label = "consumer", secret = true)
    private String token;
    @UriParam(label = "consumer", defaultValue = "10")
    private String maxResults = "10";
    @UriParam(label = "consumer", defaultValue = "https://slack.com")
    private String serverUrl = "https://slack.com";

    /**
     * Constructor for SlackEndpoint
     *
     * @param uri the full component url
     * @param channelName the channel or username the message is directed at
     * @param component the component that was created
     */
    public SlackEndpoint(String uri, String channelName, SlackComponent component) {
        super(uri, component);
        this.webhookUrl = component.getWebhookUrl();
        this.channel = channelName;
    }

    @Override
    public Producer createProducer() throws Exception {
        SlackProducer producer = new SlackProducer(this);
        return producer;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        if (ObjectHelper.isEmpty(token)) {
            throw new RuntimeCamelException("Missing required endpoint configuration: token must be defined for Slack consumer");
        }
        SlackConsumer consumer = new SlackConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }

    /**
     * The incoming webhook URL
     */
    public void setWebhookUrl(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    public String getWebhookUrl() {
        return webhookUrl;
    }

    public String getChannel() {
        return channel;
    }

    /**
     * The channel name (syntax #name) or slackuser (syntax @userName) to send a
     * message directly to an user.
     */
    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getUsername() {
        return username;
    }

    /**
     * This is the username that the bot will have when sending messages to a
     * channel or user.
     */
    public void setUsername(String username) {
        this.username = username;
    }

    public String getIconUrl() {
        return iconUrl;
    }

    /**
     * The avatar that the component will use when sending message to a channel
     * or user.
     */
    public void setIconUrl(String iconUrl) {
        this.iconUrl = iconUrl;
    }

    public String getIconEmoji() {
        return iconEmoji;
    }

    /**
     * Use a Slack emoji as an avatar
     */
    public void setIconEmoji(String iconEmoji) {
        this.iconEmoji = iconEmoji;
    }

    public String getToken() {
        return token;
    }

    /**
     * The token to use
     */
    public void setToken(String token) {
        this.token = token;
    }

    public String getMaxResults() {
        return maxResults;
    }

    /**
     * The Max Result for the poll
     */
    public void setMaxResults(String maxResult) {
        this.maxResults = maxResult;
    }

    public String getServerUrl() {
        return serverUrl;
    }
    
    /**
     * The Server URL of the Slack instance
     */
    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    public Exchange createExchange(JsonObject object) {
        return createExchange(getExchangePattern(), object);
    }

    public Exchange createExchange(ExchangePattern pattern, JsonObject object) {
        Exchange exchange = super.createExchange(pattern);
        SlackMessage slackMessage = new SlackMessage();
        String text = object.getString(SlackConstants.SLACK_TEXT_FIELD);
        String user = object.getString("user");
        slackMessage.setText(text);
        slackMessage.setUser(user);
        if (ObjectHelper.isNotEmpty(object.get("icons"))) {
            JsonObject icons = object.getMap("icons");
            if (ObjectHelper.isNotEmpty(icons.get("emoji"))) {
                slackMessage.setIconEmoji(icons.getString("emoji"));
            }
        }
        Message message = exchange.getIn();
        message.setBody(slackMessage);
        return exchange;
    }
}
