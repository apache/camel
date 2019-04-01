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
package org.apache.camel.impl.cloud;

import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.cloud.ServiceDiscovery;
import org.apache.camel.cloud.ServiceDiscoveryFactory;
import org.apache.camel.spi.annotations.CloudServiceFactory;
import org.apache.camel.util.ObjectHelper;

@CloudServiceFactory("combined-service-discovery")
public class CombinedServiceDiscoveryFactory implements ServiceDiscoveryFactory {
    private List<ServiceDiscovery> serviceDiscoveryList;

    public CombinedServiceDiscoveryFactory() {
    }

    // *************************************************************************
    // Properties
    // *************************************************************************

    public List<ServiceDiscovery> getServiceDiscoveryList() {
        return serviceDiscoveryList;
    }

    public void setServiceDiscoveryList(List<ServiceDiscovery> serviceDiscoveryList) {
        this.serviceDiscoveryList = serviceDiscoveryList;
    }

    // *************************************************************************
    // Factory
    // *************************************************************************

    @Override
    public ServiceDiscovery newInstance(CamelContext camelContext) throws Exception {
        ObjectHelper.notNull(serviceDiscoveryList, "ServiceDiscovery list");

        return new CombinedServiceDiscovery(serviceDiscoveryList);
    }
}
