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

import org.apache.camel.component.openstack.cinder.CinderConstants;
import org.apache.camel.component.openstack.common.OpenstackConstants;
import org.junit.jupiter.api.Test;
import org.openstack4j.model.storage.block.VolumeSnapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OpenstackCinderSnapshotTest extends OpenstackWiremockTestSupport {

    private static final String URI_FORMAT
            = "openstack-cinder://%s?username=user&password=secret&project=project&operation=%s&subsystem="
              + CinderConstants.SNAPSHOTS;

    @Test
    void getAllShouldSucceed() {
        String uri = String.format(URI_FORMAT, url(), OpenstackConstants.GET_ALL);
        VolumeSnapshot[] volumeSnapshots = template.requestBody(uri, null, VolumeSnapshot[].class);

        assertEquals(2, volumeSnapshots.length);
        assertEquals("a06b0531-c14b-4a7b-8749-de1378dd1007", volumeSnapshots[0].getId());
        assertEquals("b0e394e6-bb10-4bfe-960d-edf72100c810", volumeSnapshots[0].getVolumeId());
        assertNotNull(volumeSnapshots[0].getMetaData());
        assertTrue(volumeSnapshots[0].getMetaData().isEmpty());
        assertEquals("6489c55f-b9f4-442e-8d0a-5a87349d2d07", volumeSnapshots[1].getId());
        assertEquals("7f47ab73-303c-4a19-b311-6123bb115775", volumeSnapshots[1].getVolumeId());
    }

}
