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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SlackEndpoint extends DefaultEndpoint {

    private static final transient Logger LOG = LoggerFactory.getLogger(SlackEndpoint.class);

    private String webhookUrl;
    private String username;
    private String channel;
    private String iconUrl;
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

    /**
     * Creates a SlackProducer
     *
     * @return SlackProducer
     * @throws Exception
     */
    @Override
    public Producer createProducer() throws Exception {
        SlackProducer producer = new SlackProducer(this);
        return producer;
    }

    /**
     * Unsupported operation
     *
     * @param processor
     * @return
     * @throws java.lang.UnsupportedOperationException
     */
    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("You cannot consume slack messages from this endpoint: " + getEndpointUri());
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    public String getWebhookUrl() {
        return webhookUrl;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getChannel() {
        return channel;
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
}

