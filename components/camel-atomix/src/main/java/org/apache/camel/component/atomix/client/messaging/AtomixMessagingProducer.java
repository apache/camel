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
package org.apache.camel.component.atomix.client.messaging;

import io.atomix.group.DistributedGroup;
import io.atomix.group.GroupMember;
import io.atomix.group.messaging.MessageProducer;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Message;
import org.apache.camel.component.atomix.client.AbstractAtomixClientProducer;
import org.apache.camel.spi.InvokeOnHeader;
import org.apache.camel.util.ObjectHelper;

import static org.apache.camel.component.atomix.client.AtomixClientConstants.BROADCAST_TYPE;
import static org.apache.camel.component.atomix.client.AtomixClientConstants.CHANNEL_NAME;
import static org.apache.camel.component.atomix.client.AtomixClientConstants.MEMBER_NAME;
import static org.apache.camel.component.atomix.client.AtomixClientConstants.RESOURCE_ACTION;
import static org.apache.camel.component.atomix.client.AtomixClientConstants.RESOURCE_NAME;
import static org.apache.camel.component.atomix.client.AtomixClientConstants.RESOURCE_VALUE;
import static org.apache.camel.component.atomix.client.messaging.AtomixMessaging.OPTIONS_BROADCAST;
import static org.apache.camel.component.atomix.client.messaging.AtomixMessaging.OPTIONS_BROADCAST_RANDOM;
import static org.apache.camel.component.atomix.client.messaging.AtomixMessaging.OPTIONS_DIRECT;

public final class AtomixMessagingProducer extends AbstractAtomixClientProducer<AtomixMessagingEndpoint, DistributedGroup> {
    private final AtomixMessagingConfiguration configuration;

    protected AtomixMessagingProducer(AtomixMessagingEndpoint endpoint) {
        super(endpoint);
        this.configuration = endpoint.getConfiguration();
    }

    // *********************************
    // Handlers
    // *********************************

    @InvokeOnHeader("DIRECT")
    boolean onDirect(Message message, AsyncCallback callback) throws Exception {
        final Object value = message.getHeader(RESOURCE_VALUE, message::getBody, Object.class);
        final String memberName = message.getHeader(MEMBER_NAME, configuration::getMemberName, String.class);
        final String channelName = message.getHeader(CHANNEL_NAME, configuration::getChannelName, String.class);

        ObjectHelper.notNull(memberName, MEMBER_NAME);
        ObjectHelper.notNull(channelName, CHANNEL_NAME);
        ObjectHelper.notNull(value, RESOURCE_VALUE);

        final DistributedGroup group = getResource(message);
        final GroupMember member = group.member(memberName);
        final MessageProducer<Object> producer = member.messaging().producer(channelName, OPTIONS_DIRECT);

        producer.send(value).thenAccept(
            result -> processResult(message, callback, result)
        );

        return false;
    }

    @InvokeOnHeader("BROADCAST")
    boolean onBroadcast(Message message, AsyncCallback callback) throws Exception {
        final Object value = message.getHeader(RESOURCE_VALUE, message::getBody, Object.class);
        final String channelName = message.getHeader(CHANNEL_NAME, configuration::getChannelName, String.class);
        final AtomixMessaging.BroadcastType type = message.getHeader(BROADCAST_TYPE, configuration::getBroadcastType, AtomixMessaging.BroadcastType.class);

        ObjectHelper.notNull(channelName, CHANNEL_NAME);
        ObjectHelper.notNull(value, RESOURCE_VALUE);

        MessageProducer.Options options = type == AtomixMessaging.BroadcastType.RANDOM
            ? OPTIONS_BROADCAST_RANDOM
            : OPTIONS_BROADCAST;

        final DistributedGroup group = getResource(message);
        final MessageProducer<Object> producer = group.messaging().producer(channelName, options);

        producer.send(value).thenRun(
            () -> processResult(message, callback, null)
        );

        return false;
    }

    // *********************************
    // Implementation
    // *********************************

    @Override
    protected String getProcessorKey(Message message) {
        return message.getHeader(RESOURCE_ACTION, configuration::getDefaultAction, String.class);
    }

    @Override
    protected String getResourceName(Message message) {
        return message.getHeader(RESOURCE_NAME, getAtomixEndpoint()::getResourceName, String.class);
    }

    @Override
    protected DistributedGroup createResource(String resourceName) {
        return getAtomixEndpoint().getAtomix()
            .getGroup(
                resourceName,
                new DistributedGroup.Config(getAtomixEndpoint().getConfiguration().getResourceOptions(resourceName)),
                new DistributedGroup.Options(getAtomixEndpoint().getConfiguration().getResourceConfig(resourceName))
            ).join();
    }
}
