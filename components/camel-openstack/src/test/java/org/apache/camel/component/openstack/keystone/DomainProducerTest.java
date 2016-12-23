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
import org.apache.camel.component.openstack.keystone.producer.DomainProducer;
import org.apache.camel.component.openstack.neutron.NeutronConstants;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.openstack4j.api.Builders;
import org.openstack4j.model.common.ActionResponse;
import org.openstack4j.model.identity.v3.Domain;
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

public class DomainProducerTest extends KeystoneProducerTestSupport {

    private Domain dummyDomain;

    @Mock
    private Domain testOSdomain;

    @Before
    public void setUp() {
        producer = new DomainProducer(endpoint, client);

        when(domainService.create(any(Domain.class))).thenReturn(testOSdomain);
        when(domainService.get(anyString())).thenReturn(testOSdomain);

        List<Domain> getAllList = new ArrayList<>();
        getAllList.add(testOSdomain);
        getAllList.add(testOSdomain);
        doReturn(getAllList).when(domainService).list();

        dummyDomain = createDomain();

        when(testOSdomain.getName()).thenReturn(dummyDomain.getName());
        when(testOSdomain.getDescription()).thenReturn(dummyDomain.getDescription());
    }

    @Test
    public void createTest() throws Exception {
        msg.setHeader(OpenstackConstants.OPERATION, OpenstackConstants.CREATE);
        msg.setHeader(OpenstackConstants.NAME, dummyDomain.getName());
        msg.setHeader(KeystoneConstants.DESCRIPTION, dummyDomain.getDescription());


        producer.process(exchange);

        ArgumentCaptor<Domain> captor = ArgumentCaptor.forClass(Domain.class);
        verify(domainService).create(captor.capture());

        assertEqualsDomain(dummyDomain, captor.getValue());
    }

    @Test
    public void getTest() throws Exception {
        final String id = "id";
        msg.setHeader(OpenstackConstants.OPERATION, OpenstackConstants.GET);
        msg.setHeader(OpenstackConstants.ID, id);

        producer.process(exchange);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(domainService).get(captor.capture());

        assertEquals(id, captor.getValue());
        assertEqualsDomain(testOSdomain, msg.getBody(Domain.class));
    }

    @Test
    public void getAllTest() throws Exception {
        msg.setHeader(OpenstackConstants.OPERATION, OpenstackConstants.GET_ALL);

        producer.process(exchange);

        final List<Network> result = msg.getBody(List.class);
        assertTrue(result.size() == 2);
        assertEquals(testOSdomain, result.get(0));
    }

    @Test
    public void updateTest() throws Exception {
        final String id = "myID";
        msg.setHeader(OpenstackConstants.OPERATION, OpenstackConstants.UPDATE);
        final String newName = "newName";

        when(testOSdomain.getId()).thenReturn(id);
        when(testOSdomain.getName()).thenReturn(newName);
        when(testOSdomain.getDescription()).thenReturn("desc");

        when(domainService.update(any(Domain.class))).thenReturn(testOSdomain);
        msg.setBody(testOSdomain);

        producer.process(exchange);

        ArgumentCaptor<Domain> captor = ArgumentCaptor.forClass(Domain.class);
        verify(domainService).update(captor.capture());

        assertEqualsDomain(testOSdomain, captor.getValue());
        assertNotNull(captor.getValue().getId());
        assertEquals(newName, msg.getBody(Domain.class).getName());
    }

    @Test
    public void deleteTest() throws Exception {
        when(domainService.delete(anyString())).thenReturn(ActionResponse.actionSuccess());
        final String networkID = "myID";
        msg.setHeader(OpenstackConstants.OPERATION, OpenstackConstants.DELETE);
        msg.setHeader(OpenstackConstants.ID, networkID);

        producer.process(exchange);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(domainService).delete(captor.capture());
        assertEquals(networkID, captor.getValue());
        assertFalse(msg.isFault());

        //in case of failure
        final String failureMessage = "fail";
        when(domainService.delete(anyString())).thenReturn(ActionResponse.actionFailed(failureMessage, 404));
        producer.process(exchange);
        assertTrue(msg.isFault());
        assertTrue(msg.getBody(String.class).contains(failureMessage));
    }

    private void assertEqualsDomain(Domain old, Domain newDomain) {
        assertEquals(old.getName(), newDomain.getName());
        assertEquals(old.getDescription(), newDomain.getDescription());
    }

    private Domain createDomain() {
        return Builders.domain()
                .description("desc")
                .name("domain Name").build();
    }

}
