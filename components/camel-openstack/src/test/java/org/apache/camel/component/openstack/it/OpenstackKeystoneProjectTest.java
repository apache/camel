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

import java.util.Arrays;
import java.util.List;

import org.apache.camel.component.openstack.common.OpenstackConstants;
import org.apache.camel.component.openstack.keystone.KeystoneConstants;
import org.junit.jupiter.api.Test;
import org.openstack4j.api.Builders;
import org.openstack4j.model.identity.v3.Project;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OpenstackKeystoneProjectTest extends OpenstackWiremockTestSupport {

    private static final String URI_FORMAT
            = "openstack-keystone://%s?username=user&password=secret&project=project&operation=%s&subsystem="
              + KeystoneConstants.PROJECTS;

    private static final String PROJECT_NAME = "ProjectX";
    private static final String PROJECT_ID = "3337151a1c38496c8bffcb280b19c346";
    private static final String PROJECT_DOMAIN_ID = "7a71863c2d1d4444b3e6c2cd36955e1e";
    private static final String PROJECT_DESCRIPTION = "Project used for CRUD tests";
    private static final String PROJECT_DESCRIPTION_UPDATED = "An updated project used for CRUD tests";
    private static final String PROJECT_EXTRA_KEY_1 = "extra_key1";
    private static final String PROJECT_EXTRA_VALUE_1 = "value1";
    private static final String PROJECT_EXTRA_KEY_2 = "extra_key2";
    private static final String PROJECT_EXTRA_VALUE_2 = "value2";
    private static final List<String> TAGS = Arrays.asList("one", "two", "three");

    @Test
    void createShouldSucceed() {
        Project in = Builders.project().name(PROJECT_NAME).description(PROJECT_DESCRIPTION).domainId(PROJECT_DOMAIN_ID)
                .setExtra(PROJECT_EXTRA_KEY_1, PROJECT_EXTRA_VALUE_1)
                .enabled(true).setTags(TAGS).build();

        String uri = String.format(URI_FORMAT, url(), OpenstackConstants.CREATE);
        Project out = template.requestBody(uri, in, Project.class);

        assertNotNull(out);
        assertEquals(PROJECT_NAME, out.getName());
        assertEquals(PROJECT_ID, out.getId());
        assertEquals(PROJECT_DOMAIN_ID, out.getDomainId());
        assertEquals(PROJECT_DESCRIPTION, out.getDescription());
        assertEquals(PROJECT_EXTRA_VALUE_1, out.getExtra(PROJECT_EXTRA_KEY_1));
        assertEquals(TAGS, out.getTags());
    }

    @Test
    void getShouldSucceed() {
        String uri = String.format(URI_FORMAT, url(), OpenstackConstants.GET);
        Project out = template.requestBodyAndHeader(uri, null, OpenstackConstants.ID, PROJECT_ID, Project.class);

        assertNotNull(out);
        assertEquals(PROJECT_NAME, out.getName());
        assertEquals(PROJECT_ID, out.getId());
        assertEquals(PROJECT_DOMAIN_ID, out.getDomainId());
        assertEquals(PROJECT_DESCRIPTION, out.getDescription());
    }

    @Test
    void getAllShouldSucceed() {
        String uri = String.format(URI_FORMAT, url(), OpenstackConstants.GET_ALL);
        Project[] projects = template.requestBody(uri, null, Project[].class);

        assertEquals(3, projects.length);
        assertEquals("10b40033bbef48f89fe838fef62398f0", projects[0].getId());
        assertEquals("600905d353a84b20b644d2fe55a21e8a", projects[1].getId());
        assertEquals("8519dba9f4594f0f87071c87784a8d2c", projects[2].getId());
        assertNotNull(projects[2].getOptions());
        assertTrue(projects[2].getOptions().isEmpty());
        assertNotNull(projects[2].getTags());
        assertTrue(projects[2].getTags().isEmpty());
    }

    @Test
    void updateShouldSucceed() {
        Project in = Builders.project().id(PROJECT_ID).description(PROJECT_DESCRIPTION_UPDATED)
                .setExtra(PROJECT_EXTRA_KEY_2, PROJECT_EXTRA_VALUE_2).build();

        String uri = String.format(URI_FORMAT, url(), OpenstackConstants.UPDATE);
        Project out = template.requestBody(uri, in, Project.class);

        assertNotNull(out);
        assertEquals(PROJECT_ID, out.getId());
        assertEquals(PROJECT_NAME, out.getName());
        assertEquals(PROJECT_DOMAIN_ID, out.getDomainId());
        assertEquals(PROJECT_DESCRIPTION_UPDATED, out.getDescription());
        assertEquals(PROJECT_EXTRA_VALUE_1, out.getExtra(PROJECT_EXTRA_KEY_1));
        assertEquals(PROJECT_EXTRA_VALUE_2, out.getExtra(PROJECT_EXTRA_KEY_2));
    }

    @Test
    void deleteShouldSucceed() {
        String uri = String.format(URI_FORMAT, url(), OpenstackConstants.DELETE);
        assertDoesNotThrow(() -> template.requestBodyAndHeader(uri, null, OpenstackConstants.ID, PROJECT_ID));
    }

}
