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

import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;

/**
 * For interacting with cloud compute & blobstore service via jclouds.
 */
@UriEndpoint(firstVersion = "2.9.0", scheme = "jclouds", title = "JClouds", syntax = "jclouds:command:providerId", consumerClass = JcloudsConsumer.class, label = "api,cloud")
public abstract class JcloudsEndpoint extends DefaultEndpoint {

    @UriParam
    private JcloudsConfiguration configuration = new JcloudsConfiguration();

    public JcloudsEndpoint(String uri, JcloudsComponent component) {
        super(uri, component);
    }

    public boolean isSingleton() {
        return true;
    }

    public JcloudsConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(JcloudsConfiguration configuration) {
        this.configuration = configuration;
    }

    public JcloudsCommand getCommand() {
        return configuration.getCommand();
    }

    public void setCommand(JcloudsCommand command) {
        configuration.setCommand(command);
    }

    public String getProviderId() {
        return configuration.getProviderId();
    }

    public void setProviderId(String providerId) {
        configuration.setProviderId(providerId);
    }

    public String getImageId() {
        return configuration.getImageId();
    }

    public void setImageId(String imageId) {
        configuration.setImageId(imageId);
    }

    public String getLocationId() {
        return configuration.getLocationId();
    }

    public void setLocationId(String locationId) {
        configuration.setLocationId(locationId);
    }

    public String getHardwareId() {
        return configuration.getHardwareId();
    }

    public void setHardwareId(String hardwareId) {
        configuration.setHardwareId(hardwareId);
    }

    public String getOperation() {
        return configuration.getOperation();
    }

    public void setOperation(String operation) {
        configuration.setOperation(operation);
    }

    public String getNodeState() {
        return configuration.getNodeState();
    }

    public void setNodeState(String nodeState) {
        configuration.setNodeState(nodeState);
    }

    public String getNodeId() {
        return configuration.getNodeId();
    }

    public void setNodeId(String nodeId) {
        configuration.setNodeId(nodeId);
    }

    public String getGroup() {
        return configuration.getGroup();
    }

    public void setGroup(String group) {
        configuration.setGroup(group);
    }

    public String getUser() {
        return configuration.getUser();
    }

    public void setUser(String user) {
        configuration.setUser(user);
    }

    public String getContainer() {
        return configuration.getContainer();
    }

    public void setContainer(String container) {
        configuration.setContainer(container);
    }

    public String getDirectory() {
        return configuration.getDirectory();
    }

    public void setDirectory(String directory) {
        configuration.setDirectory(directory);
    }

    public String getBlobName() {
        return configuration.getBlobName();
    }

    public void setBlobName(String blobName) {
        configuration.setBlobName(blobName);
    }
}
