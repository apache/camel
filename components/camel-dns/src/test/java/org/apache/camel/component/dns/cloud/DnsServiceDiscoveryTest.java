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
package org.apache.camel.component.dns.cloud;

import java.util.List;

import org.apache.camel.cloud.ServiceDefinition;
import org.apache.camel.component.dns.DnsConfiguration;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

public class DnsServiceDiscoveryTest {
    @Test
    public void testServiceDiscovery() throws Exception {
        DnsConfiguration configuration = new DnsConfiguration();
        DnsServiceDiscovery discovery = new DnsServiceDiscovery(configuration);

        configuration.setDomain("gmail.com");
        configuration.setProto("_tcp");

        List<ServiceDefinition> services = discovery.getServices("_xmpp-server");
        assertNotNull(services);
        assertFalse(services.isEmpty());

        for (ServiceDefinition service : services) {
            assertFalse(service.getMetadata().isEmpty());
            assertNotNull(service.getMetadata().get("priority"));
            assertNotNull(service.getMetadata().get("weight"));
        }
    }
}
