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
package org.apache.camel.component.openstack.cinder;

import java.util.UUID;

import org.apache.camel.component.openstack.cinder.producer.VolumeProducer;
import org.apache.camel.component.openstack.common.OpenstackConstants;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.openstack4j.api.Builders;
import org.openstack4j.api.storage.BlockVolumeService;
import org.openstack4j.model.common.ActionResponse;
import org.openstack4j.model.storage.block.Volume;
import org.openstack4j.model.storage.block.builder.VolumeBuilder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class VolumeProducerTest extends CinderProducerTestSupport {

    @Mock
    private BlockVolumeService volumeService;

    @Mock
    private Volume testOSVolume;

    @Captor
    private ArgumentCaptor<String> idCaptor;

    @Captor
    private ArgumentCaptor<String> nameCaptor;

    @Captor
    private ArgumentCaptor<String> descCaptor;

    @Captor
    private ArgumentCaptor<String> captor;

    private Volume dummyVolume;

    @Before
    public void setUp() {
        when(blockStorageService.volumes()).thenReturn(volumeService);

        producer = new VolumeProducer(endpoint, client);

        when(volumeService.create(any())).thenReturn(testOSVolume);
        when(volumeService.get(anyString())).thenReturn(testOSVolume);

        dummyVolume = createTestVolume();
        when(testOSVolume.getId()).thenReturn(UUID.randomUUID().toString());
        when(testOSVolume.getName()).thenReturn(dummyVolume.getName());
        when(testOSVolume.getDescription()).thenReturn(dummyVolume.getDescription());
        when(testOSVolume.getImageRef()).thenReturn(dummyVolume.getImageRef());
        when(testOSVolume.getSize()).thenReturn(dummyVolume.getSize());
        when(testOSVolume.getVolumeType()).thenReturn(dummyVolume.getVolumeType());
    }

    @Test
    public void createVolumeTest() throws Exception {
        when(endpoint.getOperation()).thenReturn(OpenstackConstants.CREATE);
        msg.setBody(dummyVolume);
        producer.process(exchange);
        assertEqualVolumes(dummyVolume, msg.getBody(Volume.class));
    }

    @Test
    public void updateVolumeTest() throws Exception {
        when(volumeService.update(anyString(), anyString(), anyString())).thenReturn(ActionResponse.actionSuccess());
        msg.setHeader(OpenstackConstants.OPERATION, OpenstackConstants.UPDATE);
        final String id = "id";
        final String desc = "newDesc";
        final String name = "newName";
        msg.setHeader(OpenstackConstants.ID, id);
        msg.setHeader(OpenstackConstants.DESCRIPTION, desc);
        msg.setHeader(OpenstackConstants.NAME, name);

        producer.process(exchange);

        verify(volumeService).update(idCaptor.capture(), nameCaptor.capture(), descCaptor.capture());

        assertEquals(id, idCaptor.getValue());
        assertEquals(name, nameCaptor.getValue());
        assertEquals(desc, descCaptor.getValue());
        assertNull(msg.getBody());
    }

    @Test
    public void getVolumeTest() throws Exception {
        when(endpoint.getOperation()).thenReturn(OpenstackConstants.GET);
        msg.setHeader(OpenstackConstants.ID, "anyID");
        producer.process(exchange);

        assertEqualVolumes(dummyVolume, msg.getBody(Volume.class));
    }

    @Test
    public void deleteVolumeTest() throws Exception {
        msg.setHeader(OpenstackConstants.OPERATION, OpenstackConstants.DELETE);
        when(volumeService.delete(anyString())).thenReturn(ActionResponse.actionSuccess());
        final String id = "id";
        msg.setHeader(OpenstackConstants.ID, id);

        producer.process(exchange);

        verify(volumeService).delete(captor.capture());
        assertEquals(id, captor.getValue());
    }

    private void assertEqualVolumes(Volume old, Volume newVolume) {
        assertEquals(old.getName(), newVolume.getName());
        assertEquals(old.getDescription(), newVolume.getDescription());
        assertEquals(old.getImageRef(), newVolume.getImageRef());
        assertEquals(old.getSize(), newVolume.getSize());
        assertEquals(old.getVolumeType(), newVolume.getVolumeType());

        assertNotNull(newVolume.getId());
    }

    private Volume createTestVolume() {
        VolumeBuilder builder = Builders.volume()
                .name("name")
                .description("description")
                .imageRef("ref").size(20)
                .volumeType("type");
        return builder.build();
    }
}
