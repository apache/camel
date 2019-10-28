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
import org.apache.camel.component.openstack.neutron.producer.RouterProducer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.openstack4j.api.Builders;
import org.openstack4j.api.networking.RouterService;
import org.openstack4j.model.common.ActionResponse;
import org.openstack4j.model.network.AttachInterfaceType;
import org.openstack4j.model.network.Router;
import org.openstack4j.model.network.RouterInterface;
import org.openstack4j.openstack.networking.domain.NeutronRouterInterface;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class RouterProducerTest extends NeutronProducerTestSupport {

    private Router dummyRouter;

    @Mock
    private Router testOSrouter;

    @Mock
    private RouterService routerService;

    @Captor
    private ArgumentCaptor<Router> routerCaptor;

    @Captor
    private ArgumentCaptor<String> routerIdCaptor;

    @Captor
    private ArgumentCaptor<String> subnetIdCaptor;

    @Captor
    private ArgumentCaptor<String> portIdCaptor;

    @Captor
    private ArgumentCaptor<AttachInterfaceType> itfTypeCaptor;

    @Before
    public void setUp() {
        when(networkingService.router()).thenReturn(routerService);

        producer = new RouterProducer(endpoint, client);
        when(routerService.create(any())).thenReturn(testOSrouter);
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

        verify(routerService).create(routerCaptor.capture());

        assertEqualsRouter(dummyRouter, routerCaptor.getValue());
        assertNotNull(msg.getBody(Router.class).getId());
    }

    @Test
    public void getTest() throws Exception {
        final String routerID = "myRouterID";
        msg.setHeader(OpenstackConstants.OPERATION, OpenstackConstants.GET);
        msg.setHeader(NeutronConstants.ROUTER_ID, routerID);

        producer.process(exchange);

        verify(routerService).get(routerIdCaptor.capture());

        assertEquals(routerID, routerIdCaptor.getValue());
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
        when(routerService.update(any())).thenReturn(tmp);
        dummyRouter.setId(routerID);
        msg.setBody(dummyRouter);

        producer.process(exchange);

        verify(routerService).update(routerCaptor.capture());

        assertEqualsRouter(dummyRouter, routerCaptor.getValue());
        assertNotNull(routerCaptor.getValue().getId());
        assertEquals(newName, msg.getBody(Router.class).getName());
    }

    @Test
    public void deleteTest() throws Exception {
        when(routerService.delete(anyString())).thenReturn(ActionResponse.actionSuccess());
        final String routerID = "myRouterID";
        msg.setHeader(OpenstackConstants.OPERATION, OpenstackConstants.DELETE);
        msg.setHeader(OpenstackConstants.ID, routerID);

        producer.process(exchange);

        verify(routerService).delete(routerIdCaptor.capture());
        assertEquals(routerID, routerIdCaptor.getValue());
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

        verify(routerService).detachInterface(routerIdCaptor.capture(), subnetIdCaptor.capture(), portIdCaptor.capture());

        assertEquals(routerID, routerIdCaptor.getValue());
        assertEquals(subnetId, subnetIdCaptor.getValue());
        assertEquals(portId, portIdCaptor.getValue());

        assertEquals(ifce, msg.getBody(RouterInterface.class));
    }

    @Test
    public void attachTest() throws Exception {
        final String routerID = "myRouterID";
        final String subnetId = "subnet";
        final RouterInterface ifce = new NeutronRouterInterface(subnetId, null);
        when(routerService.attachInterface(anyString(), any(), anyString())).thenReturn(ifce);

        msg.setHeader(OpenstackConstants.OPERATION, NeutronConstants.ATTACH_INTERFACE);
        msg.setHeader(NeutronConstants.ROUTER_ID, routerID);
        msg.setHeader(NeutronConstants.SUBNET_ID, subnetId);
        msg.setHeader(NeutronConstants.ITERFACE_TYPE, AttachInterfaceType.SUBNET);

        producer.process(exchange);

        verify(routerService).attachInterface(routerIdCaptor.capture(), itfTypeCaptor.capture(), subnetIdCaptor.capture());

        assertEquals(routerID, routerIdCaptor.getValue());
        assertEquals(AttachInterfaceType.SUBNET, itfTypeCaptor.getValue());
        assertEquals(subnetId, subnetIdCaptor.getValue());

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
