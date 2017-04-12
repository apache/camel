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
package org.apache.camel.component.milo.server.internal;

import java.util.Collection;
import java.util.LinkedList;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;

import org.eclipse.milo.opcua.sdk.core.AccessLevel;
import org.eclipse.milo.opcua.sdk.server.api.ServerNodeMap;
import org.eclipse.milo.opcua.sdk.server.nodes.UaObjectNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ubyte;

public class CamelServerItem {
    private static final Logger LOG = LoggerFactory.getLogger(CamelServerItem.class);

    private UaObjectNode baseNode;
    private UaVariableNode item;

    private DataValue value = new DataValue(StatusCode.BAD);
    private final Set<Consumer<DataValue>> listeners = new CopyOnWriteArraySet<>();

    public CamelServerItem(final String itemId, final ServerNodeMap nodeManager, final UShort namespaceIndex, final UaObjectNode baseNode) {

        this.baseNode = baseNode;

        final NodeId nodeId = new NodeId(namespaceIndex, "items-" + itemId);
        final QualifiedName qname = new QualifiedName(namespaceIndex, itemId);
        final LocalizedText displayName = LocalizedText.english(itemId);

        // create variable node

        this.item = new UaVariableNode(nodeManager, nodeId, qname, displayName) {

            @Override
            public DataValue getValue() {
                return getDataValue();
            }

            @Override
            public synchronized void setValue(final DataValue value) {
                setDataValue(value);
            }

        };

        // item.setDataType();
        this.item.setAccessLevel(ubyte(AccessLevel.getMask(AccessLevel.READ_WRITE)));
        this.item.setUserAccessLevel(ubyte(AccessLevel.getMask(AccessLevel.READ_WRITE)));

        baseNode.addComponent(this.item);
    }

    public void dispose() {
        this.baseNode.removeComponent(this.item);
        this.listeners.clear();
    }

    public void addWriteListener(final Consumer<DataValue> consumer) {
        this.listeners.add(consumer);
    }

    public void removeWriteListener(final Consumer<DataValue> consumer) {
        this.listeners.remove(consumer);
    }

    protected void setDataValue(final DataValue value) {
        LOG.debug("setValue -> {}", value);
        runThrough(this.listeners, c -> c.accept(value));
    }

    /**
     * Run through a list, aggregating errors
     * <p>
     * The consumer is called for each list item, regardless if the consumer did
     * through an exception. All exceptions are caught and thrown in one
     * RuntimeException. The first exception being wrapped directly while the
     * latter ones, if any, are added as suppressed exceptions.
     * </p>
     *
     * @param list the list to run through
     * @param consumer the consumer processing list elements
     */
    protected <T> void runThrough(final Collection<Consumer<T>> list, final Consumer<Consumer<T>> consumer) {
        LinkedList<Throwable> errors = null;

        for (final Consumer<T> listener : list) {
            try {
                consumer.accept(listener);
            } catch (final Throwable e) {
                if (errors == null) {
                    errors = new LinkedList<>();
                }
                errors.add(e);
            }
        }

        if (errors == null || errors.isEmpty()) {
            return;
        }

        final RuntimeException ex = new RuntimeException(errors.pollFirst());
        errors.forEach(ex::addSuppressed);
        throw ex;
    }

    protected DataValue getDataValue() {
        return this.value;
    }

    public void update(final Object value) {
        this.value = new DataValue(new Variant(value), StatusCode.GOOD, DateTime.now());
    }
}
