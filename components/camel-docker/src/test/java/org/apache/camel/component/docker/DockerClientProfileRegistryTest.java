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
package org.apache.camel.component.docker;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.impl.PropertyPlaceholderDelegateRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.PowerMockRunner;

/**
 * Validates a {@link DockerClientProfile} is bound from the Camel registry
 */
@RunWith(PowerMockRunner.class)
public class DockerClientProfileRegistryTest extends CamelTestSupport {
        
    
    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        DockerClientProfile profile = new DockerClientProfile();
        profile.setHost("192.168.59.103");
        profile.setPort(2376);
        profile.setSecure(true);
        profile.setCertPath("/Users/cameluser/.boot2docker/certs/boot2docker-vm");
        
        PropertyPlaceholderDelegateRegistry registry = (PropertyPlaceholderDelegateRegistry) camelContext.getRegistry();
        JndiRegistry jndiRegistry = (JndiRegistry)registry.getRegistry();
        jndiRegistry.bind("dockerProfile", profile);
        
        
        return camelContext;
    }
    
    @Test
    public void clientProfileTest() {
        DockerEndpoint endpoint = resolveMandatoryEndpoint(context(), "docker://info?clientProfile=#dockerProfile", DockerEndpoint.class);
        assertNotNull(endpoint.getConfiguration().getClientProfile());
        DockerClientProfile clientProfile =  endpoint.getConfiguration().getClientProfile();
        assertEquals("192.168.59.103", clientProfile.getHost());
        assertEquals((Integer) 2376, clientProfile.getPort());
        assertTrue(clientProfile.isSecure());
        assertEquals("/Users/cameluser/.boot2docker/certs/boot2docker-vm" ,clientProfile.getCertPath());

    }


}
