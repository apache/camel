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
package org.apache.camel.component.openstack.nova;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.camel.component.openstack.common.OpenstackConstants;
import org.apache.camel.component.openstack.nova.producer.FlavorsProducer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.openstack4j.api.Builders;
import org.openstack4j.api.compute.FlavorService;
import org.openstack4j.model.common.ActionResponse;
import org.openstack4j.model.compute.Flavor;
import org.openstack4j.model.compute.builder.FlavorBuilder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class FlavorProducerTest extends NovaProducerTestSupport {

    @Mock
    private Flavor testOSFlavor;

    @Mock
    private FlavorService flavorService;

    @Captor
    private ArgumentCaptor<Flavor> flavorCaptor;

    @Captor
    private ArgumentCaptor<String> flavorIdCaptor;

    private Flavor dummyFlavor;

    @Before
    public void setUp() {
        when(computeService.flavors()).thenReturn(flavorService);

        producer = new FlavorsProducer(endpoint, client);

        when(flavorService.create(any())).thenReturn(testOSFlavor);
        when(flavorService.get(anyString())).thenReturn(testOSFlavor);

        List<org.openstack4j.model.compute.Flavor> getAllList = new ArrayList<>();
        getAllList.add(testOSFlavor);
        getAllList.add(testOSFlavor);
        doReturn(getAllList).when(flavorService).list();

        dummyFlavor = createTestFlavor();

        when(testOSFlavor.getId()).thenReturn(UUID.randomUUID().toString());
        when(testOSFlavor.getName()).thenReturn(dummyFlavor.getName());
        when(testOSFlavor.getRam()).thenReturn(dummyFlavor.getRam());
        when(testOSFlavor.getVcpus()).thenReturn(dummyFlavor.getVcpus());
        when(testOSFlavor.getDisk()).thenReturn(dummyFlavor.getDisk());
    }

    @Test
    public void createFlavor() throws Exception {
        when(endpoint.getOperation()).thenReturn(OpenstackConstants.CREATE);
        final String expectedFlavorID = UUID.randomUUID().toString();
        when(testOSFlavor.getId()).thenReturn(expectedFlavorID);

        //send dummyFlavor to create
        msg.setBody(dummyFlavor);
        producer.process(exchange);

        verify(flavorService).create(flavorCaptor.capture());
        assertEquals(dummyFlavor, flavorCaptor.getValue());

        final Flavor createdFlavor = msg.getBody(Flavor.class);
        assertEqualsFlavors(dummyFlavor, createdFlavor);
        assertNotNull(createdFlavor.getId());
    }

    @Test
    public void createFlavorWithHeaders() throws Exception {
        Map<String, Object> headers = new HashMap<>();
        headers.put(OpenstackConstants.OPERATION, OpenstackConstants.CREATE);
        headers.put(OpenstackConstants.NAME, dummyFlavor.getName());
        headers.put(NovaConstants.VCPU, dummyFlavor.getVcpus());
        headers.put(NovaConstants.DISK, dummyFlavor.getDisk());
        headers.put(NovaConstants.SWAP, dummyFlavor.getSwap());
        headers.put(NovaConstants.RAM, dummyFlavor.getRam());
        msg.setHeaders(headers);
        producer.process(exchange);

        verify(flavorService).create(flavorCaptor.capture());
        assertEqualsFlavors(dummyFlavor, flavorCaptor.getValue());

        final Flavor created = msg.getBody(Flavor.class);
        assertNotNull(created.getId());
        assertEqualsFlavors(dummyFlavor, created);
    }

    @Test
    public void getTest() throws Exception {
        msg.setHeader(OpenstackConstants.OPERATION, OpenstackConstants.GET);
        msg.setHeader(OpenstackConstants.ID, "anything - client is mocked");

        //should return dummyFlavor
        producer.process(exchange);

        final Flavor result = msg.getBody(Flavor.class);
        assertEqualsFlavors(dummyFlavor, result);
        assertNotNull(result.getId());
    }

    @Test
    public void getAllTest() throws Exception {
        when(endpoint.getOperation()).thenReturn(OpenstackConstants.GET_ALL);

        producer.process(exchange);
        List<Flavor> result = msg.getBody(List.class);

        assertTrue(result.size() == 2);
        for (Flavor f : result) {
            assertEqualsFlavors(dummyFlavor, f);
            assertNotNull(f.getId());
        }
    }

    @Test
    public void deleteSuccess() throws Exception {
        when(flavorService.delete(anyString())).thenReturn(ActionResponse.actionSuccess());
        when(endpoint.getOperation()).thenReturn(OpenstackConstants.DELETE);
        String id = "myID";
        msg.setHeader(OpenstackConstants.ID, id);
        producer.process(exchange);

        verify(flavorService).delete(flavorIdCaptor.capture());
        assertEquals(id, flavorIdCaptor.getValue());

        assertFalse(msg.isFault());
        assertNull(msg.getBody());
    }

    @Test
    public void deleteFailure() throws Exception {
        final String failReason = "unknown";
        when(flavorService.delete(anyString())).thenReturn(ActionResponse.actionFailed(failReason, 401));
        when(endpoint.getOperation()).thenReturn(OpenstackConstants.DELETE);
        String id = "myID";
        msg.setHeader(OpenstackConstants.ID, id);
        producer.process(exchange);

        verify(flavorService).delete(flavorIdCaptor.capture());
        assertEquals(id, flavorIdCaptor.getValue());

        assertTrue(msg.isFault());
        assertTrue(msg.getBody(String.class).contains(failReason));
    }

    private Flavor createTestFlavor() {
        FlavorBuilder builder = Builders.flavor()
                .name("dummy flavor")
                .ram(3)
                .vcpus(2)
                .disk(5)
                .swap(2);
        return builder.build();
    }

    private void assertEqualsFlavors(Flavor old, Flavor createdFlavor) {
        assertEquals(old.getName(), createdFlavor.getName());
        assertEquals(old.getRam(), createdFlavor.getRam());
        assertEquals(old.getVcpus(), createdFlavor.getVcpus());
        assertEquals(old.getDisk(), createdFlavor.getDisk());
    }
}
