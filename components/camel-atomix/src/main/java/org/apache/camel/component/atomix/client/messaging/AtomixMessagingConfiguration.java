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
package org.apache.camel.component.atomix.client.messaging;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.atomix.client.AtomixClientConfiguration;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

@UriParams
public class AtomixMessagingConfiguration extends AtomixClientConfiguration {
    @UriParam(defaultValue = "DIRECT")
    private AtomixMessaging.Action defaultAction = AtomixMessaging.Action.DIRECT;
    @UriParam
    private String memberName;
    @UriParam
    private String channelName;
    @UriParam(defaultValue = "ALL")
    private AtomixMessaging.BroadcastType broadcastType = AtomixMessaging.BroadcastType.ALL;

    // ****************************************
    // Properties
    // ****************************************

    public AtomixMessaging.Action getDefaultAction() {
        return defaultAction;
    }

    /**
     * The default action.
     */
    public void setDefaultAction(AtomixMessaging.Action defaultAction) {
        this.defaultAction = defaultAction;
    }

    public String getMemberName() {
        return memberName;
    }

    /**
     * The Atomix Group member name
     */
    public void setMemberName(String memberName) {
        this.memberName = memberName;
    }

    public String getChannelName() {
        return channelName;
    }

    /**
     * The messaging channel name
     */
    public void setChannelName(String channelName) {
        this.channelName = channelName;
    }

    public AtomixMessaging.BroadcastType getBroadcastType() {
        return broadcastType;
    }

    /**
     * The broadcast type.
     */
    public void setBroadcastType(AtomixMessaging.BroadcastType broadcastType) {
        this.broadcastType = broadcastType;
    }

    // ****************************************
    // Copy
    // ****************************************

    public AtomixMessagingConfiguration copy() {
        try {
            return (AtomixMessagingConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
