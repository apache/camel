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
package org.apache.camel.component.openstack.keystone;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.component.openstack.common.OpenstackConstants;
import org.apache.camel.component.openstack.keystone.producer.RegionProducer;
import org.apache.camel.component.openstack.neutron.NeutronConstants;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.openstack4j.api.Builders;
import org.openstack4j.model.common.ActionResponse;
import org.openstack4j.model.identity.v3.Region;
import org.openstack4j.model.network.Network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RegionProducerTest extends KeystoneProducerTestSupport {

    private Region dummyRegion;

    @Mock
    private Region testOSregion;

    @Before
    public void setUp() {
        producer = new RegionProducer(endpoint, client);

        when(regionService.create(any(Region.class))).thenReturn(testOSregion);
        when(regionService.get(anyString())).thenReturn(testOSregion);

        List<Region> getAllList = new ArrayList<>();
        getAllList.add(testOSregion);
        getAllList.add(testOSregion);
        doReturn(getAllList).when(regionService).list();

        dummyRegion = createRegion();

        when(testOSregion.getDescription()).thenReturn(dummyRegion.getDescription());
    }

    @Test
    public void createTest() throws Exception {
        msg.setHeader(OpenstackConstants.OPERATION, OpenstackConstants.CREATE);
        msg.setHeader(KeystoneConstants.DESCRIPTION, dummyRegion.getDescription());

        producer.process(exchange);

        ArgumentCaptor<Region> captor = ArgumentCaptor.forClass(Region.class);
        verify(regionService).create(captor.capture());

        assertEqualsRegion(dummyRegion, captor.getValue());
    }

    @Test
    public void getTest() throws Exception {
        final String id = "id";
        msg.setHeader(OpenstackConstants.OPERATION, OpenstackConstants.GET);
        msg.setHeader(OpenstackConstants.ID, id);

        producer.process(exchange);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(regionService).get(captor.capture());

        assertEquals(id, captor.getValue());
        assertEqualsRegion(testOSregion, msg.getBody(Region.class));
    }

    @Test
    public void getAllTest() throws Exception {
        msg.setHeader(OpenstackConstants.OPERATION, OpenstackConstants.GET_ALL);

        producer.process(exchange);

        final List<Network> result = msg.getBody(List.class);
        assertTrue(result.size() == 2);
        assertEquals(testOSregion, result.get(0));
    }

    @Test
    public void updateTest() throws Exception {
        final String id = "myID";
        msg.setHeader(OpenstackConstants.OPERATION, OpenstackConstants.UPDATE);

        when(testOSregion.getId()).thenReturn(id);
        final String newDescription = "ndesc";
        when(testOSregion.getDescription()).thenReturn(newDescription);

        when(regionService.update(any(Region.class))).thenReturn(testOSregion);
        msg.setBody(testOSregion);

        producer.process(exchange);

        ArgumentCaptor<Region> captor = ArgumentCaptor.forClass(Region.class);
        verify(regionService).update(captor.capture());

        assertEqualsRegion(testOSregion, captor.getValue());
        assertNotNull(captor.getValue().getId());
        assertEquals(newDescription, msg.getBody(Region.class).getDescription());
    }

    @Test
    public void deleteTest() throws Exception {
        when(regionService.delete(anyString())).thenReturn(ActionResponse.actionSuccess());
        final String networkID = "myID";
        msg.setHeader(OpenstackConstants.OPERATION, OpenstackConstants.DELETE);
        msg.setHeader(OpenstackConstants.ID, networkID);

        producer.process(exchange);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(regionService).delete(captor.capture());
        assertEquals(networkID, captor.getValue());
        assertFalse(msg.isFault());

        //in case of failure
        final String failureMessage = "fail";
        when(regionService.delete(anyString())).thenReturn(ActionResponse.actionFailed(failureMessage, 404));
        producer.process(exchange);
        assertTrue(msg.isFault());
        assertTrue(msg.getBody(String.class).contains(failureMessage));
    }

    private void assertEqualsRegion(Region old, Region newRegion) {
        assertEquals(old.getDescription(), newRegion.getDescription());
    }

    private Region createRegion() {
        return Builders.region()
                .description("desc")
                .build();
    }

}
