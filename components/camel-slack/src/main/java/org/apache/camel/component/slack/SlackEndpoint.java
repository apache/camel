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

import com.slack.api.model.ConversationType;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.ScheduledPollEndpoint;
import org.apache.camel.util.ObjectHelper;

/**
 * Send and receive messages to/from Slack.
 */
@UriEndpoint(firstVersion = "2.16.0", scheme = "slack", title = "Slack", syntax = "slack:channel",
             category = { Category.SOCIAL })
public class SlackEndpoint extends ScheduledPollEndpoint {

    @UriParam(defaultValue = "" + SlackConsumer.DEFAULT_CONSUMER_DELAY, javaType = "java.time.Duration",
              label = "consumer,scheduler",
              description = "Milliseconds before the next poll.")
    private long delay = SlackConsumer.DEFAULT_CONSUMER_DELAY;

    @UriPath
    @Metadata(required = true)
    private String channel;
    @UriParam(label = "producer")
    private String webhookUrl;
    @UriParam(label = "producer", secret = true)
    @Deprecated
    private String username;
    @UriParam(label = "producer")
    @Deprecated
    private String iconUrl;
    @UriParam(label = "producer")
    @Deprecated
    private String iconEmoji;
    @UriParam(secret = true)
    private String token;
    @UriParam(label = "consumer", defaultValue = "10")
    private String maxResults = "10";
    @UriParam(label = "consumer", defaultValue = "https://slack.com")
    private String serverUrl = "https://slack.com";
    @UriParam(label = "consumer", defaultValue = "false", javaType = "boolean",
              description = "Create exchanges in natural order (oldest to newest) or not")
    private boolean naturalOrder;
    @UriParam(label = "consumer", enums = "PUBLIC_CHANNEL,PRIVATE_CHANNEL,MPIM,IM", defaultValue = "PUBLIC_CHANNEL",
              description = "Type of conversation")
    private ConversationType conversationType = ConversationType.PUBLIC_CHANNEL;

    /**
     * Constructor for SlackEndpoint
     *
     * @param uri         the full component url
     * @param channelName the channel or username the message is directed at
     * @param component   the component that was created
     */
    public SlackEndpoint(String uri, String channelName, SlackComponent component) {
        super(uri, component);
        this.webhookUrl = component.getWebhookUrl();
        this.token = component.getToken();
        this.channel = channelName;

        // ScheduledPollConsumer default delay is 500 millis and that is too often for polling slack,
        // so we override with a new default value. End user can override this value by providing a delay parameter
        setDelay(SlackConsumer.DEFAULT_CONSUMER_DELAY);
    }

    @Override
    public Producer createProducer() throws Exception {
        if (ObjectHelper.isEmpty(token) && ObjectHelper.isEmpty(webhookUrl)) {
            throw new RuntimeCamelException(
                    "Missing required endpoint configuration: token or webhookUrl must be defined for Slack producer");
        }
        return new SlackProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        if (ObjectHelper.isEmpty(token)) {
            throw new RuntimeCamelException(
                    "Missing required endpoint configuration: token must be defined for Slack consumer");
        }
        if (ObjectHelper.isEmpty(channel)) {
            throw new RuntimeCamelException(
                    "Missing required endpoint configuration: channel must be defined for Slack consumer");
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
     * The channel name (syntax #name) or slack user (syntax @userName) to send a message directly to an user.
     */
    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getUsername() {
        return username;
    }

    /**
     * This is the username that the bot will have when sending messages to a channel or user.
     */
    public void setUsername(String username) {
        this.username = username;
    }

    public String getIconUrl() {
        return iconUrl;
    }

    /**
     * The avatar that the component will use when sending message to a channel or user.
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
     * The token to access Slack. This app needs to have channels:history, groups:history, im:history, mpim:history,
     * channels:read, groups:read, im:read and mpim:read permissions. The User OAuth Token is the kind of token needed.
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

    /**
     * Is consuming message in natural order
     */
    public void setNaturalOrder(boolean naturalOrder) {
        this.naturalOrder = naturalOrder;
    }

    public boolean isNaturalOrder() {
        return naturalOrder;
    }

    /**
     * The type of the conversation
     */
    public void setConversationType(ConversationType conversationType) {
        this.conversationType = conversationType;
    }

    public ConversationType getConversationType() {
        return conversationType;
    }

    /**
     * Milliseconds before the next poll.
     */
    @Override
    public void setDelay(long delay) {
        super.setDelay(delay);
        this.delay = delay;
    }

}
