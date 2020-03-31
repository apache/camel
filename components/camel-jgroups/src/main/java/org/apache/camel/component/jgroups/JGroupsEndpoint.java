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
package org.apache.camel.component.jgroups;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.View;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The jgroups component provides exchange of messages between Camel and JGroups clusters.
 */
@UriEndpoint(firstVersion = "2.13.0", scheme = "jgroups", title = "JGroups", syntax = "jgroups:clusterName", label = "clustering,messaging")
public class JGroupsEndpoint extends DefaultEndpoint {

    public static final String HEADER_JGROUPS_ORIGINAL_MESSAGE = "JGROUPS_ORIGINAL_MESSAGE";
    public static final String HEADER_JGROUPS_SRC = "JGROUPS_SRC";
    public static final String HEADER_JGROUPS_DEST = "JGROUPS_DEST";
    public static final String HEADER_JGROUPS_CHANNEL_ADDRESS = "JGROUPS_CHANNEL_ADDRESS";

    private static final Logger LOG = LoggerFactory.getLogger(JGroupsEndpoint.class);
    private AtomicInteger connectCount = new AtomicInteger(0);

    private JChannel channel;
    private JChannel resolvedChannel;

    @UriPath @Metadata(required = true)
    private String clusterName;
    @UriParam
    private String channelProperties;
    @UriParam(label = "consumer")
    private boolean enableViewMessages;

    public JGroupsEndpoint(String endpointUri, Component component, JChannel channel, String clusterName, String channelProperties, boolean enableViewMessages) {
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
        JGroupsConsumer consumer = new JGroupsConsumer(this, processor, resolvedChannel, clusterName);
        configureConsumer(consumer);
        return consumer;
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
    }

    @Override
    protected void doStop() throws Exception {
        if (resolvedChannel != null) {
            LOG.trace("Closing JGroups Channel {}", getEndpointUri());
            resolvedChannel.close();
        }
        super.doStop();
    }

    private JChannel resolveChannel() throws Exception {
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

    public JChannel getChannel() {
        return channel;
    }

    /**
     * The channel to use
     */
    public void setChannel(JChannel channel) {
        this.channel = channel;
    }

    public String getClusterName() {
        return clusterName;
    }

    /**
     * The name of the JGroups cluster the component should connect to.
     */
    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    public String getChannelProperties() {
        return channelProperties;
    }

    /**
     * Specifies configuration properties of the JChannel used by the endpoint.
     */
    public void setChannelProperties(String channelProperties) {
        this.channelProperties = channelProperties;
    }

    JChannel getResolvedChannel() {
        return resolvedChannel;
    }

    public boolean isEnableViewMessages() {
        return enableViewMessages;
    }

    /**
     * If set to true, the consumer endpoint will receive org.jgroups.View messages as well (not only org.jgroups.Message instances).
     * By default only regular messages are consumed by the endpoint.
     */
    public void setEnableViewMessages(boolean enableViewMessages) {
        this.enableViewMessages = enableViewMessages;
    }

}
