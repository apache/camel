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
import org.openstack4j.model.identity.v3.Domain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OpenstackKeystoneDomainTest extends OpenstackWiremockTestSupport {

    private static final String URI_FORMAT
            = "openstack-keystone://%s?username=user&password=secret&project=project&operation=%s&subsystem="
              + KeystoneConstants.DOMAINS;

    private static final String DOMAIN_NAME = "Domain_CRUD";
    private static final String DOMAIN_ID = "98c110ae41c249189c9d5c25d8377b65";
    private static final String DOMAIN_DESCRIPTION = "Domain used for CRUD tests";
    private static final String DOMAIN_DESCRIPTION_UPDATED = "An updated domain used for CRUD tests";

    @Test
    void createShouldSucceed() {
        Domain in = Builders.domain().name(DOMAIN_NAME).description(DOMAIN_DESCRIPTION).enabled(true).build();

        String uri = String.format(URI_FORMAT, url(), OpenstackConstants.CREATE);
        Domain out = template.requestBody(uri, in, Domain.class);

        assertNotNull(out);
        assertEquals(DOMAIN_NAME, out.getName());
        assertEquals(DOMAIN_ID, out.getId());
        assertEquals(DOMAIN_DESCRIPTION, out.getDescription());
    }

    @Test
    void getShouldSucceed() {
        String uri = String.format(URI_FORMAT, url(), OpenstackConstants.GET);
        Domain out = template.requestBodyAndHeader(uri, null, OpenstackConstants.ID, DOMAIN_ID, Domain.class);

        assertNotNull(out);
        assertEquals(DOMAIN_NAME, out.getName());
        assertEquals(DOMAIN_ID, out.getId());
        assertEquals(DOMAIN_DESCRIPTION, out.getDescription());
        assertFalse(out.isEnabled());
    }

    @Test
    void getAllShouldSucceed() {
        String uri = String.format(URI_FORMAT, url(), OpenstackConstants.GET_ALL);
        Domain[] domains = template.requestBody(uri, null, Domain[].class);

        assertEquals(1, domains.length);
        assertEquals("default", domains[0].getId());
        assertNotNull(domains[0].getOptions());
        assertTrue(domains[0].getOptions().isEmpty());
    }

    @Test
    void updateShouldSucceed() {
        Domain in = Builders.domain().name(DOMAIN_NAME).description(DOMAIN_DESCRIPTION_UPDATED).id(DOMAIN_ID).build();

        String uri = String.format(URI_FORMAT, url(), OpenstackConstants.UPDATE);
        Domain out = template.requestBody(uri, in, Domain.class);

        assertNotNull(out);
        assertEquals(DOMAIN_NAME, out.getName());
        assertEquals(DOMAIN_ID, out.getId());
        assertEquals(DOMAIN_DESCRIPTION_UPDATED, out.getDescription());
    }

    @Test
    void deleteShouldSucceed() {
        String uri = String.format(URI_FORMAT, url(), OpenstackConstants.DELETE);
        assertDoesNotThrow(() -> template.requestBodyAndHeader(uri, null, OpenstackConstants.ID, DOMAIN_ID));
    }
}
