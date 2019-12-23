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
package org.apache.camel.component.openstack.neutron;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.camel.component.openstack.common.OpenstackConstants;
import org.apache.camel.component.openstack.neutron.producer.NetworkProducer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.openstack4j.api.Builders;
import org.openstack4j.api.networking.NetworkService;
import org.openstack4j.model.common.ActionResponse;
import org.openstack4j.model.network.Network;
import org.openstack4j.model.network.NetworkType;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class NetworkProducerTest extends NeutronProducerTestSupport {

    private Network dummyNetwork;

    @Mock
    private Network testOSnetwork;

    @Mock
    private NetworkService networkService;

    @Captor
    private ArgumentCaptor<Network> networkCaptor;

    @Captor
    private ArgumentCaptor<String> networkIdCaptor;

    @Before
    public void setUp() {
        when(networkingService.network()).thenReturn(networkService);

        producer = new NetworkProducer(endpoint, client);
        when(networkService.create(any())).thenReturn(testOSnetwork);
        when(networkService.get(anyString())).thenReturn(testOSnetwork);

        List<Network> getAllList = new ArrayList<>();
        getAllList.add(testOSnetwork);
        getAllList.add(testOSnetwork);
        doReturn(getAllList).when(networkService).list();

        dummyNetwork = createNetwork();
        when(testOSnetwork.getName()).thenReturn(dummyNetwork.getName());
        when(testOSnetwork.getTenantId()).thenReturn(dummyNetwork.getTenantId());
        when(testOSnetwork.getNetworkType()).thenReturn(dummyNetwork.getNetworkType());
        when(testOSnetwork.getId()).thenReturn(UUID.randomUUID().toString());
    }

    @Test
    public void createTest() throws Exception {
        msg.setHeader(OpenstackConstants.OPERATION, OpenstackConstants.CREATE);
        msg.setHeader(OpenstackConstants.NAME, dummyNetwork.getName());
        msg.setHeader(NeutronConstants.NETWORK_TYPE, dummyNetwork.getNetworkType());
        msg.setHeader(NeutronConstants.TENANT_ID, dummyNetwork.getTenantId());

        producer.process(exchange);

        verify(networkService).create(networkCaptor.capture());

        assertEqualsNetwork(dummyNetwork, networkCaptor.getValue());
        assertNotNull(msg.getBody(Network.class).getId());
    }

    @Test
    public void getTest() throws Exception {
        final String networkID = "myNetID";
        msg.setHeader(OpenstackConstants.OPERATION, OpenstackConstants.GET);
        msg.setHeader(NeutronConstants.NETWORK_ID, networkID);

        producer.process(exchange);

        verify(networkService).get(networkIdCaptor.capture());

        assertEquals(networkID, networkIdCaptor.getValue());
        assertEqualsNetwork(testOSnetwork, msg.getBody(Network.class));
    }

    @Test
    public void getAllTest() throws Exception {
        msg.setHeader(OpenstackConstants.OPERATION, OpenstackConstants.GET_ALL);

        producer.process(exchange);

        final List<Network> result = msg.getBody(List.class);
        assertTrue(result.size() == 2);
        assertEquals(testOSnetwork, result.get(0));
    }

    @Test
    public void deleteTest() throws Exception {
        when(networkService.delete(anyString())).thenReturn(ActionResponse.actionSuccess());
        final String networkID = "myNetID";
        msg.setHeader(OpenstackConstants.OPERATION, OpenstackConstants.DELETE);
        msg.setHeader(OpenstackConstants.ID, networkID);

        producer.process(exchange);

        verify(networkService).delete(networkIdCaptor.capture());
        assertEquals(networkID, networkIdCaptor.getValue());
    }

    private Network createNetwork() {
        return Builders.network()
                .name("name")
                .tenantId("tenantID")
                .networkType(NetworkType.LOCAL).build();
    }

    private void assertEqualsNetwork(Network old, Network newNetwork) {
        assertEquals(old.getName(), newNetwork.getName());
        assertEquals(old.getTenantId(), newNetwork.getTenantId());
        assertEquals(old.getNetworkType(), newNetwork.getNetworkType());
    }
}
