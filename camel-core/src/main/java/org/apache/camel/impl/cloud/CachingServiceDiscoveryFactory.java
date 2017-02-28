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
package org.apache.camel.impl.cloud;

import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.cloud.ServiceDiscovery;
import org.apache.camel.cloud.ServiceDiscoveryFactory;
import org.apache.camel.util.ObjectHelper;

public class CachingServiceDiscoveryFactory implements ServiceDiscoveryFactory {
    private Integer timeout;
    private TimeUnit units;
    private ServiceDiscovery serviceDiscovery;

    public CachingServiceDiscoveryFactory() {
    }

    // *************************************************************************
    // Properties
    // *************************************************************************

    public Integer getTimeout() {
        return timeout;
    }

    public void setTimeout(Integer timeout) {
        this.timeout = timeout;
    }

    public TimeUnit getUnits() {
        return units;
    }

    public void setUnits(TimeUnit units) {
        this.units = units;
    }

    public ServiceDiscovery getServiceDiscovery() {
        return serviceDiscovery;
    }

    public void setServiceDiscovery(ServiceDiscovery serviceDiscovery) {
        this.serviceDiscovery = serviceDiscovery;
    }

    // *************************************************************************
    // Factory
    // *************************************************************************

    @Override
    public ServiceDiscovery newInstance(CamelContext camelContext) throws Exception {
        ObjectHelper.notNull(serviceDiscovery, "ServiceDiscovery configuration");
        ObjectHelper.notNull(timeout, "CachingServiceDiscovery timeout");
        ObjectHelper.notNull(units, "CachingServiceDiscovery time units");

        return new CachingServiceDiscovery(
            serviceDiscovery,
            units.toMillis(timeout)
        );
    }
}
