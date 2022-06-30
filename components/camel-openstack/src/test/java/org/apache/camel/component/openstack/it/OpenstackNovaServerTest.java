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
import java.util.Map;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.component.openstack.common.OpenstackConstants;
import org.apache.camel.component.openstack.nova.NovaConstants;
import org.junit.jupiter.api.Test;
import org.openstack4j.api.Builders;
import org.openstack4j.api.exceptions.ServerResponseException;
import org.openstack4j.model.compute.Server;
import org.openstack4j.model.compute.Server.Status;
import org.openstack4j.model.compute.ServerCreate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class OpenstackNovaServerTest extends OpenstackWiremockTestSupport {

    private static final String URI_FORMAT
            = "openstack-nova://%s?username=user&password=secret&project=project&operation=%s&subsystem="
              + NovaConstants.NOVA_SUBSYSTEM_SERVERS;

    private static final String SERVER_NAME = "server-test-1";
    private static final String SERVER_ID = "e565cbdb-8e74-4044-ba6e-0155500b2c46";
    private static final String SERVER_WRONG_ID = "05184ba3-00ba-4fbc-b7a2-03b62b884931";
    private static final String SERVER_SNAPSHOT_NAME = "server-snapshot";
    private static final String SERVER_SNAPSHOT_ID = "72f759b3-2576-4bf0-9ac9-7cb4a5b9d541";

    @Test
    void createShouldSucceed() {
        ServerCreate in = Builders.server().name(SERVER_NAME).build();

        String uri = String.format(URI_FORMAT, url(), OpenstackConstants.CREATE);
        Server out = template.requestBody(uri, in, Server.class);

        assertNotNull(out);
        assertEquals(SERVER_NAME, out.getName());
    }

    @Test
    void createSnapshotShouldSucceed() {
        Map<String, Object> headers = new HashMap<>();
        headers.put(OpenstackConstants.ID, SERVER_ID);
        headers.put(OpenstackConstants.NAME, SERVER_SNAPSHOT_NAME);

        String uri = String.format(URI_FORMAT, url(), NovaConstants.CREATE_SNAPSHOT);
        String out = template.requestBodyAndHeaders(uri, null, headers, String.class);

        assertEquals(SERVER_SNAPSHOT_ID, out);
    }

    @Test
    void getWrongIdShouldThrow() {
        String uri = String.format(URI_FORMAT, url(), OpenstackConstants.GET);

        Exception ex = assertThrows(CamelExecutionException.class,
                () -> template.requestBodyAndHeader(uri, null, OpenstackConstants.ID, SERVER_WRONG_ID, Server.class),
                "Getting nova server with wrong id should throw");

        assertInstanceOf(ServerResponseException.class, ex.getCause());
    }

    @Test
    void getAllShouldSucceed() {
        String uri = String.format(URI_FORMAT, url(), OpenstackConstants.GET_ALL);
        Server[] servers = template.requestBody(uri, null, Server[].class);

        assertNotNull(servers);
        assertEquals(1, servers.length);
        assertEquals(1, servers[0].getAddresses().getAddresses("private").size());
        assertEquals("192.168.0.3", servers[0].getAddresses().getAddresses("private").get(0).getAddr());
        assertEquals(Status.ACTIVE, servers[0].getStatus());
        assertEquals("new-server-test", servers[0].getName());
    }

}
