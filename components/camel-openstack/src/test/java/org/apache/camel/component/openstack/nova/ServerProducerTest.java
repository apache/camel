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

import java.util.UUID;

import org.apache.camel.component.openstack.common.OpenstackConstants;
import org.apache.camel.component.openstack.nova.producer.ServerProducer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.openstack4j.api.Builders;
import org.openstack4j.api.compute.ServerService;
import org.openstack4j.model.common.ActionResponse;
import org.openstack4j.model.compute.Action;
import org.openstack4j.model.compute.Server;
import org.openstack4j.model.compute.ServerCreate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ServerProducerTest extends NovaProducerTestSupport {

    @Mock
    private org.openstack4j.model.compute.Server testOSServer;

    @Mock
    private ServerService serverService;

    @Captor
    private ArgumentCaptor<Action> actionArgumentCaptor;

    @Captor
    private ArgumentCaptor<String> idArgumentCaptor;

    @Captor
    private ArgumentCaptor<String> snapshot;

    @Captor
    private ArgumentCaptor<String> idCaptor;

    private ServerCreate dummyServer;

    @Before
    public void setUp() {
        when(computeService.servers()).thenReturn(serverService);

        producer = new ServerProducer(endpoint, client);

        when(serverService.boot(any())).thenReturn(testOSServer);

        dummyServer = createDummyServer();
        initServerMock();
    }

    @Test
    public void createServer() throws Exception {
        when(endpoint.getOperation()).thenReturn(OpenstackConstants.CREATE);
        final String expectedFlavorID = UUID.randomUUID().toString();
        when(testOSServer.getId()).thenReturn(expectedFlavorID);
        msg.setBody(dummyServer);
        producer.process(exchange);
        final Server created = msg.getBody(Server.class);
        checkCreatedServer(dummyServer, created);
    }

    @Test
    public void createServerWithHeaders() throws Exception {
        final String expectedFlavorID = UUID.randomUUID().toString();
        when(testOSServer.getId()).thenReturn(expectedFlavorID);

        msg.setHeader(OpenstackConstants.OPERATION, OpenstackConstants.CREATE);
        msg.setHeader(OpenstackConstants.NAME, dummyServer.getName());
        msg.setHeader(NovaConstants.FLAVOR_ID, dummyServer.getFlavorRef());
        msg.setHeader(NovaConstants.IMAGE_ID, dummyServer.getImageRef());

        producer.process(exchange);

        final Server created = msg.getBody(Server.class);

        checkCreatedServer(dummyServer, created);
    }

    @Test
    public void serverAction() throws Exception {
        when(serverService.action(anyString(), any())).thenReturn(ActionResponse.actionSuccess());
        when(endpoint.getOperation()).thenReturn(NovaConstants.ACTION);
        String id = "myID";
        msg.setHeader(NovaConstants.ACTION, Action.PAUSE);
        msg.setHeader(OpenstackConstants.ID, id);
        producer.process(exchange);

        verify(serverService).action(idArgumentCaptor.capture(), actionArgumentCaptor.capture());

        assertEquals(id, idArgumentCaptor.getValue());
        assertTrue(actionArgumentCaptor.getValue() == Action.PAUSE);
        assertFalse(msg.isFault());
        assertNull(msg.getBody());

        //test fail
        final String failReason = "fr";
        when(serverService.action(anyString(), any())).thenReturn(ActionResponse.actionFailed(failReason, 401));
        producer.process(exchange);
        assertTrue(msg.isFault());
        assertTrue(msg.getBody(String.class).contains(failReason));
    }

    @Test
    public void createSnapshot() throws Exception {
        String id = "myID";
        String snapshotName = "mySnapshot";

        msg.setHeader(OpenstackConstants.OPERATION, NovaConstants.CREATE_SNAPSHOT);
        msg.setHeader(OpenstackConstants.NAME, snapshotName);
        msg.setHeader(OpenstackConstants.ID, id);
        producer.process(exchange);

        verify(serverService).createSnapshot(idCaptor.capture(), snapshot.capture());

        assertEquals(id, idCaptor.getValue());
        assertEquals(snapshotName, snapshot.getValue());
    }

    private void initServerMock() {
        when(testOSServer.getId()).thenReturn(UUID.randomUUID().toString());
        when(testOSServer.getName()).thenReturn(dummyServer.getName());
        when(testOSServer.getFlavorId()).thenReturn(dummyServer.getFlavorRef());
        when(testOSServer.getImageId()).thenReturn(dummyServer.getImageRef());
    }

    private ServerCreate createDummyServer() {
        return Builders.server()
                .name("MyCoolServer")
                .flavor("flavorID")
                .image("imageID").build();
    }

    private void checkCreatedServer(ServerCreate old, Server created) {
        assertEquals(old.getName(), created.getName());
        assertEquals(old.getFlavorRef(), created.getFlavorId());
        assertEquals(old.getImageRef(), created.getImageId());

        assertNotNull(created.getId());
    }
}
