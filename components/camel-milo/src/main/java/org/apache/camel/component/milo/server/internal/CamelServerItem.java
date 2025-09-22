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
package org.apache.camel.component.milo.server.internal;

import java.util.Collection;
import java.util.LinkedList;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.apache.camel.RuntimeCamelException;
import org.eclipse.milo.opcua.sdk.core.AccessLevel;
import org.eclipse.milo.opcua.sdk.server.nodes.UaNodeContext;
import org.eclipse.milo.opcua.sdk.server.nodes.UaObjectNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode;
import org.eclipse.milo.opcua.sdk.server.nodes.filters.AttributeFilter;
import org.eclipse.milo.opcua.sdk.server.nodes.filters.AttributeFilterContext;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CamelServerItem {
    private static final Logger LOG = LoggerFactory.getLogger(CamelServerItem.class);

    private final String itemId;
    private final UaObjectNode baseNode;
    private final UaVariableNode item;

    private final Set<Consumer<DataValue>> listeners = new CopyOnWriteArraySet<>();
    private DataValue value = new DataValue(StatusCode.BAD);

    public CamelServerItem(final String itemId, final UaNodeContext nodeContext, final UShort namespaceIndex,
                           final UaObjectNode baseNode) {

        this.itemId = itemId;
        this.baseNode = baseNode;

        final NodeId nodeId = new NodeId(namespaceIndex, itemId);
        final QualifiedName qname = new QualifiedName(namespaceIndex, itemId);
        final LocalizedText displayName = LocalizedText.english(itemId);

        // create variable node

        final Predicate<AttributeId> filter = AttributeId.Value::equals;
        this.item = UaVariableNode.build(nodeContext, builder -> builder
                .setNodeId(nodeId)
                .setBrowseName(qname)
                .setDisplayName(displayName)
                .setAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
                .setUserAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE))
                .addAttributeFilter(new AttributeFilter() {

                    @Override
                    public @Nullable Object getAttribute(AttributeFilterContext ctx, AttributeId attributeId) {
                        if (filter.test(attributeId) && ctx.getSession().isPresent()) {
                            return getDataValue();
                        }
                        return ctx.getAttribute(attributeId);
                    }

                    @Override
                    public void setAttribute(AttributeFilterContext ctx, AttributeId attributeId, @Nullable Object value) {
                        if (filter.test(attributeId) && ctx.getSession().isPresent()) {
                            setDataValue((DataValue) value);
                        }
                        ctx.setAttribute(attributeId, value);
                    }
                })
                .buildAndAdd());

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
     * The consumer is called for each list item, regardless if the consumer did through an exception. All exceptions
     * are caught and thrown in one RuntimeException. The first exception being wrapped directly while the latter ones,
     * if any, are added as suppressed exceptions.
     * </p>
     *
     * @param list     the list to run through
     * @param consumer the consumer processing list elements
     */
    protected <T> void runThrough(final Collection<Consumer<T>> list, final Consumer<Consumer<T>> consumer) {
        LinkedList<Throwable> errors = null;

        for (final Consumer<T> listener : list) {
            try {
                consumer.accept(listener);
            } catch (final Exception e) {
                if (errors == null) {
                    errors = new LinkedList<>();
                }
                errors.add(e);
            }
        }

        if (errors == null || errors.isEmpty()) {
            return;
        }

        final RuntimeException ex = new RuntimeCamelException(errors.pollFirst());
        errors.forEach(ex::addSuppressed);
        throw ex;
    }

    protected DataValue getDataValue() {
        return this.value;
    }

    public void update(final Object value) {
        if (value instanceof DataValue) {
            this.value = (DataValue) value;
        } else if (value instanceof Variant) {
            this.value = new DataValue((Variant) value, StatusCode.GOOD, DateTime.now());
        } else {
            this.value = new DataValue(new Variant(value), StatusCode.GOOD, DateTime.now());
        }
    }

    @Override
    public String toString() {
        return "[CamelServerItem - '" + this.itemId + "']";
    }
}
