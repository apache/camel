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
import org.apache.camel.CamelException;
import org.apache.camel.Exchange;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.RunNodesException;
import org.jclouds.compute.domain.ComputeMetadata;
import org.jclouds.compute.domain.ExecResponse;
import org.jclouds.compute.domain.Hardware;
import org.jclouds.compute.domain.Image;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.domain.TemplateBuilder;
import org.jclouds.compute.options.RunScriptOptions;
import org.jclouds.domain.Credentials;

public class JcloudsComputeProducer extends JcloudsProducer {

    private ComputeService computeService;

    public JcloudsComputeProducer(JcloudsEndpoint endpoint, ComputeService computeService) {
        super(endpoint);
        this.computeService = computeService;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        String operation = getOperation(exchange);

        if (operation == null) {
            throw new CamelException("Operation must be specified in the endpoitn URI or as a property on the exchange.");
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
        } else {

        }
    }

    /**
     * Create a node with the specified group.
     *
     * @param exchange
     * @throws CamelException
     */
    protected void createNode(Exchange exchange) throws CamelException {
        String group = getGroup(exchange);
        String imageId = getImageId(exchange);
        String locationId = getLocationId(exchange);
        String hardwareId = getHardwareId(exchange);

        if (group == null) {
            throw new CamelException("Group must be specific in the URI or as exchange property for the destroy node operation.");
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
            throw new CamelException("Error creating jclouds node.", e);
        }
    }

    /**
     * Runs a script on the target node.
     *
     * @param exchange
     * @throws CamelException
     */
    protected void runScriptOnNode(Exchange exchange) throws CamelException {
        String script = exchange.getIn().getBody(String.class);
        String nodeId = getNodeId(exchange);
        String user = getUser(exchange);

        Credentials credentials = null;

        if (user != null) {
            credentials = new Credentials(user, null);
        }
        ExecResponse execResponse = null;

        if (credentials == null) {
            execResponse = computeService.runScriptOnNode(nodeId, script);
        } else {
            execResponse = computeService.runScriptOnNode(nodeId, script, RunScriptOptions.Builder.overrideCredentialsWith(credentials).runAsRoot(false));
        }

        if (execResponse == null) {
            throw new CamelException("Failed to receive response for run script operation.");
        }

        exchange.setProperty(JcloudsConstants.RUN_SCRIPT_ERROR, execResponse.getError());
        exchange.setProperty(JcloudsConstants.RUN_SCRIPT_EXIT_CODE, execResponse.getExitCode());
        if (execResponse != null) {
            exchange.getOut().setBody(execResponse.getOutput());
        }
    }

    /**
     * Destroys the node with the specified nodeId.
     *
     * @param exchange
     * @throws CamelException
     */
    protected void destroyNode(Exchange exchange) throws CamelException {
        String nodeId = getNodeId(exchange);
        if (nodeId == null) {
            throw new CamelException("Node id must be specific in the URI or as exchange property for the destroy node operation.");
        }
        computeService.destroyNode(nodeId);
    }

    /**
     * Sets the metadata of the available nodes to the out message.
     *
     * @param exchange
     * @throws CamelException
     */
    protected void listNodes(Exchange exchange) throws CamelException {
        Set<? extends ComputeMetadata> computeMetadatas = computeService.listNodes();
        exchange.getOut().setBody(computeMetadatas);
    }

    /**
     * Sets the available images to the out message.
     *
     * @param exchange
     * @throws CamelException
     */
    protected void listImages(Exchange exchange) throws CamelException {
        Set<? extends Image> images = computeService.listImages();
        exchange.getOut().setBody(images);
    }

    /**
     * Sets the available hardware profiles to the out message.
     *
     * @param exchange
     * @throws CamelException
     */
    protected void listHardware(Exchange exchange) throws CamelException {
        Set<? extends Hardware> hardwareProfiles = computeService.listHardwareProfiles();
        exchange.getOut().setBody(hardwareProfiles);
    }

    /**
     * Retrieves the operation from the URI or from the exchange headers. The header will take precedence over the URI.
     *
     * @param exchange
     * @return
     */
    public String getOperation(Exchange exchange) {
        String operation = ((JcloudsComputeEndpoint) getEndpoint()).getOperation();

        if (exchange.getIn().getHeader(JcloudsConstants.OPERATION) != null) {
            operation = (String) exchange.getIn().getHeader(JcloudsConstants.OPERATION);
        }
        return operation;
    }

    /**
     * Retrieves the image id from the URI or from the exchange properties. The property will take precedence over the URI.
     *
     * @param exchange
     * @return
     */
    protected String getImageId(Exchange exchange) {
        String imageId = ((JcloudsComputeEndpoint) getEndpoint()).getImageId();

        if (exchange.getIn().getHeader(JcloudsConstants.IMAGE_ID) != null) {
            imageId = (String) exchange.getIn().getHeader(JcloudsConstants.IMAGE_ID);
        }
        return imageId;
    }

    /**
     * Retrieves the hardware id from the URI or from the exchange headers. The header will take precedence over the URI.
     *
     * @param exchange
     * @return
     */
    protected String getHardwareId(Exchange exchange) {
        String hardwareId = ((JcloudsComputeEndpoint) getEndpoint()).getHardwareId();

        if (exchange.getIn().getHeader(JcloudsConstants.HARDWARE_ID) != null) {
            hardwareId = (String) exchange.getIn().getHeader(JcloudsConstants.HARDWARE_ID);
        }
        return hardwareId;
    }

    /**
     * Retrieves the location id from the URI or from the exchange headers. The header will take precedence over the URI.
     *
     * @param exchange
     * @return
     */
    protected String getLocationId(Exchange exchange) {
        String locationId = ((JcloudsComputeEndpoint) getEndpoint()).getLocationId();

        if (exchange.getIn().getHeader(JcloudsConstants.LOCATION_ID) != null) {
            locationId = (String) exchange.getIn().getHeader(JcloudsConstants.LOCATION_ID);
        }
        return locationId;
    }

    /**
     * Retrieves the node id from the URI or from the exchange headers. The header will take precedence over the URI.
     *
     * @param exchange
     * @return
     */
    protected String getNodeId(Exchange exchange) {
        String nodeId = ((JcloudsComputeEndpoint) getEndpoint()).getNodeId();

        if (exchange.getIn().getHeader(JcloudsConstants.NODE_ID) != null) {
            nodeId = (String) exchange.getIn().getHeader(JcloudsConstants.NODE_ID);
        }
        return nodeId;
    }

    /**
     * Retrieves the group from the URI or from the exchange headers. The header will take precedence over the URI.
     *
     * @param exchange
     * @return
     */
    protected String getGroup(Exchange exchange) {
        String group = ((JcloudsComputeEndpoint) getEndpoint()).getGroup();

        if (exchange.getIn().getHeader(JcloudsConstants.GROUP) != null) {
            group = (String) exchange.getIn().getHeader(JcloudsConstants.GROUP);
        }
        return group;
    }

    /**
     * Retrieves the user from the URI or from the exchange headers. The header will take precedence over the URI.
     *
     * @param exchange
     * @return
     */
    protected String getUser(Exchange exchange) {
        String user = ((JcloudsComputeEndpoint) getEndpoint()).getUser();

        if (exchange.getIn().getHeader(JcloudsConstants.USER) != null) {
            user = (String) exchange.getIn().getHeader(JcloudsConstants.USER);
        }
        return user;
    }
}
