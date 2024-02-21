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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.component.openstack.common.OpenstackConstants;
import org.apache.camel.component.openstack.glance.GlanceConstants;
import org.junit.jupiter.api.Test;
import org.openstack4j.model.common.Payload;
import org.openstack4j.model.common.Payloads;
import org.openstack4j.model.image.ContainerFormat;
import org.openstack4j.model.image.DiskFormat;
import org.openstack4j.model.image.Image;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class OpenstackGlanceTest extends OpenstackWiremockTestSupport {

    private static final String URI_FORMAT = "openstack-glance://%s?username=user&password=secret&project=project&operation=%s";

    @Test
    void createShouldSucceed() {
        Map<String, Object> headers = new HashMap<>();
        headers.put(OpenstackConstants.NAME, "amphora-x64-haproxy");
        headers.put(GlanceConstants.DISK_FORMAT, DiskFormat.QCOW2);
        headers.put(GlanceConstants.CONTAINER_FORMAT, ContainerFormat.BARE);
        headers.put(GlanceConstants.MIN_DISK, 0L);
        headers.put(GlanceConstants.MIN_RAM, 0L);

        Payload<InputStream> payload = Payloads.create(new ByteArrayInputStream(new byte[] { 10, 11, 12 }));
        String uri = String.format(URI_FORMAT, url(), OpenstackConstants.CREATE);
        Image out = template.requestBodyAndHeaders(uri, payload, headers, Image.class);

        assertNotNull(out);
        assertEquals("8a2ea42d-06b5-42c2-a54d-97105420f2bb", out.getId());
        assertEquals("amphora-x64-haproxy", out.getName());
        assertEquals(ContainerFormat.BARE, out.getContainerFormat());
        assertEquals(DiskFormat.QCOW2, out.getDiskFormat());
        assertEquals(0L, out.getMinDisk());
        assertEquals(0L, out.getMinRam());
    }

    @Test
    void uploadShouldSucceed() {
        Map<String, Object> headers = new HashMap<>();
        headers.put(OpenstackConstants.NAME, "amphora-x64-haproxy");
        headers.put(OpenstackConstants.ID, "4b434528-032b-4467-946c-b5880ce15c06");
        headers.put(GlanceConstants.DISK_FORMAT, DiskFormat.QCOW2);
        headers.put(GlanceConstants.CONTAINER_FORMAT, ContainerFormat.BARE);
        headers.put(GlanceConstants.MIN_DISK, 0L);
        headers.put(GlanceConstants.MIN_RAM, 0L);

        Payload<InputStream> payload = Payloads.create(new ByteArrayInputStream(new byte[] { 10, 11, 12 }));
        String uri = String.format(URI_FORMAT, url(), GlanceConstants.UPLOAD);
        Image out = template.requestBodyAndHeaders(uri, payload, headers, Image.class);

        assertNotNull(out);
        assertEquals("4b434528-032b-4467-946c-b5880ce15c06", out.getId());
    }

    @Test
    void getShouldSucceed() {
        String uri = String.format(URI_FORMAT, url(), OpenstackConstants.GET);
        String id = "8a2ea42d-06b5-42c2-a54d-97105420f2bb";
        Image out = template.requestBodyAndHeader(uri, null, OpenstackConstants.ID, id, Image.class);

        assertEquals(id, out.getId());
    }

    @Test
    void getAllShouldSucceed() {
        String uri = String.format(URI_FORMAT, url(), OpenstackConstants.GET_ALL);
        Image[] images = template.requestBody(uri, null, Image[].class);

        assertEquals(2, images.length);
        assertEquals("7541b8be-c62b-46c3-b5a5-5bb5ce43ce01", images[0].getId());
    }

    @Test
    void deleteShouldSucceed() {
        String uri = String.format(URI_FORMAT, url(), OpenstackConstants.DELETE);
        assertDoesNotThrow(
                () -> template.requestBodyAndHeader(uri, null, OpenstackConstants.ID, "8a2ea42d-06b5-42c2-a54d-97105420f2bb"));
    }
}
