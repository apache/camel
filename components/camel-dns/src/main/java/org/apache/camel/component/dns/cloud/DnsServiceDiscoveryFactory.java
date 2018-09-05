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
package org.apache.camel.component.dns.cloud;

import org.apache.camel.CamelContext;
import org.apache.camel.cloud.ServiceDiscovery;
import org.apache.camel.cloud.ServiceDiscoveryFactory;
import org.apache.camel.component.dns.DnsConfiguration;

public class DnsServiceDiscoveryFactory implements ServiceDiscoveryFactory {
    private final DnsConfiguration configuration;

    public DnsServiceDiscoveryFactory() {
        this.configuration = new DnsConfiguration();
    }

    public DnsServiceDiscoveryFactory(DnsConfiguration configuration) {
        this.configuration = configuration;
    }

    // *************************************************************************
    // Properties
    // *************************************************************************

    public String getProto() {
        return configuration.getProto();
    }

    public void setProto(String proto) {
        configuration.setProto(proto);
    }

    public String getDomain() {
        return configuration.getDomain();
    }

    public void setDomain(String domain) {
        configuration.setDomain(domain);
    }

    // *************************************************************************
    // Factory
    // *************************************************************************

    @Override
    public ServiceDiscovery newInstance(CamelContext camelContext) throws Exception {
        return new DnsServiceDiscovery(configuration);
    }
}
