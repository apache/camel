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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.milo.opcua.sdk.core.Reference;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.api.DataItem;
import org.eclipse.milo.opcua.sdk.server.api.ManagedNamespace;
import org.eclipse.milo.opcua.sdk.server.api.MonitoredItem;
import org.eclipse.milo.opcua.sdk.server.nodes.UaFolderNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaObjectNode;
import org.eclipse.milo.opcua.sdk.server.util.SubscriptionModel;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;

public class CamelNamespace extends ManagedNamespace {

    private final SubscriptionModel subscriptionModel;

    private UaObjectNode itemsObject;
    private UaFolderNode folder;


    private final Map<String, CamelServerItem> itemMap = new HashMap<>();

    public CamelNamespace(final String namespaceUri, final OpcUaServer server) {
        super(server, namespaceUri);

        this.subscriptionModel = new SubscriptionModel(server, this);
    }

    @Override
    protected void onStartup() {
        super.onStartup();
        // create structure

        final NodeId nodeId = newNodeId("camel");
        final QualifiedName name = newQualifiedName("camel");
        final LocalizedText displayName = LocalizedText.english("Camel");

        this.folder = new UaFolderNode(getNodeContext(), nodeId, name, displayName);
        getNodeManager().addNode(this.folder);

        final NodeId nodeId2 = newNodeId("items");
        final QualifiedName name2 = newQualifiedName("items");
        final LocalizedText displayName2 = LocalizedText.english("Items");

        this.itemsObject = UaObjectNode.builder(getNodeContext())
                .setNodeId(nodeId2)
                .setBrowseName(name2)
                .setDisplayName(displayName2)
                .setTypeDefinition(Identifiers.FolderType)
                .build();
        this.folder.addComponent(this.itemsObject);
        this.itemsObject.addComponent(this.folder);
        this.getNodeManager().addNode(this.itemsObject);


        // register reference to structure

        folder.addReference(new Reference(
                folder.getNodeId(),
                Identifiers.Organizes,
                Identifiers.ObjectsFolder.expanded(),
                false
        ));

        itemsObject.addReference(new Reference(
                nodeId,
                Identifiers.HasComponent,
                Identifiers.ObjectNode.expanded(),
                Reference.Direction.INVERSE
        ));
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
                item = new CamelServerItem(itemId, getNodeContext(), getNamespaceIndex(), this.itemsObject);
                this.itemMap.put(itemId, item);
            }
            return item;
        }
    }

}
