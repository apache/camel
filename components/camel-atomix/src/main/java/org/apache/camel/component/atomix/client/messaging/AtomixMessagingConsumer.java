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

import java.util.ArrayList;
import java.util.List;

import io.atomix.catalyst.concurrent.Listener;
import io.atomix.group.DistributedGroup;
import io.atomix.group.LocalMember;
import io.atomix.group.messaging.Message;
import io.atomix.group.messaging.MessageConsumer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.atomix.client.AbstractAtomixClientConsumer;
import org.apache.camel.component.atomix.client.AtomixClientConstants;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.atomix.client.AtomixClientConstants.CHANNEL_NAME;
import static org.apache.camel.component.atomix.client.AtomixClientConstants.MEMBER_NAME;
import static org.apache.camel.component.atomix.client.AtomixClientConstants.RESOURCE_NAME;

final class AtomixMessagingConsumer extends AbstractAtomixClientConsumer<AtomixMessagingEndpoint> {
    private static final Logger LOGGER = LoggerFactory.getLogger(AtomixMessagingConsumer.class);

    private final List<Listener<Message<Object>>> listeners;
    private final String resultHeader;
    private final String groupName;
    private final String memberName;
    private final String channelName;

    private LocalMember localMember;
    private MessageConsumer<Object> consumer;

    public AtomixMessagingConsumer(AtomixMessagingEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.listeners = new ArrayList<>();
        this.resultHeader = endpoint.getConfiguration().getResultHeader();
        this.groupName = endpoint.getResourceName();
        this.memberName = endpoint.getConfiguration().getMemberName();
        this.channelName = endpoint.getConfiguration().getChannelName();

        ObjectHelper.notNull(groupName, RESOURCE_NAME);
        ObjectHelper.notNull(memberName, MEMBER_NAME);
        ObjectHelper.notNull(channelName, CHANNEL_NAME);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        DistributedGroup group = getAtomixEndpoint().getAtomix().getGroup(
            groupName,
            new DistributedGroup.Config(getAtomixEndpoint().getConfiguration().getResourceOptions(groupName)),
            new DistributedGroup.Options(getAtomixEndpoint().getConfiguration().getResourceConfig(groupName))
        ).join();

        this.localMember = group.join(memberName).join();
        this.consumer = localMember.messaging().consumer(channelName);

        LOGGER.debug("Subscribe to group: {}, member: {}, channel: {}", groupName, memberName, channelName);
        this.listeners.add(consumer.onMessage(this::onMessage));
    }

    @Override
    protected void doStop() throws Exception {
        // close listeners
        listeners.forEach(Listener::close);

        if (this.consumer != null) {
            this.consumer.close();
            this.consumer = null;
        }

        //if (this.localMember != null) {
        //    this.localMember.leave().join();
        //    this.localMember = null;
        //}

        super.doStop();
    }

    // ********************************************
    // Event handler
    // ********************************************

    private void onMessage(Message<Object> message) {
        Exchange exchange = getEndpoint().createExchange();
        exchange.getIn().setHeader(AtomixClientConstants.MESSAGE_ID, message.id());

        if (resultHeader == null) {
            exchange.getIn().setBody(message.message());
        } else {
            exchange.getIn().setHeader(resultHeader, message.message());
        }

        try {
            getProcessor().process(exchange);
            message.ack();
        } catch (Exception e) {
            message.fail();
            getExceptionHandler().handleException(e);
        }
    }
}
