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
package org.apache.camel.component.openstack.glance;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.UUID;

import org.apache.camel.component.openstack.AbstractProducerTestSupport;
import org.apache.camel.component.openstack.common.OpenstackConstants;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import org.openstack4j.api.Builders;
import org.openstack4j.api.image.ImageService;
import org.openstack4j.model.common.Payload;
import org.openstack4j.model.image.ContainerFormat;
import org.openstack4j.model.image.DiskFormat;
import org.openstack4j.model.image.Image;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class GlanceProducerTest extends AbstractProducerTestSupport {

    @Mock
    private GlanceEndpoint endpoint;

    @Mock
    private ImageService imageService;

    @Captor
    private ArgumentCaptor<Image> captor;

    @Captor
    private ArgumentCaptor<Payload<?>> payloadCaptor;

    @Captor
    private ArgumentCaptor<String> imageIdCaptor;

    @Captor
    private ArgumentCaptor<org.openstack4j.model.image.Image> imageCaptor;

    private Image dummyImage;

    @Spy
    private Image osImage = Builders.image().build();

    @Before
    public void setUp() {
        producer = new GlanceProducer(endpoint, client);
        when(client.images()).thenReturn(imageService);
        dummyImage = createImage();

        when(imageService.create(any(), any())).thenReturn(osImage);
        when(imageService.reserve(any())).thenReturn(osImage);
        when(imageService.upload(anyString(), any(), isNull())).thenReturn(osImage);
        when(imageService.upload(anyString(), any(), any())).thenReturn(osImage);

        when(osImage.getContainerFormat()).thenReturn(ContainerFormat.BARE);
        when(osImage.getDiskFormat()).thenReturn(DiskFormat.ISO);
        when(osImage.getName()).thenReturn(dummyImage.getName());
        when(osImage.getChecksum()).thenReturn(dummyImage.getChecksum());
        when(osImage.getMinDisk()).thenReturn(dummyImage.getMinDisk());
        when(osImage.getMinRam()).thenReturn(dummyImage.getMinRam());
        when(osImage.getOwner()).thenReturn(dummyImage.getOwner());
        when(osImage.getId()).thenReturn(UUID.randomUUID().toString());
    }

    @Test
    public void reserveTest() throws Exception {
        when(endpoint.getOperation()).thenReturn(GlanceConstants.RESERVE);
        msg.setBody(dummyImage);
        producer.process(exchange);

        verify(imageService).reserve(captor.capture());
        assertEquals(dummyImage, captor.getValue());

        Image result = msg.getBody(Image.class);
        assertNotNull(result.getId());
        assertEqualsImages(dummyImage, result);
    }

    @Test
    public void reserveWithHeadersTest() throws Exception {
        when(endpoint.getOperation()).thenReturn(GlanceConstants.RESERVE);
        msg.setHeader(OpenstackConstants.NAME, dummyImage.getName());
        msg.setHeader(GlanceConstants.CONTAINER_FORMAT, dummyImage.getContainerFormat());
        msg.setHeader(GlanceConstants.DISK_FORMAT, dummyImage.getDiskFormat());
        msg.setHeader(GlanceConstants.CHECKSUM, dummyImage.getChecksum());
        msg.setHeader(GlanceConstants.MIN_DISK, dummyImage.getMinDisk());
        msg.setHeader(GlanceConstants.MIN_RAM, dummyImage.getMinRam());
        msg.setHeader(GlanceConstants.OWNER, dummyImage.getOwner());

        producer.process(exchange);
        verify(imageService).reserve(captor.capture());
        assertEqualsImages(dummyImage, captor.getValue());

        final Image result = msg.getBody(Image.class);
        assertNotNull(result.getId());
        assertEqualsImages(dummyImage, result);
    }

    @Test
    public void createTest() throws Exception {
        msg.setHeader(OpenstackConstants.OPERATION, OpenstackConstants.CREATE);
        msg.setHeader(OpenstackConstants.NAME, dummyImage.getName());
        msg.setHeader(GlanceConstants.OWNER, dummyImage.getOwner());
        msg.setHeader(GlanceConstants.MIN_DISK, dummyImage.getMinDisk());
        msg.setHeader(GlanceConstants.MIN_RAM, dummyImage.getMinRam());
        msg.setHeader(GlanceConstants.CHECKSUM, dummyImage.getChecksum());
        msg.setHeader(GlanceConstants.DISK_FORMAT, dummyImage.getDiskFormat());
        msg.setHeader(GlanceConstants.CONTAINER_FORMAT, dummyImage.getContainerFormat());

        final InputStream is = new FileInputStream(File.createTempFile("image", ".iso"));
        msg.setBody(is);
        producer.process(exchange);

        verify(imageService).create(imageCaptor.capture(), payloadCaptor.capture());
        assertEquals(is, payloadCaptor.getValue().open());

        final Image result = msg.getBody(Image.class);
        assertNotNull(result.getId());
        assertEqualsImages(dummyImage, result);
    }

    @Test
    public void uploadWithoutUpdatingTest() throws Exception {
        msg.setHeader(OpenstackConstants.OPERATION, GlanceConstants.UPLOAD);
        final String id = "id";
        msg.setHeader(OpenstackConstants.ID, id);

        final File file = File.createTempFile("image", ".iso");
        msg.setBody(file);
        producer.process(exchange);

        verify(imageService).upload(imageIdCaptor.capture(), payloadCaptor.capture(), imageCaptor.capture());
        assertEquals(file, payloadCaptor.getValue().getRaw());
        assertEquals(id, imageIdCaptor.getValue());
        assertNull(imageCaptor.getValue());

        final Image result = msg.getBody(Image.class);
        assertNotNull(result.getId());
        assertEqualsImages(dummyImage, result);
    }

    @Test
    public void uploadWithUpdatingTest() throws Exception {
        final String newName = "newName";
        dummyImage.setName(newName);
        when(osImage.getName()).thenReturn(newName);
        msg.setHeader(OpenstackConstants.OPERATION, GlanceConstants.UPLOAD);
        final String id = "id";
        msg.setHeader(OpenstackConstants.ID, id);
        msg.setHeader(OpenstackConstants.NAME, dummyImage.getName());
        msg.setHeader(GlanceConstants.OWNER, dummyImage.getOwner());
        msg.setHeader(GlanceConstants.MIN_DISK, dummyImage.getMinDisk());
        msg.setHeader(GlanceConstants.MIN_RAM, dummyImage.getMinRam());
        msg.setHeader(GlanceConstants.CHECKSUM, dummyImage.getChecksum());
        msg.setHeader(GlanceConstants.DISK_FORMAT, dummyImage.getDiskFormat());
        msg.setHeader(GlanceConstants.CONTAINER_FORMAT, dummyImage.getContainerFormat());

        final File file = File.createTempFile("image", ".iso");
        msg.setBody(file);
        producer.process(exchange);

        verify(imageService).upload(imageIdCaptor.capture(), payloadCaptor.capture(), imageCaptor.capture());
        assertEquals(id, imageIdCaptor.getValue());
        assertEquals(file, payloadCaptor.getValue().getRaw());
        assertEquals(newName, imageCaptor.getValue().getName());

        final Image result = msg.getBody(Image.class);
        assertNotNull(result.getId());
        assertEqualsImages(dummyImage, result);
    }

    @Test
    public void updateTest() throws Exception {
        msg.setHeader(OpenstackConstants.OPERATION, OpenstackConstants.UPDATE);
        when(imageService.update(any())).thenReturn(osImage);
        final String newName = "newName";
        when(osImage.getName()).thenReturn(newName);
        dummyImage.setName(newName);

        msg.setBody(dummyImage);
        producer.process(exchange);

        verify(imageService).update(imageCaptor.capture());

        assertEquals(dummyImage, imageCaptor.getValue());
        assertEqualsImages(dummyImage, msg.getBody(Image.class));
    }

    private Image createImage() {
        return Builders.image()
                .name("Image Name")
                .diskFormat(DiskFormat.ISO)
                .containerFormat(ContainerFormat.BARE)
                .checksum("checksum")
                .minDisk(10L)
                .minRam(5L)
                .owner("owner").build();
    }

    private void assertEqualsImages(Image original, Image newImage) {
        assertEquals(original.getContainerFormat(), newImage.getContainerFormat());
        assertEquals(original.getDiskFormat(), newImage.getDiskFormat());
        assertEquals(original.getChecksum(), newImage.getChecksum());
        assertEquals(original.getMinDisk(), newImage.getMinDisk());
        assertEquals(original.getMinRam(), newImage.getMinRam());
        assertEquals(original.getOwner(), newImage.getOwner());
        assertEquals(original.getName(), newImage.getName());
    }
}
