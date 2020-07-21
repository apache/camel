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

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.jgroups.JChannel;

/**
 * Component providing support for messages multicasted from- or to JGroups channels ({@code org.jgroups.Channel}).
 */
@Component("jgroups")
public class JGroupsComponent extends DefaultComponent {

    @Metadata
    private JChannel channel;
    @Metadata
    private String channelProperties;
    @Metadata(label = "consumer")
    private boolean enableViewMessages;

    public JGroupsComponent() {
    }

    @Override
    protected Endpoint createEndpoint(String uri, String clusterName, Map<String, Object> parameters) throws Exception {
        JGroupsEndpoint endpoint = new JGroupsEndpoint(uri, this, channel, clusterName, channelProperties, enableViewMessages);
        setProperties(endpoint, parameters);
        return endpoint;
    }

    public JChannel getChannel() {
        return channel;
    }

    /**
     * Channel to use
     */
    public void setChannel(JChannel channel) {
        this.channel = channel;
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
