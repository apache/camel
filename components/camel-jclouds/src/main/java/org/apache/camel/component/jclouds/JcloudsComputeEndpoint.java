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

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.jclouds.compute.ComputeService;

public class JcloudsComputeEndpoint extends JcloudsEndpoint {

    private ComputeService computeService;

    private String imageId;
    private String locationId;
    private String hardwareId;

    private String operation;
    private String nodeState;
    private String nodeId;
    private String group;
    private String user;

    public JcloudsComputeEndpoint(String uri, JcloudsComponent component, ComputeService computeService) {
        super(uri, component);
        this.computeService = computeService;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new JcloudsComputeProducer(this, computeService);
    }

    @Override
    public Consumer createConsumer(Processor processor) {
        throw new UnsupportedOperationException("Consumer not supported for JcloudsComputeEndpoint!");
    }

    @Override
    public String getImageId() {
        return imageId;
    }

    @Override
    public void setImageId(String imageId) {
        this.imageId = imageId;
    }

    @Override
    public String getLocationId() {
        return locationId;
    }

    @Override
    public void setLocationId(String locationId) {
        this.locationId = locationId;
    }

    @Override
    public String getHardwareId() {
        return hardwareId;
    }

    @Override
    public void setHardwareId(String hardwareId) {
        this.hardwareId = hardwareId;
    }

    @Override
    public String getOperation() {
        return operation;
    }

    @Override
    public void setOperation(String operation) {
        this.operation = operation;
    }

    @Override
    public String getNodeState() {
        return nodeState;
    }

    @Override
    public void setNodeState(String nodeState) {
        this.nodeState = nodeState;
    }

    @Override
    public String getNodeId() {
        return nodeId;
    }

    @Override
    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    @Override
    public String getGroup() {
        return group;
    }

    @Override
    public void setGroup(String group) {
        this.group = group;
    }

    @Override
    public String getUser() {
        return user;
    }

    @Override
    public void setUser(String user) {
        this.user = user;
    }
}
