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
package org.apache.camel.component.zookeepermaster;

import org.apache.camel.component.zookeepermaster.group.Group;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.spi.Metadata;
import org.apache.camel.util.ServiceHelper;
import org.apache.curator.framework.CuratorFramework;

public abstract class ZookeeperComponentSupport extends DefaultComponent {

    private final ZookeeperGroupSupport zookeeperGroupSupport = new ZookeeperGroupSupport();

    // use for component documentation
    @Metadata(label = "advanced")
    private CuratorFramework curator;
    @Metadata(defaultValue = "10000")
    private int maximumConnectionTimeout = 10 * 1000;
    @Metadata(defaultValue = "localhost:2181")
    private String zooKeeperUrl;
    @Metadata(label = "security", secret = true)
    private String zooKeeperPassword;

    public Group<CamelNodeState> createGroup(String path) {
        return zookeeperGroupSupport.createGroup(path);
    }

    public CuratorFramework getCurator() {
        return zookeeperGroupSupport.getCurator();
    }

    /**
     * To use a custom configured CuratorFramework as connection to zookeeper ensemble.
     */
    public void setCurator(CuratorFramework curator) {
        zookeeperGroupSupport.setCurator(curator);
    }

    public int getMaximumConnectionTimeout() {
        return zookeeperGroupSupport.getMaximumConnectionTimeout();
    }

    /**
     * Timeout in millis to use when connecting to the zookeeper ensemble
     */
    public void setMaximumConnectionTimeout(int maximumConnectionTimeout) {
        zookeeperGroupSupport.setMaximumConnectionTimeout(maximumConnectionTimeout);
    }

    public String getZooKeeperUrl() {
        return zookeeperGroupSupport.getZooKeeperUrl();
    }

    /**
     * The url for the zookeeper ensemble
     */
    public void setZooKeeperUrl(String zooKeeperUrl) {
        zookeeperGroupSupport.setZooKeeperUrl(zooKeeperUrl);
    }

    public String getZooKeeperPassword() {
        return zookeeperGroupSupport.getZooKeeperPassword();
    }

    /**
     * The password to use when connecting to the zookeeper ensemble
     */
    public void setZooKeeperPassword(String zooKeeperPassword) {
        zookeeperGroupSupport.setZooKeeperPassword(zooKeeperPassword);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        zookeeperGroupSupport.setCamelContext(getCamelContext());
        ServiceHelper.startService(zookeeperGroupSupport);
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        ServiceHelper.stopAndShutdownServices(zookeeperGroupSupport);
    }

}
