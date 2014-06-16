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

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.impl.UriEndpointComponent;
import org.jgroups.Channel;

/**
 * Component providing support for messages multicasted from- or to JGroups channels ({@code org.jgroups.Channel}).
 */
public class JGroupsComponent extends UriEndpointComponent {

    private Channel channel;

    private String channelProperties;

    private Boolean enableViewMessages;

    public JGroupsComponent() {
        super(JGroupsEndpoint.class);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String clusterName, Map<String, Object> parameters) throws Exception {
        return new JGroupsEndpoint(uri, this, channel, clusterName, channelProperties, enableViewMessages);
    }

    public Channel getChannel() {
        return channel;
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
    }

    public String getChannelProperties() {
        return channelProperties;
    }

    public void setChannelProperties(String channelProperties) {
        this.channelProperties = channelProperties;
    }

    public Boolean getEnableViewMessages() {
        return enableViewMessages;
    }

    public void setEnableViewMessages(Boolean enableViewMessages) {
        this.enableViewMessages = enableViewMessages;
    }

}
