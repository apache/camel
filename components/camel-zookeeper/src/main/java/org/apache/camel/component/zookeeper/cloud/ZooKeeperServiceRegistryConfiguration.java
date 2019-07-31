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
package org.apache.camel.component.zookeeper.cloud;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.zookeeper.ZooKeeperCuratorConfiguration;

public class ZooKeeperServiceRegistryConfiguration extends ZooKeeperCuratorConfiguration {
    /**
     * Should we remove all the registered services know by this registry on stop?
     */
    private boolean deregisterServicesOnStop = true;

    /**
     * Should we override the service host if given ?
     */
    private boolean overrideServiceHost = true;

    /**
     * Service host.
     */
    private String serviceHost;

    // ***********************************************
    // Properties
    // ***********************************************

    public boolean isDeregisterServicesOnStop() {
        return deregisterServicesOnStop;
    }

    public void setDeregisterServicesOnStop(boolean deregisterServicesOnStop) {
        this.deregisterServicesOnStop = deregisterServicesOnStop;
    }

    public boolean isOverrideServiceHost() {
        return overrideServiceHost;
    }

    public void setOverrideServiceHost(boolean overrideServiceHost) {
        this.overrideServiceHost = overrideServiceHost;
    }

    public String getServiceHost() {
        return serviceHost;
    }

    public void setServiceHost(String serviceHost) {
        this.serviceHost = serviceHost;
    }

    // ***********************************************
    //
    // ***********************************************

    @Override
    public ZooKeeperServiceRegistryConfiguration copy() {
        try {
            return (ZooKeeperServiceRegistryConfiguration)super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
