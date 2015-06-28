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
package org.apache.camel.component.jclouds;

import java.util.Set;
import com.google.common.base.Predicate;
import org.apache.camel.CamelException;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.RunNodesException;
import org.jclouds.compute.domain.ComputeMetadata;
import org.jclouds.compute.domain.ExecResponse;
import org.jclouds.compute.domain.Hardware;
import org.jclouds.compute.domain.Image;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.domain.TemplateBuilder;
import org.jclouds.compute.domain.internal.NodeMetadataImpl;
import org.jclouds.compute.options.RunScriptOptions;
import org.jclouds.domain.LoginCredentials;

public class JcloudsComputeProducer extends JcloudsProducer {

    private final ComputeService computeService;

    public JcloudsComputeProducer(JcloudsEndpoint endpoint, ComputeService computeService) {
        super(endpoint);
        this.computeService = computeService;
    }

    @Override
    public JcloudsComputeEndpoint getEndpoint() {
        return (JcloudsComputeEndpoint)super.getEndpoint();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        String operation = getOperation(exchange);

        if (operation == null) {
            throw new CamelExchangeException("Operation must be specified in the endpoint URI or as a property on the exchange.", exchange);
        }

        if (JcloudsConstants.LIST_NODES.equals(operation)) {
            listNodes(exchange);
        } else if (JcloudsConstants.LIST_IMAGES.equals(operation)) {
            listImages(exchange);
        } else if (JcloudsConstants.LIST_HARDWARE.equals(operation)) {
            listHardware(exchange);
        } else if (JcloudsConstants.RUN_SCRIPT.equals(operation)) {
            runScriptOnNode(exchange);
        } else if (JcloudsConstants.CREATE_NODE.equals(operation)) {
            createNode(exchange);
        } else if (JcloudsConstants.DESTROY_NODE.equals(operation)) {
            destroyNode(exchange);
        } else if (JcloudsConstants.REBOOT_NODE.equals(operation)) {
            destroyNode(exchange);
        }
    }

    /**
     * Create a node with the specified group.
     */
    protected void createNode(Exchange exchange) throws CamelException {
        String group = getGroup(exchange);
        String imageId = getImageId(exchange);
        String locationId = getLocationId(exchange);
        String hardwareId = getHardwareId(exchange);

        if (group == null) {
            throw new CamelExchangeException("Group must be specific in the URI or as exchange property for the destroy node operation.", exchange);
        }
        TemplateBuilder builder = computeService.templateBuilder();
        builder.any();

        if (locationId != null) {
            builder.locationId(locationId);
        }
        if (imageId != null) {
            builder.imageId(imageId);
        }
        if (hardwareId != null) {
            builder.hardwareId(hardwareId);
        }

        try {
            Set<? extends NodeMetadata> nodeMetadatas = computeService.createNodesInGroup(group, 1, builder.build());
            exchange.getOut().setBody(nodeMetadatas);
            exchange.getOut().setHeaders(exchange.getIn().getHeaders());
        } catch (RunNodesException e) {
            throw new CamelExchangeException("Error creating jclouds node.", exchange, e);
        }
    }

    /**
     * Runs a script on the target node.
     */
    protected void runScriptOnNode(Exchange exchange) throws CamelException {
        String script = exchange.getIn().getBody(String.class);
        String nodeId = getNodeId(exchange);
        String user = getUser(exchange);

        LoginCredentials credentials = null;

        if (user != null) {
            credentials = LoginCredentials.builder().user(user).build();
        }
        ExecResponse execResponse = null;

        if (credentials == null) {
            execResponse = computeService.runScriptOnNode(nodeId, script);
        } else {
            execResponse = computeService.runScriptOnNode(nodeId, script, RunScriptOptions.Builder.overrideLoginCredentials(credentials).runAsRoot(false));
        }

        if (execResponse == null) {
            throw new CamelExchangeException("Failed to receive response for run script operation on node: " + nodeId + " using script: " + script, exchange);
        }

        exchange.setProperty(JcloudsConstants.RUN_SCRIPT_ERROR, execResponse.getError());
        exchange.setProperty(JcloudsConstants.RUN_SCRIPT_EXIT_CODE, execResponse.getExitStatus());
        exchange.getOut().setBody(execResponse.getOutput());
    }

    /**
     * Destroys the node with the specified nodeId.
     */
    protected void destroyNode(Exchange exchange) {
        Predicate<NodeMetadata> predicate = getNodePredicate(exchange);
        computeService.destroyNodesMatching(predicate);
    }

    /**
     * Sets the metadata of the available nodes to the out message.
     */
    protected void listNodes(Exchange exchange) {
        Predicate<ComputeMetadata> predicate = getComputePredicate(exchange);
        Set<? extends ComputeMetadata> computeMetadatas = computeService.listNodesDetailsMatching(predicate);
        exchange.getOut().setBody(computeMetadatas);
    }

    /**
     * Sets the available images to the out message.
     */
    protected void listImages(Exchange exchange) {
        Set<? extends Image> images = computeService.listImages();
        exchange.getOut().setBody(images);
    }

    /**
     * Sets the available hardware profiles to the out message.
     */
    protected void listHardware(Exchange exchange) {
        Set<? extends Hardware> hardwareProfiles = computeService.listHardwareProfiles();
        exchange.getOut().setBody(hardwareProfiles);
    }
    
    /**
     * Reboot the node with the specified nodeId.
     */
    protected void rebootNode(Exchange exchange) {
        Predicate<NodeMetadata> predicate = getNodePredicate(exchange);
        computeService.rebootNodesMatching(predicate);
    }


