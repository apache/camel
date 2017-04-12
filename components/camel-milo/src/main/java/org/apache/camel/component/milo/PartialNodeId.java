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
package org.apache.camel.component.milo;

import java.io.Serializable;
import java.util.UUID;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects;

import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExpandedNodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;
import org.eclipse.milo.opcua.stack.core.types.enumerated.IdType;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ushort;

public class PartialNodeId {

    private IdType type;

    private final Serializable id;

    public PartialNodeId(final int id) {
        this(uint(id));
    }

    public PartialNodeId(final UInteger id) {
        requireNonNull(id);
        this.id = id;
    }

    public PartialNodeId(final String id) {
        requireNonNull(id);
        this.id = id;
    }

    public PartialNodeId(final UUID id) {
        requireNonNull(id);
        this.id = id;
    }

    public PartialNodeId(final ByteString id) {
        requireNonNull(id);
        this.id = id;
    }

    public NodeId toNodeId(final int namespaceIndex) {
        if (this.id instanceof String) {
            return new NodeId(namespaceIndex, (String)this.id);
        } else if (this.id instanceof UInteger) {
            return new NodeId(ushort(namespaceIndex), (UInteger)this.id);
        } else if (this.id instanceof ByteString) {
            return new NodeId(namespaceIndex, (ByteString)this.id);
        } else if (this.id instanceof UUID) {
            return new NodeId(namespaceIndex, (UUID)this.id);
        }
        throw new IllegalStateException("Invalid id type: " + this.id);
    }

    public NodeId toNodeId(final UShort namespaceIndex) {
        if (this.id instanceof String) {
            return new NodeId(namespaceIndex, (String)this.id);
        } else if (this.id instanceof UInteger) {
            return new NodeId(namespaceIndex, (UInteger)this.id);
        } else if (this.id instanceof ByteString) {
            return new NodeId(namespaceIndex, (ByteString)this.id);
        } else if (this.id instanceof UUID) {
            return new NodeId(namespaceIndex, (UUID)this.id);
        }
        throw new IllegalStateException("Invalid id type: " + this.id);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("type", this.type).add("id", this.id).toString();
    }

    public Serializable getValue() {
        return this.id;
    }

    public static PartialNodeId fromExpandedNodeId(final ExpandedNodeId node) {
        if (node == null) {
            return null;
        }

        final Object value = node.getIdentifier();

        if (value instanceof String) {
            return new PartialNodeId((String)value);
        } else if (value instanceof UInteger) {
            return new PartialNodeId((UInteger)value);
        } else if (value instanceof UUID) {
            return new PartialNodeId((UUID)value);
        } else if (value instanceof ByteString) {
            return new PartialNodeId((ByteString)value);
        }

        throw new IllegalStateException(String.format("Unknown node id type: " + value));
    }
}
