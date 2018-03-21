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

package org.apache.camel.component.milo.call;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static java.util.stream.Collectors.toList;

import org.eclipse.milo.opcua.sdk.core.Reference;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.api.AccessContext;
import org.eclipse.milo.opcua.sdk.server.api.DataItem;
import org.eclipse.milo.opcua.sdk.server.api.MethodInvocationHandler;
import org.eclipse.milo.opcua.sdk.server.api.MonitoredItem;
import org.eclipse.milo.opcua.sdk.server.api.Namespace;
import org.eclipse.milo.opcua.sdk.server.api.ServerNodeMap;
import org.eclipse.milo.opcua.sdk.server.model.nodes.objects.FolderNode;
import org.eclipse.milo.opcua.sdk.server.nodes.AttributeContext;
import org.eclipse.milo.opcua.sdk.server.nodes.ServerNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaFolderNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaMethodNode;
import org.eclipse.milo.opcua.sdk.server.util.SubscriptionModel;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.structured.ReadValueId;
import org.eclipse.milo.opcua.stack.core.types.structured.WriteValue;

public class MockNamespace implements Namespace {

    public static final int FOLDER_ID = 1;

    public static final String URI = "urn:mock:namespace";

    private final UShort index;

    private final ServerNodeMap nodeMap;
    private final SubscriptionModel subscriptionModel;

    public MockNamespace(final UShort index, final OpcUaServer server, List<UaMethodNode> methods) {
        this.index = index;
        this.nodeMap = server.getNodeMap();
        this.subscriptionModel = new SubscriptionModel(server, this);

        registerItems(methods);
    }

    private void registerItems(List<UaMethodNode> methods) {

        // create a folder

        final UaFolderNode folder = new UaFolderNode(this.nodeMap, new NodeId(this.index, FOLDER_ID), new QualifiedName(this.index, "FooBarFolder"),
                                                     LocalizedText.english("Foo Bar Folder"));

        // add our folder to the objects folder

        this.nodeMap.getNode(Identifiers.ObjectsFolder).ifPresent(node -> {
            ((FolderNode)node).addComponent(folder);
        });

        // add method calls

        methods.forEach(folder::addComponent);
    }

    // default method implementations follow

    @Override
    public void read(final ReadContext context, final Double maxAge, final TimestampsToReturn timestamps, final List<ReadValueId> readValueIds) {

        final List<DataValue> results = new ArrayList<>(readValueIds.size());

        for (final ReadValueId id : readValueIds) {
            final ServerNode node = this.nodeMap.get(id.getNodeId());

            final DataValue value = node != null ? node.readAttribute(new AttributeContext(context), id.getAttributeId()) : new DataValue(StatusCodes.Bad_NodeIdUnknown);

            results.add(value);
        }

        // report back with result

        context.complete(results);
    }

    @Override
    public void write(final WriteContext context, final List<WriteValue> writeValues) {

        final List<StatusCode> results = writeValues.stream().map(value -> {
            if (this.nodeMap.containsKey(value.getNodeId())) {
                return new StatusCode(StatusCodes.Bad_NotWritable);
            } else {
                return new StatusCode(StatusCodes.Bad_NodeIdUnknown);
            }
        }).collect(toList());

        // report back with result

        context.complete(results);
    }

    @Override
    public CompletableFuture<List<Reference>> browse(final AccessContext context, final NodeId nodeId) {
        final ServerNode node = this.nodeMap.get(nodeId);

        if (node != null) {
            return CompletableFuture.completedFuture(node.getReferences());
        } else {
            final CompletableFuture<List<Reference>> f = new CompletableFuture<>();
            f.completeExceptionally(new UaException(StatusCodes.Bad_NodeIdUnknown));
            return f;
        }
    }

    @Override
    public Optional<MethodInvocationHandler> getInvocationHandler(final NodeId methodId) {
        return Optional.ofNullable(this.nodeMap.get(methodId)).filter(n -> n instanceof UaMethodNode).flatMap(n -> {
            final UaMethodNode m = (UaMethodNode)n;
            return m.getInvocationHandler();
        });
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

    @Override
    public UShort getNamespaceIndex() {
        return this.index;
    }

    @Override
    public String getNamespaceUri() {
        return URI;
    }
}
