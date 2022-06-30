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
package org.apache.camel.component.openstack.it;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.component.openstack.cinder.CinderConstants;
import org.apache.camel.component.openstack.common.OpenstackConstants;
import org.junit.jupiter.api.Test;
import org.openstack4j.api.Builders;
import org.openstack4j.model.storage.block.Volume;
import org.openstack4j.model.storage.block.VolumeAttachment;
import org.openstack4j.model.storage.block.VolumeType;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class OpenstackCinderVolumeTest extends OpenstackWiremockTestSupport {

    private static final String URI_FORMAT
            = "openstack-cinder://%s?username=user&password=secret&project=project&operation=%s&subsystem="
              + CinderConstants.VOLUMES;

    @Test
    void createShouldSucceed() {
        Volume in = Builders.volume().size(10).name("test_openstack4j").description("test").multiattach(true).build();

        String uri = String.format(URI_FORMAT, url(), OpenstackConstants.CREATE);
        Volume out = template.requestBody(uri, in, Volume.class);

        assertEquals(10, out.getSize());
        assertEquals(Boolean.TRUE, out.multiattach());
    }

    @Test
    void getShouldSucceed() {
        String uri = String.format(URI_FORMAT, url(), OpenstackConstants.GET);
        String id = "8a9287b7-4f4d-4213-8d75-63470f19f27c";
        Volume out = template.requestBodyAndHeader(uri, null, CinderConstants.VOLUME_ID, id, Volume.class);

        assertEquals(id, out.getId());
        assertEquals("test-volume", out.getName());
        assertEquals("a description", out.getDescription());
        assertNotNull(out.getCreated());
        assertEquals("nova", out.getZone());
        assertEquals(100, out.getSize());
        assertEquals(Volume.Status.IN_USE, out.getStatus());
        assertEquals("22222222-2222-2222-2222-222222222222", out.getSnapshotId());
        assertEquals("11111111-1111-1111-1111-111111111111", out.getSourceVolid());
        assertEquals("Gold", out.getVolumeType());

        assertNotNull(out.getMetaData());
        Map<String, String> metadata = out.getMetaData();
        assertEquals("False", metadata.get("readonly"));
        assertEquals("rw", metadata.get("attached_mode"));

        assertNotNull(out.getAttachments());
        List<? extends VolumeAttachment> attachments = out.getAttachments();
        assertEquals(1, attachments.size());
        assertEquals("/dev/vdd", attachments.get(0).getDevice());
        assertEquals("myhost", attachments.get(0).getHostname());
        assertEquals("8a9287b7-4f4d-4213-8d75-63470f19f27c", attachments.get(0).getId());
        assertEquals("eaa6a54d-35c1-40ce-831d-bb61f991e1a9", attachments.get(0).getServerId());
        assertEquals("8a9287b7-4f4d-4213-8d75-63470f19f27c", attachments.get(0).getVolumeId());
    }

    @Test
    void getAllShouldSucceed() {
        String uri = String.format(URI_FORMAT, url(), OpenstackConstants.GET_ALL);
        Volume[] volumes = template.requestBody(uri, null, Volume[].class);

        assertEquals(3, volumes.length);
        assertEquals("b0b5ed7ae06049688349fe43737796d4", volumes[0].getTenantId());
    }

    @Test
    void getAllTypesShouldSucceed() {
        String uri = String.format(URI_FORMAT, url(), CinderConstants.GET_ALL_TYPES);
        VolumeType[] volumeTypes = template.requestBody(uri, null, VolumeType[].class);

        assertEquals(2, volumeTypes.length);
        assertEquals("6a65bc1b-197b-45bf-8056-9695dc82191f", volumeTypes[0].getId());
        assertEquals("testVolume1", volumeTypes[0].getName());
        assertNotNull(volumeTypes[0].getExtraSpecs());
        assertEquals("gpu", volumeTypes[0].getExtraSpecs().get("capabilities"));
        assertEquals("10f00bb7-46d8-4f3f-b89b-702693a3dcdc", volumeTypes[1].getId());
        assertEquals("testVolume2", volumeTypes[1].getName());
        assertNotNull(volumeTypes[1].getExtraSpecs());
        assertEquals("gpu", volumeTypes[1].getExtraSpecs().get("capabilities"));
    }

    @Test
    void updateShouldSucceed() {
        Map<String, Object> headers = new HashMap<>();
        headers.put(CinderConstants.VOLUME_ID, "fffab33e-38e8-4626-9fee-fe90f240ff0f");
        headers.put(OpenstackConstants.NAME, "name");
        headers.put(OpenstackConstants.DESCRIPTION, "description");
        headers.put(CinderConstants.SIZE, 1024);
        headers.put(CinderConstants.VOLUME_TYPE, "volume-type");
        headers.put(CinderConstants.IMAGE_REF, "image-ref");
        headers.put(CinderConstants.SNAPSHOT_ID, "snaphot-id");
        headers.put(CinderConstants.IS_BOOTABLE, false);

        String uri = String.format(URI_FORMAT, url(), OpenstackConstants.UPDATE);
        assertDoesNotThrow(() -> template.requestBodyAndHeaders(uri, null, headers));
    }

    @Test
    void deleteShouldSucceed() {
        String uri = String.format(URI_FORMAT, url(), OpenstackConstants.DELETE);
        assertDoesNotThrow(() -> template.requestBodyAndHeader(uri, null, CinderConstants.VOLUME_ID,
                "fffab33e-38e8-4626-9fee-fe90f240ff0f"));
    }
}
