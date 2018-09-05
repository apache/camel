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
package org.apache.camel.component.openstack.cinder;

import java.util.UUID;

import org.apache.camel.component.openstack.cinder.producer.SnapshotProducer;
import org.apache.camel.component.openstack.common.OpenstackConstants;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.openstack4j.api.Builders;
import org.openstack4j.api.storage.BlockVolumeSnapshotService;
import org.openstack4j.model.common.ActionResponse;
import org.openstack4j.model.storage.block.VolumeSnapshot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class VolumeSnapshotProducerTest extends CinderProducerTestSupport {

    @Mock
    private BlockVolumeSnapshotService snapshotService;

    @Mock
    private VolumeSnapshot testOSVolumeSnapshot;

    @Captor
    private ArgumentCaptor<String> idCaptor;

    @Captor
    private ArgumentCaptor<String> nameCaptor;

    @Captor
    private ArgumentCaptor<String> descCaptor;

    @Captor
    private ArgumentCaptor<String> captor;

    private VolumeSnapshot dummyVolumeSnapshot;

    @Before
    public void setUp() {
        when(blockStorageService.snapshots()).thenReturn(snapshotService);
        
        producer = new SnapshotProducer(endpoint, client);

        when(snapshotService.create(any())).thenReturn(testOSVolumeSnapshot);
        when(snapshotService.get(anyString())).thenReturn(testOSVolumeSnapshot);

        dummyVolumeSnapshot = createTestVolume();
        when(testOSVolumeSnapshot.getId()).thenReturn(UUID.randomUUID().toString());
        when(testOSVolumeSnapshot.getName()).thenReturn(dummyVolumeSnapshot.getName());
        when(testOSVolumeSnapshot.getDescription()).thenReturn(dummyVolumeSnapshot.getDescription());
        when(testOSVolumeSnapshot.getVolumeId()).thenReturn(dummyVolumeSnapshot.getVolumeId());
    }

    @Test
    public void createVolumeTest() throws Exception {
        when(endpoint.getOperation()).thenReturn(OpenstackConstants.CREATE);
        msg.setBody(dummyVolumeSnapshot);
        producer.process(exchange);
        final VolumeSnapshot result = msg.getBody(VolumeSnapshot.class);
        assertEqualsVolumeSnapshots(dummyVolumeSnapshot, result);
        assertNotNull(result.getId());
    }

    @Test
    public void updateVolumeSnapshotTest() throws Exception {
        when(snapshotService.update(anyString(), anyString(), anyString())).thenReturn(ActionResponse.actionSuccess());
        msg.setHeader(OpenstackConstants.OPERATION, OpenstackConstants.UPDATE);
        final String id = "id";
        final String desc = "newDesc";
        final String name = "newName";
        msg.setHeader(OpenstackConstants.ID, id);
        msg.setHeader(OpenstackConstants.DESCRIPTION, desc);
        msg.setHeader(OpenstackConstants.NAME, name);

        producer.process(exchange);

        verify(snapshotService).update(idCaptor.capture(), nameCaptor.capture(), descCaptor.capture());

        assertEquals(id, idCaptor.getValue());
        assertEquals(name, nameCaptor.getValue());
        assertEquals(desc, descCaptor.getValue());
        assertFalse(msg.isFault());
        assertNull(msg.getBody());
    }


    @Test
    public void getVolumeSnapshotTest() throws Exception {
        when(endpoint.getOperation()).thenReturn(OpenstackConstants.GET);
        msg.setHeader(OpenstackConstants.ID, "anyID");
        producer.process(exchange);

        assertEqualsVolumeSnapshots(dummyVolumeSnapshot, msg.getBody(VolumeSnapshot.class));
    }

    @Test
    public void deleteVolumeSnapshotTest() throws Exception {
        msg.setHeader(OpenstackConstants.OPERATION, OpenstackConstants.DELETE);
        when(snapshotService.delete(anyString())).thenReturn(ActionResponse.actionSuccess());
        final String id = "id";
        msg.setHeader(OpenstackConstants.ID, id);

        producer.process(exchange);

        verify(snapshotService).delete(captor.capture());
        assertEquals(id, captor.getValue());
        assertFalse(msg.isFault());
    }

    private void assertEqualsVolumeSnapshots(VolumeSnapshot old, VolumeSnapshot newVolumeSn) {
        assertEquals(old.getName(), newVolumeSn.getName());
        assertEquals(old.getDescription(), newVolumeSn.getDescription());
        assertEquals(old.getVolumeId(), newVolumeSn.getVolumeId());
    }

    private VolumeSnapshot createTestVolume() {
        return Builders.volumeSnapshot()
                .description("descr")
                .name("name")
                .volume("volId")
                .build();
    }
}
