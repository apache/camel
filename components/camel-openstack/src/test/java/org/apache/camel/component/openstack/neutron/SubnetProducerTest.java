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
import org.apache.camel.component.openstack.neutron.producer.SubnetProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openstack4j.api.Builders;
import org.openstack4j.api.networking.SubnetService;
import org.openstack4j.model.common.ActionResponse;
import org.openstack4j.model.network.IPVersionType;
import org.openstack4j.model.network.Subnet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings(strictness = Strictness.LENIENT)
public class SubnetProducerTest extends NeutronProducerTestSupport {

    private Subnet dummySubnet;

    @Mock
    private Subnet testOSsubnet;

    @Mock
    private SubnetService subnetService;

    @Captor
    private ArgumentCaptor<Subnet> subnetCaptor;

    @Captor
    private ArgumentCaptor<String> subnetIdCaptor;

    @BeforeEach
    public void setUp() {
        when(networkingService.subnet()).thenReturn(subnetService);

        producer = new SubnetProducer(endpoint, client);
        when(subnetService.create(any())).thenReturn(testOSsubnet);
        when(subnetService.get(anyString())).thenReturn(testOSsubnet);

        List<Subnet> getAllList = new ArrayList<>();
        getAllList.add(testOSsubnet);
        getAllList.add(testOSsubnet);
        doReturn(getAllList).when(subnetService).list();

        dummySubnet = createSubnet();
        when(testOSsubnet.getName()).thenReturn(dummySubnet.getName());
        when(testOSsubnet.getNetworkId()).thenReturn(dummySubnet.getNetworkId());
        when(testOSsubnet.getId()).thenReturn(UUID.randomUUID().toString());
    }

    @Test
    public void createTest() throws Exception {
        msg.setHeader(OpenstackConstants.OPERATION, OpenstackConstants.CREATE);
        msg.setHeader(OpenstackConstants.NAME, dummySubnet.getName());
        msg.setHeader(NeutronConstants.NETWORK_ID, dummySubnet.getNetworkId());
        msg.setHeader(NeutronConstants.IP_VERSION, IPVersionType.V4);

        producer.process(exchange);

        verify(subnetService).create(subnetCaptor.capture());

        assertEqualsSubnet(dummySubnet, subnetCaptor.getValue());
        assertNotNull(msg.getBody(Subnet.class).getId());
    }

    @Test
    public void getTest() throws Exception {
        final String subnetID = "myNetID";
        msg.setHeader(OpenstackConstants.OPERATION, OpenstackConstants.GET);
        msg.setHeader(NeutronConstants.SUBNET_ID, subnetID);

        producer.process(exchange);

        verify(subnetService).get(subnetIdCaptor.capture());

        assertEquals(subnetID, subnetIdCaptor.getValue());
        assertEqualsSubnet(testOSsubnet, msg.getBody(Subnet.class));
    }

    @Test
    public void getAllTest() throws Exception {
        msg.setHeader(OpenstackConstants.OPERATION, OpenstackConstants.GET_ALL);

        producer.process(exchange);

        final List<Subnet> result = msg.getBody(List.class);
        assertEquals(2, result.size());
        assertEquals(testOSsubnet, result.get(0));
    }

    @Test
    public void deleteTest() throws Exception {
        when(subnetService.delete(anyString())).thenReturn(ActionResponse.actionSuccess());
        final String subnetID = "myNetID";
        msg.setHeader(OpenstackConstants.OPERATION, OpenstackConstants.DELETE);
        msg.setHeader(OpenstackConstants.ID, subnetID);

        producer.process(exchange);

        verify(subnetService).delete(subnetIdCaptor.capture());
        assertEquals(subnetID, subnetIdCaptor.getValue());
    }

    private Subnet createSubnet() {
        return Builders.subnet()
                .name("name")
                .networkId("netId")
                .build();
    }

    private void assertEqualsSubnet(Subnet old, Subnet newSubnet) {
        assertEquals(old.getName(), newSubnet.getName());
        assertEquals(old.getNetworkId(), newSubnet.getNetworkId());
    }
}
