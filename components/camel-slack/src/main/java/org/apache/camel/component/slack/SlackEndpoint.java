/**
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
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;

/**
 * The slack component allows you to send messages to Slack.
 */
@UriEndpoint(firstVersion = "2.16.0", scheme = "slack", title = "Slack", syntax = "slack:channel", producerOnly = true, label = "social")
public class SlackEndpoint extends DefaultEndpoint {

    @UriPath @Metadata(required = "true")
    private String channel;
    @UriParam
    private String webhookUrl;
    @UriParam(secret = true)
    private String username;
    @UriParam
    private String iconUrl;
    @UriParam
    private String iconEmoji;

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
        throw new UnsupportedOperationException("You cannot consume slack messages from this endpoint: " + getEndpointUri());
    }

    @Override
    public boolean isSingleton() {
        return true;
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
     * The channel name (syntax #name) or slackuser (syntax @userName) to send a message directly to an user.
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
}

