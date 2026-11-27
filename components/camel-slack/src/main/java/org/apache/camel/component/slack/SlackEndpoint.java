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
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.ScheduledPollEndpoint;
import org.apache.camel.util.ObjectHelper;

/**
 * Send messages to a Slack channel or user, and receive the new messages of a Slack conversation.
 */
@UriEndpoint(firstVersion = "2.16.0", scheme = "slack", title = "Slack", syntax = "slack:channel",
             category = { Category.SOCIAL })
@BodyTypes(consumerOut = @BodyType(value = Carrier.POJO, javaType = "com.slack.api.model.Message",
                                   description = "One message per new message of the conversation, the most recent first, or"
                                                 + " the oldest first with naturalOrder; the first poll gives the last message"
                                                 + " of the history. The text is ${body.text}"),
           producerIn = @BodyType(value = { Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.FILE, Carrier.POJO },
                                  description = "The text of the message, or a com.slack.api.model.Message with its blocks:"
                                                + " bytes, a stream or a file are read as text. A Map or another POJO is sent"
                                                + " as its toString(): marshal it first. No body sends no text, which Slack"
                                                + " rejects"),
           producerOut = @BodyType(value = Carrier.UNCHANGED,
                                   description = "Unchanged: the answer of Slack is not kept"))
public class SlackEndpoint extends ScheduledPollEndpoint {

    @UriParam(defaultValue = "" + SlackConsumer.DEFAULT_CONSUMER_DELAY, javaType = "java.time.Duration",
              label = "consumer,scheduler",
              description = "Milliseconds before the next poll.")
    private long delay = SlackConsumer.DEFAULT_CONSUMER_DELAY;

    @UriPath(description = "For the producer, where the message goes: a channel name such as #general, a channel ID, or a"
                           + " user ID for a direct message, as chat.postMessage of Slack accepts. For the consumer, the name"
                           + " of the conversation without #, such as general, which it looks up among the conversations"
                           + " of conversationType.")
    @Metadata(required = true)
    private String channel;
    @UriParam(label = "producer",
              description = "The URL of a Slack incoming webhook, such as https://hooks.slack.com/services/..., that the"
                            + " producer posts the message to as JSON. It is used only when no token is set. The consumer"
                            + " does not use it.")
    private String webhookUrl;
    @UriParam(label = "producer", security = "secret",
              description = "The name shown as the author of the messages that the producer sends. Slack applies it only"
                            + " for a bot token with the chat:write.customize scope, or for a legacy incoming webhook.")
    @Deprecated
    private String username;
    @UriParam(label = "producer",
              description = "The URL of an image shown as the icon of the messages that the producer sends. Slack"
                            + " applies it only for a bot token with the chat:write.customize scope, or for a legacy"
                            + " incoming webhook. iconEmoji takes precedence over it.")
    @Deprecated
    private String iconUrl;
    @UriParam(label = "producer",
              description = "An emoji, such as :ghost:, shown as the icon of the messages that the producer sends. Slack"
                            + " applies it only for a bot token with the chat:write.customize scope, or for a legacy"
                            + " incoming webhook.")
    @Deprecated
    private String iconEmoji;
    @UriParam(security = "secret",
              description = "The OAuth token of a Slack app, a bot token (xoxb-) or a user token (xoxp-). The producer"
                            + " posts with chat.postMessage, which needs the chat:write scope, and uses the token instead"
                            + " of webhookUrl when both are set. The consumer requires it, with the history and read"
                            + " scopes of conversationType, such as channels:history and channels:read.")
    private String token;
    @UriParam(label = "consumer", defaultValue = "10")
    private String maxResults = "10";
    @UriParam(label = "consumer", defaultValue = "https://slack.com",
              description = "The base URL of the Slack Web API. The consumer, and the producer with a token, call the"
                            + " API methods at this URL followed by /api/. Change it for a proxy or a test server only.")
    private String serverUrl = "https://slack.com";
    @UriParam(label = "consumer", defaultValue = "false", javaType = "boolean",
              description = "Whether the consumer routes the new messages of a poll from the oldest to the newest. By"
                            + " default the most recent message comes first.")
    private boolean naturalOrder;
    @UriParam(label = "consumer", enums = "PUBLIC_CHANNEL,PRIVATE_CHANNEL,MPIM,IM", defaultValue = "PUBLIC_CHANNEL",
              description = "The type of the conversation that the consumer reads, which it looks up by its name with"
                            + " conversations.list of Slack: a public channel, a private channel, a group direct message"
                            + " (MPIM) or a direct message (IM). The token needs the scopes of that type, such as"
                            + " groups:history and groups:read for PRIVATE_CHANNEL.")
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

    public void setWebhookUrl(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    public String getWebhookUrl() {
        return webhookUrl;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getIconUrl() {
        return iconUrl;
    }

    public void setIconUrl(String iconUrl) {
        this.iconUrl = iconUrl;
    }

    public String getIconEmoji() {
        return iconEmoji;
    }

    public void setIconEmoji(String iconEmoji) {
        this.iconEmoji = iconEmoji;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getMaxResults() {
        return maxResults;
    }

    /**
     * The maximum number of messages that the consumer requests from the conversation history at a time. When more new
     * messages are available, the consumer reads the following pages in the same poll.
     */
    public void setMaxResults(String maxResult) {
        this.maxResults = maxResult;
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    public void setNaturalOrder(boolean naturalOrder) {
        this.naturalOrder = naturalOrder;
    }

    public boolean isNaturalOrder() {
        return naturalOrder;
    }

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
