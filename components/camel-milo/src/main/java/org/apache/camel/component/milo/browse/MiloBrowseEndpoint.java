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

import java.util.Objects;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.milo.MiloConstants;
import org.apache.camel.component.milo.client.MiloClientConfiguration;
import org.apache.camel.component.milo.client.MiloClientConnection;
import org.apache.camel.component.milo.client.MiloClientConnectionManager;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.enumerated.BrowseDirection;
import org.eclipse.milo.opcua.stack.core.types.enumerated.NodeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.milo.MiloConstants.SCHEME_BROWSE;

/**
 * Connect to OPC UA servers using the binary protocol for browsing the node tree.
 */
@UriEndpoint(firstVersion = "3.15.0", scheme = SCHEME_BROWSE, syntax = "milo-browse:endpointUri", title = "OPC UA Browser",
             category = { Category.IOT }, producerOnly = true, headersClass = MiloConstants.class)
public class MiloBrowseEndpoint extends DefaultEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(MiloBrowseEndpoint.class);

    private final MiloClientConnectionManager connectionManager;

    /**
     * The OPC UA server endpoint
     */
    @UriPath
    @Metadata(required = true)
    private final String endpointUri;

    /**
     * The node definition (see Node ID)
     */
    @UriParam(defaultValue = "ns=0;id=84", defaultValueNote = "Root folder as per OPC-UA spec")
    private String node = Identifiers.RootFolder.toParseableString();

    /**
     * The direction to browse (forward, inverse, ...)
     */
    @UriParam(defaultValue = "Forward",
              enums = "Forward,Inverse,Both",
              defaultValueNote = "The direction to browse; see org.eclipse.milo.opcua.stack.core.types.enumerated.BrowseDirection")
    private BrowseDirection direction = BrowseDirection.Forward;

    /**
     * Whether to include sub-types for browsing; only applicable for non-recursive browsing
     */
    @UriParam(defaultValue = "true")
    private boolean includeSubTypes = true;

    /**
     * The mask indicating the node classes of interest in browsing
     */
    @UriParam(defaultValue = "Variable,Object,DataType",
              defaultValueNote = "Comma-separated node class list; see org.eclipse.milo.opcua.stack.core.types.enumerated.NodeClass")
    private String nodeClasses = NodeClass.Variable + "," + NodeClass.Object + "," + NodeClass.DataType;

    private int nodeClassMask = NodeClass.Variable.getValue() | NodeClass.Object.getValue() | NodeClass.DataType.getValue();

    /**
     * Whether to browse recursively into sub-types, ignores includeSubTypes setting as it's implied to be set to true
     */
    @UriParam(defaultValue = "false",
              defaultValueNote = "Whether to recursively browse sub-types: true|false")
    private boolean recursive;

    /**
     * When browsing recursively into sub-types, what's the maximum search depth for diving into the tree
     */
    @UriParam(defaultValue = "3", defaultValueNote = "Maximum depth for browsing recursively (only if recursive = true)")
    private int depth = 3;

    /**
     * Filter out node ids to limit browsing
     */
    @UriParam(defaultValue = "None", defaultValueNote = "Regular filter expression matching node ids")
    private String filter;

    /**
     * The maximum number node ids requested per server call
     */
    @UriParam(defaultValue = "10",
              defaultValueNote = "Maximum number of node ids requested per browse call (applies to browsing sub-types only; only if recursive = true)")
    private int maxNodeIdsPerRequest = 10;

    /**
     * The client configuration
     */
    @UriParam
    private MiloClientConfiguration configuration;

    public MiloBrowseEndpoint(final String uri, final MiloBrowseComponent component, final String endpointUri,
                              final MiloClientConnectionManager connectionManager) {
        super(uri, component);

        Objects.requireNonNull(component);
        Objects.requireNonNull(endpointUri);
        Objects.requireNonNull(connectionManager);

        this.endpointUri = endpointUri;
        this.connectionManager = connectionManager;
    }

    public void setConfiguration(MiloClientConfiguration configuration) {
        this.configuration = configuration;
    }

    public MiloClientConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new MiloBrowseProducer(this);
    }

    @Override
    public Consumer createConsumer(final Processor processor) throws Exception {
        throw new UnsupportedOperationException(MiloBrowseEndpoint.class.getName() + " doesn't support a consumer");
    }

    public MiloClientConnection createConnection() {
        return this.connectionManager.createConnection(configuration, null);
    }

    public void releaseConnection(MiloClientConnection connection) {
        this.connectionManager.releaseConnection(connection);
    }

    public void setNode(final String node) {
        this.node = node;
    }

    public String getNode() {
        return node;
    }

    NodeId getNodeId() {
        return getNodeId(this.node);
    }

    NodeId getNodeId(String nodeId) {
        if (nodeId != null) {
            return NodeId.parse(nodeId);
        } else {
            return null;
        }
    }

    public BrowseDirection getDirection() {
        return direction;
    }

    public boolean isIncludeSubTypes() {
        return includeSubTypes;
    }

    public void setIncludeSubTypes(boolean includeSubTypes) {
        this.includeSubTypes = includeSubTypes;
    }

    public String getNodeClasses() {
        return nodeClasses;
    }

    public void setNodeClasses(String nodeClasses) {
        this.nodeClasses = nodeClasses;
        final String[] nodeClassArray = nodeClasses.split(",");
        int mask = 0;
        try {
            for (String nodeClass : nodeClassArray) {
                mask |= NodeClass.valueOf(nodeClass).getValue();
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid node class specified: " + nodeClasses, e);
        }
        LOG.debug("Node class list conversion {} -> {}", nodeClasses, mask);
        nodeClassMask = mask;
    }

    public int getNodeClassMask() {
        return nodeClassMask;
    }

    public void setDirection(BrowseDirection direction) {
        this.direction = direction;
    }

    public boolean isRecursive() {
        return recursive;
    }

    public void setRecursive(boolean recursive) {
        this.recursive = recursive;
    }

    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }

    public String getFilter() {
        return filter;
    }

    public void setFilter(String filter) {
        this.filter = filter;
    }

    public int getMaxNodeIdsPerRequest() {
        return maxNodeIdsPerRequest;
    }

    public void setMaxNodeIdsPerRequest(int maxNodeIdsPerRequest) {
        this.maxNodeIdsPerRequest = maxNodeIdsPerRequest;
    }
}
