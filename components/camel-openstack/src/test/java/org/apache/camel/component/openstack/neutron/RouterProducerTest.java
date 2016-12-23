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
package org.apache.camel.component.openstack.neutron;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.camel.component.openstack.common.OpenstackConstants;
import org.apache.camel.component.openstack.neutron.producer.RouterProducer;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.openstack4j.api.Builders;
import org.openstack4j.model.common.ActionResponse;
import org.openstack4j.model.network.AttachInterfaceType;
import org.openstack4j.model.network.Router;
import org.openstack4j.model.network.RouterInterface;
import org.openstack4j.openstack.networking.domain.NeutronRouterInterface;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RouterProducerTest extends NeutronProducerTestSupport {

    private Router dummyRouter;

    @Mock
    private Router testOSrouter;

    @Before
    public void setUp() {
        producer = new RouterProducer(endpoint, client);
        when(routerService.create(any(Router.class))).thenReturn(testOSrouter);
        when(routerService.get(anyString())).thenReturn(testOSrouter);

        List<Router> getAllList = new ArrayList<>();
        getAllList.add(testOSrouter);
        getAllList.add(testOSrouter);
        doReturn(getAllList).when(routerService).list();

        dummyRouter = createRouter();
        when(testOSrouter.getName()).thenReturn(dummyRouter.getName());
        when(testOSrouter.getTenantId()).thenReturn(dummyRouter.getTenantId());
        when(testOSrouter.getId()).thenReturn(UUID.randomUUID().toString());
    }

    @Test
    public void createTest() throws Exception {
        msg.setHeader(OpenstackConstants.OPERATION, OpenstackConstants.CREATE);
        msg.setHeader(OpenstackConstants.NAME, dummyRouter.getName());
        msg.setHeader(NeutronConstants.TENANT_ID, dummyRouter.getTenantId());

        producer.process(exchange);

        ArgumentCaptor<Router> captor = ArgumentCaptor.forClass(Router.class);
        verify(routerService).create(captor.capture());

        assertEqualsRouter(dummyRouter, captor.getValue());
        assertNotNull(msg.getBody(Router.class).getId());
    }

    @Test
    public void getTest() throws Exception {
        final String routerID = "myRouterID";
        msg.setHeader(OpenstackConstants.OPERATION, OpenstackConstants.GET);
        msg.setHeader(NeutronConstants.ROUTER_ID, routerID);

        producer.process(exchange);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(routerService).get(captor.capture());

        assertEquals(routerID, captor.getValue());
        assertEqualsRouter(testOSrouter, msg.getBody(Router.class));
    }

    @Test
    public void getAllTest() throws Exception {
        msg.setHeader(OpenstackConstants.OPERATION, OpenstackConstants.GET_ALL);

        producer.process(exchange);

        final List<Router> result = msg.getBody(List.class);
        assertTrue(result.size() == 2);
        assertEquals(testOSrouter, result.get(0));
    }

    @Test
    public void updateTest() throws Exception {
        final String routerID = "myRouterID";
        msg.setHeader(OpenstackConstants.OPERATION, OpenstackConstants.UPDATE);
        final Router tmp = createRouter();
        final String newName = "newName";
        tmp.setName(newName);
        when(routerService.update(any(Router.class))).thenReturn(tmp);
        dummyRouter.setId(routerID);
        msg.setBody(dummyRouter);

        producer.process(exchange);

        ArgumentCaptor<Router> captor = ArgumentCaptor.forClass(Router.class);
        verify(routerService).update(captor.capture());

        assertEqualsRouter(dummyRouter, captor.getValue());
        assertNotNull(captor.getValue().getId());
        assertEquals(newName, msg.getBody(Router.class).getName());
    }

    @Test
    public void deleteTest() throws Exception {
        when(routerService.delete(anyString())).thenReturn(ActionResponse.actionSuccess());
        final String routerID = "myRouterID";
        msg.setHeader(OpenstackConstants.OPERATION, OpenstackConstants.DELETE);
        msg.setHeader(OpenstackConstants.ID, routerID);

        producer.process(exchange);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(routerService).delete(captor.capture());
        assertEquals(routerID, captor.getValue());
        assertFalse(msg.isFault());

        //in case of failure
        final String failureMessage = "fail";
        when(routerService.delete(anyString())).thenReturn(ActionResponse.actionFailed(failureMessage, 404));
        producer.process(exchange);
        assertTrue(msg.isFault());
        assertTrue(msg.getBody(String.class).contains(failureMessage));
    }

    @Test
    public void detachTest() throws Exception {
        final String routerID = "myRouterID";
        final String portId = "port";
        final String subnetId = "subnet";
        final RouterInterface ifce = new NeutronRouterInterface(subnetId, portId);
        when(routerService.detachInterface(anyString(), anyString(), anyString())).thenReturn(ifce);

        msg.setHeader(OpenstackConstants.OPERATION, NeutronConstants.DETACH_INTERFACE);
        msg.setHeader(NeutronConstants.ROUTER_ID, routerID);
        msg.setHeader(NeutronConstants.SUBNET_ID, subnetId);
        msg.setHeader(NeutronConstants.PORT_ID, portId);

        producer.process(exchange);

        ArgumentCaptor<String> routerC = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> portC = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> subnetC = ArgumentCaptor.forClass(String.class);
        verify(routerService).detachInterface(routerC.capture(), subnetC.capture(), portC.capture());

        assertEquals(routerID, routerC.getValue());
        assertEquals(subnetId, subnetC.getValue());
        assertEquals(portId, portC.getValue());

        assertEquals(ifce, msg.getBody(RouterInterface.class));
    }

    @Test
    public void attachTest() throws Exception {
        final String routerID = "myRouterID";
        final String subnetId = "subnet";
        final RouterInterface ifce = new NeutronRouterInterface(subnetId, null);
        when(routerService.attachInterface(anyString(), any(AttachInterfaceType.class), anyString())).thenReturn(ifce);

        msg.setHeader(OpenstackConstants.OPERATION, NeutronConstants.ATTACH_INTERFACE);
        msg.setHeader(NeutronConstants.ROUTER_ID, routerID);
        msg.setHeader(NeutronConstants.SUBNET_ID, subnetId);
        msg.setHeader(NeutronConstants.ITERFACE_TYPE, AttachInterfaceType.SUBNET);

        producer.process(exchange);

        ArgumentCaptor<String> routerC = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<AttachInterfaceType> itfType = ArgumentCaptor.forClass(AttachInterfaceType.class);
        ArgumentCaptor<String> subnetC = ArgumentCaptor.forClass(String.class);
        verify(routerService).attachInterface(routerC.capture(), itfType.capture(), subnetC.capture());

        assertEquals(routerID, routerC.getValue());
        assertEquals(AttachInterfaceType.SUBNET, itfType.getValue());
        assertEquals(subnetId, subnetC.getValue());

        assertEquals(ifce, msg.getBody(RouterInterface.class));
    }

    private Router createRouter() {
        return Builders.router()
                .name("name")
                .tenantId("tenantID")
                .build();
    }

    private void assertEqualsRouter(Router old, Router newRouter) {
        assertEquals(old.getName(), newRouter.getName());
        assertEquals(old.getTenantId(), newRouter.getTenantId());
    }
}