    /**
     * Returns the required {@ComputeMetadata} {@link Predicate} for the Exhcnage.
     * The predicate can be used for filtering.
     */
    public Predicate<ComputeMetadata> getComputePredicate(final Exchange exchange) {
        final String nodeId = getNodeId(exchange);

        Predicate<ComputeMetadata> predicate = new Predicate<ComputeMetadata>() {
            public boolean apply(ComputeMetadata metadata) {
                if (nodeId != null && !nodeId.equals(metadata.getId())) {
                    return false;
                }

                //If NodeMetadata also delegate to Node predicate.
                if (metadata instanceof NodeMetadataImpl) {
                    Predicate<NodeMetadata> nodeMetadataPredicate = getNodePredicate(exchange);
                    if (!nodeMetadataPredicate.apply((NodeMetadataImpl) metadata)) {
                        return false;
                    }
                }
                return true;
            }
        };

        return predicate;
    }

    /**
     * Returns the required {@ComputeMetadata} {@link Predicate} for the Exhcnage.
     * The predicate can be used for filtering.
     */
    public Predicate<NodeMetadata> getNodePredicate(Exchange exchange) {
        final String nodeId = getNodeId(exchange);
        final String imageId = getImageId(exchange);
        final String group = getGroup(exchange);
        final NodeMetadata.Status queryState = getNodeState(exchange);

        Predicate<NodeMetadata> predicate = new Predicate<NodeMetadata>() {
            public boolean apply(NodeMetadata metadata) {
                if (nodeId != null && !nodeId.equals(metadata.getId())) {
                    return false;
                }
                if (imageId != null && !imageId.equals(metadata.getImageId())) {
                    return false;
                }
                if (queryState != null && !queryState.equals(metadata.getStatus())) {
                    return false;
                }
                if (group != null && !group.equals(metadata.getGroup())) {
                    return false;
                }
                return true;
            }
        };
        return predicate;
    }

    /**
     * Retrieves the operation from the URI or from the exchange headers. The header will take precedence over the URI.
     */
    public String getOperation(Exchange exchange) {
        String operation = getEndpoint().getOperation();

        if (exchange.getIn().getHeader(JcloudsConstants.OPERATION) != null) {
            operation = (String) exchange.getIn().getHeader(JcloudsConstants.OPERATION);
        }
        return operation;
    }

    /**
     * Retrieves the node state from the URI or from the exchange headers. The header will take precedence over the URI.
     */
    public NodeMetadata.Status getNodeState(Exchange exchange) {
        NodeMetadata.Status nodeState = null;
        String state = getEndpoint().getNodeState();
        if (state != null) {
            nodeState =  NodeMetadata.Status.valueOf(state);
        }

        if (exchange.getIn().getHeader(JcloudsConstants.NODE_STATE) != null) {
            Object stateHeader = exchange.getIn().getHeader(JcloudsConstants.NODE_STATE);
            if (stateHeader == null) {
                nodeState = null;
            } else if (stateHeader instanceof  NodeMetadata.Status) {
                nodeState = (NodeMetadata.Status) stateHeader;
            } else {
                nodeState =  NodeMetadata.Status.valueOf(String.valueOf(stateHeader));
            }
        }
        return nodeState;
    }


    /**
     * Retrieves the image id from the URI or from the exchange properties. The property will take precedence over the URI.
     */
    protected String getImageId(Exchange exchange) {
        String imageId = getEndpoint().getImageId();

        if (exchange.getIn().getHeader(JcloudsConstants.IMAGE_ID) != null) {
            imageId = (String) exchange.getIn().getHeader(JcloudsConstants.IMAGE_ID);
        }
        return imageId;
    }

    /**
     * Retrieves the hardware id from the URI or from the exchange headers. The header will take precedence over the URI.
     */
    protected String getHardwareId(Exchange exchange) {
        String hardwareId = getEndpoint().getHardwareId();

        if (exchange.getIn().getHeader(JcloudsConstants.HARDWARE_ID) != null) {
            hardwareId = (String) exchange.getIn().getHeader(JcloudsConstants.HARDWARE_ID);
        }
        return hardwareId;
    }

    /**
     * Retrieves the location id from the URI or from the exchange headers. The header will take precedence over the URI.
     */
    protected String getLocationId(Exchange exchange) {
        String locationId = getEndpoint().getLocationId();

        if (exchange.getIn().getHeader(JcloudsConstants.LOCATION_ID) != null) {
            locationId = (String) exchange.getIn().getHeader(JcloudsConstants.LOCATION_ID);
        }
        return locationId;
    }

    /**
     * Retrieves the node id from the URI or from the exchange headers. The header will take precedence over the URI.
     */
    protected String getNodeId(Exchange exchange) {
        String nodeId = getEndpoint().getNodeId();

        if (exchange.getIn().getHeader(JcloudsConstants.NODE_ID) != null) {
            nodeId = (String) exchange.getIn().getHeader(JcloudsConstants.NODE_ID);
        }
        return nodeId;
    }

    /**
     * Retrieves the group from the URI or from the exchange headers. The header will take precedence over the URI.
     */
    protected String getGroup(Exchange exchange) {
        String group = getEndpoint().getGroup();

        if (exchange.getIn().getHeader(JcloudsConstants.GROUP) != null) {
            group = (String) exchange.getIn().getHeader(JcloudsConstants.GROUP);
        }
        return group;
    }

    /**
     * Retrieves the user from the URI or from the exchange headers. The header will take precedence over the URI.
     */
    protected String getUser(Exchange exchange) {
        String user = getEndpoint().getUser();

        if (exchange.getIn().getHeader(JcloudsConstants.USER) != null) {
            user = (String) exchange.getIn().getHeader(JcloudsConstants.USER);
        }
        return user;
    }

}
