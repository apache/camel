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

import java.io.ObjectInputFilter;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Category;
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
import org.apache.camel.support.DeserializationFilterHelper;
import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.View;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Exchange messages with JGroups clusters.
 */
@UriEndpoint(firstVersion = "2.13.0", scheme = "jgroups", title = "JGroups", syntax = "jgroups:clusterName",
             category = { Category.CLUSTERING, Category.MESSAGING }, headersClass = JGroupsConstants.class)
public class JGroupsEndpoint extends DefaultEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(JGroupsEndpoint.class);
    private AtomicInteger connectCount = new AtomicInteger();

    private JChannel channel;
    private JChannel resolvedChannel;

    @UriPath
    @Metadata(required = true)
    private String clusterName;
    @UriParam
    private String channelProperties;
    @UriParam(label = "consumer")
    private boolean enableViewMessages;
    @UriParam(label = "consumer,security",
              description = "Sets an ObjectInputFilter pattern (jdk.serialFilter syntax) applied as a defense-in-depth"
                            + " check on the class of the message body deserialized by org.jgroups.Message.getObject()."
                            + " The pattern is evaluated after JGroups has deserialized the payload, so this option alone"
                            + " does not prevent gadget-chain execution that happens inside the JGroups receive path;"
                            + " to block such attacks, also configure the JVM-wide -Djdk.serialFilter and secure the"
                            + " channel with AUTH and encryption. When this option is not set and no JVM-wide filter is"
                            + " configured, a conservative default filter denying java.net.** and otherwise allowing"
                            + " java.**, javax.** and org.apache.camel.** is applied. Use * to accept any type.")
    private String deserializationFilter;

    private volatile ObjectInputFilter resolvedDeserializationFilter;

    public JGroupsEndpoint(String endpointUri, Component component, JChannel channel, String clusterName,
                           String channelProperties, boolean enableViewMessages) {
        super(endpointUri, component);
        this.channel = channel;
        this.clusterName = clusterName;
        this.channelProperties = channelProperties;
        this.enableViewMessages = enableViewMessages;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new JGroupsProducer(this, clusterName);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        JGroupsConsumer consumer = new JGroupsConsumer(this, processor, clusterName);
        configureConsumer(consumer);
        return consumer;
    }

    public Exchange createExchange(Message message) {
        Exchange exchange = createExchange();
        exchange.getIn().setHeader(JGroupsConstants.HEADER_JGROUPS_ORIGINAL_MESSAGE, message);
        exchange.getIn().setHeader(JGroupsConstants.HEADER_JGROUPS_SRC, message.getSrc());
        exchange.getIn().setHeader(JGroupsConstants.HEADER_JGROUPS_DEST, message.getDest());
        Object body = message.getObject();
        if (body != null) {
            checkDeserializedType(body.getClass());
        }
        exchange.getIn().setBody(body);
        return exchange;
    }

    private void checkDeserializedType(Class<?> type) {
        ObjectInputFilter filter = resolvedDeserializationFilter;
        if (filter == null) {
            // the endpoint was not started, so resolve the filter on the fly rather than skipping the check
            filter = resolveDeserializationFilter();
            resolvedDeserializationFilter = filter;
        }
        if (DeserializationFilterHelper.checkClass(filter, type) == ObjectInputFilter.Status.REJECTED) {
            throw new JGroupsException(
                    "JGroups message deserialization blocked for class: " + type.getName()
                                       + ". Configure the 'deserializationFilter' endpoint option or -Djdk.serialFilter"
                                       + " to allow it.");
        }
    }

    public Exchange createExchange(View view) {
        Exchange exchange = createExchange();
        exchange.getIn().setBody(view);
        return exchange;
    }

    @Override
    public Exchange createExchange() {
        Exchange exchange = super.createExchange();
        exchange.getIn().setHeader(JGroupsConstants.HEADER_JGROUPS_CHANNEL_ADDRESS, resolvedChannel.getAddress());
        return exchange;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        resolvedChannel = resolveChannel();
        resolvedDeserializationFilter = resolveDeserializationFilter();
    }

    @Override
    protected void doStop() throws Exception {
        if (resolvedChannel != null) {
            LOG.trace("Closing JGroups Channel {}", getEndpointUri());
            resolvedChannel.close();
        }
        super.doStop();
    }

    private ObjectInputFilter resolveDeserializationFilter() {
        return DeserializationFilterHelper.resolveDeserializationFilter(
                deserializationFilter, DeserializationFilterHelper.DEFAULT_CLASS_DESERIALIZATION_FILTER);
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
     * If set to true, the consumer endpoint will receive org.jgroups.View messages as well (not only
     * org.jgroups.Message instances). By default only regular messages are consumed by the endpoint.
     */
    public void setEnableViewMessages(boolean enableViewMessages) {
        this.enableViewMessages = enableViewMessages;
    }

    public String getDeserializationFilter() {
        return deserializationFilter;
    }

    /**
     * Sets an {@link ObjectInputFilter} pattern (same syntax as {@code jdk.serialFilter}) applied as a defense-in-depth
     * check on the class of the message body deserialized by {@code org.jgroups.Message#getObject()}. The pattern is
     * evaluated after JGroups has deserialized the payload, so this option alone does not prevent gadget-chain
     * execution that happens inside the JGroups receive path; to block such attacks, also configure the JVM-wide
     * {@code -Djdk.serialFilter} and secure the channel with {@code AUTH} and encryption. When this option is not set
     * and no JVM-wide filter is configured, a conservative default filter denying {@code java.net.**} and otherwise
     * allowing {@code java.**}, {@code javax.**} and {@code org.apache.camel.**} is applied. Use {@code *} to accept
     * any type.
     */
    public void setDeserializationFilter(String deserializationFilter) {
        this.deserializationFilter = deserializationFilter;
    }

}
