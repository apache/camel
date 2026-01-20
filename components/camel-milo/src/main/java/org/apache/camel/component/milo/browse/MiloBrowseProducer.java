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
package org.apache.camel.component.milo.browse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.milo.client.MiloClientConnection;
import org.apache.camel.support.DefaultAsyncProducer;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExpandedNodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.structured.BrowseResult;
import org.eclipse.milo.opcua.stack.core.types.structured.ReferenceDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.milo.MiloConstants.HEADER_NODE_IDS;

public class MiloBrowseProducer extends DefaultAsyncProducer {

    private static final Logger LOG = LoggerFactory.getLogger(MiloBrowseProducer.class);

    private MiloClientConnection connection;

    public MiloBrowseProducer(final MiloBrowseEndpoint endpoint) {

        super(endpoint);
    }

    @Override
    public MiloBrowseEndpoint getEndpoint() {
        return (MiloBrowseEndpoint) super.getEndpoint();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        this.connection = getEndpoint().createConnection();
    }

    @Override
    protected void doStop() throws Exception {
        if (null != this.connection) {
            getEndpoint().releaseConnection(connection);
        }
        super.doStop();
    }

    private ExpandedNodeId tryParse(String nodeString) {
        final Optional<NodeId> nodeId = NodeId.parseSafe(nodeString);
        return nodeId.map(NodeId::expanded).orElseGet(() -> ExpandedNodeId.parse(nodeString));
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback async) {

        final Message message = exchange.getMessage();
        final List<ExpandedNodeId> expandedNodeIds = new ArrayList<>();

        if (message.getHeaders().containsKey(HEADER_NODE_IDS)) {

            final List<?> nodes
                    = message.getHeader(HEADER_NODE_IDS, Collections.singletonList(getEndpoint().getNode()), List.class);
            message.removeHeader(HEADER_NODE_IDS);
            if (null == nodes) {

                LOG.warn("Browse nodes: No node ids specified");
                async.done(true);
                return true;
            }

            for (final Object node : nodes) {
                expandedNodeIds.add(tryParse(node.toString()));
            }
        } else {

            expandedNodeIds.add(tryParse(this.getEndpoint().getNode()));
        }

        final MiloBrowseEndpoint endpoint = this.getEndpoint();
        final int depth = endpoint.isRecursive() ? endpoint.getDepth() : -1;
        final boolean subTypes = endpoint.isIncludeSubTypes() || endpoint.isRecursive();

        @SuppressWarnings("unused")
        final CompletableFuture<?> future = this.connection
                .browse(expandedNodeIds, endpoint.getDirection(), endpoint.getNodeClassMask(), depth, endpoint.getFilter(),
                        subTypes, endpoint.getMaxNodeIdsPerRequest())

                .thenApply(browseResults -> {

                    final List<String> expandedNodes = browseResults.values().stream()
                            .map(BrowseResult::getReferences)
                            .flatMap(Stream::of)
                            .map(ReferenceDescription::getNodeId)
                            .map(ExpandedNodeId::toParseableString)
                            .collect(Collectors.toList());

                    // For convenience, to be used with the milo-client producer
                    exchange.getMessage().setHeader(HEADER_NODE_IDS, expandedNodes);

                    exchange.getMessage().setBody(browseResults);

                    return browseResults;
                })

                .whenComplete((actual, error) -> {

                    final String expandedNodeIdsString = expandedNodeIds.stream()
                            .map(ExpandedNodeId::toParseableString)
                            .collect(Collectors.joining(", "));

                    if (actual != null) {

                        LOG.debug("Browse node(s) {} -> {} result(s)", expandedNodeIdsString, actual.size());

                    } else {

                        LOG.error("Browse node(s) {} -> failed: {}", expandedNodeIdsString, error.getMessage());
                        exchange.setException(error);
                    }

                    async.done(false);
                });

        return false;
    }

}
