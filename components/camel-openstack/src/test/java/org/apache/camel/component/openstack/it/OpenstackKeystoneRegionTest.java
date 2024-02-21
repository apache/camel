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

import org.apache.camel.component.openstack.common.OpenstackConstants;
import org.apache.camel.component.openstack.keystone.KeystoneConstants;
import org.junit.jupiter.api.Test;
import org.openstack4j.api.Builders;
import org.openstack4j.model.identity.v3.Region;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class OpenstackKeystoneRegionTest extends OpenstackWiremockTestSupport {

    private static final String URI_FORMAT
            = "openstack-keystone://%s?username=user&password=secret&project=project&operation=%s&subsystem="
              + KeystoneConstants.REGIONS;

    private static final String REGION_ID = "Region_CRUD";
    private static final String REGION_PARENTREGIONID = "RegionOne";
    private static final String REGION_DESCRIPTION = "No description provided.";
    private static final String REGION_DESCRIPTION_UPDATED = "A updated region used for CRUD tests.";

    @Test
    void createShouldSucceed() {
        Region in
                = Builders.region().id(REGION_ID).description(REGION_DESCRIPTION).parentRegionId(REGION_PARENTREGIONID).build();

        String uri = String.format(URI_FORMAT, url(), OpenstackConstants.CREATE);
        Region out = template.requestBody(uri, in, Region.class);

        assertNotNull(out);
        assertEquals(REGION_ID, out.getId());
        assertEquals(REGION_DESCRIPTION, out.getDescription());
        assertEquals(REGION_PARENTREGIONID, out.getParentRegionId());
    }

    @Test
    void getShouldSucceed() {
        String uri = String.format(URI_FORMAT, url(), OpenstackConstants.GET);
        Region out = template.requestBodyAndHeader(uri, null, OpenstackConstants.ID, REGION_ID, Region.class);

        assertNotNull(out);
        assertEquals(REGION_ID, out.getId());
        assertEquals(REGION_DESCRIPTION, out.getDescription());
        assertEquals(REGION_PARENTREGIONID, out.getParentRegionId());
    }

    @Test
    void getAllShouldSucceed() {
        String uri = String.format(URI_FORMAT, url(), OpenstackConstants.GET_ALL);
        Region[] regions = template.requestBody(uri, null, Region[].class);

        assertEquals(2, regions.length);
        assertEquals(REGION_PARENTREGIONID, regions[0].getId());
        assertNull(regions[0].getParentRegionId());
        assertEquals(REGION_ID, regions[1].getId());
        assertEquals(REGION_PARENTREGIONID, regions[1].getParentRegionId());
    }

    @Test
    void updateShouldSucceed() {
        Region in = Builders.region().id(REGION_ID).description(REGION_DESCRIPTION_UPDATED).build();

        String uri = String.format(URI_FORMAT, url(), OpenstackConstants.UPDATE);
        Region out = template.requestBody(uri, in, Region.class);

        assertNotNull(out);
        assertEquals(REGION_ID, out.getId());
        assertEquals(REGION_DESCRIPTION_UPDATED, out.getDescription());
        assertEquals(REGION_PARENTREGIONID, out.getParentRegionId());
    }

    @Test
    void deleteShouldSucceed() {
        String uri = String.format(URI_FORMAT, url(), OpenstackConstants.DELETE);
        assertDoesNotThrow(() -> template.requestBodyAndHeader(uri, null, OpenstackConstants.ID, REGION_ID));
    }

    @Test
    void getUnknownRegionShouldReturnNull() {
        String uri = String.format(URI_FORMAT, url(), OpenstackConstants.GET);
        Region out = template.requestBodyAndHeader(uri, null, OpenstackConstants.ID, "nonExistentRegionId", Region.class);

        assertNull(out);
    }
}
