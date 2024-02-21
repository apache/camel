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
package org.apache.camel.component.openstack.it;

import org.apache.camel.component.openstack.common.OpenstackConstants;
import org.apache.camel.component.openstack.neutron.NeutronConstants;
import org.junit.jupiter.api.Test;
import org.openstack4j.api.Builders;
import org.openstack4j.model.network.Network;
import org.openstack4j.model.network.NetworkType;
import org.openstack4j.model.network.State;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OpenstackNeutronNetworkTest extends OpenstackWiremockTestSupport {

    private static final String URI_FORMAT
            = "openstack-neutron://%s?username=user&password=secret&project=project&operation=%s&subsystem="
              + NeutronConstants.NEUTRON_NETWORK_SUBSYSTEM;

    private static final String NETWORK_NAME = "net1";
    private static final String NETWORK_ID = "4e8e5957-649f-477b-9e5b-f1f75b21c03c";

    @Test
    void createShouldSucceed() {
        Network in = Builders.network().name(NETWORK_NAME).isRouterExternal(true).adminStateUp(true).build();

        String uri = String.format(URI_FORMAT, url(), OpenstackConstants.CREATE);
        Network out = template.requestBody(uri, in, Network.class);

        assertEquals(NETWORK_NAME, out.getName());
        assertEquals(State.ACTIVE, out.getStatus());
        assertTrue(out.isRouterExternal());
    }

    @Test
    void getShouldSucceed() {
        String uri = String.format(URI_FORMAT, url(), OpenstackConstants.GET);
        Network out = template.requestBodyAndHeader(uri, null, OpenstackConstants.ID, NETWORK_ID, Network.class);

        assertNotNull(out);
        assertEquals(NETWORK_NAME, out.getName());
        assertEquals(State.ACTIVE, out.getStatus());
        assertFalse(out.isRouterExternal());
    }

    @Test
    void getAllShouldSucceed() {
        String uri = String.format(URI_FORMAT, url(), OpenstackConstants.GET_ALL);
        Network[] networks = template.requestBody(uri, null, Network[].class);

        assertNotNull(networks);
        assertEquals(1, networks.length);

        assertEquals(NETWORK_NAME, networks[0].getName());
        assertNotNull(networks[0].getSubnets());
        assertEquals(1, networks[0].getSubnets().size());
        assertEquals("0c4faf33-8c23-4dc9-8bf5-30dd1ab452f9", networks[0].getSubnets().get(0));
        assertEquals("73f6f1ac-5e58-4801-88c3-7e12c6ddfb39", networks[0].getId());
        assertEquals(NetworkType.VXLAN, networks[0].getNetworkType());
    }

    @Test
    void deleteShouldSucceed() {
        String uri = String.format(URI_FORMAT, url(), OpenstackConstants.DELETE);
        assertDoesNotThrow(() -> template.requestBodyAndHeader(uri, null, OpenstackConstants.ID, NETWORK_ID));
    }
}
