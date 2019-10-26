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
import org.apache.camel.component.openstack.neutron.producer.PortProducer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.openstack4j.api.Builders;
import org.openstack4j.api.networking.PortService;
import org.openstack4j.model.common.ActionResponse;
import org.openstack4j.model.network.Port;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class PortProducerTest extends NeutronProducerTestSupport {

    private Port dummyPort;

    @Mock
    private Port testOSport;

    @Mock
    private PortService portService;

    @Captor
    private ArgumentCaptor<Port> portCaptor;

    @Captor
    private ArgumentCaptor<String> portIdCaptor;

    @Before
    public void setUp() {
        when(networkingService.port()).thenReturn(portService);

        producer = new PortProducer(endpoint, client);
        when(portService.create(any())).thenReturn(testOSport);
        when(portService.get(anyString())).thenReturn(testOSport);

        List<Port> getAllList = new ArrayList<>();
        getAllList.add(testOSport);
        getAllList.add(testOSport);
        doReturn(getAllList).when(portService).list();

        dummyPort = createPort();
        when(testOSport.getName()).thenReturn(dummyPort.getName());
        when(testOSport.getNetworkId()).thenReturn(dummyPort.getNetworkId());
        when(testOSport.getMacAddress()).thenReturn(dummyPort.getMacAddress());
        when(testOSport.getDeviceId()).thenReturn(dummyPort.getDeviceId());
        when(testOSport.getId()).thenReturn(UUID.randomUUID().toString());
    }

    @Test
    public void createTest() throws Exception {
        msg.setHeader(OpenstackConstants.OPERATION, OpenstackConstants.CREATE);
        msg.setHeader(OpenstackConstants.NAME, dummyPort.getName());
        msg.setHeader(NeutronConstants.TENANT_ID, dummyPort.getTenantId());
        msg.setHeader(NeutronConstants.NETWORK_ID, dummyPort.getNetworkId());
        msg.setHeader(NeutronConstants.MAC_ADDRESS, dummyPort.getMacAddress());
        msg.setHeader(NeutronConstants.DEVICE_ID, dummyPort.getDeviceId());

        producer.process(exchange);

        verify(portService).create(portCaptor.capture());

        assertEqualsPort(dummyPort, portCaptor.getValue());
        assertNotNull(msg.getBody(Port.class).getId());
    }

    @Test
    public void getTest() throws Exception {
        final String portID = "myNetID";
        msg.setHeader(OpenstackConstants.OPERATION, OpenstackConstants.GET);
        msg.setHeader(OpenstackConstants.ID, portID);

        producer.process(exchange);

        verify(portService).get(portIdCaptor.capture());

        assertEquals(portID, portIdCaptor.getValue());
        assertEqualsPort(testOSport, msg.getBody(Port.class));
    }

    @Test
    public void getAllTest() throws Exception {
        msg.setHeader(OpenstackConstants.OPERATION, OpenstackConstants.GET_ALL);

        producer.process(exchange);

        final List<Port> result = msg.getBody(List.class);
        assertTrue(result.size() == 2);
        assertEquals(testOSport, result.get(0));
    }

    @Test
    public void updateTest() throws Exception {
        final String portID = "myID";
        msg.setHeader(OpenstackConstants.OPERATION, OpenstackConstants.UPDATE);
        final String newDevId = "dev";
        when(testOSport.getDeviceId()).thenReturn(newDevId);
        when(testOSport.getId()).thenReturn(portID);
        when(portService.update(any())).thenReturn(testOSport);

        msg.setBody(testOSport);

        producer.process(exchange);

        verify(portService).update(portCaptor.capture());

        assertEqualsPort(testOSport, portCaptor.getValue());
        assertNotNull(portCaptor.getValue().getId());
    }

    @Test
    public void deleteTest() throws Exception {
        when(portService.delete(anyString())).thenReturn(ActionResponse.actionSuccess());
        final String portID = "myNetID";
        msg.setHeader(OpenstackConstants.OPERATION, OpenstackConstants.DELETE);
        msg.setHeader(OpenstackConstants.ID, portID);

        producer.process(exchange);

        verify(portService).delete(portIdCaptor.capture());
        assertEquals(portID, portIdCaptor.getValue());
    }

    private Port createPort() {
        return Builders.port()
                .name("name")
                .tenantId("tenantID")
                .networkId("netId")
                .deviceId("devID")
                .macAddress("mac").build();
    }

    private void assertEqualsPort(Port old, Port newPort) {
        assertEquals(old.getName(), newPort.getName());
        assertEquals(old.getTenantId(), newPort.getTenantId());
        assertEquals(old.getNetworkId(), newPort.getNetworkId());
        assertEquals(old.getDeviceId(), newPort.getDeviceId());
        assertEquals(old.getMacAddress(), newPort.getMacAddress());
    }
}
