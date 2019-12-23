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
package org.apache.camel.component.openstack.swift;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.camel.component.openstack.common.OpenstackConstants;
import org.apache.camel.component.openstack.swift.producer.ObjectProducer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.openstack4j.api.storage.ObjectStorageObjectService;
import org.openstack4j.model.common.ActionResponse;
import org.openstack4j.model.common.Payload;
import org.openstack4j.model.common.Payloads;
import org.openstack4j.model.storage.object.SwiftObject;
import org.openstack4j.model.storage.object.options.ObjectLocation;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ObjectProducerTest extends SwiftProducerTestSupport {

    private static final String CONTAINER_NAME = "containerName";
    private static final String OBJECT_NAME = "objectName";
    private static final String ETAG = UUID.randomUUID().toString();

    @Mock
    private SwiftObject mockOsObject;

    @Mock
    private ObjectStorageObjectService objectService;

    @Captor
    private ArgumentCaptor<String> containerNameCaptor;

    @Captor
    private ArgumentCaptor<String> objectNameCaptor;

    @Captor
    private ArgumentCaptor<Payload<?>> payloadArgumentCaptor;

    @Captor
    private ArgumentCaptor<ObjectLocation> locationCaptor;

    @Captor
    private ArgumentCaptor<Map<String, String>> dataCaptor;

    @Before
    public void setUp() {
        when(objectStorageService.objects()).thenReturn(objectService);

        producer = new ObjectProducer(endpoint, client);

        when(mockOsObject.getETag()).thenReturn(ETAG);
    }

    @Test
    public void createTest() throws Exception {
        when(objectService.put(anyString(), anyString(), any())).thenReturn(ETAG);
        msg.setHeader(OpenstackConstants.OPERATION, OpenstackConstants.CREATE);
        msg.setHeader(SwiftConstants.CONTAINER_NAME, CONTAINER_NAME);
        msg.setHeader(SwiftConstants.OBJECT_NAME, OBJECT_NAME);
        final Payload<?> payload = getTmpPayload();
        msg.setBody(payload);

        producer.process(exchange);

        verify(objectService).put(containerNameCaptor.capture(), objectNameCaptor.capture(), payloadArgumentCaptor.capture());
        assertEquals(CONTAINER_NAME, containerNameCaptor.getValue());
        assertEquals(OBJECT_NAME, objectNameCaptor.getValue());
        assertEquals(payload, payloadArgumentCaptor.getValue());

        assertEquals(ETAG, msg.getBody(String.class));
    }

    @Test
    public void getTest() throws Exception {
        when(objectService.get(CONTAINER_NAME, OBJECT_NAME)).thenReturn(mockOsObject);
        when(endpoint.getOperation()).thenReturn(OpenstackConstants.GET);

        msg.setHeader(SwiftConstants.CONTAINER_NAME, CONTAINER_NAME);
        msg.setHeader(SwiftConstants.OBJECT_NAME, OBJECT_NAME);

        producer.process(exchange);

        assertEquals(ETAG, msg.getBody(SwiftObject.class).getETag());
    }

    @Test
    public void getAllFromContainerTest() throws Exception {
        List<SwiftObject> objectsList = new ArrayList<>();
        objectsList.add(mockOsObject);
        doReturn(objectsList).when(objectService).list(CONTAINER_NAME);

        when(endpoint.getOperation()).thenReturn(OpenstackConstants.GET_ALL);

        msg.setHeader(SwiftConstants.CONTAINER_NAME, CONTAINER_NAME);

        producer.process(exchange);
        assertEquals(mockOsObject, msg.getBody(List.class).get(0));
    }


    @Test
    public void deleteObjectTest() throws Exception {
        when(objectService.delete(anyString(), anyString())).thenReturn(ActionResponse.actionSuccess());
        msg.setHeader(OpenstackConstants.OPERATION, OpenstackConstants.DELETE);
        msg.setHeader(SwiftConstants.CONTAINER_NAME, CONTAINER_NAME);
        msg.setHeader(SwiftConstants.OBJECT_NAME, OBJECT_NAME);

        producer.process(exchange);

        verify(objectService).delete(containerNameCaptor.capture(), objectNameCaptor.capture());
        assertEquals(CONTAINER_NAME, containerNameCaptor.getValue());
        assertEquals(OBJECT_NAME, objectNameCaptor.getValue());
    }

    @Test
    public void updateMetadataTest() throws Exception {
        final Map<String, String> md = new HashMap<>();
        md.put("key", "val");

        msg.setHeader(OpenstackConstants.OPERATION, SwiftConstants.CREATE_UPDATE_METADATA);
        msg.setHeader(SwiftConstants.CONTAINER_NAME, CONTAINER_NAME);
        msg.setHeader(SwiftConstants.OBJECT_NAME, OBJECT_NAME);
        msg.setBody(md);

        producer.process(exchange);

        verify(objectService).updateMetadata(locationCaptor.capture(), dataCaptor.capture());
        ObjectLocation location = locationCaptor.getValue();
        assertEquals(CONTAINER_NAME, location.getContainerName());
        assertEquals(OBJECT_NAME, location.getObjectName());
        assertEquals(md, dataCaptor.getValue());
    }

    @Test
    public void getMetadataTest() throws Exception {
        final Map<String, String> md = new HashMap<>();
        md.put("key", "val");

        when(objectService.getMetadata(CONTAINER_NAME, OBJECT_NAME)).thenReturn(md);
        msg.setHeader(OpenstackConstants.OPERATION, SwiftConstants.GET_METADATA);
        msg.setHeader(SwiftConstants.CONTAINER_NAME, CONTAINER_NAME);
        msg.setHeader(SwiftConstants.OBJECT_NAME, OBJECT_NAME);

        producer.process(exchange);

        assertEquals(md, msg.getBody(Map.class));
    }


    private Payload<File> getTmpPayload() throws IOException {
        return Payloads.create(File.createTempFile("payloadPreffix", ".txt"));
    }
}
