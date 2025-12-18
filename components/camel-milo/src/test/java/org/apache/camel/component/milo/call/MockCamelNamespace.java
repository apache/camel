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
package org.apache.camel.component.milo.call;

import java.util.List;
import java.util.function.Function;

import org.apache.camel.component.milo.client.MiloClientConsumer;
import org.eclipse.milo.opcua.sdk.core.Reference;
import org.eclipse.milo.opcua.sdk.server.Lifecycle;
import org.eclipse.milo.opcua.sdk.server.ManagedNamespaceWithLifecycle;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.items.DataItem;
import org.eclipse.milo.opcua.sdk.server.items.MonitoredItem;
import org.eclipse.milo.opcua.sdk.server.methods.AbstractMethodInvocationHandler;
import org.eclipse.milo.opcua.sdk.server.nodes.UaFolderNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaMethodNode;
import org.eclipse.milo.opcua.sdk.server.util.SubscriptionModel;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MockCamelNamespace extends ManagedNamespaceWithLifecycle {

    public static final String URI = "urn:org:apache:camel:mock";
    public static final int FOLDER_ID = 1;
    public static final int CALL_ID = 2;

    private static final Logger LOG = LoggerFactory.getLogger(MiloClientConsumer.class);

    private final SubscriptionModel subscriptionModel;

    private final Function<UaMethodNode, AbstractMethodInvocationHandler> callMethodCreator;

    private UaFolderNode folder;

    public MockCamelNamespace(final OpcUaServer server,
                              Function<UaMethodNode, AbstractMethodInvocationHandler> callMethodCreator) {
        super(server, URI);

        this.subscriptionModel = new SubscriptionModel(server, this);
        this.callMethodCreator = callMethodCreator;

        super.getLifecycleManager().addLifecycle(new Lifecycle() {

            @Override
            public void startup() {
                LOG.trace("CamelNamespace startup");
                createNodes();
            }

            @Override
            public void shutdown() {
                LOG.trace("CamelNamespace shutdown");
            }
        });
    }

    private void createNodes() {

        // create structure

        final NodeId nodeId = newNodeId(FOLDER_ID);
        final QualifiedName name = newQualifiedName("camel");
        final LocalizedText displayName = LocalizedText.english("Camel");

        this.folder = new UaFolderNode(getNodeContext(), nodeId, name, displayName);
        getNodeManager().addNode(this.folder);

        // register reference to structure

        folder.addReference(new Reference(
                folder.getNodeId(),
                Identifiers.Organizes,
                Identifiers.ObjectsFolder.expanded(),
                false));

        addCallMethod(folder);
    }

    private void addCallMethod(UaFolderNode folderNode) {
        UaMethodNode methodNode = UaMethodNode.builder(getNodeContext())
                .setNodeId(new NodeId(getNamespaceIndex(), CALL_ID))
                .setBrowseName(newQualifiedName("call"))
                .setDisplayName(new LocalizedText(null, "call"))
                .setDescription(
                        LocalizedText.english("Returns the \"out-\"+entry parameter"))
                .build();

        AbstractMethodInvocationHandler callMethod = callMethodCreator.apply(methodNode);
        methodNode.setInputArguments(callMethod.getInputArguments());
        methodNode.setOutputArguments(callMethod.getOutputArguments());
        methodNode.setInvocationHandler(callMethod);

        getNodeManager().addNode(methodNode);

        methodNode.addReference(new Reference(
                methodNode.getNodeId(),
                Identifiers.HasComponent,
                folderNode.getNodeId().expanded(),
                false));

        methodNode.addReference(new Reference(
                methodNode.getNodeId(),
                Identifiers.HasComponent,
                folderNode.getNodeId().expanded(),
                false));
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
}
