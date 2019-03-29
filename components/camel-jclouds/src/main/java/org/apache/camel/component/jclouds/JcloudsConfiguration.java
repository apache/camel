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
package org.apache.camel.component.jclouds;

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

@UriParams
public class JcloudsConfiguration {

    @UriPath @Metadata(required = true)
    private JcloudsCommand command;
    @UriPath @Metadata(required = true)
    private String providerId;

    // compute options
    @UriParam(label = "producer,compute")
    private String imageId;
    @UriParam(label = "producer,compute")
    private String locationId;
    @UriParam(label = "producer,compute")
    private String hardwareId;
    @UriParam(label = "producer,compute")
    private String operation;
    @UriParam(label = "producer,compute", enums = "PENDING,TERMINATED,SUSPENDED,RUNNING,ERROR,UNRECOGNIZED")
    private String nodeState;
    @UriParam(label = "producer,compute")
    private String nodeId;
    @UriParam(label = "producer,compute")
    private String group;
    @UriParam(label = "producer,compute")
    private String user;

    // blob options
    @UriParam(label = "blobstore")
    private String container;
    @UriParam(label = "consumer,blobstore")
    private String directory;
    @UriParam(label = "producer,blobstore")
    private String blobName;

    public JcloudsCommand getCommand() {
        return command;
    }

    /**
     * What command to execute such as blobstore or compute.
     */
    public void setCommand(JcloudsCommand command) {
        this.command = command;
    }

    public String getProviderId() {
        return providerId;
    }

    /**
     * The name of the cloud provider that provides the target service (e.g. aws-s3 or aws_ec2).
     */
    public void setProviderId(String providerId) {
        this.providerId = providerId;
    }

    public String getImageId() {
        return imageId;
    }

    /**
     * The imageId that will be used for creating a node. Values depend on the actual cloud provider.
     */
    public void setImageId(String imageId) {
        this.imageId = imageId;
    }

    public String getLocationId() {
        return locationId;
    }

    /**
     * The location that will be used for creating a node. Values depend on the actual cloud provider.
     */
    public void setLocationId(String locationId) {
        this.locationId = locationId;
    }

    public String getHardwareId() {
        return hardwareId;
    }

    /**
     * The hardware that will be used for creating a node. Values depend on the actual cloud provider.
     */
    public void setHardwareId(String hardwareId) {
        this.hardwareId = hardwareId;
    }

    public String getOperation() {
        return operation;
    }

    /**
     * Specifies the type of operation that will be performed to the blobstore.
     */
    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getNodeState() {
        return nodeState;
    }

    /**
     * To filter by node status to only select running nodes etc.
     */
    public void setNodeState(String nodeState) {
        this.nodeState = nodeState;
    }

    public String getNodeId() {
        return nodeId;
    }

    /**
     * The id of the node that will run the script or destroyed.
     */
    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getGroup() {
        return group;
    }

    /**
     * The group that will be assigned to the newly created node. Values depend on the actual cloud provider.
     */
    public void setGroup(String group) {
        this.group = group;
    }

    public String getUser() {
        return user;
    }

    /**
     * The user on the target node that will run the script.
     */
    public void setUser(String user) {
        this.user = user;
    }

    public String getContainer() {
        return container;
    }

    /**
     * The name of the blob container.
     */
    public void setContainer(String container) {
        this.container = container;
    }

    public String getDirectory() {
        return directory;
    }

    /**
     * An optional directory name to use
     */
    public void setDirectory(String directory) {
        this.directory = directory;
    }

    public String getBlobName() {
        return blobName;
    }

    /**
     * The name of the blob.
     */
    public void setBlobName(String blobName) {
        this.blobName = blobName;
    }

}
