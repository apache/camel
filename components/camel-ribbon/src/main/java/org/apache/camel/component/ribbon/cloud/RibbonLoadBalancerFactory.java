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
package org.apache.camel.component.ribbon.cloud;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.cloud.LoadBalancer;
import org.apache.camel.cloud.LoadBalancerFactory;
import org.apache.camel.component.ribbon.RibbonConfiguration;

public class RibbonLoadBalancerFactory implements LoadBalancerFactory {
    private final RibbonConfiguration configuration;

    public RibbonLoadBalancerFactory() {
        this.configuration = new RibbonConfiguration();
    }

    // *************************************************************************
    // Properties
    // *************************************************************************

    public String getClientName() {
        return configuration.getClientName();
    }

    public void setClientName(String clientName) {
        configuration.setClientName(clientName);
    }

    public Map<String, String> getProperies() {
        return configuration.getClientConfig();
    }

    public void setProperties(Map<String, String> clientConfig) {
        configuration.setClientConfig(clientConfig);
    }

    // *************************************************************************
    // Factory
    // *************************************************************************

    @Override
    public LoadBalancer newInstance(CamelContext camelContext) throws Exception {
        return new RibbonLoadBalancer(configuration);
    }
}
