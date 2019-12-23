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
package org.apache.camel.component.atomix.client.value;

import java.time.Duration;

import io.atomix.resource.ReadConsistency;
import io.atomix.variables.DistributedValue;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Message;
import org.apache.camel.component.atomix.client.AbstractAtomixClientProducer;
import org.apache.camel.spi.InvokeOnHeader;
import org.apache.camel.util.ObjectHelper;

import static org.apache.camel.component.atomix.client.AtomixClientConstants.RESOURCE_ACTION;
import static org.apache.camel.component.atomix.client.AtomixClientConstants.RESOURCE_NAME;
import static org.apache.camel.component.atomix.client.AtomixClientConstants.RESOURCE_OLD_VALUE;
import static org.apache.camel.component.atomix.client.AtomixClientConstants.RESOURCE_READ_CONSISTENCY;
import static org.apache.camel.component.atomix.client.AtomixClientConstants.RESOURCE_TTL;
import static org.apache.camel.component.atomix.client.AtomixClientConstants.RESOURCE_VALUE;

public final class AtomixValueProducer extends AbstractAtomixClientProducer<AtomixValueEndpoint, DistributedValue> {
    private final AtomixValueConfiguration configuration;

    protected AtomixValueProducer(AtomixValueEndpoint endpoint) {
        super(endpoint);
        this.configuration = endpoint.getConfiguration();
    }

    // *********************************
    // Handlers
    // *********************************

    @InvokeOnHeader("SET")
    boolean onSet(Message message, AsyncCallback callback) throws Exception {
        final DistributedValue<Object> value = getResource(message);
        final long ttl = message.getHeader(RESOURCE_TTL, configuration::getTtl, long.class);
        final Object val = message.getHeader(RESOURCE_VALUE, message::getBody, Object.class);

        ObjectHelper.notNull(val, RESOURCE_VALUE);

        if (ttl > 0) {
            value.set(val, Duration.ofMillis(ttl)).thenAccept(
                result -> processResult(message, callback, result)
            );
        } else {
            value.set(val).thenAccept(
                result -> processResult(message, callback, result)
            );
        }

        return false;
    }

    @InvokeOnHeader("GET")
    boolean onGet(Message message, AsyncCallback callback) throws Exception {
        final DistributedValue<Object> value = getResource(message);
        final ReadConsistency consistency = message.getHeader(RESOURCE_READ_CONSISTENCY,  configuration::getReadConsistency, ReadConsistency.class);

        if (consistency != null) {
            value.get(consistency).thenAccept(
                result -> processResult(message, callback, result)
            );
        } else {
            value.get().thenAccept(
                result -> processResult(message, callback, result)
            );
        }

        return false;
    }

    @InvokeOnHeader("GET_AND_SET")
    boolean onGetAndSet(Message message, AsyncCallback callback) throws Exception {
        final DistributedValue<Object> value = getResource(message);
        final long ttl = message.getHeader(RESOURCE_TTL, configuration::getTtl, long.class);
        final Object val = message.getHeader(RESOURCE_VALUE, message::getBody, Object.class);

        ObjectHelper.notNull(val, RESOURCE_VALUE);

        if (ttl > 0) {
            value.getAndSet(val, Duration.ofMillis(ttl)).thenAccept(
                result -> processResult(message, callback, result)
            );
        } else {
            value.getAndSet(val).thenAccept(
                result -> processResult(message, callback, result)
            );
        }

        return false;
    }

    @InvokeOnHeader("COMPARE_AND_SET")
    boolean onCompareAndSet(Message message, AsyncCallback callback) throws Exception {
        final DistributedValue<Object> value = getResource(message);
        final long ttl = message.getHeader(RESOURCE_TTL, configuration::getTtl, long.class);
        final Object newVal = message.getHeader(RESOURCE_VALUE, message::getBody, Object.class);
        final Object oldVal = message.getHeader(RESOURCE_OLD_VALUE, Object.class);

        ObjectHelper.notNull(newVal, RESOURCE_VALUE);
        ObjectHelper.notNull(oldVal, RESOURCE_OLD_VALUE);

        if (ttl > 0) {
            value.compareAndSet(oldVal, newVal, Duration.ofMillis(ttl)).thenAccept(
                result -> processResult(message, callback, result)
            );
        } else {
            value.compareAndSet(oldVal, newVal).thenAccept(
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
    protected DistributedValue<Object> createResource(String resourceName) {
        return getAtomixEndpoint()
            .getAtomix()
            .getValue(
                resourceName,
                new DistributedValue.Config(getAtomixEndpoint().getConfiguration().getResourceOptions(resourceName)),
                new DistributedValue.Options(getAtomixEndpoint().getConfiguration().getResourceConfig(resourceName)))
            .join();
    }
}
