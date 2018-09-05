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
package org.apache.camel.component.atomix.client.set;

import java.time.Duration;

import io.atomix.collections.DistributedSet;
import io.atomix.resource.ReadConsistency;
import org.apache.camel.AsyncCallback;
import org.apache.camel.InvokeOnHeader;
import org.apache.camel.Message;
import org.apache.camel.component.atomix.client.AbstractAtomixClientProducer;
import org.apache.camel.util.ObjectHelper;

import static org.apache.camel.component.atomix.client.AtomixClientConstants.RESOURCE_ACTION;
import static org.apache.camel.component.atomix.client.AtomixClientConstants.RESOURCE_NAME;
import static org.apache.camel.component.atomix.client.AtomixClientConstants.RESOURCE_READ_CONSISTENCY;
import static org.apache.camel.component.atomix.client.AtomixClientConstants.RESOURCE_TTL;
import static org.apache.camel.component.atomix.client.AtomixClientConstants.RESOURCE_VALUE;

final class AtomixSetProducer extends AbstractAtomixClientProducer<AtomixSetEndpoint, DistributedSet> {
    private final AtomixSetConfiguration configuration;

    protected AtomixSetProducer(AtomixSetEndpoint endpoint) {
        super(endpoint);
        this.configuration = endpoint.getConfiguration();
    }

    // *********************************
    // Handlers
    // *********************************

    @InvokeOnHeader("ADD")
    boolean onAdd(Message message, AsyncCallback callback) throws Exception {
        final DistributedSet<Object> set = getResource(message);
        final long ttl = message.getHeader(RESOURCE_TTL, configuration::getTtl, long.class);
        final Object val = message.getHeader(RESOURCE_VALUE, message::getBody, Object.class);

        ObjectHelper.notNull(val, RESOURCE_VALUE);

        if (ttl > 0) {
            set.add(val, Duration.ofMillis(ttl)).thenAccept(
                result -> processResult(message, callback, result)
            );
        } else {
            set.add(val).thenAccept(
                result -> processResult(message, callback, result)
            );
        }

        return false;
    }

    @InvokeOnHeader("CLEAR")
    boolean onClear(Message message, AsyncCallback callback) throws Exception {
        final DistributedSet<Object> set = getResource(message);

        set.clear().thenAccept(
            result -> processResult(message, callback, result)
        );

        return false;
    }

    @InvokeOnHeader("CONTAINS")
    boolean onContains(Message message, AsyncCallback callback) throws Exception {
        final DistributedSet<Object> set = getResource(message);
        final ReadConsistency consistency = message.getHeader(RESOURCE_READ_CONSISTENCY,  configuration::getReadConsistency, ReadConsistency.class);
        final Object value = message.getHeader(RESOURCE_VALUE, message::getBody, Object.class);

        ObjectHelper.notNull(value, RESOURCE_VALUE);

        if (consistency != null) {
            set.contains(value, consistency).thenAccept(
                result -> processResult(message, callback, result)
            );
        } else {
            set.contains(value).thenAccept(
                result -> processResult(message, callback, result)
            );
        }

        return false;
    }

    @InvokeOnHeader("IS_EMPTY")
    boolean onIsEmpty(Message message, AsyncCallback callback) throws Exception {
        final DistributedSet<Object> set = getResource(message);
        final ReadConsistency consistency = message.getHeader(RESOURCE_READ_CONSISTENCY,  configuration::getReadConsistency, ReadConsistency.class);

        if (consistency != null) {
            set.isEmpty(consistency).thenAccept(
                result -> processResult(message, callback, result)
            );
        } else {
            set.isEmpty().thenAccept(
                result -> processResult(message, callback, result)
            );
        }

        return false;
    }

    @InvokeOnHeader("REMOVE")
    boolean onRemove(Message message, AsyncCallback callback) throws Exception {
        final DistributedSet<Object> set = getResource(message);
        final Object value = message.getHeader(RESOURCE_VALUE, message::getBody, Object.class);

        ObjectHelper.notNull(value, RESOURCE_VALUE);

        set.remove(value).thenAccept(
            result -> processResult(message, callback, result)
        );

        return false;
    }

    @InvokeOnHeader("SIZE")
    boolean onSize(Message message, AsyncCallback callback) throws Exception {
        final DistributedSet<Object> set = getResource(message);
        final ReadConsistency consistency = message.getHeader(RESOURCE_READ_CONSISTENCY,  configuration::getReadConsistency, ReadConsistency.class);

        if (consistency != null) {
            set.size(consistency).thenAccept(
                result -> processResult(message, callback, result)
            );
        } else {
            set.size().thenAccept(
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
    protected DistributedSet<Object> createResource(String resourceName) {
        return getAtomixEndpoint()
            .getAtomix()
            .getSet(
                resourceName,
                new DistributedSet.Config(getAtomixEndpoint().getConfiguration().getResourceOptions(resourceName)),
                new DistributedSet.Options(getAtomixEndpoint().getConfiguration().getResourceConfig(resourceName)))
            .join();
    }
}
