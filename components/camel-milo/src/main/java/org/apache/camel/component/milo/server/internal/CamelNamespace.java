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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.google.common.collect.Lists;

import org.apache.camel.component.milo.client.MiloClientConsumer;
import org.eclipse.milo.opcua.sdk.core.Reference;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.api.AccessContext;
import org.eclipse.milo.opcua.sdk.server.api.DataItem;
import org.eclipse.milo.opcua.sdk.server.api.MonitoredItem;
import org.eclipse.milo.opcua.sdk.server.api.Namespace;
import org.eclipse.milo.opcua.sdk.server.api.ServerNodeMap;
import org.eclipse.milo.opcua.sdk.server.nodes.AttributeContext;
import org.eclipse.milo.opcua.sdk.server.nodes.ServerNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaFolderNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaObjectNode;
import org.eclipse.milo.opcua.sdk.server.util.SubscriptionModel;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;
import org.eclipse.milo.opcua.stack.core.types.enumerated.NodeClass;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.structured.ReadValueId;
import org.eclipse.milo.opcua.stack.core.types.structured.WriteValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CamelNamespace implements Namespace {

    private static final Logger LOG = LoggerFactory.getLogger(MiloClientConsumer.class);

    private final UShort namespaceIndex;

    private final String namespaceUri;

    private final ServerNodeMap nodeManager;
    private final SubscriptionModel subscriptionModel;

    private final UaFolderNode folder;
    private final UaObjectNode itemsObject;

    private final Map<String, CamelServerItem> itemMap = new HashMap<>();

    public CamelNamespace(final UShort namespaceIndex, final String namespaceUri, final OpcUaServer server) {
        this.namespaceIndex = namespaceIndex;
        this.namespaceUri = namespaceUri;

        this.nodeManager = server.getNodeMap();
        this.subscriptionModel = new SubscriptionModel(server, this);

        // create structure

        {
            final NodeId nodeId = new NodeId(namespaceIndex, "camel");
            final QualifiedName name = new QualifiedName(namespaceIndex, "camel");
            final LocalizedText displayName = LocalizedText.english("Camel");

            this.folder = new UaFolderNode(this.nodeManager, nodeId, name, displayName);
            this.nodeManager.addNode(this.folder);
        }

        {
            final NodeId nodeId = new NodeId(namespaceIndex, "items");
            final QualifiedName name = new QualifiedName(namespaceIndex, "items");
            final LocalizedText displayName = LocalizedText.english("Items");
            this.itemsObject = new UaObjectNode(this.nodeManager, nodeId, name, displayName);
            this.folder.addComponent(this.itemsObject);
        }

        // register reference to structure

        try {
            server.getUaNamespace().addReference(Identifiers.ObjectsFolder, Identifiers.Organizes, true, this.folder.getNodeId().expanded(), NodeClass.Object);
        } catch (final UaException e) {
            throw new RuntimeException("Failed to register folder", e);
        }
    }

    @Override
    public UShort getNamespaceIndex() {
        return this.namespaceIndex;
    }

    @Override
    public String getNamespaceUri() {
        return this.namespaceUri;
    }

    @Override
    public CompletableFuture<List<Reference>> browse(final AccessContext context, final NodeId nodeId) {
        final ServerNode node = this.nodeManager.get(nodeId);

        if (node != null) {
            return CompletableFuture.completedFuture(node.getReferences());
        } else {
            final CompletableFuture<List<Reference>> f = new CompletableFuture<>();
            f.completeExceptionally(new UaException(StatusCodes.Bad_NodeIdUnknown));
            return f;
        }
    }

    @Override
    public void read(final ReadContext context, final Double maxAge, final TimestampsToReturn timestamps, final List<ReadValueId> readValueIds) {
        final List<DataValue> results = Lists.newArrayListWithCapacity(readValueIds.size());

        for (final ReadValueId id : readValueIds) {
            final ServerNode node = this.nodeManager.get(id.getNodeId());

            final DataValue value;

            if (node != null) {
                value = node.readAttribute(new AttributeContext(context), id.getAttributeId(), timestamps, id.getIndexRange());
            } else {
                value = new DataValue(StatusCodes.Bad_NodeIdUnknown);
            }

            results.add(value);
        }

        context.complete(results);
    }

    @Override
    public void write(final WriteContext context, final List<WriteValue> writeValues) {
        final List<StatusCode> results = Lists.newArrayListWithCapacity(writeValues.size());

        for (final WriteValue writeValue : writeValues) {
            try {
                final ServerNode node = this.nodeManager.getNode(writeValue.getNodeId()).orElseThrow(() -> new UaException(StatusCodes.Bad_NodeIdUnknown));

                node.writeAttribute(new AttributeContext(context), writeValue.getAttributeId(), writeValue.getValue(), writeValue.getIndexRange());

                if (LOG.isTraceEnabled()) {
                    final Variant variant = writeValue.getValue().getValue();
                    final Object o = variant != null ? variant.getValue() : null;
                    LOG.trace("Wrote value={} to attributeId={} of {}", o, writeValue.getAttributeId(), writeValue.getNodeId());
                }

                results.add(StatusCode.GOOD);
            } catch (final UaException e) {
                results.add(e.getStatusCode());
            }
        }

        context.complete(results);
    }

    @Override
    public void onDataItemsCreated(final List<DataItem> dataItems) {
        this.subscriptionModel.onDataItemsCreated(dataItems);
    }

    @Override
    public void onDataItemsModified(final List<DataItem> dataItems) {
        this.subscriptionModel.onDataItemsModified(dataItems);
    }

    @Override
    public void onDataItemsDeleted(final List<DataItem> dataItems) {
        this.subscriptionModel.onDataItemsDeleted(dataItems);
    }

    @Override
    public void onMonitoringModeChanged(final List<MonitoredItem> monitoredItems) {
        this.subscriptionModel.onMonitoringModeChanged(monitoredItems);
    }

    public CamelServerItem getOrAddItem(final String itemId) {
        synchronized (this) {
            CamelServerItem item = this.itemMap.get(itemId);
            if (item == null) {
                item = new CamelServerItem(itemId, this.nodeManager, this.namespaceIndex, this.itemsObject);
                this.itemMap.put(itemId, item);
            }
            return item;
        }
    }

}
