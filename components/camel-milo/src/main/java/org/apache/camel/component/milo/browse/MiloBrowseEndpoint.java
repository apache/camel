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
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.enumerated.BrowseDirection;
import org.eclipse.milo.opcua.stack.core.types.enumerated.NodeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.milo.MiloConstants.SCHEME_BROWSE;

/**
 * Browse the nodes of an OPC UA server and the references between them.
 */
@UriEndpoint(firstVersion = "3.15.0", scheme = SCHEME_BROWSE, syntax = "milo-browse:endpointUri", title = "OPC UA Browser",
             category = { Category.IOT }, producerOnly = true, headersClass = MiloConstants.class)
@BodyTypes(producerIn = @BodyType(value = Carrier.ANY,
                                  description = "The body is not read: the nodes to browse are the node IDs of the"
                                                + " CamelMiloNodeIds header, or else the node option"),
           producerOut = @BodyType(value = Carrier.MAP,
                                   description = "A Map of the browsed ExpandedNodeId to its Milo BrowseResult; the node IDs"
                                                 + " found are also in the CamelMiloNodeIds header, as a List of Strings for"
                                                 + " the milo-client producer"))
public class MiloBrowseEndpoint extends DefaultEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(MiloBrowseEndpoint.class);

    private final MiloClientConnectionManager connectionManager;

    @UriPath(description = "The endpoint URL of the OPC UA server, for example opc.tcp://localhost:4840/path. It can hold a"
                           + " user name and password in its user info part. The username and password options take"
                           + " precedence over them.")
    @Metadata(required = true)
    private final String endpointUri;

    @UriParam(description = "The node ID of the node to browse from, for example ns=2;s=folder. The CamelMiloNodeIds header"
                            + " replaces this option. The default is the Root folder, i=84.",
              defaultValue = "ns=0;id=84", defaultValueNote = "Root folder as per OPC-UA spec")
    private String node = NodeIds.RootFolder.toParseableString();

    @UriParam(description = "The direction of the references to follow: Forward, Inverse or Both.",
              defaultValue = "Forward",
              enums = "Forward,Inverse,Both",
              defaultValueNote = "The direction to browse; see org.eclipse.milo.opcua.stack.core.types.enumerated.BrowseDirection")
    private BrowseDirection direction = BrowseDirection.Forward;

    @UriParam(defaultValue = "true",
              description = "Whether to follow the references of all subtypes of the References reference type. If false, only"
                            + " references of the abstract References type itself are returned, which are usually none. A"
                            + " recursive browse always includes the subtypes.")
    private boolean includeSubTypes = true;

    @UriParam(description = "A comma-separated list of the node classes of the nodes to return, as names of"
                            + " org.eclipse.milo.opcua.stack.core.types.enumerated.NodeClass: Object, Variable, Method,"
                            + " ObjectType, VariableType, ReferenceType, DataType and View.",
              defaultValue = "Variable,Object,DataType",
              defaultValueNote = "Comma-separated node class list; see org.eclipse.milo.opcua.stack.core.types.enumerated.NodeClass")
    private String nodeClasses = NodeClass.Variable + "," + NodeClass.Object + "," + NodeClass.DataType;

    private int nodeClassMask = NodeClass.Variable.getValue() | NodeClass.Object.getValue() | NodeClass.DataType.getValue();

    @UriParam(description = "Whether to browse the nodes that are found again, down to the level of the depth option. A"
                            + " recursive browse always includes the subtypes of the reference type, whatever"
                            + " includeSubTypes says.",
              defaultValue = "false",
              defaultValueNote = "Whether to recursively browse sub-types: true|false")
    private boolean recursive;

    @UriParam(description = "The number of levels that a recursive browse goes down, where the nodes to browse from are"
                            + " level 1. Only used when recursive is true.",
              defaultValue = "3", defaultValueNote = "Maximum depth for browsing recursively (only if recursive = true)")
    private int depth = 3;

    @UriParam(description = "A regular expression that the node ID of a found node, in its parseable form such as ns=2;s=item,"
                            + " must match to be in the result and to be browsed further. If not set, all nodes are kept.",
              defaultValue = "None", defaultValueNote = "Regular filter expression matching node ids")
    private String filter;

    @UriParam(description = "The maximum number of nodes that one Browse request asks for, when a recursive browse goes down"
                            + " to the next level.",
              defaultValue = "10",
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
        final MiloClientConnection connection = this.connectionManager.createConnection(configuration, null);
        connection.setCamelContext(getCamelContext());
        return connection;
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
