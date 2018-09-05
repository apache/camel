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
package org.apache.camel.component.atomix.client.queue;

import io.atomix.collections.DistributedQueue;
import io.atomix.resource.ReadConsistency;
import org.apache.camel.AsyncCallback;
import org.apache.camel.InvokeOnHeader;
import org.apache.camel.Message;
import org.apache.camel.component.atomix.client.AbstractAtomixClientProducer;
import org.apache.camel.util.ObjectHelper;

import static org.apache.camel.component.atomix.client.AtomixClientConstants.RESOURCE_ACTION;
import static org.apache.camel.component.atomix.client.AtomixClientConstants.RESOURCE_NAME;
import static org.apache.camel.component.atomix.client.AtomixClientConstants.RESOURCE_READ_CONSISTENCY;
import static org.apache.camel.component.atomix.client.AtomixClientConstants.RESOURCE_VALUE;

final class AtomixQueueProducer extends AbstractAtomixClientProducer<AtomixQueueEndpoint, DistributedQueue> {
    private final AtomixQueueConfiguration configuration;

    protected AtomixQueueProducer(AtomixQueueEndpoint endpoint) {
        super(endpoint);
        this.configuration = endpoint.getConfiguration();
    }

    // *********************************
    // Handlers
    // *********************************

    @InvokeOnHeader("ADD")
    boolean onAdd(Message message, AsyncCallback callback) throws Exception {
        final DistributedQueue<Object> queue = getResource(message);
        final Object val = message.getHeader(RESOURCE_VALUE, message::getBody, Object.class);

        ObjectHelper.notNull(val, RESOURCE_VALUE);

        queue.add(val).thenAccept(
            result -> processResult(message, callback, result)
        );

        return false;
    }

    @InvokeOnHeader("OFFER")
    boolean onOffer(Message message, AsyncCallback callback) throws Exception {
        final DistributedQueue<Object> queue = getResource(message);
        final Object val = message.getHeader(RESOURCE_VALUE, message::getBody, Object.class);

        ObjectHelper.notNull(val, RESOURCE_VALUE);

        queue.offer(val).thenAccept(
            result -> processResult(message, callback, result)
        );

        return false;
    }

    @InvokeOnHeader("PEEK")
    boolean onPeek(Message message, AsyncCallback callback) throws Exception {
        final DistributedQueue<Object> queue = getResource(message);

        queue.peek().thenAccept(
            result -> processResult(message, callback, result)
        );

        return false;
    }

    @InvokeOnHeader("POLL")
    boolean onPoll(Message message, AsyncCallback callback) throws Exception {
        final DistributedQueue<Object> queue = getResource(message);

        queue.poll().thenAccept(
            result -> processResult(message, callback, result)
        );

        return false;
    }

    @InvokeOnHeader("CLEAR")
    boolean onClear(Message message, AsyncCallback callback) throws Exception {
        final DistributedQueue<Object> queue = getResource(message);

        queue.clear().thenAccept(
            result -> processResult(message, callback, result)
        );

        return false;
    }

    @InvokeOnHeader("CONTAINS")
    boolean onContains(Message message, AsyncCallback callback) throws Exception {
        final DistributedQueue<Object> queue = getResource(message);
        final ReadConsistency consistency = message.getHeader(RESOURCE_READ_CONSISTENCY,  configuration::getReadConsistency, ReadConsistency.class);
        final Object value = message.getHeader(RESOURCE_VALUE, message::getBody, Object.class);

        ObjectHelper.notNull(value, RESOURCE_VALUE);

        if (consistency != null) {
            queue.contains(value, consistency).thenAccept(
                result -> processResult(message, callback, result)
            );
        } else {
            queue.contains(value).thenAccept(
                result -> processResult(message, callback, result)
            );
        }

        return false;
    }

    @InvokeOnHeader("IS_EMPTY")
    boolean onIsEmpty(Message message, AsyncCallback callback) throws Exception {
        final DistributedQueue<Object> queue = getResource(message);
        final ReadConsistency consistency = message.getHeader(RESOURCE_READ_CONSISTENCY,  configuration::getReadConsistency, ReadConsistency.class);

        if (consistency != null) {
            queue.isEmpty(consistency).thenAccept(
                result -> processResult(message, callback, result)
            );
        } else {
            queue.isEmpty().thenAccept(
                result -> processResult(message, callback, result)
            );
        }

        return false;
    }

    @InvokeOnHeader("REMOVE")
    boolean onRemove(Message message, AsyncCallback callback) throws Exception {
        final DistributedQueue<Object> queue = getResource(message);
        final Object value = message.getHeader(RESOURCE_VALUE, message::getBody, Object.class);

        if (value == null) {
            queue.remove().thenAccept(
                result -> processResult(message, callback, result)
            );
        } else {
            queue.remove(value).thenAccept(
                result -> processResult(message, callback, result)
            );
        }

        return false;
    }

    @InvokeOnHeader("SIZE")
    boolean onSize(Message message, AsyncCallback callback) throws Exception {
        final DistributedQueue<Object> queue = getResource(message);
        final ReadConsistency consistency = message.getHeader(RESOURCE_READ_CONSISTENCY,  configuration::getReadConsistency, ReadConsistency.class);

        if (consistency != null) {
            queue.size(consistency).thenAccept(
                result -> processResult(message, callback, result)
            );
        } else {
            queue.size().thenAccept(
                result -> processResult(message, callback, result)
            );
        }

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
    protected DistributedQueue<Object> createResource(String resourceName) {
        return getAtomixEndpoint()
            .getAtomix()
            .getQueue(
                resourceName,
                new DistributedQueue.Config(getAtomixEndpoint().getConfiguration().getResourceOptions(resourceName)),
                new DistributedQueue.Options(getAtomixEndpoint().getConfiguration().getResourceConfig(resourceName)))
            .join();
    }
}
