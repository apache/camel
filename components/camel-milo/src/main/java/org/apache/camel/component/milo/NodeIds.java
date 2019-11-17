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
package org.apache.camel.component.milo;

import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExpandedNodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;

import static java.util.Objects.requireNonNull;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ushort;

/**
 * Helper class to work with node IDs
 */
public final class NodeIds {
    private NodeIds() {
    }

    public static NodeId toNodeId(final UShort namespaceIndex, final ExpandedNodeId nodeId) {
        requireNonNull(namespaceIndex);

        final Object id = nodeId.getIdentifier();

        if (id instanceof String) {
            return new NodeId(namespaceIndex, (String)id);
        } else if (id instanceof UInteger) {
            return new NodeId(namespaceIndex, (UInteger)id);
        } else if (id instanceof ByteString) {
            return new NodeId(namespaceIndex, (ByteString)id);
        } else if (id instanceof UUID) {
            return new NodeId(namespaceIndex, (UUID)id);
        }

        throw new IllegalStateException("Invalid id type: " + id);
    }

    public static NodeId toNodeId(final int namespaceIndex, final ExpandedNodeId nodeId) {
        return toNodeId(ushort(namespaceIndex), nodeId);
    }

    /**
     * Create an attribute value for the "node" attribute
     * 
     * @param namespace the namespace to use
     * @param node the node ID
     * @return the value ready to append to a "node" URI attribute
     */
    public static String nodeValue(final String namespace, final String node) {
        Objects.requireNonNull(namespace);
        Objects.requireNonNull(node);

        final StringBuilder builder = new StringBuilder("RAW(");
        appendNamespace(builder, namespace);
        builder.append(';');
        appendNodeId(builder, node);
        builder.append(')');
        return builder.toString();
    }

    /**
     * Create an attribute value for the "node" attribute
     * 
     * @param namespace the namespace to use
     * @param node the node ID
     * @return the value ready to append to a "node" URI attribute
     */
    public static String nodeValue(final String namespace, final int node) {
        Objects.requireNonNull(namespace);

        final StringBuilder builder = new StringBuilder("RAW(");
        appendNamespace(builder, namespace);
        builder.append(';');
        appendNodeId(builder, node);
        builder.append(')');
        return builder.toString();
    }

    /**
     * Create an attribute value for the "node" attribute
     * 
     * @param namespace the namespace to use
     * @param node the node ID
     * @return the value ready to append to a "node" URI attribute
     */
    public static String nodeValue(final String namespace, final UUID node) {
        Objects.requireNonNull(namespace);
        Objects.requireNonNull(node);

        final StringBuilder builder = new StringBuilder("RAW(");
        appendNamespace(builder, namespace);
        builder.append(';');
        appendNodeId(builder, node);
        builder.append(')');
        return builder.toString();
    }

    /**
     * Create an attribute value for the "node" attribute
     * 
     * @param namespace the namespace to use
     * @param node the node ID
     * @return the value ready to append to a "node" URI attribute
     */
    public static String nodeValue(final String namespace, final ByteString node) {
        Objects.requireNonNull(namespace);
        Objects.requireNonNull(node);

        final StringBuilder builder = new StringBuilder("RAW(");
        appendNamespace(builder, namespace);
        builder.append(';');
        appendNodeId(builder, node);
        builder.append(')');
        return builder.toString();
    }

    /**
     * Create an attribute value for the "node" attribute
     * 
     * @param namespace the namespace to use
     * @param node the node ID
     * @return the value ready to append to a "node" URI attribute
     */
    public static String nodeValue(final int namespace, final String node) {
        Objects.requireNonNull(namespace);
        Objects.requireNonNull(node);

        final StringBuilder builder = new StringBuilder("RAW(");
        appendNamespace(builder, namespace);
        builder.append(';');
        appendNodeId(builder, node);
        builder.append(')');
        return builder.toString();
    }

    /**
     * Create an attribute value for the "node" attribute
     * 
     * @param namespace the namespace to use
     * @param node the node ID
     * @return the value ready to append to a "node" URI attribute
     */
    public static String nodeValue(final int namespace, final int node) {
        Objects.requireNonNull(namespace);

        final StringBuilder builder = new StringBuilder("RAW(");
        appendNamespace(builder, namespace);
        builder.append(';');
        appendNodeId(builder, node);
        builder.append(')');
        return builder.toString();
    }

    /**
     * Create an attribute value for the "node" attribute
     * 
     * @param namespace the namespace to use
     * @param node the node ID
     * @return the value ready to append to a "node" URI attribute
     */
    public static String nodeValue(final int namespace, final UUID node) {
        Objects.requireNonNull(namespace);
        Objects.requireNonNull(node);

        final StringBuilder builder = new StringBuilder("RAW(");
        appendNamespace(builder, namespace);
        builder.append(';');
        appendNodeId(builder, node);
        builder.append(')');
        return builder.toString();
    }

    /**
     * Create an attribute value for the "node" attribute
     * 
     * @param namespace the namespace to use
     * @param node the node ID
     * @return the value ready to append to a "node" URI attribute
     */
    public static String nodeValue(final int namespace, final ByteString node) {
        Objects.requireNonNull(namespace);
        Objects.requireNonNull(node);

        final StringBuilder builder = new StringBuilder("RAW(");
        appendNamespace(builder, namespace);
        builder.append(';');
        appendNodeId(builder, node);
        builder.append(')');
        return builder.toString();
    }

    private static void appendNamespace(final StringBuilder builder, final String namespace) {
        builder.append("nsu=").append(namespace);
    }

    private static void appendNamespace(final StringBuilder builder, final int namespace) {
        builder.append("ns=").append(Integer.toUnsignedString(namespace));
    }

    private static void appendNodeId(final StringBuilder builder, final String nodeId) {
        builder.append("s=").append(nodeId);
    }

    private static void appendNodeId(final StringBuilder builder, final int nodeId) {
        builder.append("i=").append(Integer.toUnsignedString(nodeId));
    }

    private static void appendNodeId(final StringBuilder builder, final UUID nodeId) {
        builder.append("g=").append(nodeId);
    }

    private static void appendNodeId(final StringBuilder builder, final ByteString nodeId) {
        builder.append("b=").append(Base64.getEncoder().encodeToString(nodeId.bytes()));
    }

}
