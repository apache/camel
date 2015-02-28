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
package org.apache.camel.component.jgroups;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.jgroups.Channel;
import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.View;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@UriEndpoint(scheme = "jgroups", syntax = "jgroups:clusterName", consumerClass = JGroupsConsumer.class, label = "clustering,messaging")
public class JGroupsEndpoint extends DefaultEndpoint {

    public static final String HEADER_JGROUPS_ORIGINAL_MESSAGE = "JGROUPS_ORIGINAL_MESSAGE";

    public static final String HEADER_JGROUPS_SRC = "JGROUPS_SRC";

    public static final String HEADER_JGROUPS_DEST = "JGROUPS_DEST";

    public static final String HEADER_JGROUPS_CHANNEL_ADDRESS = "JGROUPS_CHANNEL_ADDRESS";

    private static final Logger LOG = LoggerFactory.getLogger(JGroupsEndpoint.class);

    private Channel channel;
    private AtomicInteger connectCount = new AtomicInteger(0);

    private Channel resolvedChannel;

    @UriPath @Metadata(required = "true")
    private String clusterName;

    @UriParam
    private String channelProperties;

    @UriParam
    private Boolean enableViewMessages;

    @UriParam
    private boolean resolvedEnableViewMessages;

    public JGroupsEndpoint(String endpointUri, Component component, Channel channel, String clusterName, String channelProperties, Boolean enableViewMessages) {
        super(endpointUri, component);
        this.channel = channel;
        this.clusterName = clusterName;
        this.channelProperties = channelProperties;
        this.enableViewMessages = enableViewMessages;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new JGroupsProducer(this, resolvedChannel, clusterName);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        return new JGroupsConsumer(this, processor, resolvedChannel, clusterName);
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    public Exchange createExchange(Message message) {
        Exchange exchange = createExchange();
        exchange.getIn().setHeader(HEADER_JGROUPS_ORIGINAL_MESSAGE, message);
        exchange.getIn().setHeader(HEADER_JGROUPS_SRC, message.getSrc());
        exchange.getIn().setHeader(HEADER_JGROUPS_DEST, message.getDest());
        exchange.getIn().setBody(message.getObject());
        return exchange;
    }

    public Exchange createExchange(View view) {
        Exchange exchange = createExchange();
        exchange.getIn().setBody(view);
        return exchange;
    }

    @Override
    public Exchange createExchange() {
        Exchange exchange = super.createExchange();
        exchange.getIn().setHeader(HEADER_JGROUPS_CHANNEL_ADDRESS, resolvedChannel.getAddress());
        return exchange;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        resolvedChannel = resolveChannel();
        resolvedEnableViewMessages = resolveEnableViewMessages();
    }

    @Override
    protected void doStop() throws Exception {
        LOG.trace("Closing JGroups Channel {}", getEndpointUri());
        resolvedChannel.close();
        super.doStop();
    }

    private Channel resolveChannel() throws Exception {
        if (channel != null) {
            return channel;
        }
        if (channelProperties != null && !channelProperties.isEmpty()) {
            return new JChannel(channelProperties);
        }
        return new JChannel();
    }

    /**
     * Connect shared channel, called by producer and consumer.
     * @throws Exception
     */
    public void connect() throws Exception {
        connectCount.incrementAndGet();
        LOG.trace("Connecting JGroups Channel {}", getEndpointUri());
        resolvedChannel.connect(clusterName);
    }

    /**
     * Disconnect shared channel, called by producer and consumer.
     */
    public void disconnect() {
        if (connectCount.decrementAndGet() == 0) {
            LOG.trace("Disconnecting JGroups Channel {}", getEndpointUri());
            resolvedChannel.disconnect();
        }
    }

    private boolean resolveEnableViewMessages() {
        if (enableViewMessages != null) {
            resolvedEnableViewMessages = enableViewMessages;
        }
        return resolvedEnableViewMessages;
    }

    public Channel getChannel() {
        return channel;
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
    }

    public String getClusterName() {
        return clusterName;
    }

    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    public String getChannelProperties() {
        return channelProperties;
    }

    public void setChannelProperties(String channelProperties) {
        this.channelProperties = channelProperties;
    }

    public Channel getResolvedChannel() {
        return resolvedChannel;
    }

    public void setResolvedChannel(Channel resolvedChannel) {
        this.resolvedChannel = resolvedChannel;
    }

    public Boolean getEnableViewMessages() {
        return enableViewMessages;
    }

    public void setEnableViewMessages(Boolean enableViewMessages) {
        this.enableViewMessages = enableViewMessages;
    }

    public boolean isResolvedEnableViewMessages() {
        return resolvedEnableViewMessages;
    }

    public void setResolvedEnableViewMessages(boolean resolvedEnableViewMessages) {
        this.resolvedEnableViewMessages = resolvedEnableViewMessages;
    }
}